package com.pop.jjk;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

/**
 * Helper estático para emitir grupos de partículas con patrones comunes.
 *
 * Todos los métodos operan en el servidor ({@link ServerLevel#sendParticles}).
 * Los parámetros vx/vy/vz se pasan como offset/speed al cliente,
 * donde la partícula los interpreta como velocidad inicial.
 *
 * Patrones disponibles:
 * - {@link #burst}   — Explosión radial uniforme
 * - {@link #ring}    — Anillo horizontal
 * - {@link #sphere}  — Esfera volumétrica
 * - {@link #trail}   — Estela entre dos puntos
 * - {@link #aura}    — Nube suave alrededor de un punto
 * - {@link #spiral}  — Espiral ascendente/descendente
 *
 * Optimización: count por llamada a sendParticles es 1 para que el servidor
 * pueda distribuir las posiciones exactas en vez de usar spread gaussiano.
 */
public final class JJKParticleEmitter {

    private JJKParticleEmitter() {}

    /**
     * Explosión radial. Las partículas salen del centro hacia fuera.
     */
    public static void burst(ServerLevel level, Vec3 center, ParticleOptions particle,
                             int count, double radius, double speed) {
        for (int i = 0; i < count; i++) {
            double phi = Math.acos(1.0 - 2.0 * (i + 0.5) / count);
            double theta = Math.PI * (1.0 + Math.sqrt(5.0)) * i;

            double nx = Math.sin(phi) * Math.cos(theta);
            double ny = Math.cos(phi);
            double nz = Math.sin(phi) * Math.sin(theta);

            double px = center.x + nx * radius * 0.2;
            double py = center.y + ny * radius * 0.2;
            double pz = center.z + nz * radius * 0.2;

            level.sendParticles(particle, px, py, pz, 1,
                nx * speed, ny * speed, nz * speed, speed);
        }
    }

    /**
     * Anillo horizontal de partículas.
     */
    public static void ring(ServerLevel level, Vec3 center, ParticleOptions particle,
                            int count, double radius, double speed) {
        for (int i = 0; i < count; i++) {
            double angle = (Math.PI * 2.0 * i) / count;
            double px = center.x + Math.cos(angle) * radius;
            double pz = center.z + Math.sin(angle) * radius;

            double vx = Math.cos(angle) * speed;
            double vz = Math.sin(angle) * speed;

            level.sendParticles(particle, px, center.y, pz, 1, vx, 0.0, vz, speed);
        }
    }

    /**
     * Esfera volumétrica. Partículas distribuidas uniformemente dentro de una esfera.
     */
    public static void sphere(ServerLevel level, Vec3 center, ParticleOptions particle,
                              int count, double radius) {
        for (int i = 0; i < count; i++) {
            double u = level.random.nextDouble();
            double v = level.random.nextDouble();
            double theta = 2.0 * Math.PI * u;
            double phi = Math.acos(2.0 * v - 1.0);
            double r = radius * Math.cbrt(level.random.nextDouble());

            double px = center.x + r * Math.sin(phi) * Math.cos(theta);
            double py = center.y + r * Math.cos(phi);
            double pz = center.z + r * Math.sin(phi) * Math.sin(theta);

            level.sendParticles(particle, px, py, pz, 1, 0.0, 0.0, 0.0, 0.02);
        }
    }

    /**
     * Estela entre dos puntos. Distribuye partículas uniformemente a lo largo del segmento.
     */
    public static void trail(ServerLevel level, Vec3 from, Vec3 to, ParticleOptions particle,
                             int count, double spread) {
        Vec3 direction = to.subtract(from);
        double length = direction.length();
        if (length < 0.001) return;

        for (int i = 0; i < count; i++) {
            double t = (i + 0.5) / count;
            Vec3 pos = from.lerp(to, t);

            double ox = (level.random.nextDouble() - 0.5) * spread;
            double oy = (level.random.nextDouble() - 0.5) * spread;
            double oz = (level.random.nextDouble() - 0.5) * spread;

            level.sendParticles(particle, pos.x + ox, pos.y + oy, pos.z + oz,
                1, 0.0, 0.0, 0.0, 0.01);
        }
    }

    /**
     * Nube suave (aura) alrededor de un punto. Spread gaussiano natural.
     */
    public static void aura(ServerLevel level, Vec3 center, ParticleOptions particle,
                            int count, double radius) {
        level.sendParticles(particle, center.x, center.y, center.z,
            count, radius * 0.5, radius * 0.35, radius * 0.5, 0.02);
    }

    /**
     * Espiral ascendente/descendente alrededor de un eje vertical.
     */
    public static void spiral(ServerLevel level, Vec3 center, ParticleOptions particle,
                              int count, double radius, double height, double speed) {
        for (int i = 0; i < count; i++) {
            double t = (double) i / count;
            double angle = t * Math.PI * 4.0;
            double r = radius * (1.0 - t * 0.3);

            double px = center.x + Math.cos(angle) * r;
            double py = center.y + t * height;
            double pz = center.z + Math.sin(angle) * r;

            double vx = -Math.sin(angle) * speed;
            double vz = Math.cos(angle) * speed;

            level.sendParticles(particle, px, py, pz, 1, vx, speed * 0.3, vz, speed);
        }
    }
}
