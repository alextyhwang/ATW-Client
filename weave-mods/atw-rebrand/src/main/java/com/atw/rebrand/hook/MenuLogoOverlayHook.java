/*
 * Decompiled with CFR 0.152.
 *
 * Could not load the following classes:
 *  net.weavemc.api.Hook
 *  net.weavemc.api.Hook$AssemblerConfig
 *  org.jetbrains.annotations.NotNull
 *  org.objectweb.asm.Type
 *  org.objectweb.asm.tree.AbstractInsnNode
 *  org.objectweb.asm.tree.ClassNode
 *  org.objectweb.asm.tree.InsnList
 *  org.objectweb.asm.tree.MethodInsnNode
 *  org.objectweb.asm.tree.MethodNode
 *  org.objectweb.asm.tree.VarInsnNode
 */
package com.atw.rebrand.hook;

import com.atw.rebrand.ATWRebrand;
import java.util.concurrent.atomic.AtomicBoolean;
import net.weavemc.api.Hook;
import org.jetbrains.annotations.NotNull;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

public class MenuLogoOverlayHook
extends Hook {
    private static final String GUI_SCREEN = "net/minecraft/client/gui/GuiScreen";
    private static final String MENU_LOGO_OVERLAY = "com/atw/rebrand/render/MenuLogoOverlay";
    private static final AtomicBoolean loggedInstall = new AtomicBoolean();

    public MenuLogoOverlayHook() {
        super(GUI_SCREEN);
    }

    public void transform(@NotNull ClassNode node, @NotNull Hook.AssemblerConfig cfg) {
        int installed = 0;
        for (MethodNode method : node.methods) {
            if (!this.isDrawScreen(method) || this.alreadyCallsOverlay(method)) continue;
            for (AbstractInsnNode instruction : method.instructions.toArray()) {
                if (instruction.getOpcode() != 177) continue;
                method.instructions.insertBefore(instruction, this.buildOverlayCall());
                ++installed;
            }
        }
        if (installed > 0) {
            cfg.computeFrames();
            if (loggedInstall.compareAndSet(false, true)) {
                ATWRebrand.log("fallback render hook installed in " + node.name);
            }
        }
    }

    private boolean isDrawScreen(MethodNode method) {
        return "drawScreen".equals(method.name) && "(IIF)V".equals(method.desc);
    }

    private boolean alreadyCallsOverlay(MethodNode method) {
        for (AbstractInsnNode instruction : method.instructions.toArray()) {
            if (!(instruction instanceof MethodInsnNode) || !MENU_LOGO_OVERLAY.equals(((MethodInsnNode)instruction).owner)) continue;
            return true;
        }
        return false;
    }

    private InsnList buildOverlayCall() {
        InsnList instructions = new InsnList();
        instructions.add((AbstractInsnNode)new VarInsnNode(25, 0));
        instructions.add((AbstractInsnNode)new MethodInsnNode(184, MENU_LOGO_OVERLAY, "render", "(Lnet/minecraft/client/gui/GuiScreen;)V", false));
        return instructions;
    }
}
