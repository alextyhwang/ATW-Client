package com.atw.rebrand.hook;

import net.weavemc.api.Hook;
import org.jetbrains.annotations.NotNull;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

/** Runs once per resource reload, after Minecraft resolves the player's packs. */
public final class BrandingResourcesHook extends Hook {
    private static final String OWNER = "com/atw/rebrand/BrandingResources";
    public BrandingResourcesHook() { super("net/minecraft/client/resources/SimpleReloadableResourceManager"); }

    @Override public void transform(@NotNull ClassNode node, @NotNull AssemblerConfig cfg) {
        for (MethodNode method : node.methods) {
            if (!method.name.equals("reloadResources") || !method.desc.equals("(Ljava/util/List;)V")) continue;
            boolean installed = false;
            for (AbstractInsnNode instruction : method.instructions.toArray()) {
                if (instruction instanceof MethodInsnNode && ((MethodInsnNode) instruction).owner.equals(OWNER)) installed = true;
            }
            if (installed) continue;
            InsnList call = new InsnList();
            call.add(new VarInsnNode(Opcodes.ALOAD, 1));
            call.add(new MethodInsnNode(Opcodes.INVOKESTATIC, OWNER, "append", "(Ljava/util/List;)Ljava/util/List;", false));
            call.add(new VarInsnNode(Opcodes.ASTORE, 1));
            method.instructions.insert(call);
            cfg.computeFrames();
        }
    }
}
