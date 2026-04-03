package com.pop.jjk;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class DivergingFistTechniqueHandler {

    private static final int DIVERGING_FIST_COOLDOWN_TICKS = 34;
    private static final int DIVERGING_FIST_DELAY_TICKS = 7;
    private static final double ATTACK_RANGE = 3.2;
    private static final double ATTACK_WIDTH = 1.15;
    private static final double MAX_DELAY_DISTANCE = 4.5;
    private static final double HIT_PUSH = 0.62;
    private static final float INITIAL_DAMAGE = 4.0F;
    private static final float DELAY_DAMAGE = 6.5F;
    private static final List<PendingDivergingHit> PENDING_HITS = new CopyOnWriteArrayList<>();
    private static final java.util.Map<UUID, Integer> COOLDOWNS = new ConcurrentHashMap<>();

    private DivergingFistTechniqueHandler() {
    }

    public static void activate(ServerPlayer player) {
        if (!player.isAlive()) {
            return;
        }

        if (!isTechniqueAvailableForPlayer(player)) {
            return;
        }

        int cooldown = COOLDOWNS.getOrDefault(player.getUUID(), 0);
        if (cooldown > 0) {
            player.displayClientMessage(
                Component.translatable("message.jjk.diverging_fist_cooldown", formatSeconds(cooldown)),
                true
            );
            return;
        }

        ServerLevel level = (ServerLevel) player.level();
        LivingEntity target = findTarget(player, level);

        if (target == null) {
            player.displayClientMessage(Component.translatable("message.jjk.diverging_fist_no_target"), true);
            return;
        }

        Vec3 look = horizontalDirection(player.getLookAngle());
        dealInitialHit(player, level, target, look);
        PENDING_HITS.add(new PendingDivergingHit(player.getUUID(), target.getUUID(), level, DIVERGING_FIST_DELAY_TICKS));
        COOLDOWNS.put(player.getUUID(), DIVERGING_FIST_COOLDOWN_TICKS);
        player.displayClientMessage(Component.translatable("message.jjk.diverging_fist_cast"), true);
    }

    public static void tick() {
        tickCooldowns();

        for (int index = PENDING_HITS.size() - 1; index >= 0; index--) {
            PendingDivergingHit pendingHit = PENDING_HITS.get(index);
            pendingHit.remainingTicks--;

            if (pendingHit.remainingTicks > 0) {
                continue;
            }

            resolveDelayedHit(pendingHit);
            PENDING_HITS.remove(index);
        }
    }

    public static void clearActive() {
        PENDING_HITS.clear();
        COOLDOWNS.clear();
    }

    private static boolean isTechniqueAvailableForPlayer(ServerPlayer player) {
        return JJKRoster.techniquesForCharacter(CharacterSelectionHandler.getSelectedCharacter(player)).stream()
            .anyMatch(definition -> definition.id().equals("diverging_fist"));
    }

    private static LivingEntity findTarget(ServerPlayer player, ServerLevel level) {
        Vec3 eyePosition = player.getEyePosition();
        Vec3 look = horizontalDirection(player.getLookAngle());
        Vec3 reachEnd = eyePosition.add(look.scale(ATTACK_RANGE));
        AABB searchBox = new AABB(eyePosition, reachEnd).inflate(ATTACK_WIDTH, 1.15, ATTACK_WIDTH);
        LivingEntity bestTarget = null;
        double bestScore = Double.NEGATIVE_INFINITY;

        for (LivingEntity candidate : level.getEntitiesOfClass(LivingEntity.class, searchBox, entity ->
            entity.isAlive() && entity != player
        )) {
            Vec3 candidateCenter = candidate.position().add(0.0, candidate.getBbHeight() * 0.5, 0.0);
            Vec3 toCandidate = candidateCenter.subtract(eyePosition);
            double distance = toCandidate.length();

            if (distance > ATTACK_RANGE || distance < 0.001) {
                continue;
            }

            Vec3 direction = toCandidate.normalize();
            double forwardAlignment = look.dot(direction);
            if (forwardAlignment < 0.45) {
                continue;
            }

            double forwardDistance = toCandidate.dot(look);
            Vec3 lateralOffset = toCandidate.subtract(look.scale(forwardDistance));
            double allowedWidth = ATTACK_WIDTH + (candidate.getBbWidth() * 0.5);

            if (lateralOffset.length() > allowedWidth) {
                continue;
            }

            double score = (forwardAlignment * 3.0) - distance;
            if (score > bestScore) {
                bestScore = score;
                bestTarget = candidate;
            }
        }

        return bestTarget;
    }

    private static void dealInitialHit(ServerPlayer player, ServerLevel level, LivingEntity target, Vec3 look) {
        target.hurtServer(level, level.damageSources().playerAttack(player), INITIAL_DAMAGE);
        target.push(look.x * HIT_PUSH, 0.14, look.z * HIT_PUSH);
        target.hurtMarked = true;

        Vec3 center = target.position().add(0.0, target.getBbHeight() * 0.5, 0.0);
        level.sendParticles(ParticleTypes.SWEEP_ATTACK, center.x, center.y, center.z, 1, 0.0, 0.0, 0.0, 0.0);
        level.sendParticles(ParticleTypes.CRIT, center.x, center.y, center.z, 8, 0.2, 0.25, 0.2, 0.08);
        level.playSound(null, player.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.9F, 0.95F);
    }

    private static void resolveDelayedHit(PendingDivergingHit pendingHit) {
        ServerPlayer player = pendingHit.level.getServer().getPlayerList().getPlayer(pendingHit.playerId);
        if (player == null || !player.isAlive() || player.level() != pendingHit.level) {
            return;
        }

        if (!(pendingHit.level.getEntity(pendingHit.targetId) instanceof LivingEntity target) || !target.isAlive()) {
            return;
        }

        if (player.distanceToSqr(target) > MAX_DELAY_DISTANCE * MAX_DELAY_DISTANCE) {
            return;
        }

        Vec3 look = horizontalDirection(player.getLookAngle());
        target.hurtServer(pendingHit.level, pendingHit.level.damageSources().playerAttack(player), DELAY_DAMAGE);
        target.push(look.x * (HIT_PUSH * 0.75), 0.2, look.z * (HIT_PUSH * 0.75));
        target.hurtMarked = true;

        Vec3 center = target.position().add(0.0, target.getBbHeight() * 0.5, 0.0);
        pendingHit.level.sendParticles(ParticleTypes.CRIT, center.x, center.y, center.z, 18, 0.25, 0.3, 0.25, 0.14);
        pendingHit.level.sendParticles(ParticleTypes.ENCHANTED_HIT, center.x, center.y, center.z, 12, 0.18, 0.22, 0.18, 0.0);
        pendingHit.level.playSound(null, target.blockPosition(), SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 1.0F, 0.72F);
        player.displayClientMessage(Component.translatable("message.jjk.diverging_fist_impact"), true);
    }

    private static Vec3 horizontalDirection(Vec3 look) {
        Vec3 horizontal = new Vec3(look.x, 0.0, look.z);
        return horizontal.lengthSqr() < 0.0001 ? new Vec3(0.0, 0.0, 1.0) : horizontal.normalize();
    }

    private static void tickCooldowns() {
        List<UUID> expired = new ArrayList<>();

        for (java.util.Map.Entry<UUID, Integer> entry : COOLDOWNS.entrySet()) {
            int nextValue = entry.getValue() - 1;

            if (nextValue <= 0) {
                expired.add(entry.getKey());
            } else {
                entry.setValue(nextValue);
            }
        }

        for (UUID playerId : expired) {
            COOLDOWNS.remove(playerId);
        }
    }

    private static String formatSeconds(int ticks) {
        return String.format(java.util.Locale.ROOT, "%.1f", ticks / 20.0D);
    }

    private static final class PendingDivergingHit {
        private final UUID playerId;
        private final UUID targetId;
        private final ServerLevel level;
        private int remainingTicks;

        private PendingDivergingHit(UUID playerId, UUID targetId, ServerLevel level, int remainingTicks) {
            this.playerId = playerId;
            this.targetId = targetId;
            this.level = level;
            this.remainingTicks = remainingTicks;
        }
    }
}
