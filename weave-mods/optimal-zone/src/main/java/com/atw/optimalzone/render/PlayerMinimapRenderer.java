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
import net.minecraft.util.MathHelper;
import net.minecraft.util.ResourceLocation;
import net.weavemc.loader.api.event.RenderGameOverlayEvent;
import net.weavemc.loader.api.event.TickEvent;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

public class PlayerMinimapRenderer {
    private static final int MAP_SIZE = 128;
    private static final int SCREEN_MARGIN = 10;
    private static final int INNER_PADDING = 2;
    private static final int EXPANDED_MAP_MAX_SIZE = 252;
    private static final int EXPANDED_MAP_SCREEN_MARGIN = 24;
    private static final int EXPANDED_MAP_SEGMENTS = 128;
    private static final double MINIMAP_BLOCKS_PER_PIXEL = 1.5D;
    private static final double EXPANDED_MAP_BLOCKS_PER_PIXEL = 2.25D;
    private static final float PLAYER_HEAD_SIZE = 8.0F;
    private static final float PLAYER_HEAD_BORDER = 0.85F;
    private static final float PLAYER_HEAD_OUTLINE = 0.75F;
    private static final double PLAYER_HEIGHT_ARROW_THRESHOLD = 1.5D;
    private static final float PLAYER_HEIGHT_ARROW_WIDTH = 4.2F;
    private static final float PLAYER_HEIGHT_ARROW_HEIGHT = 3.4F;
    private static final float PLAYER_HEIGHT_ARROW_GAP = 1.6F;
    private static final float PLAYER_HEIGHT_ARROW_OUTLINE = 1.0F;
    private static final double NANOS_PER_CLIENT_TICK = 50000000.0D;
    private static final float ROTATION_SMOOTHING_RESPONSE = 55.0F;
    private static final float ROTATION_SNAP_DEGREES = 35.0F;
    private static final int MARKER_OUTLINE_COLOR = 0xD0000000;
    private static final int HUD_STATE_MASK = GL11.GL_ENABLE_BIT
            | GL11.GL_COLOR_BUFFER_BIT
            | GL11.GL_DEPTH_BUFFER_BIT
            | GL11.GL_TEXTURE_BIT
            | GL11.GL_CURRENT_BIT;

    private final OptimalZoneMod mod;
    private final MinimapTerrainManager terrainManager = new MinimapTerrainManager();
    private Object tickWorld;
    private long lastClientTickNanos;
    private boolean hasYawSamples;
    private boolean hasSmoothedYaw;
    private float previousTickYaw;
    private float currentTickYaw;
    private float smoothedYaw;
    private long lastSmoothedYawNanos;
    private boolean expandedMapOpen;
    private boolean expandedMapKeyDown;

    public PlayerMinimapRenderer(OptimalZoneMod mod) {
        this.mod = mod;
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

    public void onTick(TickEvent.Post event) {
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
        if (mod.shouldRenderMinimap()) {
            terrainManager.tick(
                    mc.theWorld,
                    mc.thePlayer.posX,
                    mc.thePlayer.posY,
                    mc.thePlayer.posZ,
                    expandedMapOpen
            );
        }
    }

    public void render(RenderGameOverlayEvent.Post event) {
        if (!mod.shouldRenderMinimap()) {
            return;
        }

        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.thePlayer == null || mc.theWorld == null) {
            return;
        }

        long renderStartNanos = System.nanoTime();
        long nowNanos = renderStartNanos;
        float partialTicks = framePartialTicks(mc, event.getPartialTicks(), nowNanos);

        GL11.glPushAttrib(HUD_STATE_MASK);
        GL11.glPushMatrix();
        try {
            setupHudState();
            drawMinimap(mc, partialTicks, smoothRenderYaw(renderYaw(mc, partialTicks), nowNanos));
        } finally {
            GL11.glPopMatrix();
            GL11.glPopAttrib();
        }
        terrainManager.recordHudRender(System.nanoTime() - renderStartNanos);
    }

    private void drawMinimap(Minecraft mc, float partialTicks, float yaw) {
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
        double blocksPerPixel = expanded ? EXPANDED_MAP_BLOCKS_PER_PIXEL : MINIMAP_BLOCKS_PER_PIXEL;
        float pixelsPerBlock = (float) (1.0D / blocksPerPixel);
        double markerRadiusPixels = expanded
                ? mapRadius - PLAYER_HEAD_SIZE * 0.5F - PLAYER_HEAD_BORDER - PLAYER_HEAD_OUTLINE
                : mapRadius;
        double playerRadiusBlocks = markerRadiusPixels * blocksPerPixel;
        double playerRadiusSquared = playerRadiusBlocks * playerRadiusBlocks;

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
        terrainManager.draw(
                expanded,
                centerX,
                centerY,
                mapRadius,
                localX,
                localZ,
                yawSin,
                yawCos
        );
        if (expanded) {
            drawCircleRing(centerX, centerY, mapRadius + 1.5F, mapRadius, 0xE8FFFFFF);
            drawCircleRing(centerX, centerY, mapRadius + 2.5F, mapRadius + 1.5F, 0xD0000000);
        } else {
            drawBorder(left, top, right, bottom);
        }

        for (EntityPlayer player : mc.theWorld.playerEntities) {
            if (!shouldRenderPlayer(player)) {
                continue;
            }

            double playerX = OptimalZoneMod.interpolate(player.lastTickPosX, player.posX, partialTicks);
            double playerY = OptimalZoneMod.interpolate(player.lastTickPosY, player.posY, partialTicks);
            double playerZ = OptimalZoneMod.interpolate(player.lastTickPosZ, player.posZ, partialTicks);
            double deltaX = playerX - localX;
            double deltaZ = playerZ - localZ;
            double horizontalDistanceSquared = deltaX * deltaX + deltaZ * deltaZ;
            if (horizontalDistanceSquared > playerRadiusSquared) {
                continue;
            }

            float mapX = (float) ((-deltaX * yawCos - deltaZ * yawSin) * pixelsPerBlock);
            float mapY = (float) ((deltaX * yawSin - deltaZ * yawCos) * pixelsPerBlock);
            OverlayColorResolver.Color color = OverlayColorResolver.colorFor(player);
            drawPlayerHead(mc, player, centerX + mapX, centerY + mapY, color, playerY - localY);
        }

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

    private boolean shouldRenderPlayer(EntityPlayer player) {
        return player != null
                && !OptimalZoneMod.isSelf(player)
                && !player.isDead
                && !player.isInvisible()
                && player.isEntityAlive()
                && OverlayPlayerClassifier.shouldTreatAsRealPlayer(player);
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

    private void drawPlayerHead(Minecraft mc, EntityPlayer player, float centerX, float centerY, OverlayColorResolver.Color color, double heightDelta) {
        float halfHead = PLAYER_HEAD_SIZE * 0.5F;
        float borderLeft = centerX - halfHead - PLAYER_HEAD_BORDER;
        float borderTop = centerY - halfHead - PLAYER_HEAD_BORDER;
        float borderRight = centerX + halfHead + PLAYER_HEAD_BORDER;
        float borderBottom = centerY + halfHead + PLAYER_HEAD_BORDER;

        drawRect(borderLeft - PLAYER_HEAD_OUTLINE, borderTop - PLAYER_HEAD_OUTLINE, borderRight + PLAYER_HEAD_OUTLINE, borderBottom + PLAYER_HEAD_OUTLINE, MARKER_OUTLINE_COLOR);
        drawColorRect(borderLeft, borderTop, borderRight, borderBottom, color, 1.0F);

        NetworkPlayerInfo info = playerInfo(mc, player);
        ResourceLocation skin = info == null ? null : info.getLocationSkin();
        if (skin == null) {
            drawColorRect(centerX - halfHead, centerY - halfHead, centerX + halfHead, centerY + halfHead, color.darker(0.72F), 1.0F);
            drawHeightIndicator(centerX, centerY, halfHead, heightDelta, color);
            return;
        }

        ITextureObject skinTexture = mc.getTextureManager().getTexture(skin);
        if (skinTexture == null) {
            drawColorRect(centerX - halfHead, centerY - halfHead, centerX + halfHead, centerY + halfHead, color.darker(0.72F), 1.0F);
            drawHeightIndicator(centerX, centerY, halfHead, heightDelta, color);
            return;
        }

        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, skinTexture.getGlTextureId());
        drawSkinRegion(centerX - halfHead, centerY - halfHead, PLAYER_HEAD_SIZE, 8.0F, 8.0F, 8.0F, 8.0F);
        drawSkinRegion(centerX - halfHead, centerY - halfHead, PLAYER_HEAD_SIZE, 40.0F, 8.0F, 8.0F, 8.0F);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        drawHeightIndicator(centerX, centerY, halfHead, heightDelta, color);
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

}
