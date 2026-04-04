package com.pop.jjk;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class MalevolentShrineTechniqueHandler {

    private static final int DOMAIN_DURATION_TICKS = 20 * 10;
    private static final int DOMAIN_BUILDUP_TICKS = 20;
    private static final int DOMAIN_COOLDOWN_TICKS = 20 * 90;
    private static final int DOMAIN_ENERGY_COST = 800;
    private static final double DOMAIN_RADIUS = 25.0D;
    private static final double DOMAIN_RADIUS_SQR = DOMAIN_RADIUS * DOMAIN_RADIUS;
    private static final float BASE_DAMAGE = 1.5F;

    private static final Map<UUID, ActiveShrine> ACTIVE_SHRINES = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> COOLDOWNS = new ConcurrentHashMap<>();

    private MalevolentShrineTechniqueHandler() {
    }

    public static void activate(ServerPlayer player) {
        if (!player.isAlive()) {
            return;
        }

        if (!isTechniqueAvailableForPlayer(player)) {
            return;
        }

        UUID playerId = player.getUUID();
        boolean noCooldown = BlueTechniqueHandler.hasNoCooldown(playerId);
        if (ACTIVE_SHRINES.containsKey(playerId)) {
            player.displayClientMessage(Component.translatable("message.jjk.malevolent_shrine_active"), true);
            return;
        }

        int cooldown = COOLDOWNS.getOrDefault(playerId, 0);
        if (cooldown > 0 && !noCooldown) {
            player.displayClientMessage(
                Component.translatable("message.jjk.malevolent_shrine_cooldown", formatSeconds(cooldown)),
                true
            );
            return;
        }

        if (!CursedEnergyManager.consume(player, DOMAIN_ENERGY_COST)) {
            player.displayClientMessage(Component.translatable("message.jjk.not_enough_energy"), true);
            return;
        }

        ServerLevel level = (ServerLevel) player.level();
        ActiveShrine shrine = new ActiveShrine(playerId, player.getId(), level, getEntityCenter(player), DOMAIN_DURATION_TICKS);
        ACTIVE_SHRINES.put(playerId, shrine);

        if (!noCooldown) {
            COOLDOWNS.put(playerId, DOMAIN_COOLDOWN_TICKS);
            syncCooldownToClient(player, DOMAIN_COOLDOWN_TICKS, DOMAIN_COOLDOWN_TICKS);
        } else {
            COOLDOWNS.remove(playerId);
            syncCooldownToClient(player, 0, 0);
        }

        level.playSound(null, player.blockPosition(), SoundEvents.WITHER_SPAWN, SoundSource.PLAYERS, 0.9F, 0.85F);
        level.playSound(null, player.blockPosition(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 1.1F, 0.65F);
        spawnActivationParticles(shrine);
        broadcastShrineState(shrine, true);
        player.displayClientMessage(Component.translatable("message.jjk.malevolent_shrine_cast"), true);
    }

    public static void tick(MinecraftServer server) {
        tickCooldowns(server);

        Set<UUID> expired = new java.util.HashSet<>();
        for (Map.Entry<UUID, ActiveShrine> entry : new ArrayList<>(ACTIVE_SHRINES.entrySet())) {
            UUID ownerId = entry.getKey();
            ActiveShrine shrine = entry.getValue();
            ServerPlayer owner = server.getPlayerList().getPlayer(ownerId);

            if (owner == null || !owner.isAlive() || owner.level() != shrine.level) {
                broadcastShrineState(shrine, false);
                expired.add(ownerId);
                continue;
            }

            shrine.ageTicks++;
            shrine.remainingTicks--;

            applyDomainDamage(shrine, owner);

            if ((shrine.ageTicks % 3) == 0) {
                spawnSlashParticles(shrine);
            }

            if ((shrine.ageTicks % 5) == 0) {
                sendScreenShake(shrine);
            }

            if ((shrine.ageTicks % 30) == 0) {
                shrine.level.playSound(null, owner.blockPosition(), SoundEvents.WITHER_AMBIENT, SoundSource.PLAYERS, 0.4F, 0.7F + (shrine.level.random.nextFloat() * 0.2F));
            }

            if (shrine.remainingTicks <= 0) {
                owner.displayClientMessage(Component.translatable("message.jjk.malevolent_shrine_end"), true);
                broadcastShrineState(shrine, false);
                expired.add(ownerId);
            }
        }

        expired.forEach(ACTIVE_SHRINES::remove);
    }

    public static void clearAll() {
        ACTIVE_SHRINES.clear();
        COOLDOWNS.clear();
    }

    public static void clearCooldown(ServerPlayer player) {
        COOLDOWNS.remove(player.getUUID());
        syncCooldownToClient(player, 0, 0);
    }

    public static void syncActiveDomainsToPlayer(ServerPlayer player) {
        for (ActiveShrine shrine : ACTIVE_SHRINES.values()) {
            if (shrine.level == player.level()) {
                ServerPlayNetworking.send(player, createSyncPayload(shrine, true));
            }
        }
    }

    private static boolean isTechniqueAvailableForPlayer(ServerPlayer player) {
        return JJKRoster.techniquesForCharacter(CharacterSelectionHandler.getSelectedCharacter(player)).stream()
            .anyMatch(definition -> definition.id().equals("malevolent_shrine"));
    }

    private static void applyDomainDamage(ActiveShrine shrine, ServerPlayer owner) {
        AABB area = new AABB(shrine.center, shrine.center).inflate(DOMAIN_RADIUS);
        for (LivingEntity entity : shrine.level.getEntitiesOfClass(LivingEntity.class, area, LivingEntity::isAlive)) {
            if (entity == owner) {
                continue;
            }
            if (entity instanceof Player player && player.isSpectator()) {
                continue;
            }

            float insideFactor = getInsideFactor(entity, shrine);
            if (insideFactor <= 0.0F) {
                continue;
            }

            float damage = BASE_DAMAGE + (insideFactor * 2.0F);
            entity.invulnerableTime = 0;
            entity.hurtTime = 0;
            entity.hurtMarked = true;
            entity.hurtServer(shrine.level, shrine.level.damageSources().playerAttack(owner), damage);
        }
    }

    private static void spawnSlashParticles(ActiveShrine shrine) {
        int slashBursts = 18;
        double minY = shrine.center.y - 2.0D;
        double maxY = shrine.center.y + 6.0D;
        for (int i = 0; i < slashBursts; i++) {
            double radius = DOMAIN_RADIUS * Math.sqrt(shrine.level.random.nextDouble());
            double angle = shrine.level.random.nextDouble() * Math.PI * 2.0D;
            double x = shrine.center.x + Math.cos(angle) * radius;
            double z = shrine.center.z + Math.sin(angle) * radius;
            double y = Mth.lerp(shrine.level.random.nextDouble(), minY, maxY);
            shrine.level.sendParticles(ParticleTypes.CRIT, x, y, z, 3, 0.18D, 0.18D, 0.18D, 0.08D);
            shrine.level.sendParticles(ParticleTypes.SWEEP_ATTACK, x, y, z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        }
    }

    private static void sendScreenShake(ActiveShrine shrine) {
        for (ServerPlayer player : shrine.level.players()) {
            if (getInsideFactor(player, shrine) > 0.0F) {
                ServerPlayNetworking.send(player, new ScreenShakePayload(0.8F, 3));
            }
        }
    }

    private static void spawnActivationParticles(ActiveShrine shrine) {
        for (int i = 0; i < 36; i++) {
            double angle = (Math.PI * 2.0D * i) / 36.0D;
            double x = shrine.center.x + Math.cos(angle) * DOMAIN_RADIUS;
            double z = shrine.center.z + Math.sin(angle) * DOMAIN_RADIUS;
            shrine.level.sendParticles(ParticleTypes.CRIT, x, shrine.center.y + 0.5D, z, 4, 0.25D, 0.6D, 0.25D, 0.12D);
            shrine.level.sendParticles(ParticleTypes.SWEEP_ATTACK, x, shrine.center.y + 0.25D, z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        }

        shrine.level.sendParticles(ParticleTypes.SMOKE, shrine.center.x, shrine.center.y + 1.0D, shrine.center.z, 30, 1.6D, 1.2D, 1.6D, 0.03D);
        shrine.level.sendParticles(ParticleTypes.CRIT, shrine.center.x, shrine.center.y + 1.5D, shrine.center.z, 28, 1.4D, 2.0D, 1.4D, 0.12D);
    }

    private static float getInsideFactor(LivingEntity entity, ActiveShrine shrine) {
        Vec3 entityCenter = getEntityCenter(entity);
        double dx = entityCenter.x - shrine.center.x;
        double dz = entityCenter.z - shrine.center.z;
        double distance = Math.sqrt((dx * dx) + (dz * dz));
        if (distance >= DOMAIN_RADIUS) {
            return 0.0F;
        }

        return Mth.clamp((float) (1.0D - (distance / DOMAIN_RADIUS)), 0.0F, 1.0F);
    }

    private static Vec3 getEntityCenter(LivingEntity entity) {
        return entity.position().add(0.0D, entity.getBbHeight() * 0.5D, 0.0D);
    }

    private static void tickCooldowns(MinecraftServer server) {
        Set<UUID> expired = new java.util.HashSet<>();
        for (UUID playerId : new ArrayList<>(COOLDOWNS.keySet())) {
            Integer current = COOLDOWNS.get(playerId);
            if (current == null) {
                continue;
            }

            int next = current - 1;
            if (next <= 0) {
                ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                if (player != null) {
                    syncCooldownToClient(player, 0, 0);
                }
                expired.add(playerId);
            } else {
                COOLDOWNS.put(playerId, next);
            }
        }
        expired.forEach(COOLDOWNS::remove);
    }

    private static void syncCooldownToClient(ServerPlayer player, int remaining, int total) {
        ServerPlayNetworking.send(player, new CooldownSyncPayload(remaining, total));
    }

    private static void broadcastShrineState(ActiveShrine shrine, boolean active) {
        MalevolentShrineSyncPayload payload = createSyncPayload(shrine, active);
        for (ServerPlayer player : shrine.level.players()) {
            ServerPlayNetworking.send(player, payload);
        }
    }

    private static MalevolentShrineSyncPayload createSyncPayload(ActiveShrine shrine, boolean active) {
        return new MalevolentShrineSyncPayload(
            shrine.ownerEntityId,
            shrine.center.x,
            shrine.center.y,
            shrine.center.z,
            (float) DOMAIN_RADIUS,
            active ? shrine.remainingTicks : 0,
            active
        );
    }

    private static String formatSeconds(int ticks) {
        return String.format(Locale.ROOT, "%.1f", ticks / 20.0D);
    }

    private static final class ActiveShrine {
        private final UUID ownerId;
        private final int ownerEntityId;
        private final ServerLevel level;
        private final Vec3 center;
        private int remainingTicks;
        private int ageTicks;

        private ActiveShrine(UUID ownerId, int ownerEntityId, ServerLevel level, Vec3 center, int remainingTicks) {
            this.ownerId = ownerId;
            this.ownerEntityId = ownerEntityId;
            this.level = level;
            this.center = center;
            this.remainingTicks = remainingTicks;
            this.ageTicks = 0;
        }
    }
}
