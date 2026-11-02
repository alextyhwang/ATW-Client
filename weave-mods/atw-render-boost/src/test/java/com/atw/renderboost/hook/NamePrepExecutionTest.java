package com.atw.renderboost.hook;

import java.io.*;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.*;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.*;
import static org.junit.jupiter.api.Assertions.*;

/** Execute the pinned production method with isolated native fixtures; never launch/attach a game. */
class NamePrepExecutionTest implements Opcodes {
    public static class Base { public String formatString(String name) { return name; } }
    public static final class Text {
        public final String value;
        public final Object style = new Object(), hover = new Object();
        public String mutable;
        public Text(String value) { this.value = value; }
    }
    public static class NativeTeam extends Base {
        public String namePrefixSPT = "[", colorSuffix = "]";
        public String formatString(String name) { return name; } // Replaced by pinned bytes in fixture only.
        public String getColorPrefix() { return namePrefixSPT; }
        public String getColorSuffix() { return colorSuffix; }
        public Text display(String name) { return new Text(formatString(name)); }
    }
    public static class SubTeam extends NativeTeam {
        @Override public String getColorPrefix() { Effects.call("prefix"); return Effects.prefix; }
        @Override public String getColorSuffix() { Effects.call("suffix"); return Effects.suffix; }
    }
    public static final class Effects {
        public static List<String> trace = new ArrayList<>();
        public static String prefix = "P", suffix = "S";
        public static RuntimeException thrown;
        public static Runnable nested;
        public static void call(String where) {
            trace.add(where);
            if (where.equals("prefix") && nested != null) { Runnable r = nested; nested = null; r.run(); }
            if (where.equals("suffix") && thrown != null) throw thrown;
        }
    }
    static class Fixture extends ClassLoader {
        final Map<String, byte[]> definitions = new HashMap<>();
        final Class<?> runtime, team, sub;
        final Object receiver;
        Fixture(boolean instrumentGetters) throws Exception {
            this(instrumentGetters, NamePrepHookTest.mapped(), null);
        }
        // Execute inverse-stage protected methods against an original baseline.
        Fixture(boolean instrumentGetters, ClassNode pinned, ClassNode baselineSource) throws Exception {
            super(NamePrepExecutionTest.class.getClassLoader());
            Map<String, String> names = new HashMap<>();
            names.put(Type.getInternalName(Base.class), "net/minecraft/scoreboard/Team");
            names.put(Type.getInternalName(NativeTeam.class), NamePrepHook.OWNER);
            names.put(Type.getInternalName(SubTeam.class), "fixture/SubTeam");
            ClassNode teamNode = null;
            for (Class<?> type : Arrays.asList(Base.class, NativeTeam.class, SubTeam.class)) {
                ClassNode node = new ClassNode();
                try (InputStream in = type.getResourceAsStream("/" + Type.getInternalName(type) + ".class")) {
                    new ClassReader(in).accept(new ClassRemapper(node, new SimpleRemapper(names)), 0);
                }
                // Test fixtures are ordinary Java 8 bytecode; strip nest metadata from Java 17 test compilation if present.
                node.nestHostClass = null; node.nestMembers = null;
                if (node.name.equals(NamePrepHook.OWNER)) teamNode = node; else define(node);
            }
            teamNode.version = pinned.version;
            teamNode.methods.removeIf(m -> Arrays.asList("formatString", "getColorPrefix", "getColorSuffix").contains(m.name));
            for (String name : Arrays.asList("formatString", "formatPlayerName", "getColorPrefix", "getColorSuffix")) {
                MethodNode m = NamePrepHookTest.method(pinned, name), copy = new MethodNode(m.access, m.name, m.desc, m.signature, null);
                m.accept(copy); teamNode.methods.add(copy);
            }
            MethodNode original = NamePrepHookTest.method(baselineSource == null ? teamNode : baselineSource, "formatString"), baseline = new MethodNode(original.access, "baseline", original.desc, null, null);
            original.accept(baseline); baseline.name = "baseline"; teamNode.methods.add(baseline);
            if (baselineSource == null) {
                assertTrue(NamePrepHook.matches(teamNode)); new NamePrepHook().transform(teamNode, () -> {});
            } else {
                FieldNode marker = pinned.fields.stream().filter(f -> f.name.startsWith(com.atw.renderboost.cache.NamePrepRuntime.MARKER)).findFirst().get();
                teamNode.fields.add(new FieldNode(marker.access, marker.name, marker.desc, marker.signature, marker.value));
            }
            String marker = teamNode.fields.stream().filter(f -> f.name.startsWith(com.atw.renderboost.cache.NamePrepRuntime.MARKER)).findFirst().get().name;
            if (instrumentGetters) for (String name : Arrays.asList("getColorPrefix", "getColorSuffix")) {
                // Observation-only test instrumentation AFTER admission, never a production profile.
                InsnList trace = new InsnList(); trace.add(new LdcInsnNode(name.equals("getColorPrefix") ? "prefix" : "suffix"));
                trace.add(new MethodInsnNode(INVOKESTATIC, Type.getInternalName(Effects.class), "call", "(Ljava/lang/String;)V", false));
                NamePrepHookTest.method(teamNode, name).instructions.insert(trace);
            }
            for (MethodNode m : teamNode.methods) new Analyzer<>(new BasicVerifier()).analyze(teamNode.name, m);
            define(teamNode);
            for (String resource : Arrays.asList("com/atw/renderboost/cache/NamePrepRuntime", "com/atw/renderboost/cache/NamePrepCache")) {
                try (InputStream in = getParent().getResourceAsStream(resource + ".class")) { definitions.put(resource.replace('/', '.'), bytes(in)); }
            }
            runtime = loadClass("com.atw.renderboost.cache.NamePrepRuntime"); team = loadClass(NamePrepHook.OWNER.replace('/', '.')); sub = loadClass("fixture.SubTeam");
            receiver = team.getConstructor().newInstance(); invoke("evidence", new Class<?>[]{boolean.class, String.class}, true, marker);
            invoke("frame", new Class<?>[]{Thread.class, Object.class}, Thread.currentThread(), new Object());
        }
        static byte[] bytes(InputStream in) throws IOException { ByteArrayOutputStream out = new ByteArrayOutputStream(); byte[] b = new byte[4096]; int n; while ((n = in.read(b)) >= 0) out.write(b, 0, n); return out.toByteArray(); }
        void define(ClassNode c) {
            ClassWriter w = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS) {
                @Override protected String getCommonSuperClass(String a, String b) { return "java/lang/Object"; }
            }; c.accept(w); definitions.put(c.name.replace('/', '.'), w.toByteArray());
        }
        @Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (!definitions.containsKey(name)) return super.loadClass(name, resolve);
            Class<?> found = findLoadedClass(name); if (found == null) { byte[] b = definitions.get(name); found = defineClass(name, b, 0, b.length); }
            if (resolve) resolveClass(found); return found;
        }
        Object invoke(String name, Class<?>[] types, Object... args) throws Exception { return runtime.getMethod(name, types).invoke(null, args); }
        Object noarg(String name) throws Exception { return invoke(name, new Class<?>[0]); }
        void request(boolean value) throws Exception { invoke("request", new Class<?>[]{boolean.class}, value); }
        long[] counters() throws Exception { return (long[])noarg("counters"); }
        String format(Object target, String value) throws Exception { return (String)team.getMethod("formatString", String.class).invoke(target, value); }
        String format(String value) throws Exception { return format(receiver, value); }
        void set(String name, Object value) throws Exception { Field f = runtime.getDeclaredField(name); f.setAccessible(true); f.set(null, value); }
        Properties metadata() throws Exception { Properties p = new Properties(); invoke("metadata", new Class<?>[]{Properties.class}, p); return p; }
    }
    @Test void offExecutesOriginalWithFreshStringsAndAllNullCasesOnHitsPreserveExpectedOutputs() throws Exception {
        Fixture f = new Fixture(false); assertEquals(false, f.noarg("requested"));
        assertEquals("false", f.metadata().getProperty("nameprepActive")); assertEquals("true", f.metadata().getProperty("nameprepAvailable"));
        String one = f.format("A"), two = f.format("A"); assertEquals("[A]", one); assertNotSame(one, two); assertEquals(0, f.counters()[1]);
        String[][] cases = {{"[", "A", "]", "[A]"}, {null, "A", "]", "nullA]"}, {"[", null, "]", "[null]"}, {"[", "A", null, "[Anull"}, {null, null, null, "nullnullnull"}, {"", "", "", ""}};
        f.request(true);
        for (String[] row : cases) {
            f.team.getField("namePrefixSPT").set(f.receiver, row[0]); f.team.getField("colorSuffix").set(f.receiver, row[2]);
            String baseline = (String)f.team.getMethod("baseline", String.class).invoke(f.receiver, row[1]);
            assertEquals(row[3], baseline); String miss = f.format(row[1]); assertEquals(row[3], miss); assertSame(miss, f.format(row[1]));
        }
        assertEquals(cases.length, f.counters()[1]); assertEquals(true, f.noarg("active"));
        f.request(false); assertEquals(0, Integer.parseInt(f.metadata().getProperty("nameprepEntries"))); assertNotSame(f.format("A"), f.format("A"));
    }
    @Test void everyHitStillCallsGettersInOriginalOrderAndUsesCurrentMutation() throws Exception {
        Fixture f = new Fixture(true); f.request(true); Effects.trace.clear(); Effects.thrown = null;
        f.format("A"); String hit = f.format("A"); assertEquals("[A]", hit);
        assertEquals(Arrays.asList("prefix", "suffix", "prefix", "suffix"), Effects.trace);
        f.team.getField("namePrefixSPT").set(f.receiver, "<"); assertEquals("<A]", f.format("A"));
        f.team.getField("colorSuffix").set(f.receiver, ">"); assertEquals("<A>", f.format("A"));
        Effects.thrown = new IllegalStateException("original suffix failure");
        try { InvocationTargetException e = assertThrows(InvocationTargetException.class, () -> f.format("A")); assertSame(Effects.thrown, e.getCause()); }
        finally { Effects.thrown = null; Effects.trace.clear(); }
    }
    @Test void nullTeamPassthroughAndFreshOutwardObjectsRemainOriginal() throws Exception {
        Fixture f = new Fixture(false); f.request(true);
        Class<?> base = f.loadClass("net.minecraft.scoreboard.Team"); Method staticFormat = f.team.getMethod("formatPlayerName", base, String.class);
        String input = new String("A"); assertSame(input, staticFormat.invoke(null, null, input)); assertNull(staticFormat.invoke(null, null, null));
        Method display = f.team.getMethod("display", String.class); Text first = (Text)display.invoke(f.receiver, "A"); first.mutable = "changed";
        Text second = (Text)display.invoke(f.receiver, "A"); assertEquals("[A]", second.value); assertNull(second.mutable);
        assertNotSame(first, second); assertNotSame(first.style, second.style); assertNotSame(first.hover, second.hover); assertSame(first.value, second.value);
    }
    @Test void offThreadReentrantSubclassOversizedAndMissingProofFallbacksKeepOriginalText() throws Exception {
        Fixture f = new Fixture(false); f.request(true); f.format("A"); long hits = f.counters()[1];
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread worker = new Thread(() -> { try { f.invoke("frame", new Class<?>[]{Thread.class, Object.class}, Thread.currentThread(), new Object()); assertEquals("[A]", f.format("A")); } catch (Throwable e) { failure.set(e); } });
        worker.start(); worker.join(); assertNull(failure.get()); assertEquals(hits, f.counters()[1]); assertTrue(f.counters()[8] > 0);
        f.set("busy", true); try { assertEquals("[A]", f.format("A")); } finally { f.set("busy", false); }
        assertTrue(f.counters()[9] > 0); Object child = f.sub.getConstructor().newInstance(); Effects.trace.clear();
        assertEquals("PAS", f.format(child, "A")); assertEquals(Arrays.asList("prefix", "suffix"), Effects.trace); assertTrue(f.counters()[10] > 0);
        String large = String.join("", Collections.nCopies(2049, "x")); assertEquals("[" + large + "]", f.format(large)); assertTrue(f.counters()[6] > 0);
        f.invoke("evidence", new Class<?>[]{boolean.class, String.class}, false, null); assertEquals("[A]", f.format("A")); assertEquals(false, f.noarg("available"));
    }
    @Test void nestedSubclassCallsAndOriginalExceptionIdentityPreserveGetterEvaluation() throws Exception {
        Fixture f = new Fixture(false); f.request(true); Object child = f.sub.getConstructor().newInstance(); Effects.trace.clear();
        Effects.nested = () -> { try { assertEquals("[nested]", f.format("nested")); } catch (Exception e) { throw new AssertionError(e); } };
        assertEquals("PAS", f.format(child, "A")); assertEquals(Arrays.asList("prefix", "suffix"), Effects.trace);
        Effects.thrown = new IllegalArgumentException("suffix");
        try { InvocationTargetException e = assertThrows(InvocationTargetException.class, () -> f.format(child, "A")); assertSame(Effects.thrown, e.getCause()); }
        finally { Effects.thrown = null; Effects.nested = null; Effects.trace.clear(); }
    }
    @Test void lifecycleAndBenchmarkGuardRejectQuickOnOffClearEvidenceAndFallbackChanges() throws Exception {
        Fixture f = new Fixture(false); f.request(true); f.format("A"); f.format("A");
        long[] start = f.counters(); Class<?>[] stableArgs = {long[].class, boolean.class}; assertEquals(true, f.invoke("stable", stableArgs, start, true));
        f.noarg("clear"); assertEquals(false, f.invoke("stable", stableArgs, start, true)); assertEquals("0", f.metadata().getProperty("nameprepEntries"));
        f.format("A"); start = f.counters(); f.request(false); f.request(true); assertEquals(false, f.invoke("stable", stableArgs, start, true));
        f.format("A"); start = f.counters(); f.set("busy", true); f.format("A"); f.set("busy", false); assertEquals(false, f.invoke("stable", stableArgs, start, true));
        f.invoke("frame", new Class<?>[]{Thread.class, Object.class}, Thread.currentThread(), new Object()); assertEquals("0", f.metadata().getProperty("nameprepEntries"));
        f.request(false); start = f.counters(); f.request(true); f.request(false); assertEquals(false, f.invoke("stable", stableArgs, start, false));
        Properties p = new Properties(); f.invoke("exportDelta", new Class<?>[]{Properties.class, long[].class}, p, start);
        assertEquals("1", p.getProperty("nameprepGuardVersion")); assertEquals("512", p.getProperty("nameprepEntryLimit")); assertEquals("false", p.getProperty("nameprepCacheActive"));
    }
    @Test void wrongActualClassMarkerFailsClosedAndClearDoesNotReenableFailedGuard() throws Exception {
        Fixture f = new Fixture(false); f.set("marker", "missing"); f.request(true);
        assertEquals("[A]", f.format("A")); assertEquals("true", f.metadata().getProperty("nameprepFailed"));
        assertEquals("0", f.metadata().getProperty("nameprepEntries")); f.noarg("clear"); assertEquals(false, f.noarg("available"));
    }
}
