package com.atw.renderboost.hook;

import java.io.InputStream;
import java.util.*;
import net.weavemc.api.Hook;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import com.atw.renderboost.probe.NameProbeRuntime;

/** Diagnostic method-only gate, deliberately independent of optimization admission. */
public final class NameProbeHook extends Hook implements Opcodes {
    public static final String RUNTIME = "com/atw/renderboost/probe/NameProbeRuntime";
    public static final String[] TARGETS = {"net/minecraft/client/renderer/entity/RendererLivingEntity",
            "net/minecraft/entity/EntityLivingBase", "net/minecraft/entity/Entity", "net/minecraft/entity/player/EntityPlayer",
            "net/minecraft/client/gui/FontRenderer", "net/minecraft/util/ChatComponentStyle", "net/minecraft/util/ChatStyle"};
    private static final Properties EVIDENCE = new Properties();
    static {
        try (InputStream in = NameProbeHook.class.getResourceAsStream("/name-probe-evidence.properties")) {
            if (in != null) EVIDENCE.load(in);
            for (String key : EVIDENCE.stringPropertyNames())
                NameProbeRuntime.coverage(key,"UNAVAILABLE: hook not observed; original path retained");
        } catch (Exception e) { System.err.println("[ATW name probe] evidence unavailable; retaining originals"); }
    }
    public NameProbeHook() { super(TARGETS); }
    public static int metric(String owner, MethodNode m) {
        if (owner.equals(TARGETS[0])) {
            if (m.name.equals("renderName") && (m.desc.equals("(Lnet/minecraft/entity/EntityLivingBase;DDD)V")
                    || m.desc.equals("(Lnet/minecraft/entity/Entity;DDD)V"))) return 0;
            if (m.name.equals("impl$renderLivingLabel") && m.desc.equals("(Lnet/minecraft/entity/EntityLivingBase;Lnet/kyori/adventure/text/TextComponent;DDDI)D")) return 7;
            if (m.name.equals("impl$renderLabelSneaking") && m.desc.equals("(Lnet/kyori/adventure/text/TextComponent;DDD)V")) return 8;
            if (m.name.equals("impl$drawLabel") && m.desc.equals("(Lnet/minecraft/entity/EntityLivingBase;Lnet/kyori/adventure/text/TextComponent;DDDZ)D")) return 9;
        }
        if (owner.equals(TARGETS[1]) && m.name.equals("bridge$getDisplayNameComponent") && m.desc.equals("()Lnet/kyori/adventure/text/Component;")) return 1;
        if ((owner.equals(TARGETS[2]) || owner.equals(TARGETS[3])) && m.name.equals("getDisplayName") && m.desc.equals("()Lnet/minecraft/util/IChatComponent;")) return 2;
        if (owner.equals(TARGETS[2]) && m.name.equals("getHoverEvent") && m.desc.equals("()Lnet/minecraft/event/HoverEvent;")) return 3;
        if (owner.equals(TARGETS[4]) && m.name.equals("bridge$getStringWidth") && m.desc.equals("(Lnet/kyori/adventure/text/Component;)F")) return 4;
        if (owner.equals(TARGETS[5]) && m.name.equals("moonBridge$asAdventureComponent") && m.desc.equals("()Lnet/kyori/adventure/text/Component;")) return 5;
        if (owner.equals(TARGETS[6]) && m.name.equals("moonBridge$asAdventureStyle") && m.desc.equals("()Lnet/kyori/adventure/text/format/Style;")) return 6;
        return -1;
    }
    public static boolean matches(String owner, MethodNode m) {
        String expected = EVIDENCE.getProperty(owner + "." + m.name + m.desc);
        // This fingerprints the supplied node directly. No ClassWriter round trip, class shape,
        // ASM runtime class-name tags, or dependency on already-injected ATWHooks elsewhere.
        return metric(owner,m) >= 0 && expected != null && expected.equals(TerrainEvidence.fingerprint(m));
    }
    @Override public void transform(ClassNode node, AssemblerConfig cfg) {
        Set<String> observed = new HashSet<>();
        NameProbeHookStage stage = null;
        try { stage = new NameProbeHookStage(node); }
        catch (IllegalArgumentException e) { /* Invalid/ambiguous loader proof retains originals. */ }
        for (MethodNode m : node.methods) {
            String name;
            try { name = NameProbeHookStage.logicalName(m.name); }
            catch (IllegalArgumentException e) { continue; }
            MethodNode identity = new MethodNode(m.access,name,m.desc,null,null);
            int metric = metric(node.name,identity);
            if (metric < 0) continue;
            String signature = node.name + "." + name + m.desc;
            observed.add(signature);
            boolean installed = false;
            for (AbstractInsnNode i : m.instructions) if (i instanceof MethodInsnNode && RUNTIME.equals(((MethodInsnNode)i).owner)) installed = true;
            MethodNode comparison = null;
            try { if (stage != null) comparison = stage.comparison(m); }
            catch (IllegalArgumentException e) { /* Unknown provenance/reference retains this original. */ }
            String result;
            if (comparison == null) result = "UNAVAILABLE: invalid/ambiguous Weave conflict-name proof; original path retained";
            else if (installed) result = "installed";
            else if (!matches(node.name,comparison)) result = "UNAVAILABLE: unknown exact method body/access/descriptor; original path retained";
            else { wrap(m, metric); cfg.computeFrames(); result = "installed"; }
            NameProbeRuntime.coverage(signature,result);
            System.out.println("[ATW name probe] " + signature + ": " + result);
        }
        for (String key : EVIDENCE.stringPropertyNames()) if (key.startsWith(node.name+".") && !observed.contains(key)) {
            NameProbeRuntime.coverage(key,"UNAVAILABLE: exact method missing; original path retained");
            System.out.println("[ATW name probe] " + key + ": UNAVAILABLE (missing exact method); original path retained");
        }
    }
    private static MethodInsnNode call(String name, String desc) { return new MethodInsnNode(INVOKESTATIC,RUNTIME,name,desc,false); }
    /** Preserve original nodes/handlers in order, all return values and the original Throwable. */
    static void wrap(MethodNode m, int metric) {
        int token = m.maxLocals;
        // Also handle hook-stage nodes whose maxLocals hasn't been assembled yet.
        int args = (m.access & ACC_STATIC) == 0 ? 1 : 0;
        for (Type t : Type.getArgumentTypes(m.desc)) args += t.getSize();
        token = Math.max(token,args);
        for (AbstractInsnNode i : m.instructions) {
            if (i instanceof VarInsnNode) {
                int op=i.getOpcode(), size=(op==LLOAD || op==DLOAD || op==LSTORE || op==DSTORE) ? 2 : 1;
                token = Math.max(token,((VarInsnNode)i).var+size);
            } else if (i instanceof IincInsnNode) token=Math.max(token,((IincInsnNode)i).var+1);
        }
        m.maxLocals=token+1; m.maxStack += 4;
        LabelNode skip = new LabelNode(), start = new LabelNode(), end = new LabelNode(), handler = new LabelNode();
        InsnList head = new InsnList();
        head.add(new InsnNode(ICONST_0)); head.add(new VarInsnNode(ISTORE,token));
        head.add(new FieldInsnNode(GETSTATIC,RUNTIME,"collecting","Z")); head.add(new JumpInsnNode(IFEQ,skip));
        head.add(new IntInsnNode(BIPUSH,metric));
        head.add(metric==0 ? new VarInsnNode(ALOAD,1) : new InsnNode(ACONST_NULL));
        head.add(call("enter","(ILjava/lang/Object;)I")); head.add(new VarInsnNode(ISTORE,token));
        head.add(skip); head.add(start); m.instructions.insert(head);
        for (AbstractInsnNode i : m.instructions.toArray()) if (i.getOpcode() >= IRETURN && i.getOpcode() <= RETURN)
            m.instructions.insertBefore(i,cleanup(token,false));
        m.instructions.add(end); m.instructions.add(handler);
        m.instructions.add(cleanup(token,true)); m.instructions.add(new InsnNode(ATHROW));
        m.tryCatchBlocks.add(new TryCatchBlockNode(start,end,handler,null));
    }
    private static InsnList cleanup(int token, boolean exceptional) {
        InsnList out = new InsnList(); LabelNode skip = new LabelNode();
        out.add(new VarInsnNode(ILOAD,token)); out.add(new JumpInsnNode(IFEQ,skip));
        out.add(new VarInsnNode(ILOAD,token)); out.add(new InsnNode(exceptional ? ICONST_1 : ICONST_0));
        out.add(call("exit","(IZ)V")); out.add(skip); return out;
    }
}
