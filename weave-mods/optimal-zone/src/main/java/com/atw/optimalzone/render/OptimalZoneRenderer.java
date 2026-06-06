package com.atw.optimalzone.render;

import com.atw.optimalzone.OptimalZoneMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;
import net.weavemc.loader.api.event.RenderLivingEvent;
import org.lwjgl.opengl.GL11;

public class OptimalZoneRenderer {
    private static final double MAX_DISTANCE = 6.0D;
    private static final double MAX_DISTANCE_SQ = MAX_DISTANCE * MAX_DISTANCE;
    private static final double HALF_FOV_DEGREES = 25.0D;
    private static final double PATCH_SIZE = 0.22D;
    private static final double PATCH_HOVER_PADDING = 0.06D;
    private static final double SMOOTHING = 0.35D;
    private static final double HITBOX_INSET = 0.01D;

    private final OptimalZoneMod mod;
    private int smoothedTargetId = -1;
    private Vec3 smoothedCenter;
    private long cachedWorldTime = Long.MIN_VALUE;
    private EntityPlayer cachedTarget;

    public OptimalZoneRenderer(OptimalZoneMod mod) {
        this.mod = mod;
    }

    public void render(RenderLivingEvent.Post event) {
        if (mod.isRenderingOccludedPlayerOverlay()) {
            return;
        }

        if (!mod.shouldRenderNow()) {
            resetTargetCache();
            return;
        }

        EntityLivingBase entity = event.getEntity();
        if (!(entity instanceof EntityPlayer)) {
            return;
        }

        EntityPlayer player = (EntityPlayer) entity;
        EntityPlayer target = currentTarget(event.getPartialTicks());
        if (target == null || target != player) {
            return;
        }

        Vec3 markerCenter = computeMarkerCenter(player, event.getPartialTicks());
        if (markerCenter == null) {
            return;
        }

        Vec3 smoothed = smoothCenter(player, markerCenter);
        drawMarker(player, smoothed, event.getX(), event.getY(), event.getZ(), event.getPartialTicks());
    }

    private EntityPlayer currentTarget(float partialTicks) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null) {
            resetTargetCache();
            return null;
        }

        long worldTime = mc.theWorld.getTotalWorldTime();
        if (cachedWorldTime != worldTime) {
            cachedWorldTime = worldTime;
            cachedTarget = selectTarget(partialTicks);
            if (cachedTarget == null) {
                smoothedTargetId = -1;
                smoothedCenter = null;
            }
        }

        return cachedTarget;
    }

    private void resetTargetCache() {
        cachedWorldTime = Long.MIN_VALUE;
        cachedTarget = null;
        smoothedTargetId = -1;
        smoothedCenter = null;
    }

    private EntityPlayer selectTarget(float partialTicks) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || mc.theWorld == null) {
            return null;
        }

        EntityPlayer bestPlayer = null;
        double bestAngle = Double.MAX_VALUE;

        for (Object object : mc.theWorld.playerEntities) {
            if (!(object instanceof EntityPlayer)) {
                continue;
            }

            EntityPlayer candidate = (EntityPlayer) object;
            if (!isValidTarget(candidate)) {
                continue;
            }

            double angle = angularDistanceTo(candidate, partialTicks);
            if (angle <= HALF_FOV_DEGREES && angle < bestAngle) {
                bestAngle = angle;
                bestPlayer = candidate;
            }
        }

        return bestPlayer;
    }

    private boolean isValidTarget(EntityPlayer player) {
        Minecraft mc = Minecraft.getMinecraft();
        if (player == null || OptimalZoneMod.isSelf(player) || player.isDead || player.isInvisible()) {
            return false;
        }
        if (player.isInvisibleToPlayer(mc.thePlayer) || !player.isEntityAlive()) {
            return false;
        }
        if (mc.thePlayer.getDistanceSqToEntity(player) > MAX_DISTANCE_SQ) {
            return false;
        }

        Vec3 eyes = eyePosition(mc.thePlayer, 1.0F);
        Vec3 center = playerCenter(player, 1.0F);
        return mc.theWorld.rayTraceBlocks(eyes, center, false, true, false) == null;
    }

    private double angularDistanceTo(EntityPlayer player, float partialTicks) {
        Minecraft mc = Minecraft.getMinecraft();
        Vec3 eyes = eyePosition(mc.thePlayer, partialTicks);
        Vec3 target = closestPoint(interpolatedHitbox(player, partialTicks), eyes);

        double dx = target.xCoord - eyes.xCoord;
        double dy = target.yCoord - eyes.yCoord;
        double dz = target.zCoord - eyes.zCoord;
        double horizontal = Math.sqrt(dx * dx + dz * dz);

        float desiredYaw = (float) (Math.atan2(dz, dx) * 180.0D / Math.PI) - 90.0F;
        float desiredPitch = (float) (-(Math.atan2(dy, horizontal) * 180.0D / Math.PI));
        float yawDelta = MathHelper.wrapAngleTo180_float(desiredYaw - mc.thePlayer.rotationYaw);
        float pitchDelta = MathHelper.wrapAngleTo180_float(desiredPitch - mc.thePlayer.rotationPitch);
        return Math.sqrt(yawDelta * yawDelta + pitchDelta * pitchDelta);
    }

    private Vec3 computeMarkerCenter(EntityPlayer player, float partialTicks) {
        Minecraft mc = Minecraft.getMinecraft();
        Vec3 eyes = eyePosition(mc.thePlayer, partialTicks);
        AxisAlignedBB box = interpolatedHitbox(player, partialTicks);
        SurfacePoint surfacePoint = closestSurfacePoint(box, eyes);
        return insetInsideHitbox(box, surfacePoint);
    }

    private Vec3 closestPoint(AxisAlignedBB box, Vec3 point) {
        return new Vec3(
                clamp(point.xCoord, box.minX, box.maxX),
                clamp(point.yCoord, box.minY, box.maxY),
                clamp(point.zCoord, box.minZ, box.maxZ)
        );
    }

    private SurfacePoint closestSurfacePoint(AxisAlignedBB box, Vec3 point) {
        Vec3 closest = closestPoint(box, point);
        double normalX = 0.0D;
        double normalY = 0.0D;
        double normalZ = 0.0D;

        if (point.xCoord < box.minX) {
            normalX = -1.0D;
        } else if (point.xCoord > box.maxX) {
            normalX = 1.0D;
        }
        if (point.yCoord < box.minY) {
            normalY = -1.0D;
        } else if (point.yCoord > box.maxY) {
            normalY = 1.0D;
        }
        if (point.zCoord < box.minZ) {
            normalZ = -1.0D;
        } else if (point.zCoord > box.maxZ) {
            normalZ = 1.0D;
        }

        if (normalX == 0.0D && normalY == 0.0D && normalZ == 0.0D) {
            Vec3 clamped = closest;
            double minXDistance = Math.abs(point.xCoord - box.minX);
            double maxXDistance = Math.abs(box.maxX - point.xCoord);
            double minYDistance = Math.abs(point.yCoord - box.minY);
            double maxYDistance = Math.abs(box.maxY - point.yCoord);
            double minZDistance = Math.abs(point.zCoord - box.minZ);
            double maxZDistance = Math.abs(box.maxZ - point.zCoord);

            double bestDistance = minXDistance;
            closest = new Vec3(box.minX, clamped.yCoord, clamped.zCoord);
            normalX = -1.0D;

            if (maxXDistance < bestDistance) {
                bestDistance = maxXDistance;
                closest = new Vec3(box.maxX, clamped.yCoord, clamped.zCoord);
                normalX = 1.0D;
                normalY = 0.0D;
                normalZ = 0.0D;
            }
            if (minYDistance < bestDistance) {
                bestDistance = minYDistance;
                closest = new Vec3(clamped.xCoord, box.minY, clamped.zCoord);
                normalX = 0.0D;
                normalY = -1.0D;
                normalZ = 0.0D;
            }
            if (maxYDistance < bestDistance) {
                bestDistance = maxYDistance;
                closest = new Vec3(clamped.xCoord, box.maxY, clamped.zCoord);
                normalX = 0.0D;
                normalY = 1.0D;
                normalZ = 0.0D;
            }
            if (minZDistance < bestDistance) {
                bestDistance = minZDistance;
                closest = new Vec3(clamped.xCoord, clamped.yCoord, box.minZ);
                normalX = 0.0D;
                normalY = 0.0D;
                normalZ = -1.0D;
            }
            if (maxZDistance < bestDistance) {
                closest = new Vec3(clamped.xCoord, clamped.yCoord, box.maxZ);
                normalX = 0.0D;
                normalY = 0.0D;
                normalZ = 1.0D;
            }
        }

        return new SurfacePoint(closest, normalX, normalY, normalZ);
    }

    private Vec3 insetInsideHitbox(AxisAlignedBB box, SurfacePoint surfacePoint) {
        Vec3 point = surfacePoint.point;
        return new Vec3(
                clamp(point.xCoord - surfacePoint.normalX * HITBOX_INSET, box.minX + HITBOX_INSET, box.maxX - HITBOX_INSET),
                clamp(point.yCoord - surfacePoint.normalY * HITBOX_INSET, box.minY + HITBOX_INSET, box.maxY - HITBOX_INSET),
                clamp(point.zCoord - surfacePoint.normalZ * HITBOX_INSET, box.minZ + HITBOX_INSET, box.maxZ - HITBOX_INSET)
        );
    }

    private AxisAlignedBB interpolatedHitbox(EntityPlayer player, float partialTicks) {
        double x = OptimalZoneMod.interpolate(player.lastTickPosX, player.posX, partialTicks);
        double y = OptimalZoneMod.interpolate(player.lastTickPosY, player.posY, partialTicks);
        double z = OptimalZoneMod.interpolate(player.lastTickPosZ, player.posZ, partialTicks);
        double halfWidth = player.width / 2.0D;
        return new AxisAlignedBB(
                x - halfWidth,
                y,
                z - halfWidth,
                x + halfWidth,
                y + player.height,
                z + halfWidth
        );
    }

    private Vec3 smoothCenter(EntityPlayer player, Vec3 target) {
        int entityId = player.getEntityId();
        if (smoothedCenter == null || smoothedTargetId != entityId) {
            smoothedTargetId = entityId;
            smoothedCenter = target;
            return smoothedCenter;
        }

        smoothedCenter = new Vec3(
                smoothedCenter.xCoord + (target.xCoord - smoothedCenter.xCoord) * SMOOTHING,
                smoothedCenter.yCoord + (target.yCoord - smoothedCenter.yCoord) * SMOOTHING,
                smoothedCenter.zCoord + (target.zCoord - smoothedCenter.zCoord) * SMOOTHING
        );
        return smoothedCenter;
    }

    private void drawMarker(EntityPlayer player, Vec3 center, double renderX, double renderY, double renderZ, float partialTicks) {
        double entityX = OptimalZoneMod.interpolate(player.lastTickPosX, player.posX, partialTicks);
        double entityY = OptimalZoneMod.interpolate(player.lastTickPosY, player.posY, partialTicks);
        double entityZ = OptimalZoneMod.interpolate(player.lastTickPosZ, player.posZ, partialTicks);
        double localX = center.xCoord - entityX;
        double localY = center.yCoord - entityY;
        double localZ = center.zCoord - entityZ;
        Minecraft mc = Minecraft.getMinecraft();

        GlStateManager.pushMatrix();
        GlStateManager.translate(renderX + localX, renderY + localY, renderZ + localZ);
        GlStateManager.rotate(-mc.getRenderManager().playerViewY, 0.0F, 1.0F, 0.0F);
        GlStateManager.rotate(mc.getRenderManager().playerViewX * (mc.gameSettings.thirdPersonView == 2 ? -1 : 1), 1.0F, 0.0F, 0.0F);
        boolean crosshairInsideMarker = isCrosshairInsideMarker(center, partialTicks);

        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GlStateManager.disableDepth();
        GlStateManager.disableTexture2D();
        GlStateManager.disableLighting();
        GlStateManager.disableCull();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.disableDepth();
        GlStateManager.depthMask(false);

        if (crosshairInsideMarker) {
            mod.recordOptimalZoneAim(player);
            GlStateManager.color(0.0F, 1.0F, 0.2F, 0.58F);
            drawBillboardQuad();
        }

        GlStateManager.color(0.0F, 1.0F, 0.2F, 0.95F);
        GL11.glLineWidth(2.0F);
        drawBillboardOutline();
        GL11.glLineWidth(1.0F);

        GlStateManager.depthMask(true);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GlStateManager.enableDepth();
        GlStateManager.disableBlend();
        GlStateManager.enableCull();
        GlStateManager.enableLighting();
        GlStateManager.enableTexture2D();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.popMatrix();
    }

    private boolean isCrosshairInsideMarker(Vec3 center, float partialTicks) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null) {
            return false;
        }

        Vec3 eyes = eyePosition(mc.thePlayer, partialTicks);
        Vec3 look = mc.thePlayer.getLook(partialTicks).normalize();
        Vec3 toMarker = new Vec3(
                center.xCoord - eyes.xCoord,
                center.yCoord - eyes.yCoord,
                center.zCoord - eyes.zCoord
        );

        double depth = dot(toMarker, look);
        if (depth <= 0.0D) {
            return false;
        }

        double closestX = eyes.xCoord + look.xCoord * depth;
        double closestY = eyes.yCoord + look.yCoord * depth;
        double closestZ = eyes.zCoord + look.zCoord * depth;
        double dx = center.xCoord - closestX;
        double dy = center.yCoord - closestY;
        double dz = center.zCoord - closestZ;
        double radius = PATCH_SIZE / 2.0D + PATCH_HOVER_PADDING;
        return dx * dx + dy * dy + dz * dz <= radius * radius;
    }

    private void drawBillboardQuad() {
        double half = PATCH_SIZE / 2.0D;
        GL11.glBegin(GL11.GL_TRIANGLE_FAN);
        GL11.glVertex3d(0.0D, 0.0D, 0.0D);
        for (int i = 0; i <= 24; i++) {
            double angle = Math.PI * 2.0D * i / 24.0D;
            GL11.glVertex3d(Math.cos(angle) * half, Math.sin(angle) * half, 0.0D);
        }
        GL11.glEnd();
    }

    private void drawBillboardOutline() {
        double half = PATCH_SIZE / 2.0D;
        GL11.glBegin(GL11.GL_LINE_LOOP);
        for (int i = 0; i < 24; i++) {
            double angle = Math.PI * 2.0D * i / 24.0D;
            GL11.glVertex3d(Math.cos(angle) * half, Math.sin(angle) * half, 0.0D);
        }
        GL11.glEnd();
    }

    private Vec3 eyePosition(EntityPlayer player, float partialTicks) {
        return new Vec3(
                OptimalZoneMod.interpolate(player.lastTickPosX, player.posX, partialTicks),
                OptimalZoneMod.interpolate(player.lastTickPosY, player.posY, partialTicks) + player.getEyeHeight(),
                OptimalZoneMod.interpolate(player.lastTickPosZ, player.posZ, partialTicks)
        );
    }

    private Vec3 playerCenter(EntityPlayer player, float partialTicks) {
        return new Vec3(
                OptimalZoneMod.interpolate(player.lastTickPosX, player.posX, partialTicks),
                OptimalZoneMod.interpolate(player.lastTickPosY, player.posY, partialTicks) + player.height * 0.5D,
                OptimalZoneMod.interpolate(player.lastTickPosZ, player.posZ, partialTicks)
        );
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private double dot(Vec3 a, Vec3 b) {
        return a.xCoord * b.xCoord + a.yCoord * b.yCoord + a.zCoord * b.zCoord;
    }

    private static class SurfacePoint {
        private final Vec3 point;
        private final double normalX;
        private final double normalY;
        private final double normalZ;

        private SurfacePoint(Vec3 point, double normalX, double normalY, double normalZ) {
            this.point = point;
            this.normalX = normalX;
            this.normalY = normalY;
            this.normalZ = normalZ;
        }
    }

}
