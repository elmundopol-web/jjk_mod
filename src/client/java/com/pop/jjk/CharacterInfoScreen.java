package com.pop.jjk;

import java.util.List;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

public class CharacterInfoScreen extends Screen {

    private static final int HEADER_HEIGHT = 28;
    private static final int CHARACTER_BLOCK_HEIGHT = 36;
    private static final int ABILITY_ROW_HEIGHT = 38;
    private static final int ICON_SIZE = 22;
    private static final int PADDING = 10;

    private int leftPos;
    private int topPos;
    private int panelWidth;
    private int panelHeight;

    public CharacterInfoScreen() {
        super(Component.translatable("screen.jjk.info_title"));
    }

    @Override
    protected void init() {
        calculateDimensions();

        this.addRenderableWidget(Button.builder(
            Component.translatable("button.jjk.close"),
            button -> this.onClose()
        ).bounds(this.leftPos + this.panelWidth - 56, this.topPos + 4, 48, 16).build());

        this.addRenderableWidget(Button.builder(
            Component.translatable("button.jjk.change_character"),
            button -> {
                if (this.minecraft != null) {
                    JJKClientMod.openCharacterSelection(this.minecraft);
                }
            }
        ).bounds(this.leftPos + PADDING, this.topPos + this.panelHeight - 22, 100, 16).build());
    }

    private void calculateDimensions() {
        this.panelWidth = (int)(this.width * 0.92f);
        this.panelHeight = (int)(this.height * 0.92f);
        
        this.leftPos = (this.width - this.panelWidth) / 2;
        this.topPos = (this.height - this.panelHeight) / 2;
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        JJKRoster.CharacterDefinition character = JJKClientMod.getSelectedCharacterDefinition();
        if (character == null) {
            super.render(graphics, mouseX, mouseY, delta);
            return;
        }

        int accent = character.accentColor();

        // Fondo
        graphics.fill(0, 0, this.width, this.height, 0xA404080D);
        graphics.fill(this.leftPos - 3, this.topPos - 3, this.leftPos + this.panelWidth + 3, this.topPos + this.panelHeight + 3, 0x52000000);
        graphics.fill(this.leftPos, this.topPos, this.leftPos + this.panelWidth, this.topPos + this.panelHeight, 0xF010151C);

        // Header
        graphics.fill(this.leftPos, this.topPos, this.leftPos + this.panelWidth, this.topPos + HEADER_HEIGHT, 0xFF13202B);
        graphics.fill(this.leftPos + PADDING, this.topPos + 24, this.leftPos + this.panelWidth - PADDING, this.topPos + 25, withAlpha(accent, 160));

        graphics.drawString(this.font, Component.translatable("screen.jjk.info_title"), this.leftPos + PADDING, this.topPos + 6, opaque(0xF3F7FF), false);
        graphics.drawString(
            this.font,
            Component.translatable("screen.jjk.info_subtitle", Component.translatable(character.nameKey())),
            this.leftPos + PADDING,
            this.topPos + 16,
            opaque(0xA9BDD8),
            false
        );

        // Bloque de personaje
        this.renderCharacterBlock(graphics, character, accent);

        // Lista de habilidades
        this.renderAbilityList(graphics, character);

        super.render(graphics, mouseX, mouseY, delta);
    }

    private void renderCharacterBlock(GuiGraphics graphics, JJKRoster.CharacterDefinition character, int accent) {
        int blockTop = this.topPos + HEADER_HEIGHT + 4;
        int left = this.leftPos + PADDING;
        int maxWidth = this.panelWidth - (PADDING * 2);

        // Fondo del bloque
        graphics.fill(left, blockTop, left + maxWidth, blockTop + CHARACTER_BLOCK_HEIGHT, 0x910D1117);

        // Avatar pequeño
        graphics.fill(left + 4, blockTop + 4, left + 28, blockTop + 28, withAlpha(accent, 60));
        graphics.fill(left + 7, blockTop + 7, left + 25, blockTop + 25, withAlpha(accent, 145));
        graphics.fill(left + 11, blockTop + 11, left + 21, blockTop + 21, withAlpha(accent, 255));

        // Nombre + Trait en la misma zona
        graphics.drawString(this.font, Component.translatable(character.nameKey()), left + 34, blockTop + 6, opaque(0xF4F7FF), false);
        graphics.drawString(
            this.font,
            Component.translatable(character.supportsInfinity() ? "screen.jjk.character_trait_limitless" : "screen.jjk.character_trait_striker"),
            left + 34,
            blockTop + 16,
            withAlpha(accent, 255),
            false
        );

        // Descripción truncada
        String desc = Component.translatable(character.descriptionKey()).getString();
        String truncated = this.font.plainSubstrByWidth(desc, maxWidth - 40);
        graphics.drawString(this.font, truncated, left + 34, blockTop + 26, opaque(0xCEDAEA), false);
    }

    private void renderAbilityList(GuiGraphics graphics, JJKRoster.CharacterDefinition character) {
        List<JJKRoster.TechniqueDefinition> techniques = JJKRoster.techniquesForCharacter(character.id());
        
        int listTop = this.topPos + HEADER_HEIGHT + CHARACTER_BLOCK_HEIGHT + 10;
        int availableHeight = (this.topPos + this.panelHeight - 28) - listTop;
        
        // Ajustar altura de fila si hay muchas habilidades
        int rowH = ABILITY_ROW_HEIGHT;
        int totalNeeded = techniques.size() * (rowH + 2);
        if (totalNeeded > availableHeight && techniques.size() > 0) {
            rowH = Math.max(30, (availableHeight - (techniques.size() - 1) * 2) / techniques.size());
        }
        
        for (int i = 0; i < techniques.size(); i++) {
            int rowTop = listTop + (i * (rowH + 2));
            this.renderAbilityRow(graphics, techniques.get(i), i, rowTop, character.accentColor(), rowH);
        }
    }

    private void renderAbilityRow(GuiGraphics graphics, JJKRoster.TechniqueDefinition technique, int index, int top, int characterAccent, int rowH) {
        int accent = technique.accentColor();
        int left = this.leftPos + PADDING;
        int right = left + this.panelWidth - (PADDING * 2);

        // Fondo de fila
        graphics.fill(left, top, right, top + rowH, 0xD00F141A);
        graphics.fill(left + 1, top + 1, right - 1, top + rowH - 1, 0xEF1A2029);

        // ===== IZQUIERDA: Icono pequeño =====
        int iconY = top + (rowH - ICON_SIZE) / 2;
        int iconLeft = left + 6;

        graphics.fill(iconLeft - 1, iconY - 1, iconLeft + ICON_SIZE + 1, iconY + ICON_SIZE + 1, withAlpha(accent, 100));
        graphics.fill(iconLeft, iconY, iconLeft + ICON_SIZE, iconY + ICON_SIZE, 0xFF0F141A);
        graphics.fill(iconLeft + 2, iconY + 2, iconLeft + ICON_SIZE - 2, iconY + ICON_SIZE - 2, withAlpha(accent, 70));

        // ===== DERECHA: Texto =====
        int textLeft = iconLeft + ICON_SIZE + 8;
        int textWidth = right - textLeft - 8;

        // Nombre
        graphics.drawString(this.font, Component.translatable(technique.nameKey()), textLeft, top + 4, opaque(0xF4F7FF), false);

        // Descripción breve
        int descY = top + 15;
        int maxDescLines = Math.max(1, (rowH - 18) / 9);
        int lines = 0;
        for (FormattedCharSequence line : this.font.split(Component.translatable(technique.descriptionKey()), textWidth)) {
            if (lines >= maxDescLines) break;
            graphics.drawString(this.font, line, textLeft, descY, opaque(0xB8C8DC), false);
            descY += 9;
            lines++;
        }

        // Indicador de estado (puntito de color)
        int statusColor = technique.implemented() ? 0xFF6BFF8A : 0xFFFFB86B;
        graphics.fill(right - 8, top + 4, right - 3, top + 9, statusColor);
    }

    private static int withAlpha(int rgb, int alpha) {
        return ((alpha & 0xFF) << 24) | (rgb & 0xFFFFFF);
    }

    private static int opaque(int rgb) {
        return 0xFF000000 | (rgb & 0xFFFFFF);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
