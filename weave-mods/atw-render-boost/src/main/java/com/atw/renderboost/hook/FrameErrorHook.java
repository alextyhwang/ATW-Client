package com.atw.renderboost.hook;

import net.weavemc.api.Hook;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

/** Guards only the inspected frame pair and observes the original diagnostic's GL result. */
public final class FrameErrorHook extends Hook implements Opcodes {
    static final String POLICY = "com/atw/renderboost/FrameErrorPolicy";
    public FrameErrorHook() { super(FrameErrorPattern.GAME); }

    @Override public void transform(ClassNode node, AssemblerConfig cfg) {
        if (!FrameErrorPattern.GAME.equals(node.name)) { unavailable("unexpected class"); return; }
        MethodNode frame = null, diagnostic = null;
        for (MethodNode m : node.methods) {
            if (m.name.equals("runGameLoop") && m.desc.equals("()V")) {
                if (frame != null) { unavailable("ambiguous frame method"); return; }
                frame = m;
            }
            if (m.name.equals("checkGLError") && m.desc.equals(FrameErrorPattern.DESC)) {
                if (diagnostic != null) { unavailable("ambiguous diagnostic method"); return; }
                diagnostic = m;
            }
        }
        if (frame == null || diagnostic == null || (frame.access & (ACC_STATIC | ACC_ABSTRACT | ACC_NATIVE)) != 0) {
            unavailable("missing/unsupported frame or diagnostic method"); return;
        }
        for (AbstractInsnNode n : frame.instructions) if (n instanceof MethodInsnNode && POLICY.equals(((MethodInsnNode)n).owner)) return;
        if (!FrameErrorPattern.DIAGNOSTIC_HASH.equals(FrameErrorPattern.diagnosticHash(diagnostic))) {
            unavailable("unsupported diagnostic body"); return;
        }
        MethodInsnNode poll = null;
        for (AbstractInsnNode n : diagnostic.instructions) if (n instanceof MethodInsnNode) {
            MethodInsnNode call = (MethodInsnNode)n;
            if (call.getOpcode() == INVOKESTATIC && !call.itf && "org/lwjgl/opengl/GL11".equals(call.owner)
                    && "glGetError".equals(call.name) && "()I".equals(call.desc)) {
                if (poll != null) { unavailable("ambiguous diagnostic poll"); return; }
                poll = call;
            }
        }
        if (poll == null) { unavailable("missing diagnostic poll"); return; }
        MethodInsnNode pre = null, post = null;
        int count = 0;
        for (AbstractInsnNode n : frame.instructions) {
            if (n instanceof MethodInsnNode && ((MethodInsnNode)n).name.equals("checkGLError")) {
                count++;
                MethodInsnNode call = (MethodInsnNode)n;
                if (FrameErrorPattern.receiver(call, "Pre render") != null) pre = call;
                else if (FrameErrorPattern.receiver(call, "Post render") != null) post = call;
                else { unavailable("unsupported error-check call site"); return; }
            }
        }
        if (count != 2 || pre == null || post == null) { unavailable("expected exactly one Pre render and one Post render check"); return; }
        AbstractInsnNode receiver = FrameErrorPattern.receiver(pre, "Pre render");
        AbstractInsnNode postReceiver = FrameErrorPattern.receiver(post, "Post render");
        boolean ordered = false;
        for (AbstractInsnNode n = pre.getNext(); n != null; n = n.getNext()) if (n == postReceiver) { ordered = true; break; }
        if (!ordered || !FrameErrorPattern.safeRegion(frame, pre.getNext(), postReceiver)) {
            unavailable("unsupported control flow between frame checks"); return;
        }
        guard(frame, receiver, pre, "skipPreRenderCheck");
        guard(frame, postReceiver, post, "skipPostRenderCheck");
        InsnList observer = new InsnList();
        observer.add(new InsnNode(DUP));
        observer.add(new MethodInsnNode(INVOKESTATIC, POLICY, "observeError", "(I)V", false));
        diagnostic.instructions.insert(poll, observer);
        cfg.computeFrames();
        System.out.println("[ATW Render Boost] Frame error policy installed; default OFF; sampled post interval 250ms; GL error locks original diagnostics until restart; startup/capability polls retained");
    }
    private static void guard(MethodNode frame, AbstractInsnNode receiver, MethodInsnNode call, String method) {
        LabelNode after = new LabelNode();
        InsnList guard = new InsnList();
        guard.add(new MethodInsnNode(INVOKESTATIC, POLICY, method, "()Z", false));
        guard.add(new JumpInsnNode(IFNE, after));
        frame.instructions.insertBefore(receiver, guard);
        frame.instructions.insert(call, after);
    }
    private static void unavailable(String reason) {
        System.out.println("[ATW Render Boost] Frame error policy UNAVAILABLE: " + reason + "; original checks retained");
    }
}
