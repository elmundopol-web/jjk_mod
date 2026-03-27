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

    protected JJKBaseParticle(ClientLevel level, double x, double y, double z,
                               double vx, double vy, double vz,
                               SpriteSet sprites, JJKParticleConfig config) {
        super(level, x, y, z, sprites.get(level.random));
        this.sprites = sprites;
        this.config = config;

        this.xd = vx;
        this.yd = vy;
        this.zd = vz;

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
}
