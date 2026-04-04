package com.pop.jjk;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

public final class MalevolentShrineOverlay {

    private static final int DOMAIN_DURATION_TICKS = 20 * 10;
    private static final int DOMAIN_BUILDUP_TICKS = 20;
    private static final int DOMAIN_VISUAL_FADE_TICKS = 16;
    private static final Map<Integer, ShrineVisualState> ACTIVE_SHRINES = new HashMap<>();

    private MalevolentShrineOverlay() {
    }

    public static void handleSync(MalevolentShrineSyncPayload payload) {
        if (!payload.active() || payload.remainingTicks() <= 0) {
            ShrineVisualState state = ACTIVE_SHRINES.get(payload.ownerEntityId());
            if (state != null) {
                state.startFadeOut();
            }
            return;
        }

        ACTIVE_SHRINES.put(
            payload.ownerEntityId(),
            new ShrineVisualState(
                payload.centerX(),
                payload.centerY(),
                payload.centerZ(),
                payload.radius(),
                payload.remainingTicks()
            )
        );
    }

    public static void tick(Minecraft client) {
        if (client.level == null || client.player == null) {
            ACTIVE_SHRINES.clear();
            return;
        }

        Iterator<Map.Entry<Integer, ShrineVisualState>> iterator = ACTIVE_SHRINES.entrySet().iterator();
        while (iterator.hasNext()) {
            ShrineVisualState state = iterator.next().getValue();
            if (state.fadingOut) {
                state.fadeTicksRemaining--;
            } else {
                state.remainingTicks--;
            }

            if ((!state.fadingOut && state.remainingTicks <= 0) || (state.fadingOut && state.fadeTicksRemaining <= 0)) {
                iterator.remove();
                continue;
            }

            float targetInside = getInsideFactor(client, state);
            float inLerp = targetInside > state.smoothedFactor ? 0.08F : 0.12F;
            state.smoothedFactor += (targetInside - state.smoothedFactor) * inLerp;

            float targetProgress = getProgressFactor(state);
            state.smoothedProgress += (targetProgress - state.smoothedProgress) * 0.04F;
        }
    }

    public static void clear() {
        ACTIVE_SHRINES.clear();
    }

    public static float getInsideFactor() {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null || ACTIVE_SHRINES.isEmpty()) {
            return 0.0F;
        }

        ShrineVisualState state = getStrongestShrine(client);
        if (state == null) {
            return 0.0F;
        }

        return getInsideFactor(client, state);
    }

    public static float getSmoothedFactor() {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null || ACTIVE_SHRINES.isEmpty()) {
            return 0.0F;
        }

        ShrineVisualState state = getStrongestShrine(client);
        if (state == null) {
            return 0.0F;
        }

        return Mth.clamp(state.smoothedFactor, 0.0F, 1.0F);
    }

    public static float getProgressFactor() {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null || ACTIVE_SHRINES.isEmpty()) {
            return 0.0F;
        }

        ShrineVisualState state = getStrongestShrine(client);
        if (state == null) {
            return 0.0F;
        }

        return getProgressFactor(state);
    }

    public static float getSmoothedProgress() {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null || ACTIVE_SHRINES.isEmpty()) {
            return 0.0F;
        }

        ShrineVisualState state = getStrongestShrine(client);
        if (state == null) {
            return 0.0F;
        }

        return Mth.clamp(state.smoothedProgress, 0.0F, 1.0F);
    }

    private static ShrineVisualState getStrongestShrine(Minecraft client) {
        ShrineVisualState strongest = null;
        float strongestFactor = 0.0F;

        for (ShrineVisualState state : ACTIVE_SHRINES.values()) {
            float factor = getInsideFactor(client, state);
            if (factor > strongestFactor) {
                strongestFactor = factor;
                strongest = state;
            }
        }

        return strongest;
    }

    private static float getInsideFactor(Minecraft client, ShrineVisualState state) {
        Vec3 playerCenter = client.player.position().add(0.0, client.player.getBbHeight() * 0.5, 0.0);
        Vec3 shrineCenter = new Vec3(state.centerX, state.centerY, state.centerZ);
        double distance = playerCenter.distanceTo(shrineCenter);
        if (distance >= state.radius) {
            return 0.0F;
        }

        return Mth.clamp((float) (1.0D - (distance / state.radius)), 0.0F, 1.0F);
    }

    private static float getProgressFactor(ShrineVisualState state) {
        if (state.fadingOut) {
            return Mth.clamp(state.fadeTicksRemaining / (float) DOMAIN_VISUAL_FADE_TICKS, 0.0F, 1.0F);
        }

        int elapsedTicks = DOMAIN_DURATION_TICKS - Math.max(0, state.remainingTicks);
        return Mth.clamp(elapsedTicks / (float) DOMAIN_BUILDUP_TICKS, 0.0F, 1.0F);
    }

    private static final class ShrineVisualState {
        private final double centerX;
        private final double centerY;
        private final double centerZ;
        private final float radius;
        private int remainingTicks;
        private float smoothedFactor;
        private float smoothedProgress;
        private boolean fadingOut;
        private int fadeTicksRemaining;

        private ShrineVisualState(double centerX, double centerY, double centerZ, float radius, int remainingTicks) {
            this.centerX = centerX;
            this.centerY = centerY;
            this.centerZ = centerZ;
            this.radius = radius;
            this.remainingTicks = remainingTicks;
            this.smoothedFactor = 0.0F;
            this.smoothedProgress = 0.0F;
            this.fadingOut = false;
            this.fadeTicksRemaining = DOMAIN_VISUAL_FADE_TICKS;
        }

        private void startFadeOut() {
            this.fadingOut = true;
            this.fadeTicksRemaining = DOMAIN_VISUAL_FADE_TICKS;
        }
    }
}
