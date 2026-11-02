package com.atw.renderboost.hook;

import java.lang.reflect.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.function.Consumer;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.*;
import static org.junit.jupiter.api.Assertions.*;

/** Private actual Hook-entry replay and shipped Weave stages, all disk-only.
 * No private bytes are copied to public resources; public Gradle excludes this tag. */
@Tag("private-nameprep-incoming")
class NamePrepIncomingCaptureTest implements Opcodes {
    static final String PREFIX = "$weave_potentialConflict$";
    static final String SHA = "977af2238793d68f411d2306c13efb3600b2f2253c408b878c1a035ccb81cf3c";
    static final String LOADER_SHA = "9fbcada12fc031426eb80613b02b9be31d83add6a116720b7e17e4439d28b4c8";
    static final String SH = "net.weavemc.loader.impl.shaded.";
    static String sha(byte[] bytes) throws Exception {
        StringBuilder out = new StringBuilder();
        for (byte b : MessageDigest.getInstance("SHA-256").digest(bytes)) out.append(String.format(Locale.ROOT, "%02x", b & 255));
        return out.toString();
    }
    static ClassNode read(byte[] bytes) { ClassNode c = new ClassNode(); new ClassReader(bytes).accept(c, 0); return c; }
    static byte[] bytes(ClassNode node) { ClassWriter w = new ClassWriter(0); node.accept(w); return w.toByteArray(); }
    static ClassNode actual() throws Exception {
        byte[] data = Files.readAllBytes(Paths.get(System.getProperty("atwboost.namePrepIncomingCapture")));
        assertEquals(SHA, sha(data), "this version is tied to the exact actual transform-entry capture");
        return read(data);
    }
    static List<MethodNode> bridges(ClassNode c) {
        List<MethodNode> out = new ArrayList<>();
        for (MethodNode m : c.methods) if (m.name.startsWith(PREFIX)) out.add(m);
        return out;
    }
    private static ClassLoader shipped;
    static Class<?> shippedType(String name) throws Exception {
        // Isolate shipped shaded ASM from the existing relocation-test loaders.
        // Adding the agent jar to the app classpath would bind Hook's ASM types
        // globally and invalidate those independent tests.
        if (shipped == null) shipped = new java.net.URLClassLoader(new java.net.URL[]{
            Paths.get(System.getProperty("atwboost.namePrepIncomingLoader")).toUri().toURL()
        }, NamePrepIncomingCaptureTest.class.getClassLoader());
        return Class.forName(name, true, shipped);
    }
    static Class<?> type(String name) throws Exception { return shippedType(SH + name); }
    static Object shaded(byte[] data) throws Exception {
        Object node = type("asm.tree.ClassNode").getConstructor().newInstance();
        type("asm.ClassReader").getMethod("accept", type("asm.ClassVisitor"), int.class)
            .invoke(type("asm.ClassReader").getConstructor(byte[].class).newInstance((Object)data), node, 0);
        return node;
    }
    @SuppressWarnings("unchecked")
    static List<Object> methods(Object node) throws Exception { return (List<Object>)node.getClass().getField("methods").get(node); }
    static String member(Object node, String field) throws Exception { return (String)node.getClass().getField(field).get(node); }
    static byte[] writeShaded(Object node, Object visitor, Object writer) throws Exception {
        node.getClass().getMethod("accept", type("asm.ClassVisitor")).invoke(node, visitor);
        return (byte[])writer.getClass().getMethod("toByteArray").invoke(writer);
    }
    static byte[] stage(byte[] data) throws Exception {
        Object node = shaded(data); HashMap<String, String> map = new HashMap<>();
        Class<?> utility = shippedType("net.weavemc.loader.impl.util.UtilityKt");
        Method merged = utility.getMethod("hasMixinAnnotation", type("asm.tree.MethodNode"), String.class);
        for (Object m : methods(node)) if ((Boolean)merged.invoke(null, m, "MixinMerged"))
            map.put(member(node, "name") + "." + member(m, "name") + member(m, "desc"), PREFIX + member(m, "name"));
        Constructor<?> constructor = shippedType("net.weavemc.loader.impl.InjectionHandler$transform$2$1").getDeclaredConstructor(HashMap.class);
        constructor.setAccessible(true); Object remapper = constructor.newInstance(map);
        type("mappings.remap.RemappingKt").getMethod("remap$default", type("asm.tree.ClassNode"), type("asm.commons.Remapper"), boolean.class, int.class, Object.class)
            .invoke(null, node, remapper, false, 2, null);
        Object writer = type("asm.ClassWriter").getConstructor(int.class).newInstance(0);
        return writeShaded(node, writer, writer);
    }
    static ClassNode inverse(ClassNode incoming) throws Exception {
        Object node = shaded(bytes(incoming)); Map<String, String> map = new HashMap<>();
        for (Object m : methods(node)) if (member(m, "name").startsWith(PREFIX))
            map.put(member(node, "name") + "." + member(m, "name") + member(m, "desc"), member(m, "name").substring(PREFIX.length()));
        Object writer = type("asm.ClassWriter").getConstructor(int.class).newInstance(0);
        Object remapper = type("asm.commons.SimpleRemapper").getConstructor(int.class, Map.class).newInstance(ASM9, map);
        Object visitor = type("mappings.remap.LambdaAwareRemapper").getConstructor(type("asm.ClassVisitor"), type("asm.commons.Remapper")).newInstance(writer, remapper);
        return read(writeShaded(node, visitor, writer));
    }
    static List<ClassNode> inputs() throws Exception {
        assertEquals(LOADER_SHA, sha(Files.readAllBytes(Paths.get(System.getProperty("atwboost.namePrepIncomingLoader")))));
        List<ClassNode> out = new ArrayList<>(); out.add(actual());
        Path first = Paths.get(System.getProperty("atwboost.nameCaptureRoot"));
        Path second = Paths.get(new String(Files.readAllBytes(first.getParent().resolve("restart-capture-path.txt")), java.nio.charset.StandardCharsets.UTF_8).trim());
        for (Path directory : Arrays.asList(first, second)) try (java.util.stream.Stream<Path> files = Files.list(directory)) {
            Path team = files.filter(p -> p.getFileName().toString().startsWith("net_minecraft_scoreboard_ScorePlayerTeam.") && p.toString().endsWith(".live.class")).findFirst().get();
            out.add(read(stage(Files.readAllBytes(team))));
        }
        return out;
    }
    @Test void actualEntryAndBothPriorShippedStagesAdmitPreserveNodesAndInverseBodies() throws Exception {
        for (ClassNode c : inputs()) {
            assertTrue(NamePrepHook.matches(c)); assertEquals(3, bridges(c).size());
            ClassNode baseline = inverse(c);
            List<MethodNode> declarations = new ArrayList<>(c.methods);
            MethodNode formatter = NamePrepHookTest.method(c, "formatString");
            Map<MethodNode, List<AbstractInsnNode>> nodes = new IdentityHashMap<>();
            Map<MethodNode, String> hashes = new IdentityHashMap<>();
            for (MethodNode m : c.methods) { nodes.put(m, Arrays.asList(m.instructions.toArray())); hashes.put(m, TerrainEvidence.fingerprint(m)); }
            List<FieldNode> fields = new ArrayList<>(c.fields);
            new NamePrepHook().transform(c, () -> {});
            assertEquals(declarations, c.methods); assertEquals(fields.size() + 1, c.fields.size());
            for (int i = 0; i < fields.size(); i++) assertSame(fields.get(i), c.fields.get(i));
            for (MethodNode m : c.methods) {
                if (m != formatter) { assertArrayEquals(nodes.get(m).toArray(), m.instructions.toArray()); assertEquals(hashes.get(m), TerrainEvidence.fingerprint(m)); }
                else {
                    int at = -1; List<AbstractInsnNode> changed = Arrays.asList(m.instructions.toArray());
                    for (AbstractInsnNode n : nodes.get(m)) { assertTrue(changed.indexOf(n) > at); at = changed.indexOf(n); }
                    new Analyzer<>(new BasicVerifier()).analyze(c.name, m);
                }
            }
            ClassNode restored = inverse(c);
            assertEquals(TerrainEvidence.fingerprint(formatter), TerrainEvidence.fingerprint(NamePrepHookTest.method(restored, "formatString")), "shipped inverse must preserve the exact injected formatter");
            assertEquals(baseline.name, restored.name); assertEquals(baseline.superName, restored.superName);
            assertEquals(baseline.version, restored.version); assertEquals(baseline.access, restored.access);
            assertEquals(baseline.interfaces, restored.interfaces);
            assertEquals(baseline.methods.size(), restored.methods.size());
            for (int i = 0; i < baseline.methods.size(); i++) {
                MethodNode a = baseline.methods.get(i), b = restored.methods.get(i);
                assertEquals(a.name, b.name); assertEquals(a.desc, b.desc);
                if (!a.name.equals("formatString")) assertEquals(TerrainEvidence.fingerprint(a), TerrainEvidence.fingerprint(b));
                assertEquals(annotationText(a), annotationText(b));
            }
            for (int i = 0; i < baseline.fields.size(); i++) {
                FieldNode a = baseline.fields.get(i), b = restored.fields.get(i);
                assertEquals(a.name, b.name); assertEquals(a.desc, b.desc); assertEquals(a.access, b.access);
                assertEquals(a.signature, b.signature); assertEquals(a.value, b.value);
            }
        }
    }
    static String annotationText(MethodNode m) {
        StringBuilder out = new StringBuilder();
        if (m.visibleAnnotations != null) for (AnnotationNode a : m.visibleAnnotations) out.append(a.desc).append(a.values);
        if (m.invisibleAnnotations != null) for (AnnotationNode a : m.invisibleAnnotations) out.append(a.desc).append(a.values);
        return out.toString();
    }
    @SuppressWarnings("unchecked")
    @Test void exactIncomingProfileRejectsEveryAliasMetadataAmbiguityAndProtectedBoundary() throws Exception {
        ClassNode source = actual(); assertTrue(NamePrepHook.matches(source)); int cases = 0;
        for (int index = 0; index < 3; index++) {
            final int at = index;
            List<Consumer<MethodNode>> changes = Arrays.asList(
                m -> m.name = m.name.substring(PREFIX.length()),
                m -> m.name = PREFIX + m.name,
                m -> m.name = "malformed$" + m.name,
                m -> m.name += "$unknown",
                m -> m.desc = "()V",
                m -> m.access |= ACC_FINAL,
                m -> m.signature = "()V",
                m -> m.exceptions.add("java/lang/Exception"),
                m -> m.instructions.insert(new InsnNode(NOP)),
                m -> { LabelNode start = new LabelNode(), end = new LabelNode(); m.instructions.insert(start); m.instructions.add(end); m.tryCatchBlocks.add(new TryCatchBlockNode(start, end, end, "java/lang/Exception")); },
                m -> m.visibleAnnotations = null,
                m -> m.visibleAnnotations.add(new AnnotationNode("Lforeign/Annotation;")),
                m -> m.visibleAnnotations.add(m.visibleAnnotations.get(0)),
                m -> { m.invisibleAnnotations = m.visibleAnnotations; m.visibleAnnotations = null; },
                m -> m.invisibleAnnotations = Arrays.asList(new AnnotationNode("Lforeign/Annotation;")),
                m -> m.visibleAnnotations.get(0).desc = "Lforeign/MixinMerged;",
                m -> m.visibleAnnotations.get(0).values.set(1, "foreign.Mixin"),
                m -> m.visibleAnnotations.get(0).values.set(3, 201),
                m -> m.visibleAnnotations.get(0).values.set(3, 200L),
                m -> m.visibleAnnotations.get(0).values.set(5, "1-1-1-1-1"),
                m -> m.visibleAnnotations.get(0).values.set(5, "658F5D29-9792-470D-B7AA-375A3D9785D8"),
                m -> m.visibleAnnotations.get(0).values.set(5, "00000000-0000-0000-0000-000000000000"),
                m -> m.visibleAnnotations.get(0).values.set(0, "sessionId"),
                m -> m.visibleAnnotations.get(0).values.set(0, "unknown"),
                m -> m.visibleAnnotations.get(0).values.add("extra"),
                m -> m.visibleAnnotations.get(0).values = null,
                m -> m.visibleAnnotations.get(0).values = new ArrayList<>(m.visibleAnnotations.get(0).values.subList(0, 4)),
                m -> m.visibleTypeAnnotations = Arrays.asList(new TypeAnnotationNode(0, null, "Lforeign/Annotation;")),
                m -> m.invisibleTypeAnnotations = Arrays.asList(new TypeAnnotationNode(0, null, "Lforeign/Annotation;")),
                m -> m.annotationDefault = "unknown",
                m -> m.visibleParameterAnnotations = new List[0],
                m -> m.invisibleParameterAnnotations = new List[0],
                m -> m.visibleAnnotableParameterCount = 1,
                m -> m.invisibleAnnotableParameterCount = 1,
                m -> m.attrs = Arrays.asList(new Attribute("Unproved") {
                    @Override protected ByteVector write(ClassWriter w, byte[] code, int length, int stack, int locals) { return new ByteVector(); }
                })
            );
            for (Consumer<MethodNode> change : changes) { rejected(source, c -> change.accept(bridges(c).get(at))); cases++; }
            rejected(source, c -> c.methods.remove(bridges(c).get(at))); cases++;
            rejected(source, c -> c.methods.add(bridges(c).get(at))); cases++;
            rejected(source, c -> { MethodNode m = bridges(c).get(at); MethodNode plain = new MethodNode(m.access, m.name.substring(PREFIX.length()), m.desc, null, null); m.accept(plain); c.methods.add(plain); }); cases++;
        }
        for (String name : Arrays.asList("formatString", "formatPlayerName", "getColorPrefix", "getColorSuffix")) {
            rejected(source, c -> NamePrepHookTest.method(c, name).instructions.insert(new InsnNode(NOP))); cases++;
            rejected(source, c -> NamePrepHookTest.method(c, name).name = PREFIX + name); cases++;
        }
        rejected(source, c -> c.version = V1_6); cases++;
        rejected(source, c -> c.fields.stream().filter(f -> f.name.equals("colorSuffix")).findFirst().get().access = ACC_PRIVATE); cases++;
        rejected(source, c -> c.fields.add(new FieldNode(ACC_PUBLIC, PREFIX + "foreign", "I", null, null))); cases++;
        rejected(source, c -> c.methods.add(new MethodNode(ACC_PUBLIC, PREFIX + "unknown", "()V", null, null))); cases++;
        rejected(source, c -> c.methods.add(new MethodNode(ACC_PUBLIC, "bridge$unknown", "()V", null, null))); cases++;
        rejected(source, c -> { MethodNode m = NamePrepHookTest.method(c, "getRegisteredName"); m.visibleAnnotations = bridges(c).get(0).visibleAnnotations; }); cases++;
        assertTrue(cases >= 100); System.out.println("INCOMING_PROFILE_NEGATIVES " + cases);
    }
    @Test void serializedAliasCodeTypeMetadataRejectsWithoutMutation() throws Exception {
        for (int bridgeIndex = 0; bridgeIndex < 3; bridgeIndex++)
            for (boolean visible : new boolean[]{true, false})
                for (boolean local : new boolean[]{true, false}) {
                    ClassNode altered = actual();
                    MethodNode bridge = bridges(altered).get(bridgeIndex);
                    if (local) {
                        LabelNode start = new LabelNode(), end = new LabelNode();
                        bridge.instructions.insert(start); bridge.instructions.add(end);
                        LocalVariableAnnotationNode annotation = new LocalVariableAnnotationNode(
                            TypeReference.newTypeReference(TypeReference.LOCAL_VARIABLE).getValue(), null,
                            new LabelNode[]{start}, new LabelNode[]{end}, new int[]{0}, "Lunknown/CodeMetadata;");
                        if (visible) bridge.visibleLocalVariableAnnotations = Arrays.asList(annotation);
                        else bridge.invisibleLocalVariableAnnotations = Arrays.asList(annotation);
                    } else {
                        AbstractInsnNode invocation = null;
                        for (AbstractInsnNode instruction : bridge.instructions)
                            if (instruction instanceof MethodInsnNode) { invocation = instruction; break; }
                        assertNotNull(invocation);
                        TypeAnnotationNode annotation = new TypeAnnotationNode(
                            TypeReference.newTypeArgumentReference(TypeReference.METHOD_INVOCATION_TYPE_ARGUMENT, 0).getValue(),
                            null, "Lunknown/CodeMetadata;");
                        if (visible) invocation.visibleTypeAnnotations = Arrays.asList(annotation);
                        else invocation.invisibleTypeAnnotations = Arrays.asList(annotation);
                    }
                    // Code attributes must survive a real classfile round trip;
                    // rejection must retain all serialized bytes and skip frames.
                    rejected(read(bytes(altered)), c -> {});
                }
    }
    static void rejected(ClassNode source, Consumer<ClassNode> change) {
        ClassNode c = NamePrepHookTest.copy(source); change.accept(c);
        assertFalse(NamePrepHook.matches(c), "changed incoming profile must fail");
        boolean serializable = c.methods.stream().noneMatch(m -> m.visibleAnnotations != null && m.visibleAnnotations.stream().anyMatch(a -> a.values != null && a.values.size() % 2 != 0));
        byte[] before = serializable ? bytes(c) : null;
        List<MethodNode> methods = new ArrayList<>(c.methods); List<FieldNode> fields = new ArrayList<>(c.fields);
        List<String> metadata = new ArrayList<>(); List<AbstractInsnNode[]> instructions = new ArrayList<>();
        for (MethodNode m : c.methods) { metadata.add(annotationText(m)); instructions.add(m.instructions.toArray()); }
        new NamePrepHook().transform(c, () -> fail("rejected input cannot request frame computation"));
        assertEquals(methods, c.methods); assertEquals(fields, c.fields);
        for (int i = 0; i < methods.size(); i++) { assertArrayEquals(instructions.get(i), methods.get(i).instructions.toArray()); assertEquals(metadata.get(i), annotationText(methods.get(i))); }
        if (serializable) assertArrayEquals(before, bytes(c), "rejection must not mutate any class bytes");
    }
    @Test void allConflictReferenceFormsRejectForeignUnresolvedAndUnprefixedOperands() throws Exception {
        ClassNode source = actual(); MethodNode bridge = bridges(source).get(0);
        for (String owner : Arrays.asList(NamePrepHook.OWNER, "foreign/Owner"))
            for (String name : Arrays.asList(bridge.name, PREFIX + "unknown", PREFIX + bridge.name, "broken" + bridge.name, bridge.name.substring(PREFIX.length())))
                for (String desc : Arrays.asList(bridge.desc, "()V")) {
                    boolean valid = owner.equals(NamePrepHook.OWNER) && name.equals(bridge.name) && desc.equals(bridge.desc);
                    if (valid || (owner.equals("foreign/Owner") && !name.contains("weave_potentialConflict"))
                        ) continue;
                    rejected(source, c -> unrelated(c).instructions.insert(new MethodInsnNode(INVOKEVIRTUAL, owner, name, desc, false)));
                    rejected(source, c -> unrelated(c).instructions.insert(new LdcInsnNode(new Handle(H_INVOKEVIRTUAL, owner, name, desc, false))));
                }
        rejected(source, c -> unrelated(c).instructions.insert(new MethodInsnNode(INVOKESTATIC, NamePrepHook.OWNER, bridge.name, bridge.desc, false)));
        rejected(source, c -> unrelated(c).instructions.insert(new MethodInsnNode(INVOKEINTERFACE, NamePrepHook.OWNER, bridge.name, bridge.desc, true)));
        rejected(source, c -> unrelated(c).instructions.insert(new FieldInsnNode(GETSTATIC, NamePrepHook.OWNER, PREFIX + "field", "I")));
        Handle bootstrap = new Handle(H_INVOKESTATIC, "fixture/Bootstrap", "call", "()V", false);
        Handle foreign = new Handle(H_INVOKEVIRTUAL, "foreign/Owner", bridge.name, bridge.desc, false);
        rejected(source, c -> unrelated(c).instructions.insert(new InvokeDynamicInsnNode(PREFIX + "dynamic", "()V", bootstrap)));
        rejected(source, c -> unrelated(c).instructions.insert(new InvokeDynamicInsnNode("dynamic", "()V", foreign)));
        rejected(source, c -> unrelated(c).instructions.insert(new InvokeDynamicInsnNode("dynamic", "()V", bootstrap, foreign)));
        rejected(source, c -> unrelated(c).instructions.insert(new LdcInsnNode(new ConstantDynamic("value", "Ljava/lang/Object;", bootstrap, foreign))));
        rejected(source, c -> unrelated(c).instructions.insert(new LdcInsnNode(new ConstantDynamic(PREFIX + "value", "Ljava/lang/Object;", bootstrap))));
        rejected(source, c -> unrelated(c).instructions.insert(new LdcInsnNode(new ConstantDynamic("value", "Ljava/lang/Object;", foreign))));
        // Exact local references remain unchanged, including typed handle operands.
        ClassNode valid = NamePrepHookTest.copy(source);
        unrelated(valid).instructions.insert(new LdcInsnNode(new Handle(H_INVOKEVIRTUAL, NamePrepHook.OWNER, bridge.name, bridge.desc, false)));
        assertTrue(NamePrepHook.matches(valid));
    }
    static MethodNode unrelated(ClassNode c) { return NamePrepHookTest.method(c, "getRegisteredName"); }
    @Test void actualAndPriorInjectionThenShippedInverseExecuteOffBaselineAndOnCurrentValues() throws Exception {
        for (ClassNode incoming : inputs()) {
            ClassNode baseline = inverse(incoming);
            new NamePrepHook().transform(incoming, () -> {});
            ClassNode restored = inverse(incoming);
            NamePrepExecutionTest.Fixture f = new NamePrepExecutionTest.Fixture(false, restored, baseline);
            assertEquals(false, f.noarg("requested"));
            assertEquals("[A]", f.format("A")); assertNotSame(f.format("A"), f.format("A"));
            assertEquals(0, f.counters()[1]); f.request(true);
            String[][] cases = {{"§c[", "Captain", "§r]", "§c[Captain§r]"}, {null, null, null, "nullnullnull"},
                {"[", "Aa", "]", "[Aa]"}, {"[", "BB", "]", "[BB]"}, {"<", "NPC", ">", "<NPC>"}};
            for (String[] row : cases) {
                f.team.getField("namePrefixSPT").set(f.receiver, row[0]); f.team.getField("colorSuffix").set(f.receiver, row[2]);
                assertEquals(row[3], f.team.getMethod("baseline", String.class).invoke(f.receiver, row[1]));
                String miss = f.format(row[1]); assertEquals(row[3], miss); assertSame(miss, f.format(row[1]));
            }
            Class<?> base = f.loadClass("net.minecraft.scoreboard.Team");
            Method dispatcher = f.team.getMethod("formatPlayerName", base, String.class);
            String username = new String("null-team"); assertSame(username, dispatcher.invoke(null, null, username));
            assertNull(dispatcher.invoke(null, null, null));
            f.request(false); assertNotSame(f.format("A"), f.format("A"));
            // Observation-only getter tracing after the exact production pipeline.
            NamePrepExecutionTest.Fixture traced = new NamePrepExecutionTest.Fixture(true, restored, baseline);
            NamePrepExecutionTest.Effects.trace.clear(); traced.request(true);
            traced.format("A"); traced.format("A");
            assertEquals(Arrays.asList("prefix", "suffix", "prefix", "suffix"), NamePrepExecutionTest.Effects.trace);
            RuntimeException failure = new IllegalStateException("original suffix exception");
            NamePrepExecutionTest.Effects.thrown = failure;
            try { assertSame(failure, assertThrows(InvocationTargetException.class, () -> traced.format("A")).getCause()); }
            finally { NamePrepExecutionTest.Effects.thrown = null; NamePrepExecutionTest.Effects.trace.clear(); }
        }
    }
    @Test void actualAliasProfileAlsoAdmitsThroughWeavesRelocatedAsmAndRejectsChangedAlias() throws Exception {
        NamePrepRelocationTest.Relocated loader = new NamePrepRelocationTest.Relocated();
        Method matches = loader.loadClass(NamePrepHook.class.getName()).getMethod("matches", loader.loadClass("net.weavemc.loader.impl.shaded.asm.tree.ClassNode"));
        ClassNode c = actual(); assertEquals(true, matches.invoke(null, loader.node(c)));
        bridges(c).get(0).instructions.insert(new InsnNode(NOP));
        assertEquals(false, matches.invoke(null, loader.node(c)));
    }
}
