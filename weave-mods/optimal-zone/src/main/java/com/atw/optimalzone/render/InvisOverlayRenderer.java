package com.atw.optimalzone.render;

import com.atw.optimalzone.OptimalZoneMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.EntityFX;
import net.minecraft.client.particle.EntityFootStepFX;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.AxisAlignedBB;
import net.weavemc.loader.api.event.RenderWorldEvent;
import net.weavemc.loader.api.event.TickEvent;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class InvisOverlayRenderer {
    private static final double PLAYER_HALF_WIDTH = 0.3D;
    private static final double PLAYER_HEIGHT = 1.8D;
    private static final long HITBOX_LIFETIME_MILLIS = 900L;
    private static final int MAX_TRAIL_HITBOXES = 48;
    private static final float FILL_RED = 0.2F;
    private static final float FILL_GREEN = 0.85F;
    private static final float FILL_BLUE = 1.0F;
    private static final float OUTLINE_RED = 0.02F;
    private static final float OUTLINE_GREEN = 0.08F;
    private static final float OUTLINE_BLUE = 0.1F;
    private static final float LATEST_OUTLINE_RED = 0.82F;
    private static final float LATEST_OUTLINE_GREEN = 0.97F;
    private static final float LATEST_OUTLINE_BLUE = 1.0F;

    private final OptimalZoneMod mod;
    private final List<TrailHitbox> trailHitboxes = new ArrayList<TrailHitbox>();
    private final Map<EntityFX, Boolean> seenFootsteps = new IdentityHashMap<EntityFX, Boolean>();
    private Object particleWorld;

    public InvisOverlayRenderer(OptimalZoneMod mod) {
        this.mod = mod;
    }

    public void onTick(TickEvent.Post event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.theWorld == null) {
            clear();
            return;
        }

        if (particleWorld != mc.theWorld) {
            particleWorld = mc.theWorld;
            clear();
        }

        if (!mod.shouldRenderInvisOverlay()) {
            pruneTrail(System.currentTimeMillis());
            return;
        }

        scanFootstepParticles(mc, System.currentTimeMillis());
    }

    public void render(RenderWorldEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (!mod.shouldRenderInvisOverlay() || mc == null || mc.thePlayer == null || mc.theWorld == null || mc.gameSettings.hideGUI) {
            return;
        }

        long now = System.currentTimeMillis();
        pruneTrail(now);
        if (trailHitboxes.isEmpty()) {
            return;
        }

        GlStateManager.pushMatrix();
        GlStateManager.translate(
                -mc.getRenderManager().viewerPosX,
                -mc.getRenderManager().viewerPosY,
                -mc.getRenderManager().viewerPosZ
        );
        GlStateManager.disableTexture2D();
        GlStateManager.disableLighting();
        GlStateManager.disableDepth();
        GlStateManager.depthMask(false);
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.disableCull();
        GL11.glEnable(GL11.GL_LINE_SMOOTH);

        try {
            drawTrail(now);
        } finally {
            GL11.glLineWidth(1.0F);
            GL11.glDisable(GL11.GL_LINE_SMOOTH);
            GlStateManager.enableCull();
            GlStateManager.disableBlend();
            GlStateManager.depthMask(true);
            GlStateManager.enableDepth();
            GlStateManager.enableLighting();
            GlStateManager.enableTexture2D();
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            GlStateManager.popMatrix();
        }
    }

    private void scanFootstepParticles(Minecraft mc, long now) {
        if (mc.effectRenderer == null || mc.effectRenderer.fxLayers == null) {
            return;
        }

        for (List<EntityFX>[] layerPair : mc.effectRenderer.fxLayers) {
            if (layerPair == null) {
                continue;
            }

            for (List<EntityFX> particles : layerPair) {
                if (particles == null || particles.isEmpty()) {
                    continue;
                }

                for (EntityFX particle : particles) {
                    if (!(particle instanceof EntityFootStepFX) || seenFootsteps.containsKey(particle)) {
                        continue;
                    }

                    seenFootsteps.put(particle, Boolean.TRUE);
                    addTrailHitbox(particle.posX, particle.posY, particle.posZ, now);
                }
            }
        }
    }

    private void addTrailHitbox(double x, double y, double z, long now) {
        AxisAlignedBB hitbox = new AxisAlignedBB(
                x - PLAYER_HALF_WIDTH,
                y,
                z - PLAYER_HALF_WIDTH,
                x + PLAYER_HALF_WIDTH,
                y + PLAYER_HEIGHT,
                z + PLAYER_HALF_WIDTH
        );
        trailHitboxes.add(new TrailHitbox(hitbox, now));
        while (trailHitboxes.size() > MAX_TRAIL_HITBOXES) {
            trailHitboxes.remove(0);
        }
    }

    private void drawTrail(long now) {
        int latestIndex = trailHitboxes.size() - 1;
        for (int index = 0; index < trailHitboxes.size(); index++) {
            TrailHitbox hitbox = trailHitboxes.get(index);
            float age = (float) (now - hitbox.createdAtMillis) / (float) HITBOX_LIFETIME_MILLIS;
            float fade = 1.0F - Math.max(0.0F, Math.min(age, 1.0F));
            if (fade <= 0.0F) {
                continue;
            }

            boolean latest = index == latestIndex;
            float emphasis = latest ? 0.65F + 0.35F * fade : fade * fade;
            drawFilledBox(hitbox.bounds, (latest ? 0.24F : 0.08F) * emphasis);
            drawOutlinedBox(hitbox.bounds, (latest ? 0.98F : 0.48F) * emphasis, latest);
        }
    }

    private void drawFilledBox(AxisAlignedBB box, float alpha) {
        Tessellator tessellator = Tessellator.getInstance();
        WorldRenderer renderer = tessellator.getWorldRenderer();
        renderer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);

        addFace(renderer, box.minX, box.minY, box.minZ, box.maxX, box.minY, box.minZ, box.maxX, box.maxY, box.minZ, box.minX, box.maxY, box.minZ, alpha);
        addFace(renderer, box.maxX, box.minY, box.maxZ, box.minX, box.minY, box.maxZ, box.minX, box.maxY, box.maxZ, box.maxX, box.maxY, box.maxZ, alpha);
        addFace(renderer, box.minX, box.minY, box.maxZ, box.minX, box.minY, box.minZ, box.minX, box.maxY, box.minZ, box.minX, box.maxY, box.maxZ, alpha);
        addFace(renderer, box.maxX, box.minY, box.minZ, box.maxX, box.minY, box.maxZ, box.maxX, box.maxY, box.maxZ, box.maxX, box.maxY, box.minZ, alpha);
        addFace(renderer, box.minX, box.maxY, box.minZ, box.maxX, box.maxY, box.minZ, box.maxX, box.maxY, box.maxZ, box.minX, box.maxY, box.maxZ, alpha);
        addFace(renderer, box.minX, box.minY, box.maxZ, box.maxX, box.minY, box.maxZ, box.maxX, box.minY, box.minZ, box.minX, box.minY, box.minZ, alpha);

        tessellator.draw();
    }

    private void addFace(WorldRenderer renderer, double x1, double y1, double z1, double x2, double y2, double z2, double x3, double y3, double z3, double x4, double y4, double z4, float alpha) {
        renderer.pos(x1, y1, z1).color(FILL_RED, FILL_GREEN, FILL_BLUE, alpha).endVertex();
        renderer.pos(x2, y2, z2).color(FILL_RED, FILL_GREEN, FILL_BLUE, alpha).endVertex();
        renderer.pos(x3, y3, z3).color(FILL_RED, FILL_GREEN, FILL_BLUE, alpha).endVertex();
        renderer.pos(x4, y4, z4).color(FILL_RED, FILL_GREEN, FILL_BLUE, alpha).endVertex();
    }

    private void drawOutlinedBox(AxisAlignedBB box, float alpha, boolean latest) {
        GL11.glLineWidth(latest ? 3.6F : 1.8F);
        Tessellator tessellator = Tessellator.getInstance();
        WorldRenderer renderer = tessellator.getWorldRenderer();
        renderer.begin(GL11.GL_LINES, DefaultVertexFormats.POSITION_COLOR);

        addLine(renderer, box.minX, box.minY, box.minZ, box.maxX, box.minY, box.minZ, alpha, latest);
        addLine(renderer, box.maxX, box.minY, box.minZ, box.maxX, box.minY, box.maxZ, alpha, latest);
        addLine(renderer, box.maxX, box.minY, box.maxZ, box.minX, box.minY, box.maxZ, alpha, latest);
        addLine(renderer, box.minX, box.minY, box.maxZ, box.minX, box.minY, box.minZ, alpha, latest);
        addLine(renderer, box.minX, box.maxY, box.minZ, box.maxX, box.maxY, box.minZ, alpha, latest);
        addLine(renderer, box.maxX, box.maxY, box.minZ, box.maxX, box.maxY, box.maxZ, alpha, latest);
        addLine(renderer, box.maxX, box.maxY, box.maxZ, box.minX, box.maxY, box.maxZ, alpha, latest);
        addLine(renderer, box.minX, box.maxY, box.maxZ, box.minX, box.maxY, box.minZ, alpha, latest);
        addLine(renderer, box.minX, box.minY, box.minZ, box.minX, box.maxY, box.minZ, alpha, latest);
        addLine(renderer, box.maxX, box.minY, box.minZ, box.maxX, box.maxY, box.minZ, alpha, latest);
        addLine(renderer, box.maxX, box.minY, box.maxZ, box.maxX, box.maxY, box.maxZ, alpha, latest);
        addLine(renderer, box.minX, box.minY, box.maxZ, box.minX, box.maxY, box.maxZ, alpha, latest);

        tessellator.draw();
    }

    private void addLine(WorldRenderer renderer, double x1, double y1, double z1, double x2, double y2, double z2,
                         float alpha, boolean latest) {
        float red = latest ? LATEST_OUTLINE_RED : OUTLINE_RED;
        float green = latest ? LATEST_OUTLINE_GREEN : OUTLINE_GREEN;
        float blue = latest ? LATEST_OUTLINE_BLUE : OUTLINE_BLUE;
        renderer.pos(x1, y1, z1).color(red, green, blue, alpha).endVertex();
        renderer.pos(x2, y2, z2).color(red, green, blue, alpha).endVertex();
    }

    private void pruneTrail(long now) {
        Iterator<TrailHitbox> iterator = trailHitboxes.iterator();
        while (iterator.hasNext()) {
            if (now - iterator.next().createdAtMillis > HITBOX_LIFETIME_MILLIS) {
                iterator.remove();
            }
        }
    }

    private void clear() {
        trailHitboxes.clear();
        seenFootsteps.clear();
        particleWorld = null;
    }

    private static class TrailHitbox {
        private final AxisAlignedBB bounds;
        private final long createdAtMillis;

        private TrailHitbox(AxisAlignedBB bounds, long createdAtMillis) {
            this.bounds = bounds;
            this.createdAtMillis = createdAtMillis;
        }
    }
}
