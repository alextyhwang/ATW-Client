package com.atw.renderboost.hook;

import net.weavemc.api.Hook;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

public final class FontHook extends Hook {
    // Computed from local vanilla 1.8.9 and reconstructed local OptiFine 1.8.
    public static final String VERIFIED = "fc591e964ea0a71526b24538ff62573d8f7c5fa6ebc9acd53abf9ef51fb61f46";
    private static final String FONT = "net/minecraft/client/gui/FontRenderer";
    public FontHook() { super(FONT); }
    @Override public void transform(ClassNode node, AssemblerConfig cfg) {
        boolean available = false;
        String reason = "renderDefaultChar(IZ)F not found";
        for (MethodNode m : node.methods) {
            if (m.name.equals("renderDefaultChar") && m.desc.equals("(IZ)F")) {
                if (HookSupport.installed(m)) { available = true; continue; }
                AbstractInsnNode first = null, last = null;
                int begins = 0, ends = 0;
                for (AbstractInsnNode n : m.instructions) {
                    if (!(n instanceof MethodInsnNode)) continue;
                    MethodInsnNode call = (MethodInsnNode) n;
                    if (!call.owner.equals("org/lwjgl/opengl/GL11")) continue;
                    if (call.name.equals("glBegin")) {
                        begins++;
                        first = n.getPrevious();
                        while (first != null && first.getOpcode() < 0) first = first.getPrevious();
                    }
                    if (call.name.equals("glEnd")) { ends++; last = n; }
                }
                if (m.tryCatchBlocks.isEmpty() && begins == 1 && ends == 1 && first != null && last != null
                        && VERIFIED.equals(PrimitiveShape.fingerprint(first, last)) && noIncomingEdges(m, first, last)) {
                    install(m, first, last);
                    cfg.computeFrames();
                    available = true;
                } else reason = rejectionReason(m, begins, ends);
            }
            if ((m.name.equals("onResourceManagerReload") || m.name.equals("readFontTexture")
                    || m.name.equals("readGlyphSizes")) && !HookSupport.installed(m))
                m.instructions.insert(HookSupport.call("invalidate", "()V"));
        }
        System.out.println("[ATW Render Boost] Glyph cache " + (available ? "verified/available (opt-in)"
                : "UNAVAILABLE: " + reason + "; original rendering retained"));
    }

    // Diagnostics only: this never admits a primitive or changes the fingerprint allowlist.
    private static String rejectionReason(MethodNode method, int begins, int ends) {
        boolean vertices = false, recordsList = false;
        for (AbstractInsnNode instruction : method.instructions) {
            if (!(instruction instanceof MethodInsnNode)) continue;
            MethodInsnNode call = (MethodInsnNode) instruction;
            if (call.owner.equals("net/minecraft/client/renderer/WorldRenderer")
                    && call.name.equals("endVertex") && call.desc.equals("()V")) vertices = true;
            if (call.owner.equals("org/lwjgl/opengl/GL11")
                    && call.name.equals("glNewList") && call.desc.equals("(II)V")) recordsList = true;
        }
        if (begins == 0 && ends == 0 && vertices && recordsList)
            return "WorldRenderer batching with display-list recording; no isolated GL11 glyph primitive";
        return "unsupported font bytecode";
    }

    private static boolean noIncomingEdges(MethodNode m, AbstractInsnNode first, AbstractInsnNode last) {
        java.util.Set<LabelNode> inside = new java.util.HashSet<>();
        for (AbstractInsnNode n = first; n != null; n = n.getNext()) {
            if (n instanceof LabelNode) inside.add((LabelNode) n);
            if (n == last) break;
        }
        for (AbstractInsnNode n : m.instructions) {
            if (n instanceof JumpInsnNode && inside.contains(((JumpInsnNode) n).label)) return false;
            if (n instanceof TableSwitchInsnNode) {
                TableSwitchInsnNode t = (TableSwitchInsnNode)n;
                if (inside.contains(t.dflt) || !java.util.Collections.disjoint(inside,t.labels)) return false;
            }
            if (n instanceof LookupSwitchInsnNode) {
                LookupSwitchInsnNode t = (LookupSwitchInsnNode)n;
                if (inside.contains(t.dflt) || !java.util.Collections.disjoint(inside,t.labels)) return false;
            }
        }
        for (TryCatchBlockNode t : m.tryCatchBlocks)
            if (inside.contains(t.start) || inside.contains(t.end) || inside.contains(t.handler)) return false;
        return true;
    }

    private static void install(MethodNode m, AbstractInsnNode first, AbstractInsnNode last) {
        LabelNode start = new LabelNode(), end = new LabelNode(), done = new LabelNode(), handler = new LabelNode();
        InsnList entry = new InsnList();
        entry.add(new VarInsnNode(Opcodes.ALOAD, 0));
        entry.add(new VarInsnNode(Opcodes.ILOAD, 3)); // Atlas u/v, italic shear and actual quad width.
        entry.add(new VarInsnNode(Opcodes.ILOAD, 4));
        entry.add(new VarInsnNode(Opcodes.ILOAD, 5));
        entry.add(new VarInsnNode(Opcodes.FLOAD, 7));
        entry.add(new VarInsnNode(Opcodes.ALOAD, 0));
        entry.add(new FieldInsnNode(Opcodes.GETFIELD, FONT, "posX", "F"));
        entry.add(new VarInsnNode(Opcodes.ALOAD, 0));
        entry.add(new FieldInsnNode(Opcodes.GETFIELD, FONT, "posY", "F"));
        entry.add(HookSupport.call("beginGlyph", "(Ljava/lang/Object;IIIFFF)Z"));
        entry.add(new JumpInsnNode(Opcodes.IFNE, done));
        entry.add(start);
        m.instructions.insertBefore(first, entry);
        InsnList finish = new InsnList();
        finish.add(end);
        finish.add(HookSupport.call("endGlyph", "()V"));
        finish.add(done);
        m.instructions.insert(last, finish);
        m.instructions.add(handler);
        m.instructions.add(HookSupport.call("abortGlyph", "()V"));
        m.instructions.add(new InsnNode(Opcodes.ATHROW));
        // Before existing outer catches, to release our recording even when they handle the exception.
        m.tryCatchBlocks.add(0, new TryCatchBlockNode(start, end, handler, null));
    }
}
