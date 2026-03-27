package com.pop.jjk;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record CharacterSelectionPayload(String characterId) implements CustomPacketPayload {

    public static final Type<CharacterSelectionPayload> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath(JJKMod.MOD_ID, "select_character"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CharacterSelectionPayload> STREAM_CODEC =
        StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8,
            CharacterSelectionPayload::characterId,
            CharacterSelectionPayload::new
        );

    @Override
    public Type<CharacterSelectionPayload> type() {
        return TYPE;
    }
}
