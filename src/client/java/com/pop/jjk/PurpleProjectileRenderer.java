package com.pop.jjk;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

public class PurpleProjectileRenderer extends EntityRenderer<PurpleProjectileEntity, PurpleProjectileRenderState> {

    private static final int PURPLE_FRAME_COUNT = 8;
    private static final int PURPLE_FRAME_TICKS = 2;
    private static final Identifier BLUE_TEXTURE = Identifier.fromNamespaceAndPath(JJKMod.MOD_ID, "textures/entity/blue_orb_0.png");
    private static final Identifier RED_TEXTURE = Identifier.fromNamespaceAndPath(JJKMod.MOD_ID, "textures/entity/red_orb_0.png");

    private static final RenderType BLUE_MAIN = RenderTypes.entityCutoutNoCull(BLUE_TEXTURE);
    private static final RenderType BLUE_GLOW = RenderTypes.entityTranslucentEmissive(BLUE_TEXTURE);
    private static final RenderType RED_MAIN = RenderTypes.entityCutoutNoCull(RED_TEXTURE);
    private static final RenderType RED_GLOW = RenderTypes.entityTranslucentEmissive(RED_TEXTURE);
    private static final Identifier[] PURPLE_TEXTURES = new Identifier[PURPLE_FRAME_COUNT];
    private static final RenderType[] PURPLE_MAIN_TYPES = new RenderType[PURPLE_FRAME_COUNT];
    private static final RenderType[] PURPLE_GLOW_TYPES = new RenderType[PURPLE_FRAME_COUNT];

    static {
        for (int frame = 0; frame < PURPLE_FRAME_COUNT; frame++) {
            Identifier texture = Identifier.fromNamespaceAndPath(JJKMod.MOD_ID, "textures/entity/purple_frame_" + frame + ".png");
            PURPLE_TEXTURES[frame] = texture;
            PURPLE_MAIN_TYPES[frame] = RenderTypes.entityCutoutNoCull(texture);
            PURPLE_GLOW_TYPES[frame] = RenderTypes.entityTranslucentEmissive(texture);
        }
    }

    public PurpleProjectileRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    protected int getBlockLightLevel(PurpleProjectileEntity entity, BlockPos pos) {
        return 15;
    }

    @Override
    public PurpleProjectileRenderState createRenderState() {
        return new PurpleProjectileRenderState();
    }

    @Override
    public void extractRenderState(PurpleProjectileEntity entity, PurpleProjectileRenderState renderState, float partialTick) {
        super.extractRenderState(entity, renderState, partialTick);
        renderState.fusionProgress = entity.getFusionProgress();
        renderState.launched = entity.isLaunched();
    }

    @Override
    public void submit(PurpleProjectileRenderState renderState, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CameraRenderState cameraRenderState) {
        if (renderState.launched) {
            submitLaunched(renderState, poseStack, submitNodeCollector, cameraRenderState);
        } else {
            submitFusion(renderState, poseStack, submitNodeCollector, cameraRenderState);
        }

        super.submit(renderState, poseStack, submitNodeCollector, cameraRenderState);
    }

    private void submitFusion(PurpleProjectileRenderState renderState, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CameraRenderState cameraRenderState) {
        float animationTime = renderState.ageInTicks;
        float progress = Mth.clamp(renderState.fusionProgress, 0.0F, 1.0F);
        float eased = progress * progress * (3.0F - (2.0F * progress));
        int fusionFrame = getPurpleFrame(animationTime, 0);
        int haloFrame = getPurpleFrame(animationTime, 2);
        float separation = 0.92F - (eased * 0.72F);
        float blueScale = 0.86F + (Mth.sin(animationTime * 0.34F) * 0.05F) - (progress * 0.12F);
        float redScale = 0.72F + (Mth.cos(animationTime * 0.38F) * 0.04F) - (progress * 0.08F);
        float coreScale = 0.18F + (eased * 1.08F) + (Mth.sin(animationTime * 0.52F) * 0.04F);
        float haloScale = 0.54F + (eased * 0.68F) + (Mth.cos(animationTime * 0.66F) * 0.05F);
        float bridgeScaleX = 0.28F + (eased * 0.78F);
        float bridgeScaleY = 0.12F + (eased * 0.16F);

        poseStack.pushPose();
        poseStack.mulPose(cameraRenderState.orientation);

        submitTranslatedOrb(poseStack, submitNodeCollector, renderState, BLUE_MAIN, BLUE_GLOW, -separation, -0.02F, blueScale, animationTime * 7.5F, 0xFFFFFFFF, 0x66BFF8FF);
        submitTranslatedOrb(poseStack, submitNodeCollector, renderState, RED_MAIN, RED_GLOW, separation, -0.02F, redScale, -animationTime * 8.2F, 0xFFFFFFFF, 0x6CFF705A);

        poseStack.pushPose();
        poseStack.scale(bridgeScaleX, bridgeScaleY, 1.0F);
        submitNodeCollector.submitCustomGeometry(
            poseStack,
            PURPLE_GLOW_TYPES[haloFrame],
            (pose, vertexConsumer) -> renderQuad(renderState, pose, vertexConsumer, 0x78D48CFF)
        );
        poseStack.popPose();

        submitTranslatedOrb(poseStack, submitNodeCollector, renderState, PURPLE_MAIN_TYPES[fusionFrame], PURPLE_GLOW_TYPES[fusionFrame], 0.0F, 0.0F, coreScale, animationTime * 10.0F, 0xF0FFFFFF, 0x78E7AEFF);
        submitTranslatedOrb(poseStack, submitNodeCollector, renderState, PURPLE_GLOW_TYPES[haloFrame], PURPLE_GLOW_TYPES[haloFrame], 0.0F, 0.0F, haloScale, -animationTime * 14.0F, 0x4ACF8EFF, 0x34FFFFFF);

        poseStack.popPose();
    }

    private void submitLaunched(PurpleProjectileRenderState renderState, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CameraRenderState cameraRenderState) {
        float animationTime = renderState.ageInTicks;
        int baseFrame = getPurpleFrame(animationTime, 0);
        int shellFrame = getPurpleFrame(animationTime, 1);
        int ringFrame = getPurpleFrame(animationTime, 3);
        int coreFrame = getPurpleFrame(animationTime, 5);
        float baseScale = 2.36F + (Mth.sin(animationTime * 0.24F) * 0.1F);
        float shellScale = 1.16F + (Mth.sin(animationTime * 0.72F) * 0.08F);
        float ringScale = 1.28F + (Mth.cos(animationTime * 0.54F) * 0.09F);
        float coreScale = 0.66F + (Mth.sin(animationTime * 1.02F) * 0.05F);

        poseStack.pushPose();
        poseStack.mulPose(cameraRenderState.orientation);
        poseStack.scale(baseScale, baseScale, baseScale);

        submitOrbPlane(poseStack, submitNodeCollector, renderState, PURPLE_MAIN_TYPES[baseFrame], 1.0F, 0.0F, animationTime * 5.5F, 0.0F, 0xFFFFFFFF);
        submitOrbPlane(poseStack, submitNodeCollector, renderState, PURPLE_GLOW_TYPES[ringFrame], ringScale, animationTime * 10.0F, animationTime * 7.2F, animationTime * 12.0F, 0x8CE79FFF);
        submitOrbPlane(poseStack, submitNodeCollector, renderState, PURPLE_GLOW_TYPES[shellFrame], shellScale, 64.0F, -animationTime * 12.5F, animationTime * 8.2F, 0x78FF8A8A);
        submitOrbPlane(poseStack, submitNodeCollector, renderState, PURPLE_GLOW_TYPES[(shellFrame + 2) % PURPLE_FRAME_COUNT], shellScale * 0.96F, -64.0F, animationTime * 11.2F, -animationTime * 8.8F, 0x6C7AC5FF);
        submitOrbPlane(poseStack, submitNodeCollector, renderState, PURPLE_GLOW_TYPES[coreFrame], coreScale, 24.0F, animationTime * 18.0F, -animationTime * 15.0F, 0x72FFFFFF);

        poseStack.popPose();
    }

    private static int getPurpleFrame(float animationTime, int offset) {
        return ((int) Math.floor(animationTime / PURPLE_FRAME_TICKS) + offset) % PURPLE_FRAME_COUNT;
    }

    private void submitTranslatedOrb(
        PoseStack poseStack,
        SubmitNodeCollector submitNodeCollector,
        PurpleProjectileRenderState renderState,
        RenderType mainRenderType,
        RenderType glowRenderType,
        float xOffset,
        float yOffset,
        float scale,
        float spinDegrees,
        int mainColor,
        int glowColor
    ) {
        poseStack.pushPose();
        poseStack.translate(xOffset, yOffset, 0.0F);
        submitOrbPlane(poseStack, submitNodeCollector, renderState, mainRenderType, scale, 0.0F, spinDegrees, 0.0F, mainColor);
        submitOrbPlane(poseStack, submitNodeCollector, renderState, glowRenderType, scale * 1.08F, 0.0F, -spinDegrees * 1.25F, spinDegrees * 0.45F, glowColor);
        poseStack.popPose();
    }

    private static void submitOrbPlane(
        PoseStack poseStack,
        SubmitNodeCollector submitNodeCollector,
        PurpleProjectileRenderState renderState,
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

    private static void renderQuad(PurpleProjectileRenderState renderState, PoseStack.Pose pose, VertexConsumer vertexConsumer, int color) {
        vertex(vertexConsumer, pose, renderState.lightCoords, 0.0F, 0, 0, 1, color);
        vertex(vertexConsumer, pose, renderState.lightCoords, 1.0F, 0, 1, 1, color);
        vertex(vertexConsumer, pose, renderState.lightCoords, 1.0F, 1, 1, 0, color);
        vertex(vertexConsumer, pose, renderState.lightCoords, 0.0F, 1, 0, 0, color);
    }

    private static void vertex(VertexConsumer vertexConsumer, PoseStack.Pose pose, int light, float x, int y, int u, int v, int color) {
        vertexConsumer.addVertex(pose, x - 0.5F, y - 0.34F, 0.0F)
            .setColor(color)
            .setUv(u, v)
            .setOverlay(OverlayTexture.NO_OVERLAY)
            .setLight(light)
            .setNormal(pose, 0.0F, 1.0F, 0.0F);
    }
}
