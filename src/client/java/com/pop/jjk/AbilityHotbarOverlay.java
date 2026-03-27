package com.pop.jjk;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.DeltaTracker;
import net.minecraft.network.chat.Component;

public final class AbilityHotbarOverlay {

    private static final int TOTAL_SLOTS = 9;
    private static final int SLOT_SIZE = 20;
    private static final int SLOT_GAP = 0;
    private static final int FRAME_PADDING = 1;
    private static final int BAR_HEIGHT = 22;

    private AbilityHotbarOverlay() {
    }

    public static void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft client = Minecraft.getInstance();
        if (!JJKClientMod.shouldRenderAbilityHotbar(client)) {
            return;
        }

        List<JJKClientMod.AbilityHotbarEntry> entries = JJKClientMod.getAbilityHotbarEntries();
        if (entries.isEmpty()) {
            return;
        }

        Font font = client.font;
        int selectedIndex = JJKClientMod.getSelectedAbilityIndex();
        int barWidth = (TOTAL_SLOTS * SLOT_SIZE) + ((TOTAL_SLOTS - 1) * SLOT_GAP);
        int frameWidth = barWidth + (FRAME_PADDING * 2);
        int frameLeft = (graphics.guiWidth() - frameWidth) / 2;
        int frameTop = graphics.guiHeight() - 23;
        int slotsLeft = frameLeft + FRAME_PADDING;
        int slotsTop = frameTop + 1;
        int accent = entries.get(selectedIndex).accentColor();

        renderFrame(graphics, frameLeft, frameTop, frameWidth, BAR_HEIGHT, accent);

        for (int index = 0; index < TOTAL_SLOTS; index++) {
            int slotLeft = slotsLeft + (index * (SLOT_SIZE + SLOT_GAP));
            boolean filled = index < entries.size();

            if (filled) {
                JJKClientMod.AbilityHotbarEntry entry = entries.get(index);
                renderAbilitySlot(graphics, font, slotLeft, slotsTop, index, entry, index == selectedIndex);
            } else {
                renderLockedSlot(graphics, slotLeft, slotsTop, index);
            }
        }
    }

    private static void renderFrame(GuiGraphics graphics, int left, int top, int width, int height, int accent) {
        graphics.fill(left - 1, top - 1, left + width + 1, top, 0x5A020509);
        graphics.fill(left - 1, top + height, left + width + 1, top + height + 1, 0x5A020509);
        graphics.fill(left - 1, top, left, top + height, 0x5A020509);
        graphics.fill(left + width, top, left + width + 1, top + height, 0x5A020509);
        graphics.fill(left + 6, top - 2, left + 40, top - 1, withAlpha(accent, 175));
        graphics.fill(left + width - 40, top - 2, left + width - 6, top - 1, withAlpha(accent, 175));
        graphics.fill(left + 10, top + height + 1, left + width - 10, top + height + 2, withAlpha(accent, 88));
    }

    private static void renderAbilitySlot(
        GuiGraphics graphics,
        Font font,
        int left,
        int top,
        int index,
        JJKClientMod.AbilityHotbarEntry entry,
        boolean selected
    ) {
        boolean passiveActive = entry.passive() && JJKClientMod.isInfinitoActivo();
        boolean onCooldown = !entry.passive() && JJKClientMod.isOnCooldown();
        int accent = entry.accentColor();
        int slotTop = top;
        int outerColor = selected ? withAlpha(accent, 235) : passiveActive ? withAlpha(accent, 165) : 0x5635424F;
        int innerColor = selected ? 0xE21B232C : 0xC41A2029;
        int coreAlpha = selected ? 132 : passiveActive ? 98 : 58;

        if (onCooldown && !entry.passive()) {
            outerColor = withAlpha(accent, 80);
            coreAlpha = 25;
        }

        graphics.fill(left, slotTop, left + SLOT_SIZE, slotTop + SLOT_SIZE, outerColor);
        graphics.fill(left + 1, slotTop + 1, left + SLOT_SIZE - 1, slotTop + SLOT_SIZE - 1, 0x8B06090D);
        graphics.fill(left + 2, slotTop + 2, left + SLOT_SIZE - 2, slotTop + SLOT_SIZE - 2, innerColor);
        graphics.fill(left + 3, slotTop + 3, left + SLOT_SIZE - 3, slotTop + SLOT_SIZE - 3, withAlpha(accent, coreAlpha));
        graphics.fill(left + 2, slotTop + SLOT_SIZE - 4, left + SLOT_SIZE - 2, slotTop + SLOT_SIZE - 2, withAlpha(accent, selected ? 220 : passiveActive ? 170 : 105));
        AbilityIconRenderer.renderIcon(graphics, left + 2, slotTop + 3, entry);

        // Cooldown overlay
        if (onCooldown && !entry.passive()) {
            float progress = JJKClientMod.getCooldownProgress();
            int cooldownHeight = (int)(SLOT_SIZE * progress);
            graphics.fill(left + 1, slotTop + (SLOT_SIZE - cooldownHeight), left + SLOT_SIZE - 1, slotTop + SLOT_SIZE, 0x90100810);

            int seconds = JJKClientMod.getCooldownSeconds();
            if (seconds > 0) {
                String cdText = Integer.toString(seconds);
                int textW = font.width(cdText);
                graphics.drawString(font, cdText, left + (SLOT_SIZE - textW) / 2, slotTop + (SLOT_SIZE - 8) / 2, 0xFFFF6666, false);
            }
        }
    }

    private static void renderLockedSlot(GuiGraphics graphics, int left, int top, int index) {
        graphics.fill(left, top, left + SLOT_SIZE, top + SLOT_SIZE, 0x26303C49);
        graphics.fill(left + 1, top + 1, left + SLOT_SIZE - 1, top + SLOT_SIZE - 1, 0x6F141A22);
        graphics.fill(left + 2, top + 2, left + SLOT_SIZE - 2, top + SLOT_SIZE - 2, 0x2F0B1016);
        graphics.fill(left + 5, top + 9, left + SLOT_SIZE - 5, top + 10, 0x4D5B6F88);
    }

    private static int withAlpha(int rgb, int alpha) {
        return ((alpha & 0xFF) << 24) | (rgb & 0xFFFFFF);
    }
}
