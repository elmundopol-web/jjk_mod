package com.pop.jjk;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

public final class PiercingBloodTechniqueHandler {

    private static final int COOLDOWN_TICKS = 25;
    private static final int ENERGY_COST = 80;
    private static final int MIN_CHARGE_TICKS = 10;
    private static final int MAX_CHARGE_TICKS = 200;
    private static final int BEAM_DURATION_TICKS = 300;
    private static final DustParticleOptions CHARGE_DUST = new DustParticleOptions(0xB80020, 0.35F);

    private static final Map<UUID, Integer> CHARGE_STARTS = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> ACTIVE_PROJECTILES = new ConcurrentHashMap<>();

    private PiercingBloodTechniqueHandler() {
    }

    public static void activate(ServerPlayer player) {
        onHold(player, true);
    }

    public static void onHold(ServerPlayer player, boolean holding) {
        if (player == null || !player.isAlive()) {
            return;
        }
        if (!isTechniqueAvailable(player)) {
            CHARGE_STARTS.remove(player.getUUID());
            return;
        }

        ServerLevel level = (ServerLevel) player.level();
        UUID playerId = player.getUUID();
        cleanupStaleProjectile(player, level);

        if (holding) {
            int cooldown = TechniqueCooldownManager.getRemaining(playerId);
            if (cooldown > 0 && !BlueTechniqueHandler.hasNoCooldown(playerId)) {
                CHARGE_STARTS.remove(playerId);
                player.displayClientMessage(
                    Component.translatable("message.jjk.piercing_blood_cooldown", formatSeconds(cooldown)),
                    true
                );
                return;
            }

            int startTick = CHARGE_STARTS.computeIfAbsent(playerId, ignored -> player.tickCount);
            int chargeTicks = getChargeTicks(player, startTick);
            spawnChargeParticles(level, player, chargeTicks);
            playChargeHeartbeat(level, player, chargeTicks);

            if (chargeTicks >= MAX_CHARGE_TICKS) {
                CHARGE_STARTS.remove(playerId);
                spawnBeam(player, level, chargeTicks);
            }
            return;
        }

        Integer startTick = CHARGE_STARTS.remove(playerId);
        if (startTick == null) {
            return;
        }

        int chargeTicks = getChargeTicks(player, startTick);
        if (chargeTicks < MIN_CHARGE_TICKS) {
            return;
        }

        spawnBeam(player, level, chargeTicks);
    }

    public static void tick(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            UUID playerId = player.getUUID();
            ServerLevel level = (ServerLevel) player.level();
            cleanupStaleProjectile(player, level);

            Integer startTick = CHARGE_STARTS.get(playerId);
            if (startTick != null && (player.tickCount - startTick) > (MAX_CHARGE_TICKS + 40)) {
                CHARGE_STARTS.remove(playerId);
            }
        }
    }

    public static void clearActive() {
        CHARGE_STARTS.clear();
        ACTIVE_PROJECTILES.clear();
    }

    public static void clearCooldown(ServerPlayer player) {
        TechniqueCooldownManager.clear(player);
    }

    public static void onProjectileDied(UUID ownerUUID) {
        ACTIVE_PROJECTILES.remove(ownerUUID);
        if (!BlueTechniqueHandler.hasNoCooldown(ownerUUID)) {
            int current = TechniqueCooldownManager.getRemaining(ownerUUID);
            int target = Math.max(current, COOLDOWN_TICKS);
            TechniqueCooldownManager.set(ownerUUID, target, target);
        } else {
            TechniqueCooldownManager.clear(ownerUUID);
        }
    }

    public static void onProjectileReplaced(UUID ownerUUID) {
        ACTIVE_PROJECTILES.remove(ownerUUID);
    }

    private static void spawnBeam(ServerPlayer player, ServerLevel level, int chargeTicks) {
        UUID playerId = player.getUUID();

        PiercingBloodProjectileEntity existing = findActiveProjectile(player, level);
        if (existing != null) {
            existing.suppressCooldownOnRemoval();
            ACTIVE_PROJECTILES.remove(playerId);
            existing.discard();
            cleanupStaleProjectile(player, level);
        }

        int cooldown = TechniqueCooldownManager.getRemaining(playerId);
        if (cooldown > 0 && !BlueTechniqueHandler.hasNoCooldown(playerId)) {
            player.displayClientMessage(
                Component.translatable("message.jjk.piercing_blood_cooldown", formatSeconds(cooldown)),
                true
            );
            return;
        }

        if (!CursedEnergyManager.consume(player, ENERGY_COST)) {
            player.displayClientMessage(Component.translatable("message.jjk.not_enough_energy"), true);
            return;
        }

        float chargeFactor = Math.max(0.0F, Math.min(1.0F,
            (chargeTicks - MIN_CHARGE_TICKS) / (float) (MAX_CHARGE_TICKS - MIN_CHARGE_TICKS)));

        Vec3 dir = player.getLookAngle().normalize();
        Vec3 origin = player.getEyePosition().add(dir.scale(0.55D));

        level.playSound(null, player.blockPosition(), SoundEvents.ARROW_SHOOT, SoundSource.PLAYERS, 1.1F, 1.9F);
        level.playSound(null, player.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.7F, 1.8F);
        DustParticleOptions burst = new DustParticleOptions(0xFF0505, 0.5F + (chargeFactor * 0.25F));
        level.sendParticles(burst, origin.x, origin.y, origin.z, 10, 0.05D, 0.05D, 0.05D, 0.08D);

        PiercingBloodProjectileEntity beam = new PiercingBloodProjectileEntity(level, player);
        beam.configure(chargeFactor, BEAM_DURATION_TICKS);
        level.addFreshEntity(beam);
        ACTIVE_PROJECTILES.put(playerId, beam.getId());
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
        float buildup = Math.min(chargeTicks, MAX_CHARGE_TICKS) / (float) MAX_CHARGE_TICKS;
        double radius = 0.05D + (buildup * 0.18D);
        int count = 4 + Math.round(buildup * 10.0F);
        level.sendParticles(CHARGE_DUST, origin.x, origin.y, origin.z, count, radius, radius, radius, 0.01D);
    }

    private static void playChargeHeartbeat(ServerLevel level, ServerPlayer player, int chargeTicks) {
        if ((chargeTicks % 20) != 0) {
            return;
        }
        float chargeFactor = chargeTicks / (float) MAX_CHARGE_TICKS;
        float pitch = 0.6F + (Math.max(0.0F, Math.min(1.0F, chargeFactor)) * 0.8F);
        level.playSound(null, player.blockPosition(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 0.5F, pitch);
    }

    private static String formatSeconds(int ticks) {
        return String.format(Locale.ROOT, "%.1f", ticks / 20.0D);
    }
}
