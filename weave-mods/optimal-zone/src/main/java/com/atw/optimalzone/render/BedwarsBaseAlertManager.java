package com.atw.optimalzone.render;

import com.atw.optimalzone.OptimalZoneMod;
import com.atw.optimalzone.OverlayPlayerClassifier;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;
import net.weavemc.api.event.ChatEvent;
import net.weavemc.api.event.TickEvent;
import net.weavemc.api.event.WorldEvent;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class BedwarsBaseAlertManager {
    private static final int CHECK_INTERVAL_TICKS = 5;
    private static final int CAPTURE_DELAY_TICKS = 8;
    private static final double INNER_ZONE_BLOCKS = 24.0D;
    private static final double OUTER_ZONE_BLOCKS = 55.0D;
    private static final double OUTER_ZONE_BLOCKS_SQUARED = OUTER_ZONE_BLOCKS * OUTER_ZONE_BLOCKS;
    private static final double LOOK_DOT_THRESHOLD = 0.86D;
    private static final long LOOK_CONFIRM_MILLIS = 1250L;
    private static final double APPROACH_CONFIRM_BLOCKS = 1.0D;
    private static final double APPROACH_NOISE_BLOCKS = 0.12D;
    private static final long ALERT_COOLDOWN_MILLIS = 30000L;
    private static final long STALE_TRACKING_MILLIS = 15000L;
    private static final long ALERT_HISTORY_RETENTION_MILLIS = ALERT_COOLDOWN_MILLIS * 2L;

    private final OptimalZoneMod mod;
    private final Map<UUID, ApproachState> approachStates = new HashMap<UUID, ApproachState>();
    private final Map<UUID, Long> lastAlertMillis = new HashMap<UUID, Long>();
    private Object world;
    private boolean gameActive;
    private boolean ownBedAlive;
    private boolean ownBedDestroyed;
    private boolean startSignalSeen;
    private boolean baseCaptured;
    private int captureCountdown;
    private int checkCountdown;
    private double baseX;
    private double baseY;
    private double baseZ;

    public BedwarsBaseAlertManager(OptimalZoneMod mod) {
        this.mod = mod;
    }

    public void onWorldLoad(WorldEvent.Load event) {
        reset();
    }

    public void onWorldUnload(WorldEvent.Unload event) {
        reset();
    }

    public void onChatReceived(ChatEvent.Received event) {
        if (event == null || event.getMessage() == null) {
            return;
        }

        String message = stripFormatting(event.getMessage());
        String normalized = message.trim().toUpperCase(Locale.ROOT).replaceAll("\\s+", " ");
        if (isBedwarsStartMessage(normalized)) {
            startSignalSeen = true;
            ownBedAlive = true;
            ownBedDestroyed = false;
            captureCountdown = CAPTURE_DELAY_TICKS;
            approachStates.clear();
            lastAlertMillis.clear();
        } else if (isOwnBedDestroyedMessage(normalized)) {
            disableIncomingAlerts();
        } else if (isBedwarsStopMessage(normalized)) {
            gameActive = false;
            resetBaseState();
        }
    }

    public void onTick(TickEvent.Post event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.thePlayer == null || mc.theWorld == null) {
            reset();
            return;
        }

        if (world != mc.theWorld) {
            reset();
            world = mc.theWorld;
        }

        boolean activeNow = BedwarsTeamResolver.isGameInProgress(mc);
        if (!activeNow) {
            if (gameActive) {
                resetBaseTracking();
            }
            gameActive = false;
            return;
        }

        if (!gameActive) {
            ownBedAlive = !ownBedDestroyed;
        }
        gameActive = true;

        int localTeam = BedwarsTeamResolver.teamKey(mc, mc.thePlayer);
        if (BedwarsTeamResolver.bedStatus(mc, localTeam) == BedwarsTeamResolver.BED_DESTROYED) {
            disableIncomingAlerts();
        }

        if (!ownBedAlive) {
            return;
        }

        if (!baseCaptured) {
            if (startSignalSeen) {
                if (captureCountdown > 0) {
                    captureCountdown--;
                    return;
                }
                captureBase(mc.thePlayer);
            } else if (mc.thePlayer.ticksExisted > 20) {
                captureBase(mc.thePlayer);
            }
        }

        if (!baseCaptured) {
            return;
        }

        if (checkCountdown > 0) {
            checkCountdown--;
            return;
        }
        checkCountdown = CHECK_INTERVAL_TICKS - 1;
        updateIncomingAlerts(mc, System.currentTimeMillis());
    }

    public boolean hasBase() {
        return baseCaptured && gameActive;
    }

    public BaseLocation baseLocation() {
        return hasBase() ? new BaseLocation(baseX, baseY, baseZ) : null;
    }

    public double innerZoneBlocks() {
        return INNER_ZONE_BLOCKS;
    }

    public double outerZoneBlocks() {
        return OUTER_ZONE_BLOCKS;
    }

    private void updateIncomingAlerts(Minecraft mc, long now) {
        int localTeam = BedwarsTeamResolver.teamKey(mc, mc.thePlayer);
        for (EntityPlayer player : mc.theWorld.playerEntities) {
            if (!shouldCheckPlayer(player)) {
                continue;
            }

            int playerTeam = BedwarsTeamResolver.teamKey(mc, player);
            if (localTeam != 0 && localTeam == playerTeam) {
                clearPlayer(player);
                continue;
            }

            double deltaBaseX = baseX - player.posX;
            double deltaBaseZ = baseZ - player.posZ;
            double distanceSquared = deltaBaseX * deltaBaseX + deltaBaseZ * deltaBaseZ;
            if (distanceSquared > OUTER_ZONE_BLOCKS_SQUARED || distanceSquared < 0.0001D) {
                clearPlayer(player);
                continue;
            }

            if (!isLookingTowardBase(player, deltaBaseX, deltaBaseZ, distanceSquared)) {
                clearPlayer(player);
                continue;
            }

            UUID uuid = player.getUniqueID();
            if (uuid == null) {
                continue;
            }

            ApproachState approachState = approachStates.get(uuid);
            double distance = Math.sqrt(distanceSquared);
            if (approachState == null) {
                approachStates.put(uuid, new ApproachState(now, distance));
                continue;
            }

            if (!approachState.update(now, distance)) {
                clearPlayer(player);
                approachStates.put(uuid, new ApproachState(now, distance));
                continue;
            }

            if (now - approachState.startedAtMillis < LOOK_CONFIRM_MILLIS
                    || approachState.approachBlocks < APPROACH_CONFIRM_BLOCKS) {
                continue;
            }

            Long lastAlert = lastAlertMillis.get(uuid);
            if (lastAlert != null && now - lastAlert < ALERT_COOLDOWN_MILLIS) {
                continue;
            }

            sendIncomingAlert(mc, player, playerTeam);
            lastAlertMillis.put(uuid, now);
        }

        pruneStale(now);
    }

    private boolean shouldCheckPlayer(EntityPlayer player) {
        return player != null
                && !OptimalZoneMod.isSelf(player)
                && !player.isDead
                && player.isEntityAlive()
                && OverlayPlayerClassifier.shouldTreatAsRealPlayer(player);
    }

    private boolean isLookingTowardBase(EntityPlayer player, double deltaBaseX, double deltaBaseZ, double distanceSquared) {
        double distance = Math.sqrt(distanceSquared);
        double toBaseX = deltaBaseX / distance;
        double toBaseZ = deltaBaseZ / distance;
        double yawRadians = Math.toRadians(player.rotationYaw);
        double lookX = -Math.sin(yawRadians);
        double lookZ = Math.cos(yawRadians);
        return lookX * toBaseX + lookZ * toBaseZ >= LOOK_DOT_THRESHOLD;
    }

    private void sendIncomingAlert(Minecraft mc, EntityPlayer player, int teamKey) {
        String team = BedwarsTeamResolver.teamName(teamKey).toUpperCase(Locale.ROOT);
        EnumChatFormatting teamColor = teamFormatting(teamKey);
        displayIncomingSubtitle(
                mc,
                teamColor + team + EnumChatFormatting.YELLOW + " TEAM APPROACHING"
        );
    }

    private void displayIncomingSubtitle(Minecraft mc, String subtitle) {
        if (mc == null || mc.ingameGUI == null) {
            return;
        }

        try {
            Method method = mc.ingameGUI.getClass().getMethod("displayTitle", String.class, String.class, int.class, int.class, int.class);
            method.invoke(mc.ingameGUI, new Object[] {"", null, 3, 30, 6});
            method.invoke(mc.ingameGUI, new Object[] {null, subtitle, -1, -1, -1});
        } catch (Throwable ignored) {
        }
    }

    private EnumChatFormatting teamFormatting(int teamKey) {
        switch (teamKey) {
            case 1:
                return EnumChatFormatting.RED;
            case 2:
                return EnumChatFormatting.BLUE;
            case 3:
                return EnumChatFormatting.GREEN;
            case 4:
                return EnumChatFormatting.YELLOW;
            case 5:
                return EnumChatFormatting.AQUA;
            case 6:
                return EnumChatFormatting.WHITE;
            case 7:
                return EnumChatFormatting.LIGHT_PURPLE;
            case 8:
                return EnumChatFormatting.GRAY;
            default:
                return EnumChatFormatting.RED;
        }
    }

    private void captureBase(EntityPlayer player) {
        baseX = player.posX;
        baseY = player.posY;
        baseZ = player.posZ;
        baseCaptured = true;
        startSignalSeen = false;
        captureCountdown = 0;
        approachStates.clear();
        lastAlertMillis.clear();
        OptimalZoneMod.log("Captured BedWars base at " + format(baseX) + ", " + format(baseY) + ", " + format(baseZ) + ".");
    }

    private void reset() {
        world = null;
        gameActive = false;
        resetBaseState();
    }

    private void resetBaseState() {
        ownBedAlive = false;
        ownBedDestroyed = false;
        resetBaseTracking();
    }

    private void resetBaseTracking() {
        startSignalSeen = false;
        baseCaptured = false;
        captureCountdown = 0;
        checkCountdown = 0;
        approachStates.clear();
        lastAlertMillis.clear();
    }

    private void disableIncomingAlerts() {
        if (!ownBedDestroyed) {
            OptimalZoneMod.log("Own BedWars bed destroyed; incoming alerts disabled.");
        }
        ownBedAlive = false;
        ownBedDestroyed = true;
        approachStates.clear();
        lastAlertMillis.clear();
    }

    private void clearPlayer(EntityPlayer player) {
        UUID uuid = player.getUniqueID();
        if (uuid != null) {
            approachStates.remove(uuid);
        }
    }

    private void pruneStale(long now) {
        Iterator<Map.Entry<UUID, ApproachState>> approachIterator = approachStates.entrySet().iterator();
        while (approachIterator.hasNext()) {
            Map.Entry<UUID, ApproachState> entry = approachIterator.next();
            if (now - entry.getValue().lastUpdatedMillis > STALE_TRACKING_MILLIS) {
                approachIterator.remove();
            }
        }

        Iterator<Map.Entry<UUID, Long>> alertIterator = lastAlertMillis.entrySet().iterator();
        while (alertIterator.hasNext()) {
            Map.Entry<UUID, Long> entry = alertIterator.next();
            if (now - entry.getValue() > ALERT_HISTORY_RETENTION_MILLIS) {
                alertIterator.remove();
            }
        }
    }

    private boolean isBedwarsStartMessage(String normalizedMessage) {
        return "BED WARS".equals(normalizedMessage)
                || normalizedMessage.contains("PROTECT YOUR BED")
                || normalizedMessage.contains("DESTROY THE ENEMY BEDS")
                || normalizedMessage.contains("CROSS-TEAMING IS NOT ALLOWED");
    }

    private boolean isBedwarsStopMessage(String normalizedMessage) {
        return normalizedMessage.contains("JOINED THE LOBBY")
                || normalizedMessage.contains("SENDING YOU TO")
                || normalizedMessage.contains("YOU ARE NOW CONNECTED TO")
                || normalizedMessage.contains("PLAY AGAIN")
                || normalizedMessage.contains("WINNER")
                || normalizedMessage.contains("VICTORY")
                || normalizedMessage.contains("GAME OVER")
                || normalizedMessage.contains("BED WARS LEVEL");
    }

    private boolean isOwnBedDestroyedMessage(String normalizedMessage) {
        return normalizedMessage.startsWith("BED DESTRUCTION > YOUR BED")
                || normalizedMessage.contains("YOUR BED WAS DESTROYED")
                || (normalizedMessage.contains("YOUR BED")
                && normalizedMessage.contains("YOU WILL NO LONGER RESPAWN"));
    }

    private String stripFormatting(IChatComponent component) {
        String formatted = component.getFormattedText();
        String stripped = EnumChatFormatting.getTextWithoutFormattingCodes(formatted);
        return stripped == null ? "" : stripped;
    }

    private String format(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }

    private static class ApproachState {
        private final long startedAtMillis;
        private long lastUpdatedMillis;
        private double lastDistance;
        private double approachBlocks;

        private ApproachState(long startedAtMillis, double distance) {
            this.startedAtMillis = startedAtMillis;
            this.lastUpdatedMillis = startedAtMillis;
            this.lastDistance = distance;
        }

        private boolean update(long now, double distance) {
            double delta = lastDistance - distance;
            lastUpdatedMillis = now;
            lastDistance = distance;
            if (delta > APPROACH_NOISE_BLOCKS) {
                approachBlocks += delta;
                return true;
            }
            return delta >= -APPROACH_NOISE_BLOCKS;
        }
    }

    public static class BaseLocation {
        public final double x;
        public final double y;
        public final double z;

        private BaseLocation(double x, double y, double z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }
}
