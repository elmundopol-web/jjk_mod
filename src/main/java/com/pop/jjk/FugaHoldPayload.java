package com.pop.jjk;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record FugaHoldPayload(boolean holding) implements CustomPacketPayload {

    public static final Type<FugaHoldPayload> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath(JJKMod.MOD_ID, "hold_fuga"));

    public static final StreamCodec<RegistryFriendlyByteBuf, FugaHoldPayload> STREAM_CODEC =
        StreamCodec.composite(
            ByteBufCodecs.BOOL,
            FugaHoldPayload::holding,
            FugaHoldPayload::new
        );

    @Override
    public Type<FugaHoldPayload> type() {
        return TYPE;
    }
}
