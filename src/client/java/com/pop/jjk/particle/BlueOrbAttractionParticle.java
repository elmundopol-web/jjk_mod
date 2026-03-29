package com.pop.jjk.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;

/**
 * Partícula de ATRACCIÓN del orbe azul — streaks largos que espiralan hacia el centro.
 *
 * Diseño visual (referencia: Hollow Purple de JAESU):
 * - Representan materia/bloques siendo atraídos por el pozo gravitacional.
 * - Escala media-larga (0.08-0.16): más grandes que las sparks, dan estructura.
 * - Movimiento en ESPIRAL: componente tangencial fuerte + atracción radial creciente.
 *   No es una línea recta al centro — es una curva elegante que se aprieta.
 * - Se spawnan a distancia (2-4 bloques) y viajan hacia el centro durante 30-50 ticks.
 * - Color: azul medio → blanco-azul brillante al acercarse (se "calientan").
 * - Forma visual de "streak" via spin rápido + escala alargada implícita.
 * - Lifetime largo (~35-50 ticks): viaje completo visible desde lejos.
 *
 * Diferencias vs BlueOrbitalParticle (existente):
 * - BlueOrbital: espiral inward, tangencial dominante, parecen "orbitar" indefinidamente.
 * - BlueOrbAttraction: espiral MÁS AGRESIVA, radial dominante, claramente "caen" al centro.
 *   La componente tangencial es 40% de la radial (no 100%), así que la espiral se aprieta rápido.
 *
 * Spawn desde GravityWellEffect: 1 cada 2-3 ticks, radio 2.0-4.0 bloques.
 * Con lifetime ~40 y spawn ~0.4/tick → ~16 activas → anillos visibles de atracción.
 *
 * Convención: vx/vy/vz = (centerX - spawnX, centerY - spawnY, centerZ - spawnZ)
 */
public final class BlueOrbAttractionParticle extends JJKBaseParticle {

    private static final JJKParticleConfig CONFIG = JJKParticleConfig.builder()
        .colorGradient(
            0.3F,  0.6F,  1.0F,    // start: azul medio (lejos)
            0.8F,  0.93F, 1.0F     // end: blanco-azul brillante (cerca = caliente)
        )
        .scale(0.10F, 0.02F)       // media → desvanece
        .alpha(0.8F, 0.0F)
        .lifetime(30, 50)          // largo: viaje completo visible
        .gravity(0.0F)
        .friction(0.985F)          // mínima fricción → mantiene velocidad orbital
        .spin(0.03F)
        .fullBright(true)
        .physics(false)
        .build();

    private final double centerX;
    private final double centerY;
    private final double centerZ;
    private final float tangentialSpeed;
    private final float orbitDirection;
    private final float verticalOscPhase;
    private final float attractBase;

    public BlueOrbAttractionParticle(ClientLevel level, double x, double y, double z,
                                      double vx, double vy, double vz, SpriteSet sprites) {
        super(level, x, y, z, 0, 0, 0, sprites, CONFIG);

        this.centerX = x + vx;
        this.centerY = y + vy;
        this.centerZ = z + vz;

        // Velocidad tangencial: define cuán amplia es la espiral
        this.tangentialSpeed = 0.012F + this.random.nextFloat() * 0.01F;
        this.orbitDirection = this.random.nextBoolean() ? 1.0F : -1.0F;
        this.verticalOscPhase = this.random.nextFloat() * 6.2832F;

        // Fuerza de atracción base: varía para dar profundidad visual
        this.attractBase = 0.006F + this.random.nextFloat() * 0.004F;

        // Velocidad tangencial inicial (perpendicular al radio en XZ)
        double dx = centerX - x;
        double dz = centerZ - z;
        double dist = Math.sqrt(dx * dx + dz * dz);
        if (dist > 0.01) {
            this.xd = (-dz / dist) * tangentialSpeed * orbitDirection;
            this.zd = (dx / dist) * tangentialSpeed * orbitDirection;
        }
        this.yd = (this.random.nextFloat() - 0.5F) * 0.003F;
    }

    @Override
    protected void tickMovement(float progress) {
        double dx = centerX - this.x;
        double dy = centerY - this.y;
        double dz = centerZ - this.z;
        double distSq = dx * dx + dy * dy + dz * dz;
        double dist = Math.sqrt(distSq);

        // --- ATRACCIÓN RADIAL: crece cuadráticamente con progress ---
        // progress² da aceleración suave al inicio, agresiva al final.
        // Esto crea el efecto visual de "caer" en el pozo: lenta salida, rápida entrada.
        float attract = attractBase + 0.015F * progress * progress;

        if (dist > 0.05) {
            double invDist = 1.0 / dist;
            this.xd += dx * invDist * attract;
            this.yd += dy * invDist * attract;
            this.zd += dz * invDist * attract;
        }

        // --- VELOCIDAD TANGENCIAL: mantiene la curva de la espiral ---
        // Decae con el tiempo: al inicio orbita amplio, al final cae directo.
        // 40% de la fuerza radial como referencia: espiral agresiva pero no circular.
        if (dist > 0.15) {
            double invDist = 1.0 / dist;
            double nx = -dz * invDist;
            double nz = dx * invDist;
            float tangentForce = tangentialSpeed * 0.12F * (1.0F - progress * 0.6F);
            this.xd += nx * tangentForce * orbitDirection;
            this.zd += nz * tangentForce * orbitDirection;
        }

        // --- OSCILACIÓN VERTICAL: ondulación 3D ---
        // Sin esto la espiral es plana. Con oscilación parece un campo gravitacional 3D.
        float vertOsc = (float) Math.sin(this.age * 0.1F + verticalOscPhase) * 0.0012F;
        this.yd += vertOsc;

        // --- EFECTO DE PROXIMIDAD: se ilumina y encoge al acercarse ---
        if (dist < 0.5) {
            float proximity = (float)(1.0 - dist / 0.5);
            // Encoge + brilla: simula compresión gravitacional
            this.quadSize = baseQuadSize * Math.max(0.15F, 1.0F - proximity * 0.8F);
            // Color shift hacia blanco al acercarse (se mezcla con el interpolation del base)
        }

        // Kill al llegar al centro
        if (dist < 0.1) {
            this.remove();
        }
    }

    @Override
    protected SingleQuadParticle.Layer getLayer() {
        return SingleQuadParticle.Layer.TRANSLUCENT;
    }
}
