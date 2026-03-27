package com.pop.jjk;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record TechniqueSelectionPayload(String activeTechniqueId, boolean infinityEnabled) implements CustomPacketPayload {

    public static final Type<TechniqueSelectionPayload> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath(JJKMod.MOD_ID, "select_technique"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TechniqueSelectionPayload> STREAM_CODEC =
        StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8,
            TechniqueSelectionPayload::activeTechniqueId,
            ByteBufCodecs.BOOL,
            TechniqueSelectionPayload::infinityEnabled,
            TechniqueSelectionPayload::new
        );

    @Override
    public Type<TechniqueSelectionPayload> type() {
        return TYPE;
    }
}
