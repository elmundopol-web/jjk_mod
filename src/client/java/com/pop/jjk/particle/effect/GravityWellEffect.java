package com.pop.jjk.particle.effect;

import com.pop.jjk.JJKParticles;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.RandomSource;

/**
 * Efecto compuesto de pozo gravitacional — orquestador JAESU-level.
 *
 * Emite 6 tipos de partículas de forma coordinada para crear el efecto visual
 * de referencia (Hollow Purple de JAESU pero en azul eléctrico):
 *
 * NUEVAS (alta densidad, alta calidad):
 * - {@code BLUE_ORB_CORE}:       Glow grande volumétrico, pulso, blending aditivo.
 * - {@code BLUE_ORB_SPARK}:      Miles de chispas finas con turbulencia caótica.
 * - {@code BLUE_ORB_ATTRACTION}: Streaks largos espiralando hacia el centro.
 *
 * EXISTENTES (complementarias):
 * - {@code BLUE_ORBITAL}:  Espirales inward suaves (anillos de órbita).
 * - {@code BLUE_ABSORBED}: Streaks rápidos de absorción.
 * - {@code BLUE_CORE}:     Cores pequeñas complementarias.
 *
 * Presupuesto de partículas activas (power=1.0):
 *   OrbCore: ~12-15 | Sparks: ~40-60 | Attraction: ~16-20
 *   Orbital: ~10-14  | Absorbed: ~8-12 | Core: ~6-8
 *   → Total: ~90-130 partículas activas (razonable para MC con shaders)
 *
 * Escalado con power:
 *   power 0.0: ~20 partículas (solo core + algunos sparks)
 *   power 0.3: ~40 partículas (orbitals empiezan)
 *   power 0.5: ~65 partículas (attraction empieza)
 *   power 0.7: ~90 partículas (ground ring, densidad alta)
 *   power 1.0: ~130 partículas (máxima intensidad cinematográfica)
 */
public final class GravityWellEffect {

    private final int entityId;
    private double centerX, centerY, centerZ;
    private float power;
    private boolean launched;
    private int ticksAlive;
    private int ticksSinceLastUpdate;
    private boolean expired;

    private static final int STALE_THRESHOLD = 5;

    // Rate limiters por tipo de partícula
    private int orbCoreCooldown;
    private int sparkCooldown;
    private int attractionCooldown;
    private int orbitalCooldown;
    private int absorbedCooldown;
    private int coreCooldown;
    private int groundRingCooldown;

    public GravityWellEffect(int entityId, double x, double y, double z, float power, boolean launched) {
        this.entityId = entityId;
        this.centerX = x;
        this.centerY = y;
        this.centerZ = z;
        this.power = power;
        this.launched = launched;
        this.ticksAlive = 0;
        this.expired = false;
    }

    public int getEntityId() { return entityId; }
    public boolean isExpired() { return expired; }
    public void expire() { this.expired = true; }

    public void update(double x, double y, double z, float power, boolean launched) {
        this.centerX = x;
        this.centerY = y;
        this.centerZ = z;
        this.power = power;
        this.launched = launched;
        this.ticksSinceLastUpdate = 0;
    }

    public void tick() {
        if (expired) return;

        ticksSinceLastUpdate++;
        if (ticksSinceLastUpdate > STALE_THRESHOLD) {
            expired = true;
            return;
        }

        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            expired = true;
            return;
        }

        ticksAlive++;
        float intensity = Math.max(0.1F, power);

        // Los 3 nuevos tipos de alta calidad (siempre activos, escalan con power)
        tickOrbCore(level, intensity);
        tickSparks(level, intensity);
        tickAttraction(level, intensity);

        // Los 3 tipos existentes (complementarios)
        tickOrbital(level, intensity);
        tickAbsorbed(level, intensity);
        tickLegacyCore(level, intensity);
        tickGroundRing(level, intensity);
    }

    // ====================================================================
    // BLUE_ORB_CORE — Glow volumétrico principal
    // ====================================================================
    // Spawns: cada 1-2 ticks, 1 partícula en el centro con wobble mínimo.
    // Las partículas son GRANDES (0.5-0.8 bloques), translúcidas, blending aditivo.
    // Múltiples superpuestas = glow intenso que CRECE con power.
    // Resultado: ~12-15 cores activas → campo de glow denso.
    private void tickOrbCore(ClientLevel level, float intensity) {
        if (--orbCoreCooldown > 0) return;
        // A más power, spawn más frecuente: cada 1 tick en power=1, cada 2 en power=0
        orbCoreCooldown = Math.max(1, (int)(2 - intensity));

        RandomSource random = level.random;
        double wobble = 0.06 * intensity;
        double px = centerX + (random.nextDouble() - 0.5) * wobble;
        double py = centerY + (random.nextDouble() - 0.5) * wobble;
        double pz = centerZ + (random.nextDouble() - 0.5) * wobble;

        level.addParticle(JJKParticles.BLUE_ORB_CORE, px, py, pz, 0, 0, 0);

        // Extra core en launched o power alto (más glow = más bloom)
        if ((launched || power > 0.7F) && ticksAlive % 2 == 0) {
            level.addParticle(JJKParticles.BLUE_ORB_CORE, px, py, pz, 0, 0, 0);
        }
    }

    // ====================================================================
    // BLUE_ORB_SPARK — Chispas/rayos de energía
    // ====================================================================
    // Spawns: 2-5 por tick (la DENSIDAD es lo que crea el efecto JAESU).
    // Radio: 0.8 - 3.0 bloques (distribución esférica).
    // Las partículas son MUY pequeñas pero MUCHAS → campo eléctrico.
    // Turbulencia interna + atracción al centro → caos controlado.
    // Resultado: ~40-60 sparks activas → campo eléctrico denso.
    private void tickSparks(ClientLevel level, float intensity) {
        if (--sparkCooldown > 0) return;
        // CADA TICK en power>0.3, cada 2 ticks en power bajo
        sparkCooldown = power > 0.3F ? 1 : 2;

        // Cantidad escala agresivamente con power: 1 en power=0, 5 en power=1
        int count = Math.max(1, (int)(1 + intensity * 4));
        if (launched) count += 2;

        RandomSource random = level.random;

        for (int i = 0; i < count; i++) {
            // Radio de spawn: cerca del núcleo + extensión con power
            double radius = 0.8 + random.nextDouble() * (1.2 + intensity * 1.5);

            // Distribución esférica uniforme
            double u = random.nextDouble();
            double v = random.nextDouble();
            double theta = 2.0 * Math.PI * u;
            double phi = Math.acos(2.0 * v - 1.0);

            double sx = centerX + radius * Math.sin(phi) * Math.cos(theta);
            double sy = centerY + radius * Math.cos(phi) * 0.7; // ligeramente aplastado
            double sz = centerZ + radius * Math.sin(phi) * Math.sin(theta);

            // vx/vy/vz = delta hacia centro (la partícula lo decodifica)
            level.addParticle(JJKParticles.BLUE_ORB_SPARK, sx, sy, sz,
                centerX - sx, centerY - sy, centerZ - sz);
        }
    }

    // ====================================================================
    // BLUE_ORB_ATTRACTION — Streaks largos de atracción
    // ====================================================================
    // Spawns: 1 cada 2-3 ticks desde lejos (2-4 bloques).
    // Viaje largo (~40 ticks) con espiral agresiva → estructura de anillos.
    // Se activan a power > 0.4 (no desde el principio).
    // Resultado: ~16-20 activas → anillos visibles espiralando.
    private void tickAttraction(ClientLevel level, float intensity) {
        if (power < 0.4F) return;
        if (--attractionCooldown > 0) return;
        // Cada 2 ticks en launched, cada 3 normal
        attractionCooldown = launched ? 2 : 3;

        RandomSource random = level.random;

        // Radio grande: estos vienen de lejos
        double radius = 2.0 + random.nextDouble() * 2.0 + intensity * 1.0;
        double angle = random.nextDouble() * Math.PI * 2.0;
        // Variación vertical: no solo un plano
        double heightOff = (random.nextDouble() - 0.5) * 1.2;

        double sx = centerX + Math.cos(angle) * radius;
        double sy = centerY + heightOff;
        double sz = centerZ + Math.sin(angle) * radius;

        level.addParticle(JJKParticles.BLUE_ORB_ATTRACTION, sx, sy, sz,
            centerX - sx, centerY - sy, centerZ - sz);

        // Extra attraction en power alto (más anillos)
        if (power > 0.7F) {
            double angle2 = angle + Math.PI * 0.7;
            double sx2 = centerX + Math.cos(angle2) * radius * 0.8;
            double sz2 = centerZ + Math.sin(angle2) * radius * 0.8;
            level.addParticle(JJKParticles.BLUE_ORB_ATTRACTION, sx2, sy, sz2,
                centerX - sx2, centerY - sy, centerZ - sz2);
        }
    }

    // ====================================================================
    // EXISTENTES — complementan los nuevos tipos
    // ====================================================================

    // BLUE_ORBITAL: espirales inward suaves (anillos de órbita visible)
    private void tickOrbital(ClientLevel level, float intensity) {
        if (power < 0.3F) return;
        if (--orbitalCooldown > 0) return;
        orbitalCooldown = launched ? 2 : Math.max(3, (int)(4 - intensity));

        int count = launched ? 2 : 1;
        RandomSource random = level.random;

        for (int i = 0; i < count; i++) {
            double radius = 1.2 + random.nextDouble() * 0.8 + intensity * 0.3;
            double angle = random.nextDouble() * Math.PI * 2.0;
            double heightOffset = (random.nextDouble() - 0.5) * 0.8;

            double sx = centerX + Math.cos(angle) * radius;
            double sy = centerY + heightOffset;
            double sz = centerZ + Math.sin(angle) * radius;

            level.addParticle(JJKParticles.BLUE_ORBITAL, sx, sy, sz,
                centerX - sx, centerY - sy, centerZ - sz);
        }
    }

    // BLUE_ABSORBED: streaks rápidos (complemento de atracción)
    private void tickAbsorbed(ClientLevel level, float intensity) {
        if (--absorbedCooldown > 0) return;
        absorbedCooldown = launched ? 3 : Math.max(4, (int)(5 - intensity));

        int count = (launched && power > 0.5F) ? 2 : 1;
        RandomSource random = level.random;

        for (int i = 0; i < count; i++) {
            double radius = 1.5 + random.nextDouble() * 1.0;
            double u = random.nextDouble();
            double v = random.nextDouble();
            double theta = 2.0 * Math.PI * u;
            double phi = Math.acos(2.0 * v - 1.0);

            double sx = centerX + radius * Math.sin(phi) * Math.cos(theta);
            double sy = centerY + radius * Math.cos(phi) * 0.5;
            double sz = centerZ + radius * Math.sin(phi) * Math.sin(theta);

            level.addParticle(JJKParticles.BLUE_ABSORBED, sx, sy, sz,
                centerX - sx, centerY - sy, centerZ - sz);
        }
    }

    // BLUE_CORE (legacy): cores pequeñas complementarias
    private void tickLegacyCore(ClientLevel level, float intensity) {
        if (--coreCooldown > 0) return;
        coreCooldown = Math.max(2, (int)(3 - intensity));

        RandomSource random = level.random;
        double wobble = 0.04;
        double px = centerX + (random.nextDouble() - 0.5) * wobble;
        double py = centerY + (random.nextDouble() - 0.5) * wobble;
        double pz = centerZ + (random.nextDouble() - 0.5) * wobble;

        level.addParticle(JJKParticles.BLUE_CORE, px, py, pz, 0, 0, 0);
    }

    // GROUND RING: partículas subiendo desde el suelo
    private void tickGroundRing(ClientLevel level, float intensity) {
        if (power < 0.3F) return;
        if (--groundRingCooldown > 0) return;
        groundRingCooldown = Math.max(2, (int)(4 - intensity));

        double angle = ticksAlive * 0.41;
        double ringRadius = 1.0 + intensity * 1.5;
        double groundY = centerY - 1.2;

        double px = centerX + Math.cos(angle) * ringRadius;
        double pz = centerZ + Math.sin(angle) * ringRadius;

        // Usa ATTRACTION para ground ring (más visual que orbital)
        level.addParticle(JJKParticles.BLUE_ORB_ATTRACTION, px, groundY, pz,
            centerX - px, centerY - groundY, centerZ - pz);

        if (power > 0.6F) {
            double angle2 = angle + Math.PI;
            double px2 = centerX + Math.cos(angle2) * ringRadius * 0.8;
            double pz2 = centerZ + Math.sin(angle2) * ringRadius * 0.8;
            level.addParticle(JJKParticles.BLUE_ORB_ATTRACTION, px2, groundY, pz2,
                centerX - px2, centerY - groundY, centerZ - pz2);
        }
    }
}
