package com.pop.jjk.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.pop.jjk.BlueAnimSyncPayload;
import com.pop.jjk.BlueAnimationPose;
import com.pop.jjk.JJKClientMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AvatarRenderer.class)
public abstract class BlueFirstPersonArmMixin {

    @SuppressWarnings("unchecked")
    @Inject(
        method = "renderHand(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/resources/Identifier;Lnet/minecraft/client/model/geom/ModelPart;Z)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/SubmitNodeCollector;submitModelPart(Lnet/minecraft/client/model/geom/ModelPart;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/rendertype/RenderType;IILnet/minecraft/client/renderer/texture/TextureAtlasSprite;)V"
        )
    )
    private void jjk$applyBlueFirstPersonPose(
        PoseStack poseStack,
        SubmitNodeCollector submitNodeCollector,
        int packedLight,
        Identifier skinTexture,
        ModelPart armPart,
        boolean sleeveVisible,
        CallbackInfo ci
    ) {
        Minecraft client = Minecraft.getInstance();
        Player player = client.player;
        if (player == null) {
            return;
        }

        int phase = JJKClientMod.getBlueAnimPhase(player.getId());
        if (phase == BlueAnimSyncPayload.PHASE_STOP) {
            return;
        }

        LivingEntityRenderer<?, ?, ?> renderer = (LivingEntityRenderer<?, ?, ?>) (Object) this;
        PlayerModel model = (PlayerModel) renderer.getModel();
        BlueAnimationPose.applyToFirstPersonArm(
            model,
            armPart,
            phase,
            JJKClientMod.getBlueAnimTicksInPhase(player.getId()),
            0.0F
        );
    }
}
