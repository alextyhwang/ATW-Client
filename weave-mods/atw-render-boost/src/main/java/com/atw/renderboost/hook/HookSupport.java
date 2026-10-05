package com.atw.renderboost.hook;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

final class HookSupport {
    static final String RUNTIME = "com/atw/renderboost/RenderRuntime";
    static MethodInsnNode call(String name, String descriptor) {
        return new MethodInsnNode(Opcodes.INVOKESTATIC, RUNTIME, name, descriptor, false);
    }
    /** Finally wrapper catches implicit exceptions as well as explicit ATHROW. */
    static void scope(MethodNode method, String enter, String exit) {
        LabelNode start = new LabelNode(), end = new LabelNode(), handler = new LabelNode();
        InsnList head = new InsnList();
        head.add(call(enter, "()V"));
        head.add(start);
        method.instructions.insert(head);
        for (AbstractInsnNode insn : method.instructions.toArray()) {
            if (insn.getOpcode() == Opcodes.RETURN) method.instructions.insertBefore(insn, call(exit, "()V"));
        }
        method.instructions.add(end);
        method.instructions.add(handler);
        method.instructions.add(call(exit, "()V")); // Leaves the Throwable on stack.
        method.instructions.add(new InsnNode(Opcodes.ATHROW));
        method.tryCatchBlocks.add(new TryCatchBlockNode(start, end, handler, null));
    }
    static boolean installed(MethodNode method) {
        for (AbstractInsnNode insn : method.instructions) {
            if (insn instanceof MethodInsnNode && RUNTIME.equals(((MethodInsnNode) insn).owner)) return true;
        }
        return false;
    }
}
