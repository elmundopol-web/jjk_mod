package com.pop.jjk;

import java.util.UUID;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

public final class RedTechniqueHandler {

    private static final int RED_COOLDOWN_TICKS = 200;
    public static final int RED_ENERGY_COST = 250;

    private RedTechniqueHandler() {
    }

    public static void activate(ServerPlayer player) {
        UUID playerId = player.getUUID();
        int cooldown = BlueTechniqueHandler.getCooldown(playerId);

        if (cooldown > 0 && !BlueTechniqueHandler.hasNoCooldown(playerId)) {
            int remainingSeconds = (cooldown + 19) / 20;
            player.displayClientMessage(Component.translatable("message.jjk.red_cooldown", remainingSeconds), true);
            return;
        }

        if (!CursedEnergyManager.consume(player, RED_ENERGY_COST)) {
            player.displayClientMessage(Component.translatable("message.jjk.not_enough_energy"), true);
            return;
        }

        ServerLevel level = (ServerLevel) player.level();

        if (!BlueTechniqueHandler.hasNoCooldown(playerId)) {
            BlueTechniqueHandler.setCooldown(playerId, RED_COOLDOWN_TICKS);
            BlueTechniqueHandler.syncCooldownToClient(player, RED_COOLDOWN_TICKS, RED_COOLDOWN_TICKS);
        }

        RedProjectileEntity redProjectile = new RedProjectileEntity(level, player);
        level.addFreshEntity(redProjectile);
        player.displayClientMessage(Component.translatable("message.jjk.red_cast"), true);
    }

    public static void tickActivos() {
    }

    public static void clearActivos() {
    }
}
