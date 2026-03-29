package com.pop.jjk;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record PiercingBloodHoldPayload(boolean holding) implements CustomPacketPayload {

    public static final Type<PiercingBloodHoldPayload> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath(JJKMod.MOD_ID, "hold_piercing_blood"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PiercingBloodHoldPayload> STREAM_CODEC =
        StreamCodec.composite(
            ByteBufCodecs.BOOL,
            PiercingBloodHoldPayload::holding,
            PiercingBloodHoldPayload::new
        );

    @Override
    public Type<PiercingBloodHoldPayload> type() {
        return TYPE;
    }
}
