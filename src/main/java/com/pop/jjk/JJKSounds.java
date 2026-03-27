package com.pop.jjk;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;

public final class JJKSounds {

    public static final SoundEvent PURPLE_IMPACT = register("purple_impact");

    private static SoundEvent register(String name) {
        Identifier id = Identifier.fromNamespaceAndPath(JJKMod.MOD_ID, name);
        return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
    }

    public static void init() {
    }
}
