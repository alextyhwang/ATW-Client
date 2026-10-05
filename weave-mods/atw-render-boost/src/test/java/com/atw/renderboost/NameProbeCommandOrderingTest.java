package com.atw.renderboost;

import com.atw.renderboost.probe.NameProbe;
import com.atw.renderboost.probe.NameProbeRuntime;
import java.io.*;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import static org.junit.jupiter.api.Assertions.*;

/** Real command guards/frame callbacks/exporter; only game services and time are stubbed. */
public class NameProbeCommandOrderingTest implements Opcodes {
    public static final class Clock implements NameProbe.Clock {
        static long now;
        public long wall() { return now; }
        public long cpu() { return -1; }
        public static long nanoTime() { return now; }
    }
    @TempDir Path home;
    private Class<?> runtime;
    private ExecutorService export;
    private String previousHome;
    private final CountDownLatch release = new CountDownLatch(1);

    @BeforeEach void prepare() throws Exception {
        previousHome = System.getProperty("user.home");
        System.setProperty("user.home", home.toString());
        runtime = new Services().loadClass("com.atw.renderboost.RenderRuntime");
        export = (ExecutorService) field(runtime, "EXPORT").get(null);
        field(runtime, "failed").set(null, true); // No GL work; command/frame/probe logic stays intact.
        Clock.now = 100;
        NameProbeRuntime.collecting = false;
        field(NameProbeRuntime.class, "probe").set(null, null);
        field(NameProbeRuntime.class, "owner").set(null, null);
        CountDownLatch blocked = new CountDownLatch(1);
        export.execute(() -> {
            blocked.countDown();
            try { if (!release.await(10, TimeUnit.SECONDS)) throw new AssertionError("Export barrier timed out"); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        });
        assertTrue(blocked.await(5, TimeUnit.SECONDS));
    }
    @AfterEach void cleanup() throws Exception {
        release.countDown();
        if (export != null) { export.shutdown(); assertTrue(export.awaitTermination(5, TimeUnit.SECONDS)); }
        NameProbeRuntime.stop(); NameProbeRuntime.takeCompleted();
        NameProbeRuntime.collecting = false;
        field(NameProbeRuntime.class, "probe").set(null, null);
        field(NameProbeRuntime.class, "owner").set(null, null);
        System.setProperty("user.home", previousHome);
    }
    private static Field field(Class<?> type, String name) throws Exception {
        Field field = type.getDeclaredField(name); field.setAccessible(true); return field;
    }
    private Object call(String name, Class<?>[] parameters, Object... args) throws Exception {
        try { return runtime.getMethod(name, parameters).invoke(null, args); }
        catch (InvocationTargetException e) {
            if (e.getCause() instanceof RuntimeException) throw (RuntimeException)e.getCause();
            if (e.getCause() instanceof Error) throw (Error)e.getCause();
            throw e;
        }
    }
    private void command(String mode) throws Exception {
        if (mode.equals("bench")) call("benchmark", new Class<?>[]{int.class,int.class}, 5, 1);
        else call("nameProbe", new Class<?>[]{String.class}, mode);
    }
    private NameProbe deadlineFrame() throws Exception {
        NameProbe probe = new NameProbe(new Clock()); probe.start(false);
        field(NameProbeRuntime.class, "probe").set(null, probe);
        field(NameProbeRuntime.class, "owner").set(null, Thread.currentThread());
        NameProbeRuntime.collecting = true;
        Clock.now += NameProbe.DURATION_NS - 1;
        call("frameStart", new Class<?>[0]);
        int token = NameProbeRuntime.enter(0, null); NameProbeRuntime.exit(token, false);
        Clock.now++;
        return probe;
    }
    private Properties finishExport() throws Exception {
        release.countDown(); export.submit(() -> {}).get(5, TimeUnit.SECONDS);
        assertFalse((Boolean)field(runtime, "exporting").get(null));
        Path directory = home.resolve(".weave/atw-render-boost/name-probes");
        List<Path> files = new ArrayList<>();
        try (java.util.stream.Stream<Path> stream = Files.list(directory)) { stream.forEach(files::add); }
        assertEquals(1, files.size(), "Exactly one original result must survive");
        Properties result = new Properties();
        try (InputStream in = Files.newInputStream(files.get(0))) { result.load(in); }
        assertEquals("counters", result.getProperty("mode"));
        assertEquals("10-second limit", result.getProperty("stopReason"));
        assertEquals("1", result.getProperty("frames"));
        assertEquals("1", result.getProperty("abandonedFrames"));
        assertEquals("1", result.getProperty("otherLiving.outerName.calls"));
        return result;
    }
    @ParameterizedTest @ValueSource(strings = {"counters", "timing", "bench"})
    void statusExpiryThenQueuedCommandBeforeFrameEndPreservesResultAndExcludesBenchmark(String next) throws Exception {
        deadlineFrame();
        command("status"); // Scheduled status expires the frame opened just before the deadline.
        assertFalse(NameProbeRuntime.collecting);
        IllegalStateException rejected = null;
        try { command(next); } catch (IllegalStateException e) { rejected = e; }
        call("frameEnd", new Class<?>[0]);
        final IllegalStateException rejection = rejected;
        assertAll("status -> queued command -> frameEnd",
                () -> assertNotNull(rejection, "Queued command must wait for export"),
                () -> assertFalse(NameProbeRuntime.collecting, "New collection must not replace the old result"),
                () -> assertTrue((Boolean)field(runtime, "exporting").get(null), "Original result must be exporting"),
                () -> assertNull(field(runtime, "session").get(null), "Benchmark must never arm across probe export"));
        finishExport();
        command(next); // The same real guard admits the operation once the original export finishes.
        assertEquals(next.equals("bench"), field(runtime, "session").get(null) != null);
        assertEquals(!next.equals("bench"), NameProbeRuntime.collecting);
    }
    @ParameterizedTest @ValueSource(strings = {"counters", "timing", "bench"})
    void newCommandPollsDeadlineAndDrainsCompletionWithoutPriorStatus(String next) throws Exception {
        deadlineFrame();
        assertThrows(IllegalStateException.class, () -> command(next));
        assertFalse(NameProbeRuntime.collecting, "Command boundary must poll expiration before its guard");
        assertNull(field(runtime, "session").get(null));
        call("frameEnd", new Class<?>[0]);
        finishExport();
    }
    @ParameterizedTest @ValueSource(booleans = {false, true})
    void directRuntimeStartCannotReplaceUndrainedCompletion(boolean expire) throws Exception {
        NameProbe original = deadlineFrame();
        if (expire) NameProbeRuntime.status(); else NameProbeRuntime.stop();
        assertThrows(IllegalStateException.class, () -> NameProbeRuntime.start(true));
        assertSame(original, field(NameProbeRuntime.class, "probe").get(null));
        Properties result = NameProbeRuntime.takeCompleted();
        assertNotNull(result); assertEquals("1", result.getProperty("otherLiving.outerName.calls"));
        assertNull(NameProbeRuntime.takeCompleted());
        NameProbeRuntime.start(true); assertTrue(NameProbeRuntime.collecting);
    }

    /** Isolates each real RenderRuntime executor; no runtime method/guard is replaced. */
    private static final class Services extends ClassLoader {
        Services() { super(NameProbeCommandOrderingTest.class.getClassLoader()); }
        @Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            synchronized (getClassLoadingLock(name)) {
                Class<?> loaded = findLoadedClass(name);
                if (loaded == null && (name.startsWith("com.atw.renderboost.RenderRuntime")
                        || name.equals("com.atw.renderboost.terrain.TerrainRuntime")
                        || name.startsWith("net.minecraft.") || name.equals("org.lwjgl.BufferUtils"))) {
                    try {
                        byte[] bytes = name.startsWith("com.atw.renderboost.RenderRuntime") ? runtimeBytes(name) : stub(name);
                        loaded = defineClass(name, bytes, 0, bytes.length);
                    } catch (IOException e) { throw new ClassNotFoundException(name, e); }
                }
                if (loaded == null) loaded = super.loadClass(name, false);
                if (resolve) resolveClass(loaded);
                return loaded;
            }
        }
        private byte[] runtimeBytes(String name) throws IOException {
            ClassNode node = new ClassNode();
            try (InputStream input = getParent().getResourceAsStream(name.replace('.','/') + ".class")) {
                new ClassReader(input).accept(node, 0);
            }
            for (MethodNode method : node.methods) for (AbstractInsnNode instruction : method.instructions)
                if (instruction instanceof MethodInsnNode) {
                    MethodInsnNode call = (MethodInsnNode)instruction;
                    if (call.owner.equals("java/lang/System") && call.name.equals("nanoTime"))
                        call.owner = Type.getInternalName(Clock.class);
                }
            ClassWriter writer = new ClassWriter(0); node.accept(writer); return writer.toByteArray();
        }
        private byte[] stub(String name) {
            String owner = name.replace('.','/');
            ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            boolean component = name.equals("net.minecraft.util.IChatComponent");
            writer.visit(V1_8, ACC_PUBLIC | (component ? ACC_INTERFACE|ACC_ABSTRACT : 0), owner, null, "java/lang/Object",
                    name.equals("net.minecraft.util.ChatComponentText") ? new String[]{"net/minecraft/util/IChatComponent"} : null);
            if (component) { writer.visitEnd(); return writer.toByteArray(); }
            MethodVisitor constructor = writer.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
            constructor.visitCode(); constructor.visitVarInsn(ALOAD, 0);
            constructor.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
            constructor.visitInsn(RETURN); constructor.visitMaxs(0,0); constructor.visitEnd();
            if (name.equals("net.minecraft.client.Minecraft")) {
                writer.visitField(ACC_PUBLIC, "thePlayer", "Lnet/minecraft/client/entity/EntityPlayerSP;", null, null).visitEnd();
                writer.visitField(ACC_PUBLIC, "theWorld", "Lnet/minecraft/client/multiplayer/WorldClient;", null, null).visitEnd();
                MethodVisitor method = writer.visitMethod(ACC_PUBLIC|ACC_STATIC, "getMinecraft", "()L"+owner+";", null, null);
                method.visitCode(); method.visitTypeInsn(NEW, owner); method.visitInsn(DUP);
                method.visitMethodInsn(INVOKESPECIAL, owner, "<init>", "()V", false);
                method.visitInsn(ARETURN); method.visitMaxs(0,0); method.visitEnd();
            } else if (name.equals("org.lwjgl.BufferUtils")) {
                MethodVisitor method = writer.visitMethod(ACC_PUBLIC|ACC_STATIC, "createIntBuffer", "(I)Ljava/nio/IntBuffer;", null, null);
                method.visitCode(); method.visitVarInsn(ILOAD, 0);
                method.visitMethodInsn(INVOKESTATIC, "java/nio/IntBuffer", "allocate", "(I)Ljava/nio/IntBuffer;", false);
                method.visitInsn(ARETURN); method.visitMaxs(0,0); method.visitEnd();
            } else if (name.equals("com.atw.renderboost.terrain.TerrainRuntime")) {
                MethodVisitor method = writer.visitMethod(ACC_PUBLIC|ACC_STATIC, "frameThread", "()V", null, null);
                method.visitCode(); method.visitInsn(RETURN); method.visitMaxs(0,0); method.visitEnd();
            }
            writer.visitEnd(); return writer.toByteArray();
        }
    }
}
