package com.pop.jjk;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

public final class DismantleTechniqueHandler {

    private static final int COOLDOWN_TICKS = 20;
    private static final int ENERGY_COST = 60;

    private DismantleTechniqueHandler() {
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
        DismantleProjectileEntity proj = new DismantleProjectileEntity(level, player);
        level.addFreshEntity(proj);

        level.playSound(null, player.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.0F, 1.8F);
        level.playSound(null, player.blockPosition(), SoundEvents.TRIDENT_HIT, SoundSource.PLAYERS, 0.35F, 1.0F);

        TechniqueCooldownManager.set(player.getUUID(), COOLDOWN_TICKS);
    }

    public static void tick() {
    }

    public static void clearActive() {
    }

    private static boolean isTechniqueAvailableForPlayer(ServerPlayer player) {
        return JJKRoster.techniquesForCharacter(CharacterSelectionHandler.getSelectedCharacter(player)).stream()
            .anyMatch(definition -> definition.id().equals("dismantle"));
    }
}
