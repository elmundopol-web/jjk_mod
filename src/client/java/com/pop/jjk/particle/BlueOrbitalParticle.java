package com.pop.jjk.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SpriteSet;

/**
 * Partícula orbital del Gravity Well.
 *
 * Comportamiento:
 * - Se genera a distancia del centro y espirala hacia él
 * - Movimiento curvo no-lineal: velocidad tangencial + atracción radial creciente
 * - Al acercarse al centro: se acelera, se encoge, pierde alpha
 * - Simula materia siendo succionada por un pozo gravitacional
 *
 * Convención de spawn: vx/vy/vz = (centerX - spawnX, centerY - spawnY, centerZ - spawnZ)
 * La partícula los usa para calcular el centro de atracción.
 */
public final class BlueOrbitalParticle extends JJKBaseParticle {

    private static final JJKParticleConfig CONFIG = JJKParticleConfig.builder()
        .colorGradient(0.35F, 0.65F, 1.0F,     // azul medio
                       0.75F, 0.92F, 1.0F)      // blanco-azul (más brillante al acercarse)
        .scale(0.10F, 0.02F)
        .alpha(0.75F, 0.0F)
        .lifetime(25, 40)
        .gravity(0.0F)
        .friction(0.98F)
        .spin(0.04F)
        .fullBright(true)
        .physics(false)
        .build();

    private final double centerX;
    private final double centerY;
    private final double centerZ;
    private final float tangentialSpeed;
    private final float orbitDirection;
    private final float verticalOscPhase;

    public BlueOrbitalParticle(ClientLevel level, double x, double y, double z,
                                double vx, double vy, double vz, SpriteSet sprites) {
        super(level, x, y, z, 0, 0, 0, sprites, CONFIG);

        // vx/vy/vz encode el delta hacia el centro
        this.centerX = x + vx;
        this.centerY = y + vy;
        this.centerZ = z + vz;

        // Velocidad tangencial inicial (perpendicular al radio)
        this.tangentialSpeed = 0.025F + this.random.nextFloat() * 0.02F;
        // Dirección de órbita aleatoria (CW o CCW)
        this.orbitDirection = this.random.nextBoolean() ? 1.0F : -1.0F;
        this.verticalOscPhase = this.random.nextFloat() * 6.2832F;

        // Velocidad inicial tangencial
        double dx = centerX - x;
        double dz = centerZ - z;
        double dist = Math.sqrt(dx * dx + dz * dz);
        if (dist > 0.01) {
            // Vector perpendicular al radio (tangente)
            double nx = -dz / dist;
            double nz = dx / dist;
            this.xd = nx * tangentialSpeed * orbitDirection;
            this.zd = nz * tangentialSpeed * orbitDirection;
        }
        this.yd = (this.random.nextFloat() - 0.5F) * 0.005F;
    }

    @Override
    protected void tickMovement(float progress) {
        double dx = centerX - this.x;
        double dy = centerY - this.y;
        double dz = centerZ - this.z;
        double distSq = dx * dx + dy * dy + dz * dz;
        double dist = Math.sqrt(distSq);

        // --- Atracción radial: crece con el tiempo (succión progresiva) ---
        // Usa easing cuadrático para que la aceleración sea suave al inicio
        // y agresiva al final
        float attractBase = 0.002F;
        float attractGrowth = 0.012F * progress * progress;
        float attract = attractBase + attractGrowth;

        if (dist > 0.05) {
            double invDist = 1.0 / dist;
            this.xd += dx * invDist * attract;
            this.yd += dy * invDist * attract;
            this.zd += dz * invDist * attract;
        }

        // --- Velocidad tangencial: mantiene la curva de la espiral ---
        // Decae con el tiempo para que la partícula eventualmente caiga al centro
        if (dist > 0.1) {
            double invDist = 1.0 / dist;
            double nx = -dz * invDist;
            double nz = dx * invDist;
            float tangentForce = tangentialSpeed * 0.15F * (1.0F - progress * 0.7F);
            this.xd += nx * tangentForce * orbitDirection;
            this.zd += nz * tangentForce * orbitDirection;
        }

        // --- Oscilación vertical suave ---
        float vertOsc = (float) Math.sin(this.age * 0.12F + verticalOscPhase) * 0.001F;
        this.yd += vertOsc;

        // --- Efecto de proximidad: encoge y aumenta brillo al acercarse ---
        if (dist < 0.4) {
            float proximity = (float)(1.0 - dist / 0.4);
            this.quadSize = baseQuadSize * (1.0F - proximity * 0.7F);
            this.alpha = Math.max(0.0F, this.alpha - proximity * 0.15F);
        }

        // Kill si llega al centro
        if (dist < 0.08) {
            this.remove();
        }
    }
}
