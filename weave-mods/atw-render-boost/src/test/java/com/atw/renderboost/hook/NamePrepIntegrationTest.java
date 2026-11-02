package com.atw.renderboost.hook;

import java.io.InputStream;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import static org.junit.jupiter.api.Assertions.*;

/** Examine compiled integration without initializing Minecraft, GL, commands or windows. */
class NamePrepIntegrationTest implements Opcodes {
    private static ClassNode node(String name) throws Exception {
        ClassNode c = new ClassNode(); try (InputStream in = NamePrepIntegrationTest.class.getResourceAsStream("/" + name + ".class")) { new ClassReader(in).accept(c, 0); } return c;
    }
    private static List<String> calls(MethodNode m) {
        List<String> names = new ArrayList<>(); for (AbstractInsnNode n : m.instructions) if (n instanceof MethodInsnNode) {
            MethodInsnNode c = (MethodInsnNode)n; names.add(c.owner + "." + c.name);
        } return names;
    }
    @Test void commandCancelsBeforeNameprepMutationAndOffersAllControls() throws Exception {
        ClassNode c = node("com/atw/renderboost/BoostCommand");
        MethodNode command = NamePrepHookTest.method(c, "executeOnRenderThread");
        List<AbstractInsnNode> list = Arrays.asList(command.instructions.toArray()); int mutations = 0;
        for (int i = 0; i < list.size(); i++) if (list.get(i) instanceof MethodInsnNode) {
            MethodInsnNode call = (MethodInsnNode)list.get(i);
            if (call.owner.equals("com/atw/renderboost/cache/NamePrepRuntime") && (call.name.equals("request") || call.name.equals("clear"))) {
                int previousCancel = -1;
                for (int j = 0; j < i; j++) if (list.get(j) instanceof MethodInsnNode && ((MethodInsnNode)list.get(j)).name.equals("cancelBenchmark")) previousCancel = j;
                assertTrue(previousCancel >= 0 && i - previousCancel < 16); mutations++;
            }
        }
        assertEquals(2, mutations);
        List<Object> constants = new ArrayList<>();
        for (MethodNode m : c.methods) for (AbstractInsnNode n : m.instructions) if (n instanceof LdcInsnNode) constants.add(((LdcInsnNode)n).cst);
        assertTrue(constants.contains("nameprep")); assertTrue(constants.contains("nameprep on|off|status|clear"));
        assertTrue(constants.stream().anyMatch(v -> v instanceof String && ((String)v).startsWith("/atwboost") && ((String)v).contains("nameprep on|off|status|clear")));
    }
    @Test void benchmarkArmingSamplingFinishAndInvalidationIncludeIndependentRuntimeGuards() throws Exception {
        ClassNode c = node("com/atw/renderboost/RenderRuntime"); String runtime = "com/atw/renderboost/cache/NamePrepRuntime.";
        assertTrue(calls(NamePrepHookTest.method(c, "frameStart")).contains(runtime + "frame"));
        assertTrue(calls(NamePrepHookTest.method(c, "invalidate")).contains(runtime + "clear"));
        MethodNode arm = c.methods.stream().filter(m -> m.name.equals("benchmark") && m.desc.equals("(IIZ)V")).findFirst().get();
        assertTrue(calls(arm).contains(runtime + "counters")); assertTrue(calls(arm).contains(runtime + "requested"));
        List<String> sample = calls(NamePrepHookTest.method(c, "sample"));
        assertTrue(sample.contains(runtime + "epoch")); assertTrue(sample.contains(runtime + "stable"));
        assertTrue(sample.indexOf(runtime + "epoch") < sample.indexOf("com/atw/renderboost/benchmark/BenchmarkSession.frame"), "warmup and sampling both guarded");
        assertTrue(sample.contains(runtime + "active")); assertTrue(sample.contains(runtime + "metadata"));
        List<String> finish = calls(NamePrepHookTest.method(c, "finish"));
        assertTrue(finish.contains(runtime + "exportDelta")); assertTrue(finish.contains(runtime + "stable"));
    }
    @Test void startupAndHookConstructorDoNotLoadGameClassesOrEnableExperiment() throws Exception {
        ClassNode c = node("com/atw/renderboost/cache/NamePrepRuntime");
        for (MethodNode m : c.methods) for (AbstractInsnNode n : m.instructions) {
            if (n instanceof MethodInsnNode) assertFalse(((MethodInsnNode)n).owner.startsWith("net/minecraft/"));
            if (n instanceof TypeInsnNode) assertFalse(((TypeInsnNode)n).desc.startsWith("net/minecraft/"));
        }
        MethodNode init = NamePrepHookTest.method(node("com/atw/renderboost/hook/NamePrepHook"), "<init>");
        for (AbstractInsnNode n : init.instructions) if (n instanceof MethodInsnNode) assertEquals("net/weavemc/api/Hook", ((MethodInsnNode)n).owner);
        MethodNode staticInit = NamePrepHookTest.method(c, "<clinit>");
        for (AbstractInsnNode n : staticInit.instructions) if (n instanceof FieldInsnNode && n.getOpcode() == PUTSTATIC)
            assertNotEquals("requested", ((FieldInsnNode)n).name, "JVM zero initialization leaves experiment OFF");
    }
}
