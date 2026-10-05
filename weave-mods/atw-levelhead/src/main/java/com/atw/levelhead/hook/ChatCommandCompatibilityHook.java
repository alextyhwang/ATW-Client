package com.atw.levelhead.hook;

import net.weavemc.api.Hook;
import org.jetbrains.annotations.NotNull;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

/** Weave 1.4.1 emits chat itself; this hook only records packet provenance. */
public class ChatCommandCompatibilityHook extends Hook {
    private static final String PLAYER = "net/minecraft/client/entity/EntityPlayerSP";
    private static final String CHAT_PACKET = "net/minecraft/network/play/client/C01PacketChatMessage";
    private static final String ORIGIN = "com/atw/levelhead/hook/ChatPacketOrigin";

    public ChatCommandCompatibilityHook() {
        super(PLAYER);
    }

    @Override
    public void transform(@NotNull ClassNode node, @NotNull AssemblerConfig cfg) {
        if (!PLAYER.equals(node.name)) return;
        for (MethodNode method : node.methods) {
            if (!"sendChatMessage".equals(method.name)
                    || !"(Ljava/lang/String;)V".equals(method.desc)) continue;
            for (AbstractInsnNode instruction : method.instructions.toArray()) {
                if (!(instruction instanceof MethodInsnNode)) continue;
                MethodInsnNode call = (MethodInsnNode) instruction;
                if (call.getOpcode() != Opcodes.INVOKESPECIAL
                        || !CHAT_PACKET.equals(call.owner) || !"<init>".equals(call.name)
                        || !"(Ljava/lang/String;)V".equals(call.desc)) continue;
                AbstractInsnNode next = instruction.getNext();
                if (next != null && next.getOpcode() == Opcodes.DUP
                        && next.getNext() instanceof MethodInsnNode
                        && ORIGIN.equals(((MethodInsnNode) next.getNext()).owner)) continue;
                InsnList mark = new InsnList();
                mark.add(new InsnNode(Opcodes.DUP));
                mark.add(new MethodInsnNode(Opcodes.INVOKESTATIC, ORIGIN, "mark",
                        "(Lnet/minecraft/network/Packet;)V", false));
                method.instructions.insert(instruction, mark);
                // C01 construction leaves a reference (the vanilla NEW/DUP sequence).
                cfg.computeFrames();
            }
        }
    }
}
