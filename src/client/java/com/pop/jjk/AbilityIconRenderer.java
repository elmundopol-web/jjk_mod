package com.pop.jjk;

import net.minecraft.client.gui.GuiGraphics;

public final class AbilityIconRenderer {

    private AbilityIconRenderer() {
    }

    public static void renderIcon(GuiGraphics graphics, int left, int top, JJKClientMod.AbilityHotbarEntry entry) {
        switch (entry.id()) {
            case "blue" -> renderBlueIcon(graphics, left, top);
            case "red" -> renderRedIcon(graphics, left, top);
            case "purple" -> renderPurpleIcon(graphics, left, top);
            case "infinity" -> renderInfinityIcon(graphics, left, top);
            case "flash_step" -> renderFlashStepIcon(graphics, left, top);
            default -> renderFallbackIcon(graphics, left, top, entry.accentColor());
        }
    }

    private static void renderBlueIcon(GuiGraphics graphics, int left, int top) {
        graphics.fill(left + 3, top + 1, left + 13, top + 11, 0xCC2A85FF);
        graphics.fill(left + 5, top + 3, left + 11, top + 9, 0xFF9ED6FF);
        graphics.fill(left + 7, top + 5, left + 9, top + 7, 0xFFFFFFFF);
        graphics.fill(left, top + 5, left + 2, top + 7, 0xAA6DC7FF);
        graphics.fill(left + 14, top + 5, left + 16, top + 7, 0xAA6DC7FF);
        graphics.fill(left + 7, top, left + 9, top + 2, 0xAA6DC7FF);
        graphics.fill(left + 7, top + 10, left + 9, top + 12, 0xAA6DC7FF);
    }

    private static void renderRedIcon(GuiGraphics graphics, int left, int top) {
        graphics.fill(left + 3, top + 1, left + 13, top + 11, 0xD5A80C16);
        graphics.fill(left + 4, top + 2, left + 12, top + 10, 0xFFFF432A);
        graphics.fill(left + 6, top + 4, left + 10, top + 8, 0xFFFFC38D);
        graphics.fill(left + 2, top + 6, left + 4, top + 8, 0x90FF6C58);
        graphics.fill(left + 12, top + 6, left + 14, top + 8, 0x90FF6C58);
        graphics.fill(left + 7, top + 11, left + 9, top + 13, 0x90FF6C58);
    }

    private static void renderPurpleIcon(GuiGraphics graphics, int left, int top) {
        graphics.fill(left + 2, top + 1, left + 14, top + 12, 0xC02A143F);
        graphics.fill(left + 3, top + 2, left + 13, top + 11, 0xE27634FF);
        graphics.fill(left + 5, top + 4, left + 11, top + 9, 0xFFD8B4FF);
        graphics.fill(left + 6, top + 5, left + 10, top + 8, 0xFFFFFFFF);
        graphics.fill(left, top + 5, left + 3, top + 7, 0x9C5CA8FF);
        graphics.fill(left + 13, top + 5, left + 16, top + 7, 0xA8FF6548);
        graphics.fill(left + 7, top, left + 9, top + 2, 0x904B1B78);
        graphics.fill(left + 7, top + 11, left + 9, top + 13, 0x904B1B78);
    }

    private static void renderInfinityIcon(GuiGraphics graphics, int left, int top) {
        int color = 0xFFD8F6FF;
        int glow = 0x9A83E6FF;
        graphics.fill(left + 1, top + 4, left + 6, top + 9, glow);
        graphics.fill(left + 10, top + 4, left + 15, top + 9, glow);
        graphics.fill(left + 3, top + 6, left + 12, top + 7, color);
        graphics.fill(left + 4, top + 3, left + 5, top + 10, color);
        graphics.fill(left + 11, top + 3, left + 12, top + 10, color);
        graphics.fill(left + 6, top + 5, left + 10, top + 8, 0xFF7BD6F8);
    }

    private static void renderFlashStepIcon(GuiGraphics graphics, int left, int top) {
        int color = 0xFFF4E8AE;
        int shadow = 0xA2D29F39;
        graphics.fill(left + 2, top + 1, left + 7, top + 3, shadow);
        graphics.fill(left + 6, top + 2, left + 11, top + 4, shadow);
        graphics.fill(left + 5, top + 5, left + 10, top + 7, shadow);
        graphics.fill(left + 9, top + 6, left + 14, top + 8, shadow);
        graphics.fill(left + 3, top + 2, left + 8, top + 4, color);
        graphics.fill(left + 7, top + 3, left + 12, top + 5, color);
        graphics.fill(left + 6, top + 6, left + 11, top + 8, color);
        graphics.fill(left + 10, top + 7, left + 15, top + 9, color);
    }

    private static void renderFallbackIcon(GuiGraphics graphics, int left, int top, int accent) {
        graphics.fill(left + 3, top + 2, left + 13, top + 10, withAlpha(accent, 210));
        graphics.fill(left + 5, top + 4, left + 11, top + 8, 0xEAF5F9FF);
    }

    private static int withAlpha(int rgb, int alpha) {
        return ((alpha & 0xFF) << 24) | (rgb & 0xFFFFFF);
    }
}
