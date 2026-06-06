package com.atw.optimalzone;

import com.atw.optimalzone.command.OverlayCommand;
import com.atw.optimalzone.render.InvisOverlayRenderer;
import com.atw.optimalzone.render.OccludedPlayerRenderer;
import com.atw.optimalzone.render.OptimalZoneRenderer;
import com.atw.optimalzone.render.PlayerMinimapRenderer;
import com.atw.optimalzone.render.ProjectileTrajectoryRenderer;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import net.minecraft.client.Minecraft;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.scoreboard.ScorePlayerTeam;
import net.minecraft.scoreboard.Team;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.MovingObjectPosition;
import net.weavemc.loader.api.ModInitializer;
import net.weavemc.loader.api.command.CommandBus;
import net.weavemc.loader.api.event.EventBus;
import net.weavemc.loader.api.event.RenderGameOverlayEvent;
import net.weavemc.loader.api.event.RenderLivingEvent;
import net.weavemc.loader.api.event.RenderWorldEvent;
import net.weavemc.loader.api.event.TickEvent;
import org.lwjgl.input.Mouse;

import java.util.Collection;
import java.util.UUID;

public class OptimalZoneMod implements ModInitializer {
    public static final String PREFIX = EnumChatFormatting.AQUA + "[ATW's Overlay] " + EnumChatFormatting.RESET;
    private static final long OPTIMAL_ZONE_HIT_WINDOW_MILLIS = 650L;
    private static final long DAMAGE_SOUND_COOLDOWN_MILLIS = 650L;

    private final OptimalZoneRenderer optimalZoneRenderer = new OptimalZoneRenderer(this);
    private final ProjectileTrajectoryRenderer trajectoryRenderer = new ProjectileTrajectoryRenderer(this);
    private final OccludedPlayerRenderer occludedPlayerRenderer = new OccludedPlayerRenderer(this);
    private final PlayerMinimapRenderer minimapRenderer = new PlayerMinimapRenderer(this);
    private final InvisOverlayRenderer invisOverlayRenderer = new InvisOverlayRenderer(this);
    private boolean enabled = true;
    private boolean optimalZoneEnabled = true;
    private boolean projectilesEnabled = true;
    private boolean chamsEnabled = true;
    private boolean minimapEnabled = true;
    private boolean invisOverlayEnabled = true;
    private boolean renderingOccludedPlayerOverlay;
    private long lastFightCheckMillis;
    private int recentOptimalZoneTargetId = -1;
    private long recentOptimalZoneAimMillis;
    private int lastDamageSoundTargetId = -1;
    private long lastDamageSoundMillis;

    @Override
    public void preInit() {
        CommandBus.register(new OverlayCommand(this, "atwoverlay", OverlayCommand.Action.STATUS));
        CommandBus.register(new OverlayCommand(this, "toggleoptimalzone", OverlayCommand.Action.TOGGLE_OPTIMAL_ZONE));
        CommandBus.register(new OverlayCommand(this, "togglechams", OverlayCommand.Action.TOGGLE_CHAMS));
        CommandBus.register(new OverlayCommand(this, "toggleminimap", OverlayCommand.Action.TOGGLE_MINIMAP));
        CommandBus.register(new OverlayCommand(this, "togglebigmap", OverlayCommand.Action.TOGGLE_BIG_MAP));
        CommandBus.register(new OverlayCommand(this, "toggleinvisoverlay", OverlayCommand.Action.TOGGLE_INVIS_OVERLAY));
        EventBus.subscribe(RenderLivingEvent.Pre.class, occludedPlayerRenderer::render);
        EventBus.subscribe(RenderLivingEvent.Post.class, optimalZoneRenderer::render);
        EventBus.subscribe(RenderWorldEvent.class, trajectoryRenderer::render);
        EventBus.subscribe(RenderWorldEvent.class, invisOverlayRenderer::render);
        EventBus.subscribe(RenderGameOverlayEvent.Post.class, minimapRenderer::render);
        EventBus.subscribe(TickEvent.Post.class, minimapRenderer::onTick);
        EventBus.subscribe(TickEvent.Post.class, invisOverlayRenderer::onTick);
        EventBus.subscribe(TickEvent.Post.class, this::onTick);
        log("Loaded.");
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void toggle() {
        enabled = !enabled;
        sendChat(EnumChatFormatting.YELLOW + "Overlay " + (enabled ? "enabled." : "disabled."));
    }

    public boolean isOptimalZoneEnabled() {
        return optimalZoneEnabled;
    }

    public boolean isProjectilesEnabled() {
        return projectilesEnabled;
    }

    public boolean isChamsEnabled() {
        return chamsEnabled;
    }

    public boolean isMinimapEnabled() {
        return minimapEnabled;
    }

    public boolean isInvisOverlayEnabled() {
        return invisOverlayEnabled;
    }

    public boolean shouldRenderProjectiles() {
        return enabled && projectilesEnabled;
    }

    public boolean shouldRenderChams() {
        Minecraft mc = Minecraft.getMinecraft();
        return enabled && chamsEnabled && mc != null && mc.thePlayer != null && mc.theWorld != null && !mc.gameSettings.hideGUI;
    }

    public boolean shouldRenderMinimap() {
        Minecraft mc = Minecraft.getMinecraft();
        return enabled && minimapEnabled && mc != null && mc.thePlayer != null && mc.theWorld != null && !mc.gameSettings.hideGUI;
    }

    public boolean shouldRenderInvisOverlay() {
        Minecraft mc = Minecraft.getMinecraft();
        return enabled && invisOverlayEnabled && mc != null && mc.thePlayer != null && mc.theWorld != null && !mc.gameSettings.hideGUI;
    }

    public boolean isRenderingOccludedPlayerOverlay() {
        return renderingOccludedPlayerOverlay;
    }

    public void setRenderingOccludedPlayerOverlay(boolean renderingOccludedPlayerOverlay) {
        this.renderingOccludedPlayerOverlay = renderingOccludedPlayerOverlay;
    }

    public void toggleOptimalZone() {
        optimalZoneEnabled = !optimalZoneEnabled;
        sendModuleState("Optimal Zone", optimalZoneEnabled);
    }

    public void toggleProjectiles() {
        projectilesEnabled = !projectilesEnabled;
        sendModuleState("Projectiles", projectilesEnabled);
    }

    public void toggleChams() {
        chamsEnabled = !chamsEnabled;
        sendModuleState("Chams", chamsEnabled);
    }

    public void toggleMinimap() {
        minimapEnabled = !minimapEnabled;
        sendModuleState("Minimap", minimapEnabled);
    }

    public void toggleBigMap() {
        minimapRenderer.toggleExpandedMap();
        sendChat(EnumChatFormatting.YELLOW + "Expanded map "
                + (minimapRenderer.isExpandedMapOpen() ? "opened." : "closed."));
    }

    public void toggleMinimapTerrain() {
        boolean enabled = minimapRenderer.toggleTerrain();
        sendChat(EnumChatFormatting.YELLOW + "Minimap terrain "
                + (enabled ? "enabled." : "disabled (markers only)."));
    }

    public void sendMinimapPerformance() {
        sendChat(EnumChatFormatting.YELLOW + minimapRenderer.performanceSummary());
    }

    public void toggleInvisOverlay() {
        invisOverlayEnabled = !invisOverlayEnabled;
        sendModuleState("Invis Overlay", invisOverlayEnabled);
    }

    public void sendStatus() {
        sendChat(EnumChatFormatting.YELLOW
                + "Overlay: " + state(enabled)
                + EnumChatFormatting.GRAY + " | "
                + EnumChatFormatting.YELLOW + "Optimal Zone: " + state(optimalZoneEnabled)
                + EnumChatFormatting.GRAY + " | "
                + EnumChatFormatting.YELLOW + "Projectiles: " + state(projectilesEnabled)
                + EnumChatFormatting.GRAY + " | "
                + EnumChatFormatting.YELLOW + "Chams: " + state(chamsEnabled)
                + EnumChatFormatting.GRAY + " | "
                + EnumChatFormatting.YELLOW + "Minimap: " + state(minimapEnabled)
                + EnumChatFormatting.GRAY + " | "
                + EnumChatFormatting.YELLOW + "Big Map: " + state(minimapRenderer.isExpandedMapOpen())
                + EnumChatFormatting.GRAY + " | "
                + EnumChatFormatting.YELLOW + "Terrain: " + state(minimapRenderer.isTerrainEnabled())
                + EnumChatFormatting.GRAY + " | "
                + EnumChatFormatting.YELLOW + "Invis Overlay: " + state(invisOverlayEnabled));
    }

    public void debugTargetEntity() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.thePlayer == null || mc.theWorld == null) {
            sendChat(EnumChatFormatting.YELLOW + "Debug target unavailable: world/player is not loaded.");
            return;
        }

        MovingObjectPosition hit = mc.objectMouseOver;
        if (hit == null || hit.entityHit == null) {
            sendChat(EnumChatFormatting.YELLOW + "No entity under crosshair. Aim at a player/NPC and run /atwoverlay debugtarget.");
            return;
        }

        Entity entity = hit.entityHit;
        String report = buildEntityDebugReport(mc, entity, hit);
        for (String line : report.split("\n")) {
            log(line);
        }

        sendChat(EnumChatFormatting.YELLOW + "Debugged target: "
                + EnumChatFormatting.WHITE + safe(entity.getName())
                + EnumChatFormatting.GRAY + " | "
                + EnumChatFormatting.YELLOW + "class: "
                + EnumChatFormatting.WHITE + entity.getClass().getName());
        sendChat(EnumChatFormatting.YELLOW + "id: "
                + EnumChatFormatting.WHITE + entity.getEntityId()
                + EnumChatFormatting.GRAY + " | "
                + EnumChatFormatting.YELLOW + "uuid: "
                + EnumChatFormatting.WHITE + safeUuid(entity.getUniqueID()));
        if (entity instanceof EntityPlayer) {
            EntityPlayer player = (EntityPlayer) entity;
            sendChat(EnumChatFormatting.YELLOW + "real player: "
                    + EnumChatFormatting.WHITE + OverlayPlayerClassifier.shouldTreatAsRealPlayer(player)
                    + EnumChatFormatting.GRAY + " | "
                    + EnumChatFormatting.YELLOW + "reason: "
                    + EnumChatFormatting.WHITE + OverlayPlayerClassifier.realPlayerReason(player));
        }
        sendChat(EnumChatFormatting.YELLOW + "Full entity dump written to latest.log with [ATW's Overlay] prefix.");
    }

    public boolean shouldRenderNow() {
        Minecraft mc = Minecraft.getMinecraft();
        if (!enabled || !optimalZoneEnabled || mc == null || mc.thePlayer == null || mc.theWorld == null || mc.gameSettings.hideGUI) {
            return false;
        }

        if (Mouse.isButtonDown(0)) {
            lastFightCheckMillis = System.currentTimeMillis();
            return true;
        }

        return System.currentTimeMillis() - lastFightCheckMillis <= 1000L;
    }

    public void recordOptimalZoneAim(EntityPlayer target) {
        if (target == null || OptimalZoneMod.isSelf(target)) {
            return;
        }

        recentOptimalZoneTargetId = target.getEntityId();
        recentOptimalZoneAimMillis = System.currentTimeMillis();
    }

    private void onTick(TickEvent.Post event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (!enabled || mc == null || mc.thePlayer == null || mc.theWorld == null) {
            return;
        }

        long now = System.currentTimeMillis();
        if (recentOptimalZoneTargetId < 0 || now - recentOptimalZoneAimMillis > OPTIMAL_ZONE_HIT_WINDOW_MILLIS) {
            return;
        }

        Entity entity = mc.theWorld.getEntityByID(recentOptimalZoneTargetId);
        if (!(entity instanceof EntityPlayer)) {
            return;
        }

        EntityPlayer target = (EntityPlayer) entity;
        if (target.hurtTime <= 0 || !target.isEntityAlive()) {
            return;
        }

        if (target.getEntityId() == lastDamageSoundTargetId && now - lastDamageSoundMillis < DAMAGE_SOUND_COOLDOWN_MILLIS) {
            return;
        }

        mc.thePlayer.playSound("game.player.hurt", 0.7F, 0.82F);
        lastDamageSoundTargetId = target.getEntityId();
        lastDamageSoundMillis = now;
    }

    public void sendChat(String message) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc != null && mc.thePlayer != null) {
            mc.thePlayer.addChatMessage(new ChatComponentText(PREFIX + message));
        }
    }

    private String buildEntityDebugReport(Minecraft mc, Entity entity, MovingObjectPosition hit) {
        StringBuilder report = new StringBuilder();
        report.append("=== Entity Debug Target Begin ===\n");
        report.append("hit.type=").append(hit.typeOfHit).append("\n");
        appendEntityBasics(report, mc, entity);

        if (entity instanceof EntityLivingBase) {
            appendLivingInfo(report, (EntityLivingBase) entity);
        }

        if (entity instanceof EntityPlayer) {
            appendPlayerInfo(report, mc, (EntityPlayer) entity);
        }

        appendNbtInfo(report, entity);
        report.append("=== Entity Debug Target End ===");
        return report.toString();
    }

    private void appendEntityBasics(StringBuilder report, Minecraft mc, Entity entity) {
        report.append("class=").append(entity.getClass().getName()).append("\n");
        report.append("simpleClass=").append(entity.getClass().getSimpleName()).append("\n");
        report.append("entityId=").append(entity.getEntityId()).append("\n");
        report.append("uuid=").append(safeUuid(entity.getUniqueID())).append("\n");
        report.append("name=").append(safe(entity.getName())).append("\n");
        report.append("displayName.formatted=").append(componentFormatted(entity.getDisplayName())).append("\n");
        report.append("displayName.unformatted=").append(componentUnformatted(entity.getDisplayName())).append("\n");
        report.append("customNameTag=").append(safe(entity.getCustomNameTag())).append("\n");
        report.append("hasCustomName=").append(entity.hasCustomName()).append("\n");
        report.append("isSelf=").append(isSelf(entity)).append("\n");
        report.append("isDead=").append(entity.isDead).append("\n");
        report.append("isInvisible=").append(entity.isInvisible()).append("\n");
        report.append("isInvisibleToLocalPlayer=").append(entity.isInvisibleToPlayer(mc.thePlayer)).append("\n");
        report.append("ticksExisted=").append(entity.ticksExisted).append("\n");
        report.append("pos=").append(format(entity.posX)).append(",").append(format(entity.posY)).append(",").append(format(entity.posZ)).append("\n");
        report.append("lastTickPos=").append(format(entity.lastTickPosX)).append(",").append(format(entity.lastTickPosY)).append(",").append(format(entity.lastTickPosZ)).append("\n");
        report.append("motion=").append(format(entity.motionX)).append(",").append(format(entity.motionY)).append(",").append(format(entity.motionZ)).append("\n");
        report.append("rotationYaw=").append(format(entity.rotationYaw)).append("\n");
        report.append("rotationPitch=").append(format(entity.rotationPitch)).append("\n");
        report.append("width=").append(format(entity.width)).append("\n");
        report.append("height=").append(format(entity.height)).append("\n");
        report.append("onGround=").append(entity.onGround).append("\n");
        report.append("dimension=").append(entity.dimension).append("\n");
        report.append("ridingEntity=").append(entity.ridingEntity == null ? "null" : entity.ridingEntity.getClass().getName() + "#" + entity.ridingEntity.getEntityId()).append("\n");
        report.append("riddenByEntity=").append(entity.riddenByEntity == null ? "null" : entity.riddenByEntity.getClass().getName() + "#" + entity.riddenByEntity.getEntityId()).append("\n");
    }

    private void appendLivingInfo(StringBuilder report, EntityLivingBase living) {
        report.append("living.isEntityAlive=").append(living.isEntityAlive()).append("\n");
        report.append("living.health=").append(format(living.getHealth())).append("\n");
        report.append("living.maxHealth=").append(format(living.getMaxHealth())).append("\n");
        report.append("living.hurtTime=").append(living.hurtTime).append("\n");
        report.append("living.maxHurtTime=").append(living.maxHurtTime).append("\n");
        report.append("living.deathTime=").append(living.deathTime).append("\n");
    }

    private void appendPlayerInfo(StringBuilder report, Minecraft mc, EntityPlayer player) {
        report.append("player.gameProfile.name=").append(safe(player.getGameProfile().getName())).append("\n");
        report.append("player.gameProfile.id=").append(safeUuid(player.getGameProfile().getId())).append("\n");
        appendGameProfileProperties(report, player.getGameProfile());
        appendTeamInfo(report, player);
        appendOverlayPlayerClassifierInfo(report, player);
        appendNetworkPlayerInfo(report, mc, player);
        report.append("player.sleeping=").append(player.isPlayerSleeping()).append("\n");
        report.append("player.capabilities.isCreativeMode=").append(player.capabilities.isCreativeMode).append("\n");
        report.append("player.capabilities.allowFlying=").append(player.capabilities.allowFlying).append("\n");
        report.append("player.capabilities.isFlying=").append(player.capabilities.isFlying).append("\n");
    }

    private void appendGameProfileProperties(StringBuilder report, GameProfile profile) {
        if (profile == null || profile.getProperties() == null || profile.getProperties().isEmpty()) {
            report.append("player.gameProfile.properties=<empty>\n");
            return;
        }

        report.append("player.gameProfile.properties.keys=").append(profile.getProperties().keySet()).append("\n");
        for (String key : profile.getProperties().keySet()) {
            Collection<Property> properties = profile.getProperties().get(key);
            report.append("player.gameProfile.property.").append(key).append(".count=").append(properties.size()).append("\n");
            int index = 0;
            for (Property property : properties) {
                report.append("player.gameProfile.property.").append(key).append("[").append(index).append("].valueLength=")
                        .append(property.getValue() == null ? -1 : property.getValue().length()).append("\n");
                report.append("player.gameProfile.property.").append(key).append("[").append(index).append("].hasSignature=")
                        .append(property.hasSignature()).append("\n");
                index++;
            }
        }
    }

    private void appendTeamInfo(StringBuilder report, EntityPlayer player) {
        Team team = player.getTeam();
        if (team == null) {
            report.append("team=null\n");
            return;
        }

        report.append("team.class=").append(team.getClass().getName()).append("\n");
        report.append("team.registeredName=").append(safe(team.getRegisteredName())).append("\n");
        report.append("team.formattedPlayerName=").append(safe(ScorePlayerTeam.formatPlayerName(team, player.getName()))).append("\n");
        if (team instanceof ScorePlayerTeam) {
            ScorePlayerTeam scoreTeam = (ScorePlayerTeam) team;
            report.append("team.prefix=").append(safe(scoreTeam.getColorPrefix())).append("\n");
            report.append("team.suffix=").append(safe(scoreTeam.getColorSuffix())).append("\n");
            report.append("team.chatFormat=").append(scoreTeam.getChatFormat()).append("\n");
            report.append("team.chatFormatColorIndex=").append(scoreTeam.getChatFormat() == null ? "null" : scoreTeam.getChatFormat().getColorIndex()).append("\n");
            report.append("team.members=").append(scoreTeam.getMembershipCollection()).append("\n");
        }
    }

    private void appendOverlayPlayerClassifierInfo(StringBuilder report, EntityPlayer player) {
        report.append("overlay.realPlayerHeuristic=").append(OverlayPlayerClassifier.shouldTreatAsRealPlayer(player)).append("\n");
        report.append("overlay.realPlayerReason=").append(OverlayPlayerClassifier.realPlayerReason(player)).append("\n");
    }

    private void appendNetworkPlayerInfo(StringBuilder report, Minecraft mc, EntityPlayer player) {
        NetHandlerPlayClient netHandler = mc.getNetHandler();
        if (netHandler == null) {
            report.append("networkPlayerInfo=nullNetHandler\n");
            return;
        }

        NetworkPlayerInfo infoByUuid = netHandler.getPlayerInfo(player.getUniqueID());
        NetworkPlayerInfo infoByName = netHandler.getPlayerInfo(player.getName());
        report.append("networkPlayerInfo.byUuid.present=").append(infoByUuid != null).append("\n");
        report.append("networkPlayerInfo.byName.present=").append(infoByName != null).append("\n");
        appendNetworkPlayerInfoDetails(report, "networkPlayerInfo.byUuid", infoByUuid);
        appendNetworkPlayerInfoDetails(report, "networkPlayerInfo.byName", infoByName);
    }

    private void appendNetworkPlayerInfoDetails(StringBuilder report, String prefix, NetworkPlayerInfo info) {
        if (info == null) {
            return;
        }

        report.append(prefix).append(".profile.name=").append(safe(info.getGameProfile().getName())).append("\n");
        report.append(prefix).append(".profile.id=").append(safeUuid(info.getGameProfile().getId())).append("\n");
        report.append(prefix).append(".displayName.formatted=").append(componentFormatted(info.getDisplayName())).append("\n");
        report.append(prefix).append(".displayName.unformatted=").append(componentUnformatted(info.getDisplayName())).append("\n");
        report.append(prefix).append(".gameType=").append(info.getGameType()).append("\n");
        report.append(prefix).append(".responseTime=").append(info.getResponseTime()).append("\n");
        report.append(prefix).append(".skin=").append(info.getLocationSkin()).append("\n");
    }

    private void appendNbtInfo(StringBuilder report, Entity entity) {
        NBTTagCompound optionalNbt = new NBTTagCompound();
        try {
            report.append("nbt.writeOptionalResult=").append(entity.writeToNBTOptional(optionalNbt)).append("\n");
            report.append("nbt.optional.keys=").append(optionalNbt.getKeySet()).append("\n");
            report.append("nbt.optional=").append(optionalNbt).append("\n");
        } catch (Throwable throwable) {
            report.append("nbt.optional.error=").append(throwable.getClass().getName()).append(": ").append(throwable.getMessage()).append("\n");
        }

        NBTTagCompound fullNbt = new NBTTagCompound();
        try {
            entity.writeToNBT(fullNbt);
            report.append("nbt.full.keys=").append(fullNbt.getKeySet()).append("\n");
            report.append("nbt.full=").append(fullNbt).append("\n");
        } catch (Throwable throwable) {
            report.append("nbt.full.error=").append(throwable.getClass().getName()).append(": ").append(throwable.getMessage()).append("\n");
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
        System.out.println("[ATW's Overlay] " + message);
    }

    private void sendModuleState(String name, boolean state) {
        sendChat(EnumChatFormatting.YELLOW + name + " " + (state ? "enabled." : "disabled."));
    }

    private String state(boolean value) {
        return value ? "on" : "off";
    }

    private String safe(String value) {
        return value == null ? "null" : value.replace('\n', ' ');
    }

    private String safeUuid(UUID value) {
        return value == null ? "null" : value.toString();
    }

    private String componentFormatted(IChatComponent component) {
        return component == null ? "null" : safe(component.getFormattedText());
    }

    private String componentUnformatted(IChatComponent component) {
        return component == null ? "null" : safe(component.getUnformattedText());
    }

    private String format(double value) {
        return String.format(java.util.Locale.ROOT, "%.3f", value);
    }
}
