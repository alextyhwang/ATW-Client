package com.atw.renderboost.hook;

import com.atw.renderboost.terrain.TerrainControl;
import net.weavemc.api.Hook;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

/** All checks precede mutations. Unknown classes retain every original instruction. */
public final class TerrainHook extends Hook implements Opcodes {
    public static final String LIST = "net/minecraft/client/renderer/VboRenderList";
    public static final String BUFFER = "net/minecraft/client/renderer/vertex/VertexBuffer";
    public static final String GLOBAL = "net/minecraft/client/renderer/RenderGlobal";
    public static final String RUNTIME = "com/atw/renderboost/terrain/TerrainRuntime";
    public static final String BRIDGE_OWNER = "com/moonsworth/lunar/OCCOIRIHCIHOCIOCICROCRHRHRIHCO/HHCCHRHORCRIHRIIHRIICIHHCOOCHR/CHIRCOROIOHCCIIRRCCHOIHHHIOIOH/CHIRCOROIOHCCIIRRCCHOIHHHIOIOH/CCIRCHROCOHOCHICRORRCIRCRRIOHR";
    static final String GSM = "net/minecraft/client/renderer/GlStateManager";
    static final String ACCESS = "com/atw/renderboost/terrain/TerrainPointerAccess";
    static final String[] TARGETS = {LIST, BUFFER, GLOBAL, "net/minecraft/client/renderer/GlStateManager",
            "net/minecraft/client/renderer/OpenGlHelper", "net/minecraft/client/renderer/ChunkRenderContainer",
            "net/minecraft/client/renderer/chunk/RenderChunk", "net/minecraft/client/renderer/vertex/VertexFormat",
            "net/minecraft/client/renderer/vertex/VertexFormatElement", "net/minecraft/client/renderer/vertex/DefaultVertexFormats",
            "net/optifine/Config", "org/lwjgl/opengl/Display", "org/lwjgl/opengl/GLContext", "org/lwjgl/opengl/ContextGL", BRIDGE_OWNER};
    public TerrainHook() { super(TARGETS); }
    public static MethodInsnNode call(String name, String desc) {
        return new MethodInsnNode(INVOKESTATIC, RUNTIME, name, desc, false);
    }
    @Override public void transform(ClassNode node, AssemblerConfig cfg) {
        TerrainHookEvidenceOutput.record(node);
        if (node.interfaces.contains(ACCESS) || installed(node)
                || node.methods.stream().anyMatch(m -> m.name.equals("atwboost$bridgeActive"))) return;
        if (!TerrainHookStage.matches(node)) {
            TerrainControl.admit(node.name, false);
            System.out.println("[ATW Render Boost] Terrain UNAVAILABLE: rejected " + node.name); return;
        }
        if (LIST.equals(node.name)) {
            MethodNode layer = method(node, "renderChunkLayer", "(Lnet/minecraft/util/EnumWorldBlockLayer;)V");
            MethodInsnNode bind = null, setup = null, epilogue = null;
            for (AbstractInsnNode n : layer.instructions) if (n instanceof MethodInsnNode) {
                MethodInsnNode c = (MethodInsnNode)n;
                if (BUFFER.equals(c.owner) && c.name.equals("bindBuffer")) bind = c;
                if (LIST.equals(c.owner) && c.name.equals("setupArrayPointers")) setup = c;
                if (c.name.equals("glBindBuffer") && c.owner.equals("net/minecraft/client/renderer/OpenGlHelper")) epilogue = c;
            }
            // Full captured body proves exactly one standalone seam and no incoming edges into it.
            if (bind == null || setup == null || epilogue == null
                    || previous(bind).getOpcode() != ALOAD || ((VarInsnNode)previous(bind)).var != 4
                    || previous(setup).getOpcode() != ALOAD || ((VarInsnNode)previous(setup)).var != 0) {
                TerrainControl.admit(node.name, false); return;
            }
            AbstractInsnNode receiver = previous(bind), listReceiver = previous(setup);
            InsnList replacement = new InsnList(); replacement.add(new VarInsnNode(ALOAD, 0));
            replacement.add(new VarInsnNode(ALOAD, 4));
            // No added cross-class field/accessor reference executes while a prerequisite fails.
            replacement.add(call("pointers", "(Ljava/lang/Object;Ljava/lang/Object;)V"));
            layer.instructions.insertBefore(receiver, replacement);
            layer.instructions.remove(receiver); layer.instructions.remove(bind);
            layer.instructions.remove(listReceiver); layer.instructions.remove(setup);
            for (AbstractInsnNode n : layer.instructions.toArray()) if (n instanceof MethodInsnNode
                    && ((MethodInsnNode)n).owner.equals(BUFFER) && ((MethodInsnNode)n).name.equals("drawArrays")) {
                // Count only completed draws. A region-delegating buffer is identified at runtime.
                MethodInsnNode draw = (MethodInsnNode)n;
                AbstractInsnNode mode = previous(draw), drawReceiver = previous(mode);
                if (((VarInsnNode)drawReceiver).var != 4) continue;
                InsnList counter = new InsnList();
                counter.add(new VarInsnNode(ALOAD, 4));
                counter.add(call("drawCompleted", "(Ljava/lang/Object;)V")); layer.instructions.insert(draw, counter);
            }
            layer.instructions.insertBefore(previous(previous(epilogue)), call("layerExit", "()V"));
            scope(layer, "layerEnter", "layerReturn", true);
            MethodNode bridge = new MethodNode(ACC_PUBLIC | ACC_FINAL | ACC_SYNTHETIC,
                    "atwboost$originalPointers", "(Ljava/lang/Object;)V", null, null);
            bridge.instructions.add(new VarInsnNode(ALOAD, 1)); bridge.instructions.add(new TypeInsnNode(CHECKCAST, BUFFER));
            bridge.instructions.add(new MethodInsnNode(INVOKEVIRTUAL, BUFFER, "bindBuffer", "()V", false));
            bridge.instructions.add(new VarInsnNode(ALOAD, 0));
            bridge.instructions.add(new MethodInsnNode(INVOKEVIRTUAL, LIST, "setupArrayPointers", "()V", false));
            bridge.instructions.add(new InsnNode(RETURN)); bridge.maxLocals = 2; bridge.maxStack = 1;
            node.methods.add(bridge); node.interfaces.add(ACCESS); cfg.computeFrames();
        } else if (GSM.equals(node.name)) {
            node.methods.add(bridgeAccessor(node)); cfg.computeFrames();
        } else if (BUFFER.equals(node.name)) {
            for (MethodNode m : node.methods) if (m.name.equals("bufferData") || m.name.equals("deleteGlBuffers") || m.name.equals("setVboRegion")) {
                InsnList pre = new InsnList(); pre.add(new VarInsnNode(ALOAD, 0)); pre.add(call("bufferInvalidated", "(Ljava/lang/Object;)V"));
                m.instructions.insert(pre);
            }
            node.interfaces.add("com/atw/renderboost/terrain/TerrainBufferAccess");
            addGetter(node, "vbo", "glBufferId", "I"); addGetter(node, "format", "vertexFormat", "Lnet/minecraft/client/renderer/vertex/VertexFormat;");
            addGetter(node, "region", "vboRegion", "Lnet/optifine/render/VboRegion;");
            addGetter(node, "count", "count", "I"); addGetter(node, "mode", "drawMode", "I");
        } else if (GLOBAL.equals(node.name)) {
            scope(method(node, "renderBlockLayer", "(Lnet/minecraft/util/EnumWorldBlockLayer;)V"), "outerEnter", "outerExit", false);
            for (MethodNode m : node.methods) if (m.name.equals("loadRenderers") || m.name.equals("setWorldAndLoadRenderers"))
                m.instructions.insert(call("invalidate", "()V"));
            cfg.computeFrames();
        }
        TerrainControl.admit(node.name, true);
        System.out.println("[ATW Render Boost] Terrain captured evidence accepted: " + node.name + "; default OFF");
    }
    static void addGetter(ClassNode node, String suffix, String field, String desc) {
        boolean integer = desc.equals("I");
        MethodNode m = new MethodNode(ACC_PUBLIC | ACC_FINAL | ACC_SYNTHETIC, "atwboost$" + suffix,
                integer ? "()I" : "()Ljava/lang/Object;", null, null);
        m.instructions.add(new VarInsnNode(ALOAD, 0)); m.instructions.add(new FieldInsnNode(GETFIELD, BUFFER, field, desc));
        m.instructions.add(new InsnNode(integer ? IRETURN : ARETURN)); m.maxLocals = 1; m.maxStack = 1; node.methods.add(m);
    }
    static MethodNode bridgeAccessor(ClassNode state) {
        // The captured wrapper has the exact flag + nullable static reference gate. Reuse
        // that same GETSTATIC operand; never resolve the unloaded declared object's class.
        FieldInsnNode reference = null;
        for (AbstractInsnNode n : TerrainHookStage.mergedMethod(state, "handler$zde000$bridge$pushMatrix",
                "(Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;)V").instructions)
            if (n instanceof FieldInsnNode && ((FieldInsnNode)n).owner.equals(BRIDGE_OWNER)) { reference = (FieldInsnNode)n; break; }
        if (reference == null) throw new IllegalArgumentException("Captured bridge reference absent");
        MethodNode m = new MethodNode(ACC_PUBLIC | ACC_STATIC | ACC_SYNTHETIC, "atwboost$bridgeActive", "()Z", null, null);
        LabelNode no = new LabelNode();
        m.instructions.add(new FieldInsnNode(GETSTATIC, GSM, "bridge$modelView", "Z"));
        m.instructions.add(new JumpInsnNode(IFEQ, no));
        m.instructions.add(new FieldInsnNode(GETSTATIC, reference.owner, reference.name, reference.desc));
        m.instructions.add(new JumpInsnNode(IFNULL, no));
        m.instructions.add(new InsnNode(ICONST_1)); m.instructions.add(new InsnNode(IRETURN));
        m.instructions.add(no); m.instructions.add(new InsnNode(ICONST_0)); m.instructions.add(new InsnNode(IRETURN));
        m.maxStack = 1; m.maxLocals = 0; return m;
    }
    static void scope(MethodNode m, String enter, String exit, boolean receiver) {
        m.maxStack = Math.max(m.maxStack, 8);
        LabelNode start = new LabelNode(), end = new LabelNode(), handler = new LabelNode();
        InsnList head = new InsnList();
        if (receiver) { head.add(new VarInsnNode(ALOAD, 0)); head.add(new VarInsnNode(ALOAD, 1)); }
        head.add(call(enter, receiver ? "(Ljava/lang/Object;Ljava/lang/Object;)V" : "()V")); head.add(start); m.instructions.insert(head);
        for (AbstractInsnNode n : m.instructions.toArray()) if (n.getOpcode() == RETURN) m.instructions.insertBefore(n, call(exit, "()V"));
        m.instructions.add(end); m.instructions.add(handler);
        if (receiver) m.instructions.add(call("layerAbort", "(Ljava/lang/Throwable;)Ljava/lang/Throwable;"));
        else m.instructions.add(call(exit, "()V"));
        m.instructions.add(new InsnNode(ATHROW)); m.tryCatchBlocks.add(new TryCatchBlockNode(start, end, handler, null));
    }
    static boolean installed(ClassNode c) {
        for (MethodNode m : c.methods) for (AbstractInsnNode n : m.instructions)
            if (n instanceof MethodInsnNode && RUNTIME.equals(((MethodInsnNode)n).owner)) return true;
        return false;
    }
    static MethodNode method(ClassNode c, String name, String desc) {
        for (MethodNode m : c.methods) if (m.name.equals(name) && m.desc.equals(desc)) return m;
        throw new IllegalArgumentException("Missing captured method");
    }
    static AbstractInsnNode previous(AbstractInsnNode n) {
        do { n = n.getPrevious(); } while (n != null && n.getOpcode() < 0); return n;
    }
}
