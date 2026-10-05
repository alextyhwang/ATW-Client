package com.atw.optimalzone.render;

import com.atw.optimalzone.OptimalZoneMod;
import com.atw.optimalzone.OverlayPlayerClassifier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.texture.ITextureObject;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.projectile.EntityFireball;
import net.minecraft.util.MathHelper;
import net.minecraft.util.ResourceLocation;
import net.weavemc.api.event.RenderGameOverlayEvent;
import net.weavemc.api.event.TickEvent;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.Arrays;
import java.util.Locale;
import java.util.UUID;

public class PlayerMinimapRenderer {
    private static final int MAP_SIZE = 128;
    private static final int SCREEN_MARGIN = 10;
    private static final int INNER_PADDING = 2;
    private static final int EXPANDED_MAP_MAX_SIZE = 252;
    private static final int EXPANDED_MAP_SCREEN_MARGIN = 24;
    private static final int EXPANDED_MAP_SEGMENTS = 128;
    private static final double MINIMAP_BLOCKS_PER_PIXEL = 1.5D;
    private static final double NEARBY_MINIMAP_BLOCKS_PER_PIXEL = 1.0D;
    private static final double CROWDED_MINIMAP_BLOCKS_PER_PIXEL = 0.75D;
    private static final double EXPANDED_MAP_BLOCKS_PER_PIXEL = 2.0D;
    private static final int CROWDED_PLAYER_THRESHOLD = 3;
    private static final double ADAPTIVE_ZOOM_ENTER_RADIUS = 28.0D;
    private static final double ADAPTIVE_ZOOM_EXIT_RADIUS = 36.0D;
    private static final int ADAPTIVE_ZOOM_CHECK_INTERVAL_TICKS = 5;
    private static final int ADAPTIVE_ZOOM_EXIT_DELAY_CHECKS = 8;
    private static final double ADAPTIVE_ZOOM_SMOOTHING_RESPONSE = 6.5D;
    private static final float PLAYER_HEAD_SIZE = 8.0F;
    private static final float PLAYER_HEAD_BORDER = 0.85F;
    private static final float PLAYER_HEAD_OUTLINE = 0.75F;
    private static final double PLAYER_HEIGHT_ARROW_THRESHOLD = 1.5D;
    private static final float PLAYER_HEIGHT_ARROW_WIDTH = 4.2F;
    private static final float PLAYER_HEIGHT_ARROW_HEIGHT = 3.4F;
    private static final float PLAYER_HEIGHT_ARROW_GAP = 1.6F;
    private static final float PLAYER_HEIGHT_ARROW_OUTLINE = 1.0F;
    private static final double VOIDING_PLAYER_Y_THRESHOLD = 0.0D;
    private static final float FIREBALL_MARKER_RADIUS = 4.2F;
    private static final float FIREBALL_CORE_RADIUS = 1.85F;
    private static final float FIREBALL_ARROW_LENGTH = 6.4F;
    private static final float FIREBALL_ARROW_HALF_WIDTH = 2.6F;
    private static final float FIREBALL_ARROW_BACKSET = 1.8F;
    private static final double FIREBALL_DIRECTION_EPSILON = 1.0E-5D;
    private static final int FIREBALL_OUTLINE_COLOR = 0xE0000000;
    private static final int FIREBALL_BODY_COLOR = 0xFFFF661A;
    private static final int FIREBALL_CORE_COLOR = 0xFFFFF06A;
    private static final float INVISIBLE_GLOW_OUTER = 3.1F;
    private static final float INVISIBLE_GLOW_INNER = 1.3F;
    private static final int INVISIBLE_GLOW_OUTER_COLOR = 0x9024EFFF;
    private static final int INVISIBLE_GLOW_INNER_COLOR = 0xD8FFFFFF;
    private static final int BASE_OUTER_ZONE_COLOR = 0xC8FFE066;
    private static final int BASE_INNER_ZONE_COLOR = 0xD8FF6060;
    private static final float BASE_ZONE_RING_THICKNESS = 1.35F;
    private static final double NANOS_PER_CLIENT_TICK = 50000000.0D;
    private static final float ROTATION_SMOOTHING_RESPONSE = 55.0F;
    private static final float ROTATION_SNAP_DEGREES = 35.0F;
    private static final int MARKER_OUTLINE_COLOR = 0xD0000000;
    private static final int PLAYER_FILTER_OK = 0;
    private static final int PLAYER_FILTER_NULL = 1;
    private static final int PLAYER_FILTER_SELF = 2;
    private static final int PLAYER_FILTER_DEAD = 3;
    private static final int PLAYER_FILTER_NOT_ALIVE = 4;
    private static final int PLAYER_FILTER_NPC = 5;
    private static final int HUD_STATE_MASK = GL11.GL_ENABLE_BIT
            | GL11.GL_COLOR_BUFFER_BIT
            | GL11.GL_DEPTH_BUFFER_BIT
            | GL11.GL_TEXTURE_BIT
            | GL11.GL_CURRENT_BIT;

    private final OptimalZoneMod mod;
    private final BedwarsBaseAlertManager bedwarsBaseAlerts;
    private final MinimapTerrainManager terrainManager = new MinimapTerrainManager();
    private final ProfileUuidSet previousLoadedPlayerIds = new ProfileUuidSet();
    private final ProfileUuidSet currentLoadedPlayerIds = new ProfileUuidSet();
    private final ProfileUuidSet previousMarkerPlayerIds = new ProfileUuidSet();
    private final ProfileUuidSet currentMarkerPlayerIds = new ProfileUuidSet();
    private MarkerSnapshot[] markerSnapshots = new MarkerSnapshot[16];
    private FireballSnapshot[] fireballSnapshots = new FireballSnapshot[8];
    private int markerSnapshotCount;
    private int fireballSnapshotCount;
    private Object tickWorld;
    private long lastClientTickNanos;
    private boolean hasYawSamples;
    private boolean hasSmoothedYaw;
    private float previousTickYaw;
    private float currentTickYaw;
    private float smoothedYaw;
    private long lastSmoothedYawNanos;
    private double smoothedMinimapBlocksPerPixel = MINIMAP_BLOCKS_PER_PIXEL;
    private long lastZoomSmoothingNanos;
    private int adaptiveZoomCheckCountdown;
    private int adaptiveZoomClearChecks;
    private int adaptiveZoomTier;
    private boolean expandedMapOpen;
    private boolean expandedMapKeyDown;
    private long lastProfileRenderStartNanos;
    private long profileFrameIntervalTotalNanos;
    private long profileFrameIntervalMaxNanos;
    private long profileRenderTotalNanos;
    private long profileRenderMaxNanos;
    private long profileTerrainDrawTotalNanos;
    private long profileTerrainDrawMaxNanos;
    private long profileMarkerTotalNanos;
    private long profileMarkerMaxNanos;
    private long profileTickTotalNanos;
    private long profileTickMaxNanos;
    private long profileAdaptiveZoomTotalNanos;
    private long profileAdaptiveZoomMaxNanos;
    private long profileTerrainTickCallTotalNanos;
    private long profileTerrainTickCallMaxNanos;
    private long profilePlayersScanned;
    private long profilePlayersEligible;
    private long profileMarkersDrawn;
    private long profileSkinHeads;
    private long profileFallbackHeads;
    private long profileLoadedPlayerSamples;
    private long profileLoadedPlayersTotal;
    private long profileLoadedPlayersMax;
    private long profileLoadedPlayerAppears;
    private long profileLoadedPlayerLeaves;
    private long profileLoadedPlayerNullUuids;
    private long profileMarkerRangeEntries;
    private long profileMarkerRangeExits;
    private long profilePlayersFilteredSelf;
    private long profilePlayersFilteredDead;
    private long profilePlayersFilteredNotAlive;
    private long profilePlayersFilteredNpc;
    private long profilePlayersOutOfRange;
    private long profileInvisibleMarkers;
    private long profileFireballEntitiesScanned;
    private long profileFireballsEligible;
    private long profileFireballsOutOfRange;
    private long profileFireballMarkersDrawn;
    private long profileGcCountBaseline;
    private long profileGcTimeBaseline;
    private long profileUsedMemoryBaseline;
    private long profileTotalMemoryBaseline;
    private int profileFrameIntervalCount;
    private int profileRenderFrameCount;
    private int profileTickCount;
    private long currentTerrainDrawNanos;
    private long currentMarkerNanos;
    private int currentPlayersScanned;
    private int currentPlayersEligible;
    private int currentMarkersDrawn;
    private int currentSkinHeads;
    private int currentFallbackHeads;
    private int currentPlayersFilteredSelf;
    private int currentPlayersFilteredDead;
    private int currentPlayersFilteredNotAlive;
    private int currentPlayersFilteredNpc;
    private int currentPlayersOutOfRange;
    private int currentInvisibleMarkers;
    private int currentFireballEntitiesScanned;
    private int currentFireballsEligible;
    private int currentFireballsOutOfRange;
    private int currentFireballMarkersDrawn;

    public PlayerMinimapRenderer(OptimalZoneMod mod, BedwarsBaseAlertManager bedwarsBaseAlerts) {
        this.mod = mod;
        this.bedwarsBaseAlerts = bedwarsBaseAlerts;
    }

    public boolean isExpandedMapOpen() {
        return expandedMapOpen;
    }

    public void toggleExpandedMap() {
        expandedMapOpen = !expandedMapOpen;
    }

    public boolean toggleTerrain() {
        return terrainManager.toggleTerrain();
    }

    public boolean isTerrainEnabled() {
        return terrainManager.isTerrainEnabled();
    }

    public String performanceSummary() {
        return terrainManager.performanceSummary();
    }

    public String[] performanceSummaryLines() {
        return new String[]{renderPerformanceSummary(), playerPerformanceSummary(), projectilePerformanceSummary(), jvmPerformanceSummary(), terrainManager.performanceSummary()};
    }

    public void resetPerformance() {
        lastProfileRenderStartNanos = 0L;
        profileFrameIntervalTotalNanos = 0L;
        profileFrameIntervalMaxNanos = 0L;
        profileRenderTotalNanos = 0L;
        profileRenderMaxNanos = 0L;
        profileTerrainDrawTotalNanos = 0L;
        profileTerrainDrawMaxNanos = 0L;
        profileMarkerTotalNanos = 0L;
        profileMarkerMaxNanos = 0L;
        profileTickTotalNanos = 0L;
        profileTickMaxNanos = 0L;
        profileAdaptiveZoomTotalNanos = 0L;
        profileAdaptiveZoomMaxNanos = 0L;
        profileTerrainTickCallTotalNanos = 0L;
        profileTerrainTickCallMaxNanos = 0L;
        profilePlayersScanned = 0L;
        profilePlayersEligible = 0L;
        profileMarkersDrawn = 0L;
        profileSkinHeads = 0L;
        profileFallbackHeads = 0L;
        profileLoadedPlayerSamples = 0L;
        profileLoadedPlayersTotal = 0L;
        profileLoadedPlayersMax = 0L;
        profileLoadedPlayerAppears = 0L;
        profileLoadedPlayerLeaves = 0L;
        profileLoadedPlayerNullUuids = 0L;
        profileMarkerRangeEntries = 0L;
        profileMarkerRangeExits = 0L;
        profilePlayersFilteredSelf = 0L;
        profilePlayersFilteredDead = 0L;
        profilePlayersFilteredNotAlive = 0L;
        profilePlayersFilteredNpc = 0L;
        profilePlayersOutOfRange = 0L;
        profileInvisibleMarkers = 0L;
        profileFireballEntitiesScanned = 0L;
        profileFireballsEligible = 0L;
        profileFireballsOutOfRange = 0L;
        profileFireballMarkersDrawn = 0L;
        profileFrameIntervalCount = 0;
        profileRenderFrameCount = 0;
        profileTickCount = 0;
        profileGcCountBaseline = totalGarbageCollectionCount();
        profileGcTimeBaseline = totalGarbageCollectionMillis();
        profileUsedMemoryBaseline = usedMemoryBytes();
        profileTotalMemoryBaseline = Runtime.getRuntime().totalMemory();
        resetCurrentRenderProfile();
        seedPlayerTracking();
        terrainManager.resetPerformance();
    }

    public void onTick(TickEvent.Post event) {
        long tickStartNanos = System.nanoTime();
        Minecraft mc = Minecraft.getMinecraft();
        boolean keyDown = Keyboard.isKeyDown(Keyboard.KEY_M);
        if (mc != null
                && mc.currentScreen == null
                && mc.thePlayer != null
                && mc.theWorld != null
                && keyDown
                && !expandedMapKeyDown) {
            mod.toggleBigMap();
        }
        expandedMapKeyDown = keyDown;

        if (mc == null || mc.thePlayer == null || mc.theWorld == null) {
            if (tickWorld != null) {
                terrainManager.reset();
            }
            tickWorld = null;
            lastClientTickNanos = 0L;
            hasYawSamples = false;
            hasSmoothedYaw = false;
            resetAdaptiveZoom();
            clearPlayerTracking();
            return;
        }

        Entity camera = cameraEntity(mc);
        if (tickWorld != mc.theWorld) {
            tickWorld = mc.theWorld;
            terrainManager.reset();
            previousTickYaw = camera.rotationYaw;
            currentTickYaw = camera.rotationYaw;
            hasYawSamples = true;
            hasSmoothedYaw = false;
            resetAdaptiveZoom();
            seedPlayerTracking(mc);
        } else if (!hasYawSamples) {
            previousTickYaw = camera.rotationYaw;
            currentTickYaw = camera.rotationYaw;
            hasYawSamples = true;
            hasSmoothedYaw = false;
        } else {
            previousTickYaw = currentTickYaw;
            currentTickYaw = camera.rotationYaw;
        }

        lastClientTickNanos = System.nanoTime();
        recordLoadedPlayerSnapshot(mc);
        if (mod.shouldRenderMinimap()) {
            long adaptiveStartNanos = System.nanoTime();
            updateAdaptiveZoom(mc);
            long adaptiveEndNanos = System.nanoTime();
            updateMarkerSnapshots(mc);
            boolean prewarmExpandedMap = !expandedMapOpen && bedwarsBaseAlerts.hasBase();
            long terrainTickStartNanos = System.nanoTime();
            terrainManager.tick(
                    mc.theWorld,
                    mc.thePlayer.posX,
                    mc.thePlayer.posY,
                    mc.thePlayer.posZ,
                    expandedMapOpen,
                    prewarmExpandedMap
            );
            long tickEndNanos = System.nanoTime();
            recordTickProfile(
                    tickEndNanos - tickStartNanos,
                    adaptiveEndNanos - adaptiveStartNanos,
                    tickEndNanos - terrainTickStartNanos
            );
        } else {
            markerSnapshotCount = 0;
            fireballSnapshotCount = 0;
            previousMarkerPlayerIds.clear();
            currentMarkerPlayerIds.clear();
        }
    }

    public void render(RenderGameOverlayEvent.Post event) {
        long renderStartNanos = System.nanoTime();
        recordFrameInterval(renderStartNanos);
        if (!mod.shouldRenderMinimap()) {
            return;
        }

        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.thePlayer == null || mc.theWorld == null) {
            return;
        }

        long nowNanos = renderStartNanos;
        float partialTicks = framePartialTicks(mc, event.getPartialTicks(), nowNanos);
        resetCurrentRenderProfile();

        GL11.glPushAttrib(HUD_STATE_MASK);
        GL11.glPushMatrix();
        try {
            setupHudState();
            drawMinimap(
                    mc,
                    partialTicks,
                    smoothRenderYaw(renderYaw(mc, partialTicks), nowNanos),
                    smoothMinimapBlocksPerPixel(nowNanos)
            );
        } finally {
            GL11.glPopMatrix();
            GL11.glPopAttrib();
        }
        long renderNanos = System.nanoTime() - renderStartNanos;
        terrainManager.recordHudRender(renderNanos);
        recordRenderProfile(renderNanos);
    }

    private void drawMinimap(Minecraft mc, float partialTicks, float yaw, double normalBlocksPerPixel) {
        ScaledResolution resolution = new ScaledResolution(mc);
        boolean expanded = expandedMapOpen;
        float mapSize = expanded ? expandedMapSize(resolution) : MAP_SIZE;
        float left = expanded
                ? (resolution.getScaledWidth() - mapSize) * 0.5F
                : SCREEN_MARGIN;
        float top = expanded
                ? (resolution.getScaledHeight() - mapSize) * 0.5F
                : SCREEN_MARGIN;
        float right = left + mapSize;
        float bottom = top + mapSize;
        float centerX = left + mapSize * 0.5F;
        float centerY = top + mapSize * 0.5F;
        float mapRadius = mapSize * 0.5F - INNER_PADDING;
        double blocksPerPixel = expanded ? EXPANDED_MAP_BLOCKS_PER_PIXEL : normalBlocksPerPixel;
        float pixelsPerBlock = (float) (1.0D / blocksPerPixel);

        EntityPlayerSP localPlayer = mc.thePlayer;
        double localX = OptimalZoneMod.interpolate(localPlayer.lastTickPosX, localPlayer.posX, partialTicks);
        double localY = OptimalZoneMod.interpolate(localPlayer.lastTickPosY, localPlayer.posY, partialTicks);
        double localZ = OptimalZoneMod.interpolate(localPlayer.lastTickPosZ, localPlayer.posZ, partialTicks);
        double yawRadians = Math.toRadians(yaw);
        double yawSin = Math.sin(yawRadians);
        double yawCos = Math.cos(yawRadians);

        if (expanded) {
            drawCircle(centerX, centerY, mapRadius + INNER_PADDING, 0xB0000000);
        } else {
            drawRect(left, top, right, bottom, 0x88000000);
        }
        long terrainDrawStartNanos = System.nanoTime();
        terrainManager.draw(
                expanded,
                centerX,
                centerY,
                mapRadius,
                localX,
                localZ,
                yawSin,
                yawCos,
                blocksPerPixel
        );
        currentTerrainDrawNanos += System.nanoTime() - terrainDrawStartNanos;
        if (expanded) {
            drawBaseZones(centerX, centerY, mapRadius, localX, localZ, yawSin, yawCos, pixelsPerBlock);
        }
        if (expanded) {
            drawCircleRing(centerX, centerY, mapRadius + 1.5F, mapRadius, 0xE8FFFFFF);
            drawCircleRing(centerX, centerY, mapRadius + 2.5F, mapRadius + 1.5F, 0xD0000000);
        } else {
            drawBorder(left, top, right, bottom);
        }

        long markerStartNanos = System.nanoTime();
        for (int index = 0; index < markerSnapshotCount; index++) {
            MarkerSnapshot marker = markerSnapshots[index];
            double playerX = OptimalZoneMod.interpolate(marker.lastX, marker.x, partialTicks);
            double playerY = OptimalZoneMod.interpolate(marker.lastY, marker.y, partialTicks);
            double playerZ = OptimalZoneMod.interpolate(marker.lastZ, marker.z, partialTicks);
            double deltaX = playerX - localX;
            double deltaZ = playerZ - localZ;
            float mapX = (float) ((-deltaX * yawCos - deltaZ * yawSin) * pixelsPerBlock);
            float mapY = (float) ((deltaX * yawSin - deltaZ * yawCos) * pixelsPerBlock);
            currentMarkersDrawn++;
            drawPlayerHead(marker, centerX + mapX, centerY + mapY, playerY - localY, playerY < VOIDING_PLAYER_Y_THRESHOLD);
        }
        for (int index = 0; index < fireballSnapshotCount; index++) {
            FireballSnapshot fireball = fireballSnapshots[index];
            double fireballX = OptimalZoneMod.interpolate(fireball.lastX, fireball.x, partialTicks);
            double fireballZ = OptimalZoneMod.interpolate(fireball.lastZ, fireball.z, partialTicks);
            double deltaX = fireballX - localX;
            double deltaZ = fireballZ - localZ;
            float mapX = (float) ((-deltaX * yawCos - deltaZ * yawSin) * pixelsPerBlock);
            float mapY = (float) ((deltaX * yawSin - deltaZ * yawCos) * pixelsPerBlock);
            currentFireballMarkersDrawn++;
            drawFireballMarker(fireball, centerX + mapX, centerY + mapY, yawSin, yawCos, pixelsPerBlock);
        }
        currentMarkerNanos += System.nanoTime() - markerStartNanos;

        drawCenterArrow(centerX, centerY);
    }

    private float expandedMapSize(ScaledResolution resolution) {
        int availableWidth = Math.max(MAP_SIZE, resolution.getScaledWidth() - EXPANDED_MAP_SCREEN_MARGIN * 2);
        int availableHeight = Math.max(MAP_SIZE, resolution.getScaledHeight() - EXPANDED_MAP_SCREEN_MARGIN * 2);
        return Math.min(EXPANDED_MAP_MAX_SIZE, Math.min(availableWidth, availableHeight));
    }

    private Entity cameraEntity(Minecraft mc) {
        Entity camera = mc.getRenderViewEntity();
        return camera == null ? mc.thePlayer : camera;
    }

    private float framePartialTicks(Minecraft mc, float eventPartialTicks, long now) {
        float timerPartialTicks = eventPartialTicks;
        if (mc.timer != null) {
            timerPartialTicks = mc.timer.renderPartialTicks;
        }

        float frameClockPartialTicks = -1.0F;
        if (lastClientTickNanos > 0L && tickWorld == mc.theWorld) {
            frameClockPartialTicks = (float) ((now - lastClientTickNanos) / NANOS_PER_CLIENT_TICK);
        }

        float partialTicks = frameClockPartialTicks >= 0.0F ? frameClockPartialTicks : timerPartialTicks;

        if (partialTicks < 0.0F) {
            return 0.0F;
        }

        if (partialTicks > 1.0F) {
            return 1.0F;
        }

        return partialTicks;
    }

    private float renderYaw(Minecraft mc, float partialTicks) {
        Entity camera = cameraEntity(mc);
        float cameraYaw = camera.rotationYaw;
        if (!hasYawSamples || tickWorld != mc.theWorld) {
            return cameraYaw;
        }

        float cameraDeltaFromTick = MathHelper.wrapAngleTo180_float(cameraYaw - currentTickYaw);
        if (Math.abs(cameraDeltaFromTick) > 0.001F) {
            return cameraYaw;
        }

        float yawDeltaPerTick = MathHelper.wrapAngleTo180_float(currentTickYaw - previousTickYaw);
        return currentTickYaw + yawDeltaPerTick * partialTicks;
    }

    private float smoothRenderYaw(float rawYaw, long nowNanos) {
        if (!hasSmoothedYaw || lastSmoothedYawNanos <= 0L) {
            smoothedYaw = rawYaw;
            lastSmoothedYawNanos = nowNanos;
            hasSmoothedYaw = true;
            return rawYaw;
        }

        float yawDelta = MathHelper.wrapAngleTo180_float(rawYaw - smoothedYaw);
        if (Math.abs(yawDelta) > ROTATION_SNAP_DEGREES) {
            smoothedYaw = rawYaw;
            lastSmoothedYawNanos = nowNanos;
            return rawYaw;
        }

        float elapsedSeconds = (float) ((nowNanos - lastSmoothedYawNanos) / 1000000000.0D);
        lastSmoothedYawNanos = nowNanos;
        if (elapsedSeconds <= 0.0F) {
            return smoothedYaw;
        }

        float alpha = 1.0F - (float) Math.exp(-ROTATION_SMOOTHING_RESPONSE * elapsedSeconds);
        alpha = MathHelper.clamp_float(alpha, 0.0F, 1.0F);
        smoothedYaw += yawDelta * alpha;
        return smoothedYaw;
    }

    private void updateAdaptiveZoom(Minecraft mc) {
        if (adaptiveZoomCheckCountdown > 0) {
            adaptiveZoomCheckCountdown--;
            return;
        }
        adaptiveZoomCheckCountdown = ADAPTIVE_ZOOM_CHECK_INTERVAL_TICKS - 1;

        boolean bedwarsGame = BedwarsTeamResolver.isGameInProgress(mc);
        NearbyPlayerCounts counts = nearbyPlayerCounts(
                mc,
                ADAPTIVE_ZOOM_ENTER_RADIUS * ADAPTIVE_ZOOM_ENTER_RADIUS,
                ADAPTIVE_ZOOM_EXIT_RADIUS * ADAPTIVE_ZOOM_EXIT_RADIUS,
                bedwarsGame
        );
        int enterTier = zoomTier(counts.enterRadius);
        if (enterTier > adaptiveZoomTier) {
            adaptiveZoomTier = enterTier;
            adaptiveZoomClearChecks = 0;
            return;
        }

        int retainedTier = zoomTier(counts.exitRadius);
        if (retainedTier >= adaptiveZoomTier) {
            adaptiveZoomClearChecks = 0;
            return;
        }

        if (adaptiveZoomTier > 0 && ++adaptiveZoomClearChecks >= ADAPTIVE_ZOOM_EXIT_DELAY_CHECKS) {
            adaptiveZoomTier = retainedTier;
            adaptiveZoomClearChecks = 0;
        }
    }

    private NearbyPlayerCounts nearbyPlayerCounts(
            Minecraft mc,
            double enterRadiusSquared,
            double exitRadiusSquared,
            boolean bedwarsGame
    ) {
        double localX = mc.thePlayer.posX;
        double localZ = mc.thePlayer.posZ;
        int localBedwarsTeam = bedwarsGame ? BedwarsTeamResolver.teamKey(mc, mc.thePlayer) : 0;
        int enterCount = 0;
        int exitCount = 0;
        for (EntityPlayer player : mc.theWorld.playerEntities) {
            if (!shouldRenderPlayer(player)) {
                continue;
            }
            if (localBedwarsTeam != 0 && localBedwarsTeam == BedwarsTeamResolver.teamKey(mc, player)) {
                continue;
            }

            double deltaX = player.posX - localX;
            double deltaZ = player.posZ - localZ;
            double distanceSquared = deltaX * deltaX + deltaZ * deltaZ;
            if (distanceSquared <= exitRadiusSquared) {
                exitCount++;
                if (distanceSquared <= enterRadiusSquared) {
                    enterCount++;
                }
                if (enterCount >= CROWDED_PLAYER_THRESHOLD) {
                    break;
                }
            }
        }
        return new NearbyPlayerCounts(enterCount, exitCount);
    }

    private int zoomTier(int nearbyPlayers) {
        if (nearbyPlayers >= CROWDED_PLAYER_THRESHOLD) {
            return 2;
        }
        return nearbyPlayers > 0 ? 1 : 0;
    }

    private double smoothMinimapBlocksPerPixel(long nowNanos) {
        if (lastZoomSmoothingNanos <= 0L) {
            lastZoomSmoothingNanos = nowNanos;
            return smoothedMinimapBlocksPerPixel;
        }

        double elapsedSeconds = (nowNanos - lastZoomSmoothingNanos) / 1000000000.0D;
        lastZoomSmoothingNanos = nowNanos;
        if (elapsedSeconds <= 0.0D) {
            return smoothedMinimapBlocksPerPixel;
        }

        double target = adaptiveZoomTier >= 2
                ? CROWDED_MINIMAP_BLOCKS_PER_PIXEL
                : adaptiveZoomTier == 1 ? NEARBY_MINIMAP_BLOCKS_PER_PIXEL : MINIMAP_BLOCKS_PER_PIXEL;
        double alpha = 1.0D - Math.exp(-ADAPTIVE_ZOOM_SMOOTHING_RESPONSE * elapsedSeconds);
        smoothedMinimapBlocksPerPixel += (target - smoothedMinimapBlocksPerPixel) * Math.min(1.0D, alpha);
        if (Math.abs(target - smoothedMinimapBlocksPerPixel) < 0.0001D) {
            smoothedMinimapBlocksPerPixel = target;
        }
        return smoothedMinimapBlocksPerPixel;
    }

    private void resetAdaptiveZoom() {
        adaptiveZoomTier = 0;
        adaptiveZoomCheckCountdown = 0;
        adaptiveZoomClearChecks = 0;
        smoothedMinimapBlocksPerPixel = MINIMAP_BLOCKS_PER_PIXEL;
        lastZoomSmoothingNanos = 0L;
    }

    private static final class NearbyPlayerCounts {
        private final int enterRadius;
        private final int exitRadius;

        private NearbyPlayerCounts(int enterRadius, int exitRadius) {
            this.enterRadius = enterRadius;
            this.exitRadius = exitRadius;
        }
    }

    private boolean shouldRenderPlayer(EntityPlayer player) {
        return playerRenderFilter(player) == PLAYER_FILTER_OK;
    }

    private void updateMarkerSnapshots(Minecraft mc) {
        resetCurrentMarkerSnapshotProfile();
        markerSnapshotCount = 0;
        fireballSnapshotCount = 0;

        EntityPlayerSP localPlayer = mc.thePlayer;
        double localX = localPlayer.posX;
        double localZ = localPlayer.posZ;
        double playerRadiusSquared = currentMarkerRadiusSquared(mc);
        for (EntityPlayer player : mc.theWorld.playerEntities) {
            currentPlayersScanned++;
            int filter = playerRenderFilter(player);
            if (filter != PLAYER_FILTER_OK) {
                recordCurrentPlayerFilter(filter);
                continue;
            }
            currentPlayersEligible++;

            double deltaX = player.posX - localX;
            double deltaZ = player.posZ - localZ;
            if (deltaX * deltaX + deltaZ * deltaZ > playerRadiusSquared) {
                currentPlayersOutOfRange++;
                continue;
            }

            UUID playerId = player.getUniqueID();
            if (playerId != null) {
                currentMarkerPlayerIds.add(playerId);
            }

            boolean invisible = isInvisibleToLocalPlayer(localPlayer, player);
            if (invisible) {
                currentInvisibleMarkers++;
            }

            MarkerSnapshot marker = markerSnapshot(markerSnapshotCount++);
            marker.update(
                    player,
                    OverlayColorResolver.colorFor(player),
                    cachedSkinTextureId(mc, player),
                    invisible
            );
        }
        updateFireballSnapshots(mc, localX, localZ, playerRadiusSquared);
        recordMarkerRangeChurn();
        recordMarkerSnapshotProfile();
    }

    private void updateFireballSnapshots(Minecraft mc, double localX, double localZ, double markerRadiusSquared) {
        for (Object object : mc.theWorld.loadedEntityList) {
            currentFireballEntitiesScanned++;
            if (!(object instanceof EntityFireball)) {
                continue;
            }

            EntityFireball fireball = (EntityFireball) object;
            if (fireball.isDead || !fireball.isEntityAlive()) {
                continue;
            }
            currentFireballsEligible++;

            double deltaX = fireball.posX - localX;
            double deltaZ = fireball.posZ - localZ;
            if (deltaX * deltaX + deltaZ * deltaZ > markerRadiusSquared) {
                currentFireballsOutOfRange++;
                continue;
            }

            FireballSnapshot snapshot = fireballSnapshot(fireballSnapshotCount++);
            snapshot.update(fireball);
        }
    }

    private int playerRenderFilter(EntityPlayer player) {
        if (player == null) {
            return PLAYER_FILTER_NULL;
        }
        if (OptimalZoneMod.isSelf(player)) {
            return PLAYER_FILTER_SELF;
        }
        if (player.isDead) {
            return PLAYER_FILTER_DEAD;
        }
        if (!player.isEntityAlive()) {
            return PLAYER_FILTER_NOT_ALIVE;
        }
        if (!OverlayPlayerClassifier.shouldTreatAsRealPlayer(player)) {
            return PLAYER_FILTER_NPC;
        }
        return PLAYER_FILTER_OK;
    }

    private void recordCurrentPlayerFilter(int filter) {
        if (filter == PLAYER_FILTER_SELF) {
            currentPlayersFilteredSelf++;
        } else if (filter == PLAYER_FILTER_DEAD) {
            currentPlayersFilteredDead++;
        } else if (filter == PLAYER_FILTER_NOT_ALIVE) {
            currentPlayersFilteredNotAlive++;
        } else if (filter == PLAYER_FILTER_NPC) {
            currentPlayersFilteredNpc++;
        }
    }

    private void setupHudState() {
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDepthMask(false);
        GL11.glEnable(GL11.GL_ALPHA_TEST);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glDisable(GL11.GL_CULL_FACE);
        OpenGlHelper.glBlendFunc(770, 771, 1, 0);
    }

    private void drawBorder(float left, float top, float right, float bottom) {
        drawRect(left, top, right, top + 1.0F, 0xAAFFFFFF);
        drawRect(left, bottom - 1.0F, right, bottom, 0xAAFFFFFF);
        drawRect(left, top, left + 1.0F, bottom, 0xAAFFFFFF);
        drawRect(right - 1.0F, top, right, bottom, 0xAAFFFFFF);
    }

    private void drawBaseZones(float centerX, float centerY, float mapRadius, double localX, double localZ, double yawSin, double yawCos, float pixelsPerBlock) {
        BedwarsBaseAlertManager.BaseLocation base = bedwarsBaseAlerts.baseLocation();
        if (base == null) {
            return;
        }

        double deltaX = base.x - localX;
        double deltaZ = base.z - localZ;
        float baseMapX = centerX + (float) ((-deltaX * yawCos - deltaZ * yawSin) * pixelsPerBlock);
        float baseMapY = centerY + (float) ((deltaX * yawSin - deltaZ * yawCos) * pixelsPerBlock);
        drawClippedWorldRing(centerX, centerY, mapRadius, baseMapX, baseMapY, (float) (bedwarsBaseAlerts.outerZoneBlocks() * pixelsPerBlock), BASE_ZONE_RING_THICKNESS, BASE_OUTER_ZONE_COLOR);
        drawClippedWorldRing(centerX, centerY, mapRadius, baseMapX, baseMapY, (float) (bedwarsBaseAlerts.innerZoneBlocks() * pixelsPerBlock), BASE_ZONE_RING_THICKNESS, BASE_INNER_ZONE_COLOR);
    }

    private void drawPlayerHead(MarkerSnapshot marker, float centerX, float centerY, double heightDelta, boolean voiding) {
        OverlayColorResolver.Color markerColor = marker.color;
        OverlayColorResolver.Color borderColor = voiding ? OverlayColorResolver.voidingPlayer() : markerColor;
        float halfHead = PLAYER_HEAD_SIZE * 0.5F;
        float borderLeft = centerX - halfHead - PLAYER_HEAD_BORDER;
        float borderTop = centerY - halfHead - PLAYER_HEAD_BORDER;
        float borderRight = centerX + halfHead + PLAYER_HEAD_BORDER;
        float borderBottom = centerY + halfHead + PLAYER_HEAD_BORDER;

        if (marker.invisible) {
            drawRect(borderLeft - INVISIBLE_GLOW_OUTER, borderTop - INVISIBLE_GLOW_OUTER, borderRight + INVISIBLE_GLOW_OUTER, borderBottom + INVISIBLE_GLOW_OUTER, INVISIBLE_GLOW_OUTER_COLOR);
            drawRect(borderLeft - INVISIBLE_GLOW_INNER, borderTop - INVISIBLE_GLOW_INNER, borderRight + INVISIBLE_GLOW_INNER, borderBottom + INVISIBLE_GLOW_INNER, INVISIBLE_GLOW_INNER_COLOR);
        }

        drawRect(borderLeft - PLAYER_HEAD_OUTLINE, borderTop - PLAYER_HEAD_OUTLINE, borderRight + PLAYER_HEAD_OUTLINE, borderBottom + PLAYER_HEAD_OUTLINE, MARKER_OUTLINE_COLOR);
        drawColorRect(borderLeft, borderTop, borderRight, borderBottom, borderColor, 1.0F);

        if (marker.skinTextureId <= 0) {
            currentFallbackHeads++;
            drawColorRect(centerX - halfHead, centerY - halfHead, centerX + halfHead, centerY + halfHead, markerColor.darker(0.72F), 1.0F);
            drawHeightIndicator(centerX, centerY, halfHead, heightDelta, borderColor);
            return;
        }

        currentSkinHeads++;
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, marker.skinTextureId);
        drawSkinRegion(centerX - halfHead, centerY - halfHead, PLAYER_HEAD_SIZE, 8.0F, 8.0F, 8.0F, 8.0F);
        drawSkinRegion(centerX - halfHead, centerY - halfHead, PLAYER_HEAD_SIZE, 40.0F, 8.0F, 8.0F, 8.0F);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        drawHeightIndicator(centerX, centerY, halfHead, heightDelta, borderColor);
    }

    private boolean isInvisibleToLocalPlayer(EntityPlayerSP localPlayer, EntityPlayer player) {
        return player.isInvisible() || player.isInvisibleToPlayer(localPlayer);
    }

    private void drawHeightIndicator(float centerX, float centerY, float halfHead, double heightDelta, OverlayColorResolver.Color color) {
        if (Math.abs(heightDelta) < PLAYER_HEIGHT_ARROW_THRESHOLD) {
            return;
        }

        float halfWidth = PLAYER_HEIGHT_ARROW_WIDTH * 0.5F;
        if (heightDelta > 0.0D) {
            float tipY = centerY - halfHead - PLAYER_HEAD_BORDER - PLAYER_HEIGHT_ARROW_GAP - PLAYER_HEIGHT_ARROW_HEIGHT;
            float baseY = tipY + PLAYER_HEIGHT_ARROW_HEIGHT;
            drawTriangle(centerX, tipY - PLAYER_HEIGHT_ARROW_OUTLINE, centerX - halfWidth - PLAYER_HEIGHT_ARROW_OUTLINE, baseY + PLAYER_HEIGHT_ARROW_OUTLINE, centerX + halfWidth + PLAYER_HEIGHT_ARROW_OUTLINE, baseY + PLAYER_HEIGHT_ARROW_OUTLINE, MARKER_OUTLINE_COLOR);
            drawColorTriangle(centerX, tipY, centerX - halfWidth, baseY, centerX + halfWidth, baseY, color, 1.0F);
        } else {
            float tipY = centerY + halfHead + PLAYER_HEAD_BORDER + PLAYER_HEIGHT_ARROW_GAP + PLAYER_HEIGHT_ARROW_HEIGHT;
            float baseY = tipY - PLAYER_HEIGHT_ARROW_HEIGHT;
            drawTriangle(centerX, tipY + PLAYER_HEIGHT_ARROW_OUTLINE, centerX - halfWidth - PLAYER_HEIGHT_ARROW_OUTLINE, baseY - PLAYER_HEIGHT_ARROW_OUTLINE, centerX + halfWidth + PLAYER_HEIGHT_ARROW_OUTLINE, baseY - PLAYER_HEIGHT_ARROW_OUTLINE, MARKER_OUTLINE_COLOR);
            drawColorTriangle(centerX, tipY, centerX - halfWidth, baseY, centerX + halfWidth, baseY, color, 1.0F);
        }
    }

    private void drawFireballMarker(FireballSnapshot fireball, float centerX, float centerY, double yawSin, double yawCos, float pixelsPerBlock) {
        double velocityX = (-fireball.motionX * yawCos - fireball.motionZ * yawSin) * pixelsPerBlock;
        double velocityY = (fireball.motionX * yawSin - fireball.motionZ * yawCos) * pixelsPerBlock;
        double velocityLength = Math.sqrt(velocityX * velocityX + velocityY * velocityY);
        if (velocityLength > FIREBALL_DIRECTION_EPSILON) {
            float directionX = (float) (velocityX / velocityLength);
            float directionY = (float) (velocityY / velocityLength);
            drawFireballArrow(centerX, centerY, directionX, directionY);
        }

        drawDiamond(centerX, centerY, FIREBALL_MARKER_RADIUS + 1.15F, FIREBALL_OUTLINE_COLOR);
        drawDiamond(centerX, centerY, FIREBALL_MARKER_RADIUS, FIREBALL_BODY_COLOR);
        drawDiamond(centerX, centerY, FIREBALL_CORE_RADIUS, FIREBALL_CORE_COLOR);
    }

    private void drawFireballArrow(float centerX, float centerY, float directionX, float directionY) {
        float perpendicularX = -directionY;
        float perpendicularY = directionX;
        float tipX = centerX + directionX * FIREBALL_ARROW_LENGTH;
        float tipY = centerY + directionY * FIREBALL_ARROW_LENGTH;
        float baseX = centerX - directionX * FIREBALL_ARROW_BACKSET;
        float baseY = centerY - directionY * FIREBALL_ARROW_BACKSET;

        drawTriangle(
                tipX + directionX,
                tipY + directionY,
                baseX + perpendicularX * (FIREBALL_ARROW_HALF_WIDTH + 0.8F),
                baseY + perpendicularY * (FIREBALL_ARROW_HALF_WIDTH + 0.8F),
                baseX - perpendicularX * (FIREBALL_ARROW_HALF_WIDTH + 0.8F),
                baseY - perpendicularY * (FIREBALL_ARROW_HALF_WIDTH + 0.8F),
                FIREBALL_OUTLINE_COLOR
        );
        drawTriangle(
                tipX,
                tipY,
                baseX + perpendicularX * FIREBALL_ARROW_HALF_WIDTH,
                baseY + perpendicularY * FIREBALL_ARROW_HALF_WIDTH,
                baseX - perpendicularX * FIREBALL_ARROW_HALF_WIDTH,
                baseY - perpendicularY * FIREBALL_ARROW_HALF_WIDTH,
                FIREBALL_CORE_COLOR
        );
    }

    private NetworkPlayerInfo playerInfo(Minecraft mc, EntityPlayer player) {
        NetHandlerPlayClient netHandler = mc.getNetHandler();
        if (netHandler == null) {
            return null;
        }

        NetworkPlayerInfo info = netHandler.getPlayerInfo(player.getUniqueID());
        if (info == null) {
            info = netHandler.getPlayerInfo(player.getName());
        }

        return info;
    }

    private int cachedSkinTextureId(Minecraft mc, EntityPlayer player) {
        NetworkPlayerInfo info = playerInfo(mc, player);
        ResourceLocation skin = info == null ? null : info.getLocationSkin();
        if (skin == null) {
            return -1;
        }

        ITextureObject skinTexture = mc.getTextureManager().getTexture(skin);
        return skinTexture == null ? -1 : skinTexture.getGlTextureId();
    }

    private void drawCenterArrow(float centerX, float centerY) {
        drawTriangle(centerX, centerY - 5.6F, centerX - 4.1F, centerY + 3.7F, centerX + 4.1F, centerY + 3.7F, 0xF20D0D0D);
        drawTriangle(centerX, centerY - 4.3F, centerX - 2.8F, centerY + 2.3F, centerX + 2.8F, centerY + 2.3F, 0xFFFFFFFF);
    }

    private void drawCircle(float centerX, float centerY, float radius, int color) {
        setColor(color);
        GL11.glBegin(GL11.GL_TRIANGLE_FAN);
        GL11.glVertex2f(centerX, centerY);
        for (int segment = 0; segment <= EXPANDED_MAP_SEGMENTS; segment++) {
            double angle = Math.PI * 2.0D * segment / EXPANDED_MAP_SEGMENTS;
            GL11.glVertex2f(
                    centerX + (float) Math.cos(angle) * radius,
                    centerY + (float) Math.sin(angle) * radius
            );
        }
        GL11.glEnd();
    }

    private void drawCircleRing(float centerX, float centerY, float outerRadius, float innerRadius, int color) {
        setColor(color);
        GL11.glBegin(GL11.GL_TRIANGLE_STRIP);
        for (int segment = 0; segment <= EXPANDED_MAP_SEGMENTS; segment++) {
            double angle = Math.PI * 2.0D * segment / EXPANDED_MAP_SEGMENTS;
            float cos = (float) Math.cos(angle);
            float sin = (float) Math.sin(angle);
            GL11.glVertex2f(centerX + cos * outerRadius, centerY + sin * outerRadius);
            GL11.glVertex2f(centerX + cos * innerRadius, centerY + sin * innerRadius);
        }
        GL11.glEnd();
    }

    private void drawClippedWorldRing(float mapCenterX, float mapCenterY, float clipRadius, float ringCenterX, float ringCenterY, float ringRadius, float thickness, int color) {
        float halfThickness = thickness * 0.5F;
        float innerRadius = Math.max(0.0F, ringRadius - halfThickness);
        float outerRadius = ringRadius + halfThickness;
        float clipRadiusSquared = clipRadius * clipRadius;
        setColor(color);
        GL11.glBegin(GL11.GL_QUADS);
        for (int segment = 0; segment < EXPANDED_MAP_SEGMENTS; segment++) {
            double angle1 = Math.PI * 2.0D * segment / EXPANDED_MAP_SEGMENTS;
            double angle2 = Math.PI * 2.0D * (segment + 1) / EXPANDED_MAP_SEGMENTS;
            float cos1 = (float) Math.cos(angle1);
            float sin1 = (float) Math.sin(angle1);
            float cos2 = (float) Math.cos(angle2);
            float sin2 = (float) Math.sin(angle2);
            float outerX1 = ringCenterX + cos1 * outerRadius;
            float outerY1 = ringCenterY + sin1 * outerRadius;
            float outerX2 = ringCenterX + cos2 * outerRadius;
            float outerY2 = ringCenterY + sin2 * outerRadius;
            float innerX2 = ringCenterX + cos2 * innerRadius;
            float innerY2 = ringCenterY + sin2 * innerRadius;
            float innerX1 = ringCenterX + cos1 * innerRadius;
            float innerY1 = ringCenterY + sin1 * innerRadius;
            if (!insideCircle(mapCenterX, mapCenterY, clipRadiusSquared, outerX1, outerY1)
                    || !insideCircle(mapCenterX, mapCenterY, clipRadiusSquared, outerX2, outerY2)
                    || !insideCircle(mapCenterX, mapCenterY, clipRadiusSquared, innerX2, innerY2)
                    || !insideCircle(mapCenterX, mapCenterY, clipRadiusSquared, innerX1, innerY1)) {
                continue;
            }
            GL11.glVertex2f(outerX1, outerY1);
            GL11.glVertex2f(outerX2, outerY2);
            GL11.glVertex2f(innerX2, innerY2);
            GL11.glVertex2f(innerX1, innerY1);
        }
        GL11.glEnd();
    }

    private boolean insideCircle(float centerX, float centerY, float radiusSquared, float x, float y) {
        float deltaX = x - centerX;
        float deltaY = y - centerY;
        return deltaX * deltaX + deltaY * deltaY <= radiusSquared;
    }

    private void drawRect(float left, float top, float right, float bottom, int color) {
        setColor(color);
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glVertex2f(left, bottom);
        GL11.glVertex2f(right, bottom);
        GL11.glVertex2f(right, top);
        GL11.glVertex2f(left, top);
        GL11.glEnd();
    }

    private void drawTriangle(float x1, float y1, float x2, float y2, float x3, float y3, int color) {
        setColor(color);
        GL11.glBegin(GL11.GL_TRIANGLES);
        GL11.glVertex2f(x1, y1);
        GL11.glVertex2f(x2, y2);
        GL11.glVertex2f(x3, y3);
        GL11.glEnd();
    }

    private void drawDiamond(float centerX, float centerY, float radius, int color) {
        setColor(color);
        GL11.glBegin(GL11.GL_TRIANGLE_FAN);
        GL11.glVertex2f(centerX, centerY);
        GL11.glVertex2f(centerX, centerY - radius);
        GL11.glVertex2f(centerX + radius, centerY);
        GL11.glVertex2f(centerX, centerY + radius);
        GL11.glVertex2f(centerX - radius, centerY);
        GL11.glVertex2f(centerX, centerY - radius);
        GL11.glEnd();
    }

    private void drawColorTriangle(float x1, float y1, float x2, float y2, float x3, float y3, OverlayColorResolver.Color color, float alpha) {
        GL11.glColor4f(color.red, color.green, color.blue, alpha);
        GL11.glBegin(GL11.GL_TRIANGLES);
        GL11.glVertex2f(x1, y1);
        GL11.glVertex2f(x2, y2);
        GL11.glVertex2f(x3, y3);
        GL11.glEnd();
    }

    private void drawColorRect(float left, float top, float right, float bottom, OverlayColorResolver.Color color, float alpha) {
        GL11.glColor4f(color.red, color.green, color.blue, alpha);
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glVertex2f(left, bottom);
        GL11.glVertex2f(right, bottom);
        GL11.glVertex2f(right, top);
        GL11.glVertex2f(left, top);
        GL11.glEnd();
    }

    private void drawSkinRegion(float left, float top, float size, float textureX, float textureY, float textureWidth, float textureHeight) {
        float right = left + size;
        float bottom = top + size;
        float u1 = textureX / 64.0F;
        float v1 = textureY / 64.0F;
        float u2 = (textureX + textureWidth) / 64.0F;
        float v2 = (textureY + textureHeight) / 64.0F;

        GL11.glBegin(GL11.GL_QUADS);
        GL11.glTexCoord2f(u1, v2);
        GL11.glVertex2f(left, bottom);
        GL11.glTexCoord2f(u2, v2);
        GL11.glVertex2f(right, bottom);
        GL11.glTexCoord2f(u2, v1);
        GL11.glVertex2f(right, top);
        GL11.glTexCoord2f(u1, v1);
        GL11.glVertex2f(left, top);
        GL11.glEnd();
    }

    private void setColor(int color) {
        float alpha = (float) (color >> 24 & 255) / 255.0F;
        float red = (float) (color >> 16 & 255) / 255.0F;
        float green = (float) (color >> 8 & 255) / 255.0F;
        float blue = (float) (color & 255) / 255.0F;
        GL11.glColor4f(red, green, blue, alpha);
    }

    private void resetCurrentRenderProfile() {
        currentTerrainDrawNanos = 0L;
        currentMarkerNanos = 0L;
        currentMarkersDrawn = 0;
        currentSkinHeads = 0;
        currentFallbackHeads = 0;
        currentFireballMarkersDrawn = 0;
    }

    private void resetCurrentMarkerSnapshotProfile() {
        currentPlayersScanned = 0;
        currentPlayersEligible = 0;
        currentPlayersFilteredSelf = 0;
        currentPlayersFilteredDead = 0;
        currentPlayersFilteredNotAlive = 0;
        currentPlayersFilteredNpc = 0;
        currentPlayersOutOfRange = 0;
        currentInvisibleMarkers = 0;
        currentFireballEntitiesScanned = 0;
        currentFireballsEligible = 0;
        currentFireballsOutOfRange = 0;
        currentMarkerPlayerIds.clear();
    }

    private void recordFrameInterval(long renderStartNanos) {
        if (lastProfileRenderStartNanos > 0L) {
            long intervalNanos = renderStartNanos - lastProfileRenderStartNanos;
            if (intervalNanos > 0L) {
                profileFrameIntervalCount++;
                profileFrameIntervalTotalNanos += intervalNanos;
                profileFrameIntervalMaxNanos = Math.max(profileFrameIntervalMaxNanos, intervalNanos);
            }
        }
        lastProfileRenderStartNanos = renderStartNanos;
    }

    private void recordRenderProfile(long renderNanos) {
        profileRenderFrameCount++;
        profileRenderTotalNanos += renderNanos;
        profileRenderMaxNanos = Math.max(profileRenderMaxNanos, renderNanos);
        profileTerrainDrawTotalNanos += currentTerrainDrawNanos;
        profileTerrainDrawMaxNanos = Math.max(profileTerrainDrawMaxNanos, currentTerrainDrawNanos);
        profileMarkerTotalNanos += currentMarkerNanos;
        profileMarkerMaxNanos = Math.max(profileMarkerMaxNanos, currentMarkerNanos);
        profileMarkersDrawn += currentMarkersDrawn;
        profileSkinHeads += currentSkinHeads;
        profileFallbackHeads += currentFallbackHeads;
        profileFireballMarkersDrawn += currentFireballMarkersDrawn;
    }

    private void recordMarkerSnapshotProfile() {
        profilePlayersScanned += currentPlayersScanned;
        profilePlayersEligible += currentPlayersEligible;
        profilePlayersFilteredSelf += currentPlayersFilteredSelf;
        profilePlayersFilteredDead += currentPlayersFilteredDead;
        profilePlayersFilteredNotAlive += currentPlayersFilteredNotAlive;
        profilePlayersFilteredNpc += currentPlayersFilteredNpc;
        profilePlayersOutOfRange += currentPlayersOutOfRange;
        profileInvisibleMarkers += currentInvisibleMarkers;
        profileFireballEntitiesScanned += currentFireballEntitiesScanned;
        profileFireballsEligible += currentFireballsEligible;
        profileFireballsOutOfRange += currentFireballsOutOfRange;
    }

    private void recordTickProfile(long tickNanos, long adaptiveZoomNanos, long terrainTickCallNanos) {
        profileTickCount++;
        profileTickTotalNanos += tickNanos;
        profileTickMaxNanos = Math.max(profileTickMaxNanos, tickNanos);
        profileAdaptiveZoomTotalNanos += adaptiveZoomNanos;
        profileAdaptiveZoomMaxNanos = Math.max(profileAdaptiveZoomMaxNanos, adaptiveZoomNanos);
        profileTerrainTickCallTotalNanos += terrainTickCallNanos;
        profileTerrainTickCallMaxNanos = Math.max(profileTerrainTickCallMaxNanos, terrainTickCallNanos);
    }

    private void recordLoadedPlayerSnapshot(Minecraft mc) {
        currentLoadedPlayerIds.clear();
        int loadedPlayers = 0;
        int nullUuids = 0;
        for (EntityPlayer player : mc.theWorld.playerEntities) {
            if (player == null) {
                continue;
            }
            loadedPlayers++;
            UUID playerId = player.getUniqueID();
            if (playerId == null) {
                nullUuids++;
                continue;
            }
            if (!OptimalZoneMod.isSelf(player)) {
                currentLoadedPlayerIds.add(playerId);
            }
        }

        profileLoadedPlayerSamples++;
        profileLoadedPlayersTotal += loadedPlayers;
        profileLoadedPlayersMax = Math.max(profileLoadedPlayersMax, loadedPlayers);
        profileLoadedPlayerNullUuids += nullUuids;
        profileLoadedPlayerAppears += currentLoadedPlayerIds.countMissingFrom(previousLoadedPlayerIds);
        profileLoadedPlayerLeaves += previousLoadedPlayerIds.countMissingFrom(currentLoadedPlayerIds);
        previousLoadedPlayerIds.copyFrom(currentLoadedPlayerIds);
    }

    private void recordMarkerRangeChurn() {
        profileMarkerRangeEntries += currentMarkerPlayerIds.countMissingFrom(previousMarkerPlayerIds);
        profileMarkerRangeExits += previousMarkerPlayerIds.countMissingFrom(currentMarkerPlayerIds);
        previousMarkerPlayerIds.copyFrom(currentMarkerPlayerIds);
    }

    private void seedPlayerTracking() {
        seedPlayerTracking(Minecraft.getMinecraft());
    }

    private void seedPlayerTracking(Minecraft mc) {
        clearPlayerTracking();
        if (mc == null || mc.thePlayer == null || mc.theWorld == null) {
            return;
        }

        double localX = mc.thePlayer.posX;
        double localZ = mc.thePlayer.posZ;
        double markerRadiusSquared = currentMarkerRadiusSquared(mc);
        for (EntityPlayer player : mc.theWorld.playerEntities) {
            if (player == null || OptimalZoneMod.isSelf(player)) {
                continue;
            }

            UUID playerId = player.getUniqueID();
            if (playerId != null) {
                previousLoadedPlayerIds.add(playerId);
            }

            if (playerId != null && shouldRenderPlayer(player)) {
                double deltaX = player.posX - localX;
                double deltaZ = player.posZ - localZ;
                if (deltaX * deltaX + deltaZ * deltaZ <= markerRadiusSquared) {
                    previousMarkerPlayerIds.add(playerId);
                }
            }
        }
    }

    private double currentMarkerRadiusSquared(Minecraft mc) {
        boolean expanded = expandedMapOpen;
        float mapSize = expanded ? expandedMapSize(new ScaledResolution(mc)) : MAP_SIZE;
        float mapRadius = mapSize * 0.5F - INNER_PADDING;
        double blocksPerPixel = expanded ? EXPANDED_MAP_BLOCKS_PER_PIXEL : smoothedMinimapBlocksPerPixel;
        double markerRadiusPixels = expanded
                ? mapRadius - PLAYER_HEAD_SIZE * 0.5F - PLAYER_HEAD_BORDER - PLAYER_HEAD_OUTLINE
                : mapRadius;
        double markerRadiusBlocks = markerRadiusPixels * blocksPerPixel;
        return markerRadiusBlocks * markerRadiusBlocks;
    }

    private void clearPlayerTracking() {
        previousLoadedPlayerIds.clear();
        currentLoadedPlayerIds.clear();
        previousMarkerPlayerIds.clear();
        currentMarkerPlayerIds.clear();
        markerSnapshotCount = 0;
        fireballSnapshotCount = 0;
    }

    private MarkerSnapshot markerSnapshot(int index) {
        if (index >= markerSnapshots.length) {
            markerSnapshots = Arrays.copyOf(markerSnapshots, markerSnapshots.length * 2);
        }
        MarkerSnapshot marker = markerSnapshots[index];
        if (marker == null) {
            marker = new MarkerSnapshot();
            markerSnapshots[index] = marker;
        }
        return marker;
    }

    private FireballSnapshot fireballSnapshot(int index) {
        if (index >= fireballSnapshots.length) {
            fireballSnapshots = Arrays.copyOf(fireballSnapshots, fireballSnapshots.length * 2);
        }
        FireballSnapshot snapshot = fireballSnapshots[index];
        if (snapshot == null) {
            snapshot = new FireballSnapshot();
            fireballSnapshots[index] = snapshot;
        }
        return snapshot;
    }

    private static final class MarkerSnapshot {
        private double lastX;
        private double lastY;
        private double lastZ;
        private double x;
        private double y;
        private double z;
        private OverlayColorResolver.Color color = OverlayColorResolver.fallback();
        private int skinTextureId = -1;
        private boolean invisible;

        private void update(EntityPlayer player, OverlayColorResolver.Color color, int skinTextureId, boolean invisible) {
            lastX = player.lastTickPosX;
            lastY = player.lastTickPosY;
            lastZ = player.lastTickPosZ;
            x = player.posX;
            y = player.posY;
            z = player.posZ;
            this.color = color;
            this.skinTextureId = skinTextureId;
            this.invisible = invisible;
        }
    }

    private static final class FireballSnapshot {
        private double lastX;
        private double lastZ;
        private double x;
        private double z;
        private double motionX;
        private double motionZ;

        private void update(EntityFireball fireball) {
            lastX = fireball.lastTickPosX;
            lastZ = fireball.lastTickPosZ;
            x = fireball.posX;
            z = fireball.posZ;
            motionX = fireball.motionX;
            motionZ = fireball.motionZ;
        }
    }

    private static final class ProfileUuidSet {
        private long[] mostBits = new long[16];
        private long[] leastBits = new long[16];
        private int size;

        private void clear() {
            size = 0;
        }

        private void add(UUID id) {
            long most = id.getMostSignificantBits();
            long least = id.getLeastSignificantBits();
            if (contains(most, least)) {
                return;
            }
            ensureCapacity(size + 1);
            mostBits[size] = most;
            leastBits[size] = least;
            size++;
        }

        private int countMissingFrom(ProfileUuidSet other) {
            int missing = 0;
            for (int index = 0; index < size; index++) {
                if (!other.contains(mostBits[index], leastBits[index])) {
                    missing++;
                }
            }
            return missing;
        }

        private void copyFrom(ProfileUuidSet other) {
            ensureCapacity(other.size);
            System.arraycopy(other.mostBits, 0, mostBits, 0, other.size);
            System.arraycopy(other.leastBits, 0, leastBits, 0, other.size);
            size = other.size;
        }

        private boolean contains(long most, long least) {
            for (int index = 0; index < size; index++) {
                if (mostBits[index] == most && leastBits[index] == least) {
                    return true;
                }
            }
            return false;
        }

        private void ensureCapacity(int capacity) {
            if (capacity <= mostBits.length) {
                return;
            }
            int newCapacity = Math.max(capacity, mostBits.length * 2);
            mostBits = Arrays.copyOf(mostBits, newCapacity);
            leastBits = Arrays.copyOf(leastBits, newCapacity);
        }
    }

    private String renderPerformanceSummary() {
        return String.format(
                Locale.ROOT,
                "[Minimap Render] renderFrames=%d frameGaps=%d frameGapAvg=%.3fms frameGapMax=%.3fms renderAvg=%.3fms renderMax=%.3fms terrainDrawAvg=%.3fms terrainDrawMax=%.3fms markersAvg=%.3fms markersMax=%.3fms ticks=%d tickAvg=%.3fms tickMax=%.3fms adaptiveAvg=%.3fms adaptiveMax=%.3fms terrainTickCallAvg=%.3fms terrainTickCallMax=%.3fms",
                profileRenderFrameCount,
                profileFrameIntervalCount,
                averageMillis(profileFrameIntervalTotalNanos, profileFrameIntervalCount),
                nanosToMillis(profileFrameIntervalMaxNanos),
                averageMillis(profileRenderTotalNanos, profileRenderFrameCount),
                nanosToMillis(profileRenderMaxNanos),
                averageMillis(profileTerrainDrawTotalNanos, profileRenderFrameCount),
                nanosToMillis(profileTerrainDrawMaxNanos),
                averageMillis(profileMarkerTotalNanos, profileRenderFrameCount),
                nanosToMillis(profileMarkerMaxNanos),
                profileTickCount,
                averageMillis(profileTickTotalNanos, profileTickCount),
                nanosToMillis(profileTickMaxNanos),
                averageMillis(profileAdaptiveZoomTotalNanos, profileTickCount),
                nanosToMillis(profileAdaptiveZoomMaxNanos),
                averageMillis(profileTerrainTickCallTotalNanos, profileTickCount),
                nanosToMillis(profileTerrainTickCallMaxNanos)
        );
    }

    private String playerPerformanceSummary() {
        return String.format(
                Locale.ROOT,
                "[Minimap Players] loadedSamples=%d loadedAvg=%.1f loadedMax=%d appeared=%d left=%d nullUuid=%d scanAvg=%.1f scanTotal=%d eligibleAvg=%.1f eligibleTotal=%d markerAvg=%.1f markerTotal=%d self=%d dead=%d notAlive=%d npc=%d outRange=%d markerEnter=%d markerExit=%d invisibleMarkers=%d skinHeads=%d fallbackHeads=%d",
                profileLoadedPlayerSamples,
                average(profileLoadedPlayersTotal, profileLoadedPlayerSamples),
                profileLoadedPlayersMax,
                profileLoadedPlayerAppears,
                profileLoadedPlayerLeaves,
                profileLoadedPlayerNullUuids,
                average(profilePlayersScanned, profileTickCount),
                profilePlayersScanned,
                average(profilePlayersEligible, profileTickCount),
                profilePlayersEligible,
                average(profileMarkersDrawn, profileRenderFrameCount),
                profileMarkersDrawn,
                profilePlayersFilteredSelf,
                profilePlayersFilteredDead,
                profilePlayersFilteredNotAlive,
                profilePlayersFilteredNpc,
                profilePlayersOutOfRange,
                profileMarkerRangeEntries,
                profileMarkerRangeExits,
                profileInvisibleMarkers,
                profileSkinHeads,
                profileFallbackHeads
        );
    }

    private String projectilePerformanceSummary() {
        return String.format(
                Locale.ROOT,
                "[Minimap Projectiles] fireballScanAvg=%.1f fireballScanTotal=%d fireballEligibleAvg=%.1f fireballEligibleTotal=%d fireballMarkerAvg=%.1f fireballMarkerTotal=%d fireballOutRange=%d",
                average(profileFireballEntitiesScanned, profileTickCount),
                profileFireballEntitiesScanned,
                average(profileFireballsEligible, profileTickCount),
                profileFireballsEligible,
                average(profileFireballMarkersDrawn, profileRenderFrameCount),
                profileFireballMarkersDrawn,
                profileFireballsOutOfRange
        );
    }

    private String jvmPerformanceSummary() {
        long usedMemory = usedMemoryBytes();
        long totalMemory = Runtime.getRuntime().totalMemory();
        return String.format(
                Locale.ROOT,
                "[Minimap JVM] gcCount=%d gcTime=%dms usedMem=%.1fMB usedDelta=%.1fMB totalMem=%.1fMB totalDelta=%.1fMB",
                totalGarbageCollectionCount() - profileGcCountBaseline,
                totalGarbageCollectionMillis() - profileGcTimeBaseline,
                bytesToMegabytes(usedMemory),
                bytesToMegabytes(usedMemory - profileUsedMemoryBaseline),
                bytesToMegabytes(totalMemory),
                bytesToMegabytes(totalMemory - profileTotalMemoryBaseline)
        );
    }

    private static double average(long total, long count) {
        return count == 0L ? 0.0D : (double) total / (double) count;
    }

    private static long totalGarbageCollectionCount() {
        long total = 0L;
        for (GarbageCollectorMXBean bean : ManagementFactory.getGarbageCollectorMXBeans()) {
            long count = bean.getCollectionCount();
            if (count > 0L) {
                total += count;
            }
        }
        return total;
    }

    private static long totalGarbageCollectionMillis() {
        long total = 0L;
        for (GarbageCollectorMXBean bean : ManagementFactory.getGarbageCollectorMXBeans()) {
            long time = bean.getCollectionTime();
            if (time > 0L) {
                total += time;
            }
        }
        return total;
    }

    private static long usedMemoryBytes() {
        Runtime runtime = Runtime.getRuntime();
        return runtime.totalMemory() - runtime.freeMemory();
    }

    private static double bytesToMegabytes(long bytes) {
        return bytes / 1048576.0D;
    }

    private static double averageMillis(long totalNanos, int count) {
        return count == 0 ? 0.0D : nanosToMillis(totalNanos) / count;
    }

    private static double nanosToMillis(long nanos) {
        return nanos / 1000000.0D;
    }

}
