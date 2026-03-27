package com.pop.jjk;

import java.util.UUID;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

public final class PurpleTechniqueHandler {

    private static final int PURPLE_COOLDOWN_TICKS = 200;
    public static final int PURPLE_ENERGY_COST = 550;

    private PurpleTechniqueHandler() {
    }

    public static void activate(ServerPlayer player) {
        UUID playerId = player.getUUID();
        int cooldown = BlueTechniqueHandler.getCooldown(playerId);

        if (cooldown > 0 && !BlueTechniqueHandler.hasNoCooldown(playerId)) {
            int remainingSeconds = (cooldown + 19) / 20;
            player.displayClientMessage(Component.translatable("message.jjk.purple_cooldown", remainingSeconds), true);
            return;
        }

        if (!CursedEnergyManager.consume(player, PURPLE_ENERGY_COST)) {
            player.displayClientMessage(Component.translatable("message.jjk.not_enough_energy"), true);
            return;
        }

        ServerLevel level = (ServerLevel) player.level();

        if (!BlueTechniqueHandler.hasNoCooldown(playerId)) {
            BlueTechniqueHandler.setCooldown(playerId, PURPLE_COOLDOWN_TICKS);
            BlueTechniqueHandler.syncCooldownToClient(player, PURPLE_COOLDOWN_TICKS, PURPLE_COOLDOWN_TICKS);
        }

        PurpleProjectileEntity purpleProjectile = new PurpleProjectileEntity(level, player);
        level.addFreshEntity(purpleProjectile);
        player.displayClientMessage(Component.translatable("message.jjk.purple_charge"), true);
    }
}
