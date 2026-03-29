package com.pop.jjk;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record SupernovaUsePayload() implements CustomPacketPayload {

    public static final SupernovaUsePayload INSTANCE = new SupernovaUsePayload();
    public static final Type<SupernovaUsePayload> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath(JJKMod.MOD_ID, "use_supernova"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SupernovaUsePayload> STREAM_CODEC =
        StreamCodec.unit(INSTANCE);

    @Override
    public Type<SupernovaUsePayload> type() {
        return TYPE;
    }
}
