package com.pop.jjk;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record MalevolentShrineSyncPayload(
    int ownerEntityId,
    double centerX,
    double centerY,
    double centerZ,
    float radius,
    int remainingTicks,
    boolean active
) implements CustomPacketPayload {

    public static final Type<MalevolentShrineSyncPayload> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath(JJKMod.MOD_ID, "malevolent_shrine_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, MalevolentShrineSyncPayload> STREAM_CODEC =
        StreamCodec.of(
            (buf, payload) -> {
                buf.writeVarInt(payload.ownerEntityId());
                buf.writeDouble(payload.centerX());
                buf.writeDouble(payload.centerY());
                buf.writeDouble(payload.centerZ());
                buf.writeFloat(payload.radius());
                buf.writeVarInt(payload.remainingTicks());
                buf.writeBoolean(payload.active());
            },
            buf -> new MalevolentShrineSyncPayload(
                buf.readVarInt(),
                buf.readDouble(),
                buf.readDouble(),
                buf.readDouble(),
                buf.readFloat(),
                buf.readVarInt(),
                buf.readBoolean()
            )
        );

    @Override
    public Type<MalevolentShrineSyncPayload> type() {
        return TYPE;
    }
}
