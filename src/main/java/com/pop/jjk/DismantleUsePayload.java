package com.pop.jjk;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record DismantleUsePayload() implements CustomPacketPayload {

    public static final DismantleUsePayload INSTANCE = new DismantleUsePayload();
    public static final Type<DismantleUsePayload> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath(JJKMod.MOD_ID, "use_dismantle"));
    public static final StreamCodec<RegistryFriendlyByteBuf, DismantleUsePayload> STREAM_CODEC =
        StreamCodec.unit(INSTANCE);

    @Override
    public Type<DismantleUsePayload> type() {
        return TYPE;
    }
}
