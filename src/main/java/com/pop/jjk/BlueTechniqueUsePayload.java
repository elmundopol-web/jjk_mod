package com.pop.jjk;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record BlueTechniqueUsePayload(boolean holding) implements CustomPacketPayload {

    public static final Type<BlueTechniqueUsePayload> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath(JJKMod.MOD_ID, "use_blue"));
    public static final StreamCodec<RegistryFriendlyByteBuf, BlueTechniqueUsePayload> STREAM_CODEC =
        StreamCodec.composite(
            ByteBufCodecs.BOOL,
            BlueTechniqueUsePayload::holding,
            BlueTechniqueUsePayload::new
        );

    @Override
    public Type<BlueTechniqueUsePayload> type() {
        return TYPE;
    }
}
