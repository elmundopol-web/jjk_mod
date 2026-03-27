package com.pop.jjk;

import java.util.Locale;
import java.util.UUID;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.EntityHitResult;

public final class EnemyHealthOverlay {

    private static final int TARGET_HOLD_TICKS = 40;
    private static final int DAMAGE_FLASH_TICKS = 24;
    private static final int PANEL_WIDTH = 188;
    private static final int PANEL_HEIGHT = 36;
    private static final int BAR_WIDTH = 156;
    private static final int BAR_HEIGHT = 8;
    private static final int ABSORPTION_BAR_HEIGHT = 3;

    private static LivingEntity trackedTarget;
    private static UUID trackedTargetId;
    private static int holdTicks;
    private static float displayedHealth;
    private static float observedHealth;
    private static float observedAbsorption;
    private static float recentDamage;
    private static int recentDamageTicks;

    private EnemyHealthOverlay() {
    }

    public static void tick(Minecraft client) {
        if (client.player == null || client.level == null) {
            reset();
            return;
        }

        LivingEntity lookTarget = resolveLookTarget(client);
        boolean targetChanged = lookTarget != null && (trackedTarget == null || !lookTarget.getUUID().equals(trackedTargetId));
        if (lookTarget != null) {
            trackedTarget = lookTarget;
            trackedTargetId = lookTarget.getUUID();
            holdTicks = TARGET_HOLD_TICKS;
        } else if (trackedTarget == null || !trackedTarget.isAlive() || trackedTarget.level() != client.level) {
            reset();
            return;
        } else if (holdTicks > 0) {
            holdTicks--;
        } else {
            trackedTarget = null;
            trackedTargetId = null;
            observedHealth = 0.0F;
            observedAbsorption = 0.0F;
            displayedHealth = 0.0F;
            recentDamage = 0.0F;
            recentDamageTicks = 0;
            return;
        }

        if (trackedTarget == null || !trackedTarget.isAlive()) {
            reset();
            return;
        }

        float currentHealth = Mth.clamp(trackedTarget.getHealth(), 0.0F, trackedTarget.getMaxHealth());
        float currentAbsorption = Math.max(0.0F, trackedTarget.getAbsorptionAmount());

        if (targetChanged || trackedTargetId == null || observedHealth <= 0.0F) {
            trackedTargetId = trackedTarget.getUUID();
            displayedHealth = currentHealth;
            observedHealth = currentHealth;
            observedAbsorption = currentAbsorption;
            recentDamage = 0.0F;
            recentDamageTicks = 0;
            return;
        }

        if (currentHealth < observedHealth - 0.05F) {
            recentDamage = observedHealth - currentHealth;
            recentDamageTicks = DAMAGE_FLASH_TICKS;
        } else if (recentDamageTicks > 0) {
            recentDamageTicks--;
        }

        if (currentAbsorption < observedAbsorption - 0.05F) {
            recentDamage = Math.max(recentDamage, observedAbsorption - currentAbsorption);
            recentDamageTicks = DAMAGE_FLASH_TICKS;
        }

        displayedHealth += (currentHealth - displayedHealth) * 0.34F;
        observedHealth = currentHealth;
        observedAbsorption = currentAbsorption;
    }

    public static void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        if (trackedTarget == null || !trackedTarget.isAlive()) {
            return;
        }

        Minecraft client = Minecraft.getInstance();
        Font font = client.font;
        int left = (graphics.guiWidth() - PANEL_WIDTH) / 2;
        int top = 18;
        float maxHealth = Math.max(1.0F, trackedTarget.getMaxHealth());
        float clampedDisplayedHealth = Mth.clamp(displayedHealth, 0.0F, maxHealth);
        float healthRatio = clampedDisplayedHealth / maxHealth;
        float absorption = Math.max(0.0F, trackedTarget.getAbsorptionAmount());
        float absorptionRatio = Math.min(1.0F, absorption / maxHealth);
        int accent = healthRatio > 0.6F ? 0x67D98D : healthRatio > 0.3F ? 0xE3B85A : 0xE26262;
        int barLeft = left + 16;
        int barTop = top + 20;
        int fillWidth = Math.max(0, Math.round(BAR_WIDTH * healthRatio));
        int absorptionWidth = Math.max(0, Math.round(BAR_WIDTH * absorptionRatio));
        Component name = trackedTarget.getDisplayName();
        String numbers = formatNumber(observedHealth) + " / " + formatNumber(maxHealth) + " HP";

        graphics.fill(left, top, left + PANEL_WIDTH, top + PANEL_HEIGHT, 0xC40C1117);
        graphics.fill(left + 1, top + 1, left + PANEL_WIDTH - 1, top + PANEL_HEIGHT - 1, 0xA6121921);
        graphics.fill(left + 10, top + 4, left + 58, top + 5, withAlpha(accent, 220));
        graphics.fill(left + PANEL_WIDTH - 58, top + 4, left + PANEL_WIDTH - 10, top + 5, withAlpha(accent, 220));

        graphics.drawString(font, name, left + 15, top + 8, 0xF3F7FB, false);
        graphics.drawString(font, numbers, left + PANEL_WIDTH - 16 - font.width(numbers), top + 8, 0xC3D0DA, false);

        graphics.fill(barLeft, barTop, barLeft + BAR_WIDTH, barTop + BAR_HEIGHT, 0x66151B22);
        graphics.fill(barLeft + 1, barTop + 1, barLeft + BAR_WIDTH - 1, barTop + BAR_HEIGHT - 1, 0xAA111820);

        if (fillWidth > 0) {
            graphics.fill(barLeft + 1, barTop + 1, barLeft + fillWidth, barTop + BAR_HEIGHT - 1, withAlpha(accent, 228));
        }

        if (absorptionWidth > 0) {
            graphics.fill(
                barLeft + 1,
                barTop - ABSORPTION_BAR_HEIGHT - 1,
                barLeft + absorptionWidth,
                barTop - 1,
                0xD9EABF55
            );
        }

        if (recentDamageTicks > 0 && recentDamage > 0.05F) {
            int alpha = Math.max(70, Math.round(255.0F * (recentDamageTicks / (float) DAMAGE_FLASH_TICKS)));
            String damageText = "-" + formatNumber(recentDamage);
            graphics.drawString(
                font,
                damageText,
                left + PANEL_WIDTH + 6,
                top + 17,
                withAlpha(0xFF7A7A, alpha),
                false
            );
        }
    }

    private static LivingEntity resolveLookTarget(Minecraft client) {
        if (client.hitResult instanceof EntityHitResult entityHitResult
            && entityHitResult.getEntity() instanceof LivingEntity livingEntity
            && livingEntity != client.player
            && livingEntity.isAlive()) {
            return livingEntity;
        }

        return null;
    }

    private static String formatNumber(float value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }

    private static int withAlpha(int rgb, int alpha) {
        return ((alpha & 0xFF) << 24) | (rgb & 0xFFFFFF);
    }

    private static void reset() {
        trackedTarget = null;
        trackedTargetId = null;
        holdTicks = 0;
        displayedHealth = 0.0F;
        observedHealth = 0.0F;
        observedAbsorption = 0.0F;
        recentDamage = 0.0F;
        recentDamageTicks = 0;
    }
}
