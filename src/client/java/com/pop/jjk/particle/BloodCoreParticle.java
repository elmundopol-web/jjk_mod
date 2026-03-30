package com.pop.jjk.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;

/**
 * Núcleo de sangre (glow) para técnicas de Choso.
 * Fuerte brillo, pulso y ligera turbulencia. Pensado para sumar con blending
 * translúcido + fullbright generando bloom en shaders.
 */
public final class BloodCoreParticle extends JJKBaseParticle {

    private static final JJKParticleConfig CONFIG = JJKParticleConfig.builder()
        .colorGradient(
            1.0F, 0.18F, 0.18F,   // rojo blanco-caliente
            0.85F, 0.05F, 0.05F   // rojo profundo
        )
        .scale(0.22F, 0.06F)
        .alpha(0.85F, 0.0F)
        .lifetime(12, 20)
        .gravity(0.0F)
        .friction(0.90F)
        .spin(0.05F)
        .fullBright(true)
        .physics(false)
        .pulse(0.22F, 0.35F, 0.53F)
        .turbulence(0.0025F, 0.18F, 0.22F, 0.20F, 0.6F)
        .build();

    private final float wobblePhase;

    public BloodCoreParticle(ClientLevel level, double x, double y, double z,
                              double vx, double vy, double vz, SpriteSet sprites) {
        super(level, x, y, z, vx, vy, vz, sprites, CONFIG);
        this.quadSize = this.baseQuadSize * (0.9F + this.random.nextFloat() * 0.3F);
        this.wobblePhase = this.random.nextFloat() * 6.2832F;
    }

    @Override
    protected void tickMovement(float progress) {
        // Micro wobble para dar vida al glow
        float wobble = 0.0025F + progress * 0.0015F;
        float t = this.age * 0.22F + wobblePhase;
        this.xd += Math.sin(t * 1.9F) * wobble;
        this.yd += Math.cos(t * 1.3F) * wobble * 0.6F;
        this.zd += Math.sin(t * 1.7F + 1.1F) * wobble;
    }

    @Override
    protected SingleQuadParticle.Layer getLayer() {
        return SingleQuadParticle.Layer.TRANSLUCENT;
    }
}
