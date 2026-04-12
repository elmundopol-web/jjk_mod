package com.pop.jjk;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class MalevolentShrineTechniqueHandler {

    private static final int DOMAIN_DURATION_TICKS = 20 * 10;
    private static final int DOMAIN_BUILDUP_TICKS = 20;
    private static final int DOMAIN_COOLDOWN_TICKS = 20 * 90;
    private static final int DOMAIN_ENERGY_COST = 800;
    private static final double DOMAIN_RADIUS = 35.0D;
    private static final double DOMAIN_RADIUS_SQR = DOMAIN_RADIUS * DOMAIN_RADIUS;
    private static final float BASE_DAMAGE = 1.5F;
    private static final DustParticleOptions SHRINE_RED_DUST = new DustParticleOptions(0xCC0000, 1.2F);
    private static final DustParticleOptions SHRINE_DARK_RED_DUST = new DustParticleOptions(0x1A0000, 1.5F);
    private static final DustParticleOptions SHRINE_BLACK_DUST = new DustParticleOptions(0x0A0A0A, 2.0F);
    private static final int ENVIRONMENT_SLASH_INTERVAL = 4;
    private static final int ENVIRONMENT_SLASH_COUNT = 22;
    private static final int GROUND_SCYTHE_INTERVAL = 10;
    private static final int GROUND_SCYTHE_POINTS = 48;

    private static final Map<UUID, ActiveShrine> ACTIVE_SHRINES = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> COOLDOWNS = new ConcurrentHashMap<>();

    private MalevolentShrineTechniqueHandler() {
    }

    public static void activate(ServerPlayer player) {
        if (!player.isAlive()) {
            return;
        }

        if (!isTechniqueAvailableForPlayer(player)) {
            return;
        }

        UUID playerId = player.getUUID();
        boolean noCooldown = BlueTechniqueHandler.hasNoCooldown(playerId);
        if (ACTIVE_SHRINES.containsKey(playerId)) {
            player.displayClientMessage(Component.translatable("message.jjk.malevolent_shrine_active"), true);
            return;
        }

        int cooldown = COOLDOWNS.getOrDefault(playerId, 0);
        if (cooldown > 0 && !noCooldown) {
            player.displayClientMessage(
                Component.translatable("message.jjk.malevolent_shrine_cooldown", formatSeconds(cooldown)),
                true
            );
            return;
        }

        if (!CursedEnergyManager.consume(player, DOMAIN_ENERGY_COST)) {
            player.displayClientMessage(Component.translatable("message.jjk.not_enough_energy"), true);
            return;
        }

        ServerLevel level = (ServerLevel) player.level();
        ActiveShrine shrine = new ActiveShrine(playerId, player.getId(), level, getEntityCenter(player), DOMAIN_DURATION_TICKS);
        ACTIVE_SHRINES.put(playerId, shrine);

        if (!noCooldown) {
            COOLDOWNS.put(playerId, DOMAIN_COOLDOWN_TICKS);
            syncCooldownToClient(player, DOMAIN_COOLDOWN_TICKS, DOMAIN_COOLDOWN_TICKS);
        } else {
            COOLDOWNS.remove(playerId);
            syncCooldownToClient(player, 0, 0);
        }

        level.playSound(null, player.blockPosition(), SoundEvents.WITHER_SPAWN, SoundSource.PLAYERS, 0.9F, 0.85F);
        level.playSound(null, player.blockPosition(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 1.1F, 0.65F);
        spawnActivationParticles(shrine);
        broadcastShrineState(shrine, true);
        player.displayClientMessage(Component.translatable("message.jjk.malevolent_shrine_cast"), true);
    }

    public static void tick(MinecraftServer server) {
        tickCooldowns(server);

        Set<UUID> expired = new java.util.HashSet<>();
        for (Map.Entry<UUID, ActiveShrine> entry : new ArrayList<>(ACTIVE_SHRINES.entrySet())) {
            UUID ownerId = entry.getKey();
            ActiveShrine shrine = entry.getValue();
            ServerPlayer owner = server.getPlayerList().getPlayer(ownerId);

            if (owner == null || !owner.isAlive() || owner.level() != shrine.level) {
                broadcastShrineState(shrine, false);
                expired.add(ownerId);
                continue;
            }

            shrine.ageTicks++;
            shrine.remainingTicks--;

            applyDomainDamage(shrine, owner);

            if ((shrine.ageTicks % ENVIRONMENT_SLASH_INTERVAL) == 0) {
                sliceEnvironment(shrine, owner);
            }

            if ((shrine.ageTicks % GROUND_SCYTHE_INTERVAL) == 0) {
                sweepGroundScythes(shrine, owner);
            }

            if ((shrine.ageTicks % 5) == 0) {
                sendScreenShake(shrine);
            }

            if ((shrine.ageTicks % 8) == 0) {
                playSlashAmbience(shrine);
            }

            if ((shrine.ageTicks % 30) == 0) {
                shrine.level.playSound(null, owner.blockPosition(), SoundEvents.WITHER_AMBIENT, SoundSource.PLAYERS, 0.4F, 0.7F + (shrine.level.random.nextFloat() * 0.2F));
            }

            if ((shrine.ageTicks % 60) == 0) {
                shrine.level.playSound(null, owner.blockPosition(), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 0.3F, 1.4F);
            }

            if (shrine.remainingTicks <= 0) {
                spawnEndingCollapseParticles(shrine);
                owner.displayClientMessage(Component.translatable("message.jjk.malevolent_shrine_end"), true);
                broadcastShrineState(shrine, false);
                expired.add(ownerId);
            }
        }

        expired.forEach(ACTIVE_SHRINES::remove);
    }

    public static void clearAll() {
        ACTIVE_SHRINES.clear();
        COOLDOWNS.clear();
    }

    public static void clearCooldown(ServerPlayer player) {
        COOLDOWNS.remove(player.getUUID());
        syncCooldownToClient(player, 0, 0);
    }

    public static void syncActiveDomainsToPlayer(ServerPlayer player) {
        for (ActiveShrine shrine : ACTIVE_SHRINES.values()) {
            if (shrine.level == player.level()) {
                ServerPlayNetworking.send(player, createSyncPayload(shrine, true));
            }
        }
    }

    private static boolean isTechniqueAvailableForPlayer(ServerPlayer player) {
        return JJKRoster.techniquesForCharacter(CharacterSelectionHandler.getSelectedCharacter(player)).stream()
            .anyMatch(definition -> definition.id().equals("malevolent_shrine"));
    }

    private static void applyDomainDamage(ActiveShrine shrine, ServerPlayer owner) {
        AABB area = new AABB(shrine.center, shrine.center).inflate(DOMAIN_RADIUS);
        for (LivingEntity entity : shrine.level.getEntitiesOfClass(LivingEntity.class, area, LivingEntity::isAlive)) {
            if (entity == owner) {
                continue;
            }
            if (entity instanceof Player player && player.isSpectator()) {
                continue;
            }

            float insideFactor = getInsideFactor(entity, shrine);
            if (insideFactor <= 0.0F) {
                continue;
            }

            float damage = BASE_DAMAGE + (insideFactor * 2.0F);
            entity.invulnerableTime = 0;
            entity.hurtTime = 0;
            entity.hurtMarked = true;
            entity.hurtServer(shrine.level, shrine.level.damageSources().playerAttack(owner), damage);
            spawnEntitySlashImpact(shrine, entity);
        }
    }

    private static void sendScreenShake(ActiveShrine shrine) {
        for (ServerPlayer player : shrine.level.players()) {
            if (getInsideFactor(player, shrine) > 0.0F) {
                ServerPlayNetworking.send(player, new ScreenShakePayload(0.8F, 3));
            }
        }
    }

    private static void spawnActivationParticles(ActiveShrine shrine) {
        double[] rings = new double[] {8.0D, 15.0D, DOMAIN_RADIUS};
        for (double ringRadius : rings) {
            int points = Math.max(20, Mth.ceil((float) (ringRadius * 2.4D)));
            for (int i = 0; i < points; i++) {
                double angle = (Math.PI * 2.0D * i) / points;
                double spiralOffset = (i / (double) points) * Math.PI * 1.4D;
                double x = shrine.center.x + Math.cos(angle + spiralOffset) * ringRadius;
                double z = shrine.center.z + Math.sin(angle + spiralOffset) * ringRadius;
                shrine.level.sendParticles(ParticleTypes.CRIT, x, shrine.center.y + 0.55D, z, 3, 0.22D, 0.35D, 0.22D, 0.08D);
                shrine.level.sendParticles(ParticleTypes.SWEEP_ATTACK, x, shrine.center.y + 0.25D, z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
                shrine.level.sendParticles(ParticleTypes.LARGE_SMOKE, x, shrine.center.y + 0.1D, z, 2, 0.15D, 0.65D, 0.15D, 0.03D);
            }
        }

        shrine.level.sendParticles(ParticleTypes.SMOKE, shrine.center.x, shrine.center.y + 1.0D, shrine.center.z, 30, 1.6D, 1.2D, 1.6D, 0.03D);
        shrine.level.sendParticles(ParticleTypes.CRIT, shrine.center.x, shrine.center.y + 1.5D, shrine.center.z, 28, 1.4D, 2.0D, 1.4D, 0.12D);
        shrine.level.sendParticles(SHRINE_BLACK_DUST, shrine.center.x, shrine.center.y + 1.8D, shrine.center.z, 24, 4.5D, 2.0D, 4.5D, 0.02D);
    }

    private static void playSlashAmbience(ActiveShrine shrine) {
        double radius = DOMAIN_RADIUS * Math.sqrt(shrine.level.random.nextDouble());
        double angle = shrine.level.random.nextDouble() * Math.PI * 2.0D;
        double x = shrine.center.x + Math.cos(angle) * radius;
        double z = shrine.center.z + Math.sin(angle) * radius;
        double y = shrine.center.y + Mth.lerp(shrine.level.random.nextDouble(), -1.0D, 4.0D);
        shrine.level.playSound(null, x, y, z, SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.15F, 0.6F + (shrine.level.random.nextFloat() * 0.3F));
    }

    private static float getInsideFactor(LivingEntity entity, ActiveShrine shrine) {
        Vec3 entityCenter = getEntityCenter(entity);
        double dx = entityCenter.x - shrine.center.x;
        double dz = entityCenter.z - shrine.center.z;
        double distance = Math.sqrt((dx * dx) + (dz * dz));
        if (distance >= DOMAIN_RADIUS) {
            return 0.0F;
        }

        return Mth.clamp((float) (1.0D - (distance / DOMAIN_RADIUS)), 0.0F, 1.0F);
    }

    private static Vec3 getEntityCenter(LivingEntity entity) {
        return entity.position().add(0.0D, entity.getBbHeight() * 0.5D, 0.0D);
    }

    private static void sliceEnvironment(ActiveShrine shrine, ServerPlayer owner) {
        double minY = shrine.center.y - 2.0D;
        double maxY = shrine.center.y + 12.0D;
        int destroyed = 0;

        for (int i = 0; i < ENVIRONMENT_SLASH_COUNT; i++) {
            destroyed += emitSlashLine(shrine, minY, maxY, true, owner);
        }

        if (destroyed > 0) {
            float pitch = 0.55F + (shrine.level.random.nextFloat() * 0.25F);
            shrine.level.playSound(null, shrine.center.x, shrine.center.y, shrine.center.z, SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.BLOCKS, 0.18F, pitch);
        }
    }

    private static void sweepGroundScythes(ActiveShrine shrine, ServerPlayer owner) {
        int destroyed = 0;
        double ringRadius = DOMAIN_RADIUS - 1.5D;
        for (int i = 0; i < GROUND_SCYTHE_POINTS; i++) {
            double angle = (Math.PI * 2.0D * i) / GROUND_SCYTHE_POINTS;
            double x = shrine.center.x + Math.cos(angle) * ringRadius;
            double z = shrine.center.z + Math.sin(angle) * ringRadius;
            int yBase = Mth.floor(shrine.center.y);

            BlockPos impactPos = null;
            for (int dy = 4; dy >= -6; dy--) {
                BlockPos candidate = BlockPos.containing(x, yBase + dy, z);
                BlockState state = shrine.level.getBlockState(candidate);
                if (!state.isAir()) {
                    impactPos = candidate;
                    break;
                }
            }

            if (impactPos == null) {
                continue;
            }

            destroyed += destroyGroundSlice(shrine, owner, impactPos, angle);
        }

        if (destroyed > 0) {
            shrine.level.playSound(null, shrine.center.x, shrine.center.y, shrine.center.z, SoundEvents.TRIDENT_RIPTIDE_2, SoundSource.BLOCKS, 0.22F, 0.65F);
        }
    }

    private static int emitSlashLine(ActiveShrine shrine, double minY, double maxY, boolean breakEnvironment, ServerPlayer owner) {
        double radius = DOMAIN_RADIUS * Math.sqrt(shrine.level.random.nextDouble());
        double angle = shrine.level.random.nextDouble() * Math.PI * 2.0D;
        double x = shrine.center.x + Math.cos(angle) * radius;
        double z = shrine.center.z + Math.sin(angle) * radius;
        double y = Mth.lerp(shrine.level.random.nextDouble(), minY, maxY);

        double baseAngle = shrine.level.random.nextDouble() * Math.PI * 2.0D;
        double basePitch = Math.toRadians(Mth.lerp(shrine.level.random.nextDouble(), -20.0D, 20.0D));
        double speed = Mth.lerp(shrine.level.random.nextDouble(), 0.18D, 0.28D);
        int streakLength = 4 + shrine.level.random.nextInt(3);
        int destroyed = 0;

        for (int streak = 0; streak < streakLength; streak++) {
            double angleOffset = Math.toRadians(Mth.lerp(shrine.level.random.nextDouble(), -30.0D, 30.0D));
            double pitchOffset = Math.toRadians(Mth.lerp(shrine.level.random.nextDouble(), -12.0D, 12.0D));
            double slashAngle = baseAngle + angleOffset;
            double slashPitch = basePitch + pitchOffset;
            double horizontalSpeed = speed * Math.cos(slashPitch);
            double vx = Math.cos(slashAngle) * horizontalSpeed;
            double vy = Math.sin(slashPitch) * speed;
            double vz = Math.sin(slashAngle) * horizontalSpeed;
            double px = x + (vx * streak * 0.75D);
            double py = y + (vy * streak * 0.75D);
            double pz = z + (vz * streak * 0.75D);

            if (breakEnvironment) {
                destroyed += destroySlashBlocks(shrine, owner, px, py, pz, vx, vy, vz);
            }
        }

        return destroyed;
    }

    private static int destroySlashBlocks(ActiveShrine shrine, ServerPlayer owner, double x, double y, double z, double vx, double vy, double vz) {
        int destroyed = 0;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int baseX = Mth.floor(x);
        int baseY = Mth.floor(y);
        int baseZ = Mth.floor(z);

        for (int ox = -1; ox <= 1; ox++) {
            for (int oy = -1; oy <= 1; oy++) {
                for (int oz = -1; oz <= 1; oz++) {
                    cursor.set(baseX + ox, baseY + oy, baseZ + oz);
                    if (InfiniteDomainTechniqueHandler.isProtectedDomainBlock(shrine.level, cursor)) {
                        continue;
                    }

                    BlockState state = shrine.level.getBlockState(cursor);
                    if (!isShrineSliceable(state)) {
                        continue;
                    }

                    if (shrine.level.destroyBlock(cursor, false, owner)) {
                        Vec3 center = cursor.getCenter();
                        shrine.level.sendParticles(ParticleTypes.CRIT, center.x, center.y, center.z, 2, vx * 0.55D, vy * 0.35D, vz * 0.55D, 0.0D);
                        shrine.level.sendParticles(SHRINE_RED_DUST, center.x, center.y, center.z, 2, vx * 0.45D, vy * 0.25D, vz * 0.45D, 0.0D);
                        shrine.level.sendParticles(SHRINE_DARK_RED_DUST, center.x, center.y, center.z, 1, vx * 0.35D, vy * 0.15D, vz * 0.35D, 0.0D);
                        shrine.level.sendParticles(ParticleTypes.SWEEP_ATTACK, center.x, center.y, center.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
                        destroyed++;
                    }
                }
            }
        }

        return destroyed;
    }

    private static boolean isShrineSliceable(BlockState state) {
        return state.is(BlockTags.LOGS)
            || state.is(BlockTags.LEAVES)
            || state.is(BlockTags.PLANKS)
            || state.is(BlockTags.WOODEN_STAIRS)
            || state.is(BlockTags.WOODEN_SLABS)
            || state.is(BlockTags.WOODEN_FENCES)
            || state.is(BlockTags.SAPLINGS)
            || state.is(BlockTags.FLOWERS)
            || state.is(BlockTags.REPLACEABLE_BY_TREES)
            || state.is(BlockTags.DIRT)
            || state.getBlock() == Blocks.GRASS_BLOCK
            || state.getBlock() == Blocks.COARSE_DIRT
            || state.getBlock() == Blocks.PODZOL
            || state.getBlock() == Blocks.ROOTED_DIRT
            || state.getBlock() == Blocks.MUD
            || state.getBlock() == Blocks.CLAY
            || state.getBlock() == Blocks.SAND
            || state.getBlock() == Blocks.RED_SAND
            || state.getBlock() == Blocks.GRAVEL
            || state.getBlock() == Blocks.NETHERRACK
            || state.getBlock() == Blocks.SHORT_GRASS
            || state.getBlock() == Blocks.TALL_GRASS
            || state.getBlock() == Blocks.FERN
            || state.getBlock() == Blocks.LARGE_FERN
            || state.getBlock() == Blocks.DEAD_BUSH
            || state.getBlock() == Blocks.VINE
            || state.getBlock() == Blocks.BAMBOO
            || state.getBlock() == Blocks.BAMBOO_SAPLING;
    }

    private static int destroyGroundSlice(ActiveShrine shrine, ServerPlayer owner, BlockPos impactPos, double angle) {
        int destroyed = 0;
        double vx = Math.cos(angle) * 0.42D;
        double vz = Math.sin(angle) * 0.42D;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        for (int ox = -1; ox <= 1; ox++) {
            for (int oy = -1; oy <= 1; oy++) {
                for (int oz = -1; oz <= 1; oz++) {
                    cursor.set(impactPos.getX() + ox, impactPos.getY() + oy, impactPos.getZ() + oz);
                    if (InfiniteDomainTechniqueHandler.isProtectedDomainBlock(shrine.level, cursor)) {
                        continue;
                    }

                    BlockState state = shrine.level.getBlockState(cursor);
                    if (!isShrineSliceable(state)) {
                        continue;
                    }

                    if (shrine.level.destroyBlock(cursor, false, owner)) {
                        Vec3 center = cursor.getCenter();
                        shrine.level.sendParticles(ParticleTypes.CRIT, center.x, center.y, center.z, 2, vx, 0.06D, vz, 0.0D);
                        shrine.level.sendParticles(SHRINE_RED_DUST, center.x, center.y, center.z, 2, vx * 0.8D, 0.04D, vz * 0.8D, 0.0D);
                        shrine.level.sendParticles(SHRINE_BLACK_DUST, center.x, center.y, center.z, 1, vx * 0.55D, 0.02D, vz * 0.55D, 0.0D);
                        shrine.level.sendParticles(ParticleTypes.SWEEP_ATTACK, center.x, center.y, center.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
                        destroyed++;
                    }
                }
            }
        }

        return destroyed;
    }

    private static void spawnEntitySlashImpact(ActiveShrine shrine, LivingEntity entity) {
        Vec3 center = getEntityCenter(entity);
        Vec3 slashDir = center.subtract(shrine.center);
        if (slashDir.lengthSqr() < 0.0001D) {
            double randomAngle = shrine.level.random.nextDouble() * Math.PI * 2.0D;
            slashDir = new Vec3(Math.cos(randomAngle), 0.0D, Math.sin(randomAngle));
        } else {
            slashDir = slashDir.normalize();
        }

        shrine.level.sendParticles(ParticleTypes.CRIT, center.x, center.y, center.z, 5, slashDir.x * 0.18D, 0.08D, slashDir.z * 0.18D, 0.0D);
        shrine.level.sendParticles(SHRINE_RED_DUST, center.x, center.y, center.z, 4, slashDir.x * 0.14D, 0.05D, slashDir.z * 0.14D, 0.0D);
        shrine.level.sendParticles(SHRINE_DARK_RED_DUST, center.x, center.y, center.z, 3, slashDir.x * 0.10D, 0.03D, slashDir.z * 0.10D, 0.0D);
        shrine.level.sendParticles(ParticleTypes.SWEEP_ATTACK, center.x, center.y, center.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
    }

    private static void spawnEndingCollapseParticles(ActiveShrine shrine) {
        shrine.level.sendParticles(SHRINE_BLACK_DUST, shrine.center.x, shrine.center.y + 2.0D, shrine.center.z, 80, 6.0D, 2.0D, 6.0D, 0.03D);
        shrine.level.sendParticles(SHRINE_RED_DUST, shrine.center.x, shrine.center.y + 1.2D, shrine.center.z, 70, 5.0D, 1.6D, 5.0D, 0.02D);
        shrine.level.sendParticles(ParticleTypes.LARGE_SMOKE, shrine.center.x, shrine.center.y + 0.8D, shrine.center.z, 50, 6.5D, 1.5D, 6.5D, 0.04D);
        shrine.level.playSound(null, shrine.center.x, shrine.center.y, shrine.center.z, SoundEvents.WITHER_DEATH, SoundSource.PLAYERS, 0.35F, 1.35F);
    }

    private static void tickCooldowns(MinecraftServer server) {
        Set<UUID> expired = new java.util.HashSet<>();
        for (UUID playerId : new ArrayList<>(COOLDOWNS.keySet())) {
            Integer current = COOLDOWNS.get(playerId);
            if (current == null) {
                continue;
            }

            int next = current - 1;
            if (next <= 0) {
                ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                if (player != null) {
                    syncCooldownToClient(player, 0, 0);
                }
                expired.add(playerId);
            } else {
                COOLDOWNS.put(playerId, next);
            }
        }
        expired.forEach(COOLDOWNS::remove);
    }

    private static void syncCooldownToClient(ServerPlayer player, int remaining, int total) {
        ServerPlayNetworking.send(player, new CooldownSyncPayload(remaining, total));
    }

    private static void broadcastShrineState(ActiveShrine shrine, boolean active) {
        MalevolentShrineSyncPayload payload = createSyncPayload(shrine, active);
        for (ServerPlayer player : shrine.level.players()) {
            ServerPlayNetworking.send(player, payload);
        }
    }

    private static MalevolentShrineSyncPayload createSyncPayload(ActiveShrine shrine, boolean active) {
        return new MalevolentShrineSyncPayload(
            shrine.ownerEntityId,
            shrine.center.x,
            shrine.center.y,
            shrine.center.z,
            (float) DOMAIN_RADIUS,
            active ? shrine.remainingTicks : 0,
            active
        );
    }

    private static String formatSeconds(int ticks) {
        return String.format(Locale.ROOT, "%.1f", ticks / 20.0D);
    }

    private static final class ActiveShrine {
        private final UUID ownerId;
        private final int ownerEntityId;
        private final ServerLevel level;
        private final Vec3 center;
        private int remainingTicks;
        private int ageTicks;

        private ActiveShrine(UUID ownerId, int ownerEntityId, ServerLevel level, Vec3 center, int remainingTicks) {
            this.ownerId = ownerId;
            this.ownerEntityId = ownerEntityId;
            this.level = level;
            this.center = center;
            this.remainingTicks = remainingTicks;
            this.ageTicks = 0;
        }
    }
}
