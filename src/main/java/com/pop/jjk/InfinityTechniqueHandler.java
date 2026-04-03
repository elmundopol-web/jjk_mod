package com.pop.jjk;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;

public final class InfinityTechniqueHandler {

    private static final double OUTER_HORIZONTAL_RANGE = 1.05;
    private static final double OUTER_VERTICAL_RANGE = 0.65;
    private static final double BODY_HORIZONTAL_BUFFER = 0.42;
    private static final double BODY_VERTICAL_BUFFER = 0.3;
    private static final double PROJECTILE_HORIZONTAL_RANGE = 1.4;
    private static final double PROJECTILE_VERTICAL_RANGE = 0.9;
    private static final DustParticleOptions INFINITY_DUST = new DustParticleOptions(0xA8EEFF, 0.65F);
    private static final Map<UUID, Boolean> INFINITY_STATES = new ConcurrentHashMap<>();
    public static final int INFINITY_ENERGY_PER_SECOND = 4;
    private static final int ENERGY_DRAIN_INTERVAL = 20;

    private InfinityTechniqueHandler() {
    }

    public static void setInfinityEnabled(ServerPlayer player, boolean enabled) {
        INFINITY_STATES.put(player.getUUID(), enabled);
    }

    public static void clearAll() {
        INFINITY_STATES.clear();
    }

    public static boolean isInfinityEnabled(ServerPlayer player) {
        return INFINITY_STATES.getOrDefault(player.getUUID(), false);
    }

    public static boolean isActive(UUID playerId) {
        return INFINITY_STATES.getOrDefault(playerId, false);
    }

    public static void tick(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!isInfinityEnabled(player)) {
                continue;
            }

            if (!player.isAlive() || player.isSpectator()) {
                continue;
            }

            // Drenar energía cada segundo
            if (player.tickCount % ENERGY_DRAIN_INTERVAL == 0) {
                if (!CursedEnergyManager.consume(player, INFINITY_ENERGY_PER_SECOND)) {
                    INFINITY_STATES.put(player.getUUID(), false);
                    player.displayClientMessage(Component.translatable("message.jjk.infinity_no_energy"), true);
                    continue;
                }
            }

            applyInfinity(player);
        }
    }

    public static boolean allowDamage(LivingEntity target, DamageSource damageSource, float amount) {
        if (!(target instanceof ServerPlayer player) || !isInfinityEnabled(player)) {
            return true;
        }

        // Infinity bloquea TODO el daño: mobs, proyectiles, estalactitas,
        // magma, warden sonic boom, caída, fuego, ahogamiento, etc.
        // Solo permite /kill (genericKill) para que admins puedan matar.
        if (damageSource.is(DamageTypes.GENERIC_KILL)) {
            return true;
        }

        return false;
    }

    private static void applyInfinity(ServerPlayer player) {
        ServerLevel level = (ServerLevel) player.level();
        AABB playerBox = player.getBoundingBox();
        Vec3 center = playerBox.getCenter();
        AABB mobArea = playerBox.inflate(OUTER_HORIZONTAL_RANGE, OUTER_VERTICAL_RANGE, OUTER_HORIZONTAL_RANGE);
        AABB bodyBarrier = playerBox.inflate(BODY_HORIZONTAL_BUFFER, BODY_VERTICAL_BUFFER, BODY_HORIZONTAL_BUFFER);
        AABB projectileArea = playerBox.inflate(PROJECTILE_HORIZONTAL_RANGE, PROJECTILE_VERTICAL_RANGE, PROJECTILE_HORIZONTAL_RANGE);

        for (Mob mob : level.getEntitiesOfClass(Mob.class, mobArea, Mob::isAlive)) {
            AABB mobBox = mob.getBoundingBox();
            Vec3 mobCenter = mobBox.getCenter();
            Vec3 closestOuterPoint = closestPoint(mobCenter, playerBox);
            Vec3 offset = mobCenter.subtract(closestOuterPoint);
            double distance = offset.length();

            if (distance > OUTER_HORIZONTAL_RANGE + 0.15) {
                continue;
            }

            Vec3 direction = resolveDirection(offset, mobCenter, center);
            double closeness = 1.0 - Math.min(1.0, distance / (OUTER_HORIZONTAL_RANGE + 0.15));
            Vec3 outward = direction.scale(0.08 + (closeness * 0.18));
            Vec3 damped = mob.getDeltaMovement().scale(0.2);

            mob.setDeltaMovement(damped.add(outward.x, 0.015, outward.z));

            if (mobBox.intersects(bodyBarrier)) {
                Vec3 closestBodyPoint = closestPoint(mobCenter, playerBox);
                Vec3 bodyDirection = resolveDirection(mobCenter.subtract(closestBodyPoint), mobCenter, center);
                double pushDistance = computeEscapeDistance(bodyBarrier, mobBox, bodyDirection);
                Vec3 correction = bodyDirection.scale(pushDistance + 0.08);

                mob.move(MoverType.SELF, correction);
                mob.setDeltaMovement(bodyDirection.scale(0.26));
            }
        }

        for (Projectile projectile : level.getEntitiesOfClass(Projectile.class, projectileArea, projectile ->
            projectile.isAlive() && projectile.getOwner() != player
        )) {
            Vec3 projectilePos = projectile.position();
            Vec3 closestPoint = closestPoint(projectilePos, playerBox);
            Vec3 toBarrier = closestPoint.subtract(projectilePos);
            double distance = toBarrier.length();

            if (distance > PROJECTILE_HORIZONTAL_RANGE + 0.2) {
                continue;
            }

            Vec3 velocity = projectile.getDeltaMovement();
            Vec3 towardPlayer = velocity.lengthSqr() > 0.0001 ? velocity.normalize() : Vec3.ZERO;
            Vec3 barrierDirection = distance < 0.0001 ? projectilePos.subtract(center).normalize() : toBarrier.normalize();
            boolean approaching = towardPlayer.dot(barrierDirection) > 0.1;

            if (!approaching && distance > BODY_HORIZONTAL_BUFFER) {
                continue;
            }

            if (distance <= BODY_HORIZONTAL_BUFFER + 0.1) {
                Vec3 direction = resolveDirection(projectilePos.subtract(closestPoint), projectilePos, center);
                Vec3 target = closestPoint.add(direction.scale(BODY_HORIZONTAL_BUFFER + 0.08));
                projectile.setPos(target.x, target.y, target.z);
                projectile.setDeltaMovement(direction.scale(0.03));
            } else {
                projectile.setDeltaMovement(velocity.scale(0.08));
            }
        }

        if (player.tickCount % 6 == 0) {
            spawnInfinityParticles(level, playerBox);
        }
    }

    private static void spawnInfinityParticles(ServerLevel level, AABB playerBox) {
        double radius = (playerBox.getXsize() * 0.5) + BODY_HORIZONTAL_BUFFER;
        double bottomY = playerBox.minY + 0.18;
        double middleY = playerBox.minY + (playerBox.getYsize() * 0.55);
        double topY = playerBox.maxY - 0.08;

        for (int i = 0; i < 12; i++) {
            double angle = (Math.PI * 2.0 * i) / 12.0;
            double x = playerBox.getCenter().x + (Math.cos(angle) * radius);
            double z = playerBox.getCenter().z + (Math.sin(angle) * radius);
            level.sendParticles(INFINITY_DUST, x, bottomY, z, 1, 0.01, 0.01, 0.01, 0.0);
            level.sendParticles(INFINITY_DUST, x, middleY, z, 1, 0.01, 0.02, 0.01, 0.0);
            level.sendParticles(INFINITY_DUST, x, topY, z, 1, 0.01, 0.01, 0.01, 0.0);
        }
    }

    private static Vec3 closestPoint(Vec3 point, AABB box) {
        return new Vec3(
            clamp(point.x, box.minX, box.maxX),
            clamp(point.y, box.minY, box.maxY),
            clamp(point.z, box.minZ, box.maxZ)
        );
    }

    private static Vec3 resolveDirection(Vec3 offset, Vec3 source, Vec3 fallbackCenter) {
        if (offset.lengthSqr() > 0.0001) {
            return offset.normalize();
        }

        Vec3 horizontal = source.subtract(fallbackCenter);

        if ((horizontal.x * horizontal.x) + (horizontal.z * horizontal.z) > 0.0001) {
            return new Vec3(horizontal.x, 0.0, horizontal.z).normalize();
        }

        return new Vec3(1.0, 0.0, 0.0);
    }

    private static double computeEscapeDistance(AABB barrier, AABB mobBox, Vec3 direction) {
        double xDistance = direction.x > 0.0 ? barrier.maxX - mobBox.minX : mobBox.maxX - barrier.minX;
        double zDistance = direction.z > 0.0 ? barrier.maxZ - mobBox.minZ : mobBox.maxZ - barrier.minZ;
        double horizontalDistance = Math.max(0.0, Math.max(xDistance, zDistance));
        return Math.min(0.35, horizontalDistance);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
