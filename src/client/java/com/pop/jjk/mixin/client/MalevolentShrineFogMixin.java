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

        float keepFactor = 1.0F - visualFactor;
        float red = (color.x * keepFactor) + (0.25F * visualFactor);
        float green = color.y * keepFactor;
        float blue = color.z * keepFactor;
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
        float multiplier = 1.0F - (0.68F * density);
        return Math.max(10.0F, renderDistanceBlocks * multiplier);
    }
}
