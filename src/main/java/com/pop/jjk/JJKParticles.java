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

    // Blood (Choso)
    public static final SimpleParticleType BLOOD_CORE =
        FabricParticleTypes.simple();
    public static final SimpleParticleType BLOOD_TRAIL =
        FabricParticleTypes.simple();
    public static final SimpleParticleType BLOOD_EXPLOSION =
        FabricParticleTypes.simple();

    // Fire (Fuga)
    public static final SimpleParticleType FIRE_CHARGE =
        FabricParticleTypes.simple();
    public static final SimpleParticleType FIRE_BEAM =
        FabricParticleTypes.simple();
    public static final SimpleParticleType FIRE_TRAIL =
        FabricParticleTypes.simple();
    public static final SimpleParticleType FIRE_EXPLOSION =
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
        // Blood
        register("blood_core", BLOOD_CORE);
        register("blood_trail", BLOOD_TRAIL);
        register("blood_explosion", BLOOD_EXPLOSION);
        // Fire (Fuga)
        register("fire_charge", FIRE_CHARGE);
        register("fire_beam", FIRE_BEAM);
        register("fire_trail", FIRE_TRAIL);
        register("fire_explosion", FIRE_EXPLOSION);
    }

    private static void register(String name, SimpleParticleType type) {
        Registry.register(
            BuiltInRegistries.PARTICLE_TYPE,
            Identifier.fromNamespaceAndPath(JJKMod.MOD_ID, name),
            type
        );
    }
}
