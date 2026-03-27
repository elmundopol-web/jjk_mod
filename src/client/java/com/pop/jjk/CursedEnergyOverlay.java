package com.pop.jjk;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.DeltaTracker;
import net.minecraft.util.Mth;

public final class CursedEnergyOverlay {

    private static final int BAR_WIDTH = 120;
    private static final int BAR_HEIGHT = 6;
    private static final int SEGMENT_COUNT = 4;

    private static float displayRatio = 1.0f;
    private static float pulseTimer = 0.0f;

    private CursedEnergyOverlay() {
    }

    public static void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.options.hideGui) {
            return;
        }

        if (!JJKClientMod.isAbilityHotbarVisible()) {
            return;
        }

        int energy = JJKClientMod.getCursedEnergy();
        int maxEnergy = JJKClientMod.getMaxCursedEnergy();
        if (maxEnergy <= 0) return;

        float targetRatio = (float) energy / maxEnergy;
        float delta = deltaTracker.getGameTimeDeltaPartialTick(true);

        // Animación suave
        displayRatio = Mth.lerp(delta * 0.15f, displayRatio, targetRatio);
        if (Math.abs(displayRatio - targetRatio) < 0.002f) {
            displayRatio = targetRatio;
        }

        // Pulso cuando energía baja
        if (displayRatio < 0.25f) {
            pulseTimer += delta * 0.12f;
        } else {
            pulseTimer = 0.0f;
        }

        Font font = client.font;
        int barLeft = (graphics.guiWidth() - BAR_WIDTH) / 2;
        int barTop = graphics.guiHeight() - 52;

        // Sombra exterior
        graphics.fill(barLeft - 2, barTop - 2, barLeft + BAR_WIDTH + 2, barTop + BAR_HEIGHT + 2, 0x30000000);

        // Borde exterior
        graphics.fill(barLeft - 1, barTop - 1, barLeft + BAR_WIDTH + 1, barTop + BAR_HEIGHT + 1, 0xA0182838);

        // Fondo interior
        graphics.fill(barLeft, barTop, barLeft + BAR_WIDTH, barTop + BAR_HEIGHT, 0xE0080C12);

        // Barra de relleno con gradiente simulado
        int filledWidth = (int) (BAR_WIDTH * displayRatio);
        if (filledWidth > 0) {
            boolean lowEnergy = displayRatio < 0.25f;
            float pulse = lowEnergy ? (float) (0.5 + 0.5 * Math.sin(pulseTimer)) : 0.0f;

            int baseColor = lowEnergy ? lerpColor(0xFFFF4444, 0xFFFF8844, pulse) : getBarColor(displayRatio);
            int darkColor = darken(baseColor, 0.6f);

            // Capa inferior (más oscura)
            graphics.fill(barLeft, barTop, barLeft + filledWidth, barTop + BAR_HEIGHT, darkColor);
            // Capa superior (más clara, simula brillo)
            graphics.fill(barLeft, barTop, barLeft + filledWidth, barTop + BAR_HEIGHT / 2, baseColor);
            // Brillo en la parte superior
            graphics.fill(barLeft, barTop, barLeft + filledWidth, barTop + 1, withAlpha(0xFFFFFF, 40));
        }

        // Marcas de segmento
        for (int i = 1; i < SEGMENT_COUNT; i++) {
            int segX = barLeft + (BAR_WIDTH * i / SEGMENT_COUNT);
            graphics.fill(segX, barTop, segX + 1, barTop + BAR_HEIGHT, 0x50101820);
        }

        // Brillo del borde si la barra está llena
        if (displayRatio >= 0.99f) {
            graphics.fill(barLeft - 1, barTop - 1, barLeft + BAR_WIDTH + 1, barTop, withAlpha(0x6EC8FF, 80));
            graphics.fill(barLeft - 1, barTop + BAR_HEIGHT, barLeft + BAR_WIDTH + 1, barTop + BAR_HEIGHT + 1, withAlpha(0x6EC8FF, 50));
        }

        // Texto: "EC 750/1000"
        String label = "EC";
        String values = energy + "/" + maxEnergy;
        int labelColor = displayRatio < 0.25f ? 0xFFFF8866 : 0xFF5AA8D0;
        int valueColor = displayRatio < 0.25f ? 0xFFFF9977 : 0xFFC8E4F4;

        int labelWidth = font.width(label);
        int valuesWidth = font.width(values);
        int totalTextWidth = labelWidth + 4 + valuesWidth;
        int textLeft = barLeft + (BAR_WIDTH - totalTextWidth) / 2;

        graphics.drawString(font, label, textLeft, barTop - 10, labelColor, false);
        graphics.drawString(font, values, textLeft + labelWidth + 4, barTop - 10, valueColor, false);
    }

    private static int getBarColor(float ratio) {
        if (ratio > 0.6f) return 0xFF56C8FF;
        if (ratio > 0.4f) return 0xFF44A8E0;
        return 0xFF3888C0;
    }

    private static int darken(int color, float factor) {
        int a = (color >> 24) & 0xFF;
        int r = (int) (((color >> 16) & 0xFF) * factor);
        int g = (int) (((color >> 8) & 0xFF) * factor);
        int b = (int) ((color & 0xFF) * factor);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static int lerpColor(int c1, int c2, float t) {
        int a1 = (c1 >> 24) & 0xFF, r1 = (c1 >> 16) & 0xFF, g1 = (c1 >> 8) & 0xFF, b1 = c1 & 0xFF;
        int a2 = (c2 >> 24) & 0xFF, r2 = (c2 >> 16) & 0xFF, g2 = (c2 >> 8) & 0xFF, b2 = c2 & 0xFF;
        return ((int) Mth.lerp(t, a1, a2) << 24) |
               ((int) Mth.lerp(t, r1, r2) << 16) |
               ((int) Mth.lerp(t, g1, g2) << 8) |
               (int) Mth.lerp(t, b1, b2);
    }

    private static int withAlpha(int rgb, int alpha) {
        return ((alpha & 0xFF) << 24) | (rgb & 0xFFFFFF);
    }
}
