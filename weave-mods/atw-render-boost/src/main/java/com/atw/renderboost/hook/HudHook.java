package com.atw.renderboost.hook;

import net.weavemc.api.Hook;
import org.objectweb.asm.tree.*;

public final class HudHook extends Hook {
    public HudHook() { super("net/minecraft/client/gui/GuiIngame"); }
    @Override public void transform(ClassNode node, AssemblerConfig cfg) {
        for (MethodNode m : node.methods) {
            if (m.name.equals("renderGameOverlay") && m.desc.equals("(F)V") && !HookSupport.installed(m)) {
                HookSupport.scope(m, "hudEnter", "hudExit");
                cfg.computeFrames();
                System.out.println("[ATW Render Boost] HUD scope installed");
            }
        }
    }
}
