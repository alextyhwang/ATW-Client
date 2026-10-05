package com.atw.renderboost.hook;

import com.atw.renderboost.FrameErrorPolicy;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.*;
import static org.junit.jupiter.api.Assertions.*;
import static com.atw.renderboost.FrameErrorPolicyTest.*;

class FrameErrorHookTest implements Opcodes {
    private static final String GAME = FrameErrorPattern.GAME;
    private static final String LOGGER = "org/apache/logging/log4j/Logger";
    private static final String BUILDER = "java/lang/StringBuilder";

    static MethodNode diagnostic() {
        MethodNode m = new MethodNode(ACC_PUBLIC, "checkGLError", FrameErrorPattern.DESC, null, null);
        LabelNode poll = new LabelNode(), end = new LabelNode();
        m.instructions.add(new VarInsnNode(ALOAD, 0));
        m.instructions.add(new FieldInsnNode(GETFIELD, GAME, "enableGLErrorChecking", "Z"));
        m.instructions.add(new JumpInsnNode(IFNE, poll)); m.instructions.add(new InsnNode(RETURN)); m.instructions.add(poll);
        call(m, INVOKESTATIC, "org/lwjgl/opengl/GL11", "glGetError", "()I");
        m.instructions.add(new VarInsnNode(ISTORE, 2)); m.instructions.add(new VarInsnNode(ILOAD, 2));
        m.instructions.add(new JumpInsnNode(IFEQ, end)); m.instructions.add(new VarInsnNode(ILOAD, 2));
        call(m, INVOKESTATIC, "org/lwjgl/util/glu/GLU", "gluErrorString", "(I)Ljava/lang/String;");
        m.instructions.add(new VarInsnNode(ASTORE, 3));
        logger(m); m.instructions.add(new LdcInsnNode("########## GL ERROR ##########")); log(m);
        logger(m); builder(m); m.instructions.add(new LdcInsnNode("@ ")); append(m, "Ljava/lang/String;");
        m.instructions.add(new VarInsnNode(ALOAD, 1)); append(m, "Ljava/lang/String;"); stringify(m); log(m);
        logger(m); builder(m); m.instructions.add(new VarInsnNode(ILOAD, 2)); append(m, "I");
        m.instructions.add(new LdcInsnNode(": ")); append(m, "Ljava/lang/String;");
        m.instructions.add(new VarInsnNode(ALOAD, 3)); append(m, "Ljava/lang/String;"); stringify(m); log(m);
        m.instructions.add(end); m.instructions.add(new InsnNode(RETURN)); m.maxStack = 4; m.maxLocals = 4;
        return m;
    }
    private static void call(MethodNode m, int opcode, String owner, String name, String desc) {
        m.instructions.add(new MethodInsnNode(opcode, owner, name, desc, opcode == INVOKEINTERFACE));
    }
    private static Object call(Class<?> policy, String method) throws Exception {
        return com.atw.renderboost.FrameErrorPolicyTest.call(policy, method);
    }
    private static void logger(MethodNode m) { m.instructions.add(new FieldInsnNode(GETSTATIC, GAME, "logger", "L" + LOGGER + ";")); }
    private static void log(MethodNode m) { call(m, INVOKEINTERFACE, LOGGER, "error", "(Ljava/lang/String;)V"); }
    private static void builder(MethodNode m) {
        m.instructions.add(new TypeInsnNode(NEW, BUILDER)); m.instructions.add(new InsnNode(DUP));
        call(m, INVOKESPECIAL, BUILDER, "<init>", "()V");
    }
    private static void append(MethodNode m, String arg) { call(m, INVOKEVIRTUAL, BUILDER, "append", "(" + arg + ")L" + BUILDER + ";"); }
    private static void stringify(MethodNode m) { call(m, INVOKEVIRTUAL, BUILDER, "toString", "()Ljava/lang/String;"); }
    private static void check(MethodNode m, String label) {
        m.instructions.add(new VarInsnNode(ALOAD, 0)); m.instructions.add(new LdcInsnNode(label));
        call(m, INVOKESPECIAL, GAME, "checkGLError", FrameErrorPattern.DESC);
    }
    private static ClassNode fixture() {
        ClassNode c = new ClassNode(); c.name = GAME; c.superName = "java/lang/Object"; c.version = V1_8; c.access = ACC_PUBLIC;
        MethodNode frame = new MethodNode(ACC_PUBLIC, "runGameLoop", "()V", null, null);
        check(frame, "Pre render"); frame.instructions.add(new InsnNode(NOP)); check(frame, "Post render");
        frame.instructions.add(new InsnNode(RETURN)); frame.maxLocals = 1; frame.maxStack = 2;
        c.methods.add(frame); c.methods.add(diagnostic());
        MethodNode startup = new MethodNode(ACC_PUBLIC, "startGame", "()V", null, null);
        check(startup, "Startup"); startup.instructions.add(new InsnNode(RETURN)); c.methods.add(startup);
        return c;
    }
    private static boolean installed(ClassNode c) {
        for (AbstractInsnNode n : c.methods.get(0).instructions)
            if (n instanceof MethodInsnNode && FrameErrorHook.POLICY.equals(((MethodInsnNode)n).owner)) return true;
        return false;
    }
    private static void rejected(ClassNode c) {
        List<AbstractInsnNode[]> original = new ArrayList<>();
        for (MethodNode m : c.methods) original.add(m.instructions.toArray());
        new FrameErrorHook().transform(c, () -> fail("Rejected class must not request frames"));
        assertFalse(installed(c));
        for (int i = 0; i < c.methods.size(); i++) assertArrayEquals(original.get(i), c.methods.get(i).instructions.toArray());
    }
    @Test void diagnosticFixtureMatchesCapturedAllowlist() {
        assertEquals(FrameErrorPattern.DIAGNOSTIC_HASH, FrameErrorPattern.diagnosticHash(diagnostic()));
    }
    @Test void exactPairGetsTwoGuardsAndObserverPreservingOriginalInstructionsAndStartup() throws Exception {
        ClassNode c = fixture(); MethodNode frame = c.methods.get(0);
        AbstractInsnNode[] diagnostic = c.methods.get(1).instructions.toArray(), startup = c.methods.get(2).instructions.toArray();
        AbstractInsnNode[] original = frame.instructions.toArray();
        int[] frames = {0}; new FrameErrorHook().transform(c, () -> frames[0]++);
        assertTrue(installed(c)); assertEquals(1, frames[0]); assertEquals(original.length + 6, frame.instructions.size());
        assertEquals(diagnostic.length + 2, c.methods.get(1).instructions.size());
        assertArrayEquals(startup, c.methods.get(2).instructions.toArray());
        for (AbstractInsnNode n : original) assertTrue(frame.instructions.contains(n));
        for (AbstractInsnNode n : diagnostic) assertTrue(c.methods.get(1).instructions.contains(n));
        assertObserver(c.methods.get(1));
        new Analyzer<>(new BasicVerifier()).analyze(c.name, frame);
        new Analyzer<>(new BasicVerifier()).analyze(c.name, c.methods.get(1));
        int size = frame.instructions.size(), diagnosticSize = c.methods.get(1).instructions.size();
        new FrameErrorHook().transform(c, () -> fail("Duplicate hook"));
        assertEquals(size, frame.instructions.size()); assertEquals(diagnosticSize, c.methods.get(1).instructions.size());
    }
    private static void assertObserver(MethodNode diagnostic) {
        int polls = 0, observers = 0;
        for (AbstractInsnNode n : diagnostic.instructions) if (n instanceof MethodInsnNode) {
            MethodInsnNode c = (MethodInsnNode)n;
            if (c.name.equals("glGetError")) {
                polls++;
                assertEquals(DUP, n.getNext().getOpcode());
                MethodInsnNode observer = (MethodInsnNode)n.getNext().getNext();
                assertEquals(INVOKESTATIC, observer.getOpcode()); assertEquals(FrameErrorHook.POLICY, observer.owner);
                assertEquals("observeError", observer.name); assertEquals("(I)V", observer.desc);
                assertEquals(ISTORE, observer.getNext().getOpcode());
            }
            if (c.owner.equals(FrameErrorHook.POLICY) && c.name.equals("observeError")) observers++;
        }
        assertEquals(1, polls); assertEquals(1, observers);
    }
    @Test void missingDuplicateReversedAndExtraChecksFailClosed() {
        for (int kind = 0; kind < 5; kind++) {
            ClassNode c = fixture(); MethodNode f = c.methods.get(0);
            if (kind == 0) f.instructions.clear();
            if (kind == 1) for (AbstractInsnNode n : f.instructions) if (n instanceof LdcInsnNode) ((LdcInsnNode)n).cst = "Pre render";
            if (kind == 2) for (AbstractInsnNode n : f.instructions) if (n instanceof LdcInsnNode) ((LdcInsnNode)n).cst = "Pre render".equals(((LdcInsnNode)n).cst) ? "Post render" : "Pre render";
            if (kind == 3) check(f, "Startup");
            if (kind == 4) c.methods.remove(1);
            rejected(c);
        }
    }
    @Test void changedOwnerDescriptorOpcodeLabelAndReceiverFailClosed() {
        for (int kind = 0; kind < 7; kind++) {
            ClassNode c = fixture(); MethodNode f = c.methods.get(0);
            MethodInsnNode call = (MethodInsnNode) f.instructions.get(2);
            if (kind == 0) call.owner = "foreign/Mod";
            if (kind == 1) call.desc = "(Ljava/lang/Object;)V";
            if (kind == 2) call.setOpcode(INVOKEVIRTUAL);
            if (kind == 3) ((LdcInsnNode)f.instructions.get(1)).cst = "Pre Render";
            if (kind == 4) ((VarInsnNode)f.instructions.get(0)).var = 1;
            if (kind == 5) f.instructions.insertBefore(call, new LabelNode());
            if (kind == 6) call.itf = true;
            rejected(c);
        }
    }
    @Test void mutatedDiagnosticSideEffectsAndBranchDestinationsFailClosed() {
        for (int kind = 0; kind < 4; kind++) {
            ClassNode c = fixture(); MethodNode d = c.methods.get(1);
            if (kind == 0) d.instructions.insert(new InsnNode(NOP));
            if (kind == 1) for (AbstractInsnNode n : d.instructions) if (n instanceof MethodInsnNode && ((MethodInsnNode)n).name.equals("glGetError")) ((MethodInsnNode)n).name = "glFinish";
            if (kind == 2) for (AbstractInsnNode n : d.instructions) if (n instanceof JumpInsnNode) { ((JumpInsnNode)n).label = new LabelNode(); break; }
            if (kind == 3) d.access |= ACC_STATIC;
            rejected(c);
        }
    }
    @Test void bypassLoopsIncomingBranchesAndEarlyReturnFailClosed() {
        for (int kind = 0; kind < 4; kind++) {
            ClassNode c = fixture(); MethodNode f = c.methods.get(0); AbstractInsnNode middle = f.instructions.get(3);
            LabelNode target = new LabelNode();
            if (kind == 0) { f.instructions.insertBefore(f.instructions.getLast(), target); f.instructions.insert(middle, new JumpInsnNode(GOTO, target)); }
            if (kind == 1) { f.instructions.insertBefore(middle, target); f.instructions.insert(middle, new JumpInsnNode(GOTO, target)); }
            if (kind == 2) { f.instructions.insertBefore(middle, target); f.instructions.insert(new JumpInsnNode(GOTO, target)); }
            if (kind == 3) f.instructions.insert(middle, new InsnNode(RETURN));
            rejected(c);
        }
    }
    @Test void unsupportedMethodsSwitchesAndExceptionReentryFailClosed() {
        for (int kind = 0; kind < 8; kind++) {
            ClassNode c = fixture(); MethodNode f = c.methods.get(0);
            if (kind < 3) f.access |= new int[]{ACC_STATIC, ACC_NATIVE, ACC_ABSTRACT}[kind];
            if (kind == 3) c.methods.add(new MethodNode(ACC_PUBLIC, "runGameLoop", "()V", null, null));
            if (kind == 4) c.methods.add(diagnostic());
            if (kind >= 5) {
                LabelNode target = new LabelNode(); f.instructions.insertBefore(f.instructions.get(3), target);
                if (kind == 5) f.instructions.insert(target, new TableSwitchInsnNode(0, 0, target, target));
                if (kind == 6) f.instructions.insert(target, new LookupSwitchInsnNode(target, new int[]{0}, new LabelNode[]{target}));
                if (kind == 7) {
                    LabelNode start = new LabelNode(), end = new LabelNode(); f.instructions.insert(start); f.instructions.add(end);
                    f.tryCatchBlocks.add(new TryCatchBlockNode(start, end, target, null));
                }
            }
            rejected(c);
        }
    }
    @Test void executableCadenceAndErrorRestoreBothCallsAndPreserveOriginalLogging() throws Exception {
        AtomicLong clock = new AtomicLong(0); Class<?> policy = freshPolicy(clock::get);
        ClassNode c = fixture(); new FrameErrorHook().transform(c, () -> {});
        Class<?> game = executable(c, policy); Object instance = game.getConstructor().newInstance();
        Probe.reset(); enable(policy, true);
        game.getMethod("runGameLoop").invoke(instance); assertEquals(1, Probe.polls);
        clock.set(249_999_999L); game.getMethod("runGameLoop").invoke(instance); assertEquals(1, Probe.polls);
        clock.set(250_000_000L); game.getMethod("runGameLoop").invoke(instance); assertEquals(2, Probe.polls);
        Probe.nextError = 1282;
        clock.set(499_999_999L); game.getMethod("runGameLoop").invoke(instance); assertEquals(2, Probe.polls);
        clock.set(500_000_000L); game.getMethod("runGameLoop").invoke(instance); assertEquals(3, Probe.polls);
        assertEquals(true, call(policy, "errorFallback")); assertEquals(1282, call(policy, "firstError"));
        assertEquals(Arrays.asList("########## GL ERROR ##########", "@ Post render", "1282: test-error-1282"), Probe.logs);
        game.getMethod("runGameLoop").invoke(instance); assertEquals(5, Probe.polls);
        enable(policy, false); enable(policy, true);
        Probe.nextError = 1280;
        game.getMethod("runGameLoop").invoke(instance); assertEquals(7, Probe.polls);
        assertEquals(1282, call(policy, "firstError"));
        assertEquals(Arrays.asList("########## GL ERROR ##########", "@ Pre render", "1280: test-error-1280"), Probe.logs.subList(3, 6));
        assertEquals(5L, call(policy, "skipped")); assertEquals(2L, call(policy, "postSkipped"));
    }
    @Test void startupChecksAndDiagnosticSettingRemainOriginalAndStartupErrorLocksSampling() throws Exception {
        Class<?> policy = freshPolicy(() -> 0L); ClassNode c = fixture();
        new FrameErrorHook().transform(c, () -> {});
        Class<?> game = executable(c, policy); Object instance = game.getConstructor().newInstance();
        Probe.reset(); enable(policy, true);
        game.getField("enableGLErrorChecking").setBoolean(instance, false);
        Probe.nextError = 1281;
        game.getMethod("startGame").invoke(instance);
        assertEquals(0, Probe.polls); assertEquals(false, call(policy, "errorFallback"));
        game.getField("enableGLErrorChecking").setBoolean(instance, true);
        game.getMethod("startGame").invoke(instance);
        assertEquals(1, Probe.polls); assertEquals(true, call(policy, "errorFallback"));
        assertEquals(Arrays.asList("########## GL ERROR ##########", "@ Startup", "1281: test-error-1281"), Probe.logs);
        game.getMethod("runGameLoop").invoke(instance); assertEquals(3, Probe.polls);
        assertEquals(0L, call(policy, "skipped")); assertEquals(0L, call(policy, "postSkipped"));
    }
    @Test void disabledAndUnsupportedFallbackAndImmediateOffExecuteOriginalChecks() throws Exception {
        for (boolean supported : new boolean[]{true, false}) {
            Class<?> policy = freshPolicy(() -> 0L);
            ClassNode c = fixture();
            if (!supported) ((LdcInsnNode)c.methods.get(0).instructions.get(1)).cst = "Unknown pre";
            new FrameErrorHook().transform(c, () -> {});
            Class<?> game = executable(c, policy);
            Object instance = game.getConstructor().newInstance();
            Probe.reset();
            game.getMethod("runGameLoop").invoke(instance);
            assertEquals(2, game.getField("checks").getInt(null));
            enable(policy, true); game.getMethod("runGameLoop").invoke(instance);
            assertEquals(supported ? 3 : 4, game.getField("checks").getInt(null));
            enable(policy, false); game.getMethod("runGameLoop").invoke(instance);
            assertEquals(supported ? 5 : 6, game.getField("checks").getInt(null));
            assertEquals(3, game.getField("posts").getInt(null));
            assertEquals(supported ? 5 : 6, Probe.polls);
            assertEquals(supported, call(policy, "observed"));
            assertTrue(Probe.logs.isEmpty());
        }
    }
    public static final class Probe {
        static int polls, nextError;
        static final List<String> logs = new ArrayList<>();
        static void reset() { polls = nextError = 0; logs.clear(); }
        public static int glGetError() { polls++; int error = nextError; nextError = 0; return error; }
        public static String gluErrorString(int error) { return "test-error-" + error; }
        public static void error(String text) { logs.add(text); }
    }
    private static Class<?> executable(ClassNode c, Class<?> policy) {
        c.fields.add(new FieldNode(ACC_PUBLIC | ACC_STATIC, "checks", "I", null, null));
        c.fields.add(new FieldNode(ACC_PUBLIC | ACC_STATIC, "posts", "I", null, null));
        c.fields.add(new FieldNode(ACC_PUBLIC, "enableGLErrorChecking", "Z", null, null));
        MethodNode d = c.methods.get(1);
        // Substitute only external GL/logger dependencies; execute the diagnostic's original branches,
        // local stores, string formatting and the injected observer without a graphics context.
        for (AbstractInsnNode n : d.instructions.toArray()) {
            if (n instanceof FieldInsnNode && ((FieldInsnNode)n).name.equals("logger")) d.instructions.remove(n);
            if (n instanceof MethodInsnNode) {
                MethodInsnNode call = (MethodInsnNode)n;
                if (call.owner.equals("org/lwjgl/opengl/GL11") || call.owner.equals("org/lwjgl/util/glu/GLU"))
                    call.owner = Type.getInternalName(Probe.class);
                if (call.owner.equals(LOGGER)) {
                    call.owner = Type.getInternalName(Probe.class); call.setOpcode(INVOKESTATIC); call.itf = false;
                }
            }
        }
        MethodNode counting = new MethodNode(); increment(counting, "checks");
        LabelNode done = new LabelNode(); counting.instructions.add(new LdcInsnNode("Post render")); counting.instructions.add(new VarInsnNode(ALOAD, 1));
        call(counting, INVOKEVIRTUAL, "java/lang/String", "equals", "(Ljava/lang/Object;)Z"); counting.instructions.add(new JumpInsnNode(IFEQ, done));
        increment(counting, "posts"); counting.instructions.add(done);
        d.instructions.insert(counting.instructions);
        MethodNode ctor = new MethodNode(ACC_PUBLIC, "<init>", "()V", null, null);
        ctor.instructions.add(new VarInsnNode(ALOAD, 0)); call(ctor, INVOKESPECIAL, "java/lang/Object", "<init>", "()V");
        ctor.instructions.add(new VarInsnNode(ALOAD, 0)); ctor.instructions.add(new InsnNode(ICONST_1));
        ctor.instructions.add(new FieldInsnNode(PUTFIELD, GAME, "enableGLErrorChecking", "Z"));
        ctor.instructions.add(new InsnNode(RETURN)); c.methods.add(ctor);
        ClassWriter w = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS); c.accept(w); byte[] bytes = w.toByteArray();
        return new ClassLoader(FrameErrorHookTest.class.getClassLoader()) {
            @Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (name.equals(FrameErrorPolicy.class.getName())) return policy;
                return super.loadClass(name, resolve);
            }
            Class<?> define() { return defineClass(GAME.replace('/', '.'), bytes, 0, bytes.length); }
        }.define();
    }
    private static void increment(MethodNode m, String field) {
        m.instructions.add(new FieldInsnNode(GETSTATIC, GAME, field, "I")); m.instructions.add(new InsnNode(ICONST_1));
        m.instructions.add(new InsnNode(IADD)); m.instructions.add(new FieldInsnNode(PUTSTATIC, GAME, field, "I"));
    }
    @Test void actualCapturedLunarFrameAcceptsGuardWithoutChangingAnyOriginalInstruction() throws Exception {
        Path capture = Paths.get("../../upgrade-work/renderboost-live/capture/net_minecraft_client_Minecraft.live.class");
        Assumptions.assumeTrue(Files.isRegularFile(capture), "Optional local diagnostic capture is absent");
        ClassNode c = new ClassNode(); new ClassReader(Files.readAllBytes(capture)).accept(c, 0);
        MethodNode frame = c.methods.stream().filter(m -> m.name.equals("runGameLoop") && m.desc.equals("()V")).findFirst().get();
        MethodNode diagnostic = c.methods.stream().filter(m -> m.name.equals("checkGLError") && m.desc.equals(FrameErrorPattern.DESC)).findFirst().get();
        assertEquals(FrameErrorPattern.DIAGNOSTIC_HASH, FrameErrorPattern.diagnosticHash(diagnostic), "Captured diagnostic hash");
        Map<MethodNode, AbstractInsnNode[]> originals = new IdentityHashMap<>();
        for (MethodNode m : c.methods) originals.put(m, m.instructions.toArray());
        int[] frames = {0}; new FrameErrorHook().transform(c, () -> frames[0]++); assertEquals(1, frames[0]);
        for (MethodNode m : c.methods) {
            if (m != frame && m != diagnostic) assertArrayEquals(originals.get(m), m.instructions.toArray());
            else {
                assertEquals(originals.get(m).length + (m == frame ? 6 : 2), m.instructions.size());
                for (AbstractInsnNode n : originals.get(m)) assertTrue(m.instructions.contains(n));
            }
        }
        assertObserver(diagnostic);
        new Analyzer<>(new BasicVerifier()).analyze(c.name, frame);
        new Analyzer<>(new BasicVerifier()).analyze(c.name, diagnostic);
    }
}
