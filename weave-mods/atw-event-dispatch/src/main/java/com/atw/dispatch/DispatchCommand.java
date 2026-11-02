// SPDX-License-Identifier: GPL-3.0-only
package com.atw.dispatch;

import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.util.BlockPos;
import net.minecraft.util.ChatComponentText;
import net.weavemc.api.command.Command;

/** Uses the existing CommandBus and LevelHead bridge; no event or packet subscription. */
public final class DispatchCommand extends Command {
    public DispatchCommand() { super("atwdispatch"); setExclusiveSuggestions(true); }
    @Override public void execute(String[] args) {
        String action = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "status";
        if (args.length > 2 || !java.util.Set.of("on", "off", "status", "reset").contains(action)) {
            message("/atwdispatch on|off|status|reset — resets OFF each startup; no performance claim"); return;
        }
        if (action.equals("on") || action.equals("off")) DispatchRuntime.request(action.equals("on"));
        if (action.equals("reset")) DispatchRuntime.reset();
        message(DispatchRuntime.status());
    }
    private static void message(String text) {
        System.out.println("[ATW dispatch] " + text);
        Minecraft mc = Minecraft.getMinecraft();
        mc.addScheduledTask(() -> { if (mc.thePlayer != null) mc.thePlayer.addChatMessage(new ChatComponentText(text)); });
    }
    @Override public String[] getSuggestions(String[] args, BlockPos target) {
        if (args.length != 1) return new String[0];
        return java.util.stream.Stream.of("on", "off", "status", "reset")
            .filter(s -> s.startsWith(args[0].toLowerCase(Locale.ROOT))).toArray(String[]::new);
    }
}
