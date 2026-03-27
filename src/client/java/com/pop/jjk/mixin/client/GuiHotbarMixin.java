package com.pop.jjk.mixin.client;

import com.pop.jjk.JJKClientMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public class GuiHotbarMixin {

    @Inject(method = "renderItemHotbar", at = @At("HEAD"), cancellable = true)
    private void jjk$hideVanillaHotbar(GuiGraphics graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        if (JJKClientMod.shouldRenderAbilityHotbar(Minecraft.getInstance())) {
            ci.cancel();
        }
    }

    @Inject(method = "renderSelectedItemName", at = @At("HEAD"), cancellable = true)
    private void jjk$hideSelectedItemName(GuiGraphics graphics, CallbackInfo ci) {
        if (JJKClientMod.shouldRenderAbilityHotbar(Minecraft.getInstance())) {
            ci.cancel();
        }
    }
}
