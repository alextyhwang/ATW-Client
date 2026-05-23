package com.atw.rebrand.hook;

import com.atw.rebrand.ATWRebrand;
import net.weavemc.loader.api.Hook;
import org.jetbrains.annotations.NotNull;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.concurrent.atomic.AtomicBoolean;

public class LunarLogoResourceHook extends Hook {
    private static final String LUNAR_PACKAGE = "com/moonsworth/lunar";
    private static final String ATW_LOGO_RESOURCE = "atw-rebrand/logo.png";
    private static final AtomicBoolean loggedTarget = new AtomicBoolean();
    private static final AtomicBoolean loggedReplacement = new AtomicBoolean();

    public LunarLogoResourceHook() {
        super("*");
    }

    @Override
    public void transform(@NotNull ClassNode node, @NotNull AssemblerConfig cfg) {
        if (!node.name.startsWith(LUNAR_PACKAGE)) {
            return;
        }

        boolean sawLogoReference = false;
        int replacements = 0;
        for (MethodNode method : node.methods) {
            for (AbstractInsnNode instruction : method.instructions.toArray()) {
                if (!(instruction instanceof LdcInsnNode)) {
                    continue;
                }

                LdcInsnNode ldc = (LdcInsnNode) instruction;
                if (!(ldc.cst instanceof String)) {
                    continue;
                }

                String value = (String) ldc.cst;
                if (!isLogoPath(value)) {
                    continue;
                }

                sawLogoReference = true;
                if (shouldReplaceLogoPath(value)) {
                    ldc.cst = ATW_LOGO_RESOURCE;
                    replacements++;
                }
            }
        }

        if (sawLogoReference && loggedTarget.compareAndSet(false, true)) {
            ATWRebrand.log("target class found: " + node.name);
        }
        if (replacements > 0 && loggedReplacement.compareAndSet(false, true)) {
            ATWRebrand.log("logo string replaced with " + ATW_LOGO_RESOURCE + " in " + node.name);
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

