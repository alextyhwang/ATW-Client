package com.atw.renderboost.hook;

import java.io.*;
import java.security.MessageDigest;
import java.util.*;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.*;
import static org.junit.jupiter.api.Assertions.*;

public class TerrainHookTest implements Opcodes {
    static final String LIST_FILE = "net_minecraft_client_renderer_VboRenderList.loader2.live.class.dat";
    static final String VB_FILE = "net_minecraft_client_renderer_vertex_VertexBuffer.loader1.live.class.dat";
    static final String RG_FILE = "net_minecraft_client_renderer_RenderGlobal.loader3.live.class.dat";
    static final String GSM_FILE = "net_minecraft_client_renderer_GlStateManager.loader4.live.class.dat";
    static final String OWNER_FILE = "com_moonsworth_lunar_OCCOIRIHCIHOCIOCICROCRHRHRIHCO_HHCCHRHORCRIHRIIHRIICIHHCOOCHR_CHIRCOROIOHCCIIRRCCHOIHHHIOIOH_CHIRCOROIOHCCIIRRCCHOIHHHIOIOH_CCIRCHROCOHOCHICRORRCIRCRRIOHR.loader1.live.class.dat";
    static ClassNode captured(String file) throws Exception {
        try (InputStream in = TerrainHookTest.class.getResourceAsStream("/terrain/" + file)) {
            assertNotNull(in, "Required actual captured fixture must be present");
            ClassNode c = new ClassNode(); new ClassReader(in).accept(c, 0); return c;
        }
    }
    static MethodNode method(ClassNode c, String name) {
        return c.methods.stream().filter(m -> m.name.equals(name)).findFirst().get();
    }
    static void rejected(ClassNode c) {
        Map<MethodNode, AbstractInsnNode[]> original = new IdentityHashMap<>();
        for (MethodNode m : c.methods) original.put(m, m.instructions.toArray());
        List<String> interfaces = new ArrayList<>(c.interfaces);
        new TerrainHook().transform(c, () -> fail("Rejected bytecode must not request frames"));
        assertEquals(interfaces, c.interfaces); assertEquals(original.size(), c.methods.size());
        for (MethodNode m : c.methods) assertArrayEquals(original.get(m), m.instructions.toArray());
    }
    @Test @Tag("private-terrain-capture") void fourCoreFixturesHaveTheParentsExactCapturedHashes() throws Exception {
        String[] files = {LIST_FILE, VB_FILE, RG_FILE, GSM_FILE};
        String[] hashes = {"433e8c41fc295cf98574880fe3f93803f1c7ac844af480cab0c0d1ee84af893c",
            "3ccc4cc0df4ee2b4b3bd7764ce5d76a5c870dd578dde48694b3f9f7950161551",
            "27b5e5d3dbe137e7287c62f52cd31a4621688448c3f187798878eaeee48a5584",
            "99e5aca1b56f31e78e1ac2b6a10b6061859e8937f94adc9d244743385e05775f"};
        for (int i = 0; i < files.length; i++) try (InputStream in = getClass().getResourceAsStream("/terrain/" + files[i])) {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream(); byte[] buffer = new byte[4096]; int n;
            while ((n = in.read(buffer)) != -1) bytes.write(buffer, 0, n);
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray());
            StringBuilder hex = new StringBuilder(); for (byte b : hash) hex.append(String.format("%02x", b & 255));
            assertEquals(hashes[i], hex.toString()); assertTrue(TerrainEvidence.capturedClassMatches(captured(files[i])));
        }
    }
    @Test @Tag("private-terrain-capture") void allTenActualSupportCapturesPassAndRemainInsufficientToProveLiveActivation() throws Exception {
        String[] files = {"net_minecraft_client_renderer_chunk_RenderChunk.loader1.live.class.dat",
            "net_minecraft_client_renderer_ChunkRenderContainer.loader2.live.class.dat",
            "net_minecraft_client_renderer_OpenGlHelper.loader3.live.class.dat",
            "org_lwjgl_opengl_ContextGL.loader4.live.class.dat", "org_lwjgl_opengl_GLContext.loader5.live.class.dat",
            "org_lwjgl_opengl_Display.loader6.live.class.dat", "net_optifine_Config.loader7.live.class.dat",
            "net_minecraft_client_renderer_vertex_VertexFormatElement.loader8.live.class.dat",
            "net_minecraft_client_renderer_vertex_VertexFormat.loader9.live.class.dat",
            "net_minecraft_client_renderer_vertex_DefaultVertexFormats.loader10.live.class.dat"};
        for (String file : files) assertTrue(TerrainEvidence.capturedClassMatches(captured(file)), file);
    }
    @Test @Tag("private-terrain-capture") void actualLunarLoopChangesOnlyTheAdmittedStandaloneSeamAndAddsCleanupCounters() throws Exception {
        ClassNode c = captured(LIST_FILE); MethodNode m = method(c, "renderChunkLayer");
        AbstractInsnNode[] original = m.instructions.toArray(); int[] frames = {0};
        MethodNode pointers = method(c, "setupArrayPointers"); AbstractInsnNode[] originalPointers = pointers.instructions.toArray();
        new TerrainHook().transform(c, () -> frames[0]++); assertEquals(1, frames[0]);
        assertTrue(c.interfaces.contains(TerrainHook.ACCESS)); assertArrayEquals(originalPointers, pointers.instructions.toArray());
        int removed = 0, draws = 0;
        for (AbstractInsnNode n : original) {
            if (!m.instructions.contains(n)) {
                removed++;
                assertTrue(n instanceof VarInsnNode || n instanceof MethodInsnNode);
                if (n instanceof MethodInsnNode) assertTrue(((MethodInsnNode)n).name.equals("bindBuffer") || ((MethodInsnNode)n).name.equals("setupArrayPointers"));
            } else if (n instanceof MethodInsnNode && ((MethodInsnNode)n).name.equals("drawArrays")) draws++;
        }
        assertEquals(4, removed); assertEquals(2, draws, "Both region and standalone draws are original nodes");
        assertEquals(1, m.tryCatchBlocks.size());
        new Analyzer<>(new BasicVerifier()).analyze(c.name, m);
        new Analyzer<>(new BasicVerifier()).analyze(c.name, method(c, "atwboost$originalPointers"));
        int size = m.instructions.size(); new TerrainHook().transform(c, () -> fail("duplicate")); assertEquals(size, m.instructions.size());
    }
    @Test @Tag("private-terrain-capture") void currentDrawAndOuterArrayTeardownRemainOriginalInstructions() throws Exception {
        ClassNode buffer = captured(VB_FILE); MethodNode draw = method(buffer, "drawArrays");
        AbstractInsnNode[] originalDraw = draw.instructions.toArray();
        new TerrainHook().transform(buffer, () -> fail("Straight-line invalidations do not require frames"));
        assertArrayEquals(originalDraw, draw.instructions.toArray());
        for (String name : Arrays.asList("bufferData", "deleteGlBuffers", "setVboRegion")) {
            MethodNode m = method(buffer, name); assertEquals(ALOAD, m.instructions.getFirst().getOpcode());
            assertEquals("bufferInvalidated", ((MethodInsnNode)m.instructions.getFirst().getNext()).name);
            new Analyzer<>(new BasicVerifier()).analyze(buffer.name, m);
        }
        ClassNode global = captured(RG_FILE);
        MethodNode layer = TerrainHook.method(global, "renderBlockLayer", "(Lnet/minecraft/util/EnumWorldBlockLayer;)V");
        AbstractInsnNode[] original = layer.instructions.toArray();
        new TerrainHook().transform(global, () -> {});
        for (AbstractInsnNode n : original) assertTrue(layer.instructions.contains(n));
        new Analyzer<>(new BasicVerifier()).analyze(global.name, layer);
    }
    @Test @Tag("private-terrain-capture") void rejectsChangedPointerConstantsCallsAndSignedLightmapType() throws Exception {
        for (String mutation : Arrays.asList("stride", "signed", "offset", "owner", "call")) {
            ClassNode c = captured(LIST_FILE); MethodNode m = method(c, "setupArrayPointers");
            boolean changed = false;
            for (AbstractInsnNode n : m.instructions) {
                if (mutation.equals("stride") && n instanceof IntInsnNode && ((IntInsnNode)n).operand == 28) { ((IntInsnNode)n).operand = 32; changed = true; break; }
                if (mutation.equals("signed") && n instanceof IntInsnNode && ((IntInsnNode)n).operand == 5122) { ((IntInsnNode)n).operand = 5123; changed = true; break; }
                if (mutation.equals("offset") && n instanceof LdcInsnNode && Long.valueOf(24).equals(((LdcInsnNode)n).cst)) { ((LdcInsnNode)n).cst = 20L; changed = true; break; }
                if (n instanceof MethodInsnNode && ((MethodInsnNode)n).name.equals("glVertexPointer")) {
                    if (mutation.equals("owner")) { ((MethodInsnNode)n).owner = "unknown/Wrapper"; changed = true; break; }
                    if (mutation.equals("call")) { m.instructions.insert(n, new MethodInsnNode(INVOKESTATIC, "unknown/Callback", "visit", "()V", false)); changed = true; break; }
                }
            }
            assertTrue(changed); rejected(c);
        }
    }
    @Test @Tag("private-terrain-capture") void rejectsChangedRegionsShadersLifecycleCountControlFlowAndHierarchy() throws Exception {
        ClassNode c = captured(LIST_FILE); method(c, "renderChunkLayer").instructions.insert(new InsnNode(NOP)); rejected(c);
        c = captured(LIST_FILE); c.superName = "unknown/Container"; rejected(c);
        c = captured(VB_FILE); for (AbstractInsnNode n : method(c, "drawArrays").instructions)
            if (n instanceof FieldInsnNode && ((FieldInsnNode)n).name.equals("count")) { ((FieldInsnNode)n).name = "cachedCount"; break; }
        rejected(c);
        c = captured(VB_FILE); method(c, "deleteGlBuffers").instructions.insert(new InsnNode(NOP)); rejected(c);
        c = captured(RG_FILE); method(c, "loadRenderers").instructions.insert(new InsnNode(NOP)); rejected(c);
        c = captured("net_optifine_Config.loader7.live.class.dat"); method(c, "isRenderRegions").instructions.insert(new InsnNode(NOP)); rejected(c);
        c = captured("org_lwjgl_opengl_ContextGL.loader4.live.class.dat"); method(c, "destroy").instructions.insert(new InsnNode(NOP)); rejected(c);
    }
    public static class Region {
        public void drawArrays(int mode, Object range) { RecordedGl.values.add("region:" + mode); }
    }
    public static class RecordedGl {
        static final List<String> values = new ArrayList<>();
        public static void glDrawArrays(int mode, int first, int count) { values.add(mode + ":" + first + ":" + count); }
    }
    @Test @Tag("private-terrain-capture") void executableActualCapturedDrawReadsCurrentCountModeAndRegionEveryInvocation() throws Exception {
        ClassNode captured = captured(VB_FILE); MethodNode original = method(captured, "drawArrays");
        ClassNode harness = new ClassNode(); harness.version = V1_8; harness.access = ACC_PUBLIC;
        harness.name = "com/atw/renderboost/terrain/ActualDrawHarness"; harness.superName = "java/lang/Object";
        String region = Type.getInternalName(Region.class), gl = Type.getInternalName(RecordedGl.class);
        harness.fields.add(new FieldNode(ACC_PUBLIC, "count", "I", null, null));
        harness.fields.add(new FieldNode(ACC_PUBLIC, "drawMode", "I", null, null));
        harness.fields.add(new FieldNode(ACC_PUBLIC, "vboRegion", "L" + region + ";", null, null));
        harness.fields.add(new FieldNode(ACC_PUBLIC, "vboRange", "Ljava/lang/Object;", null, null));
        MethodNode draw = new MethodNode(ACC_PUBLIC, original.name, original.desc, null, null); original.accept(draw);
        for (AbstractInsnNode n : draw.instructions) {
            if (n instanceof FieldInsnNode) {
                FieldInsnNode f = (FieldInsnNode)n; f.owner = harness.name;
                if (f.name.equals("vboRegion")) f.desc = "L" + region + ";";
                if (f.name.equals("vboRange")) f.desc = "Ljava/lang/Object;";
            } else if (n instanceof MethodInsnNode) {
                MethodInsnNode call = (MethodInsnNode)n;
                if (call.owner.equals("org/lwjgl/opengl/GL11")) call.owner = gl;
                else { call.owner = region; call.desc = "(ILjava/lang/Object;)V"; }
            }
        }
        harness.methods.add(draw);
        MethodNode ctor = new MethodNode(ACC_PUBLIC, "<init>", "()V", null, null);
        ctor.instructions.add(new VarInsnNode(ALOAD, 0)); ctor.instructions.add(new MethodInsnNode(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false));
        ctor.instructions.add(new InsnNode(RETURN)); harness.methods.add(ctor);
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS); harness.accept(writer);
        byte[] bytes = writer.toByteArray();
        Class<?> type = new ClassLoader(getClass().getClassLoader()) { Class<?> define() { return defineClass(null, bytes, 0, bytes.length); } }.define();
        Object instance = type.getConstructor().newInstance(); Method call = type.getMethod("drawArrays", int.class);
        RecordedGl.values.clear(); type.getField("count").setInt(instance, 24); call.invoke(instance, 7);
        type.getField("count").setInt(instance, 80); call.invoke(instance, 7);
        type.getField("drawMode").setInt(instance, 4); type.getField("count").setInt(instance, 12); call.invoke(instance, 7);
        type.getField("vboRegion").set(instance, new Region()); call.invoke(instance, 7);
        assertEquals(Arrays.asList("7:0:24", "7:0:80", "4:0:12", "region:4"), RecordedGl.values);
    }
    public static class BridgeHolder { public static Object value; }
    @Test @Tag("private-terrain-capture") void exactCapturedNullGuardAllowsModelviewWhenReferenceNullAndRejectsOnlyActiveNonnullObject() throws Exception {
        ClassNode owner = captured(OWNER_FILE); assertTrue(TerrainEvidence.capturedClassMatches(owner));
        assertEquals(TerrainHook.BRIDGE_OWNER, owner.name);
        assertFalse(owner.methods.stream().anyMatch(m -> m.name.equals("<clinit>")), "Owner null read cannot run a class initializer");
        ClassNode state = captured(GSM_FILE); MethodNode accessor = TerrainHook.bridgeAccessor(state);
        assertEquals("()Z", accessor.desc);
        assertFalse(Arrays.stream(accessor.instructions.toArray()).anyMatch(n -> n instanceof MethodInsnNode || n instanceof TypeInsnNode),
                "Accessor cannot instantiate/call/force the unloaded declared matrix type");
        ClassNode harness = new ClassNode(); harness.version = V1_8; harness.access = ACC_PUBLIC;
        harness.name = "com/atw/renderboost/terrain/BridgeGateHarness"; harness.superName = "java/lang/Object";
        harness.fields.add(new FieldNode(ACC_PUBLIC | ACC_STATIC, "bridge$modelView", "Z", null, null));
        for (AbstractInsnNode n : accessor.instructions) if (n instanceof FieldInsnNode) {
            FieldInsnNode f = (FieldInsnNode)n;
            if (f.owner.equals(TerrainHook.GSM)) f.owner = harness.name;
            else { assertEquals(TerrainHook.BRIDGE_OWNER, f.owner); f.owner = Type.getInternalName(BridgeHolder.class); f.name = "value"; f.desc = "Ljava/lang/Object;"; }
        }
        harness.methods.add(accessor); ClassWriter w = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS); harness.accept(w);
        byte[] bytes = w.toByteArray(); Class<?> gate = new ClassLoader(getClass().getClassLoader()) {
            Class<?> define() { return defineClass(null, bytes, 0, bytes.length); }
        }.define();
        Method read = gate.getMethod("atwboost$bridgeActive");
        try {
            BridgeHolder.value = null; assertEquals(false, read.invoke(null));
            gate.getField("bridge$modelView").setBoolean(null, true); assertEquals(false, read.invoke(null));
            BridgeHolder.value = new Object(); assertEquals(true, read.invoke(null));
            gate.getField("bridge$modelView").setBoolean(null, false); assertEquals(false, read.invoke(null));
        } finally { BridgeHolder.value = null; }
    }
    @Test @Tag("private-terrain-capture") void partialHookFallbackDoesNotReferenceRejectedClassesAddedMembersAtTheSeam() throws Exception {
        ClassNode list = captured(LIST_FILE); new TerrainHook().transform(list, () -> {});
        MethodNode layer = method(list, "renderChunkLayer");
        for (AbstractInsnNode n : layer.instructions) {
            if (n instanceof FieldInsnNode) assertFalse(((FieldInsnNode)n).owner.equals(TerrainHook.BUFFER));
            if (n instanceof MethodInsnNode) assertFalse(((MethodInsnNode)n).name.equals("atwboost$bridgeActive"));
        }
        ClassNode state = captured(GSM_FILE); Map<MethodNode, AbstractInsnNode[]> originals = new IdentityHashMap<>();
        for (MethodNode m : state.methods) originals.put(m, m.instructions.toArray());
        new TerrainHook().transform(state, () -> {});
        for (Map.Entry<MethodNode, AbstractInsnNode[]> e : originals.entrySet()) assertArrayEquals(e.getValue(), e.getKey().instructions.toArray());
        new Analyzer<>(new BasicVerifier()).analyze(state.name, method(state, "atwboost$bridgeActive"));
    }
    @Test void allFifteenPrerequisitesAreRequiredAndRejectionCannotBeOverridden() throws Exception {
        String name = "com.atw.renderboost.terrain.TerrainControl";
        byte[] bytes;
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(name.replace('.', '/') + ".class")) {
            ByteArrayOutputStream out = new ByteArrayOutputStream(); byte[] data = new byte[4096]; int n;
            while ((n = in.read(data)) != -1) out.write(data, 0, n); bytes = out.toByteArray();
        }
        Class<?> isolated = new ClassLoader(getClass().getClassLoader()) { Class<?> define() { return defineClass(name, bytes, 0, bytes.length); } }.define();
        java.lang.reflect.Field requiredField = isolated.getDeclaredField("REQUIRED"); requiredField.setAccessible(true);
        List<String> required = new ArrayList<>((Set<String>)requiredField.get(null)); assertEquals(15, required.size());
        Method admit = isolated.getMethod("admit", String.class, boolean.class), available = isolated.getMethod("available");
        assertEquals(false, available.invoke(null));
        for (int i = 0; i < required.size() - 1; i++) admit.invoke(null, required.get(i), true);
        assertEquals(false, available.invoke(null)); admit.invoke(null, required.get(14), true); assertEquals(true, available.invoke(null));
        admit.invoke(null, required.get(0), false); assertEquals(false, available.invoke(null));
        admit.invoke(null, required.get(0), true); assertEquals(false, available.invoke(null));
    }
}
