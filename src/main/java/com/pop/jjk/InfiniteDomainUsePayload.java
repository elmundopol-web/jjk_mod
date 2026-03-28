package com.pop.jjk;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record InfiniteDomainUsePayload() implements CustomPacketPayload {

    public static final InfiniteDomainUsePayload INSTANCE = new InfiniteDomainUsePayload();
    public static final Type<InfiniteDomainUsePayload> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath(JJKMod.MOD_ID, "use_infinite_domain"));
    public static final StreamCodec<RegistryFriendlyByteBuf, InfiniteDomainUsePayload> STREAM_CODEC =
        StreamCodec.unit(INSTANCE);

    @Override
    public Type<InfiniteDomainUsePayload> type() {
        return TYPE;
    }
}
