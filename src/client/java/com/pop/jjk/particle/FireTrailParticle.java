package com.pop.jjk.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SpriteSet;

public final class FireTrailParticle extends JJKBaseParticle {

    private static final JJKParticleConfig CONFIG = JJKParticleConfig.builder()
        // Negro -> rojo oscuro (rastro)
        .colorGradient(0.05F, 0.02F, 0.02F, 0.60F, 0.10F, 0.05F)
        .scale(0.18F, 0.04F)
        .alpha(0.92F, 0.0F)
        .lifetime(10, 18)
        .gravity(0.0F)
        .friction(0.93F)
        .spin(0.20F)
        .fullBright(true)
        .physics(false)
        .pulse(0.08F, 0.35F, 0.27F)
        .turbulence(0.10F, 0.30F, 0.41F, 0.33F, 0.7F)
        .build();

    public FireTrailParticle(ClientLevel level, double x, double y, double z,
                             double vx, double vy, double vz, SpriteSet sprites) {
        super(level, x, y, z, vx, vy, vz, sprites, CONFIG);
    }

    @Override
    protected void tickMovement(float progress) {
        // Sutil subida por calor
        this.yd += 0.0015;
    }
}
