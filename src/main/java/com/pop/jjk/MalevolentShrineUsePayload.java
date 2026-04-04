package com.pop.jjk;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record MalevolentShrineUsePayload() implements CustomPacketPayload {

    public static final MalevolentShrineUsePayload INSTANCE = new MalevolentShrineUsePayload();
    public static final Type<MalevolentShrineUsePayload> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath(JJKMod.MOD_ID, "use_malevolent_shrine"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MalevolentShrineUsePayload> STREAM_CODEC =
        StreamCodec.unit(INSTANCE);

    @Override
    public Type<MalevolentShrineUsePayload> type() {
        return TYPE;
    }
}
