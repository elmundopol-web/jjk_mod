package com.pop.jjk;

public final class FugaTechnique {

    private FugaTechnique() {}

    // Energía y cooldown
    public static final int ENERGY_COST = 730;           // 650-750 objetivo
    public static final int COOLDOWN_TICKS = 240;        // 12s (más agresivo)

    // Charge (hold)
    public static final int CHARGE_MIN_TICKS = 12;       // 0.6s
    public static final int OVERCHARGE_TICKS = 40;       // 2.0s

    // Rayo
    public static final double SPEED = 4.2;              // base
    public static final double MAX_DISTANCE = 76.0;      // base
    public static final double PATH_RADIUS = 1.90;       // base
    public static final double IGNITE_RADIUS = 1.40;     // base

    // Daños
    public static final float PATH_DAMAGE = 20.0F;       // base
    public static final float IMPACT_DAMAGE = 54.0F;     // base

    // Explosión
    public static final double IMPACT_RADIUS = 13.0;     // base
    public static final double KNOCKBACK = 1.95;         // base

    // Overcharge absolute values
    public static final double OVR_SPEED = 4.8;
    public static final double OVR_MAX_DISTANCE = 84.0;
    public static final double OVR_PATH_RADIUS = 2.10;
    public static final double OVR_IGNITE_RADIUS = 1.60;
    public static final float OVR_PATH_DAMAGE = 24.0F;
    public static final float OVR_IMPACT_DAMAGE = 64.0F;
    public static final double OVR_IMPACT_RADIUS = 15.0;
    public static final double OVR_KNOCKBACK = 2.20;
}
