// SPDX-License-Identifier: GPL-3.0-only
package com.atw.dispatch.hook;

import com.atw.dispatch.DispatchRuntime;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Set;
import net.weavemc.api.Hook;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import static org.objectweb.asm.Opcodes.*;

/** Ordinary Weave Hook only. No instrumentation, EventBus class literal, reflection or singleton lookup. */
public final class DispatchHook extends Hook {
    public static final String TARGET = "net/weavemc/api/event/EventBus";
    public static final String DESC = "(Lnet/weavemc/api/event/Event;)V";
    public static final String RUNTIME = "com/atw/dispatch/DispatchRuntime";
    public static final String SEAM_FAILURE = "atwDispatchSeamFailure";
    private static final Set<String> PINS = Set.of("1cb1a2db1294b640d628e2e8a0726ea5db82b242d31b21ca4e70476d69151642", "6d127d1ce0a5b347189230c20e7453defdd91b43145174d0a511791872ba7b7c");
    public DispatchHook() { super(TARGET); }

    /** Exact whole-class semantics, excluding only debug tables and stack-map encoding. */
    public static String fingerprint(ClassNode node) {
        ClassWriter first = new ClassWriter(0); node.accept(first);
        ClassNode normalized = new ClassNode();
        new ClassReader(first.toByteArray()).accept(normalized, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        ClassWriter writer = new ClassWriter(0); normalized.accept(writer);
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(writer.toByteArray())); }
        catch (NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
    }

    public static boolean matches(ClassNode node) {
        if (!TARGET.equals(node.name) || !PINS.contains(fingerprint(node))) return false;
        return node.fields.stream().filter(f -> f.name.equals("map") && f.desc.equals("Ljava/util/Map;")
            && f.access == (ACC_PRIVATE | ACC_STATIC | ACC_FINAL)).count() == 1
            && node.methods.stream().filter(m -> m.name.equals("postEvent") && m.desc.equals(DESC)
                && m.access == (ACC_PUBLIC | ACC_STATIC | ACC_FINAL)).count() == 1;
    }

    @Override public void transform(ClassNode node, AssemblerConfig config) {
        if (!matches(node)) {
            DispatchRuntime.hookResult(false, "unknown EventBus composition/descriptor/map; original retained");
            return;
        }
        MethodNode method = node.methods.stream().filter(m -> m.name.equals("postEvent") && m.desc.equals(DESC))
            .findFirst().orElseThrow();
        inject(method);
        node.fields.add(new FieldNode(ACC_PUBLIC | ACC_STATIC | ACC_VOLATILE | ACC_SYNTHETIC,
            SEAM_FAILURE, "Ljava/lang/String;", null, null));
        node.fields.add(new FieldNode(ACC_PUBLIC | ACC_STATIC | ACC_FINAL | ACC_SYNTHETIC,
            "atwDispatchMarker", "Ljava/lang/String;", null, "atw-dispatch-strict-v1"));
        config.computeFrames();
        DispatchRuntime.hookResult(true, "accepted");
    }

    /** Retains every original instruction and handler. Helper linkage catch ends before dispatch. */
    private static void inject(MethodNode method) {
        LabelNode start = new LabelNode(), end = new LabelNode(), missing = new LabelNode();
        LabelNode original = new LabelNode(), candidate = new LabelNode();
        InsnList prefix = new InsnList();
        prefix.add(start);
        prefix.add(new VarInsnNode(ALOAD, 0));
        prefix.add(new FieldInsnNode(GETSTATIC, TARGET, "map", "Ljava/util/Map;"));
        prefix.add(new LdcInsnNode(Type.getObjectType(TARGET)));
        prefix.add(new MethodInsnNode(INVOKESTATIC, RUNTIME, "prepare",
            "(Ljava/lang/Object;Ljava/util/Map;Ljava/lang/Class;)Z", false));
        prefix.add(end);
        prefix.add(new JumpInsnNode(IFNE, candidate));
        prefix.add(new JumpInsnNode(GOTO, original));
        prefix.add(missing);
        prefix.add(new InsnNode(POP));
        prefix.add(new LdcInsnNode("helper linkage unavailable; original retained; helper counters unavailable"));
        prefix.add(new FieldInsnNode(PUTSTATIC, TARGET, SEAM_FAILURE, "Ljava/lang/String;"));
        prefix.add(new JumpInsnNode(GOTO, original));
        prefix.add(candidate);
        prefix.add(new VarInsnNode(ALOAD, 0));
        prefix.add(new FieldInsnNode(GETSTATIC, TARGET, "map", "Ljava/util/Map;"));
        prefix.add(new MethodInsnNode(INVOKESTATIC, RUNTIME, "dispatch", "(Ljava/lang/Object;Ljava/util/Map;)V", false));
        prefix.add(new InsnNode(RETURN));
        prefix.add(original);
        method.instructions.insert(prefix);
        method.tryCatchBlocks.add(0, new TryCatchBlockNode(start, end, missing, "java/lang/LinkageError"));
        method.maxStack = Math.max(method.maxStack, 3);
    }
}
