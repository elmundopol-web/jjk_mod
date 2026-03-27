package com.pop.jjk;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Motor de comportamiento del orbe Azul durante la fase CHARGING.
 *
 * Separa la lógica en tres ejes independientes, priorizando sensación visual y fluidez:
 *
 * 1. POSICIÓN — Spring dynamics con inercia + micro-vibración orgánica
 *    Por qué: Un lerp simple (pos += (target-pos)*k) no tiene memoria: el orbe
 *    se "pega" a la cámara sin peso. Un sistema masa-resorte-amortiguador produce
 *    overshoot natural, settling gradual, y sensación de MASA real.
 *    La micro-vibración Lissajous (frecuencias irracionales entre sí) crea un
 *    patrón que nunca se repite visualmente, simulando energía inestable contenida.
 *
 * 2. PARTÍCULAS — Flujo continuo posicionado con intención
 *    Por qué: Un burst de partículas random parece una explosión, no un campo.
 *    Emitir 1-2 partículas por tick en posiciones calculadas (espiral paramétrica,
 *    contra-rotación) crea un flujo coherente y continuo. El presupuesto se mantiene
 *    bajo (~15-20 partículas activas) distribuyendo la carga en el tiempo.
 *
 * 3. ATRACCIÓN — Curva inversa-cuadrada + smoothstep + componente tangencial
 *    Por qué: Una fuerza lineal se siente mecánica. La inversa-cuadrada
 *    (F ∝ 1/d²) da aceleración natural al acercarse. smoothstep en el borde
 *    elimina el "pop" al entrar al radio. La componente tangencial crea
 *    approach en espiral — más orgánico que una línea recta.
 *
 * Cada sub-sistema es independiente, testeable, y configurable vía BlueConfig.
 */
public final class BlueAbility {

    // Velocidad del spring (persiste entre ticks = inercia)
    private Vec3 springVelocity = Vec3.ZERO;

    // Fases aleatorias para Lissajous (únicas por instancia,
    // así múltiples orbes no vibran en sincronía)
    private final double phaseX;
    private final double phaseY;
    private final double phaseZ;

    public BlueAbility() {
        this.phaseX = Math.random() * Math.PI * 2;
        this.phaseY = Math.random() * Math.PI * 2;
        this.phaseZ = Math.random() * Math.PI * 2;
    }

    /**
     * Tick principal. Ejecuta los tres subsistemas en orden.
     * Llamado cada tick durante CHARGING desde BlueTechniqueHandler.
     */
    public void tick(ActiveBlue ab, ServerPlayer owner) {
        Vec3 target = computeTarget(owner);
        updatePosition(ab, target);
        updateParticles(ab);
        updateAttraction(ab, owner);
    }

    // =====================================================================
    // 1. POSICIÓN: Spring Dynamics + Micro-Vibración
    // =====================================================================
    //
    // En vez de:  pos += (target - pos) * 0.25    ← lerp: sin memoria, sin peso
    //
    // Usamos un sistema masa-resorte-amortiguador:
    //   fuerza  = (target - pos) * stiffness       ← tira hacia el target
    //   vel     = (vel + fuerza) * damping          ← inercia + fricción
    //   pos     += vel                              ← movimiento
    //
    // La velocidad PERSISTE entre ticks. Esto es clave:
    // - Si la cámara gira rápido, el orbe acumula velocidad y SOBREPASA
    // - Luego la fuerza lo corrige, creando oscilación amortiguada
    // - Resultado: sensación de peso, inercia, y energía contenida
    //
    // Encima del spring, la micro-vibración Lissajous:
    // Tres senos con frecuencias en ratio ~dorado (0.17, 0.23, 0.19...)
    // para que el patrón NUNCA se repita visualmente. Cada eje tiene
    // dos armónicos sumados para complejidad orgánica.
    // Se escala con power: más energía acumulada = más inestable.

    private void updatePosition(ActiveBlue ab, Vec3 target) {
        // Spring force: desplazamiento * rigidez
        Vec3 displacement = target.subtract(ab.position);
        Vec3 force = displacement.scale(BlueConfig.SPRING_STIFFNESS);

        // Integrar: velocidad acumula fuerza, luego se amortigua
        springVelocity = springVelocity.add(force).scale(BlueConfig.SPRING_DAMPING);

        // Aplicar movimiento
        ab.previousPosition = ab.position;
        ab.position = ab.position.add(springVelocity);

        // --- Micro-vibración Lissajous multi-armónico ---
        // Frecuencias primarias: 0.17, 0.23, 0.19 (irracionales entre sí)
        // Frecuencias secundarias: 0.31, 0.41, 0.37 (más rápidas, menor amplitud)
        // Esto crea un patrón 3D que parece aleatorio pero es determinista y suave.
        double t = ab.ticksAlive;
        double power = ab.getCurrentPower();
        double amp = BlueConfig.VIBRATION_AMPLITUDE * (0.5 + power * 0.5);

        double vx = Math.sin(t * 0.17 + phaseX) * amp
                   + Math.sin(t * 0.31 + phaseX * 1.7) * amp * 0.5;
        double vy = Math.sin(t * 0.23 + phaseY) * amp * 1.2
                   + Math.cos(t * 0.41 + phaseY * 0.6) * amp * 0.6;
        double vz = Math.cos(t * 0.19 + phaseZ) * amp
                   + Math.sin(t * 0.37 + phaseZ * 1.3) * amp * 0.5;

        ab.position = ab.position.add(vx, vy, vz);
    }

    // =====================================================================
    // 2. PARTÍCULAS: Flujo Continuo con Intención
    // =====================================================================
    //
    // Regla: NUNCA burst. SIEMPRE flujo.
    //
    // Cada tick emite 1-2 partículas en posiciones CALCULADAS:
    // - Orbital primaria: ángulo = f(tick), no random.
    //   Crea un anillo visible que GIRA alrededor del orbe.
    // - Orbital secundaria (contra-rotación): se activa con power > 0.3.
    //   Gira en sentido opuesto → interferencia visual = complejidad.
    // - Wisps de absorción: cada 4 ticks, desde un anillo exterior.
    //   Simula materia siendo succionada desde lejos.
    // - Energía ambiental: cada 5 ticks, cerca del núcleo.
    //   Rellena el volumen interior del efecto.
    //
    // Presupuesto total: ~15-20 partículas activas simultáneas.
    // Distribuidas en el tiempo = flujo, no puffs discretos.

    private void updateParticles(ActiveBlue ab) {
        int tick = ab.ticksAlive;
        double power = ab.getCurrentPower();
        ServerLevel level = ab.level;

        // --- Orbital primaria: 1 partícula/tick en espiral paramétrica ---
        // ángulo avanza 0.28 rad/tick ≈ 22.4 ticks por revolución
        double orbAngle = tick * 0.28;
        double orbRadius = 0.8 + power * 0.4;
        double orbHeight = Math.sin(tick * 0.13) * 0.25;

        level.sendParticles(JJKParticles.BLUE_ENERGY,
            ab.position.x + Math.cos(orbAngle) * orbRadius,
            ab.position.y + orbHeight,
            ab.position.z + Math.sin(orbAngle) * orbRadius,
            1, 0.02, 0.02, 0.02, 0.005);

        // --- Orbital secundaria (contra-rotación) ---
        // Gira al revés con velocidad diferente → patrón de interferencia
        if (power > 0.3 && tick % 2 == 0) {
            double orbAngle2 = -tick * 0.22 + Math.PI;
            double orbRadius2 = 0.6 + power * 0.3;

            level.sendParticles(JJKParticles.BLUE_ENERGY,
                ab.position.x + Math.cos(orbAngle2) * orbRadius2,
                ab.position.y + Math.cos(tick * 0.17) * 0.2,
                ab.position.z + Math.sin(orbAngle2) * orbRadius2,
                1, 0.02, 0.02, 0.02, 0.005);
        }

        // --- Wisps de absorción: desde anillo exterior hacia dentro ---
        // Cada 4 ticks, en ángulo calculado (no random)
        if (tick % 4 == 0) {
            double absAngle = tick * 0.43;
            double absRadius = 1.5 + power * 0.5;

            level.sendParticles(JJKParticles.BLUE_ENERGY,
                ab.position.x + Math.cos(absAngle) * absRadius,
                ab.position.y + Math.sin(tick * 0.09) * 0.15,
                ab.position.z + Math.sin(absAngle) * absRadius,
                1, 0.01, 0.01, 0.01, 0.01);
        }

        // --- Energía ambiental interior ---
        // Cada 5 ticks, muy cerca del núcleo. Rellena el volumen.
        if (tick % 5 == 0) {
            double wispAngle = tick * 0.13;
            double wispDist = 0.3 + power * 0.2;

            level.sendParticles(JJKParticles.BLUE_ENERGY,
                ab.position.x + Math.cos(wispAngle) * wispDist,
                ab.position.y + Math.sin(tick * 0.09) * 0.12,
                ab.position.z + Math.sin(wispAngle) * wispDist,
                1, 0.03, 0.03, 0.03, 0.003);
        }

        // --- Debris del suelo: polvo/partículas subiendo por gravedad invertida ---
        // Vende la fantasía de "pozo gravitacional": el suelo se desintegra hacia arriba.
        // Cada 3 ticks, desde un anillo a nivel del suelo bajo el orbe.
        // El radio crece con power (más energía = más área de efecto visible).
        if (tick % 3 == 0) {
            double debrisAngle = tick * 0.37;
            double debrisRadius = 1.0 + power * 2.5;
            // Buscar el suelo real bajo el orbe
            double groundY = ab.position.y - 2.0;

            level.sendParticles(JJKParticles.BLUE_ENERGY,
                ab.position.x + Math.cos(debrisAngle) * debrisRadius,
                groundY,
                ab.position.z + Math.sin(debrisAngle) * debrisRadius,
                1, 0.05, 0.2, 0.05, 0.015);

            // Segundo punto de debris en ángulo opuesto (simetría visual)
            if (power > 0.4) {
                level.sendParticles(JJKParticles.BLUE_ENERGY,
                    ab.position.x + Math.cos(debrisAngle + Math.PI) * debrisRadius * 0.7,
                    groundY,
                    ab.position.z + Math.sin(debrisAngle + Math.PI) * debrisRadius * 0.7,
                    1, 0.05, 0.15, 0.05, 0.012);
            }
        }

        // --- Líneas radiales de estrés: cada 7 ticks ---
        // Partículas a medio camino entre suelo y orbe, moviéndose hacia arriba.
        // Da la sensación de "columna" de atracción.
        if (tick % 7 == 0 && power > 0.2) {
            double stressAngle = tick * 0.53;
            double stressRadius = 0.4 + power * 0.8;
            double midY = ab.position.y - 1.0;

            level.sendParticles(JJKParticles.BLUE_ENERGY,
                ab.position.x + Math.cos(stressAngle) * stressRadius,
                midY,
                ab.position.z + Math.sin(stressAngle) * stressRadius,
                1, 0.02, 0.3, 0.02, 0.008);
        }
    }

    // =====================================================================
    // 3. ATRACCIÓN: Curva Inversa-Cuadrada Suave
    // =====================================================================
    //
    // Tres mejoras sobre la atracción lineal anterior:
    //
    // A) Inversa-cuadrada: F ∝ R² / (d² + 1)
    //    Es la ley de gravedad real. Más cerca = exponencialmente más fuerte.
    //    El +1 en el denominador evita singularidad cuando d→0.
    //
    // B) smoothstep en el borde: sin esto, la fuerza "aparece" de golpe
    //    cuando una entidad cruza el radio. Con smoothstep, la fuerza
    //    transiciona de 0 a full en ~30% del borde → entrada suave.
    //
    // C) Componente tangencial: además de tirar hacia el centro,
    //    aplicamos una fuerza perpendicular sutil (30% de la radial).
    //    Esto hace que las entidades se acerquen en ESPIRAL.
    //    Visualmente mucho más interesante que una línea recta.

    private void updateAttraction(ActiveBlue ab, ServerPlayer owner) {
        double power = ab.getCurrentPower();
        double pullRadius = ab.getAttractRadius();
        double basePull = lerp(BlueConfig.CHARGE_ENTITY_PULL_MIN, BlueConfig.CHARGE_ENTITY_PULL_MAX, power);
        boolean shouldDamage = ab.ticksAlive % BlueConfig.CHARGE_DAMAGE_INTERVAL == 0;
        float damage = lerp(BlueConfig.CHARGE_DAMAGE_MIN, BlueConfig.CHARGE_DAMAGE_MAX, (float) power);

        AABB area = new AABB(ab.position, ab.position).inflate(pullRadius);

        // --- Mobs: atracción completa + daño ---
        for (Mob mob : ab.level.getEntitiesOfClass(Mob.class, area, Mob::isAlive)) {
            Vec3 center = mob.position().add(0, mob.getBbHeight() * 0.5, 0);
            Vec3 toOrb = ab.position.subtract(center);
            double dist = toOrb.length();
            if (dist < 0.1) continue;

            Vec3 pull = computePullForce(toOrb, dist, pullRadius, basePull);
            mob.push(pull.x, pull.y * 0.5 + 0.03, pull.z);
            mob.setDeltaMovement(clampVelocity(mob.getDeltaMovement()));

            if (shouldDamage) {
                mob.hurtServer(ab.level, ab.level.damageSources().playerAttack(owner), damage);
            }
        }

        // --- Items: misma curva pero más ligera (×0.6) ---
        for (ItemEntity item : ab.level.getEntitiesOfClass(ItemEntity.class, area, ItemEntity::isAlive)) {
            Vec3 toOrb = ab.position.subtract(item.position());
            double dist = toOrb.length();
            if (dist < 0.1) continue;

            Vec3 pull = computePullForce(toOrb, dist, pullRadius, basePull * 0.6);
            item.setDeltaMovement(item.getDeltaMovement().scale(0.85).add(pull));
            item.hurtMarked = true;
        }
    }

    /**
     * Calcula la fuerza de atracción para una entidad.
     * Combina: inversa-cuadrada + smoothstep + tangencial.
     */
    private Vec3 computePullForce(Vec3 toOrb, double dist, double pullRadius, double basePull) {
        // smoothstep: fade-in suave en el borde del radio
        double edgeFade = smoothstep(pullRadius, pullRadius * 0.7, dist);

        // Inversa-cuadrada: fuerza crece cuadráticamente al acercarse
        double invSq = (pullRadius * pullRadius) / (dist * dist + 1.0);
        double strength = basePull * invSq * edgeFade;

        // Componente radial (hacia el centro)
        Vec3 radial = toOrb.normalize().scale(strength);

        // Componente tangencial (perpendicular, crea espiral)
        // 30% de la fuerza radial, en el plano horizontal
        Vec3 tangent = new Vec3(-toOrb.z, 0, toOrb.x).normalize().scale(strength * 0.3);

        return radial.add(tangent);
    }

    // =====================================================================
    // UTILIDADES
    // =====================================================================

    /**
     * Calcula la posición objetivo del orbe basada en la cámara del jugador.
     * Incluye componente vertical (arriba/abajo) con clamp.
     */
    static Vec3 computeTarget(ServerPlayer player) {
        Vec3 look = player.getLookAngle();
        double clampedY = Math.max(-0.6, Math.min(0.6, look.y));
        Vec3 direction = new Vec3(look.x, clampedY, look.z);
        if (direction.lengthSqr() < 0.0001) direction = new Vec3(1, 0, 0);
        direction = direction.normalize();
        return player.position()
            .add(0, BlueConfig.CHARGE_HEIGHT_OFFSET, 0)
            .add(direction.scale(BlueConfig.CHARGE_FORWARD_OFFSET));
    }

    /**
     * Hermite smoothstep: transición suave de 1→0 entre edge1 y edge0.
     * Usado para fade-in de la atracción en el borde del radio.
     */
    private static double smoothstep(double edge0, double edge1, double x) {
        double t = Math.max(0, Math.min(1, (x - edge1) / (edge0 - edge1)));
        return t * t * (3 - 2 * t);
    }

    private static Vec3 clampVelocity(Vec3 vel) {
        double h = Math.sqrt(vel.x * vel.x + vel.z * vel.z);
        double hs = h > BlueConfig.MAX_HORIZONTAL_SPEED ? BlueConfig.MAX_HORIZONTAL_SPEED / h : 1.0;
        double v = Math.max(-BlueConfig.MAX_VERTICAL_SPEED, Math.min(BlueConfig.MAX_VERTICAL_SPEED, vel.y));
        return new Vec3(vel.x * hs, v, vel.z * hs);
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }
}
