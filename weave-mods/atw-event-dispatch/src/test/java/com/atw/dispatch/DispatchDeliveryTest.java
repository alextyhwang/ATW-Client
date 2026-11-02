// SPDX-License-Identifier: GPL-3.0-only
package com.atw.dispatch;

import com.atw.dispatch.hook.DispatchHook;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.jar.JarFile;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.objectweb.asm.Opcodes.*;

final class DispatchDeliveryTest {
    static final Path REPOSITORY = Path.of(System.getProperty("dispatch.repository"));
    static final Path API = Path.of(System.getProperty("dispatch.api"));
    static final Path LOADER = REPOSITORY.resolve("runtime/weave/Weave-Loader-Agent-1.4.1.jar");
    static final String BUS = "net.weavemc.api.event.EventBus";
    static final String HELPER = DispatchRuntime.class.getName();
    static final String HARNESS = "com.atw.dispatch.test.EventBusScreen";

    static ClassNode node(byte[] bytes) { ClassNode n = new ClassNode(); new ClassReader(bytes).accept(n, 0); return n; }
    static byte[] read(Path jar, String name) throws IOException {
        try (JarFile zip = new JarFile(jar.toFile()); InputStream in = zip.getInputStream(zip.getJarEntry(name))) {
            return in.readAllBytes();
        }
    }
    static String hash(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    @Test void pinsAndGuard() throws Exception {
        assertEquals("e8fd470f32e609f79d577932295fa39c010a45a540b9cf9731932c26bfcb6111", hash(Files.readAllBytes(API)));
        assertEquals("9fbcada12fc031426eb80613b02b9be31d83add6a116720b7e17e4439d28b4c8", hash(Files.readAllBytes(LOADER)));
        for (Path jar : List.of(API, LOADER)) {
            ClassNode n = node(read(jar, DispatchHook.TARGET + ".class"));
            System.out.println(jar.getFileName() + " normalized=" + DispatchHook.fingerprint(n));
            assertTrue(DispatchHook.matches(n), "exact pin rejected");
        }
    }

    /** Child-first API and helper; first definition is transformed before defineClass, with original jar code source. */
    static final class Isolated extends URLClassLoader {
        final Path artifact;
        final boolean transform;
        final boolean denyHelper;
        final boolean mismatch;
        final List<String> definitions = new ArrayList<>();
        byte[] original, transformed;
        Isolated(Path artifact, boolean transform, boolean denyHelper, boolean mismatch) throws Exception {
            super(new URL[]{artifact.toUri().toURL()}, DispatchDeliveryTest.class.getClassLoader());
            this.artifact = artifact; this.transform = transform; this.denyHelper = denyHelper; this.mismatch = mismatch;
        }
        @Override protected synchronized Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (name.startsWith("net.weavemc.api.event.") || name.equals(HELPER)
                || name.startsWith(HARNESS) || name.equals("com.atw.dispatch.test.MapFixture")) {
                if (denyHelper && name.equals(HELPER)) throw new ClassNotFoundException("deliberate unavailable helper");
                Class<?> result = findLoadedClass(name);
                if (result == null) {
                    try {
                        String resource = name.replace('.', '/') + ".class";
                        byte[] bytes;
                        if (name.startsWith("net.weavemc.api.event.")) bytes = read(artifact, resource);
                        else try (InputStream in = getParent().getResourceAsStream(resource)) { bytes = Objects.requireNonNull(in).readAllBytes(); }
                        definitions.add(name);
                        if (name.equals(BUS)) {
                            original = bytes;
                            if (transform) {
                                ClassNode n = node(bytes);
                                if (mismatch) n.methods.stream().filter(m -> m.name.equals("postEvent")).findFirst().orElseThrow()
                                    .instructions.insert(new InsnNode(NOP));
                                new DispatchHook().transform(n, () -> {});
                                ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES); n.accept(writer);
                                bytes = writer.toByteArray(); transformed = bytes;
                            }
                        }
                        result = defineClass(name, bytes, 0, bytes.length,
                            new java.security.ProtectionDomain(new java.security.CodeSource(artifact.toUri().toURL(),
                                (java.security.cert.Certificate[]) null), null, this, null));
                    } catch (Exception e) { throw new ClassNotFoundException(name, e); }
                }
                if (resolve) resolveClass(result);
                return result;
            }
            return super.loadClass(name, resolve);
        }
        Class<?> bus() throws Exception { return loadClass(BUS); }
        Class<?> helper() throws Exception { return loadClass(HELPER); }
        void request(boolean enabled) throws Exception { helper().getMethod("request", boolean.class).invoke(null, enabled); }
        String status() throws Exception { return (String) helper().getMethod("status").invoke(null); }
        Object event() throws Exception { return loadClass(HARNESS + "$CancelEvent").getConstructor().newInstance(); }
        @SuppressWarnings("unchecked") Map<Object, Object> map() throws Exception {
            var field = bus().getDeclaredField("map"); field.setAccessible(true); return (Map<Object, Object>) field.get(null);
        }
        void subscribe(Class<?> type, Consumer<Object> listener) throws Exception {
            bus().getMethod("subscribe", Class.class, Consumer.class).invoke(null, type, listener);
        }
        void post(Object event) throws Throwable {
            try { bus().getMethod("postEvent", loadClass("net.weavemc.api.event.Event")).invoke(null, event); }
            catch (java.lang.reflect.InvocationTargetException error) { throw error.getCause(); }
        }
        @SuppressWarnings("unchecked") List<String> suite() throws Exception {
            return (List<String>) loadClass(HARNESS).getMethod("run").invoke(null);
        }
    }

    @Test void actualPublishedAndShadedParityAgainstIndependentOriginalDefinitions() throws Exception {
        for (Path artifact : List.of(API, LOADER)) {
            try (Isolated original = new Isolated(artifact, false, false, false);
                 Isolated candidate = new Isolated(artifact, true, false, false)) {
                List<String> expected = original.suite(), actual = candidate.suite();
                assertEquals(45, expected.size()); assertEquals(expected, actual);
                assertTrue(original.status().contains("effectivePosts=0"), original.status());
                assertFalse(candidate.status().contains("effectivePosts=0"), candidate.status());
                assertTrue(candidate.status().contains("available=true"), candidate.status());
                assertEquals(candidate, candidate.bus().getClassLoader());
                assertEquals(candidate, candidate.helper().getClassLoader());
                System.out.println(artifact.getFileName() + " 45 cases (including 20 concurrency schedules): " + candidate.status());
            }
        }
    }

    @Test void defaultOffAndOnRequiresExercisedPost() throws Throwable {
        for (Path artifact : List.of(API, LOADER)) try (Isolated loader = new Isolated(artifact, true, false, false)) {
            AtomicInteger calls = new AtomicInteger(); Object event = loader.event();
            loader.subscribe(event.getClass(), e -> { assertSame(event, e); calls.incrementAndGet(); });
            assertTrue(loader.status().contains("requested=false"));
            loader.post(event); assertEquals(1, calls.get());
            assertTrue(loader.status().contains("effectivePosts=0"));
            assertTrue(loader.status().contains("baselineFallback=1"));
            loader.request(true); assertTrue(loader.status().contains("active=false"));
            loader.post(event); assertEquals(2, calls.get()); assertTrue(loader.status().contains("active=true"));
            loader.request(false); loader.post(event); assertEquals(3, calls.get());
            assertTrue(loader.status().contains("active=false"));
            loader.request(true); assertTrue(loader.status().contains("active=false"));
            loader.helper().getMethod("reset").invoke(null); assertTrue(loader.status().contains("requested=true"));
            assertTrue(loader.status().contains("active=false"));
        }
    }

    @Test void helperUnavailableAndLateHookRetainBaseline() throws Throwable {
        for (Path artifact : List.of(API, LOADER)) {
            try (Isolated absent = new Isolated(artifact, true, true, false)) {
                Object event = absent.event(); AtomicInteger calls = new AtomicInteger();
                absent.subscribe(event.getClass(), e -> calls.incrementAndGet()); absent.post(event); absent.post(event);
                assertEquals(2, calls.get());
                assertTrue(((String) absent.bus().getField(DispatchHook.SEAM_FAILURE).get(null)).contains("unavailable"));
            }
            try (Isolated late = new Isolated(artifact, false, false, false)) {
                Class<?> alreadyDefined = late.bus();
                // A later Hook changes only a separate ClassNode, never the already defined class.
                ClassNode n = node(read(artifact, DispatchHook.TARGET + ".class"));
                new DispatchHook().transform(n, () -> {});
                assertSame(alreadyDefined, late.bus());
                late.request(true); late.post(late.event());
                assertTrue(late.status().contains("active=false"));
                assertTrue(late.status().contains("effectivePosts=0"));
                assertThrows(NoSuchFieldException.class, () -> alreadyDefined.getField(DispatchHook.SEAM_FAILURE));
            }
        }
    }

    @Test void rejectionLeavesNodeExactAndOffRetainsOriginalInstructions() throws Exception {
        for (Path artifact : List.of(API, LOADER)) {
            byte[] bytes = read(artifact, DispatchHook.TARGET + ".class");
            for (String mutation : List.of("method", "map", "clinit", "name")) {
                ClassNode n = node(bytes);
                switch (mutation) {
                    case "method" -> n.methods.stream().filter(m -> m.name.equals("postEvent")).findFirst().orElseThrow().desc = "(Ljava/lang/Object;)V";
                    case "map" -> n.fields.stream().filter(f -> f.name.equals("map")).findFirst().orElseThrow().desc = "Ljava/util/concurrent/ConcurrentHashMap;";
                    case "clinit" -> n.methods.stream().filter(m -> m.name.equals("<clinit>")).findFirst().orElseThrow().instructions.insert(new InsnNode(NOP));
                    case "name" -> n.name += "Unknown";
                }
                String before = DispatchHook.fingerprint(n);
                new DispatchHook().transform(n, () -> fail("rejected node requested frames"));
                assertEquals(before, DispatchHook.fingerprint(n));
                assertFalse(n.fields.stream().anyMatch(f -> f.name.equals(DispatchHook.SEAM_FAILURE)));
            }
            ClassNode n = node(bytes);
            MethodNode method = n.methods.stream().filter(m -> m.name.equals("postEvent")).findFirst().orElseThrow();
            AbstractInsnNode[] original = method.instructions.toArray();
            var handlers = List.copyOf(method.tryCatchBlocks);
            new DispatchHook().transform(n, () -> {});
            AbstractInsnNode[] all = method.instructions.toArray();
            assertArrayEquals(original, Arrays.copyOfRange(all, all.length - original.length, all.length));
            assertEquals(handlers, method.tryCatchBlocks.subList(1, method.tryCatchBlocks.size()));
            var catcher = method.tryCatchBlocks.get(0);
            for (AbstractInsnNode i = catcher.start; i != catcher.end; i = i.getNext())
                if (i instanceof MethodInsnNode call) assertNotEquals("dispatch", call.name);
        }
    }

    @Test void strictMapListPreflightAndNoPartialReplay() throws Throwable {
        for (Path artifact : List.of(API, LOADER)) try (Isolated loader = new Isolated(artifact, true, false, false)) {
            Object event = loader.event(); AtomicInteger calls = new AtomicInteger();
            var map = loader.map();
            map.put(event.getClass(), new ArrayList<>(List.of((Consumer<Object>) e -> calls.incrementAndGet())));
            loader.request(true); loader.post(event);
            assertEquals(1, calls.get()); assertTrue(loader.status().contains("effectivePosts=0"));
            assertTrue(loader.status().contains("not exact CopyOnWriteArrayList"));
            map.clear();
            LinkageError marker = new NoClassDefFoundError("callback deliberate linkage failure");
            loader.subscribe(event.getClass(), e -> { calls.incrementAndGet(); throw marker; });
            loader.subscribe(event.getClass(), e -> fail("callback after failure"));
            assertSame(marker, assertThrows(LinkageError.class, () -> loader.post(event)));
            assertEquals(2, calls.get()); assertTrue(loader.status().contains("baselineFallback=1"));
            assertTrue(loader.status().contains("dispatchFailures=1"));
            // Non-CHM input exercises admission directly, before any callback.
            assertEquals(false, loader.helper().getMethod("prepare", Object.class, Map.class, Class.class)
                .invoke(null, event, new HashMap<>(), loader.bus()));
            assertTrue(loader.status().contains("not exact ConcurrentHashMap"));
        }
    }

    @Test void unknownSuperclassListRejectsBeforeAnyCandidateCallbackAndSubclassIsRejected() throws Throwable {
        for (Path artifact : List.of(API, LOADER)) try (Isolated loader = new Isolated(artifact, true, false, false)) {
            Object event = loader.event(); List<String> trace = new ArrayList<>();
            loader.subscribe(event.getClass(), e -> trace.add("child"));
            loader.map().put(event.getClass().getSuperclass(),
                new ArrayList<>(List.of((Consumer<Object>) e -> trace.add("parent"))));
            loader.request(true); loader.post(event);
            assertEquals(List.of("child", "parent"), trace);
            assertTrue(loader.status().contains("effectivePosts=0"));
            assertTrue(loader.status().contains("baselineFallback=1"));
            loader.map().clear(); trace.clear();
            class CowSubclass extends java.util.concurrent.CopyOnWriteArrayList<Object> {}
            var subclass = new CowSubclass(); subclass.add((Consumer<Object>) e -> trace.add("subclass"));
            loader.map().put(event.getClass(), subclass); loader.post(event);
            assertEquals(List.of("subclass"), trace); assertTrue(loader.status().contains("effectivePosts=0"));
        }
    }

    @Test void helperSplitDefinitionAdmissionFailsClosed() throws Exception {
        try (Isolated control = new Isolated(API, true, false, false);
             Isolated other = new Isolated(API, true, false, false)) {
            // A helper belonging to one loader cannot admit the other loader's bus/map.
            control.request(true);
            assertEquals(false, control.helper().getMethod("prepare", Object.class, Map.class, Class.class)
                .invoke(null, other.event(), other.map(), other.bus()));
            assertTrue(control.status().contains("helper identity differs"));
            assertTrue(control.status().contains("active=false"));
        }
    }

    @Test void hookAndInitializerConstructionDoNotResolveEventBus() throws Exception {
        // Structural check: no target Class constants, executable descriptors or owner references.
        for (Class<?> type : List.of(DispatchHook.class, DispatchMod.class, DispatchRuntime.class)) {
            try (InputStream in = type.getResourceAsStream("/" + type.getName().replace('.', '/') + ".class")) {
                ClassNode n = node(Objects.requireNonNull(in).readAllBytes());
                for (MethodNode method : n.methods) for (AbstractInsnNode i : method.instructions) {
                    if (i instanceof LdcInsnNode constant && constant.cst instanceof Type t)
                        assertFalse(t.getDescriptor().contains(DispatchHook.TARGET));
                    if (i instanceof MethodInsnNode call) assertNotEquals(DispatchHook.TARGET, call.owner);
                    if (i instanceof FieldInsnNode field) assertNotEquals(DispatchHook.TARGET, field.owner);
                }
            }
        }
    }
}
