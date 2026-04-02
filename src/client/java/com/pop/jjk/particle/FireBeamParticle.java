package com.pop.jjk.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SpriteSet;

public final class FireBeamParticle extends JJKBaseParticle {

    private static final JJKParticleConfig CONFIG = JJKParticleConfig.builder()
        // Rojo profundo -> naranja brillante
        .colorGradient(0.50F, 0.08F, 0.06F, 1.00F, 0.45F, 0.08F)
        .scale(0.30F, 0.08F)
        .alpha(0.95F, 0.0F)
        .lifetime(8, 14)
        .gravity(0.0F)
        .friction(0.94F)
        .spin(0.25F)
        .fullBright(true)
        .physics(false)
        .pulse(0.10F, 0.42F, 0.26F)
        .turbulence(0.08F, 0.38F, 0.33F, 0.41F, 0.6F)
        .build();

    public FireBeamParticle(ClientLevel level, double x, double y, double z,
                            double vx, double vy, double vz, SpriteSet sprites) {
        super(level, x, y, z, vx, vy, vz, sprites, CONFIG);
    }

    @Override
    protected void tickMovement(float progress) {
        // Leve aceleración hacia delante (usa vx/vy/vz provistos) y efervescencia por turbulencia
        this.yd += 0.0008;
    }
}
