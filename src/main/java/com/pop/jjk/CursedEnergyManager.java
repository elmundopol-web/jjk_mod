package com.pop.jjk;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

public final class CursedEnergyManager {

    public static final int MAX_ENERGY = 1000;
    private static final int REGEN_PER_SECOND = 5;
    private static final int REGEN_INTERVAL_TICKS = 20;

    private static final Map<UUID, Integer> ENERGY = new ConcurrentHashMap<>();
    private static final java.util.Set<UUID> INFINITE_ENERGY = java.util.Collections.newSetFromMap(new ConcurrentHashMap<>());

    private CursedEnergyManager() {
    }

    public static int getEnergy(UUID playerId) {
        return ENERGY.computeIfAbsent(playerId, k -> MAX_ENERGY);
    }

    public static int getEnergy(ServerPlayer player) {
        return getEnergy(player.getUUID());
    }

    public static void setEnergy(UUID playerId, int amount) {
        ENERGY.put(playerId, Math.max(0, Math.min(MAX_ENERGY, amount)));
    }

    public static boolean consume(ServerPlayer player, int amount) {
        if (INFINITE_ENERGY.contains(player.getUUID())) {
            return true;
        }
        UUID playerId = player.getUUID();
        int current = getEnergy(playerId);
        if (current < amount) {
            return false;
        }
        setEnergy(playerId, current - amount);
        syncToClient(player);
        return true;
    }

    public static boolean hasEnergy(ServerPlayer player, int amount) {
        return getEnergy(player) >= amount;
    }

    public static void tick(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!player.isAlive() || player.isSpectator()) {
                continue;
            }

            UUID playerId = player.getUUID();
            int current = getEnergy(playerId);

            // Regen pasiva (solo si no tiene max)
            if (current < MAX_ENERGY && player.tickCount % REGEN_INTERVAL_TICKS == 0) {
                int newEnergy = Math.min(MAX_ENERGY, current + REGEN_PER_SECOND);
                setEnergy(playerId, newEnergy);
                syncToClient(player);
            }
        }
    }

    public static void syncToClient(ServerPlayer player) {
        int energy = getEnergy(player);
        ServerPlayNetworking.send(player, new CursedEnergySyncPayload(energy, MAX_ENERGY));
    }

    public static void onPlayerJoin(ServerPlayer player) {
        ENERGY.computeIfAbsent(player.getUUID(), k -> MAX_ENERGY);
        syncToClient(player);
    }

    public static boolean toggleInfiniteEnergy(ServerPlayer player) {
        UUID playerId = player.getUUID();
        if (INFINITE_ENERGY.contains(playerId)) {
            INFINITE_ENERGY.remove(playerId);
            return false;
        }
        INFINITE_ENERGY.add(playerId);
        setEnergy(playerId, MAX_ENERGY);
        syncToClient(player);
        return true;
    }

    public static boolean hasInfiniteEnergy(ServerPlayer player) {
        return INFINITE_ENERGY.contains(player.getUUID());
    }

    public static void clearAll() {
        ENERGY.clear();
        INFINITE_ENERGY.clear();
    }
}
