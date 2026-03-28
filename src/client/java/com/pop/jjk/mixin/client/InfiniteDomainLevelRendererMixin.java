package com.pop.jjk.mixin.client;

import com.pop.jjk.InfiniteDomainOverlay;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.framegraph.FrameGraphBuilder;
import net.minecraft.client.Camera;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public abstract class InfiniteDomainLevelRendererMixin {

    @Inject(method = "addSkyPass(Lcom/mojang/blaze3d/framegraph/FrameGraphBuilder;Lnet/minecraft/client/Camera;Lcom/mojang/blaze3d/buffers/GpuBufferSlice;)V", at = @At("HEAD"), cancellable = true)
    private void jjk$suppressSkyPass(FrameGraphBuilder frameGraphBuilder, Camera camera, GpuBufferSlice gpuBufferSlice, CallbackInfo ci) {
        if (InfiniteDomainOverlay.getInsideFactor() > 0.0F) {
            ci.cancel();
        }
    }

    @Inject(method = "addCloudsPass(Lcom/mojang/blaze3d/framegraph/FrameGraphBuilder;Lnet/minecraft/client/CloudStatus;Lnet/minecraft/world/phys/Vec3;JFIF)V", at = @At("HEAD"), cancellable = true)
    private void jjk$suppressCloudsPass(FrameGraphBuilder frameGraphBuilder, CloudStatus cloudStatus, Vec3 vec3, long time, float partialTick, int renderDistance, float cloudHeight, CallbackInfo ci) {
        if (InfiniteDomainOverlay.getInsideFactor() > 0.0F) {
            ci.cancel();
        }
    }

    @Inject(method = "addWeatherPass(Lcom/mojang/blaze3d/framegraph/FrameGraphBuilder;Lcom/mojang/blaze3d/buffers/GpuBufferSlice;)V", at = @At("HEAD"), cancellable = true)
    private void jjk$suppressWeatherPass(FrameGraphBuilder frameGraphBuilder, GpuBufferSlice gpuBufferSlice, CallbackInfo ci) {
        if (InfiniteDomainOverlay.getInsideFactor() > 0.0F) {
            ci.cancel();
        }
    }
}
