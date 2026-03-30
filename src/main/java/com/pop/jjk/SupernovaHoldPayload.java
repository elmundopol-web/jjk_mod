package com.pop.jjk;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record SupernovaHoldPayload(boolean holding) implements CustomPacketPayload {

    public static final Type<SupernovaHoldPayload> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath(JJKMod.MOD_ID, "hold_supernova"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SupernovaHoldPayload> STREAM_CODEC =
        StreamCodec.composite(
            ByteBufCodecs.BOOL,
            SupernovaHoldPayload::holding,
            SupernovaHoldPayload::new
        );

    @Override
    public Type<SupernovaHoldPayload> type() {
        return TYPE;
    }
}
