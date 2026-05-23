package com.atw.rebrand.hook;

import com.atw.rebrand.ATWRebrand;
import com.atw.rebrand.render.MenuLogoOverlay;
import net.weavemc.loader.api.Hook;
import org.jetbrains.annotations.NotNull;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.util.concurrent.atomic.AtomicBoolean;

public class MenuLogoOverlayHook extends Hook {
    private static final String GUI_SCREEN = "net/minecraft/client/gui/GuiScreen";
    private static final String MENU_LOGO_OVERLAY = Type.getInternalName(MenuLogoOverlay.class);
    private static final AtomicBoolean loggedInstall = new AtomicBoolean();

    public MenuLogoOverlayHook() {
        super(GUI_SCREEN);
    }

    @Override
    public void transform(@NotNull ClassNode node, @NotNull AssemblerConfig cfg) {
        int installed = 0;
        for (MethodNode method : node.methods) {
            if (!isDrawScreen(method) || alreadyCallsOverlay(method)) {
                continue;
            }

            for (AbstractInsnNode instruction : method.instructions.toArray()) {
                if (instruction.getOpcode() == Opcodes.RETURN) {
                    method.instructions.insertBefore(instruction, buildOverlayCall());
                    installed++;
                }
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
            if (instruction instanceof MethodInsnNode
                    && MENU_LOGO_OVERLAY.equals(((MethodInsnNode) instruction).owner)) {
                return true;
            }
        }
        return false;
    }

    private InsnList buildOverlayCall() {
        InsnList instructions = new InsnList();
        instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        instructions.add(new MethodInsnNode(
                Opcodes.INVOKESTATIC,
                MENU_LOGO_OVERLAY,
                "render",
                "(L" + GUI_SCREEN + ";)V",
                false
        ));
        return instructions;
    }
}

