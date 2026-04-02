package com.pop.jjk.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SpriteSet;

public final class FireChargeParticle extends JJKBaseParticle {

    private static final JJKParticleConfig CONFIG = JJKParticleConfig.builder()
        // Negro rojizo -> naranja intenso
        .colorGradient(0.04F, 0.02F, 0.02F, 1.00F, 0.40F, 0.05F)
        .scale(0.22F, 0.06F)
        .alpha(0.95F, 0.0F)
        .lifetime(10, 16)
        .gravity(0.0F)
        .friction(0.92F)
        .spin(0.35F)
        .fullBright(true)
        .physics(false)
        .pulse(0.08F, 0.24F, 0.37F)
        .turbulence(0.06F, 0.25F, 0.33F, 0.29F, 0.7F)
        .attraction(0.065F)
        .build();

    public FireChargeParticle(ClientLevel level, double x, double y, double z,
                              double vx, double vy, double vz, SpriteSet sprites) {
        super(level, x, y, z, vx, vy, vz, sprites, CONFIG);
    }

    @Override
    protected void tickMovement(float progress) {
        // Ligero ascenso y espiral suave (usamos spin y turbulencia del config)
        this.yd += 0.0025;
    }
}
