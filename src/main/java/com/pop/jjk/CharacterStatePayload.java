package com.pop.jjk;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record CharacterStatePayload(String characterId) implements CustomPacketPayload {

    public static final Type<CharacterStatePayload> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath(JJKMod.MOD_ID, "character_state"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CharacterStatePayload> STREAM_CODEC =
        StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8,
            CharacterStatePayload::characterId,
            CharacterStatePayload::new
        );

    @Override
    public Type<CharacterStatePayload> type() {
        return TYPE;
    }
}
