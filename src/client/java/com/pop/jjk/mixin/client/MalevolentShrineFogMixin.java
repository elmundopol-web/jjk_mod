package com.pop.jjk.mixin.client;

import com.pop.jjk.MalevolentShrineOverlay;
import net.minecraft.client.renderer.fog.FogRenderer;
import net.minecraft.util.Mth;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(FogRenderer.class)
public abstract class MalevolentShrineFogMixin {

    @ModifyVariable(
        method = "setupFog(Lnet/minecraft/client/Camera;ILnet/minecraft/client/DeltaTracker;FLnet/minecraft/client/multiplayer/ClientLevel;)Lorg/joml/Vector4f;",
        at = @At(value = "STORE"),
        ordinal = 0
    )
    private Vector4f jjk$malevolentFogColor(Vector4f color) {
        float visualFactor = Mth.clamp(
            MalevolentShrineOverlay.getSmoothedFactor() * MalevolentShrineOverlay.getSmoothedProgress(),
            0.0F,
            1.0F
        );
        if (visualFactor <= 0.0F) {
            return color;
        }

        float keepFactor = 1.0F - (visualFactor * 0.92F);
        float red = (color.x * keepFactor) + (0.34F * visualFactor);
        float green = (color.y * (keepFactor * 0.32F)) + (0.01F * visualFactor);
        float blue = (color.z * (keepFactor * 0.25F)) + (0.015F * visualFactor);
        color.set(red, green, blue, 1.0F);
        return color;
    }

    @ModifyVariable(
        method = "setupFog(Lnet/minecraft/client/Camera;ILnet/minecraft/client/DeltaTracker;FLnet/minecraft/client/multiplayer/ClientLevel;)Lorg/joml/Vector4f;",
        at = @At(value = "STORE"),
        ordinal = 1
    )
    private float jjk$compressMalevolentFogDistance(float renderDistanceBlocks) {
        float visualFactor = Mth.clamp(
            MalevolentShrineOverlay.getSmoothedFactor() * MalevolentShrineOverlay.getSmoothedProgress(),
            0.0F,
            1.0F
        );
        if (visualFactor <= 0.0F) {
            return renderDistanceBlocks;
        }

        float density = visualFactor * visualFactor;
        float multiplier = 1.0F - (0.78F * density);
        return Math.max(8.0F, renderDistanceBlocks * multiplier);
    }
}
