package com.pop.jjk;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class SupernovaTechniqueHandler {

    private static final int    COOLDOWN_TICKS = 300;
    private static final int    ENERGY_COST    = 220;
    private static final double RADIUS         = 6.0;
    private static final float  DAMAGE         = 10.0F;
    private static final double KNOCKBACK      = 1.1;
    private static final double KNOCKBACK_UP   = 0.45;

    private static final DustParticleOptions BLOOD_DUST       = new DustParticleOptions(0xCC1010, 1.4F);
    private static final DustParticleOptions BLOOD_DUST_MED   = new DustParticleOptions(0xFF3030, 1.0F);
    private static final DustParticleOptions BLOOD_DUST_SMALL = new DustParticleOptions(0xFF7070, 0.7F);

    private static final Map<UUID, Integer> COOLDOWNS = new HashMap<>();

    private SupernovaTechniqueHandler() {
    }

    public static void activate(ServerPlayer player) {
        if (!player.isAlive()) return;
        if (!isTechniqueAvailable(player)) return;

        int cd = COOLDOWNS.getOrDefault(player.getUUID(), 0);
        if (cd > 0) {
            player.displayClientMessage(
                Component.translatable("message.jjk.supernova_cooldown", formatSeconds(cd)), true);
            return;
        }

        if (!CursedEnergyManager.consume(player, ENERGY_COST)) {
            player.displayClientMessage(Component.translatable("message.jjk.not_enough_energy"), true);
            return;
        }

        ServerLevel level = (ServerLevel) player.level();
        Vec3 center = player.position().add(0, player.getBbHeight() * 0.5, 0);

        explode(level, player, center);

        COOLDOWNS.put(player.getUUID(), COOLDOWN_TICKS);
        player.displayClientMessage(Component.translatable("message.jjk.supernova_cast"), true);
    }

    public static void tick() {
        COOLDOWNS.entrySet().removeIf(entry -> {
            int next = entry.getValue() - 1;
            if (next <= 0) return true;
            entry.setValue(next);
            return false;
        });
    }

    public static void clearActive() {
        COOLDOWNS.clear();
    }

    private static void explode(ServerLevel level, ServerPlayer player, Vec3 center) {
        AABB area = new AABB(
            center.x - RADIUS, center.y - RADIUS, center.z - RADIUS,
            center.x + RADIUS, center.y + RADIUS, center.z + RADIUS
        );

        List<LivingEntity> targets = level.getEntitiesOfClass(
            LivingEntity.class, area,
            e -> e.isAlive() && e != player
        );

        for (LivingEntity target : targets) {
            double dist = target.position().add(0, target.getBbHeight() * 0.5, 0).distanceTo(center);
            if (dist > RADIUS) continue;

            double falloff = 1.0 - (dist / RADIUS);
            float dmg = (float)(DAMAGE * (0.4 + falloff * 0.6));

            target.hurtServer(level, level.damageSources().playerAttack(player), dmg);

            Vec3 push = target.position().add(0, target.getBbHeight() * 0.5, 0).subtract(center);
            if (push.lengthSqr() < 0.0001) {
                push = new Vec3(0, 1, 0);
            }
            push = push.normalize();
            double str = KNOCKBACK * falloff;
            target.push(push.x * str, KNOCKBACK_UP * falloff, push.z * str);
            target.hurtMarked = true;
        }

        spawnExplosionParticles(level, center);
        sendShakeToNearby(level, center, 3.5F, 12);

        level.playSound(null, center.x, center.y, center.z,
            SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 1.6F, 0.65F);
    }

    private static void spawnExplosionParticles(ServerLevel level, Vec3 center) {
        level.sendParticles(BLOOD_DUST,       center.x, center.y, center.z, 80,  2.5, 2.5, 2.5, 0.0);
        level.sendParticles(BLOOD_DUST_MED,   center.x, center.y, center.z, 60,  3.5, 3.5, 3.5, 0.0);
        level.sendParticles(BLOOD_DUST_SMALL, center.x, center.y, center.z, 40,  4.5, 4.5, 4.5, 0.0);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, center.x, center.y, center.z, 15, 2.0, 2.0, 2.0, 0.04);
        level.sendParticles(ParticleTypes.EXPLOSION,   center.x, center.y, center.z,  4, 1.5, 1.5, 1.5, 0.0);
    }

    private static void sendShakeToNearby(ServerLevel level, Vec3 center, float intensity, int durationTicks) {
        double range = 40.0;
        for (ServerPlayer nearby : level.getPlayers(p -> p.position().distanceTo(center) < range)) {
            float dist    = (float) nearby.position().distanceTo(center);
            float falloff = Math.max(0.0F, 1.0F - (dist / (float) range));
            if (falloff > 0.05F) {
                ServerPlayNetworking.send(nearby, new ScreenShakePayload(intensity * falloff, durationTicks));
            }
        }
    }

    private static boolean isTechniqueAvailable(ServerPlayer player) {
        return JJKRoster.techniquesForCharacter(
            CharacterSelectionHandler.getSelectedCharacter(player)
        ).stream().anyMatch(d -> d.id().equals("supernova"));
    }

    private static String formatSeconds(int ticks) {
        return String.format(java.util.Locale.ROOT, "%.1f", ticks / 20.0);
    }
}
