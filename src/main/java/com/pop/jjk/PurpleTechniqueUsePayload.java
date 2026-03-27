package com.pop.jjk;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record PurpleTechniqueUsePayload() implements CustomPacketPayload {

    public static final PurpleTechniqueUsePayload INSTANCE = new PurpleTechniqueUsePayload();
    public static final Type<PurpleTechniqueUsePayload> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath(JJKMod.MOD_ID, "use_purple"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PurpleTechniqueUsePayload> STREAM_CODEC =
        StreamCodec.unit(INSTANCE);

    @Override
    public Type<PurpleTechniqueUsePayload> type() {
        return TYPE;
    }
}
