/*
 * Decompiled with CFR 0.152.
 *
 * Could not load the following classes:
 *  net.minecraft.client.Minecraft
 *  net.minecraft.util.ChatComponentText
 *  net.minecraft.util.EnumChatFormatting
 *  net.minecraft.util.IChatComponent
 */
package com.github.koxx12dev.util;

import net.minecraft.client.Minecraft;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;

public class ChatUtil {
    public static void addMessage(IChatComponent message) {
        Minecraft.getMinecraft().thePlayer.addChatMessage(message);
    }

    public static void addMessage(String message) {
        ChatUtil.addMessage((IChatComponent)new ChatComponentText(message));
    }

    public static void addMessage(EnumChatFormatting color, String message) {
        ChatUtil.addMessage(color + message);
    }
}
