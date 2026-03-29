package com.pop.jjk;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record PiercingBloodUsePayload() implements CustomPacketPayload {

    public static final PiercingBloodUsePayload INSTANCE = new PiercingBloodUsePayload();
    public static final Type<PiercingBloodUsePayload> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath(JJKMod.MOD_ID, "use_piercing_blood"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PiercingBloodUsePayload> STREAM_CODEC =
        StreamCodec.unit(INSTANCE);

    @Override
    public Type<PiercingBloodUsePayload> type() {
        return TYPE;
    }
}
