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
        Identifier.fromNamespaceAndPath(JJKMod.MOD_ID, "textures/entity/piercing_blood_beam.png");

    private static final RenderType CORE_TYPE = RenderTypes.entityCutoutNoCull(TEXTURE);

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
    public void submit(
        EntityRenderState renderState,
        PoseStack poseStack,
        SubmitNodeCollector submitNodeCollector,
        CameraRenderState cameraRenderState
    ) {
        float t = renderState.ageInTicks;
        float intensity = Math.max(0.35F, 1.0F - (t / 45.0F));

        float pulse = 1.0F + Mth.sin(t * 4.2F) * 0.05F;

        poseStack.pushPose();
        poseStack.scale(pulse, pulse * (0.98F + intensity * 0.04F), pulse);
        poseStack.mulPose(cameraRenderState.orientation);

        int solidAlpha = alphaScale(0xFF, intensity);
        submitNeedle(
            poseStack,
            submitNodeCollector,
            renderState,
            CORE_TYPE,
            0.245F,
            0.92F,
            (solidAlpha << 24) | 0xFFFFFF
        );

        poseStack.popPose();
        super.submit(renderState, poseStack, submitNodeCollector, cameraRenderState);
    }

    private static int alphaScale(int baseAlpha, float factor) {
        return Mth.clamp((int) (baseAlpha * factor), 0, 255);
    }

    private static void submitNeedle(
        PoseStack poseStack,
        SubmitNodeCollector submitNodeCollector,
        EntityRenderState renderState,
        RenderType renderType,
        float halfW,
        float halfH,
        int color
    ) {
        submitNodeCollector.submitCustomGeometry(
            poseStack,
            renderType,
            (pose, vc) -> renderNeedle(renderState, pose, vc, halfW, halfH, color)
        );
    }

    private static void renderNeedle(
        EntityRenderState renderState,
        PoseStack.Pose pose,
        VertexConsumer vc,
        float halfW,
        float halfH,
        int color
    ) {
        vertex(vc, pose, renderState.lightCoords, -halfW, -halfH, 0, 1, color);
        vertex(vc, pose, renderState.lightCoords, halfW, -halfH, 1, 1, color);
        vertex(vc, pose, renderState.lightCoords, halfW, halfH, 1, 0, color);
        vertex(vc, pose, renderState.lightCoords, -halfW, halfH, 0, 0, color);
    }

    private static void vertex(VertexConsumer vc, PoseStack.Pose pose, int light, float x, float y, int u, int v, int color) {
        vc.addVertex(pose, x, y, 0.0F)
            .setColor(color)
            .setUv(u, v)
            .setOverlay(OverlayTexture.NO_OVERLAY)
            .setLight(light)
            .setNormal(pose, 0.0F, 1.0F, 0.0F);
    }
}
