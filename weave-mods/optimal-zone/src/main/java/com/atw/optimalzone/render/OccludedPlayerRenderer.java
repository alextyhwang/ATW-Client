package com.atw.optimalzone.render;

import com.atw.optimalzone.OptimalZoneMod;
import com.atw.optimalzone.OverlayPlayerClassifier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.ModelBase;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.weavemc.loader.api.event.RenderLivingEvent;
import org.lwjgl.opengl.EXTFramebufferObject;
import org.lwjgl.opengl.EXTPackedDepthStencil;
import org.lwjgl.opengl.GL11;

public class OccludedPlayerRenderer {
    private static final float HIDDEN_ALPHA = 0.30F;
    private static final float OUTLINE_ALPHA = 0.42F;
    private static final float INVISIBLE_ALPHA = 0.52F;
    private static final float INVISIBLE_OUTLINE_ALPHA = 0.88F;
    private static final float OUTLINE_SCALE = 1.045F;
    private static final float MODEL_SCALE = 0.0625F;
    private static final float NORMAL_SCALE = 1.0F;
    private static final int FILL_STENCIL_REF = 1;
    private static final int OUTLINE_STENCIL_REF = 2;

    private final OptimalZoneMod mod;
    private boolean renderingOverlayPass;

    public OccludedPlayerRenderer(OptimalZoneMod mod) {
        this.mod = mod;
    }

    public void render(RenderLivingEvent.Pre event) {
        if (renderingOverlayPass) {
            return;
        }

        EntityLivingBase entity = event.getEntity();
        if (!(entity instanceof EntityPlayer) || OptimalZoneMod.isSelf(entity) || entity.isDead) {
            return;
        }

        EntityPlayer player = (EntityPlayer) entity;
        if (!player.isEntityAlive()) {
            return;
        }

        boolean invisible = player.isInvisible();
        if (invisible ? !mod.shouldRenderInvisOverlay() : !mod.shouldRenderChams()) {
            return;
        }

        if (!OverlayPlayerClassifier.shouldTreatAsRealPlayer(player)) {
            return;
        }

        drawPlayerSilhouette(event, player, invisible);
    }

    private void drawPlayerSilhouette(RenderLivingEvent.Pre event, EntityPlayer player, boolean invisible) {
        renderingOverlayPass = true;
        mod.setRenderingOccludedPlayerOverlay(true);
        RenderStateSnapshot previousState = RenderStateSnapshot.capture();
        try {
            OverlayColorResolver.Color color = invisible
                    ? OverlayColorResolver.invisiblePlayer()
                    : OverlayColorResolver.colorFor(player);
            int maskDepthFunc = invisible ? GL11.GL_ALWAYS : GL11.GL_GREATER;
            float fillAlpha = invisible ? INVISIBLE_ALPHA : HIDDEN_ALPHA;
            float outlineAlpha = invisible ? INVISIBLE_OUTLINE_ALPHA : OUTLINE_ALPHA;
            if (prepareStencilBuffer()) {
                drawFlatSilhouette(event, player, color, maskDepthFunc, fillAlpha, outlineAlpha);
            } else {
                drawDirectSilhouette(event, player, color, maskDepthFunc, fillAlpha, outlineAlpha);
            }
        } finally {
            previousState.restore();
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            mod.setRenderingOccludedPlayerOverlay(false);
            renderingOverlayPass = false;
        }
    }

    private void drawFlatSilhouette(RenderLivingEvent.Pre event, EntityPlayer player, OverlayColorResolver.Color color,
                                    int maskDepthFunc, float fillAlpha, float outlineAlpha) {
        OverlayColorResolver.Color outlineColor = color.darker();
        GL11.glEnable(GL11.GL_STENCIL_TEST);
        GL11.glClearStencil(0);
        GL11.glClear(GL11.GL_STENCIL_BUFFER_BIT);

        GL11.glStencilMask(0xFF);
        GL11.glStencilFunc(GL11.GL_ALWAYS, OUTLINE_STENCIL_REF, 0xFF);
        GL11.glStencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_REPLACE);
        GlStateManager.colorMask(false, false, false, false);
        GlStateManager.depthFunc(maskDepthFunc);
        setupHiddenModelState(false, color, fillAlpha);
        renderBasePlayerModelPass(event, player, OUTLINE_SCALE);

        GL11.glStencilFunc(GL11.GL_ALWAYS, FILL_STENCIL_REF, 0xFF);
        renderBasePlayerModelPass(event, player, NORMAL_SCALE);

        GlStateManager.colorMask(true, true, true, true);
        GL11.glStencilFunc(GL11.GL_EQUAL, OUTLINE_STENCIL_REF, 0xFF);
        GL11.glStencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_ZERO);
        GlStateManager.depthFunc(GL11.GL_ALWAYS);
        setupHiddenModelState(true, outlineColor, outlineAlpha);
        renderBasePlayerModelPass(event, player, OUTLINE_SCALE);

        GlStateManager.colorMask(true, true, true, true);
        GL11.glStencilFunc(GL11.GL_EQUAL, FILL_STENCIL_REF, 0xFF);
        GL11.glStencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_ZERO);
        GlStateManager.depthFunc(GL11.GL_ALWAYS);
        setupHiddenModelState(true, color, fillAlpha);
        renderBasePlayerModelPass(event, player, NORMAL_SCALE);

        GL11.glStencilMask(0xFF);
        GL11.glClear(GL11.GL_STENCIL_BUFFER_BIT);
        GL11.glDisable(GL11.GL_STENCIL_TEST);
    }

    private void drawDirectSilhouette(RenderLivingEvent.Pre event, EntityPlayer player, OverlayColorResolver.Color color,
                                      int depthFunc, float fillAlpha, float outlineAlpha) {
        GlStateManager.depthFunc(depthFunc);
        setupHiddenModelState(true, color.darker(), outlineAlpha);
        renderBasePlayerModelPass(event, player, OUTLINE_SCALE);
        setupHiddenModelState(true, color, fillAlpha);
        renderBasePlayerModelPass(event, player, NORMAL_SCALE);
    }

    private void setupHiddenModelState(boolean blendColor, OverlayColorResolver.Color color, float alpha) {
        GlStateManager.depthMask(false);
        GlStateManager.disableTexture2D();
        GlStateManager.disableLighting();
        GlStateManager.enableAlpha();
        GlStateManager.disableCull();

        if (blendColor) {
            GlStateManager.enableBlend();
            GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
            GlStateManager.color(color.red, color.green, color.blue, alpha);
        } else {
            GlStateManager.disableBlend();
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        }
    }

    private void renderBasePlayerModelPass(RenderLivingEvent.Pre event, EntityPlayer player, float renderScale) {
        GlStateManager.pushMatrix();
        try {
            renderBasePlayerModel(event, player, renderScale);
        } finally {
            GlStateManager.popMatrix();
        }
    }

    private boolean prepareStencilBuffer() {
        if (GL11.glGetInteger(GL11.GL_STENCIL_BITS) > 0) {
            return true;
        }

        Framebuffer framebuffer = Minecraft.getMinecraft().getFramebuffer();
        if (framebuffer == null || framebuffer.depthBuffer < 0) {
            return false;
        }

        int packedDepthStencil = 0;
        try {
            packedDepthStencil = EXTFramebufferObject.glGenRenderbuffersEXT();
            EXTFramebufferObject.glBindRenderbufferEXT(EXTFramebufferObject.GL_RENDERBUFFER_EXT, packedDepthStencil);
            EXTFramebufferObject.glRenderbufferStorageEXT(
                    EXTFramebufferObject.GL_RENDERBUFFER_EXT,
                    EXTPackedDepthStencil.GL_DEPTH_STENCIL_EXT,
                    framebuffer.framebufferTextureWidth,
                    framebuffer.framebufferTextureHeight
            );

            EXTFramebufferObject.glBindFramebufferEXT(EXTFramebufferObject.GL_FRAMEBUFFER_EXT, framebuffer.framebufferObject);
            EXTFramebufferObject.glFramebufferRenderbufferEXT(
                    EXTFramebufferObject.GL_FRAMEBUFFER_EXT,
                    EXTFramebufferObject.GL_DEPTH_ATTACHMENT_EXT,
                    EXTFramebufferObject.GL_RENDERBUFFER_EXT,
                    packedDepthStencil
            );
            EXTFramebufferObject.glFramebufferRenderbufferEXT(
                    EXTFramebufferObject.GL_FRAMEBUFFER_EXT,
                    EXTFramebufferObject.GL_STENCIL_ATTACHMENT_EXT,
                    EXTFramebufferObject.GL_RENDERBUFFER_EXT,
                    packedDepthStencil
            );

            if (EXTFramebufferObject.glCheckFramebufferStatusEXT(EXTFramebufferObject.GL_FRAMEBUFFER_EXT) != EXTFramebufferObject.GL_FRAMEBUFFER_COMPLETE_EXT) {
                restoreDepthBuffer(framebuffer);
                EXTFramebufferObject.glDeleteRenderbuffersEXT(packedDepthStencil);
                return false;
            }

            if (framebuffer.depthBuffer != packedDepthStencil) {
                EXTFramebufferObject.glDeleteRenderbuffersEXT(framebuffer.depthBuffer);
                framebuffer.depthBuffer = packedDepthStencil;
            }

            return GL11.glGetInteger(GL11.GL_STENCIL_BITS) > 0;
        } catch (Throwable ignored) {
            if (packedDepthStencil > 0) {
                EXTFramebufferObject.glDeleteRenderbuffersEXT(packedDepthStencil);
            }
            restoreDepthBuffer(framebuffer);
            return false;
        }
    }

    private void restoreDepthBuffer(Framebuffer framebuffer) {
        EXTFramebufferObject.glBindFramebufferEXT(EXTFramebufferObject.GL_FRAMEBUFFER_EXT, framebuffer.framebufferObject);
        EXTFramebufferObject.glFramebufferRenderbufferEXT(
                EXTFramebufferObject.GL_FRAMEBUFFER_EXT,
                EXTFramebufferObject.GL_DEPTH_ATTACHMENT_EXT,
                EXTFramebufferObject.GL_RENDERBUFFER_EXT,
                framebuffer.depthBuffer
        );
        EXTFramebufferObject.glFramebufferRenderbufferEXT(
                EXTFramebufferObject.GL_FRAMEBUFFER_EXT,
                EXTFramebufferObject.GL_STENCIL_ATTACHMENT_EXT,
                EXTFramebufferObject.GL_RENDERBUFFER_EXT,
                0
        );
    }

    private void renderBasePlayerModel(RenderLivingEvent.Pre event, EntityPlayer player, float renderScale) {
        float partialTicks = event.getPartialTicks();
        float bodyYaw = event.getRenderer().interpolateRotation(player.prevRenderYawOffset, player.renderYawOffset, partialTicks);
        float headYaw = event.getRenderer().interpolateRotation(player.prevRotationYawHead, player.rotationYawHead, partialTicks);
        float netHeadYaw = headYaw - bodyYaw;
        float headPitch = player.prevRotationPitch + (player.rotationPitch - player.prevRotationPitch) * partialTicks;
        float ageInTicks = event.getRenderer().handleRotationFloat(player, partialTicks);
        float limbSwingAmount = player.prevLimbSwingAmount + (player.limbSwingAmount - player.prevLimbSwingAmount) * partialTicks;
        float limbSwing = player.limbSwing - player.limbSwingAmount * (1.0F - partialTicks);

        if (player.isChild()) {
            limbSwing *= 3.0F;
        }

        if (limbSwingAmount > 1.0F) {
            limbSwingAmount = 1.0F;
        }

        ModelBase model = event.getRenderer().getMainModel();
        model.swingProgress = event.getRenderer().getSwingProgress(player, partialTicks);
        model.isRiding = player.isRiding();
        model.isChild = player.isChild();

        event.getRenderer().renderLivingAt(player, event.getX(), event.getY(), event.getZ());
        event.getRenderer().rotateCorpse(player, ageInTicks, bodyYaw, partialTicks);
        GlStateManager.enableRescaleNormal();
        GlStateManager.scale(-1.0F, -1.0F, 1.0F);
        event.getRenderer().preRenderCallback(player, partialTicks);
        GlStateManager.translate(0.0F, -1.5078125F, 0.0F);
        GlStateManager.scale(renderScale, renderScale, renderScale);

        model.setLivingAnimations(player, limbSwing, limbSwingAmount, partialTicks);
        model.setRotationAngles(limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch, MODEL_SCALE, player);
        model.render(player, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch, MODEL_SCALE);
        GlStateManager.disableRescaleNormal();
    }

    private static class RenderStateSnapshot {
        private final boolean texture2D;
        private final boolean lighting;
        private final boolean alpha;
        private final boolean cull;
        private final boolean blend;
        private final boolean stencil;
        private final boolean depthMask;
        private final int depthFunc;

        private RenderStateSnapshot(boolean texture2D, boolean lighting, boolean alpha, boolean cull, boolean blend, boolean stencil, boolean depthMask, int depthFunc) {
            this.texture2D = texture2D;
            this.lighting = lighting;
            this.alpha = alpha;
            this.cull = cull;
            this.blend = blend;
            this.stencil = stencil;
            this.depthMask = depthMask;
            this.depthFunc = depthFunc;
        }

        private static RenderStateSnapshot capture() {
            return new RenderStateSnapshot(
                    GL11.glIsEnabled(GL11.GL_TEXTURE_2D),
                    GL11.glIsEnabled(GL11.GL_LIGHTING),
                    GL11.glIsEnabled(GL11.GL_ALPHA_TEST),
                    GL11.glIsEnabled(GL11.GL_CULL_FACE),
                    GL11.glIsEnabled(GL11.GL_BLEND),
                    GL11.glIsEnabled(GL11.GL_STENCIL_TEST),
                    GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK),
                    GL11.glGetInteger(GL11.GL_DEPTH_FUNC)
            );
        }

        private void restore() {
            restoreTexture2D(texture2D);
            restoreLighting(lighting);
            restoreAlpha(alpha);
            restoreCull(cull);
            restoreBlend(blend);
            restoreStencil(stencil);
            GlStateManager.colorMask(true, true, true, true);
            GlStateManager.depthMask(depthMask);
            GlStateManager.depthFunc(depthFunc);
            GL11.glStencilMask(0xFF);
            GL11.glStencilFunc(GL11.GL_ALWAYS, 0, 0xFF);
            GL11.glStencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_KEEP);
        }

        private static void restoreTexture2D(boolean enabled) {
            if (enabled) {
                GlStateManager.enableTexture2D();
            } else {
                GlStateManager.disableTexture2D();
            }
        }

        private static void restoreLighting(boolean enabled) {
            if (enabled) {
                GlStateManager.enableLighting();
            } else {
                GlStateManager.disableLighting();
            }
        }

        private static void restoreAlpha(boolean enabled) {
            if (enabled) {
                GlStateManager.enableAlpha();
            } else {
                GlStateManager.disableAlpha();
            }
        }

        private static void restoreCull(boolean enabled) {
            if (enabled) {
                GlStateManager.enableCull();
            } else {
                GlStateManager.disableCull();
            }
        }

        private static void restoreBlend(boolean enabled) {
            if (enabled) {
                GlStateManager.enableBlend();
            } else {
                GlStateManager.disableBlend();
            }
        }

        private static void restoreStencil(boolean enabled) {
            if (enabled) {
                GL11.glEnable(GL11.GL_STENCIL_TEST);
            } else {
                GL11.glDisable(GL11.GL_STENCIL_TEST);
            }
        }
    }
}
