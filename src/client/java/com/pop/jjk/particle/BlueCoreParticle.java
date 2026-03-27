package com.pop.jjk.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SpriteSet;

/**
 * Partícula de núcleo del Gravity Well.
 *
 * Comportamiento:
 * - Se mantiene cerca del centro con mínimo desplazamiento
 * - Pulso de tamaño sinusoidal (respira)
 * - Color blanco-azul brillante, fullbright
 * - Ligera inestabilidad (wobble) para sensación de energía contenida
 * - Lifetime corto, se respawnea constantemente desde GravityWellEffect
 */
public final class BlueCoreParticle extends JJKBaseParticle {

    private static final JJKParticleConfig CONFIG = JJKParticleConfig.builder()
        .colorGradient(0.7F, 0.9F, 1.0F,      // blanco-azul brillante
                       0.4F, 0.65F, 1.0F)      // azul medio
        .scale(0.22F, 0.08F)
        .alpha(0.95F, 0.0F)
        .lifetime(8, 14)
        .gravity(0.0F)
        .friction(0.85F)
        .spin(0.12F)
        .fullBright(true)
        .physics(false)
        .build();

    private final float pulsePhase;
    private final float pulseSpeed;
    private final float wobbleAmplitude;

    public BlueCoreParticle(ClientLevel level, double x, double y, double z,
                             double vx, double vy, double vz, SpriteSet sprites) {
        super(level, x, y, z, vx, vy, vz, sprites, CONFIG);

        this.pulsePhase = this.random.nextFloat() * 6.2832F;
        this.pulseSpeed = 0.4F + this.random.nextFloat() * 0.2F;
        this.wobbleAmplitude = 0.003F + this.random.nextFloat() * 0.004F;
    }

    @Override
    protected void tickMovement(float progress) {
        // Pulso de tamaño: la partícula "respira"
        float pulse = (float) Math.sin(this.age * pulseSpeed + pulsePhase);
        this.quadSize = baseQuadSize * (1.0F + pulse * 0.25F) * (1.0F - progress * 0.3F);

        // Wobble sutil — inestabilidad contenida, no caos
        float time = this.age * 0.3F;
        this.xd += Math.sin(time * 2.1F + pulsePhase) * wobbleAmplitude;
        this.yd += Math.cos(time * 1.7F + pulsePhase * 0.7F) * wobbleAmplitude * 0.6;
        this.zd += Math.sin(time * 1.9F + pulsePhase * 1.3F) * wobbleAmplitude;
    }
}
