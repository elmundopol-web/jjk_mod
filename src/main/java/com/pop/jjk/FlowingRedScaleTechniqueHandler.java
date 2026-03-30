package com.pop.jjk;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.phys.Vec3;

public final class FlowingRedScaleTechniqueHandler {

    private static final int   COOLDOWN_TICKS  = 400;
    private static final int   ENERGY_COST     = 100;
    private static final int   EFFECT_DURATION = 160;

    private static final DustParticleOptions BLOOD_DUST = new DustParticleOptions(0xCC1010, 0.9F);

    private static final Map<UUID, Integer> COOLDOWNS = new HashMap<>();
    private static final Map<UUID, ActiveBuff> ACTIVE = new HashMap<>();

    private FlowingRedScaleTechniqueHandler() {
    }

    public static void activate(ServerPlayer player) {
        if (!player.isAlive()) return;
        if (!isTechniqueAvailable(player)) return;

        int cd = COOLDOWNS.getOrDefault(player.getUUID(), 0);
        if (cd > 0 && !BlueTechniqueHandler.hasNoCooldown(player.getUUID())) {
            player.displayClientMessage(
                Component.translatable("message.jjk.flowing_red_scale_cooldown", formatSeconds(cd)), true);
            return;
        }

        if (!CursedEnergyManager.consume(player, ENERGY_COST)) {
            player.displayClientMessage(Component.translatable("message.jjk.not_enough_energy"), true);
            return;
        }

        ServerLevel lvl = (ServerLevel) player.level();
        applyEffects(player);
        spawnActivationParticles(lvl, player);
        lvl.playSound(null, player.blockPosition(), SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 1.0F, 0.9F);
        ACTIVE.put(player.getUUID(), new ActiveBuff(lvl, EFFECT_DURATION));

        if (!BlueTechniqueHandler.hasNoCooldown(player.getUUID())) {
            COOLDOWNS.put(player.getUUID(), COOLDOWN_TICKS);
        } else {
            COOLDOWNS.remove(player.getUUID());
        }
        player.displayClientMessage(Component.translatable("message.jjk.flowing_red_scale_cast"), true);
    }

    public static void tick() {
        COOLDOWNS.entrySet().removeIf(entry -> {
            int next = entry.getValue() - 1;
            if (next <= 0) return true;
            entry.setValue(next);
            return false;
        });

        java.util.Iterator<java.util.Map.Entry<UUID, ActiveBuff>> it = ACTIVE.entrySet().iterator();
        while (it.hasNext()) {
            java.util.Map.Entry<UUID, ActiveBuff> e = it.next();
            ActiveBuff buff = e.getValue();
            buff.remainingTicks--;
            if (buff.remainingTicks <= 0) {
                it.remove();
                continue;
            }
            ServerPlayer p = buff.level.getServer().getPlayerList().getPlayer(e.getKey());
            if (p != null && p.isAlive() && p.level() == buff.level) {
                spawnOngoingParticles(buff.level, p);
            } else {
                it.remove();
            }
        }
    }

    public static void clearActive() {
        COOLDOWNS.clear();
        ACTIVE.clear();
    }

    private static void applyEffects(ServerPlayer player) {
        HolderLookup.RegistryLookup<MobEffect> lookup =
            ((ServerLevel) player.level()).registryAccess().lookupOrThrow(Registries.MOB_EFFECT);

        lookup.get(ResourceKey.create(Registries.MOB_EFFECT, Identifier.withDefaultNamespace("speed")))
            .ifPresent(h -> player.addEffect(new MobEffectInstance(h, EFFECT_DURATION, 1, false, true, true)));
        lookup.get(ResourceKey.create(Registries.MOB_EFFECT, Identifier.withDefaultNamespace("strength")))
            .ifPresent(h -> player.addEffect(new MobEffectInstance(h, EFFECT_DURATION, 1, false, true, true)));
    }

    private static void spawnActivationParticles(ServerLevel level, ServerPlayer player) {
        Vec3 pos = player.position().add(0, player.getBbHeight() * 0.5, 0);
        level.sendParticles(BLOOD_DUST, pos.x, pos.y, pos.z, 40, 0.5, 0.8, 0.5, 0.02);
        level.sendParticles(BLOOD_DUST, pos.x, pos.y + 0.5, pos.z, 20, 0.3, 0.5, 0.3, 0.05);
    }

    private static void spawnOngoingParticles(ServerLevel level, ServerPlayer player) {
        Vec3 pos = player.position().add(0, player.getBbHeight() * 0.5, 0);
        level.sendParticles(BLOOD_DUST, pos.x, pos.y, pos.z, 14, 0.45, 0.75, 0.45, 0.03);
    }

    private static final class ActiveBuff {
        private final ServerLevel level;
        private int remainingTicks;

        private ActiveBuff(ServerLevel level, int remainingTicks) {
            this.level = level;
            this.remainingTicks = remainingTicks;
        }
    }

    private static boolean isTechniqueAvailable(ServerPlayer player) {
        return JJKRoster.techniquesForCharacter(
            CharacterSelectionHandler.getSelectedCharacter(player)
        ).stream().anyMatch(d -> d.id().equals("flowing_red_scale"));
    }

    private static String formatSeconds(int ticks) {
        return String.format(java.util.Locale.ROOT, "%.1f", ticks / 20.0);
    }

    public static void clearCooldown(ServerPlayer player) {
        COOLDOWNS.remove(player.getUUID());
    }
}
