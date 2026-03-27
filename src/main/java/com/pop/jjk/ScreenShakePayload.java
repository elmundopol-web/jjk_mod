package com.pop.jjk;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record ScreenShakePayload(float intensity, int durationTicks) implements CustomPacketPayload {

    public static final Type<ScreenShakePayload> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath(JJKMod.MOD_ID, "screen_shake"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ScreenShakePayload> STREAM_CODEC =
        StreamCodec.composite(
            ByteBufCodecs.FLOAT,
            ScreenShakePayload::intensity,
            ByteBufCodecs.INT,
            ScreenShakePayload::durationTicks,
            ScreenShakePayload::new
        );

    @Override
    public Type<ScreenShakePayload> type() {
        return TYPE;
    }
}
