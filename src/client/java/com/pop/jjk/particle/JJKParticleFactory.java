package com.pop.jjk.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.RandomSource;

/**
 * Factory genérica que delega la creación a un {@link ParticleConstructor}.
 *
 * Esto evita duplicar factories para cada tipo de partícula.
 * Cada partícula concreta registra su constructor como lambda:
 *
 * <pre>
 * ParticleFactoryRegistry.getInstance().register(
 *     JJKParticles.CURSED_ENERGY,
 *     sprites -> new JJKParticleFactory(sprites, CursedEnergyParticle::new)
 * );
 * </pre>
 */
public final class JJKParticleFactory implements ParticleProvider<SimpleParticleType> {

    private final SpriteSet sprites;
    private final ParticleConstructor constructor;

    public JJKParticleFactory(SpriteSet sprites, ParticleConstructor constructor) {
        this.sprites = sprites;
        this.constructor = constructor;
    }

    @Override
    public Particle createParticle(SimpleParticleType type, ClientLevel level,
                                   double x, double y, double z,
                                   double vx, double vy, double vz,
                                   RandomSource random) {
        return constructor.create(level, x, y, z, vx, vy, vz, sprites);
    }

    @FunctionalInterface
    public interface ParticleConstructor {
        JJKBaseParticle create(ClientLevel level, double x, double y, double z,
                               double vx, double vy, double vz, SpriteSet sprites);
    }
}
