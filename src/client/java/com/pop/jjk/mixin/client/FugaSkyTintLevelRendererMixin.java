package com.pop.jjk.mixin.client;

import com.pop.jjk.JJKClientMod;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SkyRenderer;
import net.minecraft.client.renderer.state.SkyRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SkyRenderer.class)
public abstract class FugaSkyTintLevelRendererMixin {

    @Inject(
        method = "extractRenderState(Lnet/minecraft/client/multiplayer/ClientLevel;FLnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/state/SkyRenderState;)V",
        at = @At("TAIL")
    )
    private void jjk$fugaTintSky(ClientLevel level, float partialTick, Camera camera, SkyRenderState state, CallbackInfo ci) {
        float f = JJKClientMod.getFugaSkyTintFactor();
        if (f <= 0.0F) return;

        // Mezclar el color del cielo hacia un naranja rojizo sin afectar HUD
        int sky = state.skyColor;
        int tint = 0xCC3A0A; // RGB
        state.skyColor = lerpRgb(sky, tint, f);

        // Mezclar amanecer/atardecer suavemente
        int ss = state.sunriseAndSunsetColor;
        state.sunriseAndSunsetColor = lerpArgb(ss, 0xCC3A0A | 0x99000000, f * 0.6F);
    }

    private static int lerpRgb(int from, int to, float f) {
        f = Math.max(0.0F, Math.min(1.0F, f));
        int r0 = (from >> 16) & 0xFF;
        int g0 = (from >> 8) & 0xFF;
        int b0 = from & 0xFF;
        int r1 = (to >> 16) & 0xFF;
        int g1 = (to >> 8) & 0xFF;
        int b1 = to & 0xFF;
        int r = Math.round(r0 * (1 - f) + r1 * f);
        int g = Math.round(g0 * (1 - f) + g1 * f);
        int b = Math.round(b0 * (1 - f) + b1 * f);
        return (r << 16) | (g << 8) | b;
    }

    private static int lerpArgb(int from, int to, float f) {
        f = Math.max(0.0F, Math.min(1.0F, f));
        int a0 = (from >>> 24) & 0xFF;
        int r0 = (from >> 16) & 0xFF;
        int g0 = (from >> 8) & 0xFF;
        int b0 = from & 0xFF;
        int a1 = (to >>> 24) & 0xFF;
        int r1 = (to >> 16) & 0xFF;
        int g1 = (to >> 8) & 0xFF;
        int b1 = to & 0xFF;
        int a = Math.round(a0 * (1 - f) + a1 * f);
        int r = Math.round(r0 * (1 - f) + r1 * f);
        int g = Math.round(g0 * (1 - f) + g1 * f);
        int b = Math.round(b0 * (1 - f) + b1 * f);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }
}
