package com.atw.renderboost.hook;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

/** Match inspected 1.8.9 diagnostics and a conservative straight-through frame region. */
final class FrameErrorPattern implements Opcodes {
    static final String GAME = "net/minecraft/client/Minecraft";
    static final String DESC = "(Ljava/lang/String;)V";
    // Full diagnostic body including branch destinations; ignores only debug/frame metadata.
    static final String DIAGNOSTIC_HASH = "1df523c9abedd512815f9d7c6d6b60e6203d873d2b1164b18cd80cd1a8bece02";

    static String diagnosticHash(MethodNode m) {
        if (!m.tryCatchBlocks.isEmpty() || (m.access & (ACC_STATIC | ACC_ABSTRACT | ACC_NATIVE | ACC_SYNCHRONIZED)) != 0)
            return "unsupported";
        Map<AbstractInsnNode, Integer> indices = new IdentityHashMap<>();
        int index = 0;
        for (AbstractInsnNode n : m.instructions) if (n.getOpcode() >= 0) indices.put(n, index++);
        StringBuilder body = new StringBuilder();
        for (AbstractInsnNode n : m.instructions) {
            if (n.getOpcode() < 0) continue;
            body.append(n.getOpcode());
            if (n instanceof VarInsnNode) body.append(':').append(((VarInsnNode)n).var);
            else if (n instanceof TypeInsnNode) body.append(':').append(((TypeInsnNode)n).desc);
            else if (n instanceof FieldInsnNode) {
                FieldInsnNode f = (FieldInsnNode)n;
                body.append(':').append(f.owner).append(':').append(f.name).append(':').append(f.desc);
            } else if (n instanceof MethodInsnNode) {
                MethodInsnNode c = (MethodInsnNode)n;
                body.append(':').append(c.owner).append(':').append(c.name).append(':').append(c.desc).append(':').append(c.itf);
            } else if (n instanceof LdcInsnNode) {
                Object value = ((LdcInsnNode)n).cst;
                if (!(value instanceof String)) return "unsupported";
                body.append(':').append(((String)value).length()).append(':').append(value);
            } else if (n instanceof JumpInsnNode) {
                Integer target = indices.get(nextOpcode(((JumpInsnNode)n).label));
                if (target == null) return "unsupported";
                body.append(':').append(target);
            } else if (!(n instanceof InsnNode)) return "unsupported";
            body.append('\n');
        }
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(body.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) hex.append(String.format(Locale.ROOT, "%02x", b & 255));
            return hex.toString();
        } catch (NoSuchAlgorithmException e) { throw new AssertionError(e); }
    }

    static AbstractInsnNode nextOpcode(AbstractInsnNode n) {
        while (n != null && n.getOpcode() < 0) n = n.getNext();
        return n;
    }
    private static AbstractInsnNode previousOpcode(AbstractInsnNode n) {
        do { n = n.getPrevious(); } while (n != null && n.getOpcode() < 0);
        return n;
    }
    static AbstractInsnNode receiver(MethodInsnNode call, String label) {
        if (call.getOpcode() != INVOKESPECIAL || call.itf || !GAME.equals(call.owner) || !DESC.equals(call.desc)) return null;
        AbstractInsnNode text = previousOpcode(call), receiver = text == null ? null : previousOpcode(text);
        if (!(text instanceof LdcInsnNode) || !label.equals(((LdcInsnNode)text).cst)
                || !(receiver instanceof VarInsnNode) || receiver.getOpcode() != ALOAD || ((VarInsnNode)receiver).var != 0) return null;
        // No incoming branch, frame, or exception boundary may enter inside the operand sequence.
        for (AbstractInsnNode n = receiver.getNext(); n != call; n = n.getNext())
            if (n instanceof LabelNode || n instanceof FrameNode) return null;
        return receiver;
    }
    static boolean safeRegion(MethodNode m, AbstractInsnNode first, AbstractInsnNode post) {
        Set<AbstractInsnNode> region = Collections.newSetFromMap(new IdentityHashMap<>());
        for (AbstractInsnNode n = first; n != null && n != post; n = n.getNext()) region.add(n);
        if (!region.contains(first)) return false;
        // Permit forward conditionals/jumps inside the region. Reject loops, early exits,
        // switches, and a normal branch bypassing the retained post-render call.
        Map<AbstractInsnNode, Integer> order = new IdentityHashMap<>();
        int i = 0;
        for (AbstractInsnNode n : m.instructions) order.put(n, i++);
        for (AbstractInsnNode n : region) {
            int op = n.getOpcode();
            if ((op >= IRETURN && op <= RETURN) || op == ATHROW || op == JSR || op == RET
                    || n instanceof TableSwitchInsnNode || n instanceof LookupSwitchInsnNode) return false;
            if (n instanceof JumpInsnNode) {
                AbstractInsnNode target = nextOpcode(((JumpInsnNode)n).label);
                if (target == null || (!region.contains(target) && target != post) || order.get(target) <= order.get(n)) return false;
            }
        }
        // No branch or exception handler may re-enter after the pre site and before post.
        for (AbstractInsnNode n : m.instructions) {
            if (region.contains(n)) continue;
            List<LabelNode> targets = new ArrayList<>();
            if (n instanceof JumpInsnNode) targets.add(((JumpInsnNode)n).label);
            if (n instanceof TableSwitchInsnNode) { targets.add(((TableSwitchInsnNode)n).dflt); targets.addAll(((TableSwitchInsnNode)n).labels); }
            if (n instanceof LookupSwitchInsnNode) { targets.add(((LookupSwitchInsnNode)n).dflt); targets.addAll(((LookupSwitchInsnNode)n).labels); }
            for (LabelNode target : targets) if (region.contains(nextOpcode(target))) return false;
        }
        for (TryCatchBlockNode t : m.tryCatchBlocks) if (region.contains(nextOpcode(t.handler))) return false;
        return true;
    }
}
