package com.pop.jjk.mixin.client;

import com.pop.jjk.InfiniteDomainOverlay;
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
public abstract class InfiniteDomainLevelRendererMixin {

    @Inject(
        method = "extractRenderState(Lnet/minecraft/client/multiplayer/ClientLevel;FLnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/state/SkyRenderState;)V",
        at = @At("TAIL")
    )
    private void jjk$blackenSkyProgressively(ClientLevel level, float partialTick, Camera camera, SkyRenderState state, CallbackInfo ci) {
        float visualFactor = Mth.clamp(
            InfiniteDomainOverlay.getInsideFactor() * InfiniteDomainOverlay.getProgressFactor(),
            0.0F,
            1.0F
        );
        if (visualFactor <= 0.0F) {
            return;
        }

        float keepFactor = 1.0F - visualFactor;
        state.skyColor = scaleRgb(state.skyColor, keepFactor);
        state.sunriseAndSunsetColor = scaleArgb(state.sunriseAndSunsetColor, keepFactor);
        state.starBrightness *= keepFactor;
        state.rainBrightness *= keepFactor;
        state.endFlashIntensity *= keepFactor;
        state.shouldRenderDarkDisc = true;
    }

    private static int scaleRgb(int color, float factor) {
        int red = Math.round(((color >> 16) & 0xFF) * factor);
        int green = Math.round(((color >> 8) & 0xFF) * factor);
        int blue = Math.round((color & 0xFF) * factor);
        return (red << 16) | (green << 8) | blue;
    }

    private static int scaleArgb(int color, float factor) {
        int alpha = Math.round(((color >>> 24) & 0xFF) * factor);
        int red = Math.round(((color >> 16) & 0xFF) * factor);
        int green = Math.round(((color >> 8) & 0xFF) * factor);
        int blue = Math.round((color & 0xFF) * factor);
        return (alpha << 24) | (red << 16) | (green << 8) | blue;
    }
}
