package com.pop.jjk;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

public final class InfiniteDomainOverlay {

    private static final int DOMAIN_DURATION_TICKS = 20 * 12;
    private static final int DOMAIN_BUILDUP_TICKS = 50;
    private static final Map<Integer, DomainVisualState> ACTIVE_DOMAINS = new HashMap<>();

    private InfiniteDomainOverlay() {
    }

    public static void handleSync(InfiniteDomainSyncPayload payload) {
        if (!payload.active() || payload.remainingTicks() <= 0) {
            ACTIVE_DOMAINS.remove(payload.ownerEntityId());
            return;
        }

        ACTIVE_DOMAINS.put(
            payload.ownerEntityId(),
            new DomainVisualState(
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
            ACTIVE_DOMAINS.clear();
            return;
        }

        Iterator<Map.Entry<Integer, DomainVisualState>> iterator = ACTIVE_DOMAINS.entrySet().iterator();
        while (iterator.hasNext()) {
            DomainVisualState state = iterator.next().getValue();
            state.remainingTicks--;
            if (state.remainingTicks <= 0) {
                iterator.remove();
            }
            // Suavizado de entrada/salida y progreso
            float targetInside = getInsideFactor(client, state);
            float inLerp = targetInside > state.smoothedFactor ? 0.08F : 0.12F;
            state.smoothedFactor += (targetInside - state.smoothedFactor) * inLerp;

            float targetProgress = getProgressFactor(state);
            state.smoothedProgress += (targetProgress - state.smoothedProgress) * 0.04F;
        }
    }

    public static void clear() {
        ACTIVE_DOMAINS.clear();
    }

    public static float getInsideFactor() {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null || ACTIVE_DOMAINS.isEmpty()) {
            return 0.0F;
        }

        DomainVisualState state = getStrongestDomain(client);
        if (state == null) {
            return 0.0F;
        }

        return getInsideFactor(client, state);
    }

    public static float getProgressFactor() {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null || ACTIVE_DOMAINS.isEmpty()) {
            return 0.0F;
        }

        DomainVisualState state = getStrongestDomain(client);
        if (state == null) {
            return 0.0F;
        }

        return getProgressFactor(state);
    }

    public static float getSmoothedFactor() {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null || ACTIVE_DOMAINS.isEmpty()) {
            return 0.0F;
        }

        DomainVisualState state = getStrongestDomain(client);
        if (state == null) {
            return 0.0F;
        }

        return Mth.clamp(state.smoothedFactor, 0.0F, 1.0F);
    }

    public static float getSmoothedProgress() {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null || ACTIVE_DOMAINS.isEmpty()) {
            return 0.0F;
        }

        DomainVisualState state = getStrongestDomain(client);
        if (state == null) {
            return 0.0F;
        }

        return Mth.clamp(state.smoothedProgress, 0.0F, 1.0F);
    }

    private static DomainVisualState getStrongestDomain(Minecraft client) {
        DomainVisualState strongest = null;
        float strongestFactor = 0.0F;

        for (DomainVisualState state : ACTIVE_DOMAINS.values()) {
            float factor = getInsideFactor(client, state);
            if (factor > strongestFactor) {
                strongestFactor = factor;
                strongest = state;
            }
        }

        return strongest;
    }

    private static float getInsideFactor(Minecraft client, DomainVisualState state) {
        Vec3 playerCenter = client.player.position().add(0.0, client.player.getBbHeight() * 0.5, 0.0);
        Vec3 domainCenter = new Vec3(state.centerX, state.centerY, state.centerZ);
        double distance = playerCenter.distanceTo(domainCenter);
        if (distance >= state.radius) {
            return 0.0F;
        }

        return Mth.clamp((float) (1.0D - (distance / state.radius)), 0.0F, 1.0F);
    }

    private static float getProgressFactor(DomainVisualState state) {
        int elapsedTicks = DOMAIN_DURATION_TICKS - Math.max(0, state.remainingTicks);
        return Mth.clamp(elapsedTicks / (float) DOMAIN_BUILDUP_TICKS, 0.0F, 1.0F);
    }

    private static final class DomainVisualState {
        private final double centerX;
        private final double centerY;
        private final double centerZ;
        private final float radius;
        private int remainingTicks;
        private float smoothedFactor;
        private float smoothedProgress;

        private DomainVisualState(double centerX, double centerY, double centerZ, float radius, int remainingTicks) {
            this.centerX = centerX;
            this.centerY = centerY;
            this.centerZ = centerZ;
            this.radius = radius;
            this.remainingTicks = remainingTicks;
            this.smoothedFactor = 0.0F;
            this.smoothedProgress = 0.0F;
        }
    }
}
