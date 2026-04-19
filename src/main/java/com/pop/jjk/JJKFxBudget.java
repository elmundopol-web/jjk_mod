package com.pop.jjk;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.level.ServerLevel;

public final class JJKFxBudget {

    private static final int MAX_PARTICLE_COST_PER_TICK = 900;
    private static final int MAX_SOUND_COST_PER_TICK = 24;
    private static final int MAX_BLOCK_OPS_PER_TICK = 80;

    private static final Map<ServerLevel, BudgetState> BUDGETS = new ConcurrentHashMap<>();

    private JJKFxBudget() {
    }

    public static boolean allowParticles(ServerLevel level, int cost) {
        return consume(level, Math.max(1, cost), BudgetType.PARTICLES);
    }

    public static boolean allowSound(ServerLevel level) {
        return consume(level, 1, BudgetType.SOUNDS);
    }

    public static boolean allowBlockOperation(ServerLevel level) {
        return consume(level, 1, BudgetType.BLOCKS);
    }

    public static void clear() {
        BUDGETS.clear();
    }

    private static boolean consume(ServerLevel level, int cost, BudgetType type) {
        BudgetState state = BUDGETS.computeIfAbsent(level, ignored -> new BudgetState());
        long gameTime = level.getGameTime();
        if (state.tick != gameTime) {
            state.tick = gameTime;
            state.particleCost = 0;
            state.soundCost = 0;
            state.blockOps = 0;
        }

        return switch (type) {
            case PARTICLES -> {
                if (state.particleCost + cost > MAX_PARTICLE_COST_PER_TICK) {
                    yield false;
                }
                state.particleCost += cost;
                yield true;
            }
            case SOUNDS -> {
                if (state.soundCost + cost > MAX_SOUND_COST_PER_TICK) {
                    yield false;
                }
                state.soundCost += cost;
                yield true;
            }
            case BLOCKS -> {
                if (state.blockOps + cost > MAX_BLOCK_OPS_PER_TICK) {
                    yield false;
                }
                state.blockOps += cost;
                yield true;
            }
        };
    }

    private enum BudgetType {
        PARTICLES,
        SOUNDS,
        BLOCKS
    }

    private static final class BudgetState {
        private long tick = Long.MIN_VALUE;
        private int particleCost;
        private int soundCost;
        private int blockOps;
    }
}
