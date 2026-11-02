package com.atw.renderboost.hook;

import java.nio.file.*;
import java.util.*;
import java.util.jar.JarFile;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.*;
import static org.junit.jupiter.api.Assertions.*;

class NamePrepHookTest implements Opcodes {
    static ClassNode mapped() throws Exception {
        try (JarFile jar = new JarFile(System.getProperty("atwboost.namePrepMappedJar"))) {
            ClassNode c = new ClassNode(); new ClassReader(jar.getInputStream(jar.getJarEntry(NamePrepHook.OWNER + ".class"))).accept(c, 0); return c;
        }
    }
    static MethodNode method(ClassNode c, String name) { return c.methods.stream().filter(m -> m.name.equals(name)).findFirst().get(); }
    static ClassNode copy(ClassNode c) { ClassNode out = new ClassNode(); c.accept(out); return out; }
    @Test void pinnedMappedBodyAdmittedAndOnlyFormattingSeamChanges() throws Exception {
        ClassNode c = mapped(); assertTrue(NamePrepHook.matches(c));
        MethodNode m = method(c, "formatString"); List<AbstractInsnNode> original = Arrays.asList(m.instructions.toArray());
        Map<MethodNode, String> hashes = new IdentityHashMap<>();
        for (MethodNode other : c.methods) if (other != m) hashes.put(other, TerrainEvidence.fingerprint(other));
        int fields = c.fields.size(); new NamePrepHook().transform(c, () -> {});
        assertEquals(fields + 1, c.fields.size()); int at = -1;
        List<AbstractInsnNode> changed = Arrays.asList(m.instructions.toArray());
        for (AbstractInsnNode n : original) { assertTrue(changed.indexOf(n) > at); at = changed.indexOf(n); }
        for (MethodNode other : hashes.keySet()) assertEquals(hashes.get(other), TerrainEvidence.fingerprint(other));
        assertTrue(m.tryCatchBlocks.isEmpty()); new Analyzer<>(new BasicVerifier()).analyze(c.name, m);
        assertFalse(NamePrepHook.matches(c), "duplicate composition rejected");
        AbstractInsnNode[] once = m.instructions.toArray(); new NamePrepHook().transform(c, () -> fail("duplicate must not inject"));
        assertArrayEquals(once, m.instructions.toArray());
    }
    @Test void everyExecutableInstructionOperandAccessDescriptorNameAndGetterMutationRejects() throws Exception {
        ClassNode source = mapped(); int changed = 0;
        for (String name : Arrays.asList("formatString", "formatPlayerName", "getColorPrefix", "getColorSuffix")) {
            MethodNode original = method(source, name);
            for (int i = 0; i < original.instructions.size(); i++) {
                if (original.instructions.get(i).getOpcode() < 0) continue;
                ClassNode c = copy(source); MethodNode m = method(c, name); m.instructions.insertBefore(m.instructions.get(i), new InsnNode(NOP));
                assertFalse(NamePrepHook.matches(c), name + "@" + i); changed++;
            }
            for (int change = 0; change < 5; change++) {
                ClassNode c = copy(source); MethodNode m = method(c, name);
                if (change == 0) m.access |= ACC_FINAL;
                if (change == 1) m.name = "$weave_potentialConflict$" + name;
                if (change == 2) m.desc = "()V";
                if (change == 3) c.methods.add(m);
                if (change == 4) m.visibleAnnotations = Arrays.asList(new AnnotationNode("Lunknown/Wrapper;"));
                assertFalse(NamePrepHook.matches(c));
            }
        }
        assertTrue(changed > 20);
        ClassNode c = copy(source); MethodNode m = method(c, "formatString");
        for (AbstractInsnNode n : m.instructions) if (n instanceof MethodInsnNode && ((MethodInsnNode)n).name.equals("getColorSuffix")) ((MethodInsnNode)n).name = "getColorPrefix";
        assertFalse(NamePrepHook.matches(c)); AbstractInsnNode[] before = m.instructions.toArray();
        new NamePrepHook().transform(c, () -> fail("unknown body retains original")); assertArrayEquals(before, m.instructions.toArray());
    }
    @Test void subclassOwnerChangedSuperclassFieldsAndUnknownVersionReject() throws Exception {
        for (int mutation = 0; mutation < 6; mutation++) {
            ClassNode c = mapped();
            if (mutation == 0) c.name = "fixture/Subclass";
            if (mutation == 1) c.superName = "java/lang/Object";
            if (mutation == 2) c.version = V11;
            if (mutation == 3) c.access |= ACC_FINAL;
            if (mutation == 4) c.fields.stream().filter(f -> f.name.equals("colorSuffix")).findFirst().get().access |= ACC_VOLATILE;
            if (mutation == 5) c.fields.stream().filter(f -> f.name.equals("namePrefixSPT")).findFirst().get().desc = "Ljava/lang/Object;";
            assertFalse(NamePrepHook.matches(c));
        }
    }
}
