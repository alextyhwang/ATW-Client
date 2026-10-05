/*
 * Decompiled with CFR 0.152.
 *
 * Could not load the following classes:
 *  net.minecraft.client.Minecraft
 *  net.minecraft.util.EnumChatFormatting
 *  net.weavemc.api.event.SubscribeEvent
 *  net.weavemc.api.event.TickEvent
 */
package com.github.koxx12dev.listener;

import com.github.koxx12dev.util.ChatUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.util.EnumChatFormatting;
import net.weavemc.api.event.SubscribeEvent;
import net.weavemc.api.event.TickEvent;

public class UnintendedUsageWarningListener {
    private boolean hasSentWarningForSession = false;

    @SubscribeEvent
    public void sendWarning(TickEvent event) {
        if (this.hasSentWarningForSession || System.getProperty("os.name").toLowerCase().contains("windows") || Minecraft.getMinecraft().thePlayer == null) {
            return;
        }
        ChatUtil.addMessage(EnumChatFormatting.RED, "[RawInput] his mod is only intended for Windows. Please remove this mod from your mods folder and relaunch your game as soon as possible, as this mod is dormant when used outside of Windows. \u00c2\u00a7ePlease run \u00c2\u00a7l/modfolder\u00c2\u00a7r\u00c2\u00a7e to open it now.");
        ChatUtil.addMessage(EnumChatFormatting.RED, "[RawInput] You are currently on " + System.getProperty("os.name"));
        this.hasSentWarningForSession = true;
    }
}
