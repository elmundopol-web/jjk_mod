package com.pop.jjk;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

public final class DismantleTechniqueHandler {

    private static final int COOLDOWN_TICKS = 20;
    private static final int ENERGY_COST = 60;
    private static final Map<UUID, Integer> COOLDOWNS = new ConcurrentHashMap<>();

    private DismantleTechniqueHandler() {
    }

    public static void activate(ServerPlayer player) {
        if (player == null || !player.isAlive()) {
            return;
        }
        if (!isTechniqueAvailableForPlayer(player)) {
            return;
        }

        int cd = COOLDOWNS.getOrDefault(player.getUUID(), 0);
        if (cd > 0) {
            return;
        }

        if (!CursedEnergyManager.consume(player, ENERGY_COST)) {
            player.displayClientMessage(Component.translatable("message.jjk.not_enough_energy"), true);
            return;
        }

        ServerLevel level = (ServerLevel) player.level();
        DismantleProjectileEntity proj = new DismantleProjectileEntity(level, player);
        level.addFreshEntity(proj);

        level.playSound(null, player.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.0F, 1.8F);
        level.playSound(null, player.blockPosition(), SoundEvents.TRIDENT_HIT, SoundSource.PLAYERS, 0.35F, 1.0F);

        COOLDOWNS.put(player.getUUID(), COOLDOWN_TICKS);
    }

    public static void tick() {
        if (COOLDOWNS.isEmpty()) return;
        var iter = COOLDOWNS.entrySet().iterator();
        while (iter.hasNext()) {
            var e = iter.next();
            int next = e.getValue() - 1;
            if (next <= 0) iter.remove(); else e.setValue(next);
        }
    }

    public static void clearActive() {
        COOLDOWNS.clear();
    }

    private static boolean isTechniqueAvailableForPlayer(ServerPlayer player) {
        return JJKRoster.techniquesForCharacter(CharacterSelectionHandler.getSelectedCharacter(player)).stream()
            .anyMatch(definition -> definition.id().equals("dismantle"));
    }
}
