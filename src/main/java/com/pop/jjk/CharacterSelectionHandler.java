package com.pop.jjk;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;

public final class CharacterSelectionHandler {

    private static final String CHARACTER_TAG_PREFIX = "jjk_character_";

    private CharacterSelectionHandler() {
    }

    public static String getSelectedCharacter(ServerPlayer player) {
        for (String tag : player.getTags()) {
            if (tag.startsWith(CHARACTER_TAG_PREFIX)) {
                return tag.substring(CHARACTER_TAG_PREFIX.length());
            }
        }

        return JJKRoster.NONE;
    }

    public static boolean hasSelectedCharacter(ServerPlayer player) {
        return !JJKRoster.NONE.equals(getSelectedCharacter(player));
    }

    public static boolean setSelectedCharacter(ServerPlayer player, String characterId) {
        if (!JJKRoster.isValidCharacter(characterId)) {
            return false;
        }

        for (String tag : player.getTags()) {
            if (tag.startsWith(CHARACTER_TAG_PREFIX)) {
                player.removeTag(tag);
            }
        }

        player.addTag(CHARACTER_TAG_PREFIX + characterId);
        syncCharacterToClient(player);
        return true;
    }

    public static void syncCharacterToClient(ServerPlayer player) {
        ServerPlayNetworking.send(player, new CharacterStatePayload(getSelectedCharacter(player)));
    }
}
