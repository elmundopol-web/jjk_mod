package com.pop.jjk;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/**
 * Handler de técnica Open: Fuga.
 * Alto coste, alto daño, cooldown largo. Spawnea una entidad Fuga con breve carga y luego rayo + explosión.
 */
public final class FugaTechniqueHandler {

    private static final int FUGA_COOLDOWN_TICKS = 520;
    public static final int FUGA_ENERGY_COST = 600;

    private FugaTechniqueHandler() {}

    public static void activate(ServerPlayer player) {
        if (player == null || !player.isAlive()) return;
        if (!isTechniqueAvailable(player)) return;

        UUID id = player.getUUID();
        int cd = BlueTechniqueHandler.getCooldown(id);
        if (cd > 0 && !BlueTechniqueHandler.hasNoCooldown(id)) {
            int secs = (cd + 19) / 20;
            player.displayClientMessage(Component.translatable("message.jjk.fuga_cooldown", secs), true);
            return;
        }

        if (!CursedEnergyManager.consume(player, FUGA_ENERGY_COST)) {
            player.displayClientMessage(Component.translatable("message.jjk.not_enough_energy"), true);
            return;
        }

        ServerLevel level = (ServerLevel) player.level();
        FugaProjectileEntity proj = new FugaProjectileEntity(level, player);
        level.addFreshEntity(proj);

        if (!BlueTechniqueHandler.hasNoCooldown(id)) {
            BlueTechniqueHandler.setCooldown(id, FUGA_COOLDOWN_TICKS);
            BlueTechniqueHandler.syncCooldownToClient(player, FUGA_COOLDOWN_TICKS, FUGA_COOLDOWN_TICKS);
        }

        Vec3 palm = player.getEyePosition().add(player.getLookAngle().normalize().scale(0.6)).add(0.0, -0.28, 0.0);
        level.playSound(null, palm.x, palm.y, palm.z, SoundEvents.FLINTANDSTEEL_USE, SoundSource.PLAYERS, 0.9F, 1.6F);
        player.displayClientMessage(Component.translatable("message.jjk.fuga_open"), true);
    }

    private static boolean isTechniqueAvailable(ServerPlayer player) {
        return JJKRoster.techniquesForCharacter(CharacterSelectionHandler.getSelectedCharacter(player)).stream()
            .anyMatch(def -> def.id().equals("fuga"));
    }
}
