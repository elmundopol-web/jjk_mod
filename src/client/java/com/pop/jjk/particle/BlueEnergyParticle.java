package com.pop.jjk.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SpriteSet;

/**
 * Partícula de energía azul (técnica Blue / atracción).
 *
 * Comportamiento:
 * - Movimiento en espiral que se contrae hacia el origen
 * - Simula la atracción gravitacional de Blue
 * - Color azul brillante → azul profundo
 * - Más rápida y agresiva que CursedEnergy
 */
public final class BlueEnergyParticle extends JJKBaseParticle {

    private static final JJKParticleConfig CONFIG = JJKParticleConfig.builder()
        .colorGradient(0.45F, 0.75F, 1.0F,    // azul brillante
                       0.10F, 0.30F, 0.75F)    // azul profundo
        .scale(0.12F, 0.01F)
        .alpha(0.9F, 0.0F)
        .lifetime(10, 20)
        .gravity(0.0F)
        .friction(0.92F)
        .spin(0.08F)
        .fullBright(true)
        .physics(false)
        .build();

    private final double originX;
    private final double originY;
    private final double originZ;
    private final float orbitPhase;
    private final float orbitSpeed;
    private final float attractForce;

    public BlueEnergyParticle(ClientLevel level, double x, double y, double z,
                               double vx, double vy, double vz, SpriteSet sprites) {
        super(level, x, y, z, vx, vy, vz, sprites, CONFIG);
        this.originX = x;
        this.originY = y;
        this.originZ = z;

        this.orbitPhase = this.random.nextFloat() * 6.2832F;
        this.orbitSpeed = 0.2F + this.random.nextFloat() * 0.15F;
        this.attractForce = 0.04F + this.random.nextFloat() * 0.03F;

        // Variación individual de tono azul
        float shift = (this.random.nextFloat() - 0.5F) * 0.1F;
        this.rCol = Math.max(0.0F, this.rCol + shift * 0.3F);
        this.gCol = Math.max(0.0F, this.gCol + shift);
        this.bCol = Math.min(1.0F, this.bCol + Math.abs(shift) * 0.2F);
    }

    @Override
    protected void tickMovement(float progress) {
        float time = this.age * orbitSpeed + orbitPhase;
        float shrink = 1.0F - progress;

        // Espiral que se contrae — simula atracción
        float orbitRadius = 0.06F * shrink;
        this.xd += Math.cos(time) * orbitRadius * 0.1;
        this.yd += Math.sin(time * 0.7F) * orbitRadius * 0.06;
        this.zd += Math.sin(time) * orbitRadius * 0.1;

        // Atracción progresiva al centro
        double dx = originX - this.x;
        double dy = originY - this.y;
        double dz = originZ - this.z;
        float pull = attractForce * (0.3F + progress * 0.7F);

        this.xd += dx * pull;
        this.yd += dy * pull;
        this.zd += dz * pull;
    }
}
