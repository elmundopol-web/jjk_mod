package com.pop.jjk.mixin.client;

import com.pop.jjk.JJKClientMod;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardHandler.class)
public class KeyboardHandlerMixin {

    @Shadow
    @Final
    private Minecraft minecraft;

    @Inject(method = "keyPress", at = @At("HEAD"), cancellable = true)
    private void jjk$captureHotbarNumberKeys(long windowPointer, int action, KeyEvent event, CallbackInfo ci) {
        if (action != GLFW.GLFW_PRESS) {
            return;
        }

        int key = event.key();
        if (key < GLFW.GLFW_KEY_1 || key > GLFW.GLFW_KEY_9) {
            return;
        }

        int hotbarIndex = key - GLFW.GLFW_KEY_1;
        if (JJKClientMod.captureHotbarNumberKey(hotbarIndex, this.minecraft)) {
            ci.cancel();
        }
    }
}
