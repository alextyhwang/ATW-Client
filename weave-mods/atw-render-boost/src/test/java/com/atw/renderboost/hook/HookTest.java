package com.atw.renderboost.hook;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.lang.reflect.InvocationTargetException;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.*;
import static org.junit.jupiter.api.Assertions.*;

class HookTest implements Opcodes {
    static final String FONT = "net/minecraft/client/gui/FontRenderer";
    private static final net.weavemc.api.Hook.AssemblerConfig CFG = () -> {};

    private MethodNode fixture() throws IOException {
        MethodNode m = new MethodNode(ACC_PUBLIC, "renderDefaultChar", "(IZ)F", null, null);
        for (int slot = 3; slot <= 6; slot++) { m.instructions.add(new InsnNode(ICONST_0)); m.instructions.add(new VarInsnNode(ISTORE, slot)); }
        m.instructions.add(new LdcInsnNode(5.99f)); m.instructions.add(new VarInsnNode(FSTORE, 7));
        try (BufferedReader r = new BufferedReader(new InputStreamReader(getClass().getResourceAsStream("/glyph-primitive.txt"), StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                String[] tokens = line.split("\\s+"); String op = tokens[0];
                if (op.equals("iload") || op.equals("fload") || op.equals("aload"))
                    m.instructions.add(new VarInsnNode(op.equals("iload") ? ILOAD : op.equals("fload") ? FLOAD : ALOAD, Integer.parseInt(tokens[1])));
                else if (op.matches("[ifa]load_[0-9]+"))
                    m.instructions.add(new VarInsnNode(op.charAt(0) == 'i' ? ILOAD : op.charAt(0) == 'f' ? FLOAD : ALOAD, Integer.parseInt(op.substring(op.indexOf('_') + 1))));
                else if (op.equals("getfield")) {
                    String[] f = line.substring(line.indexOf("Field ") + 6).split(":");
                    m.instructions.add(new FieldInsnNode(GETFIELD, FONT, f[0], f[1]));
                } else if (op.equals("invokestatic")) {
                    String call = line.substring(line.indexOf("Method ") + 7); int dot = call.indexOf('.'), colon = call.indexOf(':');
                    m.instructions.add(new MethodInsnNode(INVOKESTATIC, call.substring(0, dot), call.substring(dot + 1, colon), call.substring(colon + 1), false));
                } else if (op.equals("ldc")) {
                    m.instructions.add(new LdcInsnNode(Float.parseFloat(line.substring(line.indexOf("float ") + 6).replace("f", ""))));
                } else {
                    switch (op) {
                        case "iconst_5": m.instructions.add(new InsnNode(ICONST_5)); break;
                        case "i2f": m.instructions.add(new InsnNode(I2F)); break;
                        case "fadd": m.instructions.add(new InsnNode(FADD)); break;
                        case "fsub": m.instructions.add(new InsnNode(FSUB)); break;
                        case "fdiv": m.instructions.add(new InsnNode(FDIV)); break;
                        case "fconst_0": m.instructions.add(new InsnNode(FCONST_0)); break;
                        case "fconst_1": m.instructions.add(new InsnNode(FCONST_1)); break;
                        default: throw new IllegalArgumentException(line);
                    }
                }
            }
        }
        m.instructions.add(new VarInsnNode(ILOAD, 6)); m.instructions.add(new InsnNode(I2F)); m.instructions.add(new InsnNode(FRETURN));
        m.maxLocals = 8; m.maxStack = 12;
        return m;
    }
    private ClassNode font(MethodNode method) {
        ClassNode c = new ClassNode(); c.name = FONT; c.superName = "java/lang/Object"; c.version = V1_8; c.access = ACC_PUBLIC;
        c.fields.add(new FieldNode(ACC_PRIVATE, "posX", "F", null, null));
        c.fields.add(new FieldNode(ACC_PRIVATE, "posY", "F", null, null)); c.methods.add(method);
        return c;
    }
    @Test void verifiesReferencePrimitiveAndPreservesReturnStack() throws Exception {
        MethodNode m = fixture(); ClassNode c = font(m); new FontHook().transform(c, CFG);
        assertTrue(HookSupport.installed(m));
        new Analyzer<>(new BasicVerifier()).analyze(c.name, m);
        ClassWriter w = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS); c.accept(w);
        assertTrue(w.toByteArray().length > 0);
        int count = m.instructions.size(); new FontHook().transform(c, CFG); assertEquals(count, m.instructions.size());
    }
    @Test void alteredGeometryOrSideEffectsRetainOriginalMethod() throws Exception {
        for (int mutation = 0; mutation < 4; mutation++) {
            MethodNode m = fixture();
            for (AbstractInsnNode n : m.instructions.toArray()) {
                if (mutation == 0 && n instanceof FieldInsnNode) { ((FieldInsnNode)n).name="differentPosition"; break; }
                if (mutation == 1 && n instanceof MethodInsnNode) {
                    m.instructions.insert(n, new MethodInsnNode(INVOKESTATIC, "foreign/Mod", "sideEffect", "()V", false)); break;
                }
                if (mutation == 2 && n instanceof LdcInsnNode && ((LdcInsnNode)n).cst.equals(128f)) {
                    ((LdcInsnNode)n).cst=256f; break;
                }
                if (mutation == 3 && n instanceof VarInsnNode && n.getOpcode() == ILOAD && ((VarInsnNode)n).var == 3) {
                    ((VarInsnNode)n).var=4; break;
                }
            }
            int count = m.instructions.size(); new FontHook().transform(font(m), CFG);
            assertFalse(HookSupport.installed(m)); assertEquals(count, m.instructions.size());
        }
    }
    @Test void externalJumpIntoPrimitiveIsRejected() throws Exception {
        MethodNode m = fixture(); LabelNode label = new LabelNode();
        for (AbstractInsnNode n : m.instructions.toArray()) if (n instanceof FieldInsnNode) { m.instructions.insertBefore(n,label); break; }
        m.instructions.insert(new JumpInsnNode(GOTO, label));
        new FontHook().transform(font(m), CFG); assertFalse(HookSupport.installed(m));
    }
    @Test void enclosingExceptionHandlerIsRejected() throws Exception {
        MethodNode m = fixture(); LabelNode start=new LabelNode(), end=new LabelNode(), handler=new LabelNode();
        m.instructions.insert(start); m.instructions.add(end); m.instructions.add(handler);
        m.instructions.add(new InsnNode(ATHROW)); m.tryCatchBlocks.add(new TryCatchBlockNode(start,end,handler,null));
        new FontHook().transform(font(m),CFG); assertFalse(HookSupport.installed(m));
    }
    @Test void lunarBatchedDisplayListPathIsDiagnosedAndLeftUntouched() throws Exception {
        // Reduced from PID 28600's renderDefaultChar: float locals and buffer writes,
        // inside Lunar-owned display-list recording, rather than an immediate GL primitive.
        MethodNode m = new MethodNode(ACC_PUBLIC, "renderDefaultChar", "(IZ)F", null, null);
        m.instructions.add(new InsnNode(ICONST_1));
        m.instructions.add(new IntInsnNode(SIPUSH, 4864));
        m.instructions.add(new MethodInsnNode(INVOKESTATIC, "org/lwjgl/opengl/GL11", "glNewList", "(II)V", false));
        m.instructions.add(new InsnNode(FCONST_0)); m.instructions.add(new VarInsnNode(FSTORE, 3));
        for (int vertex = 0; vertex < 4; vertex++) {
            m.instructions.add(new FieldInsnNode(GETSTATIC, FONT, "wr", "Lnet/minecraft/client/renderer/WorldRenderer;"));
            m.instructions.add(new MethodInsnNode(INVOKEVIRTUAL, "net/minecraft/client/renderer/WorldRenderer", "endVertex", "()V", false));
        }
        m.instructions.add(new VarInsnNode(FLOAD, 3)); m.instructions.add(new InsnNode(FRETURN));
        AbstractInsnNode[] original = m.instructions.toArray();
        ByteArrayOutputStream output = new ByteArrayOutputStream(); PrintStream previous = System.out;
        try (PrintStream capture = new PrintStream(output, true, "UTF-8")) {
            System.setOut(capture);
            new FontHook().transform(font(m), () -> fail("Batched glyph must not request frame computation"));
        } finally { System.setOut(previous); }
        assertArrayEquals(original, m.instructions.toArray());
        assertTrue(m.tryCatchBlocks.isEmpty()); assertFalse(HookSupport.installed(m));
        assertTrue(output.toString("UTF-8").contains("WorldRenderer batching with display-list recording"));
    }
    public static final class Probe {
        static int depth, enters, exits;
        public static void frameStart() { depth++; enters++; }
        public static void frameEnd() { depth--; exits++; }
    }
    @Test void scopeCleanupRunsOnReturnAndImplicitException() throws Exception {
        ClassNode c = new ClassNode(); c.name="fixture/Game"; c.superName="java/lang/Object"; c.version=V1_8; c.access=ACC_PUBLIC;
        MethodNode m = new MethodNode(ACC_PUBLIC | ACC_STATIC, "runGameLoop", "()V", null, null);
        m.instructions.add(new InsnNode(ICONST_1)); m.instructions.add(new InsnNode(ICONST_0));
        m.instructions.add(new InsnNode(IDIV)); m.instructions.add(new InsnNode(POP)); m.instructions.add(new InsnNode(RETURN));
        c.methods.add(m); new FrameHook().transform(c, CFG);
        for (AbstractInsnNode n : m.instructions) if (n instanceof MethodInsnNode)
            ((MethodInsnNode)n).owner = Type.getInternalName(Probe.class);
        ClassWriter w = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS); c.accept(w);
        byte[] bytes=w.toByteArray();
        Class<?> game = new ClassLoader(getClass().getClassLoader()) { Class<?> define() { return defineClass("fixture.Game", bytes, 0, bytes.length); } }.define();
        Probe.depth=Probe.enters=Probe.exits=0;
        InvocationTargetException thrown = assertThrows(InvocationTargetException.class, () -> game.getMethod("runGameLoop").invoke(null));
        assertInstanceOf(ArithmeticException.class, thrown.getCause());
        assertEquals(0, Probe.depth); assertEquals(1, Probe.enters); assertEquals(1, Probe.exits);
    }
    @Test void scopeCleanupRunsExactlyOnceOnNormalReturn() throws Exception {
        ClassNode c=new ClassNode(); c.name="fixture/NormalGame"; c.superName="java/lang/Object"; c.version=V1_8; c.access=ACC_PUBLIC;
        MethodNode m=new MethodNode(ACC_PUBLIC | ACC_STATIC,"runGameLoop","()V",null,null);
        m.instructions.add(new InsnNode(RETURN)); c.methods.add(m); new FrameHook().transform(c,CFG);
        for (AbstractInsnNode n:m.instructions) if(n instanceof MethodInsnNode) ((MethodInsnNode)n).owner=Type.getInternalName(Probe.class);
        ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS); c.accept(w); byte[] bytes=w.toByteArray();
        Class<?> game=new ClassLoader(getClass().getClassLoader()) { Class<?> define() { return defineClass("fixture.NormalGame",bytes,0,bytes.length); } }.define();
        Probe.depth=Probe.enters=Probe.exits=0; game.getMethod("runGameLoop").invoke(null);
        assertEquals(0,Probe.depth); assertEquals(1,Probe.enters); assertEquals(1,Probe.exits);
    }
}
