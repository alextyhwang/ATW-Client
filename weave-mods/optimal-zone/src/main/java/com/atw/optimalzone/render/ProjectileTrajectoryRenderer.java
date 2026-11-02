package com.atw.optimalzone.render;

import com.atw.optimalzone.OptimalZoneMod;
import com.atw.optimalzone.OverlayPlayerClassifier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.weavemc.api.event.RenderWorldEvent;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.List;

public class ProjectileTrajectoryRenderer {
    private static final int MAX_STEPS = 160;
    private static final double DRAG = 0.99D;
    private static final double BOW_GRAVITY = 0.05D;
    private static final double PEARL_GRAVITY = 0.03D;
    private static final double PEARL_VELOCITY = 1.5D;
    private static final double MIN_BOW_FORCE = 0.1D;
    private static final double PLAYER_HITBOX_EXPANSION = 0.18D;
    private static final float TRAJECTORY_ALPHA = 0.88F;
    private static final RangeColor NEAR_COLOR = new RangeColor(0.20F, 0.95F, 1.0F);
    private static final RangeColor MID_COLOR = new RangeColor(1.0F, 0.90F, 0.20F);
    private static final RangeColor FAR_COLOR = new RangeColor(1.0F, 0.18F, 0.10F);

    private final OptimalZoneMod mod;
    private final TrajectoryPreparation preparation = new TrajectoryPreparation();
    private boolean computing;
    private boolean preparationInterrupted;

    public ProjectileTrajectoryRenderer(OptimalZoneMod mod) {
        this.mod = mod;
    }

    public void render(RenderWorldEvent event) {
        preparation.renderCalls = TrajectoryPreparation.increment(preparation.renderCalls);
        if (preparation.requested) {
            preparation.requestedRenderCalls = TrajectoryPreparation.increment(preparation.requestedRenderCalls);
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (!mod.shouldRenderProjectiles() || mc == null || mc.thePlayer == null || mc.theWorld == null || mc.gameSettings.hideGUI) {
            return;
        }

        Trajectory trajectory = computeTrajectory(mc, mc.thePlayer, event.getPartialTicks());
        if (preparation.lastComputationActive) {
            preparation.activeRenderCalls = TrajectoryPreparation.increment(preparation.activeRenderCalls);
        }
        if (trajectory == null || trajectory.points.size() < 2) {
            return;
        }

        drawTrajectory(mc, trajectory);
    }

    public void setTrajectoryPreparation(boolean requested) {
        preparation.setRequested(requested);
    }

    public void clearTrajectoryPreparation() {
        preparation.clear();
    }

    public void disconnectTrajectoryPreparation() {
        preparation.disconnect();
    }

    public String[] trajectoryPreparationStatus() {
        return preparation.status();
    }

    private Trajectory computeTrajectory(Minecraft mc, EntityPlayerSP player, float partialTicks) {
        preparation.computeCalls = TrajectoryPreparation.increment(preparation.computeCalls);
        if (computing) {
            preparationInterrupted = true;
            preparation.invalidate("reentrancy");
            return computeTrajectoryBody(mc, player, partialTicks, false);
        }
        computing = true;
        preparationInterrupted = false;
        preparation.lastComputationActive = false;
        preparation.fallback = preparation.requested ? "inactive-item" : "off";
        try {
            return computeTrajectoryBody(mc, player, partialTicks, true);
        } finally {
            preparation.lastComputationActive = preparation.current != null && preparation.current.countedActive;
            if (preparation.current != null) preparation.current.disable();
            preparation.current = null;
            computing = false;
        }
    }

    private Trajectory computeTrajectoryBody(Minecraft mc, EntityPlayerSP player, float partialTicks,
                                              boolean allowPreparation) {
        ItemStack heldItem = player.getHeldItem();
        if (heldItem == null) {
            return null;
        }

        boolean bow = heldItem.getItem() == Items.bow;
        boolean pearl = heldItem.getItem() == Items.ender_pearl;
        if (!bow && !pearl) {
            return null;
        }

        double velocity;
        double gravity;
        if (bow) {
            if (!player.isUsingItem()) {
                return null;
            }
            double force = bowForce(player);
            if (force < MIN_BOW_FORCE) {
                return null;
            }
            velocity = force * 3.0D;
            gravity = BOW_GRAVITY;
        } else {
            velocity = PEARL_VELOCITY;
            gravity = PEARL_GRAVITY;
        }

        double yaw = Math.toRadians(player.rotationYaw);
        double pitch = Math.toRadians(player.rotationPitch);
        double posX = OptimalZoneMod.interpolate(player.lastTickPosX, player.posX, partialTicks) - MathHelper.cos((float) yaw) * 0.16D;
        double posY = OptimalZoneMod.interpolate(player.lastTickPosY, player.posY, partialTicks) + player.getEyeHeight() - 0.1D;
        double posZ = OptimalZoneMod.interpolate(player.lastTickPosZ, player.posZ, partialTicks) - MathHelper.sin((float) yaw) * 0.16D;

        double motionX = -MathHelper.sin((float) yaw) * MathHelper.cos((float) pitch);
        double motionY = -MathHelper.sin((float) pitch);
        double motionZ = MathHelper.cos((float) yaw) * MathHelper.cos((float) pitch);
        double length = Math.sqrt(motionX * motionX + motionY * motionY + motionZ * motionZ);
        motionX = motionX / length * velocity;
        motionY = motionY / length * velocity;
        motionZ = motionZ / length * velocity;

        List<Vec3> points = new ArrayList<Vec3>();
        points.add(new Vec3(posX, posY, posZ));
        boolean hitBlock = false;
        PlayerHit playerHit = null;

        // Start only after original held-item/aim reads. A nested compute uses the original path.
        TrajectoryPreparation.Scratch scratch = null;
        if (allowPreparation && preparation.requested && !preparationInterrupted) {
            if (mc.isCallingFromMinecraftThread()) {
                scratch = new TrajectoryPreparation.Scratch(mc.theWorld);
                preparation.current = scratch;
                preparation.fallback = "none-source-only";
            } else {
                preparation.fallback = "non-client-thread";
            }
        }
        for (int i = 0; i < MAX_STEPS; i++) {
            Vec3 current = new Vec3(posX, posY, posZ);
            Vec3 next = new Vec3(posX + motionX, posY + motionY, posZ + motionZ);
            MovingObjectPosition blockHit = mc.theWorld.rayTraceBlocks(current, next, false, true, false);
            PlayerHit segmentPlayerHit = findPlayerHit(mc, player, current, next, partialTicks, scratch);

            if (blockHit != null && (segmentPlayerHit == null || distanceSquared(current, blockHit.hitVec) <= distanceSquared(current, segmentPlayerHit.hitVec))) {
                points.add(blockHit.hitVec);
                hitBlock = true;
                break;
            }

            if (segmentPlayerHit != null) {
                points.add(segmentPlayerHit.hitVec);
                playerHit = segmentPlayerHit;
                break;
            }

            points.add(next);
            posX = next.xCoord;
            posY = next.yCoord;
            posZ = next.zCoord;
            motionX *= DRAG;
            motionY = motionY * DRAG - gravity;
            motionZ *= DRAG;
        }

        return new Trajectory(points, hitBlock, playerHit);
    }

    private PlayerHit findPlayerHit(Minecraft mc, EntityPlayerSP shooter, Vec3 current, Vec3 next, float partialTicks) {
        return findPlayerHit(mc, shooter, current, next, partialTicks, null);
    }

    private PlayerHit findPlayerHit(Minecraft mc, EntityPlayerSP shooter, Vec3 current, Vec3 next, float partialTicks,
                                    TrajectoryPreparation.Scratch scratch) {
        PlayerHit closest = null;
        double closestDistanceSquared = Double.MAX_VALUE;

        if (scratch != null && !scratch.disabled) {
            if (mc.theWorld != scratch.world) {
                preparation.invalidate("world-change");
            } else if (mc.theWorld.playerEntities.getClass() != ArrayList.class) {
                // Unknown collections may have stateful size/iteration methods. Do not inspect size.
                preparation.invalidate("unknown-player-list");
            } else if (mc.theWorld.playerEntities.size() > TrajectoryPreparation.CAPACITY) {
                preparation.invalidate("scene-cap");
            }
            if (!scratch.disabled && !scratch.countedActive) {
                scratch.countedActive = true;
                preparation.activeComputations = TrajectoryPreparation.increment(preparation.activeComputations);
            }
        }
        int index = 0;
        for (EntityPlayer target : mc.theWorld.playerEntities) {
            int slot = index++;
            if (!shouldCollideWithPlayer(target, shooter)) {
                if (scratch != null && !scratch.disabled && slot < TrajectoryPreparation.CAPACITY) {
                    if (scratch.derived[slot] != null) preparation.invalidations = TrajectoryPreparation.increment(preparation.invalidations);
                    scratch.clearSlot(slot);
                }
                continue;
            }

            AxisAlignedBB hitbox;
            if (scratch != null && !scratch.disabled && slot < TrajectoryPreparation.CAPACITY) {
                hitbox = preparedBoundingBox(target, partialTicks, scratch, slot);
            } else {
                preparation.baselinePreparations = TrajectoryPreparation.increment(preparation.baselinePreparations);
                if (preparation.requested) preparation.fallbackPreparations = TrajectoryPreparation.increment(preparation.fallbackPreparations);
                hitbox = interpolatedBoundingBox(target, partialTicks).expand(
                    PLAYER_HITBOX_EXPANSION,
                    PLAYER_HITBOX_EXPANSION,
                    PLAYER_HITBOX_EXPANSION
                );
            }
            MovingObjectPosition intercept = hitbox.calculateIntercept(current, next);
            Vec3 hitVec = intercept == null ? (hitbox.isVecInside(current) ? current : null) : intercept.hitVec;
            if (hitVec == null) {
                continue;
            }

            double distanceSquared = distanceSquared(current, hitVec);
            if (distanceSquared < closestDistanceSquared) {
                closestDistanceSquared = distanceSquared;
                closest = new PlayerHit(target, hitVec);
            }
        }

        return closest;
    }

    private AxisAlignedBB preparedBoundingBox(EntityPlayer player, float partialTicks,
                                              TrajectoryPreparation.Scratch scratch, int slot) {
        // Keep the original getter, interpolation reads, and offset argument evaluation order EVERY segment.
        AxisAlignedBB currentBox = player.getEntityBoundingBox();
        double interpolatedX = OptimalZoneMod.interpolate(player.lastTickPosX, player.posX, partialTicks);
        double interpolatedY = OptimalZoneMod.interpolate(player.lastTickPosY, player.posY, partialTicks);
        double interpolatedZ = OptimalZoneMod.interpolate(player.lastTickPosZ, player.posZ, partialTicks);
        double offsetX = interpolatedX - player.posX;
        double offsetY = interpolatedY - player.posY;
        double offsetZ = interpolatedZ - player.posZ;

        // A getter may reenter, clear, toggle, or disconnect. Such a call invalidates this scope.
        if (!scratch.disabled && TrajectoryPreparation.Scratch.eligible(currentBox, offsetX, offsetY, offsetZ)) {
            if (scratch.matches(slot, player, currentBox, offsetX, offsetY, offsetZ)) {
                preparation.reuses = TrajectoryPreparation.increment(preparation.reuses);
                return scratch.derived[slot];
            }
            preparation.misses = TrajectoryPreparation.increment(preparation.misses);
            if (scratch.derived[slot] != null) {
                preparation.invalidations = TrajectoryPreparation.increment(preparation.invalidations);
            }
            AxisAlignedBB offset = currentBox.offset(offsetX, offsetY, offsetZ);
            AxisAlignedBB expanded = offset.expand(PLAYER_HITBOX_EXPANSION, PLAYER_HITBOX_EXPANSION, PLAYER_HITBOX_EXPANSION);
            preparation.baselinePreparations = TrajectoryPreparation.increment(preparation.baselinePreparations);
            if (!scratch.disabled && TrajectoryPreparation.Scratch.eligible(offset, 0, 0, 0)
                    && TrajectoryPreparation.Scratch.eligible(expanded, 0, 0, 0)) {
                scratch.store(slot, player, currentBox, offsetX, offsetY, offsetZ, expanded);
            } else {
                preparation.invalidate("derived-shape-or-nonfinite");
            }
            return expanded;
        }
        if (!scratch.disabled) {
            preparation.fallback = "box-class-or-nonfinite";
            if (scratch.derived[slot] != null) preparation.invalidations = TrajectoryPreparation.increment(preparation.invalidations);
            scratch.clearSlot(slot);
        }
        preparation.baselinePreparations = TrajectoryPreparation.increment(preparation.baselinePreparations);
        preparation.fallbackPreparations = TrajectoryPreparation.increment(preparation.fallbackPreparations);
        return currentBox.offset(offsetX, offsetY, offsetZ).expand(
                PLAYER_HITBOX_EXPANSION, PLAYER_HITBOX_EXPANSION, PLAYER_HITBOX_EXPANSION);
    }

    private boolean shouldCollideWithPlayer(EntityPlayer target, EntityPlayerSP shooter) {
        return target != null
                && target != shooter
                && !target.isDead
                && !target.isInvisible()
                && target.isEntityAlive()
                && OverlayPlayerClassifier.shouldTreatAsRealPlayer(target);
    }

    private AxisAlignedBB interpolatedBoundingBox(EntityPlayer player, float partialTicks) {
        AxisAlignedBB currentBox = player.getEntityBoundingBox();
        double interpolatedX = OptimalZoneMod.interpolate(player.lastTickPosX, player.posX, partialTicks);
        double interpolatedY = OptimalZoneMod.interpolate(player.lastTickPosY, player.posY, partialTicks);
        double interpolatedZ = OptimalZoneMod.interpolate(player.lastTickPosZ, player.posZ, partialTicks);
        return currentBox.offset(
                interpolatedX - player.posX,
                interpolatedY - player.posY,
                interpolatedZ - player.posZ
        );
    }

    private double distanceSquared(Vec3 from, Vec3 to) {
        double deltaX = to.xCoord - from.xCoord;
        double deltaY = to.yCoord - from.yCoord;
        double deltaZ = to.zCoord - from.zCoord;
        return deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ;
    }

    private double bowForce(EntityPlayerSP player) {
        float draw = Math.min(player.getItemInUseDuration(), 20) / 20.0F;
        draw = (draw * draw + draw * 2.0F) / 3.0F;
        return Math.min(draw, 1.0F);
    }

    private void drawTrajectory(Minecraft mc, Trajectory trajectory) {
        GlStateManager.pushMatrix();
        GlStateManager.translate(
                -mc.getRenderManager().viewerPosX,
                -mc.getRenderManager().viewerPosY,
                -mc.getRenderManager().viewerPosZ
        );
        GlStateManager.disableTexture2D();
        GlStateManager.disableLighting();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.disableDepth();
        GlStateManager.depthMask(false);
        GL11.glEnable(GL11.GL_LINE_SMOOTH);
        GL11.glLineWidth(2.0F);

        Tessellator tessellator = Tessellator.getInstance();
        WorldRenderer renderer = tessellator.getWorldRenderer();
        double totalDistance = totalDistance(trajectory.points);
        double currentDistance = 0.0D;
        Vec3 previous = null;
        renderer.begin(GL11.GL_LINE_STRIP, DefaultVertexFormats.POSITION_COLOR);
        for (Vec3 point : trajectory.points) {
            if (previous != null) {
                currentDistance += distance(previous, point);
            }
            RangeColor color = trajectoryRangeColor(totalDistance <= 0.0D ? 0.0F : (float) (currentDistance / totalDistance));
            renderer.pos(point.xCoord, point.yCoord, point.zCoord)
                    .color(color.red, color.green, color.blue, TRAJECTORY_ALPHA)
                    .endVertex();
            previous = point;
        }
        tessellator.draw();

        if (trajectory.hitBlock) {
            drawImpactMarker(trajectory.points.get(trajectory.points.size() - 1));
        } else if (trajectory.hitPlayer()) {
            drawPlayerImpactMarker(trajectory.playerHit.hitVec);
        }

        GL11.glLineWidth(1.0F);
        GL11.glDisable(GL11.GL_LINE_SMOOTH);
        GlStateManager.depthMask(true);
        GlStateManager.enableDepth();
        GlStateManager.disableBlend();
        GlStateManager.enableLighting();
        GlStateManager.enableTexture2D();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.popMatrix();
    }

    private void drawImpactMarker(Vec3 point) {
        double size = 0.08D;
        GL11.glBegin(GL11.GL_LINES);
        GL11.glColor4f(1.0F, 0.15F, 0.1F, 0.9F);
        GL11.glVertex3d(point.xCoord - size, point.yCoord, point.zCoord);
        GL11.glVertex3d(point.xCoord + size, point.yCoord, point.zCoord);
        GL11.glVertex3d(point.xCoord, point.yCoord - size, point.zCoord);
        GL11.glVertex3d(point.xCoord, point.yCoord + size, point.zCoord);
        GL11.glVertex3d(point.xCoord, point.yCoord, point.zCoord - size);
        GL11.glVertex3d(point.xCoord, point.yCoord, point.zCoord + size);
        GL11.glEnd();
    }

    private void drawPlayerImpactMarker(Vec3 point) {
        double size = 0.18D;
        GL11.glLineWidth(3.0F);
        GL11.glBegin(GL11.GL_LINES);
        GL11.glColor4f(0.25F, 1.0F, 0.2F, 0.95F);
        GL11.glVertex3d(point.xCoord - size, point.yCoord, point.zCoord);
        GL11.glVertex3d(point.xCoord + size, point.yCoord, point.zCoord);
        GL11.glVertex3d(point.xCoord, point.yCoord - size, point.zCoord);
        GL11.glVertex3d(point.xCoord, point.yCoord + size, point.zCoord);
        GL11.glVertex3d(point.xCoord, point.yCoord, point.zCoord - size);
        GL11.glVertex3d(point.xCoord, point.yCoord, point.zCoord + size);
        GL11.glEnd();
        GL11.glLineWidth(2.0F);
    }

    private double totalDistance(List<Vec3> points) {
        double total = 0.0D;
        for (int i = 1; i < points.size(); i++) {
            total += distance(points.get(i - 1), points.get(i));
        }
        return total;
    }

    private double distance(Vec3 from, Vec3 to) {
        return Math.sqrt(distanceSquared(from, to));
    }

    private RangeColor trajectoryRangeColor(float ratio) {
        float clamped = Math.max(0.0F, Math.min(1.0F, ratio));
        if (clamped < 0.5F) {
            return RangeColor.lerp(NEAR_COLOR, MID_COLOR, clamped * 2.0F);
        }
        return RangeColor.lerp(MID_COLOR, FAR_COLOR, (clamped - 0.5F) * 2.0F);
    }

    private static class Trajectory {
        private final List<Vec3> points;
        private final boolean hitBlock;
        private final PlayerHit playerHit;

        private Trajectory(List<Vec3> points, boolean hitBlock, PlayerHit playerHit) {
            this.points = points;
            this.hitBlock = hitBlock;
            this.playerHit = playerHit;
        }

        private boolean hitPlayer() {
            return playerHit != null;
        }
    }

    private static class PlayerHit {
        private final EntityPlayer player;
        private final Vec3 hitVec;

        private PlayerHit(EntityPlayer player, Vec3 hitVec) {
            this.player = player;
            this.hitVec = hitVec;
        }
    }

    private static class RangeColor {
        private final float red;
        private final float green;
        private final float blue;

        private RangeColor(float red, float green, float blue) {
            this.red = red;
            this.green = green;
            this.blue = blue;
        }

        private static RangeColor lerp(RangeColor from, RangeColor to, float amount) {
            return new RangeColor(
                    from.red + (to.red - from.red) * amount,
                    from.green + (to.green - from.green) * amount,
                    from.blue + (to.blue - from.blue) * amount
            );
        }
    }
}
