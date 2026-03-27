package com.pop.jjk;

import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;

public final class JJKParticles {

    public static final SimpleParticleType BLUE_ENERGY =
        FabricParticleTypes.simple();

    public static final SimpleParticleType CURSED_ENERGY =
        FabricParticleTypes.simple();

    public static final SimpleParticleType BLUE_CORE =
        FabricParticleTypes.simple();

    public static final SimpleParticleType BLUE_ORBITAL =
        FabricParticleTypes.simple();

    public static final SimpleParticleType BLUE_ABSORBED =
        FabricParticleTypes.simple();

    public static final SimpleParticleType BLUE_ORB_CORE =
        FabricParticleTypes.simple();

    public static final SimpleParticleType BLUE_ORB_SPARK =
        FabricParticleTypes.simple();

    public static final SimpleParticleType BLUE_ORB_ATTRACTION =
        FabricParticleTypes.simple();

    public static void init() {
        register("blue_energy", BLUE_ENERGY);
        register("cursed_energy", CURSED_ENERGY);
        register("blue_core", BLUE_CORE);
        register("blue_orbital", BLUE_ORBITAL);
        register("blue_absorbed", BLUE_ABSORBED);
        register("blue_orb_core", BLUE_ORB_CORE);
        register("blue_orb_spark", BLUE_ORB_SPARK);
        register("blue_orb_attraction", BLUE_ORB_ATTRACTION);
    }

    private static void register(String name, SimpleParticleType type) {
        Registry.register(
            BuiltInRegistries.PARTICLE_TYPE,
            Identifier.fromNamespaceAndPath(JJKMod.MOD_ID, name),
            type
        );
    }
}
