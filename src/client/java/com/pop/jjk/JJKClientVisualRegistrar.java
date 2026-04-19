package com.pop.jjk;

import net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry;
import net.minecraft.client.renderer.entity.EntityRenderers;

final class JJKClientVisualRegistrar {

    private JJKClientVisualRegistrar() {
    }

    static void registerAll() {
        registerEntityRenderers();
        registerParticles();
    }

    private static void registerEntityRenderers() {
        EntityRenderers.register(JJKMod.BLUE_ORB, BlueOrbRenderer::new);
        EntityRenderers.register(JJKMod.RED_PROJECTILE, RedProjectileRenderer::new);
        EntityRenderers.register(JJKMod.PURPLE_PROJECTILE, PurpleProjectileRenderer::new);
        EntityRenderers.register(JJKMod.SUPERNOVA_ORB_PROJECTILE, SupernovaOrbRenderer::new);
        EntityRenderers.register(JJKMod.DISMANTLE_PROJECTILE, DismantleProjectileRenderer::new);
        EntityRenderers.register(JJKMod.FUGA_PROJECTILE, FugaProjectileRenderer::new);
    }

    private static void registerParticles() {
        ParticleFactoryRegistry.getInstance().register(JJKParticles.BLUE_ENERGY,
            sprites -> new com.pop.jjk.particle.JJKParticleFactory(sprites, com.pop.jjk.particle.BlueEnergyParticle::new));
        ParticleFactoryRegistry.getInstance().register(JJKParticles.CURSED_ENERGY,
            sprites -> new com.pop.jjk.particle.JJKParticleFactory(sprites, com.pop.jjk.particle.CursedEnergyParticle::new));
        ParticleFactoryRegistry.getInstance().register(JJKParticles.BLUE_CORE,
            sprites -> new com.pop.jjk.particle.JJKParticleFactory(sprites, com.pop.jjk.particle.BlueCoreParticle::new));
        ParticleFactoryRegistry.getInstance().register(JJKParticles.BLUE_ORBITAL,
            sprites -> new com.pop.jjk.particle.JJKParticleFactory(sprites, com.pop.jjk.particle.BlueOrbitalParticle::new));
        ParticleFactoryRegistry.getInstance().register(JJKParticles.BLUE_ABSORBED,
            sprites -> new com.pop.jjk.particle.JJKParticleFactory(sprites, com.pop.jjk.particle.BlueAbsorbedParticle::new));
        ParticleFactoryRegistry.getInstance().register(JJKParticles.BLUE_ORB_CORE,
            sprites -> new com.pop.jjk.particle.JJKParticleFactory(sprites, com.pop.jjk.particle.BlueOrbCoreParticle::new));
        ParticleFactoryRegistry.getInstance().register(JJKParticles.BLUE_ORB_SPARK,
            sprites -> new com.pop.jjk.particle.JJKParticleFactory(sprites, com.pop.jjk.particle.BlueOrbSparkParticle::new));
        ParticleFactoryRegistry.getInstance().register(JJKParticles.BLUE_ORB_ATTRACTION,
            sprites -> new com.pop.jjk.particle.JJKParticleFactory(sprites, com.pop.jjk.particle.BlueOrbAttractionParticle::new));
        ParticleFactoryRegistry.getInstance().register(JJKParticles.BLOOD_CORE,
            sprites -> new com.pop.jjk.particle.JJKParticleFactory(sprites, com.pop.jjk.particle.BloodCoreParticle::new));
        ParticleFactoryRegistry.getInstance().register(JJKParticles.BLOOD_TRAIL,
            sprites -> new com.pop.jjk.particle.JJKParticleFactory(sprites, com.pop.jjk.particle.BloodTrailParticle::new));
        ParticleFactoryRegistry.getInstance().register(JJKParticles.BLOOD_EXPLOSION,
            sprites -> new com.pop.jjk.particle.JJKParticleFactory(sprites, com.pop.jjk.particle.BloodExplosionParticle::new));
        ParticleFactoryRegistry.getInstance().register(JJKParticles.FIRE_CHARGE,
            sprites -> new com.pop.jjk.particle.JJKParticleFactory(sprites, com.pop.jjk.particle.FireChargeParticle::new));
        ParticleFactoryRegistry.getInstance().register(JJKParticles.FIRE_BEAM,
            sprites -> new com.pop.jjk.particle.JJKParticleFactory(sprites, com.pop.jjk.particle.FireBeamParticle::new));
        ParticleFactoryRegistry.getInstance().register(JJKParticles.FIRE_TRAIL,
            sprites -> new com.pop.jjk.particle.JJKParticleFactory(sprites, com.pop.jjk.particle.FireTrailParticle::new));
        ParticleFactoryRegistry.getInstance().register(JJKParticles.FIRE_EXPLOSION,
            sprites -> new com.pop.jjk.particle.JJKParticleFactory(sprites, com.pop.jjk.particle.FireExplosionParticle::new));
    }
}
