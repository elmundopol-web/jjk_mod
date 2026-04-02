package com.pop.jjk;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record FugaUsePayload() implements CustomPacketPayload {

    public static final FugaUsePayload INSTANCE = new FugaUsePayload();
    public static final Type<FugaUsePayload> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath(JJKMod.MOD_ID, "use_fuga"));
    public static final StreamCodec<RegistryFriendlyByteBuf, FugaUsePayload> STREAM_CODEC =
        StreamCodec.unit(INSTANCE);

    @Override
    public Type<FugaUsePayload> type() {
        return TYPE;
    }
}
