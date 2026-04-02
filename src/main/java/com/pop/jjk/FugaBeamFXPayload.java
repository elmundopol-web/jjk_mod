package com.pop.jjk;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record FugaBeamFXPayload(double x1, double y1, double z1,
                                double x2, double y2, double z2) implements CustomPacketPayload {

    public static final Type<FugaBeamFXPayload> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath(JJKMod.MOD_ID, "fuga_beam_fx"));

    public static final StreamCodec<RegistryFriendlyByteBuf, FugaBeamFXPayload> STREAM_CODEC =
        StreamCodec.composite(
            ByteBufCodecs.DOUBLE, FugaBeamFXPayload::x1,
            ByteBufCodecs.DOUBLE, FugaBeamFXPayload::y1,
            ByteBufCodecs.DOUBLE, FugaBeamFXPayload::z1,
            ByteBufCodecs.DOUBLE, FugaBeamFXPayload::x2,
            ByteBufCodecs.DOUBLE, FugaBeamFXPayload::y2,
            ByteBufCodecs.DOUBLE, FugaBeamFXPayload::z2,
            FugaBeamFXPayload::new
        );

    @Override
    public Type<FugaBeamFXPayload> type() {
        return TYPE;
    }
}
