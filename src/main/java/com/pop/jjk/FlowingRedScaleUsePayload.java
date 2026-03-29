package com.pop.jjk;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record FlowingRedScaleUsePayload() implements CustomPacketPayload {

    public static final FlowingRedScaleUsePayload INSTANCE = new FlowingRedScaleUsePayload();
    public static final Type<FlowingRedScaleUsePayload> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath(JJKMod.MOD_ID, "use_flowing_red_scale"));
    public static final StreamCodec<RegistryFriendlyByteBuf, FlowingRedScaleUsePayload> STREAM_CODEC =
        StreamCodec.unit(INSTANCE);

    @Override
    public Type<FlowingRedScaleUsePayload> type() {
        return TYPE;
    }
}
