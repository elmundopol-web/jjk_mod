package com.pop.jjk.mixin.client;

import com.pop.jjk.BlueAnimSyncPayload;
import com.pop.jjk.BlueAnimationPose;
import com.pop.jjk.JJKClientMod;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HumanoidModel.class)
public abstract class BluePlayerAnimMixin {

    @Shadow
    public ModelPart rightArm;

    @Shadow
    public ModelPart leftArm;

    @Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/HumanoidRenderState;)V", at = @At("TAIL"))
    private void jjk$applyBluePose(HumanoidRenderState state, CallbackInfo ci) {
        if (!(state instanceof AvatarRenderState avatarState)) {
            return;
        }

        int phase = JJKClientMod.getBlueAnimPhase(avatarState.id);
        if (phase == BlueAnimSyncPayload.PHASE_STOP) {
            return;
        }

        int ticksInPhase = JJKClientMod.getBlueAnimTicksInPhase(avatarState.id);
        BlueAnimationPose.applyToHumanoidArms(
            this.rightArm,
            this.leftArm,
            phase,
            ticksInPhase,
            fractionalPart(avatarState.ageInTicks)
        );
    }

    private float fractionalPart(float value) {
        return value - Mth.floor(value);
    }
}
