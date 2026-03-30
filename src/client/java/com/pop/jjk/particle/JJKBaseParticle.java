package com.pop.jjk.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;

/**
 * Clase base para todas las partículas del mod JJK.
 *
 * Proporciona:
 * - Interpolación automática de color (start → end)
 * - Curva de alpha con fade-out suave
 * - Curva de tamaño (grow → sustain → shrink)
 * - Rotación/spin configurable
 * - Emisión propia (fullbright) opcional
 * - Hook {@link #tickMovement} para que subclases definan movimiento custom
 *
 * Las subclases solo necesitan implementar {@link #tickMovement} y {@link #getLayer}.
 */
public abstract class JJKBaseParticle extends SingleQuadParticle {

    protected final SpriteSet sprites;
    protected final JJKParticleConfig config;
    protected final float baseQuadSize;
    // Phases for procedural modulation
    private final float pulsePhaseA;
    private final float pulsePhaseB;
    private final float turbPhase;
    // Optional attraction center (derived from vx,vy,vz as center offset)
    private final boolean hasAttractCenter;
    private final double centerX;
    private final double centerY;
    private final double centerZ;

    protected JJKBaseParticle(ClientLevel level, double x, double y, double z,
                               double vx, double vy, double vz,
                               SpriteSet sprites, JJKParticleConfig config) {
        super(level, x, y, z, sprites.get(level.random));
        this.sprites = sprites;
        this.config = config;

        this.xd = vx;
        this.yd = vy;
        this.zd = vz;

        // If attraction is enabled, interpret (vx,vy,vz) also as center offset
        if (config.attractStrength() != 0.0F) {
            this.centerX = x + vx;
            this.centerY = y + vy;
            this.centerZ = z + vz;
            this.hasAttractCenter = true;
        } else {
            this.centerX = x;
            this.centerY = y;
            this.centerZ = z;
            this.hasAttractCenter = false;
        }

        int range = Math.max(1, config.maxLifetime() - config.minLifetime());
        this.lifetime = config.minLifetime() + this.random.nextInt(range);

        this.baseQuadSize = config.startScale() * (0.85F + this.random.nextFloat() * 0.3F);
        this.quadSize = this.baseQuadSize;

        this.rCol = config.startR();
        this.gCol = config.startG();
        this.bCol = config.startB();
        this.alpha = config.startAlpha();

        this.gravity = config.gravity();
        this.friction = config.friction();
        this.hasPhysics = config.hasPhysics();

        this.oRoll = 0.0F;
        this.roll = (this.random.nextFloat() - 0.5F) * 6.2832F;

        // Randomize phases for organic variation
        this.pulsePhaseA = this.random.nextFloat() * 6.2832F;
        this.pulsePhaseB = this.random.nextFloat() * 6.2832F;
        this.turbPhase = this.random.nextFloat() * 6.2832F;

        this.setSpriteFromAge(sprites);
    }

    @Override
    public void tick() {
        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;
        this.oRoll = this.roll;

        if (this.age++ >= this.lifetime) {
            this.remove();
            return;
        }

        float progress = lifeProgress();

        interpolateColor(progress);
        interpolateAlpha(progress);
        interpolateSize(progress);
        applyRoll(progress);
        tickMovement(progress);
        applyBaseBehaviors(progress);

        this.yd -= this.gravity;
        this.move(this.xd, this.yd, this.zd);
        this.xd *= this.friction;
        this.yd *= this.friction;
        this.zd *= this.friction;

        this.setSpriteFromAge(sprites);
    }

    /**
     * Hook para que subclases definan movimiento personalizado.
     * Se llama cada tick antes de aplicar física.
     * @param progress valor 0→1 del ciclo de vida
     */
    protected abstract void tickMovement(float progress);

    @Override
    protected SingleQuadParticle.Layer getLayer() {
        return SingleQuadParticle.Layer.TRANSLUCENT;
    }


    @Override
    public int getLightColor(float partialTick) {
        if (config.fullBright()) {
            return 0xF000F0;
        }
        return super.getLightColor(partialTick);
    }

    protected final float lifeProgress() {
        return Math.min(1.0F, (float) this.age / (float) this.lifetime);
    }

    /**
     * Interpolación suave entre dos valores usando smoothstep.
     */
    protected static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    protected static float smoothstep(float t) {
        return t * t * (3.0F - 2.0F * t);
    }

    private void interpolateColor(float progress) {
        float t = smoothstep(progress);
        this.rCol = lerp(config.startR(), config.endR(), t);
        this.gCol = lerp(config.startG(), config.endG(), t);
        this.bCol = lerp(config.startB(), config.endB(), t);
    }

    private void interpolateAlpha(float progress) {
        // Fase 1 (0-10%): fade in rápido
        // Fase 2 (10-70%): alpha estable
        // Fase 3 (70-100%): fade out suave
        if (progress < 0.1F) {
            this.alpha = lerp(0.0F, config.startAlpha(), progress / 0.1F);
        } else if (progress < 0.7F) {
            this.alpha = config.startAlpha();
        } else {
            float fadeProgress = (progress - 0.7F) / 0.3F;
            this.alpha = lerp(config.startAlpha(), config.endAlpha(), smoothstep(fadeProgress));
        }
    }

    private void interpolateSize(float progress) {
        // Fase 1 (0-15%): grow rápido
        // Fase 2 (15-60%): tamaño estable
        // Fase 3 (60-100%): shrink suave
        if (progress < 0.15F) {
            this.quadSize = lerp(baseQuadSize * 0.3F, baseQuadSize, progress / 0.15F);
        } else if (progress < 0.6F) {
            this.quadSize = baseQuadSize;
        } else {
            float shrinkProgress = (progress - 0.6F) / 0.4F;
            float endSize = baseQuadSize * (config.endScale() / Math.max(0.001F, config.startScale()));
            this.quadSize = lerp(baseQuadSize, endSize, smoothstep(shrinkProgress));
        }
    }

    private void applyRoll(float progress) {
        if (config.spinSpeed() != 0.0F) {
            this.roll += config.spinSpeed() * (1.0F - progress * 0.4F);
        }
    }

    private void applyBaseBehaviors(float progress) {
        // Pulse (scale breathing)
        if (config.pulseAmp() != 0.0F) {
            float a = (float) Math.sin(this.age * config.pulseFreqA() + pulsePhaseA);
            float b = (float) Math.sin(this.age * config.pulseFreqB() + pulsePhaseB) * 0.5F;
            float amp = config.pulseAmp() * (1.0F - progress * 0.4F);
            float pulse = (a + b) * amp;
            this.quadSize *= (1.0F + pulse);
        }

        // Turbulence (soft chaotic motion)
        if (config.turbAmp() != 0.0F) {
            float decay = Math.max(0.0F, 1.0F - progress * config.turbDecay());
            float amp = config.turbAmp() * decay;
            float t = this.age + turbPhase;
            this.xd += Math.sin(t * config.turbFreqX()) * amp;
            this.yd += Math.cos(t * config.turbFreqY()) * amp * 0.7F;
            this.zd += Math.sin(t * config.turbFreqZ() + 1.3F) * amp;
        }

        // Radial attraction towards center (if provided via constructor)
        if (hasAttractCenter && config.attractStrength() != 0.0F) {
            double dx = centerX - this.x;
            double dy = centerY - this.y;
            double dz = centerZ - this.z;
            double distSq = dx * dx + dy * dy + dz * dz;
            if (distSq > 0.0004) {
                double dist = Math.sqrt(distSq);
                double inv = 1.0 / dist;
                float strength = config.attractStrength() * (0.3F + progress * progress * 0.7F);
                this.xd += dx * inv * strength;
                this.yd += dy * inv * strength;
                this.zd += dz * inv * strength;
            }
        }
    }
}
