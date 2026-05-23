package com.atw.optimalzone;

import com.atw.optimalzone.command.ToggleOptimalZoneCommand;
import com.atw.optimalzone.render.OptimalZoneRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;
import net.weavemc.loader.api.ModInitializer;
import net.weavemc.loader.api.command.CommandBus;
import net.weavemc.loader.api.event.EventBus;
import net.weavemc.loader.api.event.RenderLivingEvent;
import org.lwjgl.input.Mouse;

public class OptimalZoneMod implements ModInitializer {
    public static final String PREFIX = EnumChatFormatting.RED + "[OptimalZone] " + EnumChatFormatting.RESET;

    private final OptimalZoneRenderer renderer = new OptimalZoneRenderer(this);
    private boolean enabled = false;
    private long lastFightCheckMillis;

    @Override
    public void preInit() {
        CommandBus.register(new ToggleOptimalZoneCommand(this));
        EventBus.subscribe(RenderLivingEvent.Post.class, renderer::render);
        log("Loaded.");
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void toggle() {
        enabled = !enabled;
        sendChat(enabled ? EnumChatFormatting.GREEN + "Enabled." : EnumChatFormatting.RED + "Disabled.");
    }

    public boolean shouldRenderNow() {
        Minecraft mc = Minecraft.getMinecraft();
        if (!enabled || mc == null || mc.thePlayer == null || mc.theWorld == null || mc.gameSettings.hideGUI) {
            return false;
        }

        if (Mouse.isButtonDown(0)) {
            lastFightCheckMillis = System.currentTimeMillis();
            return true;
        }

        return System.currentTimeMillis() - lastFightCheckMillis <= 1000L;
    }

    public void sendChat(String message) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc != null && mc.thePlayer != null) {
            mc.thePlayer.addChatMessage(new ChatComponentText(PREFIX + message));
        }
    }

    public static double interpolate(double previous, double current, float partialTicks) {
        return previous + (current - previous) * partialTicks;
    }

    public static boolean isSelf(Entity entity) {
        Minecraft mc = Minecraft.getMinecraft();
        return mc != null && mc.thePlayer != null && entity == mc.thePlayer;
    }

    public static void log(String message) {
        System.out.println("[OptimalZone] " + message);
    }
}
