/*
 * Decompiled with CFR 0.152.
 *
 * Could not load the following classes:
 *  net.minecraft.util.EnumChatFormatting
 *  net.weavemc.api.command.Command
 */
package com.github.koxx12dev.command;

import com.github.koxx12dev.RawInput;
import com.github.koxx12dev.util.ChatUtil;
import net.minecraft.util.EnumChatFormatting;
import net.weavemc.api.command.Command;

public class RescanCommand
extends Command {
    public RescanCommand() {
        super("rescan", new String[0]);
    }

    public void execute(String[] args) {
        ChatUtil.addMessage(EnumChatFormatting.GOLD, "[RawInput] Rescanning input devices...");
        RawInput.mouse = null;
    }
}
