package com.pop.jjk;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.core.BlockPos;

/**
 * Renderer minimalista e intencionalmente vacío: el rayo y la explosión se expresan sólo con partículas.
 */
public class FugaProjectileRenderer extends EntityRenderer<FugaProjectileEntity, EntityRenderState> {

    public FugaProjectileRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    protected int getBlockLightLevel(FugaProjectileEntity entity, BlockPos pos) {
        return 15;
    }

    @Override
    public EntityRenderState createRenderState() {
        return new EntityRenderState();
    }

    @Override
    public void submit(EntityRenderState renderState, PoseStack poseStack, SubmitNodeCollector submitNodeCollector,
                       CameraRenderState cameraRenderState) {
        // Sin geometría: Fuga es un rayo de partículas y una explosión, no un mesh persistente.
    }
}
