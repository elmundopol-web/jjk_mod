package com.pop.jjk;

import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class InfiniteDomainTechniqueHandler {

    private static final int DOMAIN_ENERGY_COST = 600;
    private static final int DOMAIN_DURATION_TICKS = 20 * 12;
    private static final int DOMAIN_BUILDUP_TICKS = 50;
    private static final int DOMAIN_COLLAPSE_TICKS = 24;
    private static final int DOMAIN_COOLDOWN_TICKS = 20 * 60;
    private static final double DOMAIN_RADIUS = 20.0;
    private static final double DOMAIN_RADIUS_SQR = DOMAIN_RADIUS * DOMAIN_RADIUS;
    private static final int DOMAIN_RADIUS_BLOCKS = 20;
    private static final int DOMAIN_FLOOR_OFFSET = -1;
    private static final int DOMAIN_CEILING_OFFSET = 12;
    private static final int DOMAIN_INITIAL_CLEAR_LAYERS = 3;
    private static final int DOMAIN_RELOCATION_DEPTH = 4;
    private static final float DOMAIN_DAMAGE_BONUS_MULTIPLIER = 0.5F;
    private static final BlockState DOMAIN_SHELL_BLOCK = Blocks.BLACK_CONCRETE.defaultBlockState();

    private static final Map<UUID, ActiveDomain> ACTIVE_DOMAINS = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> COOLDOWNS = new ConcurrentHashMap<>();
    private static final Map<UUID, ParalyzedMobState> PARALYZED_MOBS = new ConcurrentHashMap<>();
    private static final Set<UUID> BONUS_DAMAGE_GUARD = ConcurrentHashMap.newKeySet();

    private InfiniteDomainTechniqueHandler() {
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
        if (ACTIVE_DOMAINS.containsKey(playerId)) {
            player.displayClientMessage(Component.translatable("message.jjk.infinite_domain_active"), true);
            return;
        }

        int cooldown = COOLDOWNS.getOrDefault(playerId, 0);
        if (cooldown > 0 && !noCooldown) {
            player.displayClientMessage(
                Component.translatable("message.jjk.infinite_domain_cooldown", formatSeconds(cooldown)),
                true
            );
            return;
        }

        if (!CursedEnergyManager.consume(player, DOMAIN_ENERGY_COST)) {
            player.displayClientMessage(Component.translatable("message.jjk.not_enough_energy"), true);
            return;
        }

        ServerLevel level = (ServerLevel) player.level();
        Vec3 center = getEntityCenter(player);
        ActiveDomain domain = new ActiveDomain(playerId, player.getId(), level, center, player.blockPosition(), DOMAIN_DURATION_TICKS);
        initializeDomainShell(domain);
        ACTIVE_DOMAINS.put(playerId, domain);
        if (!noCooldown) {
            COOLDOWNS.put(playerId, DOMAIN_COOLDOWN_TICKS);
            syncCooldownToClient(player, DOMAIN_COOLDOWN_TICKS, DOMAIN_COOLDOWN_TICKS);
        } else {
            COOLDOWNS.remove(playerId);
            syncCooldownToClient(player, 0, 0);
        }

        level.playSound(null, player.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.4F, 0.55F);
        level.playSound(null, player.blockPosition(), SoundEvents.END_PORTAL_SPAWN, SoundSource.PLAYERS, 0.8F, 0.75F);
        broadcastDomainState(domain, true);
        spawnActivationParticles(domain);
        player.displayClientMessage(Component.translatable("message.jjk.infinite_domain_cast"), true);
    }

    public static void tick(MinecraftServer server) {
        tickCooldowns(server);

        Set<UUID> paralyzedThisTick = new java.util.HashSet<>();
        Set<UUID> domainsToRemove = new java.util.HashSet<>();

        for (Map.Entry<UUID, ActiveDomain> entry : new java.util.ArrayList<>(ACTIVE_DOMAINS.entrySet())) {
            UUID ownerId = entry.getKey();
            ActiveDomain domain = entry.getValue();
            ServerPlayer owner = server.getPlayerList().getPlayer(ownerId);

            if (owner == null || !owner.isAlive() || owner.level() != domain.level) {
                restoreDomainShell(domain);
                broadcastDomainState(domain, false);
                domainsToRemove.add(ownerId);
                continue;
            }

            domain.ageTicks++;
            advanceDomainWall(domain);
            enforceDomainBoundary(domain);
            domain.remainingTicks--;
            applyDomainParalysis(domain, paralyzedThisTick);
            spawnAmbientParticles(domain);

            float buildProgress = getBuildProgress(domain);
            float collapseProgress = getCollapseProgress(domain);

            if (buildProgress > 0.15F && domain.ageTicks % 3 == 0) {
                for (ServerPlayer p : domain.level.getPlayers(pl -> isInsideDomain(pl, domain))) {
                    float insideFactor = getInsideFactor(p, domain);
                    float intensity = (0.06F + (0.16F * buildProgress) + (0.08F * collapseProgress)) * insideFactor;
                    if (intensity > 0.02F) {
                        ServerPlayNetworking.send(p, new ScreenShakePayload(intensity, collapseProgress > 0.0F ? 4 : 3));
                    }
                }
            }

            if (buildProgress > 0.35F && domain.ageTicks % 50 == 0) {
                float volume = 0.35F + (0.25F * buildProgress);
                float pitch = 0.55F + (0.08F * domain.level.random.nextFloat());
                domain.level.playSound(null, owner.blockPosition(), SoundEvents.BEACON_AMBIENT, SoundSource.PLAYERS, volume, pitch);
            }

            if (buildProgress > 0.55F && domain.ageTicks % 72 == 0) {
                float volume = 0.10F + (0.10F * collapseProgress);
                float pitch = 0.95F + (0.20F * domain.level.random.nextFloat());
                domain.level.playSound(null, owner.blockPosition(), SoundEvents.PORTAL_TRIGGER, SoundSource.PLAYERS, volume, pitch);
            }

            if (domain.remainingTicks == DOMAIN_COLLAPSE_TICKS) {
                domain.level.playSound(null, owner.blockPosition(), SoundEvents.RESPAWN_ANCHOR_DEPLETE.value(), SoundSource.PLAYERS, 0.55F, 0.7F);
            }

            if (domain.remainingTicks <= 0) {
                owner.displayClientMessage(Component.translatable("message.jjk.infinite_domain_end"), true);
                spawnCollapseParticles(domain);
                domain.level.playSound(null, owner.blockPosition(), SoundEvents.END_PORTAL_FRAME_FILL, SoundSource.PLAYERS, 1.0F, 0.65F);
                domain.level.playSound(null, owner.blockPosition(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 0.8F, 0.6F);
                restoreDomainShell(domain);
                broadcastDomainState(domain, false);
                domainsToRemove.add(ownerId);
            }
        }

        domainsToRemove.forEach(ACTIVE_DOMAINS::remove);

        restoreReleasedMobs(paralyzedThisTick);
    }

    public static void afterDamage(LivingEntity target, DamageSource source, float baseDamageTaken, float damageTaken, boolean blocked) {
        if (blocked || damageTaken <= 0.0F) {
            return;
        }

        if (!(source.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        if (target == player || BONUS_DAMAGE_GUARD.contains(target.getUUID())) {
            return;
        }

        ActiveDomain domain = ACTIVE_DOMAINS.get(player.getUUID());
        if (domain == null || target.level() != domain.level || !isInsideDomain(target, domain)) {
            return;
        }

        if (!(target.level() instanceof ServerLevel level)) {
            return;
        }

        float bonusDamage = damageTaken * DOMAIN_DAMAGE_BONUS_MULTIPLIER;
        if (bonusDamage <= 0.0F) {
            return;
        }

        BONUS_DAMAGE_GUARD.add(target.getUUID());
        try {
            target.invulnerableTime = 0;
            target.hurtTime = 0;
            target.hurtServer(level, level.damageSources().indirectMagic(player, player), bonusDamage);
        } finally {
            BONUS_DAMAGE_GUARD.remove(target.getUUID());
        }
    }

    public static void clearAll() {
        for (ActiveDomain domain : ACTIVE_DOMAINS.values()) {
            restoreDomainShell(domain);
        }

        ACTIVE_DOMAINS.clear();
        COOLDOWNS.clear();
        BONUS_DAMAGE_GUARD.clear();

        for (ParalyzedMobState state : PARALYZED_MOBS.values()) {
            restoreMobState(state);
        }

        PARALYZED_MOBS.clear();
    }

    public static void clearCooldown(ServerPlayer player) {
        COOLDOWNS.remove(player.getUUID());
        syncCooldownToClient(player, 0, 0);
    }

    public static void syncActiveDomainsToPlayer(ServerPlayer player) {
        for (ActiveDomain domain : ACTIVE_DOMAINS.values()) {
            if (domain.level == player.level()) {
                ServerPlayNetworking.send(player, createSyncPayload(domain, true));
            }
        }
    }

    public static boolean isProtectedDomainBlock(ServerLevel level, BlockPos pos) {
        BlockPos immutablePos = pos.immutable();
        for (ActiveDomain domain : ACTIVE_DOMAINS.values()) {
            if (domain.level == level && domain.originalBlocks.containsKey(immutablePos)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isTechniqueAvailableForPlayer(ServerPlayer player) {
        return JJKRoster.techniquesForCharacter(CharacterSelectionHandler.getSelectedCharacter(player)).stream()
            .anyMatch(definition -> definition.id().equals("infinite_domain"));
    }

    private static void applyDomainParalysis(ActiveDomain domain, Set<UUID> paralyzedThisTick) {
        AABB area = new AABB(domain.center, domain.center).inflate(DOMAIN_RADIUS);

        for (Mob mob : domain.level.getEntitiesOfClass(Mob.class, area, Mob::isAlive)) {
            if (!isInsideDomain(mob, domain)) {
                continue;
            }

            UUID mobId = mob.getUUID();
            paralyzedThisTick.add(mobId);
            PARALYZED_MOBS.computeIfAbsent(mobId, id -> new ParalyzedMobState(mob, mob.isNoAi()));
            snapMobToDomainFloorIfNeeded(domain, mob);

            mob.setNoAi(true);
            mob.setAggressive(false);
            mob.setTarget(null);
            mob.getNavigation().stop();
            mob.stopInPlace();
            mob.setXxa(0.0F);
            mob.setYya(0.0F);
            mob.setZza(0.0F);
            mob.setDeltaMovement(Vec3.ZERO);
            mob.hurtMarked = true;
        }
    }

    private static void restoreReleasedMobs(Set<UUID> paralyzedThisTick) {
        Set<UUID> toRemove = new java.util.HashSet<>();

        for (Map.Entry<UUID, ParalyzedMobState> entry : new java.util.ArrayList<>(PARALYZED_MOBS.entrySet())) {
            ParalyzedMobState state = entry.getValue();

            if (paralyzedThisTick.contains(entry.getKey())) {
                if (state.mob == null || !state.mob.isAlive()) {
                    toRemove.add(entry.getKey());
                }
                continue;
            }

            restoreMobState(state);
            toRemove.add(entry.getKey());
        }

        toRemove.forEach(PARALYZED_MOBS::remove);
    }

    private static void restoreMobState(ParalyzedMobState state) {
        if (state.mob == null || !state.mob.isAlive()) {
            return;
        }

        state.mob.setNoAi(state.wasNoAi);
        state.mob.setDeltaMovement(Vec3.ZERO);
    }

    private static void snapMobToDomainFloorIfNeeded(ActiveDomain domain, Mob mob) {
        double floorTargetY = domain.floorY + 1.0D;
        if (Math.abs(mob.getY() - floorTargetY) <= 0.1D) {
            return;
        }

        Vec3 safeFloorPosition = findSafeFloorPosition(domain, mob, mob.getX(), mob.getZ());
        if (safeFloorPosition == null) {
            safeFloorPosition = findSafeDomainPosition(domain, mob);
        }

        if (safeFloorPosition != null) {
            teleportInsideDomain(mob, domain, safeFloorPosition);
        }
    }

    private static boolean isInsideDomain(LivingEntity entity, ActiveDomain domain) {
        Vec3 entityCenter = getEntityCenter(entity);
        if (entityCenter.y < domain.floorY || entityCenter.y > domain.ceilingY + 1.0D) {
            return false;
        }

        double dx = entityCenter.x - domain.center.x;
        double dz = entityCenter.z - domain.center.z;
        return (dx * dx) + (dz * dz) <= DOMAIN_RADIUS_SQR;
    }

    private static boolean isWithinDomainHorizontal(double x, double z, ActiveDomain domain) {
        double dx = x - domain.center.x;
        double dz = z - domain.center.z;
        return (dx * dx) + (dz * dz) <= DOMAIN_RADIUS_SQR;
    }

    private static float getInsideFactor(LivingEntity entity, ActiveDomain domain) {
        Vec3 entityCenter = getEntityCenter(entity);
        if (entityCenter.y < domain.floorY || entityCenter.y > domain.ceilingY + 1.0D) {
            return 0.0F;
        }

        double dx = entityCenter.x - domain.center.x;
        double dz = entityCenter.z - domain.center.z;
        double distance = Math.sqrt((dx * dx) + (dz * dz));
        if (distance >= DOMAIN_RADIUS) {
            return 0.0F;
        }

        return Mth.clamp((float) (1.0D - (distance / DOMAIN_RADIUS)), 0.0F, 1.0F);
    }

    private static Vec3 getEntityCenter(LivingEntity entity) {
        return entity.position().add(0.0, entity.getBbHeight() * 0.5, 0.0);
    }

    private static void initializeDomainShell(ActiveDomain domain) {
        buildFloorAndCeiling(domain);
        advanceInteriorVoid(domain, DOMAIN_INITIAL_CLEAR_LAYERS);
        relocateEntitiesIntoDomain(domain);
    }

    private static void advanceInteriorVoid(ActiveDomain domain, int targetLayers) {
        int fullInteriorHeight = Math.max(0, domain.ceilingY - domain.floorY - 1);
        int boundedTarget = Math.min(fullInteriorHeight, Math.max(targetLayers, 0));
        while (domain.clearedInteriorLayers < boundedTarget) {
            domain.clearedInteriorLayers++;
            clearInteriorLayer(domain, domain.floorY + domain.clearedInteriorLayers);
        }
    }

    private static void clearInteriorLayer(ActiveDomain domain, int y) {
        int clearRadius = DOMAIN_RADIUS_BLOCKS - 1;
        int clearRadiusSq = clearRadius * clearRadius;

        for (int x = -clearRadius; x <= clearRadius; x++) {
            for (int z = -clearRadius; z <= clearRadius; z++) {
                if ((x * x) + (z * z) >= clearRadiusSq) {
                    continue;
                }

                clearDomainBlock(domain, new BlockPos(domain.centerBlockX + x, y, domain.centerBlockZ + z));
            }
        }
    }

    private static void buildFloorAndCeiling(ActiveDomain domain) {
        int radiusSq = DOMAIN_RADIUS_BLOCKS * DOMAIN_RADIUS_BLOCKS;

        for (int x = -DOMAIN_RADIUS_BLOCKS; x <= DOMAIN_RADIUS_BLOCKS; x++) {
            for (int z = -DOMAIN_RADIUS_BLOCKS; z <= DOMAIN_RADIUS_BLOCKS; z++) {
                if ((x * x) + (z * z) > radiusSq) {
                    continue;
                }

                placeDomainBlock(domain, new BlockPos(domain.centerBlockX + x, domain.floorY, domain.centerBlockZ + z));
                placeDomainBlock(domain, new BlockPos(domain.centerBlockX + x, domain.ceilingY, domain.centerBlockZ + z));
            }
        }
    }

    private static void advanceDomainWall(ActiveDomain domain) {
        int fullWallHeight = Math.max(0, domain.ceilingY - domain.floorY - 1);
        if (fullWallHeight <= 0) {
            return;
        }

        int targetWallLayers = Math.min(
            fullWallHeight,
            (int) Math.ceil((Math.min(domain.ageTicks, DOMAIN_BUILDUP_TICKS) / (double) DOMAIN_BUILDUP_TICKS) * fullWallHeight)
        );

        while (domain.builtWallLayers < targetWallLayers) {
            domain.builtWallLayers++;
            int layerY = domain.floorY + domain.builtWallLayers;
            buildWallLayer(domain, layerY);
            playWallLayerSound(domain, layerY);
        }

        advanceInteriorVoid(domain, Math.max(targetWallLayers, DOMAIN_INITIAL_CLEAR_LAYERS));
    }

    private static void buildWallLayer(ActiveDomain domain, int y) {
        int outerSq = DOMAIN_RADIUS_BLOCKS * DOMAIN_RADIUS_BLOCKS;
        int innerSq = (DOMAIN_RADIUS_BLOCKS - 1) * (DOMAIN_RADIUS_BLOCKS - 1);
        int placedBlocks = 0;

        for (int x = -DOMAIN_RADIUS_BLOCKS; x <= DOMAIN_RADIUS_BLOCKS; x++) {
            for (int z = -DOMAIN_RADIUS_BLOCKS; z <= DOMAIN_RADIUS_BLOCKS; z++) {
                int distSq = (x * x) + (z * z);
                if (distSq > outerSq || distSq < innerSq) {
                    continue;
                }

                if (placeDomainBlock(domain, new BlockPos(domain.centerBlockX + x, y, domain.centerBlockZ + z))) {
                    placedBlocks++;
                }
            }
        }

        if (placedBlocks > 0) {
            spawnWallLayerParticles(domain, y);
        }
    }

    private static boolean placeDomainBlock(ActiveDomain domain, BlockPos pos) {
        BlockPos immutablePos = pos.immutable();
        if (domain.originalBlocks.containsKey(immutablePos)) {
            return false;
        }

        BlockState currentState = domain.level.getBlockState(immutablePos);
        if (domain.level.getBlockEntity(immutablePos) != null) {
            return false;
        }

        float hardness = currentState.getDestroySpeed(domain.level, immutablePos);
        if (hardness < 0.0F) {
            return false;
        }

        domain.originalBlocks.put(immutablePos, currentState);
        domain.level.setBlock(immutablePos, DOMAIN_SHELL_BLOCK, 3);
        return true;
    }

    private static boolean clearDomainBlock(ActiveDomain domain, BlockPos pos) {
        BlockPos immutablePos = pos.immutable();
        if (domain.originalBlocks.containsKey(immutablePos)) {
            return false;
        }

        BlockState currentState = domain.level.getBlockState(immutablePos);
        if (currentState.isAir()) {
            return false;
        }

        if (domain.level.getBlockEntity(immutablePos) != null) {
            return false;
        }

        float hardness = currentState.getDestroySpeed(domain.level, immutablePos);
        if (hardness < 0.0F) {
            return false;
        }

        domain.originalBlocks.put(immutablePos, currentState);
        domain.level.setBlock(immutablePos, Blocks.AIR.defaultBlockState(), 3);
        return true;
    }

    private static void spawnWallLayerParticles(ActiveDomain domain, int y) {
        int particleCount = 56;
        double particleRadius = DOMAIN_RADIUS_BLOCKS - 0.2D;

        for (int i = 0; i < particleCount; i++) {
            double angle = (Math.PI * 2.0D * i) / particleCount;
            double x = domain.center.x + Math.cos(angle) * particleRadius;
            double z = domain.center.z + Math.sin(angle) * particleRadius;
            double px = x + (domain.level.random.nextDouble() - 0.5D) * 0.35D;
            double pz = z + (domain.level.random.nextDouble() - 0.5D) * 0.35D;

            domain.level.sendParticles(ParticleTypes.END_ROD, px, y + 0.55D, pz, 1, 0.12D, 0.06D, 0.12D, 0.0D);
            domain.level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, px, y + 0.18D, pz, 1, 0.08D, 0.04D, 0.08D, 0.0D);
        }
    }

    private static void playWallLayerSound(ActiveDomain domain, int y) {
        float pitch = 0.5F + domain.level.random.nextFloat() * 0.3F;
        domain.level.playSound(null, domain.center.x, y + 0.5D, domain.center.z, SoundEvents.STONE_PLACE, SoundSource.PLAYERS, 0.4F, pitch);
    }

    private static void restoreDomainShell(ActiveDomain domain) {
        for (Map.Entry<BlockPos, BlockState> entry : domain.originalBlocks.entrySet()) {
            domain.level.setBlock(entry.getKey(), entry.getValue(), 3);
        }
        domain.originalBlocks.clear();
        relocateEntitiesAfterRestore(domain);
    }

    private static void relocateEntitiesIntoDomain(ActiveDomain domain) {
        AABB area = new AABB(
            domain.center.x - DOMAIN_RADIUS,
            domain.floorY - DOMAIN_RELOCATION_DEPTH,
            domain.center.z - DOMAIN_RADIUS,
            domain.center.x + DOMAIN_RADIUS,
            domain.ceilingY + 2.0D,
            domain.center.z + DOMAIN_RADIUS
        );

        for (LivingEntity entity : domain.level.getEntitiesOfClass(LivingEntity.class, area, LivingEntity::isAlive)) {
            if (entity instanceof Player player && player.isSpectator()) {
                continue;
            }

            boolean forceFloorSnap = entity instanceof Mob && isWithinDomainHorizontal(entity.getX(), entity.getZ(), domain);
            if (!forceFloorSnap && !shouldRelocateIntoDomain(entity, domain)) {
                continue;
            }

            Vec3 safePosition = forceFloorSnap
                ? findSafeFloorPosition(domain, entity, entity.getX(), entity.getZ())
                : findSafeDomainPosition(domain, entity);
            if (safePosition == null && forceFloorSnap) {
                safePosition = findSafeDomainPosition(domain, entity);
            }
            if (safePosition == null) {
                continue;
            }

            teleportInsideDomain(entity, domain, safePosition);
        }
    }

    private static boolean shouldRelocateIntoDomain(LivingEntity entity, ActiveDomain domain) {
        double dx = entity.getX() - domain.center.x;
        double dz = entity.getZ() - domain.center.z;
        if ((dx * dx) + (dz * dz) > DOMAIN_RADIUS_SQR) {
            return false;
        }

        AABB box = entity.getBoundingBox();
        if (box.maxY < domain.floorY - DOMAIN_RELOCATION_DEPTH || box.minY > domain.ceilingY) {
            return false;
        }

        double safeMinY = domain.floorY + 1.0D;
        double safeMaxY = domain.ceilingY - Math.max(1.25D, entity.getBbHeight());
        return entity.isInWall() || entity.getY() < safeMinY || entity.getY() > safeMaxY || !domain.level.noCollision(entity, box);
    }

    private static Vec3 findSafeDomainPosition(ActiveDomain domain, LivingEntity entity) {
        double preferredX = Mth.clamp(entity.getX(), domain.center.x - (DOMAIN_RADIUS - 2.5D), domain.center.x + (DOMAIN_RADIUS - 2.5D));
        double preferredZ = Mth.clamp(entity.getZ(), domain.center.z - (DOMAIN_RADIUS - 2.5D), domain.center.z + (DOMAIN_RADIUS - 2.5D));

        Vec3 directCandidate = tryFindSafeColumn(domain, entity, preferredX, preferredZ, false);
        if (directCandidate != null) {
            return directCandidate;
        }

        int angleSteps = 20;
        for (double radius = 0.0D; radius <= DOMAIN_RADIUS - 2.0D; radius += 2.0D) {
            for (int i = 0; i < angleSteps; i++) {
                double angle = (Math.PI * 2.0D * i) / angleSteps;
                double x = domain.center.x + Math.cos(angle) * radius;
                double z = domain.center.z + Math.sin(angle) * radius;
                Vec3 candidate = tryFindSafeColumn(domain, entity, x, z, false);
                if (candidate != null) {
                    return candidate;
                }
            }
        }

        return null;
    }

    private static Vec3 findSafeFloorPosition(ActiveDomain domain, LivingEntity entity, double preferredX, double preferredZ) {
        Vec3 directCandidate = tryFindSafeColumn(domain, entity, preferredX, preferredZ, true);
        if (directCandidate != null) {
            return directCandidate;
        }

        int angleSteps = 16;
        for (double radius = 0.0D; radius <= 4.0D; radius += 1.5D) {
            for (int i = 0; i < angleSteps; i++) {
                double angle = (Math.PI * 2.0D * i) / angleSteps;
                double x = preferredX + Math.cos(angle) * radius;
                double z = preferredZ + Math.sin(angle) * radius;
                Vec3 candidate = tryFindSafeColumn(domain, entity, x, z, true);
                if (candidate != null) {
                    return candidate;
                }
            }
        }

        return null;
    }

    private static Vec3 tryFindSafeColumn(ActiveDomain domain, LivingEntity entity, double x, double z, boolean floorOnly) {
        double maxEntityBaseY = domain.ceilingY - Math.max(1.25D, entity.getBbHeight());
        double startY = domain.floorY + 1.0D;
        double endY = floorOnly ? startY : maxEntityBaseY;
        for (double y = startY; y <= endY; y += 1.0D) {
            boolean safe = floorOnly
                ? isSafeGroundedSpot(domain, entity, x, y, z)
                : isSafeDomainSpot(domain, entity, x, y, z);
            if (safe) {
                return new Vec3(x, y, z);
            }
        }

        return null;
    }

    private static void relocateEntitiesAfterRestore(ActiveDomain domain) {
        AABB area = new AABB(
            domain.center.x - DOMAIN_RADIUS,
            domain.floorY - 2.0D,
            domain.center.z - DOMAIN_RADIUS,
            domain.center.x + DOMAIN_RADIUS,
            domain.ceilingY + 10.0D,
            domain.center.z + DOMAIN_RADIUS
        );

        for (LivingEntity entity : domain.level.getEntitiesOfClass(LivingEntity.class, area, LivingEntity::isAlive)) {
            if (entity instanceof Player player && player.isSpectator()) {
                continue;
            }

            if (domain.level.noCollision(entity, entity.getBoundingBox()) && !entity.isInWall() && !needsGroundRecovery(domain, entity)) {
                continue;
            }

            Vec3 safePosition = findSafePostRestorePosition(domain, entity);
            if (safePosition != null) {
                teleportInsideDomain(entity, domain, safePosition);
            }
        }
    }

    private static Vec3 findSafePostRestorePosition(ActiveDomain domain, LivingEntity entity) {
        Vec3 directCandidate = tryFindGroundedColumn(domain, entity, entity.getX(), entity.getZ(), true);
        if (directCandidate != null) {
            return directCandidate;
        }

        int angleSteps = 20;
        for (double radius = 0.0D; radius <= 6.0D; radius += 1.5D) {
            for (int i = 0; i < angleSteps; i++) {
                double angle = (Math.PI * 2.0D * i) / angleSteps;
                double x = entity.getX() + Math.cos(angle) * radius;
                double z = entity.getZ() + Math.sin(angle) * radius;
                Vec3 candidate = tryFindGroundedColumn(domain, entity, x, z, true);
                if (candidate != null) {
                    return candidate;
                }
            }
        }

        return findSafeFloorPosition(domain, entity, entity.getX(), entity.getZ());
    }

    private static Vec3 tryFindGroundedColumn(ActiveDomain domain, LivingEntity entity, double x, double z, boolean highestFirst) {
        double dx = x - domain.center.x;
        double dz = z - domain.center.z;
        double maxRadius = DOMAIN_RADIUS - 1.5D;
        if ((dx * dx) + (dz * dz) > maxRadius * maxRadius) {
            return null;
        }

        double minY = domain.floorY + 1.0D;
        double maxY = domain.ceilingY + 8.0D;

        if (highestFirst) {
            for (double y = maxY; y >= minY; y -= 1.0D) {
                if (isSafeGroundedSpot(domain, entity, x, y, z)) {
                    return new Vec3(x, y, z);
                }
            }
        } else {
            for (double y = minY; y <= maxY; y += 1.0D) {
                if (isSafeGroundedSpot(domain, entity, x, y, z)) {
                    return new Vec3(x, y, z);
                }
            }
        }

        return null;
    }

    private static boolean isSafeDomainSpot(ActiveDomain domain, LivingEntity entity, double x, double y, double z) {
        double dx = x - domain.center.x;
        double dz = z - domain.center.z;
        double maxRadius = DOMAIN_RADIUS - 1.5D;
        if ((dx * dx) + (dz * dz) > maxRadius * maxRadius) {
            return false;
        }

        AABB movedBox = entity.getBoundingBox().move(x - entity.getX(), y - entity.getY(), z - entity.getZ());
        return domain.level.noCollision(entity, movedBox);
    }

    private static boolean isSafeGroundedSpot(ActiveDomain domain, LivingEntity entity, double x, double y, double z) {
        if (!isSafeDomainSpot(domain, entity, x, y, z)) {
            return false;
        }

        BlockPos supportPos = BlockPos.containing(x, y - 0.1D, z).below();
        BlockState supportState = domain.level.getBlockState(supportPos);
        if (supportState.isAir()) {
            return false;
        }

        return supportState.blocksMotion() || supportState.isFaceSturdy(domain.level, supportPos, net.minecraft.core.Direction.UP);
    }

    private static boolean needsGroundRecovery(ActiveDomain domain, LivingEntity entity) {
        if (!isWithinDomainHorizontal(entity.getX(), entity.getZ(), domain)) {
            return false;
        }

        BlockPos supportPos = BlockPos.containing(entity.getX(), entity.getY() - 0.1D, entity.getZ()).below();
        BlockState supportState = domain.level.getBlockState(supportPos);
        if (supportState.isAir()) {
            return true;
        }

        return !supportState.blocksMotion() && !supportState.isFaceSturdy(domain.level, supportPos, net.minecraft.core.Direction.UP);
    }

    private static void teleportInsideDomain(LivingEntity entity, ActiveDomain domain, Vec3 safePosition) {
        entity.setDeltaMovement(Vec3.ZERO);
        entity.fallDistance = -8.0F;

        if (entity instanceof ServerPlayer player) {
            player.teleportTo(domain.level, safePosition.x, safePosition.y, safePosition.z, Set.of(), player.getYRot(), player.getXRot(), false);
        } else {
            entity.teleportTo(safePosition.x, safePosition.y, safePosition.z);
        }

        entity.invulnerableTime = Math.max(entity.invulnerableTime, 10);
        entity.hurtMarked = true;
    }

    private static void enforceDomainBoundary(ActiveDomain domain) {
        AABB barrierArea = new AABB(
            domain.center.x - DOMAIN_RADIUS - 2.0D,
            domain.floorY - 1.0D,
            domain.center.z - DOMAIN_RADIUS - 2.0D,
            domain.center.x + DOMAIN_RADIUS + 2.0D,
            domain.ceilingY + 2.0D,
            domain.center.z + DOMAIN_RADIUS + 2.0D
        );

        for (LivingEntity entity : domain.level.getEntitiesOfClass(LivingEntity.class, barrierArea, LivingEntity::isAlive)) {
            if (entity instanceof ServerPlayer player && player.isSpectator()) {
                continue;
            }

            AABB entityBox = entity.getBoundingBox();
            if (entityBox.maxY < domain.floorY || entityBox.minY > domain.ceilingY + 1.0D) {
                continue;
            }

            double dx = entity.getX() - domain.center.x;
            double dz = entity.getZ() - domain.center.z;
            double horizontalDistanceSqr = (dx * dx) + (dz * dz);
            double horizontalDistance = Math.sqrt(Math.max(horizontalDistanceSqr, 1.0E-6D));

            if (horizontalDistance < DOMAIN_RADIUS - 1.2D || horizontalDistance > DOMAIN_RADIUS + 1.8D) {
                continue;
            }

            Vec3 direction = horizontalDistance < 1.0E-4D
                ? new Vec3(1.0D, 0.0D, 0.0D)
                : new Vec3(dx / horizontalDistance, 0.0D, dz / horizontalDistance);
            boolean inside = horizontalDistance <= DOMAIN_RADIUS;
            Vec3 correction = direction.scale(inside ? -0.45D : 0.45D);

            entity.move(MoverType.SELF, correction);
            entity.setDeltaMovement(entity.getDeltaMovement().scale(0.15D).add(correction.x * 0.7D, 0.03D, correction.z * 0.7D));
            entity.hurtMarked = true;

            if (entity instanceof Mob mob) {
                mob.getNavigation().stop();
                mob.setTarget(null);
            }
        }
    }

    private static void tickCooldowns(MinecraftServer server) {
        Set<UUID> expired = new java.util.HashSet<>();
        for (UUID playerId : new java.util.ArrayList<>(COOLDOWNS.keySet())) {
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

    private static void broadcastDomainState(ActiveDomain domain, boolean active) {
        InfiniteDomainSyncPayload payload = createSyncPayload(domain, active);
        for (ServerPlayer player : domain.level.players()) {
            ServerPlayNetworking.send(player, payload);
        }
    }

    private static InfiniteDomainSyncPayload createSyncPayload(ActiveDomain domain, boolean active) {
        return new InfiniteDomainSyncPayload(
            domain.ownerEntityId,
            domain.center.x,
            domain.center.y,
            domain.center.z,
            (float) DOMAIN_RADIUS,
            active ? domain.remainingTicks : 0,
            active
        );
    }

    private static void spawnActivationParticles(ActiveDomain domain) {
        for (int i = 0; i < 28; i++) {
            double angle = (Math.PI * 2.0D * i) / 28.0D;
            double x = domain.center.x + Math.cos(angle) * DOMAIN_RADIUS;
            double z = domain.center.z + Math.sin(angle) * DOMAIN_RADIUS;
            domain.level.sendParticles(ParticleTypes.SQUID_INK, x, domain.center.y + 0.4D, z, 4, 0.25D, 1.3D, 0.25D, 0.02D);
            domain.level.sendParticles(ParticleTypes.ASH, x, domain.center.y + 0.2D, z, 3, 0.2D, 1.0D, 0.2D, 0.01D);
        }

        domain.level.sendParticles(ParticleTypes.SQUID_INK, domain.center.x, domain.center.y + 1.5D, domain.center.z, 36, 1.8D, 2.5D, 1.8D, 0.02D);
        domain.level.sendParticles(ParticleTypes.ASH, domain.center.x, domain.center.y + 3.5D, domain.center.z, 48, DOMAIN_RADIUS * 0.45D, 2.0D, DOMAIN_RADIUS * 0.45D, 0.005D);
        domain.level.sendParticles(ParticleTypes.SMOKE, domain.center.x, domain.center.y + 0.8D, domain.center.z, 24, 1.0D, 1.0D, 1.0D, 0.01D);
        domain.level.sendParticles(ParticleTypes.REVERSE_PORTAL, domain.center.x, domain.center.y + 2.0D, domain.center.z, 20, 0.8D, 1.2D, 0.8D, 0.02D);
    }

    private static void spawnAmbientParticles(ActiveDomain domain) {
        float buildProgress = getBuildProgress(domain);
        float collapseProgress = getCollapseProgress(domain);
        float intensity = Mth.clamp((buildProgress * 0.85F) + (collapseProgress * 0.35F), 0.15F, 1.15F);
        // Volumen interior: END_ROD dispersas (energía suspendida) cada 2 ticks
        if (domain.ageTicks % 2 == 0) {
            int count = Mth.ceil(8.0F + (14.0F * intensity));
            double height = Math.max(0.0D, domain.ceilingY - domain.floorY);
            for (int i = 0; i < count; i++) {
                double r = DOMAIN_RADIUS * Math.sqrt(domain.level.random.nextDouble());
                double a = domain.level.random.nextDouble() * Math.PI * 2.0D;
                double x = domain.center.x + Math.cos(a) * r;
                double z = domain.center.z + Math.sin(a) * r;
                double y = domain.floorY + 0.5D + domain.level.random.nextDouble() * Math.max(0.0D, height - 1.0D);
                domain.level.sendParticles(ParticleTypes.END_ROD, x, y, z, 1, 0.04D, 0.06D, 0.04D, 0.0D);
            }
        }

        // Columnas ascendentes desde el suelo hacia el centro cada 3 ticks
        if (domain.ageTicks % 3 == 0) {
            int columns = 3 + Mth.ceil(4.0F * intensity);
            for (int i = 0; i < columns; i++) {
                double r = DOMAIN_RADIUS * (0.3D + 0.6D * domain.level.random.nextDouble());
                double a = (Math.PI * 2.0D * i) / columns + domain.ageTicks * 0.07D;
                double x = domain.center.x + Math.cos(a) * r;
                double z = domain.center.z + Math.sin(a) * r;
                double y = domain.floorY + 0.6D;
                boolean alt = ((domain.ageTicks / 3) + i) % 2 == 0;
                if (alt) {
                    domain.level.sendParticles(ParticleTypes.REVERSE_PORTAL, x, y, z, 3, 0.06D, 0.8D, 0.06D, 0.0D);
                } else {
                    domain.level.sendParticles(ParticleTypes.SONIC_BOOM, x, y, z, 1, 0.02D, 0.6D, 0.02D, 0.0D);
                }
            }
        }

        // Anillo perimetral y bruma interior suave cada 4 ticks (efecto existente)
        if (domain.ageTicks % 4 == 0) {
            int perimeterCount = 10 + Mth.ceil(10.0F * intensity);
            for (int i = 0; i < perimeterCount; i++) {
                double angle = (Math.PI * 2.0D * i) / perimeterCount + domain.remainingTicks * 0.035D;
                double x = domain.center.x + Math.cos(angle) * DOMAIN_RADIUS;
                double z = domain.center.z + Math.sin(angle) * DOMAIN_RADIUS;
                domain.level.sendParticles(ParticleTypes.SQUID_INK, x, domain.center.y + 0.25D, z, 2, 0.18D, 0.9D, 0.18D, 0.01D);
            }

            domain.level.sendParticles(ParticleTypes.ASH, domain.center.x, domain.center.y + 2.5D, domain.center.z, Mth.ceil(6.0F + (10.0F * intensity)), DOMAIN_RADIUS * 0.55D, 1.6D, DOMAIN_RADIUS * 0.55D, 0.002D);
            domain.level.sendParticles(ParticleTypes.SMOKE, domain.center.x, domain.center.y + 1.1D, domain.center.z, Mth.ceil(3.0F + (5.0F * intensity)), 0.8D, 0.4D, 0.8D, 0.003D);
        }

        if (buildProgress > 0.35F && domain.ageTicks % 2 == 1) {
            double spiralAngle = domain.ageTicks * 0.16D;
            double vortexRadius = 1.8D + (1.6D * (1.0D - collapseProgress));
            for (int i = 0; i < 4; i++) {
                double angle = spiralAngle + (Math.PI * 0.5D * i);
                double x = domain.center.x + Math.cos(angle) * vortexRadius;
                double z = domain.center.z + Math.sin(angle) * vortexRadius;
                double y = domain.center.y + 1.2D + (0.35D * i);
                domain.level.sendParticles(ParticleTypes.SQUID_INK, x, y, z, 1, 0.05D, 0.08D, 0.05D, 0.0D);
                domain.level.sendParticles(ParticleTypes.SMOKE, x, y, z, 1, 0.03D, 0.04D, 0.03D, 0.0D);
            }
        }
    }

    private static void spawnCollapseParticles(ActiveDomain domain) {
        int ringCount = 24;
        for (int i = 0; i < ringCount; i++) {
            double angle = (Math.PI * 2.0D * i) / ringCount;
            double x = domain.center.x + Math.cos(angle) * DOMAIN_RADIUS;
            double z = domain.center.z + Math.sin(angle) * DOMAIN_RADIUS;
            domain.level.sendParticles(ParticleTypes.SQUID_INK, x, domain.center.y + 0.8D, z, 6, 0.18D, 1.1D, 0.18D, 0.02D);
            domain.level.sendParticles(ParticleTypes.SMOKE, x, domain.center.y + 0.5D, z, 4, 0.14D, 0.6D, 0.14D, 0.01D);
        }

        domain.level.sendParticles(ParticleTypes.REVERSE_PORTAL, domain.center.x, domain.center.y + 2.5D, domain.center.z, 40, 2.2D, 2.6D, 2.2D, 0.06D);
        domain.level.sendParticles(ParticleTypes.ASH, domain.center.x, domain.center.y + 2.0D, domain.center.z, 54, DOMAIN_RADIUS * 0.4D, 2.0D, DOMAIN_RADIUS * 0.4D, 0.01D);
        domain.level.sendParticles(ParticleTypes.SQUID_INK, domain.center.x, domain.center.y + 1.8D, domain.center.z, 30, 1.4D, 1.8D, 1.4D, 0.015D);
    }

    private static float getBuildProgress(ActiveDomain domain) {
        return Mth.clamp(domain.ageTicks / (float) DOMAIN_BUILDUP_TICKS, 0.0F, 1.0F);
    }

    private static float getCollapseProgress(ActiveDomain domain) {
        if (domain.remainingTicks >= DOMAIN_COLLAPSE_TICKS) {
            return 0.0F;
        }

        return Mth.clamp((DOMAIN_COLLAPSE_TICKS - Math.max(domain.remainingTicks, 0)) / (float) DOMAIN_COLLAPSE_TICKS, 0.0F, 1.0F);
    }

    private static String formatSeconds(int ticks) {
        return String.format(Locale.ROOT, "%.1f", ticks / 20.0D);
    }

    private static final class ActiveDomain {
        private final UUID ownerId;
        private final int ownerEntityId;
        private final ServerLevel level;
        private final Vec3 center;
        private final int centerBlockX;
        private final int centerBlockZ;
        private final int floorY;
        private final int ceilingY;
        private final Map<BlockPos, BlockState> originalBlocks;
        private int remainingTicks;
        private int ageTicks;
        private int builtWallLayers;
        private int clearedInteriorLayers;

        private ActiveDomain(UUID ownerId, int ownerEntityId, ServerLevel level, Vec3 center, BlockPos activationPos, int remainingTicks) {
            this.ownerId = ownerId;
            this.ownerEntityId = ownerEntityId;
            this.level = level;
            this.center = center;
            this.centerBlockX = activationPos.getX();
            this.centerBlockZ = activationPos.getZ();
            this.floorY = activationPos.getY() + DOMAIN_FLOOR_OFFSET;
            this.ceilingY = activationPos.getY() + DOMAIN_CEILING_OFFSET;
            this.originalBlocks = new ConcurrentHashMap<>();
            this.remainingTicks = remainingTicks;
            this.ageTicks = 0;
            this.builtWallLayers = 0;
            this.clearedInteriorLayers = 0;
        }
    }

    private static final class ParalyzedMobState {
        private final Mob mob;
        private final boolean wasNoAi;

        private ParalyzedMobState(Mob mob, boolean wasNoAi) {
            this.mob = mob;
            this.wasNoAi = wasNoAi;
        }
    }
}
