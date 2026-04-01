package com.pop.jjk.mixin.client;

import com.pop.jjk.InfiniteDomainOverlay;
import net.minecraft.client.renderer.fog.FogRenderer;
import net.minecraft.util.Mth;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(FogRenderer.class)
public abstract class InfiniteDomainFogRendererMixin {

    @ModifyVariable(
        method = "setupFog(Lnet/minecraft/client/Camera;ILnet/minecraft/client/DeltaTracker;FLnet/minecraft/client/multiplayer/ClientLevel;)Lorg/joml/Vector4f;",
        at = @At(value = "STORE"),
        ordinal = 0
    )
    private Vector4f jjk$blackFogColor(Vector4f color) {
        float visualFactor = Mth.clamp(
            InfiniteDomainOverlay.getSmoothedFactor() * InfiniteDomainOverlay.getSmoothedProgress(),
            0.0F,
            1.0F
        );
        if (visualFactor <= 0.0F) {
            return color;
        }

        if (visualFactor >= 0.95F) {
            color.set(0.0F, 0.0F, 0.0F, 1.0F);
            return color;
        }

        float keepFactor = 1.0F - visualFactor;
        color.set(color.x * keepFactor, color.y * keepFactor, color.z * keepFactor, 1.0F);
        return color;
    }

    @ModifyVariable(
        method = "setupFog(Lnet/minecraft/client/Camera;ILnet/minecraft/client/DeltaTracker;FLnet/minecraft/client/multiplayer/ClientLevel;)Lorg/joml/Vector4f;",
        at = @At(value = "STORE"),
        ordinal = 1
    )
    private float jjk$compressFogDistance(float renderDistanceBlocks) {
        float visualFactor = Mth.clamp(
            InfiniteDomainOverlay.getSmoothedFactor() * InfiniteDomainOverlay.getSmoothedProgress(),
            0.0F,
            1.0F
        );
        if (visualFactor <= 0.0F) {
            return renderDistanceBlocks;
        }

        float density = visualFactor * visualFactor;
        float multiplier = 1.0F - (0.92F * density);
        return Math.max(8.0F, renderDistanceBlocks * multiplier);
    }
}
