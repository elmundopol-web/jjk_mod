package com.pop.jjk;

/**
 * Configuración centralizada para la técnica Azul (v2).
 *
 * Flujo: CHARGING → LAUNCHED → IMPACT → COOLDOWN
 *
 * CHARGING: orbe flota delante del jugador, atrae y destruye bloques.
 * LAUNCHED: se lanza en la dirección de la cámara con físicas reales.
 * IMPACT:   al colisionar destruye bloques en radio y desaparece.
 */
public final class BlueConfig {

    private BlueConfig() {}

    // ==================== Energía y cooldown ====================
    public static final int COOLDOWN_TICKS = 200;          // 10 segundos

    // ==================== Posición del orbe durante CHARGING ====================
    public static final double CHARGE_FORWARD_OFFSET = 2.2;  // bloques delante del jugador
    public static final double CHARGE_HEIGHT_OFFSET = 1.2;   // altura sobre los pies

    // Spring dynamics: simula peso/inercia real en vez de lerp sin memoria.
    // stiffness baja = más pesado/lento, damping bajo = más amortiguado.
    public static final double SPRING_STIFFNESS = 0.08;      // fuerza del resorte (0.05-0.15 rango útil)
    public static final double SPRING_DAMPING = 0.72;        // retención de energía (0.6-0.85 rango útil)

    // Micro-vibración: simula energía inestable contenida.
    // Usa frecuencias irracionales para que el patrón nunca se repita.
    public static final double VIBRATION_AMPLITUDE = 0.015;  // amplitud base de la vibración

    // ==================== Atracción de bloques durante CHARGING ====================
    public static final double CHARGE_ATTRACT_RADIUS_MIN = 4.0;  // radio inicial
    public static final double CHARGE_ATTRACT_RADIUS_MAX = 8.0;  // radio máximo (con carga)
    public static final double CHARGE_POWER_RATE = 0.005;         // power ganado por tick (200 ticks = 1.0)
    public static final int CHARGE_BLOCK_INTERVAL = 3;            // ticks entre capturas de bloques
    public static final int CHARGE_BLOCKS_PER_TICK_MIN = 2;       // bloques por captura (power=0)
    public static final int CHARGE_BLOCKS_PER_TICK_MAX = 6;       // bloques por captura (power=1)
    public static final double SOFT_BLOCK_DESTROY_SPEED = 50.0;   // dureza máxima que el azul puede destruir
    public static final double PLAYER_SAFE_RADIUS = 3.0;           // radio alrededor del jugador que no se come
    public static final int MAX_FLYING_BLOCKS = 30;               // máximo de bloques volando a la vez
    public static final double FLYING_BLOCK_ACCEL = 0.06;         // aceleración hacia el orbe por tick
    public static final double FLYING_BLOCK_MAX_SPEED = 0.8;      // velocidad máxima del bloque volando
    public static final double FLYING_BLOCK_ARRIVE_DIST = 0.6;    // distancia para considerar que llegó
    public static final int FLYING_BLOCK_MAX_TICKS = 40;          // máximo de vida si no llega (safety)

    // ==================== Pull de entidades durante CHARGING ====================
    public static final double CHARGE_ENTITY_PULL_MIN = 0.12;
    public static final double CHARGE_ENTITY_PULL_MAX = 0.35;
    public static final int CHARGE_DAMAGE_INTERVAL = 20;          // ticks entre daños
    public static final float CHARGE_DAMAGE_MIN = 1.5F;
    public static final float CHARGE_DAMAGE_MAX = 5.0F;

    // ==================== Lanzamiento ====================
    public static final double LAUNCH_SPEED = 1.8;         // velocidad inicial (bloques/tick)
    public static final double LAUNCH_GRAVITY = 0.04;      // gravedad por tick
    public static final double LAUNCH_DRAG = 0.98;         // drag por tick (1.0 = sin drag)
    public static final int LAUNCH_MAX_TICKS = 200;        // máximo de vida en vuelo (10s)

    // ==================== Pull de entidades durante LAUNCHED ====================
    public static final double LAUNCH_PULL_RADIUS = 4.0;
    public static final double LAUNCH_ENTITY_PULL = 0.6;
    public static final float LAUNCH_CONTACT_DAMAGE = 8.0F;

    // ==================== Impacto ====================
    public static final int IMPACT_DESTROY_RADIUS = 2;      // radio en bloques de destrucción al impactar
    public static final float IMPACT_ENTITY_DAMAGE = 12.0F;  // daño a entidades en la zona de impacto
    public static final double IMPACT_ENTITY_RADIUS = 3.0;   // radio de daño a entidades
    public static final double IMPACT_KNOCKBACK = 1.2;        // fuerza de knockback en impacto

    // ==================== Velocidades máximas para entidades ====================
    public static final double MAX_HORIZONTAL_SPEED = 2.0;
    public static final double MAX_VERTICAL_SPEED = 1.0;

    // ==================== Partículas ====================
    public static final double PARTICLE_TRAIL_DENSITY = 3.0;
}
