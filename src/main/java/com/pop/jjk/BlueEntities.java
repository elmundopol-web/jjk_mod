package com.pop.jjk;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Display;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Estados de la técnica Azul (v2).
 */
enum BlueState {
    CHARGING,
    LAUNCHED
}

/**
 * Representa una instancia activa de Azul en el mundo (v2).
 */
final class ActiveBlue {
    final UUID ownerId;
    final ServerLevel level;
    final List<FlyingBlock> flyingBlocks = new ArrayList<>();
    final BlueAbility ability = new BlueAbility();
    BlueOrbEntity orbEntity;
    Vec3 previousPosition;
    Vec3 position;
    Vec3 velocity = Vec3.ZERO;
    BlueState state = BlueState.CHARGING;
    double power;
    int ticksAlive;
    int ticksInState;

    ActiveBlue(UUID ownerId, ServerLevel level, Vec3 position, BlueOrbEntity orbEntity) {
        this.ownerId = ownerId;
        this.level = level;
        this.orbEntity = orbEntity;
        this.previousPosition = position;
        this.position = position;
    }

    void launch(Vec3 direction) {
        this.state = BlueState.LAUNCHED;
        this.velocity = direction.normalize().scale(BlueConfig.LAUNCH_SPEED);
        this.ticksInState = 0;
    }

    double getCurrentPower() {
        return Math.min(1.0, power);
    }

    double getAttractRadius() {
        double p = getCurrentPower();
        return BlueConfig.CHARGE_ATTRACT_RADIUS_MIN +
            (BlueConfig.CHARGE_ATTRACT_RADIUS_MAX - BlueConfig.CHARGE_ATTRACT_RADIUS_MIN) * p;
    }
}

/**
 * Bloque arrancado que vuela hacia el orbe antes de destruirse.
 * Usa un BlockDisplay para el visual y se mueve con aceleración hacia el centro.
 */
final class FlyingBlock {
    final Display.BlockDisplay display;
    final BlockState blockState;
    final Vec3 origin;
    Vec3 position;
    Vec3 velocity;
    int ticksAlive;
    boolean arrived;

    FlyingBlock(Display.BlockDisplay display, BlockState blockState, Vec3 origin) {
        this.display = display;
        this.blockState = blockState;
        this.origin = origin;
        this.position = origin;
        this.velocity = Vec3.ZERO;
        this.ticksAlive = 0;
        this.arrived = false;
    }
}
