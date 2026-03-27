package com.pop.jjk;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class PurpleProjectileEntity extends Projectile {

    private static final EntityDataAccessor<Float> FUSION_PROGRESS =
        SynchedEntityData.defineId(PurpleProjectileEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> LAUNCHED =
        SynchedEntityData.defineId(PurpleProjectileEntity.class, EntityDataSerializers.BOOLEAN);

    private static final int FUSION_TICKS = 24;
    private static final int MAX_FLIGHT_TICKS = 28;
    private static final double PURPLE_DISTANCE = 34.0;
    private static final double PURPLE_SPEED = 1.55;
    private static final double PURPLE_PATH_RADIUS = 3.4;
    private static final double PURPLE_IMPACT_RADIUS = 5.6;
    private static final float PURPLE_DAMAGE_AMOUNT = 9999.0F;
    private static final DustParticleOptions PURPLE_DUST = new DustParticleOptions(0x9B4DFF, 1.7F);
    private static final DustParticleOptions PURPLE_DUST_BRIGHT = new DustParticleOptions(0xE6CCFF, 1.05F);

    private final Set<UUID> vaporizedEntities = new HashSet<>();
    private double distanceTravelled;
    private int launchedTicks;

    public PurpleProjectileEntity(EntityType<? extends PurpleProjectileEntity> entityType, Level level) {
        super(entityType, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    public PurpleProjectileEntity(ServerLevel level, ServerPlayer owner) {
        this(JJKMod.PURPLE_PROJECTILE, level);
        this.setOwner(owner);

        Vec3 direction = owner.getLookAngle().normalize();
        Vec3 anchor = computeFusionAnchor(owner, direction);

        this.setPos(anchor);
        this.setYRot(owner.getYRot());
        this.setXRot(owner.getXRot());
        this.setOldPosAndRot();
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(FUSION_PROGRESS, 0.0F);
        builder.define(LAUNCHED, false);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
    }

    @Override
    public void tick() {
        super.tick();
        this.noPhysics = true;
        this.setNoGravity(true);

        if (!(this.level() instanceof ServerLevel serverLevel)) {
            return;
        }

        ServerPlayer owner = this.getOwner() instanceof ServerPlayer serverPlayer ? serverPlayer : null;

        if (!this.isLaunched()) {
            tickFusion(serverLevel, owner);
        } else {
            tickFlight(serverLevel, owner);
        }
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
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 8192.0;
    }

    public float getFusionProgress() {
        return this.entityData.get(FUSION_PROGRESS);
    }

    public boolean isLaunched() {
        return this.entityData.get(LAUNCHED);
    }

    private void tickFusion(ServerLevel level, ServerPlayer owner) {
        if (owner == null || !owner.isAlive() || owner.level() != level) {
            this.discard();
            return;
        }

        Vec3 direction = owner.getLookAngle().normalize();
        Vec3 anchor = computeFusionAnchor(owner, direction);
        float progress = Mth.clamp(this.tickCount / (float) FUSION_TICKS, 0.0F, 1.0F);

        this.setPos(anchor);
        this.setDeltaMovement(Vec3.ZERO);
        this.setYRot(owner.getYRot());
        this.setXRot(owner.getXRot());
        this.entityData.set(FUSION_PROGRESS, progress);

        if (this.tickCount == 1) {
            level.playSound(null, anchor.x, anchor.y, anchor.z, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.55F, 1.7F);
        }

        if (this.tickCount % 2 == 0) {
            spawnFusionParticles(level, anchor, progress);
        }

        if (this.tickCount >= FUSION_TICKS) {
            launch(level, owner, anchor, direction);
        }
    }

    private void launch(ServerLevel level, ServerPlayer owner, Vec3 anchor, Vec3 direction) {
        this.entityData.set(FUSION_PROGRESS, 1.0F);
        this.entityData.set(LAUNCHED, true);
        this.setPos(anchor.add(direction.scale(1.15)));
        this.setDeltaMovement(direction.scale(PURPLE_SPEED));
        this.setYRot(owner.getYRot());
        this.setXRot(owner.getXRot());
        level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 0.85F, 0.82F);
        sendShakeToNearby(level, this.position(), 3.5F, 12);
    }

    private void tickFlight(ServerLevel level, ServerPlayer owner) {
        Vec3 currentVelocity = this.getDeltaMovement();
        if (currentVelocity.lengthSqr() < 0.0001) {
            finishFlight(level, owner);
            return;
        }

        Vec3 previousPosition = this.position();
        double remainingDistance = PURPLE_DISTANCE - this.distanceTravelled;

        if (remainingDistance <= 0.0 || this.launchedTicks >= MAX_FLIGHT_TICKS) {
            finishFlight(level, owner);
            return;
        }

        Vec3 movement = currentVelocity.length() > remainingDistance
            ? currentVelocity.normalize().scale(remainingDistance)
            : currentVelocity;
        Vec3 nextPosition = previousPosition.add(movement);

        this.setPos(nextPosition);
        this.distanceTravelled += movement.length();
        this.launchedTicks++;

        destroyBlocksAlongPath(level, previousPosition, nextPosition);
        vaporizeEntitiesAlongPath(level, previousPosition, nextPosition, owner);
        spawnFlightParticles(level, nextPosition);
        if (this.launchedTicks % 3 == 0) {
            sendShakeToNearby(level, nextPosition, 1.2F, 4);
        }

        if (this.distanceTravelled >= PURPLE_DISTANCE || this.launchedTicks >= MAX_FLIGHT_TICKS) {
            finishFlight(level, owner);
        }
    }

    private void finishFlight(ServerLevel level, ServerPlayer owner) {
        Vec3 center = this.position();
        destroyBlocksInBurst(level, center);
        vaporizeEntitiesInBurst(level, center, owner);
        spawnBurstParticles(level, center);
        level.playSound(null, center.x, center.y, center.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 1.2F, 0.65F);
        level.playSound(null, center.x, center.y, center.z, JJKSounds.PURPLE_IMPACT, SoundSource.PLAYERS, 1.5F, 1.0F);
        sendShakeToNearby(level, center, 6.0F, 18);
        this.discard();
    }

    private void vaporizeEntitiesAlongPath(ServerLevel level, Vec3 from, Vec3 to, ServerPlayer owner) {
        AABB effectArea = new AABB(from, to).inflate(PURPLE_PATH_RADIUS + 0.65);

        for (LivingEntity livingEntity : level.getEntitiesOfClass(LivingEntity.class, effectArea, entity -> entity.isAlive() && entity != this.getOwner())) {
            if (distancePointToSegment(livingEntity.position().add(0.0, livingEntity.getBbHeight() * 0.5, 0.0), from, to) > PURPLE_PATH_RADIUS) {
                continue;
            }

            if (!this.vaporizedEntities.add(livingEntity.getUUID())) {
                continue;
            }

            hurtEntity(level, livingEntity, owner);
        }
    }

    private void vaporizeEntitiesInBurst(ServerLevel level, Vec3 center, ServerPlayer owner) {
        AABB burstArea = new AABB(center, center).inflate(PURPLE_IMPACT_RADIUS);

        for (LivingEntity livingEntity : level.getEntitiesOfClass(LivingEntity.class, burstArea, entity -> entity.isAlive() && entity != this.getOwner())) {
            if (livingEntity.position().distanceTo(center) > PURPLE_IMPACT_RADIUS + 0.8) {
                continue;
            }

            hurtEntity(level, livingEntity, owner);
        }
    }

    private void hurtEntity(ServerLevel level, LivingEntity target, ServerPlayer owner) {
        if (owner != null) {
            target.hurtServer(level, level.damageSources().playerAttack(owner), PURPLE_DAMAGE_AMOUNT);
        } else {
            target.hurtServer(level, level.damageSources().magic(), PURPLE_DAMAGE_AMOUNT);
        }
    }

    private static void destroyBlocksAlongPath(ServerLevel level, Vec3 from, Vec3 to) {
        AABB sweep = new AABB(from, to).inflate(PURPLE_PATH_RADIUS + 0.8);
        List<BlockPos> candidates = new ArrayList<>();

        for (BlockPos pos : BlockPos.betweenClosed(
            BlockPos.containing(sweep.minX, sweep.minY, sweep.minZ),
            BlockPos.containing(sweep.maxX, sweep.maxY, sweep.maxZ)
        )) {
            BlockPos immutablePos = pos.immutable();
            if (!isBreakableForPurple(level, immutablePos)) {
                continue;
            }

            if (distancePointToSegment(immutablePos.getCenter(), from, to) > PURPLE_PATH_RADIUS) {
                continue;
            }

            candidates.add(immutablePos);
        }

        candidates.sort(Comparator.comparingDouble(pos -> pos.getCenter().distanceToSqr(to)));

        for (BlockPos pos : candidates) {
            destroyPurpleBlock(level, pos);
        }
    }

    private static void destroyBlocksInBurst(ServerLevel level, Vec3 center) {
        BlockPos centerPos = BlockPos.containing(center);
        int radius = Mth.ceil(PURPLE_IMPACT_RADIUS);

        for (BlockPos pos : BlockPos.betweenClosed(
            centerPos.offset(-radius, -radius, -radius),
            centerPos.offset(radius, radius, radius)
        )) {
            BlockPos immutablePos = pos.immutable();
            if (!isBreakableForPurple(level, immutablePos)) {
                continue;
            }

            if (immutablePos.getCenter().distanceTo(center) > PURPLE_IMPACT_RADIUS) {
                continue;
            }

            destroyPurpleBlock(level, immutablePos);
        }
    }

    private static boolean isBreakableForPurple(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);

        if (state.isAir()) {
            return false;
        }

        if (!state.getFluidState().isEmpty()) {
            return false;
        }

        return state.getDestroySpeed(level, pos) >= 0.0F;
    }

    private static void destroyPurpleBlock(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) {
            return;
        }

        Vec3 center = pos.getCenter();
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), center.x, center.y, center.z, 16, 0.18, 0.18, 0.18, 0.05);
        level.sendParticles(PURPLE_DUST, center.x, center.y, center.z, 8, 0.06, 0.06, 0.06, 0.0);
        level.destroyBlock(pos, false);
    }

    private static void spawnFusionParticles(ServerLevel level, Vec3 center, float progress) {
        double spread = 0.18 + (progress * 0.18);
        level.sendParticles(PURPLE_DUST_BRIGHT, center.x, center.y, center.z, 4, spread, spread * 0.4, spread, 0.0);
        level.sendParticles(PURPLE_DUST, center.x, center.y, center.z, 2, spread * 0.7, spread * 0.25, spread * 0.7, 0.0);
    }

    private static void spawnFlightParticles(ServerLevel level, Vec3 center) {
        level.sendParticles(PURPLE_DUST, center.x, center.y, center.z, 12, 0.35, 0.35, 0.35, 0.0);
        level.sendParticles(PURPLE_DUST_BRIGHT, center.x, center.y, center.z, 6, 0.15, 0.15, 0.15, 0.0);
        level.sendParticles(ParticleTypes.WITCH, center.x, center.y, center.z, 3, 0.2, 0.2, 0.2, 0.02);
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, center.x, center.y, center.z, 5, 0.3, 0.3, 0.3, 0.05);
    }

    private static void spawnBurstParticles(ServerLevel level, Vec3 center) {
        level.sendParticles(PURPLE_DUST, center.x, center.y, center.z, 80, 2.0, 2.0, 2.0, 0.0);
        level.sendParticles(PURPLE_DUST_BRIGHT, center.x, center.y, center.z, 45, 1.4, 1.4, 1.4, 0.0);
        level.sendParticles(ParticleTypes.EXPLOSION, center.x, center.y, center.z, 6, 1.5, 1.5, 1.5, 0.0);
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, center.x, center.y, center.z, 20, 1.8, 1.8, 1.8, 0.08);
        level.sendParticles(ParticleTypes.WITCH, center.x, center.y, center.z, 12, 1.2, 1.2, 1.2, 0.04);
        level.sendParticles(ParticleTypes.END_ROD, center.x, center.y, center.z, 15, 1.0, 1.0, 1.0, 0.1);
    }

    private static Vec3 computeFusionAnchor(ServerPlayer owner, Vec3 direction) {
        return owner.getEyePosition().add(direction.scale(1.12)).add(0.0, -0.35, 0.0);
    }

    private static void sendShakeToNearby(ServerLevel level, Vec3 center, float intensity, int durationTicks) {
        double range = 48.0;
        for (ServerPlayer nearby : level.getPlayers(p -> p.position().distanceTo(center) < range)) {
            float dist = (float) nearby.position().distanceTo(center);
            float falloff = Math.max(0.0F, 1.0F - (dist / (float) range));
            if (falloff > 0.05F) {
                ServerPlayNetworking.send(nearby, new ScreenShakePayload(intensity * falloff, durationTicks));
            }
        }
    }

    private static double distancePointToSegment(Vec3 point, Vec3 segmentStart, Vec3 segmentEnd) {
        Vec3 segment = segmentEnd.subtract(segmentStart);
        double segmentLengthSquared = segment.lengthSqr();

        if (segmentLengthSquared < 0.0001) {
            return point.distanceTo(segmentStart);
        }

        double projection = point.subtract(segmentStart).dot(segment) / segmentLengthSquared;
        double clampedProjection = Math.max(0.0, Math.min(1.0, projection));
        Vec3 closest = segmentStart.add(segment.scale(clampedProjection));
        return point.distanceTo(closest);
    }
}
