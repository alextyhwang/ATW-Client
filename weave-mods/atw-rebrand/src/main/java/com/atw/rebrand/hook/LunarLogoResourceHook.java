/*
 * Decompiled with CFR 0.152.
 *
 * Could not load the following classes:
 *  net.weavemc.api.Hook
 *  net.weavemc.api.Hook$AssemblerConfig
 *  org.jetbrains.annotations.NotNull
 *  org.objectweb.asm.tree.AbstractInsnNode
 *  org.objectweb.asm.tree.ClassNode
 *  org.objectweb.asm.tree.LdcInsnNode
 *  org.objectweb.asm.tree.MethodNode
 */
package com.atw.rebrand.hook;

import com.atw.rebrand.ATWRebrand;
import java.util.concurrent.atomic.AtomicBoolean;
import net.weavemc.api.Hook;
import org.jetbrains.annotations.NotNull;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodNode;

public class LunarLogoResourceHook
extends Hook {
    private static final String LUNAR_PACKAGE = "com/moonsworth/lunar";
    private static final String ATW_LOGO_RESOURCE = "atw-rebrand/logo.png";
    private static final AtomicBoolean loggedTarget = new AtomicBoolean();
    private static final AtomicBoolean loggedReplacement = new AtomicBoolean();

    public LunarLogoResourceHook() {
        super("*");
    }

    public void transform(@NotNull ClassNode node, @NotNull Hook.AssemblerConfig cfg) {
        if (!node.name.startsWith(LUNAR_PACKAGE)) {
            return;
        }
        boolean sawLogoReference = false;
        int replacements = 0;
        for (MethodNode method : node.methods) {
            for (AbstractInsnNode instruction : method.instructions.toArray()) {
                String value;
                if (!(instruction instanceof LdcInsnNode)) continue;
                LdcInsnNode ldc = (LdcInsnNode)instruction;
                if (!(ldc.cst instanceof String) || !this.isLogoPath(value = (String)ldc.cst)) continue;
                sawLogoReference = true;
                if (!this.shouldReplaceLogoPath(value)) continue;
                ldc.cst = ATW_LOGO_RESOURCE;
                ++replacements;
            }
        }
        if (sawLogoReference && loggedTarget.compareAndSet(false, true)) {
            ATWRebrand.log("target class found: " + node.name);
        }
        if (replacements > 0 && loggedReplacement.compareAndSet(false, true)) {
            ATWRebrand.log("logo string replaced with atw-rebrand/logo.png in " + node.name);
        }
    }

    private boolean isLogoPath(String value) {
        String normalized = value.toLowerCase();
        return normalized.contains("logo") && (normalized.endsWith(".png") || normalized.endsWith(".jpg"));
    }

    private boolean shouldReplaceLogoPath(String value) {
        return "logo/logo-128x117.png".equals(value);
    }
}
