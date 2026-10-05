package com.atw.renderboost.hook;

import java.io.*;
import java.lang.reflect.Method;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;
import org.objectweb.asm.tree.*;
import static org.junit.jupiter.api.Assertions.*;

/** Exercise the production gate in the same ASM namespace used by Weave 1.4.1. */
class TerrainOperandRelocationTest implements Opcodes {
    private static final String ASM = "org/objectweb/asm/";
    private static final String SHADE = "net/weavemc/loader/impl/shaded/asm/";

    // Isolated test loader: relocates public ASM and the actual production evidence/gate
    // classes in memory. No game classes, private loader jar, filesystem or game loading.
    static final class Relocated extends ClassLoader {
        Relocated() { super(TerrainOperandRelocationTest.class.getClassLoader()); }
        @Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            String internal = name.replace('.', '/');
            boolean asm = internal.startsWith(SHADE);
            boolean gate = name.equals(TerrainEvidence.class.getName())
                    || name.startsWith(TerrainHookStage.class.getName());
            if (!asm && !gate) return super.loadClass(name, resolve);
            Class<?> type = findLoadedClass(name);
            if (type == null) {
                String resource = (asm ? ASM + internal.substring(SHADE.length()) : internal) + ".class";
                try (InputStream in = getParent().getResourceAsStream(resource)) {
                    if (in == null) throw new ClassNotFoundException(resource);
                    ClassWriter writer = new ClassWriter(0);
                    new ClassReader(in).accept(new ClassRemapper(writer, new Remapper() {
                        @Override public String map(String n) {
                            return n.startsWith(ASM) ? SHADE + n.substring(ASM.length()) : n;
                        }
                    }), 0);
                    byte[] data = writer.toByteArray(); type = defineClass(name, data, 0, data.length);
                } catch (IOException e) { throw new ClassNotFoundException(name, e); }
            }
            if (resolve) resolveClass(type);
            return type;
        }
        Object node(ClassNode source) throws Exception {
            Class<?> node = loadClass((SHADE + "tree/ClassNode").replace('/', '.'));
            Class<?> reader = loadClass((SHADE + "ClassReader").replace('/', '.'));
            Class<?> visitor = loadClass((SHADE + "ClassVisitor").replace('/', '.'));
            Object result = node.getConstructor().newInstance();
            reader.getMethod("accept", visitor, int.class).invoke(
                    reader.getConstructor(byte[].class).newInstance((Object)TerrainHookStageTest.bytes(source)), result, 0);
            return result;
        }
        boolean matches(ClassNode source) throws Exception {
            Method method = loadClass(TerrainHookStage.class.getName()).getDeclaredMethod("matches",
                    loadClass((SHADE + "tree/ClassNode").replace('/', '.')));
            method.setAccessible(true); return (Boolean)method.invoke(null, node(source));
        }
        String fingerprint(ClassNode source, int index) throws Exception {
            return fingerprints(source).get(index);
        }
        List<String> fingerprints(ClassNode source) throws Exception {
            Object node = node(source);
            Method fingerprint = loadClass(TerrainEvidence.class.getName()).getMethod("fingerprint",
                    loadClass((SHADE + "tree/MethodNode").replace('/', '.')));
            List<String> result = new ArrayList<>();
            for (Object method : (List<?>)node.getClass().getField("methods").get(node))
                result.add((String)fingerprint.invoke(null, method));
            return result;
        }
    }

    static ClassNode operand(Object value) {
        ClassNode c = new ClassNode(); c.version = V11; c.access = ACC_PUBLIC;
        c.name = "test/Operand"; c.superName = "java/lang/Object";
        MethodNode m = new MethodNode(ACC_PUBLIC | ACC_STATIC, "value", "()V", null, null);
        m.instructions.add(new LdcInsnNode(value)); m.instructions.add(new InsnNode(POP));
        m.instructions.add(new InsnNode(RETURN)); m.maxStack = 1; c.methods.add(m); return c;
    }
    static Handle handle(int tag, String owner, String name, String desc, boolean itf) {
        return new Handle(tag, owner, name, desc, itf);
    }
    @Test void actualProductionFingerprintsAreStableUnderWeavesAsmRelocation() throws Exception {
        Handle h = handle(H_INVOKESTATIC, "example/Bootstrap", "make", "()Ljava/lang/Object;", false);
        for (Object value : Arrays.asList(Type.getObjectType("example/Value"), h,
                new ConstantDynamic("value", "Ljava/lang/Object;", h, Type.getObjectType("example/Value"),
                        new ConstantDynamic("inner", "Ljava/lang/Object;", h, h)))) {
            ClassNode c = operand(value);
            assertEquals(TerrainEvidence.fingerprint(c.methods.get(0)), new Relocated().fingerprint(c, 0));
        }
        ClassNode c = operand("unchanged"); MethodNode m = c.methods.get(0); m.instructions.clear();
        m.instructions.add(new InvokeDynamicInsnNode("run", "()V", h, Type.getMethodType("()V"), h));
        m.instructions.add(new InsnNode(RETURN));
        assertEquals(TerrainEvidence.fingerprint(m), new Relocated().fingerprint(c, 0));
    }
    @Test void stableTypeNamesRetainEveryExecutableOperandDistinction() throws Exception {
        List<Object> values = Arrays.asList(Type.getObjectType("example/A"), Type.getObjectType("example/B"),
                Type.getMethodType("()V"), "Lexample/A;",
                handle(H_INVOKESTATIC, "example/A", "call", "()V", false),
                handle(H_INVOKEVIRTUAL, "example/A", "call", "()V", false),
                handle(H_INVOKESTATIC, "example/B", "call", "()V", false),
                handle(H_INVOKESTATIC, "example/A", "changed", "()V", false),
                handle(H_INVOKESTATIC, "example/A", "call", "(I)V", false),
                handle(H_INVOKESTATIC, "example/A", "call", "()V", true));
        Set<String> hashes = new HashSet<>(); Relocated runtime = new Relocated();
        for (Object value : values) {
            ClassNode c = operand(value); String hash = TerrainEvidence.fingerprint(c.methods.get(0));
            assertTrue(hashes.add(hash), "Type, target, descriptor, tag and interface bit remain distinct");
            assertEquals(hash, runtime.fingerprint(c, 0));
        }
    }
    @Test @Tag("private-terrain-capture") void allThirtyExistingCapturesPassTheRelocatedProductionGateWithoutMutation() throws Exception {
        Relocated runtime = new Relocated(); int bodies = 0;
        for (String owner : TerrainHook.TARGETS) {
            for (ClassNode c : Arrays.asList(TerrainHookStageTest.stage(owner), TerrainHookStageTest.post(owner))) {
                byte[] original = TerrainHookStageTest.bytes(c);
                assertTrue(runtime.matches(c), owner);
                List<String> fingerprints = runtime.fingerprints(c);
                for (int i = 0; i < c.methods.size(); i++) {
                    assertEquals(TerrainEvidence.fingerprint(c.methods.get(i)), fingerprints.get(i)); bodies++;
                }
                assertArrayEquals(original, TerrainHookStageTest.bytes(c));
            }
        }
        assertEquals(1618, bodies);
    }
    static void unknownsReject(Relocated runtime, ClassNode source) throws Exception {
        for (int mutation = 0; mutation < 8; mutation++) {
            ClassNode c = new ClassNode(); source.accept(c);
            switch (mutation) {
                case 0: c.methods.get(0).instructions.insert(new InsnNode(NOP)); break;
                case 1: c.methods.add(new MethodNode(ACC_PUBLIC, "unknown", "()V", null, null)); break;
                case 2: c.fields.get(0).access ^= ACC_FINAL; break;
                case 3: c.methods.get(0).access ^= ACC_FINAL; break;
                case 4: c.methods.add(c.methods.get(0)); break;
                case 5: Collections.swap(c.methods, 0, 1); break;
                case 6: c.superName = "unknown/Base"; break;
                case 7:
                    MethodNode merged = c.methods.stream().filter(m -> TerrainHookStage.merged(m) != null).findFirst().get();
                    AnnotationNode a = TerrainHookStage.merged(merged);
                    for (int i = 0; i < a.values.size(); i += 2)
                        if (a.values.get(i).equals("sessionId")) a.values.set(i + 1, "12345678-1234-1234-1234-123456abcdef");
                    break;
                default: throw new AssertionError();
            }
            byte[] original = TerrainHookStageTest.bytes(c);
            assertFalse(runtime.matches(c), "mutation=" + mutation);
            assertArrayEquals(original, TerrainHookStageTest.bytes(c));
        }
        ClassNode c = new ClassNode(); source.accept(c);
        boolean changed = false;
        for (MethodNode m : c.methods) for (AbstractInsnNode n : m.instructions) {
            if (n instanceof LdcInsnNode && ((LdcInsnNode)n).cst instanceof Type) {
                ((LdcInsnNode)n).cst = Type.getObjectType("unknown/Type"); changed = true; break;
            }
            if (n instanceof InvokeDynamicInsnNode) {
                InvokeDynamicInsnNode d = (InvokeDynamicInsnNode)n;
                for (int i = 0; i < d.bsmArgs.length; i++) if (d.bsmArgs[i] instanceof Handle) {
                    Handle h = (Handle)d.bsmArgs[i];
                    d.bsmArgs[i] = handle(h.getTag(), h.getOwner(), "unknown", h.getDesc(), h.isInterface());
                    changed = true; break;
                }
            }
        }
        assertTrue(changed); assertFalse(runtime.matches(c), "Unknown type/bootstrap reference");
    }
    @Test @Tag("private-terrain-capture") void unknownInstructionsMembersAccessReferencesAndSessionsStillRejectAfterRelocation() throws Exception {
        Relocated runtime = new Relocated();
        unknownsReject(runtime, TerrainHookStageTest.stage("net/optifine/Config"));
        unknownsReject(runtime, TerrainHookStageTest.stage(TerrainHook.GLOBAL));
    }
    @Test @Tag("private-terrain-capture") @Tag("private-terrain-restart-capture")
    void bothRealRestartsPassTheRelocatedGateAndUnknownChangesStillReject() throws Exception {
        String root = System.getProperty("atwboost.terrainRestartCaptureRoot");
        assertNotNull(root, "Explicit private restart-capture mode required");
        Relocated runtime = new Relocated();
        Properties hashes = new Properties();
        try (InputStream in = getClass().getResourceAsStream("/terrain/restart-hashes.properties")) {
            assertNotNull(in); hashes.load(in);
        }
        for (String directory : Arrays.asList("terrain-hookstage", "terrain-restart-evidence/first-start"))
            for (String simple : Arrays.asList("Config", "RenderGlobal")) {
                Path file = Paths.get(root, directory, simple + ".hook-input.class");
                byte[] data = Files.readAllBytes(file);
                assertEquals(hashes.getProperty(directory + "/" + simple), TerrainHookEvidenceOutput.sha256(data));
                ClassNode c = new ClassNode(); new ClassReader(data).accept(c, 0);
                assertTrue(runtime.matches(c), directory + "/" + simple); unknownsReject(runtime, c);
            }
    }
}
