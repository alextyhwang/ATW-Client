package com.atw.renderboost.hook;

import java.io.*;
import java.util.*;
import java.lang.reflect.Method;
import java.nio.file.*;
import java.security.MessageDigest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.*;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.*;
import static org.junit.jupiter.api.Assertions.*;

class TerrainHookStageTest implements Opcodes {
    static ClassNode stage(String owner) throws Exception {
        String simple = owner.substring(owner.lastIndexOf('/') + 1);
        return TerrainHookTest.captured("hookstage/" + simple + ".hook-input.class.dat");
    }
    static ClassNode post(String owner) throws Exception {
        Path dir = Paths.get(TerrainHookStageTest.class.getResource("/terrain/").toURI());
        try (DirectoryStream<Path> files = Files.newDirectoryStream(dir, "*.live.class.dat")) {
            for (Path file : files) {
                ClassNode c = TerrainHookTest.captured(file.getFileName().toString());
                if (c.name.equals(owner)) return c;
            }
        }
        throw new AssertionError("Missing actual post-load fixture");
    }
    static byte[] bytes(ClassNode c) {
        ClassWriter w = new ClassWriter(0); c.accept(w); return w.toByteArray();
    }
    static void reject(ClassNode c) {
        byte[] before = bytes(c);
        assertFalse(TerrainHookStage.matches(c));
        new TerrainHook().transform(c, () -> fail("Rejected class must not request frames"));
        assertArrayEquals(before, bytes(c), "Rejection must not alter the actual node");
    }
    @Test void unknownClassAndUnprovedConflictMetadataRejectWithoutMutation() {
        ClassNode c = new ClassNode(); c.version = V1_8; c.access = ACC_PUBLIC; c.name = "unknown/Terrain"; c.superName = "java/lang/Object";
        MethodNode m = new MethodNode(ACC_PUBLIC, TerrainHookStage.PREFIX + "callback", "()V", null, null);
        m.instructions.add(new InsnNode(RETURN)); c.methods.add(m); reject(c);
    }
    @Test void exactMergedDescriptorAndTypedValuesAreRequired() {
        MethodNode m = new MethodNode();
        assertNull(TerrainHookStage.merged(m));
        m.visibleAnnotations = new ArrayList<>();
        AnnotationNode a = new AnnotationNode("Lunknown/MixinMerged;"); m.visibleAnnotations.add(a);
        assertNull(TerrainHookStage.merged(m));
        a.desc = TerrainHookStage.MERGED;
        assertThrows(IllegalArgumentException.class, () -> TerrainHookStage.metadata(m));
        a.values = Arrays.<Object>asList("mixin", "example.Mixin", "priority", "200", "sessionId", "invalid");
        assertThrows(IllegalArgumentException.class, () -> TerrainHookStage.metadata(m));
        a.values = Arrays.<Object>asList(12, "example.Mixin", "priority", 200, "sessionId", "invalid");
        assertThrows(IllegalArgumentException.class, () -> TerrainHookStage.metadata(m));
        m.invisibleAnnotations = Collections.singletonList(a);
        assertThrows(IllegalArgumentException.class, () -> TerrainHookStage.merged(m));
    }
    @Test @Tag("private-terrain-capture") void actualAllFifteenStageInputsPassWithoutChangingOriginalNodes() throws Exception {
        int rawRejected = 0;
        for (String owner : TerrainHook.TARGETS) {
            ClassNode c = stage(owner); byte[] original = bytes(c);
            if (!TerrainEvidence.capturedClassMatches(c)) rawRejected++;
            assertTrue(TerrainHookStage.matches(c), owner); assertArrayEquals(original, bytes(c));
            assertTrue(TerrainHookStage.matches(post(owner)), "Previous exact captures remain accepted");
        }
        assertEquals(14, rawRejected, "The actual reported rejection must remain reproducible through the old evidence gate");
    }
    @Test @Tag("private-terrain-capture") void allFifteenSerializedFixturesRetainParentsExactBeforeHookHashes() throws Exception {
        Properties expected = new Properties();
        try (InputStream in = getClass().getResourceAsStream("/terrain/hookstage-hashes.properties")) {
            assertNotNull(in); expected.load(in);
        }
        assertEquals(15, expected.size());
        for (String owner : TerrainHook.TARGETS) {
            String simple = owner.substring(owner.lastIndexOf('/') + 1);
            try (InputStream in = getClass().getResourceAsStream("/terrain/hookstage/" + simple + ".hook-input.class.dat")) {
                assertNotNull(in); ByteArrayOutputStream out = new ByteArrayOutputStream(); byte[] data = new byte[4096]; int n;
                while ((n = in.read(data)) != -1) out.write(data, 0, n);
                StringBuilder hex = new StringBuilder();
                for (byte b : MessageDigest.getInstance("SHA-256").digest(out.toByteArray())) hex.append(String.format(Locale.ROOT, "%02x", b & 255));
                assertEquals(expected.getProperty(simple), hex.toString(), simple);
            }
        }
    }
    @Test @Tag("private-terrain-capture") void all809OriginalExecutableBodiesAndFieldConstantsProveTheStageProfile() throws Exception {
        int count = 0, publicInitializers = 0;
        for (String owner : TerrainHook.TARGETS) {
            ClassNode old = post(owner), copy = TerrainHookStage.comparisonCopy(stage(owner));
            assertEquals(old.version, copy.version); assertEquals(old.access, copy.access);
            assertEquals(old.superName, copy.superName); assertEquals(old.interfaces, copy.interfaces);
            assertEquals(old.fields.size(), copy.fields.size()); assertEquals(old.methods.size(), copy.methods.size());
            for (int i = 0; i < old.fields.size(); i++) {
                FieldNode a = old.fields.get(i), b = copy.fields.get(i);
                assertEquals(a.name, b.name); assertEquals(a.desc, b.desc); assertEquals(a.access, b.access);
                assertEquals(a.value, b.value); assertEquals(a.signature, b.signature);
            }
            for (MethodNode m : copy.methods) {
                MethodNode original = TerrainHook.method(old, m.name, m.desc);
                if (m.access != original.access) {
                    assertEquals("<clinit>", m.name); assertEquals("()V", m.desc);
                    assertEquals(9, m.access); assertEquals(8, original.access); m.access = 8; publicInitializers++;
                }
                assertEquals(TerrainEvidence.fingerprint(original), TerrainEvidence.fingerprint(m), owner + "." + m.name + m.desc);
                count++;
            }
        }
        assertEquals(809, count); assertEquals(8, publicInitializers);
    }
    @Test @Tag("private-terrain-capture") void actualTransformsRetainLoaderNamesFieldOrderAndOriginalDrawMatrixAndStateInstructions() throws Exception {
        for (String owner : TerrainHook.TARGETS) {
            ClassNode c = stage(owner);
            List<FieldNode> fields = new ArrayList<>(c.fields);
            List<MethodNode> methods = new ArrayList<>(c.methods);
            Map<MethodNode, AbstractInsnNode[]> insns = new IdentityHashMap<>();
            Map<MethodNode, String> names = new IdentityHashMap<>();
            for (MethodNode m : methods) { insns.put(m, m.instructions.toArray()); names.put(m, m.name); }
            new TerrainHook().transform(c, () -> {});
            assertEquals(fields, c.fields); assertEquals(methods, c.methods.subList(0, methods.size()));
            for (MethodNode m : methods) {
                assertEquals(names.get(m), m.name, "Loader owns temporary names");
                int removed = 0, last = -1;
                for (AbstractInsnNode n : insns.get(m)) {
                    if (m.instructions.contains(n)) {
                        int at = m.instructions.indexOf(n); assertTrue(at > last, "Original draw/matrix/state order"); last = at;
                    } else {
                        removed++;
                        assertEquals(TerrainHook.LIST, owner); assertEquals("renderChunkLayer", m.name);
                        assertTrue(n instanceof VarInsnNode || n instanceof MethodInsnNode);
                        if (n instanceof MethodInsnNode) assertTrue(Arrays.asList("bindBuffer", "setupArrayPointers").contains(((MethodInsnNode)n).name));
                    }
                }
                if (owner.equals(TerrainHook.LIST) && m.name.equals("renderChunkLayer")) assertEquals(4, removed);
                else assertEquals(0, removed);
                if (m.instructions.size() != insns.get(m).length) new Analyzer<>(new BasicVerifier()).analyze(owner, m);
            }
            if (owner.equals(TerrainHook.GSM)) {
                MethodNode accessor = TerrainHook.method(c, "atwboost$bridgeActive", "()Z");
                new Analyzer<>(new BasicVerifier()).analyze(owner, accessor);
                assertFalse(Arrays.stream(accessor.instructions.toArray()).anyMatch(n -> n instanceof MethodInsnNode || n instanceof TypeInsnNode));
            }
        }
    }
    @Test @Tag("private-terrain-capture") void unknownMembersDuplicatesAccessTypesAndLayoutsStillRejectForEveryStageInput() throws Exception {
        for (String owner : TerrainHook.TARGETS) {
            ClassNode c = stage(owner); c.methods.add(new MethodNode(ACC_PUBLIC, "unknown", "()V", null, null)); reject(c);
            c = stage(owner); c.methods.add(c.methods.get(0)); reject(c);
            c = stage(owner); c.fields.add(new FieldNode(ACC_PUBLIC, "unknown", "I", null, null)); reject(c);
            c = stage(owner); c.fields.add(c.fields.get(0)); reject(c);
            c = stage(owner); c.fields.get(0).access ^= ACC_FINAL; reject(c);
            c = stage(owner); c.fields.get(0).desc = "J"; reject(c);
            c = stage(owner); c.methods.get(0).access ^= ACC_FINAL; reject(c);
            c = stage(owner); Collections.swap(c.methods, 0, 1); reject(c);
            c = stage(owner); c.superName = "unknown/Base"; reject(c);
            c = stage(owner); c.interfaces.add("unknown/Interface"); reject(c);
            c = stage(owner); c.methods.get(0).instructions.insert(new InsnNode(NOP)); reject(c);
        }
    }
    @Test @Tag("private-terrain-capture") void changedPointerOperandsControlFlowAndPreviouslyUnhashedMethodsReject() throws Exception {
        for (String mutation : Arrays.asList("stride", "type", "offset", "owner", "call")) {
            ClassNode c = stage(TerrainHook.LIST); MethodNode m = TerrainHookTest.method(c, "setupArrayPointers"); boolean changed = false;
            for (AbstractInsnNode n : m.instructions) {
                if (mutation.equals("stride") && n instanceof IntInsnNode && ((IntInsnNode)n).operand == 28) { ((IntInsnNode)n).operand = 32; changed = true; break; }
                if (mutation.equals("type") && n instanceof IntInsnNode && ((IntInsnNode)n).operand == 5122) { ((IntInsnNode)n).operand = 5123; changed = true; break; }
                if (mutation.equals("offset") && n instanceof LdcInsnNode && Long.valueOf(24).equals(((LdcInsnNode)n).cst)) { ((LdcInsnNode)n).cst = 20L; changed = true; break; }
                if (n instanceof MethodInsnNode && ((MethodInsnNode)n).name.equals("glVertexPointer")) {
                    if (mutation.equals("owner")) { ((MethodInsnNode)n).owner = "unknown/GL"; changed = true; break; }
                    if (mutation.equals("call")) { m.instructions.insert(n, new MethodInsnNode(INVOKESTATIC, "unknown/GL", "callback", "()V", false)); changed = true; break; }
                }
            }
            assertTrue(changed); reject(c);
        }
        ClassNode c = stage(TerrainHook.GLOBAL); TerrainHookTest.method(c, "renderSky").instructions.insert(new InsnNode(NOP)); reject(c);
        c = stage(TerrainHook.GSM); TerrainHookTest.method(c, "blendFunc").instructions.insert(new InsnNode(NOP)); reject(c);
        c = stage("net/optifine/Config"); c.fields.stream().filter(f -> f.value != null).findFirst().get().value = "changed"; reject(c);
    }
    @Test @Tag("private-terrain-capture") void mergedNameMetadataSessionAndOwnCallTargetsMustAllAgree() throws Exception {
        for (String owner : Arrays.asList(TerrainHook.GSM, TerrainHook.GLOBAL, "net/optifine/Config", "net/minecraft/client/renderer/vertex/VertexFormat")) {
            ClassNode c = stage(owner); MethodNode m = c.methods.stream().filter(x -> x.name.startsWith(TerrainHookStage.PREFIX)).findFirst().get();
            m.visibleAnnotations.remove(TerrainHookStage.merged(m)); reject(c);
            c = stage(owner); m = c.methods.stream().filter(x -> x.name.startsWith(TerrainHookStage.PREFIX)).findFirst().get();
            m.name = m.name.substring(TerrainHookStage.PREFIX.length()); reject(c);
            c = stage(owner); m = c.methods.stream().filter(x -> x.name.startsWith(TerrainHookStage.PREFIX)).findFirst().get();
            TerrainHookStage.merged(m).values.set(1, "unknown.Mixin"); reject(c);
            c = stage(owner); m = c.methods.stream().filter(x -> x.name.startsWith(TerrainHookStage.PREFIX)).findFirst().get();
            TerrainHookStage.merged(m).values.set(5, "invalid"); reject(c);
            c = stage(owner); m = c.methods.stream().filter(x -> x.name.startsWith(TerrainHookStage.PREFIX)).findFirst().get();
            AnnotationNode annotation = TerrainHookStage.merged(m); m.visibleAnnotations.remove(annotation);
            m.invisibleAnnotations = Collections.singletonList(annotation); reject(c);
        }
        ClassNode c = stage(TerrainHook.GSM);
        for (AbstractInsnNode n : TerrainHookTest.method(c, "pushMatrix").instructions) if (n instanceof MethodInsnNode) {
            ((MethodInsnNode)n).name = ((MethodInsnNode)n).name.substring(TerrainHookStage.PREFIX.length()); break;
        }
        reject(c);
        c = stage(TerrainHook.GLOBAL);
        MethodNode reload = c.methods.stream().filter(m -> m.name.endsWith("bridge$reloadChunks")).findFirst().get();
        for (AbstractInsnNode n : reload.instructions) if (n instanceof InvokeDynamicInsnNode) {
            InvokeDynamicInsnNode dynamic = (InvokeDynamicInsnNode)n;
            for (int i = 0; i < dynamic.bsmArgs.length; i++) if (dynamic.bsmArgs[i] instanceof Handle) {
                Handle h = (Handle)dynamic.bsmArgs[i];
                if (h.getOwner().equals(c.name)) dynamic.bsmArgs[i] = new Handle(h.getTag(), h.getOwner(), h.getName().substring(TerrainHookStage.PREFIX.length()), h.getDesc(), h.isInterface());
            }
        }
        reject(c);
        c = stage(TerrainHook.GSM); MethodNode merged = TerrainHookStage.mergedMethod(c, "handler$zde000$bridge$pushMatrix", "(Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;)V");
        MethodNode duplicate = new MethodNode(merged.access, merged.name.substring(TerrainHookStage.PREFIX.length()), merged.desc, null, null); merged.accept(duplicate); c.methods.add(duplicate); reject(c);
        ClassNode duplicateState = c;
        assertThrows(IllegalArgumentException.class, () -> TerrainHook.bridgeAccessor(duplicateState));
    }
    @Test @Tag("private-terrain-capture") void sessionDerivedNamesMayChangeOnlyWithExactConsistentMergedSessionAndMemberTails() throws Exception {
        ClassNode c = stage(TerrainHook.GLOBAL);
        String old = "66beb3", next = "abcdef", id = "12345678-1234-1234-1234-123456abcdef";
        Map<String, String> map = new HashMap<>();
        for (MethodNode m : c.methods) if (m.name.contains("md" + old + "$")) map.put(c.name + "." + m.name + m.desc, m.name.replace("md" + old + "$", "md" + next + "$"));
        for (FieldNode f : c.fields) if (f.name.startsWith("fd" + old + "$")) map.put(c.name + "." + f.name, f.name.replace("fd" + old + "$", "fd" + next + "$"));
        ClassNode renamed = new ClassNode(); c.accept(new ClassRemapper(renamed, new SimpleRemapper(map)));
        for (MethodNode m : renamed.methods) if (TerrainHookStage.merged(m) != null) {
            AnnotationNode a = TerrainHookStage.merged(m); for (int i = 0; i < a.values.size(); i += 2) if (a.values.get(i).equals("sessionId")) a.values.set(i + 1, id);
        }
        assertTrue(TerrainHookStage.matches(renamed), "Known session-derived declarations, references and bootstrap handles track the same UUID");
        FieldNode unique = renamed.fields.get(renamed.fields.size() - 1);
        unique.name = "fdd6e203$lunar$chunksToWait$0"; reject(renamed);
        unique.name = "fd" + next + "$lunar$chunksToWait$0";
        MethodNode lambda = renamed.methods.stream().filter(m -> m.name.contains("lambda$bridge$reloadChunks$0$0")).findFirst().get();
        lambda.name = lambda.name.replace("md" + next + "$", "mdd6e203$"); reject(renamed);
        c = stage(TerrainHook.GLOBAL);
        for (MethodNode m : c.methods) if (TerrainHookStage.merged(m) != null) {
            AnnotationNode a = TerrainHookStage.merged(m); for (int i = 0; i < a.values.size(); i += 2) if (a.values.get(i).equals("sessionId")) a.values.set(i + 1, id);
        }
        reject(c); // Declarations and references unchanged together still cannot carry the wrong session suffix.
        c = stage(TerrainHook.GLOBAL); c.methods.stream().filter(m -> m.name.contains("lambda$bridge$reloadChunks$0$0")).findFirst().get().name += "$unknown"; reject(c);
    }
    public static class Holder { public static Object value; }
    @Test @Tag("private-terrain-capture") void emittedStageBridgeGateExecutesAllFlagReferenceCasesWithoutCallingOpaqueType() throws Exception {
        MethodNode accessor = TerrainHook.bridgeAccessor(stage(TerrainHook.GSM));
        ClassNode harness = new ClassNode(); harness.version = V1_8; harness.access = ACC_PUBLIC;
        harness.name = "com/atw/renderboost/hook/StageBridgeHarness"; harness.superName = "java/lang/Object";
        harness.fields.add(new FieldNode(ACC_PUBLIC | ACC_STATIC, "bridge$modelView", "Z", null, null));
        for (AbstractInsnNode n : accessor.instructions) if (n instanceof FieldInsnNode) {
            FieldInsnNode f = (FieldInsnNode)n;
            if (f.owner.equals(TerrainHook.GSM)) f.owner = harness.name;
            else { f.owner = Type.getInternalName(Holder.class); f.name = "value"; f.desc = "Ljava/lang/Object;"; }
        }
        harness.methods.add(accessor); ClassWriter w = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS); harness.accept(w); byte[] data = w.toByteArray();
        Class<?> type = new ClassLoader(getClass().getClassLoader()) { Class<?> define() { return defineClass(null, data, 0, data.length); } }.define();
        Method call = type.getMethod("atwboost$bridgeActive");
        try {
            for (boolean flag : Arrays.asList(false, true)) for (boolean reference : Arrays.asList(false, true)) {
                type.getField("bridge$modelView").setBoolean(null, flag); Holder.value = reference ? new Object() : null;
                assertEquals(flag && reference, call.invoke(null));
            }
        } finally { Holder.value = null; }
    }
}
