package com.pop.jjk;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

public class CharacterSelectionScreen extends Screen {

    private static final int PANEL_WIDTH = 418;
    private static final int PANEL_HEIGHT = 246;
    private static final int ENTRY_WIDTH = 150;
    private static final int ENTRY_HEIGHT = 26;
    private static final int ENTRY_SPACING = 8;

    private final List<CharacterButtonData> characterButtons = new ArrayList<>();
    private int leftPos;
    private int topPos;

    public CharacterSelectionScreen() {
        super(Component.translatable("screen.jjk.character_title"));
    }

    @Override
    protected void init() {
        this.leftPos = (this.width - PANEL_WIDTH) / 2;
        this.topPos = (this.height - PANEL_HEIGHT) / 2;
        this.characterButtons.clear();

        int listLeft = this.leftPos + 20;
        int listTop = this.topPos + 54;

        for (int index = 0; index < JJKRoster.characters().size(); index++) {
            JJKRoster.CharacterDefinition character = JJKRoster.characters().get(index);
            int x = listLeft;
            int y = listTop + (index * (ENTRY_HEIGHT + ENTRY_SPACING));

            Button button = Button.builder(
                Component.translatable(character.nameKey()),
                widget -> this.selectCharacter(character.id())
            ).bounds(x, y, ENTRY_WIDTH, ENTRY_HEIGHT).build();
            this.addRenderableWidget(button);
            this.characterButtons.add(new CharacterButtonData(character, button));
        }

        if (!JJKClientMod.isCharacterSelectionPending()) {
            this.addRenderableWidget(Button.builder(
                Component.translatable("button.jjk.back"),
                button -> this.onClose()
            ).bounds(this.leftPos + PANEL_WIDTH - 90, this.topPos + PANEL_HEIGHT - 30, 72, 20).build());
        }
    }

    private void selectCharacter(String characterId) {
        if (this.minecraft == null) {
            return;
        }

        JJKClientMod.confirmCharacterSelection(characterId, this.minecraft);
        this.onClose();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        CharacterButtonData previewData = this.getPreviewCharacter(mouseX, mouseY);
        int previewLeft = this.leftPos + 190;
        int previewTop = this.topPos + 54;
        int previewWidth = PANEL_WIDTH - 210;

        graphics.fill(0, 0, this.width, this.height, 0xB0060A10);
        graphics.fill(this.leftPos - 3, this.topPos - 3, this.leftPos + PANEL_WIDTH + 3, this.topPos + PANEL_HEIGHT + 3, 0x42000000);
        graphics.fill(this.leftPos, this.topPos, this.leftPos + PANEL_WIDTH, this.topPos + PANEL_HEIGHT, 0xF011151C);
        graphics.fill(this.leftPos + 2, this.topPos + 2, this.leftPos + PANEL_WIDTH - 2, this.topPos + PANEL_HEIGHT - 2, 0xF71A2029);
        graphics.fill(this.leftPos + 2, this.topPos + 2, this.leftPos + PANEL_WIDTH - 2, this.topPos + 42, 0xFF1B2840);
        graphics.fill(this.leftPos + 174, this.topPos + 18, this.leftPos + 176, this.topPos + PANEL_HEIGHT - 18, 0x4036485B);
        graphics.fill(previewLeft, previewTop, previewLeft + previewWidth, previewTop + 154, 0x930B1017);
        graphics.fill(previewLeft + 10, previewTop + 114, previewLeft + previewWidth - 10, previewTop + 116, withAlpha(previewData.character().accentColor(), 170));

        graphics.drawString(this.font, Component.translatable("screen.jjk.character_title"), this.leftPos + 18, this.topPos + 12, 0xF4F7FF, false);
        graphics.drawString(this.font, Component.translatable("screen.jjk.character_subtitle"), this.leftPos + 18, this.topPos + 26, 0x8FA6C7, false);
        graphics.drawString(this.font, Component.translatable("screen.jjk.character_list_label"), this.leftPos + 20, this.topPos + 44, 0xA6BCD9, false);

        this.renderCharacterDecorations(graphics, mouseX, mouseY);
        this.renderPreview(graphics, previewData, previewLeft, previewTop, previewWidth);

        super.render(graphics, mouseX, mouseY, delta);
    }

    private void renderCharacterDecorations(GuiGraphics graphics, int mouseX, int mouseY) {
        String selectedId = JJKClientMod.getSelectedCharacterId();

        for (CharacterButtonData buttonData : this.characterButtons) {
            Button button = buttonData.button();
            int accent = buttonData.character().accentColor();
            boolean selected = buttonData.character().id().equals(selectedId);
            boolean hovered = button.isMouseOver(mouseX, mouseY);

            graphics.fill(
                button.getX() - 5,
                button.getY() - 3,
                button.getX() + ENTRY_WIDTH + 5,
                button.getY() + ENTRY_HEIGHT + 3,
                selected ? withAlpha(accent, 215) : hovered ? withAlpha(accent, 120) : 0x2D283241
            );
            graphics.fill(
                button.getX() - 4,
                button.getY() - 2,
                button.getX() + ENTRY_WIDTH + 4,
                button.getY() + ENTRY_HEIGHT + 2,
                selected ? 0x4D111A24 : hovered ? 0x38131C26 : 0x23121920
            );
        }
    }

    private void renderPreview(GuiGraphics graphics, CharacterButtonData previewData, int left, int top, int width) {
        JJKRoster.CharacterDefinition character = previewData.character();
        int accent = character.accentColor();
        int descriptionTop = top + 10;
        int techniquesTop = top + 96;
        int infoLeft = left + 18;

        graphics.fill(left + 18, top + 14, left + 52, top + 48, withAlpha(accent, 60));
        graphics.fill(left + 24, top + 20, left + 46, top + 42, withAlpha(accent, 145));
        graphics.fill(left + 30, top + 26, left + 40, top + 36, withAlpha(accent, 255));

        graphics.drawString(this.font, Component.translatable(character.nameKey()), left + 66, top + 16, 0xF4F7FF, false);
        graphics.drawString(
            this.font,
            Component.translatable(character.supportsInfinity() ? "screen.jjk.character_trait_limitless" : "screen.jjk.character_trait_striker"),
            left + 66,
            top + 30,
            withAlpha(accent, 255),
            false
        );

        for (FormattedCharSequence line : this.font.split(Component.translatable(character.descriptionKey()), width - 36)) {
            graphics.drawString(this.font, line, infoLeft, descriptionTop + 48, 0xC8D4E7, false);
            descriptionTop += 11;
        }

        graphics.drawString(this.font, Component.translatable("screen.jjk.character_hotbar_label"), infoLeft, techniquesTop, 0xA6BCD9, false);

        int slotTop = techniquesTop + 16;
        List<JJKRoster.TechniqueDefinition> techniques = JJKRoster.techniquesForCharacter(character.id());
        for (int index = 0; index < techniques.size(); index++) {
            JJKRoster.TechniqueDefinition technique = techniques.get(index);
            int slotLeft = infoLeft + (index * 56);
            int slotAccent = technique.accentColor();

            graphics.fill(slotLeft, slotTop, slotLeft + 46, slotTop + 36, 0xAA11161F);
            graphics.fill(slotLeft + 2, slotTop + 2, slotLeft + 44, slotTop + 34, withAlpha(slotAccent, 80));
            graphics.fill(slotLeft + 10, slotTop + 10, slotLeft + 36, slotTop + 26, withAlpha(slotAccent, 210));
            graphics.drawCenteredString(this.font, Component.literal(Integer.toString(index + 1)), slotLeft + 23, slotTop + 40, 0x8299BC);
        }
    }

    private CharacterButtonData getPreviewCharacter(int mouseX, int mouseY) {
        for (CharacterButtonData buttonData : this.characterButtons) {
            if (buttonData.button().isMouseOver(mouseX, mouseY)) {
                return buttonData;
            }
        }

        String selectedId = JJKClientMod.getSelectedCharacterId();
        for (CharacterButtonData buttonData : this.characterButtons) {
            if (buttonData.character().id().equals(selectedId)) {
                return buttonData;
            }
        }

        return this.characterButtons.getFirst();
    }

    private static int withAlpha(int rgb, int alpha) {
        return ((alpha & 0xFF) << 24) | (rgb & 0xFFFFFF);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return !JJKClientMod.isCharacterSelectionPending();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private record CharacterButtonData(JJKRoster.CharacterDefinition character, Button button) {
    }
}
