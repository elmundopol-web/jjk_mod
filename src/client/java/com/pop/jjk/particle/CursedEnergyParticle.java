package com.pop.jjk.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SpriteSet;

/**
 * Partícula de energía maldita inspirada en Jujutsu Kaisen.
 *
 * Comportamiento:
 * - Movimiento orgánico basado en ruido senoidal multi-frecuencia
 * - Ligera atracción hacia el punto de origen (simula cohesión de energía)
 * - Color degradado violeta oscuro → negro con variación aleatoria
 * - Fade progresivo con grow/shrink
 * - Fullbright para efecto de emisión propia
 */
public final class CursedEnergyParticle extends JJKBaseParticle {

    private static final JJKParticleConfig CONFIG = JJKParticleConfig.builder()
        .colorGradient(0.35F, 0.08F, 0.55F,   // violeta oscuro
                       0.08F, 0.02F, 0.12F)    // casi negro
        .scale(0.14F, 0.02F)
        .alpha(0.85F, 0.0F)
        .lifetime(14, 26)
        .gravity(-0.008F)
        .friction(0.94F)
        .spin(0.06F)
        .fullBright(true)
        .physics(false)
        .build();

    private final double originX;
    private final double originY;
    private final double originZ;
    private final float noisePhaseX;
    private final float noisePhaseY;
    private final float noisePhaseZ;
    private final float noiseAmplitude;
    private final float attractionStrength;

    public CursedEnergyParticle(ClientLevel level, double x, double y, double z,
                                 double vx, double vy, double vz, SpriteSet sprites) {
        super(level, x, y, z, vx, vy, vz, sprites, CONFIG);
        this.originX = x;
        this.originY = y;
        this.originZ = z;

        // Fase aleatoria para que cada partícula oscile de forma diferente
        this.noisePhaseX = this.random.nextFloat() * 6.2832F;
        this.noisePhaseY = this.random.nextFloat() * 6.2832F;
        this.noisePhaseZ = this.random.nextFloat() * 6.2832F;
        this.noiseAmplitude = 0.006F + this.random.nextFloat() * 0.012F;
        this.attractionStrength = 0.015F + this.random.nextFloat() * 0.02F;

        // Variación de color individual (más violeta o más oscuro)
        float colorShift = (this.random.nextFloat() - 0.5F) * 0.12F;
        this.rCol += colorShift;
        this.gCol += colorShift * 0.3F;
        this.bCol += colorShift * 0.6F;
    }

    @Override
    protected void tickMovement(float progress) {
        float time = this.age * 0.15F;
        float decay = 1.0F - progress * 0.5F;

        // Ruido multi-frecuencia para movimiento orgánico (no lineal, no predecible)
        // Combina dos frecuencias para romper la periodicidad
        float noiseX = (float)(Math.sin(time * 1.7F + noisePhaseX) * 0.7
                             + Math.sin(time * 3.1F + noisePhaseX * 2.3) * 0.3);
        float noiseY = (float)(Math.sin(time * 1.3F + noisePhaseY) * 0.6
                             + Math.cos(time * 2.7F + noisePhaseY * 1.8) * 0.4);
        float noiseZ = (float)(Math.sin(time * 1.9F + noisePhaseZ) * 0.7
                             + Math.sin(time * 2.3F + noisePhaseZ * 2.1) * 0.3);

        this.xd += noiseX * noiseAmplitude * decay;
        this.yd += noiseY * noiseAmplitude * decay;
        this.zd += noiseZ * noiseAmplitude * decay;

        // Atracción suave al origen (la energía se mantiene cohesiva)
        double dx = originX - this.x;
        double dy = originY - this.y;
        double dz = originZ - this.z;
        float attract = attractionStrength * progress;

        this.xd += dx * attract;
        this.yd += dy * attract;
        this.zd += dz * attract;
    }
}
