package com.atw.renderboost.hook;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Exact captured executable evidence; class presence alone does not establish runtime use. */
public final class TerrainEvidence {
    private static final Properties CAPTURED = new Properties();
    static {
        try (InputStream in = TerrainEvidence.class.getResourceAsStream("/terrain-evidence.properties")) {
            if (in == null) throw new IllegalStateException("Missing terrain evidence");
            CAPTURED.load(in);
        } catch (Exception e) { throw new ExceptionInInitializerError(e); }
    }
    private TerrainEvidence() {}

    /** Private diagnostics only. Does not normalize names, alter evidence, or admit classes. */
    static Properties mismatchReport(ClassNode node) {
        Properties report = new Properties();
        String expectedShape = CAPTURED.getProperty(node.name + ".@shape");
        String actualShape = classShape(node);
        report.setProperty("class", node.name);
        report.setProperty("expectedShape", expectedShape == null ? "missing" : expectedShape);
        report.setProperty("actualShape", actualShape);
        report.setProperty("shapeMatch", String.valueOf(actualShape.equals(expectedShape)));
        int missing = 0, changed = 0, duplicates = 0, conflicts = 0;
        for (MethodNode m : node.methods) if (m.name.startsWith("$weave_potentialConflict$")) conflicts++;
        for (String key : new TreeSet<>(CAPTURED.stringPropertyNames())) {
            if (!key.startsWith(node.name + ".") || key.endsWith(".@shape")) continue;
            String signature = key.substring(node.name.length() + 1);
            MethodNode found = null;
            int count = 0;
            for (MethodNode m : node.methods) if ((m.name + m.desc).equals(signature)) { found = m; count++; }
            String actual = count == 0 ? "missing" : count > 1 ? "duplicate" : fingerprint(found);
            if (count == 0) missing++;
            else if (count > 1) duplicates++;
            else if (!actual.equals(CAPTURED.getProperty(key))) changed++;
            if (!actual.equals(CAPTURED.getProperty(key))) {
                report.setProperty("method." + signature + ".expected", CAPTURED.getProperty(key));
                report.setProperty("method." + signature + ".actual", actual);
            }
        }
        report.setProperty("missingMethods", String.valueOf(missing));
        report.setProperty("changedFingerprints", String.valueOf(changed));
        report.setProperty("duplicateMethods", String.valueOf(duplicates));
        report.setProperty("weaveConflictNames", String.valueOf(conflicts));
        return report;
    }

    public static boolean capturedMethodMatches(String owner, MethodNode method) {
        String expected = CAPTURED.getProperty(owner + "." + method.name + method.desc);
        return expected != null && expected.equals(fingerprint(method));
    }

    /** Passing the four-class seam is necessary, and explicitly insufficient for activation. */
    public static boolean capturedClassMatches(ClassNode node) {
        String shape = CAPTURED.getProperty(node.name + ".@shape");
        if (shape == null || !shape.equals(classShape(node))) return false;
        int checked = 0;
        for (String key : CAPTURED.stringPropertyNames()) {
            if (!key.startsWith(node.name + ".") || key.endsWith(".@shape")) continue;
            String signature = key.substring(node.name.length() + 1);
            MethodNode found = null;
            for (MethodNode m : node.methods) if ((m.name + m.desc).equals(signature)) {
                if (found != null) return false;
                found = m;
            }
            if (found == null || !capturedMethodMatches(node.name, found)) return false;
            checked++;
        }
        return checked > 0;
    }

    public static String classShape(ClassNode c) {
        StringBuilder s = new StringBuilder();
        s.append(c.version).append(':').append(c.access).append(':').append(c.name)
                .append(':').append(c.superName).append(':').append(c.interfaces);
        for (FieldNode f : c.fields) s.append('|').append(f.access).append(':').append(f.name).append(':').append(f.desc);
        for (MethodNode m : c.methods) s.append('|').append(m.access).append(':').append(m.name).append(':').append(m.desc);
        return s.toString();
    }

    /** Complete executable operands and targets, including handlers; debug/frame metadata excluded. */
    public static String fingerprint(MethodNode method) {
        Map<AbstractInsnNode, Integer> positions = new IdentityHashMap<>();
        int i = 0;
        for (AbstractInsnNode n : method.instructions) if (n.getOpcode() >= 0) positions.put(n, i++);
        StringBuilder s = new StringBuilder();
        s.append(method.access).append(':').append(method.name).append(':').append(method.desc).append('\n');
        for (AbstractInsnNode n : method.instructions) {
            if (n.getOpcode() < 0) continue;
            s.append(n.getOpcode());
            if (n instanceof IntInsnNode) part(s, ((IntInsnNode)n).operand);
            else if (n instanceof VarInsnNode) part(s, ((VarInsnNode)n).var);
            else if (n instanceof TypeInsnNode) part(s, ((TypeInsnNode)n).desc);
            else if (n instanceof FieldInsnNode) {
                FieldInsnNode f = (FieldInsnNode)n; part(s, f.owner); part(s, f.name); part(s, f.desc);
            } else if (n instanceof MethodInsnNode) {
                MethodInsnNode c = (MethodInsnNode)n;
                part(s, c.owner); part(s, c.name); part(s, c.desc); part(s, c.itf);
            } else if (n instanceof InvokeDynamicInsnNode) {
                InvokeDynamicInsnNode c = (InvokeDynamicInsnNode)n;
                part(s, c.name); part(s, c.desc); part(s, c.bsm); for (Object a : c.bsmArgs) part(s, a);
            } else if (n instanceof JumpInsnNode) part(s, position(positions, ((JumpInsnNode)n).label, i));
            else if (n instanceof LdcInsnNode) part(s, ((LdcInsnNode)n).cst);
            else if (n instanceof IincInsnNode) {
                part(s, ((IincInsnNode)n).var); part(s, ((IincInsnNode)n).incr);
            } else if (n instanceof TableSwitchInsnNode) {
                TableSwitchInsnNode t = (TableSwitchInsnNode)n;
                part(s, t.min); part(s, t.max); part(s, position(positions, t.dflt, i));
                for (LabelNode l : t.labels) part(s, position(positions, l, i));
            } else if (n instanceof LookupSwitchInsnNode) {
                LookupSwitchInsnNode t = (LookupSwitchInsnNode)n;
                part(s, position(positions, t.dflt, i)); part(s, t.keys);
                for (LabelNode l : t.labels) part(s, position(positions, l, i));
            } else if (n instanceof MultiANewArrayInsnNode) {
                part(s, ((MultiANewArrayInsnNode)n).desc); part(s, ((MultiANewArrayInsnNode)n).dims);
            } else if (!(n instanceof InsnNode)) return "unsupported";
            s.append('\n');
        }
        for (TryCatchBlockNode t : method.tryCatchBlocks) {
            part(s, position(positions, t.start, i)); part(s, position(positions, t.end, i));
            part(s, position(positions, t.handler, i)); part(s, t.type); s.append('\n');
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(s.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : digest) hex.append(String.format(Locale.ROOT, "%02x", b & 255));
            return hex.toString();
        } catch (Exception e) { throw new AssertionError(e); }
    }
    private static void part(StringBuilder s, Object value) {
        // Weave relocates ASM at mod load time. Its package name is metadata, not
        // an executable operand: keep the existing offline type tags byte-for-byte.
        // Actual typed descriptors/handles/bootstrap values remain fully fingerprinted.
        String type = value instanceof Type ? "org.objectweb.asm.Type"
                : value instanceof Handle ? "org.objectweb.asm.Handle"
                : value instanceof ConstantDynamic ? "org.objectweb.asm.ConstantDynamic"
                : value == null ? null : value.getClass().getName();
        String text = value == null ? "null" : type + ":" + value;
        s.append(':').append(text.length()).append(':').append(text);
    }
    private static int position(Map<AbstractInsnNode, Integer> positions, LabelNode label, int end) {
        AbstractInsnNode n = label;
        while (n != null && n.getOpcode() < 0) n = n.getNext();
        return n == null ? end : positions.get(n);
    }
}
