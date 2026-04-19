package com.pop.jjk;

import java.util.ArrayList;
import java.util.Set;
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
    private static final int BEAM_ACTIVE_TICKS = 200;
    private static final DustParticleOptions CHARGE_DUST = new DustParticleOptions(0xB80020, 0.35F);
    private static final DustParticleOptions MAX_CHARGE_DUST = new DustParticleOptions(0xFF2020, 0.75F);

    private static final Map<UUID, Integer> CHARGE_STARTS = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> ACTIVE_PROJECTILES = new ConcurrentHashMap<>();
    private static final Set<UUID> SUPPRESSED_COOLDOWN_REMOVALS = ConcurrentHashMap.newKeySet();

    private PiercingBloodTechniqueHandler() {
    }

    public static void activate(ServerPlayer player) {
        if (!player.isAlive() || !isTechniqueAvailable(player)) {
            debug(player, "activate ignored: alive=%s available=%s", player.isAlive(), isTechniqueAvailable(player));
            return;
        }

        ServerLevel level = (ServerLevel) player.level();
        UUID playerId = player.getUUID();
        cleanupStaleProjectile(player, level);
        PiercingBloodProjectileEntity activeProjectile = findActiveProjectile(player, level);
        if (activeProjectile != null) {
            debug(player, "activate replacing activeProjectile id=%d held=%s tick=%d", activeProjectile.getId(), activeProjectile.isHeldPhase(), activeProjectile.tickCount);
            discardForRecast(player, level, activeProjectile);
        }

        int cooldown = TechniqueCooldownManager.getRemaining(playerId);
        if (cooldown > 0 && !BlueTechniqueHandler.hasNoCooldown(playerId)) {
            debug(player, "activate blocked: cooldown=%d", cooldown);
            player.displayClientMessage(
                Component.translatable("message.jjk.piercing_blood_cooldown", formatSeconds(cooldown)),
                true
            );
            return;
        }

        CHARGE_STARTS.put(playerId, player.tickCount);
        debug(player, "activate ok: startTick=%d", player.tickCount);
        spawnChargeParticles(level, player, 1);
    }

    public static void tick(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            UUID playerId = player.getUUID();
            ServerLevel level = (ServerLevel) player.level();
            cleanupStaleProjectile(player, level);

            PiercingBloodProjectileEntity projectile = findActiveProjectile(player, level);
            if (projectile == null) {
                ACTIVE_PROJECTILES.remove(playerId);
            } else if (!projectile.isHeldPhase() && projectile.tickCount > (projectile.getMaxHeldTicks() + 15)) {
                debug(player, "tick forced discard: projectile id=%d tick=%d maxHeld=%d", projectile.getId(), projectile.tickCount, projectile.getMaxHeldTicks());
                projectile.discard();
                ACTIVE_PROJECTILES.remove(playerId);
            }

            Integer startTick = CHARGE_STARTS.get(playerId);
            if (startTick != null && (player.tickCount - startTick) > (MAX_CHARGE_TICKS + 40)) {
                debug(player, "tick cleared stale charge: startTick=%d currentTick=%d", startTick, player.tickCount);
                CHARGE_STARTS.remove(playerId);
            }
        }
    }

    public static void clearActive() {
        CHARGE_STARTS.clear();
        ACTIVE_PROJECTILES.clear();
        SUPPRESSED_COOLDOWN_REMOVALS.clear();
    }

    public static void onHold(ServerPlayer player, boolean holding) {
        if (player == null || !player.isAlive()) {
            return;
        }
        if (!isTechniqueAvailable(player)) {
            CHARGE_STARTS.remove(player.getUUID());
            debug(player, "onHold ignored: technique unavailable");
            return;
        }

        ServerLevel level = (ServerLevel) player.level();
        UUID playerId = player.getUUID();
        cleanupStaleProjectile(player, level);
        debug(
            player,
            "onHold %s: chargeStart=%s activeProjectile=%s cooldown=%d tick=%d",
            holding,
            CHARGE_STARTS.get(playerId),
            ACTIVE_PROJECTILES.get(playerId),
            TechniqueCooldownManager.getRemaining(playerId),
            player.tickCount
        );

        if (holding) {
            int cooldown = TechniqueCooldownManager.getRemaining(playerId);
            if (cooldown > 0 && !BlueTechniqueHandler.hasNoCooldown(playerId)) {
                debug(player, "onHold(true) blocked by cooldown=%d", cooldown);
                return;
            }
            PiercingBloodProjectileEntity activeProjectile = findActiveProjectile(player, level);
            if (activeProjectile != null) {
                debug(player, "onHold(true) replacing activeProjectile id=%d held=%s tick=%d", activeProjectile.getId(), activeProjectile.isHeldPhase(), activeProjectile.tickCount);
                discardForRecast(player, level, activeProjectile);
            }

            int startTick = CHARGE_STARTS.computeIfAbsent(playerId, ignored -> player.tickCount);
            int chargeTicks = getChargeTicks(player, startTick);
            debug(player, "onHold(true) charging: startTick=%d chargeTicks=%d", startTick, chargeTicks);
            spawnChargeParticles(level, player, chargeTicks);
            playChargeHeartbeat(level, player, chargeTicks);

            if (chargeTicks >= MAX_CHARGE_TICKS) {
                CHARGE_STARTS.remove(playerId);
                debug(player, "onHold(true) reached max charge -> spawnReleasedBeam chargeTicks=%d", chargeTicks);
                spawnReleasedBeam(player, level, chargeTicks);
            }
            return;
        }

        Integer startTick = CHARGE_STARTS.remove(playerId);
        if (startTick == null) {
            debug(player, "onHold(false) ignored: no startTick");
            return;
        }

        int chargeTicks = getChargeTicks(player, startTick);
        if (chargeTicks < MIN_CHARGE_TICKS) {
            debug(player, "onHold(false) cancelled: chargeTicks=%d < min=%d", chargeTicks, MIN_CHARGE_TICKS);
            return;
        }

        debug(player, "onHold(false) releasing beam: chargeTicks=%d", chargeTicks);
        spawnReleasedBeam(player, level, chargeTicks);
    }

    public static void onProjectileDied(UUID ownerUUID) {
        ACTIVE_PROJECTILES.remove(ownerUUID);
        if (SUPPRESSED_COOLDOWN_REMOVALS.remove(ownerUUID)) {
            return;
        }
        if (!BlueTechniqueHandler.hasNoCooldown(ownerUUID)) {
            int current = TechniqueCooldownManager.getRemaining(ownerUUID);
            TechniqueCooldownManager.set(ownerUUID, Math.max(current, COOLDOWN_TICKS), Math.max(current, COOLDOWN_TICKS));
        } else {
            TechniqueCooldownManager.clear(ownerUUID);
        }
    }

    public static void clearCooldown(ServerPlayer player) {
        TechniqueCooldownManager.clear(player);
    }

    private static void cleanupStaleProjectile(ServerPlayer player, ServerLevel level) {
        Integer projectileId = ACTIVE_PROJECTILES.get(player.getUUID());
        if (projectileId == null) {
            return;
        }

        net.minecraft.world.entity.Entity entity = level.getEntity(projectileId);
        if (!(entity instanceof PiercingBloodProjectileEntity projectile) || !projectile.isAlive()) {
            debug(player, "cleanupStaleProjectile removed stale id=%d entity=%s", projectileId, entity == null ? "null" : entity.getClass().getSimpleName());
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

    private static void playMaxChargeFeedback(ServerLevel level, ServerPlayer player) {
        Vec3 dir = player.getLookAngle().normalize();
        Vec3 origin = player.getEyePosition().add(dir.scale(0.55D));
        level.playSound(null, player.blockPosition(), SoundEvents.WITHER_AMBIENT, SoundSource.PLAYERS, 0.8F, 1.0F);
        level.sendParticles(MAX_CHARGE_DUST, origin.x, origin.y, origin.z, 20, 0.18D, 0.18D, 0.18D, 0.05D);
        level.sendParticles(JJKParticles.BLOOD_CORE, origin.x, origin.y, origin.z, 8, 0.10D, 0.10D, 0.10D, 0.0D);
    }

    private static void spawnReleasedBeam(ServerPlayer player, ServerLevel level, int chargeTicks) {
        PiercingBloodProjectileEntity activeProjectile = findActiveProjectile(player, level);
        if (activeProjectile != null) {
            debug(player, "spawnReleasedBeam replacing activeProjectile id=%d held=%s tick=%d", activeProjectile.getId(), activeProjectile.isHeldPhase(), activeProjectile.tickCount);
            discardForRecast(player, level, activeProjectile);
        }

        int cooldown = TechniqueCooldownManager.getRemaining(player.getUUID());
        if (cooldown > 0 && !BlueTechniqueHandler.hasNoCooldown(player.getUUID())) {
            debug(player, "spawnReleasedBeam blocked: cooldown=%d", cooldown);
            player.displayClientMessage(
                Component.translatable("message.jjk.piercing_blood_cooldown", formatSeconds(cooldown)),
                true
            );
            return;
        }

        if (!CursedEnergyManager.consume(player, ENERGY_COST)) {
            debug(player, "spawnReleasedBeam blocked: not enough energy");
            player.displayClientMessage(Component.translatable("message.jjk.not_enough_energy"), true);
            return;
        }

        Vec3 dir = player.getLookAngle().normalize();
        Vec3 origin = player.getEyePosition().add(dir.scale(0.55D));
        float damageMultiplier = getDamageMultiplier(chargeTicks);
        float chargeFactor = Math.max(0.0F, Math.min(1.0F, chargeTicks / (float) MAX_CHARGE_TICKS));

        if (chargeTicks >= MAX_CHARGE_TICKS) {
            playMaxChargeFeedback(level, player);
        }

        level.playSound(null, player.blockPosition(), SoundEvents.ARROW_SHOOT, SoundSource.PLAYERS, 1.1F, 1.90F);
        level.playSound(null, player.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.7F, 1.80F);
        DustParticleOptions burst = new DustParticleOptions(0xFF0505, 0.50F + ((damageMultiplier - 1.0F) * 0.25F));
        level.sendParticles(burst, origin.x, origin.y, origin.z, 10, 0.05D, 0.05D, 0.05D, 0.08D);

        PiercingBloodProjectileEntity projectile = new PiercingBloodProjectileEntity(level, player);
        projectile.setChargeDamageMultiplier(damageMultiplier);
        projectile.setMaxHeldTicks(BEAM_ACTIVE_TICKS);
        projectile.setHeldSpeed(2.5F + chargeFactor);
        projectile.setHitRadius(0.55F + (chargeFactor * 0.30F));
        projectile.setHeld(true);
        level.addFreshEntity(projectile);
        ACTIVE_PROJECTILES.put(player.getUUID(), projectile.getId());
        debug(player, "spawnReleasedBeam ok: projectile id=%d chargeTicks=%d damageMult=%.2f chargeFactor=%.2f", projectile.getId(), chargeTicks, damageMultiplier, chargeFactor);
    }

    private static float getDamageMultiplier(int chargeTicks) {
        float chargeFactor = (chargeTicks - MIN_CHARGE_TICKS) / (float) (MAX_CHARGE_TICKS - MIN_CHARGE_TICKS);
        return 1.0F + (Math.max(0.0F, Math.min(1.0F, chargeFactor)) * 0.8F);
    }

    private static String formatSeconds(int ticks) {
        return String.format(Locale.ROOT, "%.1f", ticks / 20.0D);
    }

    private static void discardForRecast(ServerPlayer player, ServerLevel level, PiercingBloodProjectileEntity projectile) {
        UUID playerId = player.getUUID();
        SUPPRESSED_COOLDOWN_REMOVALS.add(playerId);
        ACTIVE_PROJECTILES.remove(playerId);
        projectile.discard();
        cleanupStaleProjectile(player, level);
    }

    private static void debug(ServerPlayer player, String message, Object... args) {
        // Piercing Blood is currently disabled from gameplay; keep the handler silent.
    }
}
