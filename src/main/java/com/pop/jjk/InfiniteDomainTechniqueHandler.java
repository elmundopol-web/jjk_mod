package com.pop.jjk;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class InfiniteDomainTechniqueHandler {

    private static final int DOMAIN_ENERGY_COST = 600;
    private static final int DOMAIN_DURATION_TICKS = 20 * 12;
    private static final int DOMAIN_COOLDOWN_TICKS = 20 * 60;
    private static final double DOMAIN_RADIUS = 20.0;
    private static final double DOMAIN_RADIUS_SQR = DOMAIN_RADIUS * DOMAIN_RADIUS;
    private static final float DOMAIN_DAMAGE_BONUS_MULTIPLIER = 0.5F;

    private static final Map<UUID, ActiveDomain> ACTIVE_DOMAINS = new HashMap<>();
    private static final Map<UUID, Integer> COOLDOWNS = new HashMap<>();
    private static final Map<UUID, ParalyzedMobState> PARALYZED_MOBS = new HashMap<>();
    private static final Set<UUID> BONUS_DAMAGE_GUARD = new HashSet<>();

    private InfiniteDomainTechniqueHandler() {
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
        if (ACTIVE_DOMAINS.containsKey(playerId)) {
            player.displayClientMessage(Component.translatable("message.jjk.infinite_domain_active"), true);
            return;
        }

        int cooldown = COOLDOWNS.getOrDefault(playerId, 0);
        if (cooldown > 0 && !noCooldown) {
            player.displayClientMessage(
                Component.translatable("message.jjk.infinite_domain_cooldown", formatSeconds(cooldown)),
                true
            );
            return;
        }

        if (!CursedEnergyManager.consume(player, DOMAIN_ENERGY_COST)) {
            player.displayClientMessage(Component.translatable("message.jjk.not_enough_energy"), true);
            return;
        }

        ServerLevel level = (ServerLevel) player.level();
        Vec3 center = getEntityCenter(player);
        ActiveDomain domain = new ActiveDomain(playerId, player.getId(), level, center, DOMAIN_DURATION_TICKS);
        ACTIVE_DOMAINS.put(playerId, domain);
        if (!noCooldown) {
            COOLDOWNS.put(playerId, DOMAIN_COOLDOWN_TICKS);
            syncCooldownToClient(player, DOMAIN_COOLDOWN_TICKS, DOMAIN_COOLDOWN_TICKS);
        } else {
            COOLDOWNS.remove(playerId);
            syncCooldownToClient(player, 0, 0);
        }

        level.playSound(null, player.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.4F, 0.55F);
        level.playSound(null, player.blockPosition(), SoundEvents.END_PORTAL_SPAWN, SoundSource.PLAYERS, 0.8F, 0.75F);
        broadcastDomainState(domain, true);
        spawnActivationParticles(domain);
        player.displayClientMessage(Component.translatable("message.jjk.infinite_domain_cast"), true);
    }

    public static void tick(MinecraftServer server) {
        tickCooldowns(server);

        Set<UUID> paralyzedThisTick = new HashSet<>();
        Iterator<Map.Entry<UUID, ActiveDomain>> iterator = ACTIVE_DOMAINS.entrySet().iterator();

        while (iterator.hasNext()) {
            Map.Entry<UUID, ActiveDomain> entry = iterator.next();
            ActiveDomain domain = entry.getValue();
            ServerPlayer owner = server.getPlayerList().getPlayer(entry.getKey());

            if (owner == null || !owner.isAlive() || owner.level() != domain.level) {
                broadcastDomainState(domain, false);
                iterator.remove();
                continue;
            }

            domain.remainingTicks--;
            applyDomainParalysis(domain, paralyzedThisTick);
            spawnAmbientParticles(domain);

            if (domain.remainingTicks % 40 == 0) {
                domain.level.playSound(null, owner.blockPosition(), SoundEvents.BEACON_AMBIENT, SoundSource.PLAYERS, 0.7F, 0.7F);
            }

            if (domain.remainingTicks <= 0) {
                owner.displayClientMessage(Component.translatable("message.jjk.infinite_domain_end"), true);
                broadcastDomainState(domain, false);
                iterator.remove();
            }
        }

        restoreReleasedMobs(paralyzedThisTick);
    }

    public static void afterDamage(LivingEntity target, DamageSource source, float baseDamageTaken, float damageTaken, boolean blocked) {
        if (blocked || damageTaken <= 0.0F) {
            return;
        }

        if (!(source.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        if (target == player || BONUS_DAMAGE_GUARD.contains(target.getUUID())) {
            return;
        }

        ActiveDomain domain = ACTIVE_DOMAINS.get(player.getUUID());
        if (domain == null || target.level() != domain.level || !isInsideDomain(target, domain)) {
            return;
        }

        if (!(target.level() instanceof ServerLevel level)) {
            return;
        }

        float bonusDamage = damageTaken * DOMAIN_DAMAGE_BONUS_MULTIPLIER;
        if (bonusDamage <= 0.0F) {
            return;
        }

        BONUS_DAMAGE_GUARD.add(target.getUUID());
        try {
            target.invulnerableTime = 0;
            target.hurtTime = 0;
            target.hurtServer(level, level.damageSources().indirectMagic(player, player), bonusDamage);
        } finally {
            BONUS_DAMAGE_GUARD.remove(target.getUUID());
        }
    }

    public static void clearAll() {
        ACTIVE_DOMAINS.clear();
        COOLDOWNS.clear();
        BONUS_DAMAGE_GUARD.clear();

        for (ParalyzedMobState state : PARALYZED_MOBS.values()) {
            restoreMobState(state);
        }

        PARALYZED_MOBS.clear();
    }

    public static void clearCooldown(ServerPlayer player) {
        COOLDOWNS.remove(player.getUUID());
        syncCooldownToClient(player, 0, 0);
    }

    public static void syncActiveDomainsToPlayer(ServerPlayer player) {
        for (ActiveDomain domain : ACTIVE_DOMAINS.values()) {
            if (domain.level == player.level()) {
                ServerPlayNetworking.send(player, createSyncPayload(domain, true));
            }
        }
    }

    private static boolean isTechniqueAvailableForPlayer(ServerPlayer player) {
        return JJKRoster.techniquesForCharacter(CharacterSelectionHandler.getSelectedCharacter(player)).stream()
            .anyMatch(definition -> definition.id().equals("infinite_domain"));
    }

    private static void applyDomainParalysis(ActiveDomain domain, Set<UUID> paralyzedThisTick) {
        AABB area = new AABB(domain.center, domain.center).inflate(DOMAIN_RADIUS);

        for (Mob mob : domain.level.getEntitiesOfClass(Mob.class, area, Mob::isAlive)) {
            if (!isInsideDomain(mob, domain)) {
                continue;
            }

            UUID mobId = mob.getUUID();
            paralyzedThisTick.add(mobId);
            PARALYZED_MOBS.computeIfAbsent(mobId, id -> new ParalyzedMobState(mob, mob.isNoAi()));

            mob.setNoAi(true);
            mob.setAggressive(false);
            mob.setTarget(null);
            mob.getNavigation().stop();
            mob.stopInPlace();
            mob.setXxa(0.0F);
            mob.setYya(0.0F);
            mob.setZza(0.0F);
            mob.setDeltaMovement(Vec3.ZERO);
            mob.hurtMarked = true;
        }
    }

    private static void restoreReleasedMobs(Set<UUID> paralyzedThisTick) {
        Iterator<Map.Entry<UUID, ParalyzedMobState>> iterator = PARALYZED_MOBS.entrySet().iterator();

        while (iterator.hasNext()) {
            Map.Entry<UUID, ParalyzedMobState> entry = iterator.next();
            ParalyzedMobState state = entry.getValue();

            if (paralyzedThisTick.contains(entry.getKey())) {
                if (state.mob == null || !state.mob.isAlive()) {
                    iterator.remove();
                }
                continue;
            }

            restoreMobState(state);
            iterator.remove();
        }
    }

    private static void restoreMobState(ParalyzedMobState state) {
        if (state.mob == null || !state.mob.isAlive()) {
            return;
        }

        state.mob.setNoAi(state.wasNoAi);
        state.mob.setDeltaMovement(Vec3.ZERO);
    }

    private static boolean isInsideDomain(LivingEntity entity, ActiveDomain domain) {
        return getEntityCenter(entity).distanceToSqr(domain.center) <= DOMAIN_RADIUS_SQR;
    }

    private static Vec3 getEntityCenter(LivingEntity entity) {
        return entity.position().add(0.0, entity.getBbHeight() * 0.5, 0.0);
    }

    private static void tickCooldowns(MinecraftServer server) {
        Iterator<Map.Entry<UUID, Integer>> iterator = COOLDOWNS.entrySet().iterator();

        while (iterator.hasNext()) {
            Map.Entry<UUID, Integer> entry = iterator.next();
            int next = entry.getValue() - 1;

            if (next <= 0) {
                ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
                if (player != null) {
                    syncCooldownToClient(player, 0, 0);
                }
                iterator.remove();
            } else {
                entry.setValue(next);
            }
        }
    }

    private static void syncCooldownToClient(ServerPlayer player, int remaining, int total) {
        ServerPlayNetworking.send(player, new CooldownSyncPayload(remaining, total));
    }

    private static void broadcastDomainState(ActiveDomain domain, boolean active) {
        InfiniteDomainSyncPayload payload = createSyncPayload(domain, active);
        for (ServerPlayer player : domain.level.players()) {
            ServerPlayNetworking.send(player, payload);
        }
    }

    private static InfiniteDomainSyncPayload createSyncPayload(ActiveDomain domain, boolean active) {
        return new InfiniteDomainSyncPayload(
            domain.ownerEntityId,
            domain.center.x,
            domain.center.y,
            domain.center.z,
            (float) DOMAIN_RADIUS,
            active ? domain.remainingTicks : 0,
            active
        );
    }

    private static void spawnActivationParticles(ActiveDomain domain) {
        for (int i = 0; i < 28; i++) {
            double angle = (Math.PI * 2.0D * i) / 28.0D;
            double x = domain.center.x + Math.cos(angle) * DOMAIN_RADIUS;
            double z = domain.center.z + Math.sin(angle) * DOMAIN_RADIUS;
            domain.level.sendParticles(ParticleTypes.SQUID_INK, x, domain.center.y + 0.4D, z, 4, 0.25D, 1.3D, 0.25D, 0.02D);
            domain.level.sendParticles(ParticleTypes.ASH, x, domain.center.y + 0.2D, z, 3, 0.2D, 1.0D, 0.2D, 0.01D);
        }

        domain.level.sendParticles(ParticleTypes.SQUID_INK, domain.center.x, domain.center.y + 1.5D, domain.center.z, 36, 1.8D, 2.5D, 1.8D, 0.02D);
        domain.level.sendParticles(ParticleTypes.ASH, domain.center.x, domain.center.y + 3.5D, domain.center.z, 48, DOMAIN_RADIUS * 0.45D, 2.0D, DOMAIN_RADIUS * 0.45D, 0.005D);
        domain.level.sendParticles(ParticleTypes.SMOKE, domain.center.x, domain.center.y + 0.8D, domain.center.z, 24, 1.0D, 1.0D, 1.0D, 0.01D);
    }

    private static void spawnAmbientParticles(ActiveDomain domain) {
        if (domain.remainingTicks % 4 != 0) {
            return;
        }

        for (int i = 0; i < 16; i++) {
            double angle = (Math.PI * 2.0D * i) / 16.0D + domain.remainingTicks * 0.035D;
            double x = domain.center.x + Math.cos(angle) * DOMAIN_RADIUS;
            double z = domain.center.z + Math.sin(angle) * DOMAIN_RADIUS;
            domain.level.sendParticles(ParticleTypes.SQUID_INK, x, domain.center.y + 0.25D, z, 2, 0.18D, 0.9D, 0.18D, 0.01D);
        }

        domain.level.sendParticles(ParticleTypes.ASH, domain.center.x, domain.center.y + 2.5D, domain.center.z, 12, DOMAIN_RADIUS * 0.55D, 1.6D, DOMAIN_RADIUS * 0.55D, 0.002D);
        domain.level.sendParticles(ParticleTypes.SMOKE, domain.center.x, domain.center.y + 1.1D, domain.center.z, 4, 0.8D, 0.4D, 0.8D, 0.003D);
    }

    private static String formatSeconds(int ticks) {
        return String.format(Locale.ROOT, "%.1f", ticks / 20.0D);
    }

    private static final class ActiveDomain {
        private final UUID ownerId;
        private final int ownerEntityId;
        private final ServerLevel level;
        private final Vec3 center;
        private int remainingTicks;

        private ActiveDomain(UUID ownerId, int ownerEntityId, ServerLevel level, Vec3 center, int remainingTicks) {
            this.ownerId = ownerId;
            this.ownerEntityId = ownerEntityId;
            this.level = level;
            this.center = center;
            this.remainingTicks = remainingTicks;
        }
    }

    private static final class ParalyzedMobState {
        private final Mob mob;
        private final boolean wasNoAi;

        private ParalyzedMobState(Mob mob, boolean wasNoAi) {
            this.mob = mob;
            this.wasNoAi = wasNoAi;
        }
    }
}
