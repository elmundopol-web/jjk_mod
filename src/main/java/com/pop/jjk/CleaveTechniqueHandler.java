package com.pop.jjk;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class CleaveTechniqueHandler {

    private static final int COOLDOWN_TICKS = 30;
    private static final int ENERGY_COST = 80;
    private static final double RANGE = 4.0;
    private static final double HALF_ANGLE_DEG = 30.0; // 60° total
    private static final double COS_THRESHOLD = Math.cos(Math.toRadians(HALF_ANGLE_DEG));

    private CleaveTechniqueHandler() {
    }

    public static void activate(ServerPlayer player) {
        if (player == null || !player.isAlive()) {
            return;
        }
        if (!isTechniqueAvailableForPlayer(player)) {
            return;
        }

        int cd = TechniqueCooldownManager.getRemaining(player.getUUID());
        if (cd > 0) {
            return;
        }

        if (!CursedEnergyManager.consume(player, ENERGY_COST)) {
            player.displayClientMessage(Component.translatable("message.jjk.not_enough_energy"), true);
            return;
        }

        ServerLevel level = (ServerLevel) player.level();
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle().normalize();

        // Buscar objetivos en un AABB a lo largo del cono
        AABB box = new AABB(eye, eye.add(look.scale(RANGE))).inflate(2.5, 2.0, 2.5);
        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, box, e -> e.isAlive() && e != player)) {
            Vec3 center = entity.position().add(0.0, entity.getBbHeight() * 0.5, 0.0);
            Vec3 toEntity = center.subtract(eye);
            double dist = toEntity.length();
            if (dist > RANGE || dist < 1.0E-3) continue;
            Vec3 dir = toEntity.normalize();
            double alignment = look.dot(dir);
            if (alignment < COS_THRESHOLD) continue;

            float maxHp = entity.getMaxHealth();
            float damage = 8.0F + (maxHp / 20.0F) * 6.0F;
            if (damage < 8.0F) damage = 8.0F;
            if (damage > 28.0F) damage = 28.0F;
            entity.hurtServer(level, level.damageSources().playerAttack(player), damage);
            entity.hurtMarked = true;

            level.sendParticles(ParticleTypes.CRIT, center.x, center.y, center.z, 10, 0.18, 0.22, 0.18, 0.08);
        }

        spawnSweepArc(level, player, look);
        ServerPlayNetworking.send(player, new ScreenShakePayload(1.5F, 6));

        level.playSound(null, player.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.0F, 1.0F);
        level.playSound(null, player.blockPosition(), SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 0.9F, 1.0F);

        TechniqueCooldownManager.set(player.getUUID(), COOLDOWN_TICKS);
    }

    public static void tick() {
    }

    public static void clearActive() {
    }

    private static boolean isTechniqueAvailableForPlayer(ServerPlayer player) {
        return JJKRoster.techniquesForCharacter(CharacterSelectionHandler.getSelectedCharacter(player)).stream()
            .anyMatch(definition -> definition.id().equals("cleave"));
    }

    private static void spawnSweepArc(ServerLevel level, ServerPlayer player, Vec3 look) {
        Vec3 fwd = new Vec3(look.x, 0.0, look.z).normalize();
        if (fwd.lengthSqr() < 1.0E-6) fwd = new Vec3(0.0, 0.0, 1.0);
        Vec3 left = new Vec3(-fwd.z, 0.0, fwd.x);
        Vec3 origin = player.getEyePosition().add(0.0, -0.5, 0.0);
        double radius = 2.6;
        int steps = 12;
        for (int i = 0; i <= steps; i++) {
            double t = (double) i / steps; // 0..1
            double ang = Math.toRadians(-HALF_ANGLE_DEG + t * (2 * HALF_ANGLE_DEG));
            double ca = Math.cos(ang);
            double sa = Math.sin(ang);
            Vec3 dir = fwd.scale(ca).add(left.scale(sa));
            Vec3 p = origin.add(dir.scale(radius));
            level.sendParticles(ParticleTypes.SWEEP_ATTACK, p.x, p.y, p.z, 1, 0.02, 0.02, 0.02, 0.0);
        }
    }
}
