package com.pop.jjk.particle;

/**
 * Configuración inmutable para partículas JJK.
 * Controla el ciclo de vida visual completo: color, tamaño, alpha, física.
 *
 * Los valores de color están en rango [0,1].
 * Las curvas de tamaño/alpha usan interpolación con fase de grow/sustain/shrink.
 */
public record JJKParticleConfig(
    float startR, float startG, float startB,
    float endR,   float endG,   float endB,
    float startScale,
    float endScale,
    float startAlpha,
    float endAlpha,
    int minLifetime,
    int maxLifetime,
    float gravity,
    float friction,
    float spinSpeed,
    boolean fullBright,
    boolean hasPhysics,
    // Pulse
    float pulseAmp,
    float pulseFreqA,
    float pulseFreqB,
    // Turbulence
    float turbAmp,
    float turbFreqX,
    float turbFreqY,
    float turbFreqZ,
    float turbDecay,
    // Attraction (radial towards provided center via per-instance vx/vy/vz usage)
    float attractStrength
) {

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private float startR = 1.0F, startG = 1.0F, startB = 1.0F;
        private float endR = 1.0F, endG = 1.0F, endB = 1.0F;
        private float startScale = 0.15F;
        private float endScale = 0.02F;
        private float startAlpha = 0.9F;
        private float endAlpha = 0.0F;
        private int minLifetime = 10;
        private int maxLifetime = 20;
        private float gravity = 0.0F;
        private float friction = 0.96F;
        private float spinSpeed = 0.0F;
        private boolean fullBright = true;
        private boolean hasPhysics = false;
        // defaults (0 = disabled)
        private float pulseAmp = 0.0F;
        private float pulseFreqA = 0.0F;
        private float pulseFreqB = 0.0F;
        private float turbAmp = 0.0F;
        private float turbFreqX = 0.0F;
        private float turbFreqY = 0.0F;
        private float turbFreqZ = 0.0F;
        private float turbDecay = 0.8F;
        private float attractStrength = 0.0F;

        private Builder() {}

        public Builder color(float r, float g, float b) {
            this.startR = r; this.startG = g; this.startB = b;
            this.endR = r; this.endG = g; this.endB = b;
            return this;
        }

        public Builder colorGradient(float sr, float sg, float sb, float er, float eg, float eb) {
            this.startR = sr; this.startG = sg; this.startB = sb;
            this.endR = er; this.endG = eg; this.endB = eb;
            return this;
        }

        public Builder scale(float start, float end) {
            this.startScale = start; this.endScale = end;
            return this;
        }

        public Builder alpha(float start, float end) {
            this.startAlpha = start; this.endAlpha = end;
            return this;
        }

        public Builder lifetime(int min, int max) {
            this.minLifetime = min; this.maxLifetime = max;
            return this;
        }

        public Builder gravity(float gravity) {
            this.gravity = gravity;
            return this;
        }

        public Builder friction(float friction) {
            this.friction = friction;
            return this;
        }

        public Builder spin(float speed) {
            this.spinSpeed = speed;
            return this;
        }

        public Builder fullBright(boolean value) {
            this.fullBright = value;
            return this;
        }

        public Builder physics(boolean value) {
            this.hasPhysics = value;
            return this;
        }

        public Builder pulse(float amp, float freqA, float freqB) {
            this.pulseAmp = amp;
            this.pulseFreqA = freqA;
            this.pulseFreqB = freqB;
            return this;
        }

        public Builder turbulence(float amp, float fx, float fy, float fz, float decay) {
            this.turbAmp = amp;
            this.turbFreqX = fx;
            this.turbFreqY = fy;
            this.turbFreqZ = fz;
            this.turbDecay = decay;
            return this;
        }

        public Builder attraction(float strength) {
            this.attractStrength = strength;
            return this;
        }

        public JJKParticleConfig build() {
            return new JJKParticleConfig(
                startR, startG, startB,
                endR, endG, endB,
                startScale, endScale,
                startAlpha, endAlpha,
                minLifetime, maxLifetime,
                gravity, friction, spinSpeed,
                fullBright, hasPhysics,
                pulseAmp, pulseFreqA, pulseFreqB,
                turbAmp, turbFreqX, turbFreqY, turbFreqZ, turbDecay,
                attractStrength
            );
        }
    }
}
