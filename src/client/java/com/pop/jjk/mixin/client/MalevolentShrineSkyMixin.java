package com.pop.jjk.mixin.client;

import com.pop.jjk.MalevolentShrineOverlay;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SkyRenderer;
import net.minecraft.client.renderer.state.SkyRenderState;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SkyRenderer.class)
public abstract class MalevolentShrineSkyMixin {

    @Inject(
        method = "extractRenderState(Lnet/minecraft/client/multiplayer/ClientLevel;FLnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/state/SkyRenderState;)V",
        at = @At("TAIL")
    )
    private void jjk$tintMalevolentSky(ClientLevel level, float partialTick, Camera camera, SkyRenderState state, CallbackInfo ci) {
        float visualFactor = Mth.clamp(
            MalevolentShrineOverlay.getSmoothedFactor() * MalevolentShrineOverlay.getSmoothedProgress(),
            0.0F,
            1.0F
        );
        if (visualFactor <= 0.0F) {
            return;
        }

        float keepFactor = Math.max(0.30F, 1.0F - (0.70F * visualFactor));
        state.skyColor = tintRgb(state.skyColor, keepFactor, visualFactor);
        state.sunriseAndSunsetColor = tintArgb(state.sunriseAndSunsetColor, keepFactor, visualFactor);
        state.starBrightness *= Math.max(0.08F, keepFactor * 0.28F);
        state.rainBrightness *= keepFactor * 0.55F;
        state.endFlashIntensity *= keepFactor * 0.45F;
    }

    private static int tintRgb(int color, float keepFactor, float visualFactor) {
        int red = Math.round((((color >> 16) & 0xFF) * keepFactor) + (150.0F * visualFactor));
        int green = Math.round((((color >> 8) & 0xFF) * (keepFactor * 0.22F)) + (6.0F * visualFactor));
        int blue = Math.round(((color & 0xFF) * (keepFactor * 0.18F)) + (10.0F * visualFactor));
        return (Mth.clamp(red, 0, 255) << 16) | (Mth.clamp(green, 0, 255) << 8) | Mth.clamp(blue, 0, 255);
    }

    private static int tintArgb(int color, float keepFactor, float visualFactor) {
        int alpha = (color >>> 24) & 0xFF;
        int red = Math.round((((color >> 16) & 0xFF) * keepFactor) + (162.0F * visualFactor));
        int green = Math.round((((color >> 8) & 0xFF) * (keepFactor * 0.18F)) + (6.0F * visualFactor));
        int blue = Math.round(((color & 0xFF) * (keepFactor * 0.15F)) + (10.0F * visualFactor));
        return (alpha << 24) | (Mth.clamp(red, 0, 255) << 16) | (Mth.clamp(green, 0, 255) << 8) | Mth.clamp(blue, 0, 255);
    }
}
