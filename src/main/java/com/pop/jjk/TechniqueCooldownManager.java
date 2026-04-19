package com.pop.jjk;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

public final class TechniqueCooldownManager {

    private static final Map<UUID, CooldownState> COOLDOWNS = new ConcurrentHashMap<>();

    private TechniqueCooldownManager() {
    }

    public static int getRemaining(UUID playerId) {
        CooldownState state = COOLDOWNS.get(playerId);
        return state == null ? 0 : state.remainingTicks;
    }

    public static int getTotal(UUID playerId) {
        CooldownState state = COOLDOWNS.get(playerId);
        return state == null ? 0 : state.totalTicks;
    }

    public static void set(ServerPlayer player, int ticks) {
        set(player, ticks, ticks);
    }

    public static void set(ServerPlayer player, int remainingTicks, int totalTicks) {
        if (player == null) {
            return;
        }

        set(player.getUUID(), remainingTicks, totalTicks);
        sync(player, remainingTicks, totalTicks);
    }

    public static void set(UUID playerId, int ticks) {
        set(playerId, ticks, ticks);
    }

    public static void set(UUID playerId, int remainingTicks, int totalTicks) {
        if (playerId == null) {
            return;
        }

        if (remainingTicks <= 0 || totalTicks <= 0) {
            COOLDOWNS.remove(playerId);
            return;
        }

        COOLDOWNS.put(playerId, new CooldownState(remainingTicks, totalTicks));
    }

    public static void clear(ServerPlayer player) {
        if (player == null) {
            return;
        }

        clear(player.getUUID());
        sync(player, 0, 0);
    }

    public static void clear(UUID playerId) {
        if (playerId == null) {
            return;
        }
        COOLDOWNS.remove(playerId);
    }

    public static void clearAll() {
        COOLDOWNS.clear();
    }

    public static void tick(MinecraftServer server) {
        for (UUID playerId : new java.util.ArrayList<>(COOLDOWNS.keySet())) {
            CooldownState state = COOLDOWNS.get(playerId);
            if (state == null) {
                continue;
            }

            int next = state.remainingTicks - 1;
            if (next <= 0) {
                COOLDOWNS.remove(playerId);
                if (server != null) {
                    ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                    if (player != null) {
                        sync(player, 0, 0);
                    }
                }
            } else {
                COOLDOWNS.put(playerId, new CooldownState(next, state.totalTicks));
            }
        }
    }

    public static void sync(ServerPlayer player) {
        if (player == null) {
            return;
        }

        CooldownState state = COOLDOWNS.get(player.getUUID());
        if (state == null) {
            sync(player, 0, 0);
            return;
        }

        sync(player, state.remainingTicks, state.totalTicks);
    }

    public static void sync(ServerPlayer player, int remainingTicks, int totalTicks) {
        if (player == null) {
            return;
        }
        ServerPlayNetworking.send(player, new CooldownSyncPayload(remainingTicks, totalTicks));
    }

    private static final class CooldownState {
        private final int remainingTicks;
        private final int totalTicks;

        private CooldownState(int remainingTicks, int totalTicks) {
            this.remainingTicks = remainingTicks;
            this.totalTicks = totalTicks;
        }
    }
}
