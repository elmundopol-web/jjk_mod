package com.pop.jjk;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record FugaOverchargeSyncPayload(int durationTicks) implements CustomPacketPayload {

    public static final Type<FugaOverchargeSyncPayload> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath(JJKMod.MOD_ID, "fuga_overcharge_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, FugaOverchargeSyncPayload> STREAM_CODEC =
        StreamCodec.composite(
            ByteBufCodecs.VAR_INT, FugaOverchargeSyncPayload::durationTicks,
            FugaOverchargeSyncPayload::new
        );

    @Override
    public Type<FugaOverchargeSyncPayload> type() {
        return TYPE;
    }
}
