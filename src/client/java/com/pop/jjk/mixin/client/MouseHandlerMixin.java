package com.pop.jjk.mixin.client;

import com.pop.jjk.JJKClientMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public class MouseHandlerMixin {

    @Shadow
    @Final
    private Minecraft minecraft;

    @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
    private void jjk$captureMouseWheel(long window, double horizontal, double vertical, CallbackInfo ci) {
        double scrollAmount = vertical != 0.0D ? vertical : horizontal;
        if (scrollAmount == 0.0D) {
            return;
        }

        if (JJKClientMod.captureMouseScroll(scrollAmount, this.minecraft)) {
            ci.cancel();
        }
    }
}
