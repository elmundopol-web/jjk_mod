package com.pop.jjk;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

public class PiercingBloodRenderer extends EntityRenderer<PiercingBloodProjectileEntity, EntityRenderState> {

    private static final Identifier TEXTURE =
        Identifier.fromNamespaceAndPath(JJKMod.MOD_ID, "textures/entity/red_orb_0.png");

    private static final RenderType CORE_TYPE = RenderTypes.entityCutoutNoCull(TEXTURE);
    private static final RenderType GLOW_TYPE = RenderTypes.entityTranslucentEmissive(TEXTURE);

    public PiercingBloodRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    protected int getBlockLightLevel(PiercingBloodProjectileEntity entity, BlockPos pos) {
        return 15;
    }

    @Override
    public EntityRenderState createRenderState() {
        return new EntityRenderState();
    }

    @Override
    public void submit(EntityRenderState renderState, PoseStack poseStack, SubmitNodeCollector submitNodeCollector,
                       CameraRenderState cameraRenderState) {
        float t     = renderState.ageInTicks;
        // intensity: 1.0 en tick 0 → 0.0 en tick MAX_LIFETIME (30)
        float intensity = Math.max(0.0F, 1.0F - t / 30.0F);
        if (intensity <= 0.01F) return;

        float pulse = 1.0F + Mth.sin(t * 4.2F) * 0.07F;

        poseStack.pushPose();
        poseStack.scale(pulse, pulse, pulse);
        poseStack.mulPose(cameraRenderState.orientation);

        // Escalar la componente alpha de cada capa por intensity
        int a1 = alphaScale(0xFF, intensity);
        int a2 = alphaScale(0xFF, intensity);
        int a3 = alphaScale(0xA8, intensity);
        int a4 = alphaScale(0x65, intensity);
        int a5 = alphaScale(0x38, intensity);

        // Layer 1 – white-hot core
        submitNeedle(poseStack, submitNodeCollector, renderState, CORE_TYPE,  0.038F, 0.66F, (a1 << 24) | 0xFFFFFF);
        // Layer 2 – red-hot inner needle
        submitNeedle(poseStack, submitNodeCollector, renderState, GLOW_TYPE,  0.072F, 0.58F, (a2 << 24) | 0xFF1010);
        // Layer 3 – crimson mid-glow
        submitNeedle(poseStack, submitNodeCollector, renderState, GLOW_TYPE,  0.140F, 0.43F, (a3 << 24) | 0xCC0000);
        // Layer 4 – dark outer halo
        submitNeedle(poseStack, submitNodeCollector, renderState, GLOW_TYPE,  0.240F, 0.28F, (a4 << 24) | 0x880000);
        // Layer 5 – wide dark aura blob
        submitNeedle(poseStack, submitNodeCollector, renderState, GLOW_TYPE,  0.380F, 0.16F, (a5 << 24) | 0x440000);

        poseStack.popPose();
        super.submit(renderState, poseStack, submitNodeCollector, cameraRenderState);
    }

    /** Escala un valor de alpha [0-255] por un factor [0.0-1.0]. */
    private static int alphaScale(int baseAlpha, float factor) {
        return Mth.clamp((int)(baseAlpha * factor), 0, 255);
    }

    /** Submits a billboard-aligned needle (halfW wide, halfH tall) with the given color. */
    private static void submitNeedle(PoseStack poseStack, SubmitNodeCollector submitNodeCollector,
                                     EntityRenderState renderState, RenderType renderType,
                                     float halfW, float halfH, int color) {
        submitNodeCollector.submitCustomGeometry(
            poseStack, renderType,
            (pose, vc) -> renderNeedle(renderState, pose, vc, halfW, halfH, color)
        );
    }

    private static void renderNeedle(EntityRenderState renderState, PoseStack.Pose pose,
                                     VertexConsumer vc, float halfW, float halfH, int color) {
        vertex(vc, pose, renderState.lightCoords, -halfW, -halfH, 0, 1, color);
        vertex(vc, pose, renderState.lightCoords,  halfW, -halfH, 1, 1, color);
        vertex(vc, pose, renderState.lightCoords,  halfW,  halfH, 1, 0, color);
        vertex(vc, pose, renderState.lightCoords, -halfW,  halfH, 0, 0, color);
    }

    private static void vertex(VertexConsumer vc, PoseStack.Pose pose, int light,
                                float x, float y, int u, int v, int color) {
        vc.addVertex(pose, x, y, 0.0F)
            .setColor(color)
            .setUv(u, v)
            .setOverlay(OverlayTexture.NO_OVERLAY)
            .setLight(light)
            .setNormal(pose, 0.0F, 1.0F, 0.0F);
    }
}
