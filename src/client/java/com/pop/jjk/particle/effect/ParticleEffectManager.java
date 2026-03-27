package com.pop.jjk.particle.effect;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manager centralizado para efectos de partículas compuestos (client-side).
 *
 * Responsabilidades:
 * - Almacena efectos activos indexados por entityId (O(1) lookup)
 * - Tick global: actualiza todos los efectos activos cada client tick
 * - Limpieza automática de efectos expirados
 * - Previene duplicados: un entityId solo puede tener un efecto activo
 *
 * Uso desde JJKClientMod:
 * - {@link #tick()} se llama en END_CLIENT_TICK
 * - {@link #startGravityWell} / {@link #updateGravityWell} se llaman
 *   desde el renderer del BlueOrb o un tick handler
 * - {@link #stopGravityWell} cuando el orb desaparece
 *
 * Thread safety: usa ConcurrentHashMap porque el renderer corre
 * en el render thread pero tick() en el client thread.
 *
 * Optimización:
 * - Máximo de efectos simultáneos limitado (evita leak si algo falla)
 * - Cleanup automático cada tick
 * - Los efectos individuales ya rate-limitan sus partículas internamente
 */
public final class ParticleEffectManager {

    private static final int MAX_ACTIVE_EFFECTS = 16;

    private static final Map<Integer, GravityWellEffect> gravityWells = new ConcurrentHashMap<>();

    private ParticleEffectManager() {}

    /**
     * Tick global. Llamar desde END_CLIENT_TICK.
     */
    public static void tick() {
        tickGravityWells();
    }

    /**
     * Limpia todos los efectos (ej: al desconectar del servidor).
     */
    public static void clear() {
        gravityWells.clear();
    }

    // ==================== GRAVITY WELL API ====================

    /**
     * Inicia o actualiza un efecto de Gravity Well para un entity.
     * Si ya existe para ese entityId, actualiza posición/power.
     * Si no existe y hay espacio, crea uno nuevo.
     */
    public static void updateGravityWell(int entityId, double x, double y, double z,
                                          float power, boolean launched) {
        GravityWellEffect existing = gravityWells.get(entityId);
        if (existing != null && !existing.isExpired()) {
            existing.update(x, y, z, power, launched);
        } else if (gravityWells.size() < MAX_ACTIVE_EFFECTS) {
            gravityWells.put(entityId, new GravityWellEffect(entityId, x, y, z, power, launched));
        }
    }

    /**
     * Detiene el efecto de Gravity Well para un entity.
     */
    public static void stopGravityWell(int entityId) {
        GravityWellEffect effect = gravityWells.remove(entityId);
        if (effect != null) {
            effect.expire();
        }
    }

    /**
     * Consulta si hay un Gravity Well activo para un entity.
     */
    public static boolean hasGravityWell(int entityId) {
        GravityWellEffect effect = gravityWells.get(entityId);
        return effect != null && !effect.isExpired();
    }

    // ==================== INTERNAL ====================

    private static void tickGravityWells() {
        Iterator<Map.Entry<Integer, GravityWellEffect>> it = gravityWells.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, GravityWellEffect> entry = it.next();
            GravityWellEffect effect = entry.getValue();

            if (effect.isExpired()) {
                it.remove();
                continue;
            }

            effect.tick();
        }
    }
}
