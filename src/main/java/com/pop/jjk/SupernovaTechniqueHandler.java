package com.pop.jjk;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

public final class SupernovaTechniqueHandler {

    private static final int COOLDOWN_TICKS = 400;
    private static final int COST_PER_ORB = 18;   // coste por orbe
    private static final int MAX_ORBS = 12;       // máximo de orbes acumulables

    private static final DustParticleOptions BLOOD_DUST_SMALL = new DustParticleOptions(0xFF7070, 0.7F);

    private static final Map<UUID, Integer> COOLDOWNS = new HashMap<>();
    private static final Map<UUID, HoldState> HOLDS = new HashMap<>();
    private static final Map<UUID, List<Integer>> ORBS = new HashMap<>();
    private static final Map<UUID, Double> ORBIT_SPEEDS = new HashMap<>();

    private SupernovaTechniqueHandler() {}

    // Compat: un "use" sin hold inicia la carga
    public static void activate(ServerPlayer player) {
        onHold(player, true);
    }

    public static void onHold(ServerPlayer player, boolean holding) {
        if (!player.isAlive()) return;
        if (!isTechniqueAvailable(player)) return;

        UUID id = player.getUUID();
        int cd = COOLDOWNS.getOrDefault(id, 0);
        if (holding) {
            if (cd > 0 && !BlueTechniqueHandler.hasNoCooldown(id)) {
                player.displayClientMessage(Component.translatable("message.jjk.supernova_cooldown", formatSeconds(cd)), true);
                return;
            }
            HoldState prev = HOLDS.putIfAbsent(id, new HoldState((ServerLevel) player.level()));
            if (prev == null) {
                ((ServerLevel) player.level()).playSound(null, player.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.6F, 1.6F);
            }
        } else {
            HoldState state = HOLDS.remove(id);
            if (state != null) {
                launchAll(player);
                if (!BlueTechniqueHandler.hasNoCooldown(id)) {
                    COOLDOWNS.put(id, COOLDOWN_TICKS);
                } else {
                    COOLDOWNS.remove(id);
                }
            }
        }
    }

    public static void tick() {
        // Tick cooldowns
        COOLDOWNS.entrySet().removeIf(entry -> {
            int next = entry.getValue() - 1;
            if (next <= 0) return true;
            entry.setValue(next);
            return false;
        });

        // Tick holds: spawn orbs, increase orbit speed, ring FX
        for (Map.Entry<UUID, HoldState> entry : new ArrayList<>(HOLDS.entrySet())) {
            UUID pid = entry.getKey();
            HoldState state = entry.getValue();
            state.ticks++;

            ServerPlayer p = state.level.getServer().getPlayerList().getPlayer(pid);
            if (p == null || !p.isAlive() || p.level() != state.level) {
                HOLDS.remove(pid);
                continue;
            }

            List<Integer> list = ORBS.computeIfAbsent(pid, k -> new ArrayList<>());
            if (state.spawnCooldown > 0) state.spawnCooldown--;
            int spawnInterval = Math.max(3, 10 - (state.ticks / 20) * 2);
            if (state.spawnCooldown <= 0 && list.size() < MAX_ORBS) {
                if (BlueTechniqueHandler.hasNoCooldown(pid) || CursedEnergyManager.consume(p, COST_PER_ORB)) {
                    spawnOrb(state.level, p, list);
                    state.spawnCooldown = spawnInterval;
                } else {
                    state.spawnCooldown = 6;
                }
            }

            double speed = 0.14 + 0.02 * list.size() + 0.0012 * state.ticks;
            ORBIT_SPEEDS.put(pid, Math.min(0.6, speed));

            if (state.ticks % 6 == 0) {
                Vec3 c = p.position().add(0, p.getBbHeight() * 0.5, 0);
                double r = 1.8 + Math.min(1.5, list.size() * 0.12);
                for (int i = 0; i < 18; i++) {
                    double ang = (i / 18.0) * (Math.PI * 2.0) + (state.ticks * 0.07);
                    double x = c.x + Math.cos(ang) * r;
                    double z = c.z + Math.sin(ang) * r;
                    double y = c.y + (p.getRandom().nextDouble() - 0.5) * 0.5;
                    state.level.sendParticles(BLOOD_DUST_SMALL, x, y, z, 1, 0.02, 0.02, 0.02, 0.0);
                }
            }

            // Limpiar referencias a orbes muertos
            list.removeIf(id -> state.level.getEntity(id) == null);
            if (list.isEmpty()) {
                ORBS.remove(pid);
            }
        }
    }

    public static void clearActive() {
        COOLDOWNS.clear();
        HOLDS.clear();
        ORBS.clear();
        ORBIT_SPEEDS.clear();
    }

    private static void spawnOrb(ServerLevel level, ServerPlayer p, List<Integer> list) {
        float baseAngle = p.getRandom().nextFloat() * (float) (Math.PI * 2.0);
        SupernovaOrbProjectileEntity orb = new SupernovaOrbProjectileEntity(level, p, baseAngle);
        level.addFreshEntity(orb);
        list.add(orb.getId());
        level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BEACON_AMBIENT, SoundSource.PLAYERS, 0.25F, 1.9F);
    }

    private static void launchAll(ServerPlayer player) {
        UUID pid = player.getUUID();
        List<Integer> list = ORBS.get(pid);
        if (list == null || list.isEmpty()) return;
        Vec3 dir = player.getLookAngle().normalize();
        ServerLevel level = (ServerLevel) player.level();
        for (Integer id : new ArrayList<>(list)) {
            if (level.getEntity(id) instanceof SupernovaOrbProjectileEntity orb) {
                orb.launch(dir);
            }
        }
        player.displayClientMessage(Component.translatable("message.jjk.supernova_cast"), true);
    }

    public static void onOrbExploded(UUID playerId, int orbId) {
        List<Integer> list = ORBS.get(playerId);
        if (list != null) {
            list.remove((Integer) orbId);
            if (list.isEmpty()) {
                ORBS.remove(playerId);
            }
        }
    }

    public static double getOrbitSpeed(UUID playerId) {
        return ORBIT_SPEEDS.getOrDefault(playerId, 0.18);
    }

    // (screen shake opcional no requerido por este rediseño)

    private static final class HoldState {
        final ServerLevel level;
        int ticks = 0;
        int spawnCooldown = 0;
        HoldState(ServerLevel level) { this.level = level; }
    }

    private static boolean isTechniqueAvailable(ServerPlayer player) {
        return JJKRoster.techniquesForCharacter(
            CharacterSelectionHandler.getSelectedCharacter(player)
        ).stream().anyMatch(d -> d.id().equals("supernova"));
    }

    private static String formatSeconds(int ticks) {
        return String.format(java.util.Locale.ROOT, "%.1f", ticks / 20.0);
    }

    public static void clearCooldown(ServerPlayer player) {
        COOLDOWNS.remove(player.getUUID());
    }
}
