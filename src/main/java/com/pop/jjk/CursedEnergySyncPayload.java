package com.pop.jjk;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record CursedEnergySyncPayload(int currentEnergy, int maxEnergy) implements CustomPacketPayload {

    public static final Type<CursedEnergySyncPayload> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath(JJKMod.MOD_ID, "cursed_energy_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CursedEnergySyncPayload> STREAM_CODEC =
        StreamCodec.composite(
            ByteBufCodecs.INT,
            CursedEnergySyncPayload::currentEnergy,
            ByteBufCodecs.INT,
            CursedEnergySyncPayload::maxEnergy,
            CursedEnergySyncPayload::new
        );

    @Override
    public Type<CursedEnergySyncPayload> type() {
        return TYPE;
    }
}
