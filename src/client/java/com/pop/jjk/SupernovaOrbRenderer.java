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

/**
 * Renderizador simple para las pequeñas esferas rojas de Supernova.
 * Usa una textura roja emisiva y varias capas para un glow bonito.
 */
public final class SupernovaOrbRenderer extends EntityRenderer<SupernovaOrbProjectileEntity, EntityRenderState> {

    private static final Identifier TEXTURE =
        Identifier.fromNamespaceAndPath(JJKMod.MOD_ID, "textures/entity/red_orb_0.png");

    private static final RenderType CORE_TYPE = RenderTypes.entityCutoutNoCull(TEXTURE);
    private static final RenderType GLOW_TYPE = RenderTypes.entityTranslucentEmissive(TEXTURE);

    public SupernovaOrbRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    protected int getBlockLightLevel(SupernovaOrbProjectileEntity entity, BlockPos pos) {
        return 15;
    }

    @Override
    public EntityRenderState createRenderState() {
        return new EntityRenderState();
    }

    @Override
    public void submit(EntityRenderState state, PoseStack pose, SubmitNodeCollector submitter, CameraRenderState camera) {
        float t = state.ageInTicks;
        float pulse = 1.0F + Mth.sin(t * 6.2F) * 0.12F;
        pose.pushPose();
        pose.mulPose(camera.orientation);
        pose.scale(1.0F, 1.0F, 1.0F);

        int a1 = 0xC0;
        int a2 = 0x90;
        int a3 = 0x50;

        // Núcleo pequeño
        submitBillboard(pose, submitter, state, CORE_TYPE, 0.06F * pulse, 0.06F * pulse, (a1 << 24) | 0xFFFFFF);
        // Glow medio
        submitBillboard(pose, submitter, state, GLOW_TYPE, 0.12F * pulse, 0.12F * pulse, (a2 << 24) | 0xFF3030);
        // Halo exterior
        submitBillboard(pose, submitter, state, GLOW_TYPE, 0.20F * pulse, 0.20F * pulse, (a3 << 24) | 0xAA0000);

        pose.popPose();
        super.submit(state, pose, submitter, camera);
    }

    private static void submitBillboard(PoseStack pose, SubmitNodeCollector submitter, EntityRenderState state,
                                        RenderType type, float halfW, float halfH, int color) {
        submitter.submitCustomGeometry(pose, type, (m, vc) -> renderQuad(state, m, vc, halfW, halfH, color));
    }

    private static void renderQuad(EntityRenderState state, PoseStack.Pose m, VertexConsumer vc,
                                   float halfW, float halfH, int color) {
        vertex(vc, m, state.lightCoords, -halfW, -halfH, 0, 1, color);
        vertex(vc, m, state.lightCoords,  halfW, -halfH, 1, 1, color);
        vertex(vc, m, state.lightCoords,  halfW,  halfH, 1, 0, color);
        vertex(vc, m, state.lightCoords, -halfW,  halfH, 0, 0, color);
    }

    private static void vertex(VertexConsumer vc, PoseStack.Pose m, int light, float x, float y, int u, int v, int color) {
        vc.addVertex(m, x, y, 0.0F)
            .setColor(color)
            .setUv(u, v)
            .setOverlay(OverlayTexture.NO_OVERLAY)
            .setLight(light)
            .setNormal(m, 0.0F, 1.0F, 0.0F);
    }
}
