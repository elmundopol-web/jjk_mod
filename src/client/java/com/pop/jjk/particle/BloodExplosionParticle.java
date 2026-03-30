package com.pop.jjk.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;

/**
 * Estallido de sangre: ráfaga radial con ligero pulso y fade rápido.
 * Ideal para impactos de Piercing Blood y Supernova.
 */
public final class BloodExplosionParticle extends JJKBaseParticle {

    private static final JJKParticleConfig CONFIG = JJKParticleConfig.builder()
        .colorGradient(1.0F, 0.20F, 0.20F, 0.9F, 0.05F, 0.05F)
        .scale(0.16F, 0.02F)
        .alpha(0.9F, 0.0F)
        .lifetime(10, 16)
        .gravity(0.0F)
        .friction(0.90F)
        .spin(0.18F)
        .fullBright(true)
        .physics(false)
        .turbulence(0.0028F, 0.25F, 0.21F, 0.28F, 0.7F)
        .pulse(0.12F, 0.45F, 0.62F)
        .build();

    public BloodExplosionParticle(ClientLevel level, double x, double y, double z,
                                   double vx, double vy, double vz, SpriteSet sprites) {
        super(level, x, y, z, vx, vy, vz, sprites, CONFIG);
        // Pequeño impulso inicial radial (si vx/vy/vz no se usan como centro)
        this.xd += (this.random.nextFloat() - 0.5F) * 0.06F;
        this.yd += (this.random.nextFloat() - 0.5F) * 0.04F;
        this.zd += (this.random.nextFloat() - 0.5F) * 0.06F;
    }

    @Override
    protected void tickMovement(float progress) {
        // Expandir ligeramente (ya controlado por size curve), añadimos levantamiento sutil
        this.yd += 0.0025F * (1.0F - progress);
    }

    @Override
    protected SingleQuadParticle.Layer getLayer() {
        return SingleQuadParticle.Layer.TRANSLUCENT;
    }
}
