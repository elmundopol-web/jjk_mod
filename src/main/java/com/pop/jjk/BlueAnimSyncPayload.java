package com.pop.jjk;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * S2C sync de animación del jugador para la técnica Blue.
 *
 * No replica un timeline completo: solo envía la FASE.
 * El cliente genera el timing local:
 * - INVOKE: one-shot corto
 * - CHARGING: loop
 * - LAUNCH: one-shot corto
 * - STOP: reset
 */
public record BlueAnimSyncPayload(int entityId, int phase) implements CustomPacketPayload {

    public static final int PHASE_STOP = 0;
    public static final int PHASE_INVOKE = 1;
    public static final int PHASE_CHARGING = 2;
    public static final int PHASE_LAUNCH = 3;

    public static final Type<BlueAnimSyncPayload> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath(JJKMod.MOD_ID, "blue_anim"));

    public static final StreamCodec<RegistryFriendlyByteBuf, BlueAnimSyncPayload> STREAM_CODEC =
        StreamCodec.of(
            (buf, payload) -> {
                buf.writeVarInt(payload.entityId());
                buf.writeVarInt(payload.phase());
            },
            buf -> new BlueAnimSyncPayload(buf.readVarInt(), buf.readVarInt())
        );

    @Override
    public Type<BlueAnimSyncPayload> type() {
        return TYPE;
    }
}
