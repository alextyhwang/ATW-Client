package com.atw.renderboost.hook;

import net.weavemc.api.Hook;
import org.objectweb.asm.tree.*;

public final class FrameHook extends Hook {
    public FrameHook() { super("net/minecraft/client/Minecraft"); }
    @Override public void transform(ClassNode node, AssemblerConfig cfg) {
        boolean found = false;
        for (MethodNode m : node.methods) {
            if (m.name.equals("runGameLoop") && m.desc.equals("()V")) {
                found = true;
                if (!HookSupport.installed(m)) {
                    HookSupport.scope(m, "frameStart", "frameEnd");
                    cfg.computeFrames();
                }
            }
            if ((m.name.equals("refreshResources") || m.name.equals("shutdownMinecraftApplet"))
                    && m.desc.equals("()V") && !HookSupport.installed(m)) {
                m.instructions.insert(HookSupport.call("invalidate", "()V"));
            }
        }
        System.out.println("[ATW Render Boost] Frame hook " + (found ? "installed" : "UNAVAILABLE"));
    }
}
