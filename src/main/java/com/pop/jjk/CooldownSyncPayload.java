package com.pop.jjk;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record CooldownSyncPayload(int remainingTicks, int totalTicks) implements CustomPacketPayload {

    public static final Type<CooldownSyncPayload> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath(JJKMod.MOD_ID, "cooldown_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CooldownSyncPayload> STREAM_CODEC =
        StreamCodec.composite(
            ByteBufCodecs.INT,
            CooldownSyncPayload::remainingTicks,
            ByteBufCodecs.INT,
            CooldownSyncPayload::totalTicks,
            CooldownSyncPayload::new
        );

    @Override
    public Type<CooldownSyncPayload> type() {
        return TYPE;
    }
}
