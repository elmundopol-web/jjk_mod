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
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.phys.Vec3;

public final class FlowingRedScaleTechniqueHandler {

    private static final int   COOLDOWN_TICKS  = 180;
    private static final int   ENERGY_COST     = 120;
    private static final int   EFFECT_DURATION = 100;

    private static final DustParticleOptions BLOOD_DUST = new DustParticleOptions(0xCC1010, 0.9F);

    private static final Map<UUID, Integer> COOLDOWNS = new HashMap<>();

    private FlowingRedScaleTechniqueHandler() {
    }

    public static void activate(ServerPlayer player) {
        if (!player.isAlive()) return;
        if (!isTechniqueAvailable(player)) return;

        int cd = COOLDOWNS.getOrDefault(player.getUUID(), 0);
        if (cd > 0) {
            player.displayClientMessage(
                Component.translatable("message.jjk.flowing_red_scale_cooldown", formatSeconds(cd)), true);
            return;
        }

        if (!CursedEnergyManager.consume(player, ENERGY_COST)) {
            player.displayClientMessage(Component.translatable("message.jjk.not_enough_energy"), true);
            return;
        }

        applyEffects(player);
        spawnActivationParticles((ServerLevel) player.level(), player);

        COOLDOWNS.put(player.getUUID(), COOLDOWN_TICKS);
        player.displayClientMessage(Component.translatable("message.jjk.flowing_red_scale_cast"), true);
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

    private static void applyEffects(ServerPlayer player) {
        HolderLookup.RegistryLookup<MobEffect> lookup =
            ((ServerLevel) player.level()).registryAccess().lookupOrThrow(Registries.MOB_EFFECT);

        lookup.get(ResourceKey.create(Registries.MOB_EFFECT, Identifier.withDefaultNamespace("speed")))
            .ifPresent(h -> player.addEffect(new MobEffectInstance(h, EFFECT_DURATION, 1, false, true, true)));
        lookup.get(ResourceKey.create(Registries.MOB_EFFECT, Identifier.withDefaultNamespace("strength")))
            .ifPresent(h -> player.addEffect(new MobEffectInstance(h, EFFECT_DURATION, 0, false, true, true)));
        lookup.get(ResourceKey.create(Registries.MOB_EFFECT, Identifier.withDefaultNamespace("regeneration")))
            .ifPresent(h -> player.addEffect(new MobEffectInstance(h, EFFECT_DURATION, 0, false, true, true)));
    }

    private static void spawnActivationParticles(ServerLevel level, ServerPlayer player) {
        Vec3 pos = player.position().add(0, player.getBbHeight() * 0.5, 0);
        level.sendParticles(BLOOD_DUST, pos.x, pos.y, pos.z, 40, 0.5, 0.8, 0.5, 0.02);
        level.sendParticles(BLOOD_DUST, pos.x, pos.y + 0.5, pos.z, 20, 0.3, 0.5, 0.3, 0.05);
    }

    private static boolean isTechniqueAvailable(ServerPlayer player) {
        return JJKRoster.techniquesForCharacter(
            CharacterSelectionHandler.getSelectedCharacter(player)
        ).stream().anyMatch(d -> d.id().equals("flowing_red_scale"));
    }

    private static String formatSeconds(int ticks) {
        return String.format(java.util.Locale.ROOT, "%.1f", ticks / 20.0);
    }
}
