package com.pop.jjk.mixin.client;

import com.pop.jjk.ScreenShakeManager;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public abstract class CameraShakeMixin {

    @Shadow
    protected abstract void setRotation(float yRot, float xRot);

    @Shadow
    private float yRot;

    @Shadow
    private float xRot;

    @Inject(method = "setup", at = @At("RETURN"))
    private void jjk$applyScreenShake(CallbackInfo ci) {
        if (ScreenShakeManager.isShaking()) {
            float offsetX = ScreenShakeManager.getOffsetX(0.0F);
            float offsetY = ScreenShakeManager.getOffsetY(0.0F);
            setRotation(this.yRot + offsetX, this.xRot + offsetY);
        }
    }
}
