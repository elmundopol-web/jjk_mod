package com.pop.jjk;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record DivergingFistUsePayload() implements CustomPacketPayload {

    public static final DivergingFistUsePayload INSTANCE = new DivergingFistUsePayload();
    public static final Type<DivergingFistUsePayload> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath(JJKMod.MOD_ID, "use_diverging_fist"));
    public static final StreamCodec<RegistryFriendlyByteBuf, DivergingFistUsePayload> STREAM_CODEC =
        StreamCodec.unit(INSTANCE);

    @Override
    public Type<DivergingFistUsePayload> type() {
        return TYPE;
    }
}
