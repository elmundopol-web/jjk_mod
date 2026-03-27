package com.pop.jjk.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SpriteSet;

/**
 * Partícula absorbida del Gravity Well.
 *
 * Comportamiento:
 * - Se genera en el borde del efecto y se lanza directamente hacia el centro
 * - Aceleración progresiva (no lineal): empieza lenta, termina rápida
 * - Se encoge y desvanece al acercarse — simula ser "consumida"
 * - Lifetime corto, movimiento decisivo
 * - Ligera desviación lateral para evitar aspecto de línea recta
 *
 * Convención de spawn: vx/vy/vz = (centerX - spawnX, centerY - spawnY, centerZ - spawnZ)
 */
public final class BlueAbsorbedParticle extends JJKBaseParticle {

    private static final JJKParticleConfig CONFIG = JJKParticleConfig.builder()
        .colorGradient(0.55F, 0.82F, 1.0F,     // azul claro
                       0.9F, 0.97F, 1.0F)       // casi blanco (al ser absorbido)
        .scale(0.07F, 0.01F)
        .alpha(0.85F, 0.0F)
        .lifetime(8, 14)
        .gravity(0.0F)
        .friction(1.0F)                         // sin fricción — la aceleración es manual
        .spin(0.0F)
        .fullBright(true)
        .physics(false)
        .build();

    private final double centerX;
    private final double centerY;
    private final double centerZ;
    private final float lateralPhase;
    private final float lateralAmplitude;

    public BlueAbsorbedParticle(ClientLevel level, double x, double y, double z,
                                 double vx, double vy, double vz, SpriteSet sprites) {
        super(level, x, y, z, 0, 0, 0, sprites, CONFIG);

        this.centerX = x + vx;
        this.centerY = y + vy;
        this.centerZ = z + vz;

        this.lateralPhase = this.random.nextFloat() * 6.2832F;
        this.lateralAmplitude = 0.003F + this.random.nextFloat() * 0.005F;

        // Velocidad inicial: lenta, orientada al centro
        double dist = Math.sqrt(vx * vx + vy * vy + vz * vz);
        if (dist > 0.01) {
            float initialSpeed = 0.02F;
            this.xd = (vx / dist) * initialSpeed;
            this.yd = (vy / dist) * initialSpeed;
            this.zd = (vz / dist) * initialSpeed;
        }
    }

    @Override
    protected void tickMovement(float progress) {
        double dx = centerX - this.x;
        double dy = centerY - this.y;
        double dz = centerZ - this.z;
        double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);

        // --- Aceleración no-lineal: easing cúbico ---
        // Empieza suave, termina agresivo. Simula "caer" en el pozo.
        float accel = 0.008F + 0.04F * progress * progress * progress;

        if (dist > 0.03) {
            double invDist = 1.0 / dist;
            this.xd += dx * invDist * accel;
            this.yd += dy * invDist * accel;
            this.zd += dz * invDist * accel;
        }

        // --- Desviación lateral sutil: rompe la linealidad ---
        // Perpendicular al vector de movimiento en el plano XZ
        if (dist > 0.2) {
            float lateralForce = lateralAmplitude * (1.0F - progress);
            float time = this.age * 0.5F + lateralPhase;
            double perpX = -dz;
            double perpZ = dx;
            double perpLen = Math.sqrt(perpX * perpX + perpZ * perpZ);
            if (perpLen > 0.01) {
                this.xd += (perpX / perpLen) * Math.sin(time) * lateralForce;
                this.zd += (perpZ / perpLen) * Math.sin(time) * lateralForce;
            }
        }

        // --- Efecto de cercanía: la partícula desaparece al llegar ---
        if (dist < 0.3) {
            float proximity = (float)(1.0 - dist / 0.3);
            this.quadSize = baseQuadSize * Math.max(0.1F, 1.0F - proximity * 0.9F);
        }

        // Kill al llegar al centro
        if (dist < 0.06) {
            this.remove();
        }
    }
}
