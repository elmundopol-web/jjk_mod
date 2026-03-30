package com.pop.jjk.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;

public final class BloodTrailParticle extends JJKBaseParticle {

    private static final JJKParticleConfig CONFIG = JJKParticleConfig.builder()
        .colorGradient(1.0F, 0.16F, 0.16F, 0.85F, 0.05F, 0.05F)
        .scale(0.06F, 0.012F)
        .alpha(0.92F, 0.0F)
        .lifetime(6, 12)
        .gravity(0.0F)
        .friction(0.92F)
        .spin(0.12F)
        .fullBright(true)
        .physics(false)
        .turbulence(0.0032F, 0.22F, 0.27F, 0.24F, 0.7F)
        .build();

    public BloodTrailParticle(ClientLevel level, double x, double y, double z,
                               double vx, double vy, double vz, SpriteSet sprites) {
        super(level, x, y, z, vx, vy, vz, sprites, CONFIG);
    }

    @Override
    protected void tickMovement(float progress) {
        // ligera deriva adicional
        this.yd += 0.0005F * (1.0F - progress);
    }

    @Override
    protected SingleQuadParticle.Layer getLayer() {
        return SingleQuadParticle.Layer.TRANSLUCENT;
    }
}
