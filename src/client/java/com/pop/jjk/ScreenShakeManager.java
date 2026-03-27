package com.pop.jjk;

import java.util.Random;

public final class ScreenShakeManager {

    private static final Random RANDOM = new Random();

    private static float intensity;
    private static int remainingTicks;
    private static float currentOffsetX;
    private static float currentOffsetY;

    private ScreenShakeManager() {
    }

    public static void trigger(float shakeIntensity, int durationTicks) {
        if (shakeIntensity > intensity) {
            intensity = shakeIntensity;
        }
        if (durationTicks > remainingTicks) {
            remainingTicks = durationTicks;
        }
    }

    public static void tick() {
        if (remainingTicks <= 0) {
            intensity = 0.0F;
            currentOffsetX = 0.0F;
            currentOffsetY = 0.0F;
            return;
        }

        float progress = (float) remainingTicks / 20.0F;
        float decay = Math.min(progress, 1.0F);
        float shake = intensity * decay;

        currentOffsetX = (RANDOM.nextFloat() - 0.5F) * 2.0F * shake;
        currentOffsetY = (RANDOM.nextFloat() - 0.5F) * 2.0F * shake;

        remainingTicks--;
    }

    public static float getOffsetX(float partialTick) {
        return currentOffsetX;
    }

    public static float getOffsetY(float partialTick) {
        return currentOffsetY;
    }

    public static boolean isShaking() {
        return remainingTicks > 0;
    }
}
