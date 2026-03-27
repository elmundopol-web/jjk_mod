package com.pop.jjk.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.pop.jjk.BlueAnimSyncPayload;
import com.pop.jjk.BlueAnimationPose;
import com.pop.jjk.JJKClientMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.entity.HumanoidArm;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemInHandRenderer.class)
public abstract class BlueFirstPersonTransformMixin {

    @Inject(
        method = "renderPlayerArm(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;IFFLnet/minecraft/world/entity/HumanoidArm;)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/entity/player/AvatarRenderer;renderRightHand(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/resources/Identifier;Z)V"
        )
    )
    private void jjk$adjustRightBlueArm(
        PoseStack poseStack,
        SubmitNodeCollector submitNodeCollector,
        int packedLight,
        float equippedProgress,
        float swingProgress,
        HumanoidArm armSide,
        CallbackInfo ci
    ) {
        applyBlueTransform(poseStack, armSide);
    }

    @Inject(
        method = "renderPlayerArm(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;IFFLnet/minecraft/world/entity/HumanoidArm;)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/entity/player/AvatarRenderer;renderLeftHand(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/resources/Identifier;Z)V"
        )
    )
    private void jjk$adjustLeftBlueArm(
        PoseStack poseStack,
        SubmitNodeCollector submitNodeCollector,
        int packedLight,
        float equippedProgress,
        float swingProgress,
        HumanoidArm armSide,
        CallbackInfo ci
    ) {
        applyBlueTransform(poseStack, armSide);
    }

    private void applyBlueTransform(PoseStack poseStack, HumanoidArm armSide) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }

        int phase = JJKClientMod.getBlueAnimPhase(player.getId());
        if (phase == BlueAnimSyncPayload.PHASE_STOP) {
            return;
        }

        BlueAnimationPose.applyFirstPersonCameraTransform(
            poseStack,
            armSide == HumanoidArm.RIGHT,
            phase,
            JJKClientMod.getBlueAnimTicksInPhase(player.getId()),
            0.0F
        );
    }
}
