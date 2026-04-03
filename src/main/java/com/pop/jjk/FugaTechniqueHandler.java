package com.pop.jjk;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

public final class FugaTechniqueHandler {

    private static final Map<UUID, HoldState> HOLDS = new ConcurrentHashMap<>();

    private FugaTechniqueHandler() {
    }

    public static void activate(ServerPlayer player) {
        onHold(player, true);
    }

    public static void onHold(ServerPlayer player, boolean holding) {
        if (player == null || !player.isAlive()) {
            return;
        }
        if (!isTechniqueAvailable(player)) {
            return;
        }

        UUID playerId = player.getUUID();
        int cooldown = BlueTechniqueHandler.getCooldown(playerId);
        if (holding) {
            if (cooldown > 0 && !BlueTechniqueHandler.hasNoCooldown(playerId)) {
                player.displayClientMessage(Component.translatable("message.jjk.fuga_cooldown", formatSeconds(cooldown)), true);
                return;
            }

            HoldState existing = HOLDS.get(playerId);
            if (existing == null) {
                ServerLevel level = (ServerLevel) player.level();
                FugaProjectileEntity chargeEntity = new FugaProjectileEntity(level, player);
                chargeEntity.syncChargeVisual(player, 0.0F);
                level.addFreshEntity(chargeEntity);
                HOLDS.put(playerId, new HoldState(level, chargeEntity));

                Vec3 palm = palmAnchor(player);
                level.playSound(null, palm.x, palm.y, palm.z, SoundEvents.FLINTANDSTEEL_USE, SoundSource.PLAYERS, 0.8F, 1.9F);
                player.displayClientMessage(Component.translatable("message.jjk.fuga_open"), true);
            }
            return;
        }

        HoldState state = HOLDS.get(playerId);
        if (state == null) {
            return;
        }

        if (state.ticks >= FugaTechnique.CHARGE_MIN_TICKS) {
            launchNow(player);
        } else {
            state.pendingRelease = true;
        }
    }

    public static void tick() {
        for (Map.Entry<UUID, HoldState> entry : new java.util.ArrayList<>(HOLDS.entrySet())) {
            UUID playerId = entry.getKey();
            HoldState state = entry.getValue();
            ServerPlayer player = state.level.getServer().getPlayerList().getPlayer(playerId);

            if (player == null || !player.isAlive() || player.level() != state.level) {
                discardHeldProjectile(state);
                HOLDS.remove(playerId);
                continue;
            }

            state.ticks++;
            float chargePower = getChargePower(state.ticks);
            ensureChargeProjectile(player, state, chargePower);
            state.projectile.syncChargeVisual(player, chargePower);

            spawnChargeParticles(state.level, player, chargePower, state.ticks);

            if ((state.ticks % 8) == 0 && state.ticks < FugaTechnique.OVERCHARGE_TICKS) {
                float pitch = 0.8F + (chargePower * 0.8F);
                state.level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.FIRE_AMBIENT, SoundSource.PLAYERS, 0.4F, pitch);
            }

            if (state.ticks == FugaTechnique.CHARGE_MIN_TICKS) {
                Vec3 palm = palmAnchor(player);
                state.level.playSound(null, palm.x, palm.y, palm.z, SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 0.6F, 1.8F);
                player.displayClientMessage(Component.literal("Open: Fuga ready"), true);
            }

            if (state.ticks == FugaTechnique.OVERCHARGE_TICKS) {
                net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player, new FugaOverchargeSyncPayload(120));
                state.level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 0.6F, 1.6F);
                player.displayClientMessage(Component.literal("Open: Fuga overcharge"), true);
            }

            if (state.pendingRelease && state.ticks >= FugaTechnique.CHARGE_MIN_TICKS) {
                launchNow(player);
            }
        }
    }

    public static void clearActive() {
        for (HoldState state : HOLDS.values()) {
            discardHeldProjectile(state);
        }
        HOLDS.clear();
    }

    private static void launchNow(ServerPlayer player) {
        UUID playerId = player.getUUID();
        HoldState state = HOLDS.remove(playerId);
        if (state == null) {
            return;
        }

        float chargePower = getChargePower(state.ticks);
        int energyCost = (int) (FugaTechnique.ENERGY_COST * (0.2F + (0.8F * chargePower)));
        if (!BlueTechniqueHandler.hasNoCooldown(playerId) && !CursedEnergyManager.consume(player, energyCost)) {
            discardHeldProjectile(state);
            player.displayClientMessage(Component.translatable("message.jjk.not_enough_energy"), true);
            return;
        }

        FugaProjectileEntity projectile = state.projectile;
        if (projectile == null || !projectile.isAlive() || projectile.isRemoved()) {
            projectile = new FugaProjectileEntity((ServerLevel) player.level(), player);
            ((ServerLevel) player.level()).addFreshEntity(projectile);
        }

        projectile.setChargePower(chargePower);
        projectile.launchFrom(player);

        if (!BlueTechniqueHandler.hasNoCooldown(playerId)) {
            int cooldownTicks = (int) (FugaTechnique.COOLDOWN_TICKS * (0.4F + (0.6F * chargePower)));
            BlueTechniqueHandler.setCooldown(playerId, cooldownTicks);
            BlueTechniqueHandler.syncCooldownToClient(player, cooldownTicks, cooldownTicks);
        }

        player.displayClientMessage(Component.literal("Open: Fuga released (" + String.format(Locale.ROOT, "%.0f", chargePower * 100.0F) + "%)"), true);
    }

    private static void ensureChargeProjectile(ServerPlayer player, HoldState state, float chargePower) {
        if (state.projectile != null && state.projectile.isAlive() && !state.projectile.isRemoved()) {
            return;
        }

        FugaProjectileEntity projectile = new FugaProjectileEntity((ServerLevel) player.level(), player);
        projectile.syncChargeVisual(player, chargePower);
        ((ServerLevel) player.level()).addFreshEntity(projectile);
        state.projectile = projectile;
    }

    private static void discardHeldProjectile(HoldState state) {
        if (state.projectile != null && state.projectile.isAlive() && !state.projectile.isRemoved()) {
            state.projectile.discard();
        }
    }

    private static float getChargePower(int holdTicks) {
        return Math.min(1.0F, holdTicks / (float) FugaTechnique.OVERCHARGE_TICKS);
    }

    private static void spawnChargeParticles(ServerLevel level, ServerPlayer player, float chargePower, int holdTicks) {
        Vec3 palm = palmAnchor(player);
        if ((holdTicks % 2) != 0) {
            return;
        }

        int chargeCount = 2 + Math.round(chargePower * 5.0F);
        level.sendParticles(JJKParticles.FIRE_CHARGE, palm.x, palm.y, palm.z, chargeCount, 0.08D + (chargePower * 0.12D), 0.06D + (chargePower * 0.08D), 0.08D + (chargePower * 0.12D), 0.0D);
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.SMOKE, palm.x, palm.y, palm.z, 1 + Math.round(chargePower * 3.0F), 0.05D, 0.02D, 0.05D, 0.01D);
    }

    private static Vec3 palmAnchor(ServerPlayer player) {
        Vec3 dir = player.getLookAngle().normalize();
        return player.getEyePosition().add(dir.scale(0.70D)).add(0.0D, -0.28D, 0.0D);
    }

    private static boolean isTechniqueAvailable(ServerPlayer player) {
        return JJKRoster.techniquesForCharacter(CharacterSelectionHandler.getSelectedCharacter(player)).stream()
            .anyMatch(def -> def.id().equals("fuga"));
    }

    private static String formatSeconds(int ticks) {
        return String.format(Locale.ROOT, "%.1f", ticks / 20.0D);
    }

    private static final class HoldState {
        private final ServerLevel level;
        private FugaProjectileEntity projectile;
        private int ticks;
        private boolean pendingRelease;

        private HoldState(ServerLevel level, FugaProjectileEntity projectile) {
            this.level = level;
            this.projectile = projectile;
            this.ticks = 0;
            this.pendingRelease = false;
        }
    }
}
