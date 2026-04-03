package com.pop.jjk;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class FugaProjectileEntity extends Projectile {

    private static final EntityDataAccessor<Float> CHARGE_POWER =
        SynchedEntityData.defineId(FugaProjectileEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> LAUNCHED =
        SynchedEntityData.defineId(FugaProjectileEntity.class, EntityDataSerializers.BOOLEAN);

    private static final int MAX_FLIGHT_TICKS = (int) Math.ceil(FugaTechnique.MAX_DISTANCE / FugaTechnique.SPEED) + 6;
    private static final List<PersistentFireZone> ACTIVE_FIRE_ZONES = new ArrayList<>();

    private double distanceTravelled = 0.0D;
    private int ticksSinceLaunch = 0;
    private boolean launched = false;
    private final Set<UUID> hitEntities = new HashSet<>();
    private final Deque<BlockPos> ignitionQueue = new ArrayDeque<>();
    private float chargePower = 0.0F;

    public FugaProjectileEntity(EntityType<? extends FugaProjectileEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    public FugaProjectileEntity(ServerLevel level, ServerPlayer owner) {
        this(JJKMod.FUGA_PROJECTILE, level);
        this.setOwner(owner);
        Vec3 dir = owner.getLookAngle().normalize();
        Vec3 anchor = computePalmAnchor(owner, dir);
        this.setPos(anchor);
        this.setDeltaMovement(Vec3.ZERO);
        this.setYRot(owner.getYRot());
        this.setXRot(owner.getXRot());
        this.setOldPosAndRot();
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(CHARGE_POWER, 0.0F);
        builder.define(LAUNCHED, false);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean canBeHitByProjectile() {
        return false;
    }

    @Override
    public void tick() {
        super.tick();
        this.noPhysics = true;
        this.setNoGravity(true);

        if (!(this.level() instanceof ServerLevel level)) {
            return;
        }

        ServerPlayer owner = this.getOwner() instanceof ServerPlayer sp ? sp : null;
        if (owner == null || !owner.isAlive() || owner.level() != level) {
            this.discard();
            return;
        }

        if (this.tickCount > MAX_FLIGHT_TICKS + 40) {
            this.discard();
            return;
        }

        if (!this.launched) {
            this.setDeltaMovement(Vec3.ZERO);
            return;
        }

        tickFlight(level, owner);
    }

    public void syncChargeVisual(ServerPlayer owner, float power) {
        Vec3 dir = owner.getLookAngle().normalize();
        Vec3 anchor = computePalmAnchor(owner, dir);
        this.setPos(anchor);
        this.setDeltaMovement(Vec3.ZERO);
        this.setYRot(owner.getYRot());
        this.setXRot(owner.getXRot());
        this.setChargePower(power);
        this.launched = false;
        this.entityData.set(LAUNCHED, false);
    }

    public void launchFrom(ServerPlayer owner) {
        if (!(this.level() instanceof ServerLevel level)) {
            return;
        }

        Vec3 dir = owner.getLookAngle().normalize();
        Vec3 anchor = computePalmAnchor(owner, dir);
        this.launched = true;
        this.entityData.set(LAUNCHED, true);
        this.ticksSinceLaunch = 0;
        this.distanceTravelled = 0.0D;
        Vec3 start = anchor.add(dir.scale(1.0D));
        this.setPos(start);
        this.setDeltaMovement(dir.scale(effSpeed()));
        this.setYRot(owner.getYRot());
        this.setXRot(owner.getXRot());
        this.setOldPosAndRot();
        level.playSound(null, start.x, start.y, start.z, SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 1.0F, 0.95F);
        sendShakeToNearby(level, start, 1.8F + (this.chargePower * 1.6F), 8 + Math.round(this.chargePower * 4.0F));
    }

    private void tickFlight(ServerLevel level, ServerPlayer owner) {
        Vec3 velocity = this.getDeltaMovement();
        if (velocity.lengthSqr() < 1.0E-6D) {
            finish(level, owner);
            return;
        }

        Vec3 from = this.position();
        double remaining = effMaxDistance() - this.distanceTravelled;
        if (remaining <= 0.0D || this.ticksSinceLaunch >= MAX_FLIGHT_TICKS) {
            finish(level, owner);
            return;
        }

        Vec3 movement = velocity.length() > remaining ? velocity.normalize().scale(remaining) : velocity;
        Vec3 to = from.add(movement);

        burnEntitiesAlongPath(level, from, to, owner, velocity.normalize());
        igniteBlocksAlongPath(level, from, to);

        if ((this.ticksSinceLaunch & 3) == 0) {
            sendBeamFX(level, owner, from, to);
        }

        this.setPos(to);
        this.distanceTravelled += movement.length();
        this.ticksSinceLaunch++;

        int igniteBudget = 12 + (int) (this.chargePower * 10.0F);
        while (igniteBudget-- > 0 && !this.ignitionQueue.isEmpty()) {
            BlockPos pos = this.ignitionQueue.pollFirst();
            if (pos != null && canPlaceFireAt(level, pos)) {
                level.setBlock(pos, Blocks.FIRE.defaultBlockState(), 11);
            }
        }

        if (this.distanceTravelled >= effMaxDistance() || this.ticksSinceLaunch >= MAX_FLIGHT_TICKS) {
            finish(level, owner);
        }
    }

    private void finish(ServerLevel level, ServerPlayer owner) {
        Vec3 center = this.position();
        explode(level, center, owner);
        this.discard();
    }

    private void burnEntitiesAlongPath(ServerLevel level, Vec3 from, Vec3 to, ServerPlayer owner, Vec3 dir) {
        double radius = effPathRadius();
        AABB area = new AABB(from, to).inflate(radius + 0.6D);
        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, area, e -> e.isAlive() && e != this.getOwner())) {
            Vec3 center = entity.position().add(0.0D, entity.getBbHeight() * 0.5D, 0.0D);
            if (distancePointToSegment(center, from, to) > radius) {
                continue;
            }
            if (!this.hitEntities.add(entity.getUUID())) {
                continue;
            }

            float damage = effPathDamage();
            if (owner != null) {
                entity.hurtServer(level, level.damageSources().playerAttack(owner), damage);
            } else {
                entity.hurtServer(level, level.damageSources().magic(), damage);
            }
            entity.push(dir.x * 0.35D, 0.08D, dir.z * 0.35D);
            entity.hurtMarked = true;
            entity.setRemainingFireTicks(120);
            level.sendParticles(JJKParticles.FIRE_TRAIL, center.x, center.y, center.z, 2, 0.08D, 0.08D, 0.08D, 0.0D);
        }
    }

    private void igniteBlocksAlongPath(ServerLevel level, Vec3 from, Vec3 to) {
        if (this.ignitionQueue.size() > 512) {
            return;
        }

        double igniteRadius = effIgniteRadius();
        List<BlockPos> candidates = new ArrayList<>();
        Vec3 delta = to.subtract(from);
        double length = delta.length();
        if (length < 1.0E-4D) {
            return;
        }

        Vec3 direction = delta.scale(1.0D / length);
        double sideX = -direction.z;
        double sideZ = direction.x;
        double sideLength = Math.sqrt((sideX * sideX) + (sideZ * sideZ));
        if (sideLength < 1.0E-6D) {
            sideX = 1.0D;
            sideZ = 0.0D;
            sideLength = 1.0D;
        }
        sideX /= sideLength;
        sideZ /= sideLength;

        int segments = Math.max(1, (int) Math.ceil(length / 0.9D));
        for (int i = 0; i <= segments; i++) {
            double t = (double) i / (double) segments;
            Vec3 point = from.add(direction.scale(t * length));
            double[] radii = new double[] {0.0D, 0.9D, -0.9D, 1.8D, -1.8D};

            for (double r : radii) {
                double offsetX = (sideX * r) + ((level.random.nextDouble() - 0.5D) * 0.5D);
                double offsetZ = (sideZ * r) + ((level.random.nextDouble() - 0.5D) * 0.5D);
                BlockPos pos = BlockPos.containing(point.x + offsetX, point.y, point.z + offsetZ).immutable();
                if (distancePointToSegment(pos.getCenter(), from, to) > igniteRadius + 0.5D) {
                    continue;
                }
                candidates.add(pos);
                if (level.random.nextFloat() < 0.35F) {
                    candidates.add(pos.above());
                }
            }
        }

        int queueBudget = Math.min(64, 12 + (segments * 6));
        for (BlockPos pos : candidates) {
            if (queueBudget-- <= 0) {
                break;
            }

            BlockPos place = canPlaceFireAt(level, pos) ? pos : canPlaceFireAt(level, pos.above()) ? pos.above() : null;
            if (place == null) {
                continue;
            }
            float keepProbability = 0.50F + (0.20F * this.chargePower);
            if (level.random.nextFloat() > keepProbability) {
                continue;
            }
            if (this.ignitionQueue.size() < 640) {
                this.ignitionQueue.addLast(place);
            }
        }
    }

    private static boolean canPlaceFireAt(ServerLevel level, BlockPos pos) {
        if (!level.getBlockState(pos).isAir()) {
            return false;
        }
        BlockState fire = Blocks.FIRE.defaultBlockState();
        return fire.canSurvive(level, pos);
    }

    private void explode(ServerLevel level, Vec3 center, ServerPlayer owner) {
        level.playSound(null, center.x, center.y, center.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 1.6F + (this.chargePower * 0.5F), 0.85F);
        level.playSound(null, center.x, center.y, center.z, SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 1.3F, 0.72F);
        level.playSound(null, center.x, center.y, center.z, SoundEvents.DRAGON_FIREBALL_EXPLODE, SoundSource.PLAYERS, 0.9F + (this.chargePower * 0.3F), 0.78F);
        spawnExplosionParticles(level, center);
        sendExplosionFX(level, owner, center, (float) effImpactRadius());
        sendShakeToNearby(level, center, 4.0F + (this.chargePower * 4.5F), 12 + Math.round(this.chargePower * 10.0F));

        double impactRadius = effImpactRadius();
        float knockback = effKnockback();
        float impactDamage = effImpactDamage();
        AABB burst = new AABB(center, center).inflate(impactRadius + 0.5D);

        boolean ownerHasInfinity = owner != null && InfinityTechniqueHandler.isActive(owner.getUUID());
        Vec3 ownerPush = Vec3.ZERO;
        if (owner != null && !ownerHasInfinity) {
            ownerPush = owner.position().subtract(center).normalize().scale(knockback * 0.6D);
        }

        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, burst, e -> e.isAlive() && e != this.getOwner())) {
            double dist = entity.position().distanceTo(center);
            if (dist > impactRadius + 0.8D) {
                continue;
            }

            Vec3 push = entity.position().subtract(center).normalize().scale(knockback);
            entity.push(push.x, 0.25D + (push.y * 0.1D), push.z);
            entity.hurtMarked = true;
            if (owner != null) {
                entity.hurtServer(level, level.damageSources().playerAttack(owner), impactDamage);
            } else {
                entity.hurtServer(level, level.damageSources().magic(), impactDamage);
            }
            entity.setRemainingFireTicks(160);
        }

        if (owner != null && !ownerHasInfinity && ownerPush.lengthSqr() > 0.0D) {
            owner.push(ownerPush.x, 0.15D, ownerPush.z);
            owner.hurtMarked = true;
        }

        int samples = (int) (120 + (40 * this.chargePower));
        int igniteBudget = 32 + (int) (24 * this.chargePower);
        BlockPos base = BlockPos.containing(center);
        List<BlockPos> persistentFirePositions = new ArrayList<>();

        for (int i = 0; i < samples; i++) {
            double theta = level.random.nextDouble() * Math.PI * 2.0D;
            double phi = Math.acos((level.random.nextDouble() * 2.0D) - 1.0D);
            double radius = level.random.nextDouble() * (impactRadius + 0.6D);
            int ox = (int) Math.round(radius * Math.sin(phi) * Math.cos(theta));
            int oy = (int) Math.round(radius * Math.cos(phi));
            int oz = (int) Math.round(radius * Math.sin(phi) * Math.sin(theta));
            BlockPos pos = base.offset(ox, oy, oz);
            if (pos.getCenter().distanceTo(center) > impactRadius + 0.6D) {
                continue;
            }
            if (((pos.getX() ^ pos.getZ()) & 1) != 0) {
                continue;
            }

            BlockPos place = canPlaceFireAt(level, pos) ? pos : canPlaceFireAt(level, pos.above()) ? pos.above() : null;
            if (place != null && level.random.nextFloat() < 0.58F) {
                level.setBlock(place, Blocks.FIRE.defaultBlockState(), 11);
                persistentFirePositions.add(place.immutable());
                if (--igniteBudget <= 0) {
                    break;
                }
            }
        }

        if (this.chargePower >= 0.8F && owner != null) {
            destroyWeakBlocks(level, center, owner);
        }

        if (!persistentFirePositions.isEmpty()) {
            createPersistentFireZone(level, persistentFirePositions, owner);
        }
    }

    private void destroyWeakBlocks(ServerLevel level, Vec3 center, ServerPlayer owner) {
        BlockPos base = BlockPos.containing(center);
        double craterRadius = 5.0D + (2.0D * this.chargePower);
        int radiusInt = Mth.ceil(craterRadius);
        int destroyedCount = 0;

        for (int ox = -radiusInt; ox <= radiusInt; ox++) {
            for (int oz = -radiusInt; oz <= radiusInt; oz++) {
                for (int oy = -radiusInt; oy <= Math.max(1, radiusInt / 2); oy++) {
                    double horizontal = Math.sqrt((ox * ox) + (oz * oz));
                    if (horizontal > craterRadius) {
                        continue;
                    }

                    double normalizedY = oy < 0 ? (Math.abs(oy) / craterRadius) * 0.75D : (oy / craterRadius) * 1.4D;
                    double normalizedDistance = Math.pow(horizontal / craterRadius, 2.0D) + (normalizedY * normalizedY);
                    if (normalizedDistance > 1.0D) {
                        continue;
                    }

                    BlockPos pos = base.offset(ox, oy, oz);
                    if (InfiniteDomainTechniqueHandler.isProtectedDomainBlock(level, pos)) {
                        continue;
                    }

                    BlockState state = level.getBlockState(pos);
                    if (!isWeakBlock(state)) {
                        continue;
                    }

                    if (level.destroyBlock(pos, false, owner)) {
                        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D, 4, 0.2D, 0.2D, 0.2D, 0.1D);
                        destroyedCount++;
                    }
                }
            }
        }

        if (destroyedCount > 0) {
            level.playSound(null, center.x, center.y, center.z, SoundEvents.GENERIC_EXPLODE, SoundSource.BLOCKS, 0.8F, 0.6F);
        }
    }

    private boolean isWeakBlock(BlockState state) {
        return state.getBlock() == Blocks.STONE
            || state.getBlock() == Blocks.COBBLESTONE
            || state.getBlock() == Blocks.DIRT
            || state.getBlock() == Blocks.GRASS_BLOCK
            || state.getBlock() == Blocks.SAND
            || state.getBlock() == Blocks.GRAVEL
            || state.getBlock() == Blocks.OAK_LOG
            || state.getBlock() == Blocks.BIRCH_LOG
            || state.getBlock() == Blocks.SPRUCE_LOG
            || state.getBlock() == Blocks.JUNGLE_LOG
            || state.getBlock() == Blocks.ACACIA_LOG
            || state.getBlock() == Blocks.DARK_OAK_LOG
            || state.getBlock() == Blocks.OAK_PLANKS
            || state.getBlock() == Blocks.COBBLESTONE_WALL
            || state.getBlock() == Blocks.BRICKS
            || state.getBlock() == Blocks.NETHERRACK
            || state.getBlock() == Blocks.END_STONE;
    }

    private void createPersistentFireZone(ServerLevel level, List<BlockPos> firePositions, ServerPlayer owner) {
        ACTIVE_FIRE_ZONES.add(
            new PersistentFireZone(
                level.dimension(),
                new ArrayList<>(firePositions),
                owner != null ? owner.getUUID() : null,
                this.chargePower
            )
        );
    }

    private static void applyZoneDamage(ServerLevel level, List<BlockPos> firePositions, ServerPlayer owner, float zoneChargePower) {
        Set<BlockPos> activePositions = new HashSet<>();
        for (BlockPos pos : firePositions) {
            if (level.getBlockState(pos).getBlock() == Blocks.FIRE) {
                activePositions.add(pos);
            }
        }

        if (activePositions.isEmpty()) {
            return;
        }

        AABB zoneBounds = null;
        for (BlockPos pos : activePositions) {
            AABB box = new AABB(pos).inflate(2.0D);
            zoneBounds = zoneBounds == null ? box : zoneBounds.minmax(box);
        }
        if (zoneBounds == null) {
            return;
        }

        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, zoneBounds, e -> e.isAlive() && e != owner)) {
            boolean inZone = false;
            for (BlockPos pos : activePositions) {
                if (entity.position().distanceToSqr(Vec3.atCenterOf(pos)) < 4.0D) {
                    inZone = true;
                    break;
                }
            }
            if (!inZone) {
                continue;
            }

            float zoneDamage = 1.5F + (zoneChargePower * 1.0F);
            if (owner != null) {
                entity.hurtServer(level, level.damageSources().playerAttack(owner), zoneDamage);
            } else {
                entity.hurtServer(level, level.damageSources().magic(), zoneDamage);
            }

            entity.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40, 1, false, false, true));
            Vec3 entityPos = entity.position();
            level.sendParticles(JJKParticles.FIRE_TRAIL, entityPos.x, entityPos.y + 0.5D, entityPos.z, 2, 0.3D, 0.2D, 0.3D, 0.02D);
        }
    }

    private static void sendBeamFX(ServerLevel level, ServerPlayer owner, Vec3 from, Vec3 to) {
        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(owner, new FugaBeamFXPayload(from.x, from.y, from.z, to.x, to.y, to.z));
    }

    private static void spawnExplosionParticles(ServerLevel level, Vec3 center) {
        level.sendParticles(JJKParticles.FIRE_EXPLOSION, center.x, center.y, center.z, 88, 2.8D, 2.2D, 2.8D, 0.0D);
        level.sendParticles(JJKParticles.FIRE_TRAIL, center.x, center.y, center.z, 32, 1.6D, 1.4D, 1.6D, 0.0D);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, center.x, center.y + 0.3D, center.z, 42, 2.4D, 1.6D, 2.4D, 0.04D);
        level.sendParticles(ParticleTypes.FLAME, center.x, center.y + 0.2D, center.z, 64, 2.1D, 1.2D, 2.1D, 0.05D);
    }

    private static void sendExplosionFX(ServerLevel level, ServerPlayer owner, Vec3 center, float radius) {
        if (owner != null) {
            net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(owner, new FugaExplosionFXPayload(center.x, center.y, center.z, radius));
        }
    }

    public static void tickPersistentFireZones(MinecraftServer server) {
        if (ACTIVE_FIRE_ZONES.isEmpty()) {
            return;
        }

        List<PersistentFireZone> expired = new ArrayList<>();
        for (PersistentFireZone zone : ACTIVE_FIRE_ZONES) {
            ServerLevel level = server.getLevel(zone.dimension);
            if (level == null) {
                expired.add(zone);
                continue;
            }

            zone.remainingTicks--;
            if (zone.remainingTicks <= 0) {
                expired.add(zone);
                continue;
            }

            spawnZoneParticles(level, zone.firePositions, zone.chargePower);

            zone.damageCooldown--;
            if (zone.damageCooldown > 0) {
                continue;
            }

            zone.damageCooldown = PersistentFireZone.DAMAGE_INTERVAL_TICKS;
            ServerPlayer owner = zone.ownerId != null ? server.getPlayerList().getPlayer(zone.ownerId) : null;
            applyZoneDamage(level, zone.firePositions, owner, zone.chargePower);
        }

        ACTIVE_FIRE_ZONES.removeAll(expired);
    }

    public static void clearPersistentFireZones() {
        ACTIVE_FIRE_ZONES.clear();
    }

    private static void spawnZoneParticles(ServerLevel level, List<BlockPos> firePositions, float zoneChargePower) {
        int budget = Math.min(18, Math.max(6, firePositions.size() / 3));
        for (int i = 0; i < budget; i++) {
            BlockPos pos = firePositions.get(level.random.nextInt(firePositions.size()));
            double x = pos.getX() + 0.5D + ((level.random.nextDouble() - 0.5D) * 0.65D);
            double y = pos.getY() + 0.1D + (level.random.nextDouble() * 0.6D);
            double z = pos.getZ() + 0.5D + ((level.random.nextDouble() - 0.5D) * 0.65D);
            level.sendParticles(ParticleTypes.LARGE_SMOKE, x, y, z, 1, 0.08D, 0.12D, 0.08D, 0.01D);
            level.sendParticles(ParticleTypes.FLAME, x, y, z, 1, 0.04D, 0.05D + (zoneChargePower * 0.03D), 0.04D, 0.005D);
        }
    }

    private static Vec3 computePalmAnchor(ServerPlayer owner, Vec3 dir) {
        return owner.getEyePosition().add(dir.scale(0.85D)).add(0.0D, -0.28D, 0.0D);
    }

    public void setChargePower(float power) {
        this.chargePower = Mth.clamp(power, 0.0F, 1.0F);
        this.entityData.set(CHARGE_POWER, this.chargePower);
    }

    public float getChargePower() {
        return this.entityData.get(CHARGE_POWER);
    }

    public boolean isLaunched() {
        return this.entityData.get(LAUNCHED);
    }

    private double effMaxDistance() {
        return FugaTechnique.MAX_DISTANCE + ((FugaTechnique.OVR_MAX_DISTANCE - FugaTechnique.MAX_DISTANCE) * this.chargePower);
    }

    private double effSpeed() {
        return FugaTechnique.SPEED + ((FugaTechnique.OVR_SPEED - FugaTechnique.SPEED) * this.chargePower);
    }

    private double effPathRadius() {
        return FugaTechnique.PATH_RADIUS + ((FugaTechnique.OVR_PATH_RADIUS - FugaTechnique.PATH_RADIUS) * this.chargePower);
    }

    private double effIgniteRadius() {
        return FugaTechnique.IGNITE_RADIUS + ((FugaTechnique.OVR_IGNITE_RADIUS - FugaTechnique.IGNITE_RADIUS) * this.chargePower);
    }

    private float effPathDamage() {
        return FugaTechnique.PATH_DAMAGE + ((FugaTechnique.OVR_PATH_DAMAGE - FugaTechnique.PATH_DAMAGE) * this.chargePower);
    }

    private float effImpactDamage() {
        return FugaTechnique.IMPACT_DAMAGE + ((FugaTechnique.OVR_IMPACT_DAMAGE - FugaTechnique.IMPACT_DAMAGE) * this.chargePower);
    }

    private double effImpactRadius() {
        return FugaTechnique.IMPACT_RADIUS + ((FugaTechnique.OVR_IMPACT_RADIUS - FugaTechnique.IMPACT_RADIUS) * this.chargePower);
    }

    private float effKnockback() {
        return (float) (FugaTechnique.KNOCKBACK + ((FugaTechnique.OVR_KNOCKBACK - FugaTechnique.KNOCKBACK) * this.chargePower));
    }

    private static double distancePointToSegment(Vec3 point, Vec3 a, Vec3 b) {
        Vec3 ab = b.subtract(a);
        double len2 = ab.lengthSqr();
        if (len2 < 1.0E-6D) {
            return point.distanceTo(a);
        }
        double t = Math.max(0.0D, Math.min(1.0D, point.subtract(a).dot(ab) / len2));
        return point.distanceTo(a.add(ab.scale(t)));
    }

    private static void sendShakeToNearby(ServerLevel level, Vec3 center, float intensity, int durationTicks) {
        double range = 48.0D;
        for (ServerPlayer nearby : level.getPlayers(p -> p.position().distanceTo(center) < range)) {
            float dist = (float) nearby.position().distanceTo(center);
            float falloff = Math.max(0.0F, 1.0F - (dist / (float) range));
            if (falloff > 0.05F) {
                net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(nearby, new ScreenShakePayload(intensity * falloff, durationTicks));
            }
        }
    }

    private static final class PersistentFireZone {
        private static final int DURATION_TICKS = 200;
        private static final int DAMAGE_INTERVAL_TICKS = 10;

        private final ResourceKey<Level> dimension;
        private final List<BlockPos> firePositions;
        private final UUID ownerId;
        private final float chargePower;
        private int remainingTicks;
        private int damageCooldown;

        private PersistentFireZone(ResourceKey<Level> dimension, List<BlockPos> firePositions, UUID ownerId, float chargePower) {
            this.dimension = dimension;
            this.firePositions = firePositions;
            this.ownerId = ownerId;
            this.chargePower = chargePower;
            this.remainingTicks = DURATION_TICKS;
            this.damageCooldown = DAMAGE_INTERVAL_TICKS;
        }
    }
}
