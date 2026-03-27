package com.pop.jjk;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

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

public class RedProjectileRenderer extends EntityRenderer<RedProjectileEntity, EntityRenderState> {

    private static final int FRAME_COUNT = 4;
    private static final int FRAME_TICKS = 2;
    private static final Identifier[] RED_TEXTURES = new Identifier[FRAME_COUNT];
    private static final RenderType[] MAIN_RENDER_TYPES = new RenderType[FRAME_COUNT];
    private static final RenderType[] GLOW_RENDER_TYPES = new RenderType[FRAME_COUNT];

    static {
        for (int frame = 0; frame < FRAME_COUNT; frame++) {
            Identifier texture = Identifier.fromNamespaceAndPath(JJKMod.MOD_ID, "textures/entity/red_orb_" + frame + ".png");
            RED_TEXTURES[frame] = texture;
            MAIN_RENDER_TYPES[frame] = RenderTypes.entityCutoutNoCull(texture);
            GLOW_RENDER_TYPES[frame] = RenderTypes.entityTranslucentEmissive(texture);
        }
    }

    public RedProjectileRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    protected int getBlockLightLevel(RedProjectileEntity entity, BlockPos pos) {
        return 15;
    }

    @Override
    public EntityRenderState createRenderState() {
        return new EntityRenderState();
    }

    @Override
    public void submit(EntityRenderState renderState, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CameraRenderState cameraRenderState) {
        float animationTime = renderState.ageInTicks;
        int baseFrame = getFrameIndex(animationTime, 0);
        int shellFrame = getFrameIndex(animationTime, 1);
        int crossFrame = getFrameIndex(animationTime, 2);
        int coreFrame = getFrameIndex(animationTime, 3);
        float basePulse = 1.42F + (Mth.sin(animationTime * 0.34F) * 0.12F);
        float shellPulse = 1.0F + (Mth.sin(animationTime * 0.72F) * 0.08F);
        float crossPulse = 1.0F + (Mth.cos(animationTime * 0.58F) * 0.09F);
        float corePulse = 1.0F + (Mth.sin(animationTime * 1.08F) * 0.05F);

        poseStack.pushPose();
        poseStack.scale(basePulse, basePulse, basePulse);
        poseStack.mulPose(cameraRenderState.orientation);

        // Front-facing base sprite keeps the pixel-art silhouette readable.
        submitOrbPlane(poseStack, submitNodeCollector, renderState, MAIN_RENDER_TYPES[baseFrame], 1.0F, 0.0F, 0.0F, 0.0F, 0xFFFFFFFF);

        // Crossed emissive planes add fake depth without needing a full 3D model.
        submitOrbPlane(poseStack, submitNodeCollector, renderState, GLOW_RENDER_TYPES[shellFrame], 1.1F * shellPulse, 0.0F, animationTime * 8.0F, animationTime * 4.0F, 0x5CFF4A4A);
        submitOrbPlane(poseStack, submitNodeCollector, renderState, GLOW_RENDER_TYPES[crossFrame], 0.98F * crossPulse, animationTime * 11.5F, 58.0F, -animationTime * 6.0F, 0x42FF8A8A);
        submitOrbPlane(poseStack, submitNodeCollector, renderState, GLOW_RENDER_TYPES[(crossFrame + 1) % FRAME_COUNT], 0.94F * crossPulse, -animationTime * 9.5F, -58.0F, animationTime * 7.0F, 0x36FFD6D6);
        submitOrbPlane(poseStack, submitNodeCollector, renderState, GLOW_RENDER_TYPES[coreFrame], 0.54F * corePulse, 18.0F, animationTime * 16.0F, -animationTime * 12.0F, 0x2EFFFFFF);

        poseStack.popPose();
        super.submit(renderState, poseStack, submitNodeCollector, cameraRenderState);
    }

    private static int getFrameIndex(float animationTime, int offset) {
        return ((int) Math.floor(animationTime / FRAME_TICKS) + offset) % FRAME_COUNT;
    }

    private static void submitOrbPlane(
        PoseStack poseStack,
        SubmitNodeCollector submitNodeCollector,
        EntityRenderState renderState,
        RenderType renderType,
        float scale,
        float tiltXDegrees,
        float spinYDegrees,
        float spinZDegrees,
        int color
    ) {
        poseStack.pushPose();
        poseStack.mulPose(Axis.XP.rotationDegrees(tiltXDegrees));
        poseStack.mulPose(Axis.YP.rotationDegrees(spinYDegrees));
        poseStack.mulPose(Axis.ZP.rotationDegrees(spinZDegrees));
        poseStack.scale(scale, scale, scale);
        submitNodeCollector.submitCustomGeometry(
            poseStack,
            renderType,
            (pose, vertexConsumer) -> renderQuad(renderState, pose, vertexConsumer, color)
        );
        poseStack.popPose();
    }

    private static void renderQuad(EntityRenderState renderState, PoseStack.Pose pose, VertexConsumer vertexConsumer, int color) {
        vertex(vertexConsumer, pose, renderState.lightCoords, 0.0F, 0, 0, 1, color);
        vertex(vertexConsumer, pose, renderState.lightCoords, 1.0F, 0, 1, 1, color);
        vertex(vertexConsumer, pose, renderState.lightCoords, 1.0F, 1, 1, 0, color);
        vertex(vertexConsumer, pose, renderState.lightCoords, 0.0F, 1, 0, 0, color);
    }

    private static void vertex(VertexConsumer vertexConsumer, PoseStack.Pose pose, int light, float x, int y, int u, int v, int color) {
        vertexConsumer.addVertex(pose, x - 0.5F, y - 0.25F, 0.0F)
            .setColor(color)
            .setUv(u, v)
            .setOverlay(OverlayTexture.NO_OVERLAY)
            .setLight(light)
            .setNormal(pose, 0.0F, 1.0F, 0.0F);
    }
}
