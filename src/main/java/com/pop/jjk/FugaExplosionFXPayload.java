package com.pop.jjk;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record FugaExplosionFXPayload(double x, double y, double z, float radius) implements CustomPacketPayload {

    public static final Type<FugaExplosionFXPayload> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath(JJKMod.MOD_ID, "fuga_explosion_fx"));

    public static final StreamCodec<RegistryFriendlyByteBuf, FugaExplosionFXPayload> STREAM_CODEC =
        StreamCodec.composite(
            ByteBufCodecs.DOUBLE, FugaExplosionFXPayload::x,
            ByteBufCodecs.DOUBLE, FugaExplosionFXPayload::y,
            ByteBufCodecs.DOUBLE, FugaExplosionFXPayload::z,
            ByteBufCodecs.FLOAT, FugaExplosionFXPayload::radius,
            FugaExplosionFXPayload::new
        );

    @Override
    public Type<FugaExplosionFXPayload> type() {
        return TYPE;
    }
}
