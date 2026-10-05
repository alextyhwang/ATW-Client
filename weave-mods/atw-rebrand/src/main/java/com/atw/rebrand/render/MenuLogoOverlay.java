/*
 * Decompiled with CFR 0.152.
 *
 * Could not load the following classes:
 *  net.minecraft.client.Minecraft
 *  net.minecraft.client.gui.FontRenderer
 *  net.minecraft.client.gui.Gui
 *  net.minecraft.client.gui.GuiScreen
 *  net.minecraft.client.gui.ScaledResolution
 */
package com.atw.rebrand.render;

import com.atw.rebrand.ATWRebrand;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;

public class MenuLogoOverlay {
    private static boolean loggedRender;

    public static void render(GuiScreen screen) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || screen == null || mc.theWorld != null || !MenuLogoOverlay.isLunarMenuScreen(screen)) {
            return;
        }
        FontRenderer fontRenderer = mc.fontRendererObj;
        if (fontRenderer == null) {
            return;
        }
        ScaledResolution resolution = new ScaledResolution(mc);
        int centerX = resolution.getScaledWidth() / 2;
        int y = Math.max(34, resolution.getScaledHeight() / 2 - 32);
        int width = 150;
        int height = 22;
        int left = centerX - width / 2;
        int top = y;
        int right = centerX + width / 2;
        int bottom = top + height;
        Gui.drawRect((int)left, (int)top, (int)right, (int)bottom, (int)-300936939);
        String text = "ATW Client";
        int textX = centerX - fontRenderer.getStringWidth(text) / 2;
        int textY = top + 7;
        fontRenderer.drawStringWithShadow(text, (float)textX, (float)textY, 0xFFFFFF);
        if (!loggedRender) {
            loggedRender = true;
            ATWRebrand.log("fallback ATW Client text rendered over " + screen.getClass().getName());
        }
    }

    private static boolean isLunarMenuScreen(GuiScreen screen) {
        String className = screen.getClass().getName();
        return "net.minecraft.client.gui.GuiMainMenu".equals(className) || className.startsWith("com.moonsworth.lunar") && (className.toLowerCase().contains("menu") || className.toLowerCase().contains("main") || className.toLowerCase().contains("home"));
    }
}
