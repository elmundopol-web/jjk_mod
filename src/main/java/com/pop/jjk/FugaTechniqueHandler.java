package com.pop.jjk;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.HashSet;

/**
 * Handler de técnica Open: Fuga con hold-to-release exacto.
 * - Hold 0.6-0.8s (12-16 ticks): partículas negras/naranjas en la palma.
 * - Al soltar: dispara el rayo y entra en cooldown global (compartido con Blue/Red/Purple).
 */
public final class FugaTechniqueHandler {

    private static final Map<UUID, HoldState> HOLDS = new ConcurrentHashMap<>();

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
                // Iniciar hold: NO consumir energía al inicio, se consume al lanzar según carga
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
        for (Map.Entry<UUID, HoldState> entry : new HashSet<>(HOLDS.entrySet())) {
            UUID pid = entry.getKey();
            HoldState state = entry.getValue();
            state.ticks++;

            ServerPlayer p = state.level.getServer().getPlayerList().getPlayer(pid);
            if (p == null || !p.isAlive() || p.level() != state.level) {
                HOLDS.remove(pid);
                continue;
            }

            spawnChargeParticles(state.level, p, state.ticks);

            // Sonido ambiente de fuego cada 8 ticks durante la carga, con pitch que sube 0.8 -> 1.6
            if ((state.ticks % 8) == 0 && state.ticks < FugaTechnique.OVERCHARGE_TICKS) {
                double progress = Math.min(1.0, state.ticks / (double) FugaTechnique.OVERCHARGE_TICKS);
                float pitch = (float) (0.8 + (progress * 0.8));
                state.level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.FIRE_AMBIENT, SoundSource.PLAYERS, 0.4F, pitch);
            }

            // Feedback al alcanzar la carga mínima
            if (state.ticks == FugaTechnique.CHARGE_MIN_TICKS) {
                Vec3 palm = palmAnchor(p);
                state.level.playSound(null, palm.x, palm.y, palm.z, SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 0.6F, 1.8F);
                p.displayClientMessage(Component.literal("»Open: Fuga« listo"), true);
            }

            // Overcharge a los 2.0s: tintar cielo del dueño (cliente)
            if (state.ticks == FugaTechnique.OVERCHARGE_TICKS) {
                net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(p, new FugaOverchargeSyncPayload(120));
                state.level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 0.6F, 1.6F);
                p.displayClientMessage(Component.literal("»Open: Fuga« overcharge"), true);
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
        
        // Calcular coste de energía según carga (20%-100% del coste base)
        float chargePower = (float) Math.min(1.0, state.ticks / (double) FugaTechnique.OVERCHARGE_TICKS);
        int energyCost = (int) (FugaTechnique.ENERGY_COST * (0.2F + 0.8F * chargePower));
        
        // Verificar y consumir energía
        if (!BlueTechniqueHandler.hasNoCooldown(id) && !CursedEnergyManager.consume(player, energyCost)) {
            player.displayClientMessage(Component.translatable("message.jjk.not_enough_energy"), true);
            // Cancelar cooldown si no tenía energía
            return;
        }
        
        FugaProjectileEntity proj = new FugaProjectileEntity(level, player);
        proj.setChargePower(chargePower);
        proj.launchFrom(player);

        // Cooldown global (proporcional a la carga: 40%-100%)
        if (!BlueTechniqueHandler.hasNoCooldown(id)) {
            int cooldownTicks = (int) (FugaTechnique.COOLDOWN_TICKS * (0.4F + 0.6F * chargePower));
            BlueTechniqueHandler.setCooldown(id, cooldownTicks);
            BlueTechniqueHandler.syncCooldownToClient(player, cooldownTicks, cooldownTicks);
        }
        
        // Feedback visual del consumo
        player.displayClientMessage(Component.literal("»Open: Fuga« liberado (" + String.format("%.0f", chargePower * 100) + "% carga)"), true);
    }

    private static void spawnChargeParticles(ServerLevel level, ServerPlayer player, int holdTicks) {
        Vec3 palm = palmAnchor(player);
        if ((holdTicks % 4) != 0) return;

        int count = holdTicks >= FugaTechnique.CHARGE_MIN_TICKS ? 2 : 1;
        double spreadXZ = holdTicks >= FugaTechnique.CHARGE_MIN_TICKS ? 0.10D : 0.07D;
        double spreadY = holdTicks >= FugaTechnique.CHARGE_MIN_TICKS ? 0.06D : 0.04D;

        level.sendParticles(
            JJKParticles.FIRE_CHARGE,
            palm.x,
            palm.y,
            palm.z,
            count,
            spreadXZ,
            spreadY,
            spreadXZ,
            0.0
        );
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
