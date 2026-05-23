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
        if (mc == null || screen == null || mc.theWorld != null || !isLunarMenuScreen(screen)) {
            return;
        }

        FontRenderer fontRenderer = mc.fontRendererObj;
        if (fontRenderer == null) {
            return;
        }

        ScaledResolution resolution = new ScaledResolution(mc);
        int centerX = resolution.getScaledWidth() / 2;
        int y = Math.max(18, resolution.getScaledHeight() / 5 - 18);
        int width = 170;
        int height = 30;
        int left = centerX - width / 2;
        int top = y - 8;
        int right = centerX + width / 2;
        int bottom = top + height;

        Gui.drawRect(left, top, right, bottom, 0xDD111216);
        Gui.drawRect(left, bottom - 2, right, bottom, 0xFF32D9C8);

        String text = ATWRebrand.NAME;
        int textX = centerX - fontRenderer.getStringWidth(text) / 2;
        int textY = top + 10;
        fontRenderer.drawStringWithShadow(text, textX, textY, 0xFFFFFF);

        if (!loggedRender) {
            loggedRender = true;
            ATWRebrand.log("fallback ATW Client text rendered over " + screen.getClass().getName());
        }
    }

    private static boolean isLunarMenuScreen(GuiScreen screen) {
        String className = screen.getClass().getName();
        return className.startsWith("com.moonsworth.lunar")
                && (className.toLowerCase().contains("menu")
                || className.toLowerCase().contains("main")
                || className.toLowerCase().contains("home"));
    }
}

