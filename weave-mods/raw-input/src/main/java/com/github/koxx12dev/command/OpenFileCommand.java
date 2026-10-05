/*
 * Decompiled with CFR 0.152.
 *
 * Could not load the following classes:
 *  net.minecraft.util.EnumChatFormatting
 *  net.weavemc.api.command.Command
 */
package com.github.koxx12dev.command;

import com.github.koxx12dev.util.ChatUtil;
import com.github.koxx12dev.util.WeaveUtil;
import java.awt.Desktop;
import net.minecraft.util.EnumChatFormatting;
import net.weavemc.api.command.Command;

public class OpenFileCommand
extends Command {
    public OpenFileCommand() {
        super("modfolder", new String[0]);
    }

    public void execute(String[] args) {
        try {
            Desktop.getDesktop().open(WeaveUtil.getModFolder().toFile());
        }
        catch (Exception e) {
            ChatUtil.addMessage(EnumChatFormatting.RED, "[RawInput] You are currently on " + System.getProperty("os.name") + ", and RawInput had a problem trying to access your mods folder.");
        }
    }
}
