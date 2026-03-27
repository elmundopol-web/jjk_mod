package com.pop.jjk;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

/**
 * Efectos visuales para la técnica Azul (v2).
 */
public final class BlueEffects {

    private BlueEffects() {}

    public static final DustParticleOptions BLUE_DUST = new DustParticleOptions(0x3FA7FF, 1.3F);
    public static final DustParticleOptions BLUE_DUST_SMALL = new DustParticleOptions(0x9AD8FF, 0.85F);

    /**
     * Trail de partículas durante el vuelo del orbe lanzado.
     */
    public static void spawnLaunchTrail(ServerLevel level, Vec3 from, Vec3 to, double power) {
        Vec3 dir = to.subtract(from);
        double distance = dir.length();
        if (distance < 0.01) return;

        int count = Math.max(3, (int) Math.ceil(distance * BlueConfig.PARTICLE_TRAIL_DENSITY * (2 + power * 2)));
        JJKParticleEmitter.trail(level, from, to, JJKParticles.BLUE_ENERGY, count, 0.15);

        // Aura periódica alrededor del orbe en vuelo
        if (level.getServer().getTickCount() % 2 == 0) {
            int auraCount = 4 + (int)(power * 6);
            JJKParticleEmitter.ring(level, to, JJKParticles.BLUE_ENERGY, auraCount, 0.6 + power * 0.3, 0.02);
        }
    }
}
