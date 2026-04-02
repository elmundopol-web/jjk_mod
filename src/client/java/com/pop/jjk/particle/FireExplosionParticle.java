package com.pop.jjk.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SpriteSet;

public final class FireExplosionParticle extends JJKBaseParticle {

    private static final JJKParticleConfig CONFIG = JJKParticleConfig.builder()
        // Núcleo blanco-naranja muy brillante que decae a rojo-anaranjado
        .colorGradient(1.00F, 0.60F, 0.20F, 0.90F, 0.25F, 0.08F)
        .scale(0.55F, 0.10F)
        .alpha(1.0F, 0.0F)
        .lifetime(14, 22)
        .gravity(0.0F)
        .friction(0.90F)
        .spin(0.45F)
        .fullBright(true)
        .physics(false)
        .pulse(0.15F, 0.50F, 0.33F)
        .turbulence(0.12F, 0.45F, 0.41F, 0.38F, 0.5F)
        .build();

    public FireExplosionParticle(ClientLevel level, double x, double y, double z,
                                 double vx, double vy, double vz, SpriteSet sprites) {
        super(level, x, y, z, vx, vy, vz, sprites, CONFIG);
    }

    @Override
    protected void tickMovement(float progress) {
        // Expansión radial sutil (ya controlada por size + turbulence); leve subida térmica
        this.yd += 0.002;
    }
}
