package com.pop.jjk;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

public final class InfiniteDomainOverlay {

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
            state.ageTicks++;
            if (state.remainingTicks <= 0) {
                iterator.remove();
            }
        }
    }

    public static void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null || ACTIVE_DOMAINS.isEmpty()) {
            return;
        }

        DomainVisualState state = getStrongestDomain(client);
        if (state == null) {
            return;
        }

        float insideFactor = getInsideFactor(client, state);
        if (insideFactor <= 0.0F) {
            return;
        }

        int width = graphics.guiWidth();
        int height = graphics.guiHeight();
        float time = (client.level.getGameTime() + deltaTracker.getGameTimeDeltaTicks()) * 0.05F + state.ageTicks * 0.015F;
        float pulse = 0.82F + 0.18F * Mth.sin(time * 2.4F);
        int darknessAlpha = Mth.clamp(Math.round(125.0F + insideFactor * 95.0F + pulse * 18.0F), 0, 235);

        graphics.fill(0, 0, width, height, withAlpha(0x020205, darknessAlpha));
        renderVignette(graphics, width, height, insideFactor, pulse);

        int centerX = width / 2;
        int centerY = height / 2 - Math.round(height * 0.02F);
        int holeRadius = Math.round(Math.min(width, height) * (0.11F + insideFactor * 0.035F + 0.01F * pulse));
        int innerHaloRadius = Math.round(holeRadius * (1.34F + 0.05F * pulse));
        int outerHaloRadius = Math.round(holeRadius * (1.88F + 0.08F * pulse));
        int auraRadius = Math.round(holeRadius * (2.35F + 0.1F * Mth.sin(time * 1.6F)));

        fillCircle(graphics, centerX, centerY, auraRadius, withAlpha(0x061018, 28 + Math.round(insideFactor * 28.0F)));
        fillRing(graphics, centerX, centerY, outerHaloRadius, innerHaloRadius, withAlpha(0xC4E9FF, 26 + Math.round(insideFactor * 34.0F)));
        fillRing(graphics, centerX, centerY, innerHaloRadius, holeRadius + 8, withAlpha(0x90BFE0, 54 + Math.round(insideFactor * 50.0F)));
        fillRing(graphics, centerX, centerY, holeRadius + 8, Math.max(0, holeRadius - 4), withAlpha(0x1A2734, 160));
        fillCircle(graphics, centerX, centerY, holeRadius, withAlpha(0x000000, 240));
        fillCircle(graphics, centerX, centerY, Math.max(8, Math.round(holeRadius * 0.68F)), withAlpha(0x020202, 255));

        renderOrbitBands(graphics, centerX, centerY, holeRadius, time, insideFactor);
    }

    public static void clear() {
        ACTIVE_DOMAINS.clear();
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

    private static void renderVignette(GuiGraphics graphics, int width, int height, float insideFactor, float pulse) {
        int layers = 7;
        for (int i = 0; i < layers; i++) {
            float progress = (i + 1) / (float) layers;
            int alpha = Math.round((20.0F + progress * 26.0F + insideFactor * 28.0F) * pulse);
            int insetX = Math.round(progress * 24.0F);
            int insetY = Math.round(progress * 18.0F);
            graphics.fill(0, 0, width, insetY, withAlpha(0x000000, alpha));
            graphics.fill(0, height - insetY, width, height, withAlpha(0x000000, alpha));
            graphics.fill(0, 0, insetX, height, withAlpha(0x000000, alpha));
            graphics.fill(width - insetX, 0, width, height, withAlpha(0x000000, alpha));
        }
    }

    private static void renderOrbitBands(GuiGraphics graphics, int centerX, int centerY, int holeRadius, float time, float insideFactor) {
        for (int band = 0; band < 3; band++) {
            float bandPulse = Mth.sin(time * (1.2F + band * 0.35F) + band * 1.8F);
            int outerRadius = Math.round(holeRadius * (1.55F + band * 0.28F + bandPulse * 0.05F));
            int innerRadius = Math.round(outerRadius - (6 + band * 3));
            int alpha = Math.round(22.0F + insideFactor * 30.0F - band * 4.0F + Math.abs(bandPulse) * 14.0F);
            fillRing(graphics, centerX, centerY, outerRadius, innerRadius, withAlpha(0xE8F7FF, Mth.clamp(alpha, 8, 78)));
        }
    }

    private static void fillCircle(GuiGraphics graphics, int centerX, int centerY, int radius, int color) {
        if (radius <= 0) {
            return;
        }

        int radiusSquared = radius * radius;
        for (int y = -radius; y <= radius; y++) {
            int x = (int) Math.sqrt(Math.max(0, radiusSquared - y * y));
            graphics.fill(centerX - x, centerY + y, centerX + x + 1, centerY + y + 1, color);
        }
    }

    private static void fillRing(GuiGraphics graphics, int centerX, int centerY, int outerRadius, int innerRadius, int color) {
        if (outerRadius <= 0 || outerRadius <= innerRadius) {
            return;
        }

        int outerSquared = outerRadius * outerRadius;
        int innerSquared = innerRadius * innerRadius;
        for (int y = -outerRadius; y <= outerRadius; y++) {
            int outerX = (int) Math.sqrt(Math.max(0, outerSquared - y * y));
            int innerX = Math.abs(y) <= innerRadius ? (int) Math.sqrt(Math.max(0, innerSquared - y * y)) : -1;

            graphics.fill(centerX - outerX, centerY + y, centerX - innerX, centerY + y + 1, color);
            graphics.fill(centerX + innerX + 1, centerY + y, centerX + outerX + 1, centerY + y + 1, color);
        }
    }

    private static int withAlpha(int rgb, int alpha) {
        return ((Mth.clamp(alpha, 0, 255) & 0xFF) << 24) | (rgb & 0xFFFFFF);
    }

    private static final class DomainVisualState {
        private final double centerX;
        private final double centerY;
        private final double centerZ;
        private final float radius;
        private int remainingTicks;
        private int ageTicks;

        private DomainVisualState(double centerX, double centerY, double centerZ, float radius, int remainingTicks) {
            this.centerX = centerX;
            this.centerY = centerY;
            this.centerZ = centerZ;
            this.radius = radius;
            this.remainingTicks = remainingTicks;
            this.ageTicks = 0;
        }
    }
}
