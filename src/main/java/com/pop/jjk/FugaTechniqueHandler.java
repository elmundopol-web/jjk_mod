package com.pop.jjk;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Handler de técnica Open: Fuga con hold-to-release exacto.
 * - Hold 0.6-0.8s (12-16 ticks): partículas negras/naranjas en la palma.
 * - Al soltar: dispara el rayo y entra en cooldown global (compartido con Blue/Red/Purple).
 */
public final class FugaTechniqueHandler {

    private static final Map<UUID, HoldState> HOLDS = new HashMap<>();

    private FugaTechniqueHandler() {}

    // Compat: un "use" sin hold inicia la carga
    public static void activate(ServerPlayer player) {
        onHold(player, true);
    }

    public static void onHold(ServerPlayer player, boolean holding) {
        if (player == null || !player.isAlive()) return;
        if (!isTechniqueAvailable(player)) return;

        UUID id = player.getUUID();
        int cd = BlueTechniqueHandler.getCooldown(id);
        if (holding) {
            if (cd > 0 && !BlueTechniqueHandler.hasNoCooldown(id)) {
                player.displayClientMessage(Component.translatable("message.jjk.fuga_cooldown", formatSeconds(cd)), true);
                return;
            }
            HoldState state = HOLDS.get(id);
            if (state == null) {
                // Iniciar hold: consumir energía al inicio
                if (!BlueTechniqueHandler.hasNoCooldown(id) && !CursedEnergyManager.consume(player, FugaTechnique.ENERGY_COST)) {
                    player.displayClientMessage(Component.translatable("message.jjk.not_enough_energy"), true);
                    return;
                }
                ServerLevel level = (ServerLevel) player.level();
                HOLDS.put(id, new HoldState(level));
                // Sonido "Open" sutil
                Vec3 palm = palmAnchor(player);
                level.playSound(null, palm.x, palm.y, palm.z, SoundEvents.FLINTANDSTEEL_USE, SoundSource.PLAYERS, 0.8F, 1.9F);
                player.displayClientMessage(Component.translatable("message.jjk.fuga_open"), true);
            }
        } else {
            HoldState state = HOLDS.get(id);
            if (state == null) return;
            // Si ya alcanzó la mínima carga, disparar ahora; si no, marcar para disparo cuando alcance el mínimo
            if (state.ticks >= FugaTechnique.CHARGE_MIN_TICKS) {
                launchNow(player);
            } else {
                state.pendingRelease = true;
            }
        }
    }

    public static void tick() {
        // Tick holds
        for (Map.Entry<UUID, HoldState> entry : new HashMap<>(HOLDS).entrySet()) {
            UUID pid = entry.getKey();
            HoldState state = entry.getValue();
            state.ticks++;

            ServerPlayer p = state.level.getServer().getPlayerList().getPlayer(pid);
            if (p == null || !p.isAlive() || p.level() != state.level) {
                HOLDS.remove(pid);
                continue;
            }

            spawnChargeParticles(state.level, p);

            // Overcharge a los 2.0s: tintar cielo del dueño (cliente)
            if (state.ticks == FugaTechnique.OVERCHARGE_TICKS) {
                net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(p, new FugaOverchargeSyncPayload(120));
                state.level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 0.6F, 1.6F);
            }

            // Si el jugador soltó antes de mínimo, disparar cuando llegue
            if (state.pendingRelease && state.ticks >= FugaTechnique.CHARGE_MIN_TICKS) {
                launchNow(p);
            }
        }
    }

    public static void clearActive() {
        HOLDS.clear();
    }

    private static void launchNow(ServerPlayer player) {
        UUID id = player.getUUID();
        HoldState state = HOLDS.remove(id);
        if (state == null) return;

        ServerLevel level = (ServerLevel) player.level();
        FugaProjectileEntity proj = new FugaProjectileEntity(level, player);
        float power = (float) Math.min(1.0, state.ticks / (double) FugaTechnique.OVERCHARGE_TICKS);
        level.addFreshEntity(proj);
        proj.setChargePower(power);
        proj.launchFrom(player);

        // Cooldown global
        if (!BlueTechniqueHandler.hasNoCooldown(id)) {
            BlueTechniqueHandler.setCooldown(id, FugaTechnique.COOLDOWN_TICKS);
            BlueTechniqueHandler.syncCooldownToClient(player, FugaTechnique.COOLDOWN_TICKS, FugaTechnique.COOLDOWN_TICKS);
        }
    }

    private static void spawnChargeParticles(ServerLevel level, ServerPlayer player) {
        Vec3 palm = palmAnchor(player);
        if ((player.tickCount & 1) == 1) return;
        for (int i = 0; i < 2; i++) {
            level.sendParticles(JJKParticles.FIRE_CHARGE,
                palm.x, palm.y, palm.z,
                1,
                (player.getRandom().nextDouble() - 0.5) * 0.12,
                (player.getRandom().nextDouble() - 0.5) * 0.08,
                (player.getRandom().nextDouble() - 0.5) * 0.12,
                0.0);
        }
    }

    private static Vec3 palmAnchor(ServerPlayer player) {
        Vec3 dir = player.getLookAngle().normalize();
        return player.getEyePosition().add(dir.scale(0.70)).add(0.0, -0.28, 0.0);
    }

    private static boolean isTechniqueAvailable(ServerPlayer player) {
        return JJKRoster.techniquesForCharacter(CharacterSelectionHandler.getSelectedCharacter(player)).stream()
            .anyMatch(def -> def.id().equals("fuga"));
    }

    private static String formatSeconds(int ticks) {
        return String.format(java.util.Locale.ROOT, "%.1f", ticks / 20.0);
    }

    private static final class HoldState {
        final ServerLevel level;
        int ticks = 0;
        boolean pendingRelease = false;
        HoldState(ServerLevel level) { this.level = level; }
    }
}
