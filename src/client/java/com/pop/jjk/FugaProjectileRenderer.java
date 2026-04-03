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

public class FugaProjectileRenderer extends EntityRenderer<FugaProjectileEntity, FugaProjectileRenderState> {

    private static final Identifier RED0 = Identifier.fromNamespaceAndPath(JJKMod.MOD_ID, "textures/entity/red_orb_0.png");
    private static final Identifier RED1 = Identifier.fromNamespaceAndPath(JJKMod.MOD_ID, "textures/entity/red_orb_1.png");
    private static final RenderType MAIN0 = RenderTypes.entityCutoutNoCull(RED0);
    private static final RenderType GLOW0 = RenderTypes.entityTranslucentEmissive(RED0);
    private static final RenderType MAIN1 = RenderTypes.entityCutoutNoCull(RED1);
    private static final RenderType GLOW1 = RenderTypes.entityTranslucentEmissive(RED1);

    public FugaProjectileRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    protected int getBlockLightLevel(FugaProjectileEntity entity, BlockPos pos) {
        return 15;
    }

    @Override
    public FugaProjectileRenderState createRenderState() {
        return new FugaProjectileRenderState();
    }

    @Override
    public void extractRenderState(FugaProjectileEntity entity, FugaProjectileRenderState renderState, float partialTick) {
        super.extractRenderState(entity, renderState, partialTick);
        double vx = entity.getDeltaMovement().x;
        double vy = entity.getDeltaMovement().y;
        double vz = entity.getDeltaMovement().z;
        float speed = (float) Math.sqrt((vx * vx) + (vy * vy) + (vz * vz));
        renderState.speed = speed;
        if (speed > 1.0E-3F) {
            float inv = 1.0F / speed;
            renderState.vx = (float) (vx * inv);
            renderState.vy = (float) (vy * inv);
            renderState.vz = (float) (vz * inv);
        } else {
            renderState.vx = 0.0F;
            renderState.vy = 0.0F;
            renderState.vz = 1.0F;
        }
        renderState.launched = entity.isLaunched();
        renderState.chargePower = Mth.clamp(entity.getChargePower(), 0.0F, 1.0F);
    }

    @Override
    public void submit(FugaProjectileRenderState renderState, PoseStack poseStack, SubmitNodeCollector submitNodeCollector,
        CameraRenderState cameraRenderState) {
        if (renderState.launched) {
            submitBeam(renderState, poseStack, submitNodeCollector);
        } else {
            submitChargeOrb(renderState, poseStack, submitNodeCollector, cameraRenderState);
        }
        super.submit(renderState, poseStack, submitNodeCollector, cameraRenderState);
    }

    private void submitChargeOrb(FugaProjectileRenderState renderState, PoseStack poseStack, SubmitNodeCollector submitNodeCollector,
        CameraRenderState cameraRenderState) {
        float t = renderState.chargePower;
        float pulse = 0.92F + (0.18F * Mth.sin(renderState.ageInTicks * 0.7F));
        float baseScale = (0.24F + (0.88F * t)) * pulse;

        int coreColor = lerpColor(0xFFC05010, 0xFFFFFFFF, t);
        int glowColor = lerpColor(0xA0FF7A20, 0xC8FFF5D0, t);
        int haloColor = lerpColor(0x70300000, 0x90FFF0C0, t);

        poseStack.pushPose();
        poseStack.mulPose(cameraRenderState.orientation);
        submitOrbPlane(poseStack, submitNodeCollector, renderState, MAIN0, baseScale, 0.0F, renderState.ageInTicks * 14.0F, 0.0F, coreColor);
        submitOrbPlane(poseStack, submitNodeCollector, renderState, GLOW0, baseScale * 1.16F, 0.0F, -renderState.ageInTicks * 11.0F, renderState.ageInTicks * 7.0F, glowColor);
        submitOrbPlane(poseStack, submitNodeCollector, renderState, GLOW1, baseScale * 1.38F, 0.0F, renderState.ageInTicks * 18.0F, -renderState.ageInTicks * 9.0F, haloColor);
        poseStack.popPose();
    }

    private void submitBeam(FugaProjectileRenderState renderState, PoseStack poseStack, SubmitNodeCollector submitNodeCollector) {
        float t = renderState.ageInTicks;
        float width = 0.22F + (0.11F * renderState.chargePower) + (0.05F * (0.5F + (0.5F * Mth.sin(t * 8.0F))));
        width = Mth.clamp(width, 0.18F, 0.38F);
        float length = Mth.clamp(renderState.speed * (1.35F + (0.35F * renderState.chargePower)), 0.8F, 9.0F);

        float yawDeg = (float) (Math.atan2(renderState.vx, renderState.vz) * (180.0F / Math.PI));
        float pitchDeg = (float) (-Math.asin(renderState.vy) * (180.0F / Math.PI));

        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(yawDeg));
        poseStack.mulPose(Axis.XP.rotationDegrees(pitchDeg));

        submitBeamLayer(poseStack, submitNodeCollector, renderState, GLOW0, width * 0.50F, length, 0xF2FFFFFF);
        submitBeamLayer(poseStack, submitNodeCollector, renderState, GLOW1, width * 0.96F, length, 0xDDFFB050);
        submitBeamLayer(poseStack, submitNodeCollector, renderState, GLOW1, width * 1.42F, length, 0x90B02000);

        poseStack.popPose();
    }

    private static void submitOrbPlane(
        PoseStack poseStack,
        SubmitNodeCollector submitNodeCollector,
        FugaProjectileRenderState renderState,
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

    private static void submitBeamLayer(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, FugaProjectileRenderState renderState,
        RenderType renderType, float halfWidth, float length, int color) {
        poseStack.pushPose();
        submitNodeCollector.submitCustomGeometry(
            poseStack,
            renderType,
            (pose, vertexConsumer) -> renderBeam(vertexConsumer, pose, renderState.lightCoords, halfWidth, length, color)
        );
        poseStack.popPose();
    }

    private static void renderQuad(FugaProjectileRenderState renderState, PoseStack.Pose pose, VertexConsumer vertexConsumer, int color) {
        vertex(vertexConsumer, pose, renderState.lightCoords, 0.0F, 0, 0, 1, color);
        vertex(vertexConsumer, pose, renderState.lightCoords, 1.0F, 0, 1, 1, color);
        vertex(vertexConsumer, pose, renderState.lightCoords, 1.0F, 1, 1, 0, color);
        vertex(vertexConsumer, pose, renderState.lightCoords, 0.0F, 1, 0, 0, color);
    }

    private static void renderBeam(VertexConsumer vertexConsumer, PoseStack.Pose pose, int light, float halfWidth, float length, int color) {
        float x0 = -halfWidth;
        float x1 = halfWidth;
        float z0 = 0.0F;
        float z1 = length;
        vertexConsumer.addVertex(pose, x0, 0.0F, z0).setColor(color).setUv(0, 0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, 0.0F, 1.0F, 0.0F);
        vertexConsumer.addVertex(pose, x1, 0.0F, z0).setColor(color).setUv(1, 0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, 0.0F, 1.0F, 0.0F);
        vertexConsumer.addVertex(pose, x1, 0.0F, z1).setColor(color).setUv(1, 1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, 0.0F, 1.0F, 0.0F);
        vertexConsumer.addVertex(pose, x0, 0.0F, z1).setColor(color).setUv(0, 1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, 0.0F, 1.0F, 0.0F);
    }

    private static void vertex(VertexConsumer vertexConsumer, PoseStack.Pose pose, int light, float x, int y, int u, int v, int color) {
        vertexConsumer.addVertex(pose, x - 0.5F, y - 0.34F, 0.0F)
            .setColor(color)
            .setUv(u, v)
            .setOverlay(OverlayTexture.NO_OVERLAY)
            .setLight(light)
            .setNormal(pose, 0.0F, 1.0F, 0.0F);
    }

    private static int lerpColor(int from, int to, float t) {
        int a = Mth.lerpInt(t, (from >>> 24) & 0xFF, (to >>> 24) & 0xFF);
        int r = Mth.lerpInt(t, (from >>> 16) & 0xFF, (to >>> 16) & 0xFF);
        int g = Mth.lerpInt(t, (from >>> 8) & 0xFF, (to >>> 8) & 0xFF);
        int b = Mth.lerpInt(t, from & 0xFF, to & 0xFF);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }
}
