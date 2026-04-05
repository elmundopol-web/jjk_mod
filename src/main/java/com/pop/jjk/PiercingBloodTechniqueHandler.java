package com.pop.jjk;

import java.util.ArrayList;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

public final class PiercingBloodTechniqueHandler {

    private static final int COOLDOWN_TICKS = 25;
    private static final int ENERGY_COST = 80;
    private static final int MIN_CHARGE_TICKS = 10;
    private static final int MAX_CHARGE_TICKS = 20 * 6;
    private static final DustParticleOptions CHARGE_DUST = new DustParticleOptions(0xB80020, 0.35F);

    private static final Map<UUID, Integer> COOLDOWNS = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> CHARGE_STARTS = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> ACTIVE_PROJECTILES = new ConcurrentHashMap<>();
    private static final Set<UUID> AUTO_RELEASE_LOCK = ConcurrentHashMap.newKeySet();

    private PiercingBloodTechniqueHandler() {
    }

    public static void activate(ServerPlayer player) {
        if (!player.isAlive()) {
            return;
        }
        if (!isTechniqueAvailable(player)) {
            return;
        }

        ServerLevel level = (ServerLevel) player.level();
        UUID playerId = player.getUUID();
        cleanupStaleProjectile(player, level);
        if (findActiveProjectile(player, level) != null) {
            return;
        }

        // Si no queda beam activo, cualquier estado residual de carga ya no debe bloquear usos futuros.
        CHARGE_STARTS.remove(playerId);
        AUTO_RELEASE_LOCK.remove(playerId);

        int cooldown = COOLDOWNS.getOrDefault(playerId, 0);
        if (cooldown > 0 && !BlueTechniqueHandler.hasNoCooldown(playerId)) {
            player.displayClientMessage(
                Component.translatable("message.jjk.piercing_blood_cooldown", formatSeconds(cooldown)),
                true
            );
            return;
        }

        if (CHARGE_STARTS.putIfAbsent(playerId, player.tickCount) != null) {
            return;
        }

        spawnChargeParticles(level, player, 1);
    }

    public static void tick() {
        for (UUID playerId : new ArrayList<>(COOLDOWNS.keySet())) {
            if (BlueTechniqueHandler.hasNoCooldown(playerId)) {
                COOLDOWNS.remove(playerId);
                continue;
            }

            Integer current = COOLDOWNS.get(playerId);
            if (current == null) {
                continue;
            }

            int next = current - 1;
            if (next <= 0) {
                COOLDOWNS.remove(playerId);
            } else {
                COOLDOWNS.put(playerId, next);
            }
        }
    }

    public static void clearActive() {
        COOLDOWNS.clear();
        CHARGE_STARTS.clear();
        ACTIVE_PROJECTILES.clear();
        AUTO_RELEASE_LOCK.clear();
    }

    public static void onHold(ServerPlayer player, boolean holding) {
        if (player == null || !player.isAlive()) {
            return;
        }
        if (!isTechniqueAvailable(player)) {
            CHARGE_STARTS.remove(player.getUUID());
            AUTO_RELEASE_LOCK.remove(player.getUUID());
            return;
        }

        ServerLevel level = (ServerLevel) player.level();
        cleanupStaleProjectile(player, level);
        UUID playerId = player.getUUID();

        if (holding) {
            if (AUTO_RELEASE_LOCK.contains(playerId)) {
                return;
            }

            int startTick = CHARGE_STARTS.computeIfAbsent(playerId, ignored -> player.tickCount);
            int chargeTicks = getChargeTicks(player, startTick);
            PiercingBloodProjectileEntity projectile = findActiveProjectile(player, level);

            if (projectile == null) {
                spawnChargeParticles(level, player, chargeTicks);
                if (chargeTicks >= MIN_CHARGE_TICKS) {
                    projectile = trySpawnHeldProjectile(player, level, chargeTicks);
                    if (projectile != null) {
                        projectile.setHeld(true);
                    }
                }
                return;
            }

            projectile.setHeld(true);
            projectile.setChargeDamageMultiplier(getDamageMultiplier(chargeTicks));

            if (chargeTicks >= MAX_CHARGE_TICKS) {
                projectile.setHeld(false);
                CHARGE_STARTS.remove(playerId);
                AUTO_RELEASE_LOCK.add(playerId);
            }
            return;
        }

        AUTO_RELEASE_LOCK.remove(playerId);

        Integer startTick = CHARGE_STARTS.remove(playerId);
        PiercingBloodProjectileEntity projectile = findActiveProjectile(player, level);
        if (startTick == null && projectile == null) {
            return;
        }

        if (projectile == null) {
            int chargeTicks = getChargeTicks(player, startTick);
            if (chargeTicks < MIN_CHARGE_TICKS) {
                return;
            }

            projectile = trySpawnHeldProjectile(player, level, chargeTicks);
            if (projectile == null) {
                return;
            }
        }

        projectile.setHeld(false);
    }

    public static void onProjectileDied(UUID ownerUUID) {
        ACTIVE_PROJECTILES.remove(ownerUUID);
        CHARGE_STARTS.remove(ownerUUID);
        AUTO_RELEASE_LOCK.remove(ownerUUID);
        if (!BlueTechniqueHandler.hasNoCooldown(ownerUUID)) {
            COOLDOWNS.put(ownerUUID, COOLDOWN_TICKS);
        } else {
            COOLDOWNS.remove(ownerUUID);
        }
    }

    public static void clearCooldown(ServerPlayer player) {
        COOLDOWNS.remove(player.getUUID());
    }

    private static void cleanupStaleProjectile(ServerPlayer player, ServerLevel level) {
        Integer projectileId = ACTIVE_PROJECTILES.get(player.getUUID());
        if (projectileId == null) {
            return;
        }

        net.minecraft.world.entity.Entity entity = level.getEntity(projectileId);
        if (!(entity instanceof PiercingBloodProjectileEntity projectile) || !projectile.isAlive()) {
            ACTIVE_PROJECTILES.remove(player.getUUID());
        }
    }

    private static PiercingBloodProjectileEntity findActiveProjectile(ServerPlayer player, ServerLevel level) {
        Integer id = ACTIVE_PROJECTILES.get(player.getUUID());
        if (id != null) {
            net.minecraft.world.entity.Entity entity = level.getEntity(id);
            if (entity instanceof PiercingBloodProjectileEntity projectile && projectile.isAlive()) {
                return projectile;
            }
        }

        for (PiercingBloodProjectileEntity projectile : level.getEntitiesOfClass(
            PiercingBloodProjectileEntity.class,
            player.getBoundingBox().inflate(96.0D),
            entity -> entity.getOwner() == player
        )) {
            ACTIVE_PROJECTILES.put(player.getUUID(), projectile.getId());
            return projectile;
        }

        return null;
    }

    private static boolean isTechniqueAvailable(ServerPlayer player) {
        return JJKRoster.techniquesForCharacter(CharacterSelectionHandler.getSelectedCharacter(player)).stream()
            .anyMatch(definition -> definition.id().equals("piercing_blood"));
    }

    private static int getChargeTicks(ServerPlayer player, int startTick) {
        return Math.min(MAX_CHARGE_TICKS, Math.max(0, (player.tickCount - startTick) + 1));
    }

    private static void spawnChargeParticles(ServerLevel level, ServerPlayer player, int chargeTicks) {
        Vec3 dir = player.getLookAngle().normalize();
        Vec3 origin = player.getEyePosition().add(dir.scale(0.55D));
        float buildup = Math.min(chargeTicks, MIN_CHARGE_TICKS) / (float) MIN_CHARGE_TICKS;
        double radius = 0.05D + (buildup * 0.11D);
        int count = 4 + Math.round(buildup * 6.0F);
        level.sendParticles(CHARGE_DUST, origin.x, origin.y, origin.z, count, radius, radius, radius, 0.01D);
    }

    private static PiercingBloodProjectileEntity trySpawnHeldProjectile(ServerPlayer player, ServerLevel level, int chargeTicks) {
        int cooldown = COOLDOWNS.getOrDefault(player.getUUID(), 0);
        if (cooldown > 0 && !BlueTechniqueHandler.hasNoCooldown(player.getUUID())) {
            player.displayClientMessage(
                Component.translatable("message.jjk.piercing_blood_cooldown", formatSeconds(cooldown)),
                true
            );
            return null;
        }

        if (!CursedEnergyManager.consume(player, ENERGY_COST)) {
            player.displayClientMessage(Component.translatable("message.jjk.not_enough_energy"), true);
            return null;
        }

        Vec3 dir = player.getLookAngle().normalize();
        Vec3 origin = player.getEyePosition().add(dir.scale(0.55D));
        float damageMultiplier = getDamageMultiplier(chargeTicks);

        level.playSound(null, player.blockPosition(), SoundEvents.ARROW_SHOOT, SoundSource.PLAYERS, 1.1F, 1.90F);
        level.playSound(null, player.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.7F, 1.80F);
        DustParticleOptions burst = new DustParticleOptions(0xFF0505, 0.50F + ((damageMultiplier - 1.0F) * 0.25F));
        level.sendParticles(burst, origin.x, origin.y, origin.z, 10, 0.05D, 0.05D, 0.05D, 0.08D);

        PiercingBloodProjectileEntity projectile = new PiercingBloodProjectileEntity(level, player);
        projectile.setChargeDamageMultiplier(damageMultiplier);
        level.addFreshEntity(projectile);
        ACTIVE_PROJECTILES.put(player.getUUID(), projectile.getId());
        return projectile;
    }

    private static float getDamageMultiplier(int chargeTicks) {
        float chargeFactor = (chargeTicks - MIN_CHARGE_TICKS) / (float) (MAX_CHARGE_TICKS - MIN_CHARGE_TICKS);
        return 1.0F + (Math.max(0.0F, Math.min(1.0F, chargeFactor)) * 0.8F);
    }

    private static String formatSeconds(int ticks) {
        return String.format(Locale.ROOT, "%.1f", ticks / 20.0D);
    }
}
