package com.pop.jjk;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record RedTechniqueUsePayload() implements CustomPacketPayload {

    public static final RedTechniqueUsePayload INSTANCE = new RedTechniqueUsePayload();
    public static final Type<RedTechniqueUsePayload> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath(JJKMod.MOD_ID, "use_red"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RedTechniqueUsePayload> STREAM_CODEC =
        StreamCodec.unit(INSTANCE);

    @Override
    public Type<RedTechniqueUsePayload> type() {
        return TYPE;
    }
}
