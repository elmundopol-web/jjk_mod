package com.pop.jjk.mixin.client;

import com.pop.jjk.JJKClientMod;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
public class MinecraftAbilityInputMixin {

    @Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
    private void jjk$useAbilityInsteadOfAttack(CallbackInfoReturnable<Boolean> cir) {
        Minecraft client = (Minecraft) (Object) this;
        if (JJKClientMod.handlePrimaryAbilityClick(client)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "continueAttack", at = @At("HEAD"), cancellable = true)
    private void jjk$stopHeldAttack(boolean mining, CallbackInfo ci) {
        Minecraft client = (Minecraft) (Object) this;
        if (JJKClientMod.isAbilityHotbarVisible() && client.screen == null) {
            ci.cancel();
        }
    }

    @Inject(method = "startUseItem", at = @At("HEAD"), cancellable = true)
    private void jjk$blockVanillaItemUse(CallbackInfo ci) {
        Minecraft client = (Minecraft) (Object) this;
        if (JJKClientMod.isAbilityHotbarVisible() && client.screen == null) {
            ci.cancel();
        }
    }
}
