package com.pop.jjk.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;

/**
 * Partícula de CHISPA/RAYO del orbe azul — energía eléctrica caótica.
 *
 * Diseño visual (referencia: Hollow Purple de JAESU):
 * - MUCHAS partículas finas y brillantes (el volumen se logra con cantidad, no tamaño).
 * - Escala pequeña (0.04-0.12): puntos luminosos, no blobs.
 * - Movimiento en DOS fases:
 *     1) TURBULENCIA: perturbación caótica multi-frecuencia (simula rayos/chispas).
 *        Tres ejes con frecuencias altas y aleatorias, amplitud decreciente.
 *     2) ATRACCIÓN: fuerza radial hacia el centro del orbe (todo cae al núcleo).
 *        Crece con progress → las chispas empiezan caóticas y terminan absorbidas.
 *
 * - Color: cyan brillante → blanco (más brillante al acercarse al centro).
 * - Fullbright + TRANSLUCENT: se suman entre sí, crean "campo eléctrico".
 * - Lifetime corto (6-14 ticks): renovación constante = flujo, no acumulación.
 *
 * Spawn desde GravityWellEffect: hasta 3-5/tick a power alto.
 * Radio de spawn: 1.5-3.0 bloques (distribución esférica).
 * Con lifetime ~10 y spawn ~4/tick → ~40 sparks activas simultáneas.
 * Esto es lo que crea la densidad visual del efecto JAESU.
 *
 * Convención: vx/vy/vz = (centerX - spawnX, centerY - spawnY, centerZ - spawnZ)
 */
public final class BlueOrbSparkParticle extends JJKBaseParticle {

    private static final JJKParticleConfig CONFIG = JJKParticleConfig.builder()
        .colorGradient(
            0.50F, 0.78F, 1.0F,    // start: azul sprite (80C8FF aprox)
            0.90F, 0.97F, 1.0F     // end: casi blanco (C0EEFF/EEFFFF aprox)
        )
        .scale(0.06F, 0.01F)       // MUY pequeñas — la densidad viene de la cantidad
        .alpha(0.9F, 0.0F)
        .lifetime(6, 14)           // cortas: renovación rápida = flujo constante
        .gravity(0.0F)
        .friction(0.94F)
        .spin(0.15F)               // spin rápido → centelleo
        .fullBright(true)
        .physics(false)
        .build();

    private final double centerX;
    private final double centerY;
    private final double centerZ;

    // Parámetros de turbulencia únicos por instancia
    private final float turbFreqX, turbFreqY, turbFreqZ;
    private final float turbPhase;
    private final float turbAmplitude;
    private final float attractStrength;

    public BlueOrbSparkParticle(ClientLevel level, double x, double y, double z,
                                 double vx, double vy, double vz, SpriteSet sprites) {
        super(level, x, y, z, 0, 0, 0, sprites, CONFIG);

        // Centro del orbe (decodificado de vx/vy/vz)
        this.centerX = x + vx;
        this.centerY = y + vy;
        this.centerZ = z + vz;

        // Frecuencias de turbulencia: altas y variadas entre instancias.
        // Rango 0.8-1.5 rad/tick → movimiento rápido, errático.
        // Cada eje tiene frecuencia diferente → patrón 3D no repetitivo.
        this.turbFreqX = 0.2F + this.random.nextFloat() * 0.3F;
        this.turbFreqY = 0.2F + this.random.nextFloat() * 0.3F;
        this.turbFreqZ = 0.2F + this.random.nextFloat() * 0.3F;
        this.turbPhase = this.random.nextFloat() * 6.2832F;

        // Amplitud de turbulencia: define cuán "salvaje" es esta chispa
        this.turbAmplitude = 0.004F + this.random.nextFloat() * 0.004F;

        // Fuerza de atracción: varía para que algunas caigan rápido y otras orbiten más
        this.attractStrength = 0.018F + this.random.nextFloat() * 0.012F;

        // Velocidad tangencial inicial (perpendicular al radio → espiral de entrada)
        double dx = vx;
        double dz = vz;
        double dist = Math.sqrt(dx * dx + dz * dz);
        if (dist > 0.01) {
            float tangent = 0.015F + this.random.nextFloat() * 0.01F;
            float dir = this.random.nextBoolean() ? 1.0F : -1.0F;
            this.xd = (-dz / dist) * tangent * dir;
            this.zd = (dx / dist) * tangent * dir;
        }
        this.yd = (this.random.nextFloat() - 0.5F) * 0.01F;
    }

    @Override
    protected void tickMovement(float progress) {
        float t = this.age + turbPhase;

        // --- TURBULENCIA ---
        // Perturbación caótica que DECAE con el tiempo.
        // Al inicio (progress~0): máxima turbulencia → chispas salen disparadas.
        // Al final (progress~1): turbulencia mínima → caen suavemente al centro.
        // Esto crea la transición: caos exterior → orden interior.
        float turbDecay = (1.0F - progress * 0.8F);
        float amp = turbAmplitude * turbDecay;

        this.xd += Math.sin(t * turbFreqX) * amp;
        this.yd += Math.cos(t * turbFreqY) * amp * 0.7F;
        this.zd += Math.sin(t * turbFreqZ + 1.5F) * amp;

        // --- ATRACCIÓN AL CENTRO ---
        // Fuerza radial que CRECE con progress.
        // Curva cuadrática: suave al inicio, agresiva al final.
        double dx = centerX - this.x;
        double dy = centerY - this.y;
        double dz = centerZ - this.z;
        double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);

        if (dist > 0.05) {
            // La fuerza crece cuadráticamente con progress Y con cercanía
            float attract = attractStrength * (0.3F + progress * progress * 0.7F);
            double invDist = 1.0 / dist;
            this.xd += dx * invDist * attract;
            this.yd += dy * invDist * attract;
            this.zd += dz * invDist * attract;
        }

        // --- EFECTO DE PROXIMIDAD ---
        // Al acercarse al centro: escala crece brevemente (flash) → luego kill.
        // Esto simula la chispa "absorbida" con un último destello.
        if (dist < 0.25) {
            float proximity = (float)(1.0 - dist / 0.25);
            // Flash: crece un poco antes de morir
            this.quadSize = baseQuadSize * (1.0F + proximity * 0.5F);
            this.alpha = Math.max(0.0F, this.alpha - proximity * 0.2F);
        }

        if (dist < 0.08) {
            this.remove();
        }
    }

    @Override
    protected SingleQuadParticle.Layer getLayer() {
        return SingleQuadParticle.Layer.TRANSLUCENT;
    }
}
