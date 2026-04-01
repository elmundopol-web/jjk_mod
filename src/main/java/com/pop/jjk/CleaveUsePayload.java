package com.pop.jjk;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record CleaveUsePayload() implements CustomPacketPayload {

    public static final CleaveUsePayload INSTANCE = new CleaveUsePayload();
    public static final Type<CleaveUsePayload> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath(JJKMod.MOD_ID, "use_cleave"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CleaveUsePayload> STREAM_CODEC =
        StreamCodec.unit(INSTANCE);

    @Override
    public Type<CleaveUsePayload> type() {
        return TYPE;
    }
}
