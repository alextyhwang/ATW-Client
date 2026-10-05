package com.atw.renderboost.hook;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.objectweb.asm.tree.*;

/** Fail closed: compare every opcode, local, constant, field, and GL call. */
public final class PrimitiveShape {
    private PrimitiveShape() {}
    public static String fingerprint(AbstractInsnNode first, AbstractInsnNode last) {
        StringBuilder s = new StringBuilder();
        for (AbstractInsnNode n = first; n != null; n = n.getNext()) {
            if (n.getOpcode() >= 0) {
                s.append(n.getOpcode());
                if (n instanceof VarInsnNode) s.append(':').append(((VarInsnNode) n).var);
                else if (n instanceof FieldInsnNode) {
                    FieldInsnNode f = (FieldInsnNode) n;
                    s.append(':').append(f.owner).append(':').append(f.name).append(':').append(f.desc);
                } else if (n instanceof MethodInsnNode) {
                    MethodInsnNode m = (MethodInsnNode) n;
                    s.append(':').append(m.owner).append(':').append(m.name).append(':').append(m.desc).append(':').append(m.itf);
                } else if (n instanceof LdcInsnNode) s.append(':').append(((LdcInsnNode) n).cst);
                else if (!(n instanceof InsnNode)) return "unsupported";
                s.append('\n');
            }
            if (n == last) break;
        }
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(s.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) hex.append(String.format("%02x", b & 255));
            return hex.toString();
        } catch (java.security.NoSuchAlgorithmException e) { throw new AssertionError(e); }
    }
}
