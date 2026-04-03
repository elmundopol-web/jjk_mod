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
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class RedProjectileEntity extends Projectile {

    private static final int RED_DAMAGE_INTERVAL = 6;
    private static final int RED_MAX_LIFETIME_TICKS = 24;
    private static final int RED_BLOCKS_PER_TICK = 5;
    private static final double RED_DISTANCE = 14.0;
    private static final double RED_SPEED = 0.82;
    private static final double RED_RADIUS = 3.25;
    private static final double RED_BREAK_RADIUS = 1.85;
    private static final double RED_PUSH_STRENGTH = 0.28;
    private static final double RED_MAX_PUSH_BONUS = 0.22;
    private static final double RED_BLOCK_PUSH_STRENGTH = 0.7;
    private static final double RED_MAX_HORIZONTAL_SPEED = 2.8;
    private static final double RED_MAX_VERTICAL_SPEED = 1.3;
    private static final float RED_DAMAGE_AMOUNT = 2.0F;
    private static final float RED_IMPACT_DAMAGE = 4.0F;
    private static final DustParticleOptions RED_DUST = new DustParticleOptions(0xFF3B30, 1.45F);
    private static final DustParticleOptions RED_DUST_SMALL = new DustParticleOptions(0xFF8A80, 0.95F);

    private final Set<UUID> hitMobIds = new HashSet<>();
    private double distanceTravelled;

    public RedProjectileEntity(EntityType<? extends RedProjectileEntity> entityType, Level level) {
        super(entityType, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    public RedProjectileEntity(ServerLevel level, ServerPlayer owner) {
        this(JJKMod.RED_PROJECTILE, level);
        this.setOwner(owner);

        Vec3 direction = owner.getLookAngle().normalize();
        Vec3 origin = owner.getEyePosition().add(direction.scale(0.9));

        this.setPos(origin);
        this.setDeltaMovement(direction.scale(RED_SPEED));
        this.setYRot(owner.getYRot());
        this.setXRot(owner.getXRot());
        this.setOldPosAndRot();
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
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

        Vec3 currentVelocity = this.getDeltaMovement();

        if (currentVelocity.lengthSqr() < 0.0001) {
            this.discard();
            return;
        }

        Vec3 previousPosition = this.position();
        double remainingDistance = RED_DISTANCE - this.distanceTravelled;

        if (remainingDistance <= 0.0) {
            this.finishFlight();
            return;
        }

        Vec3 movement = currentVelocity.length() > remainingDistance
            ? currentVelocity.normalize().scale(remainingDistance)
            : currentVelocity;
        Vec3 nextPosition = previousPosition.add(movement);

        this.distanceTravelled += movement.length();
        this.setPos(nextPosition);
        this.move(MoverType.SELF, Vec3.ZERO);
        this.updateRotation();

        if (!(this.level() instanceof ServerLevel serverLevel)) {
            return;
        }

        breakBlocksAlongPath(serverLevel, previousPosition, nextPosition);
        pulseRed(serverLevel, nextPosition, currentVelocity.normalize(), this.tickCount % RED_DAMAGE_INTERVAL == 0);
        spawnRedParticles(serverLevel, nextPosition, false);

        if (this.distanceTravelled >= RED_DISTANCE || this.tickCount >= RED_MAX_LIFETIME_TICKS) {
            this.finishFlight();
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

    private void finishFlight() {
        if (this.level() instanceof ServerLevel serverLevel) {
            explodeAtEnd(serverLevel, this.position());
        }

        this.discard();
    }

    private void pulseRed(ServerLevel level, Vec3 center, Vec3 directionHint, boolean shouldDamage) {
        AABB effectArea = new AABB(
            center.x - RED_RADIUS,
            center.y - RED_RADIUS,
            center.z - RED_RADIUS,
            center.x + RED_RADIUS,
            center.y + RED_RADIUS,
            center.z + RED_RADIUS
        );
        Entity owner = this.getOwner();
        ServerPlayer playerOwner = owner instanceof ServerPlayer serverPlayer ? serverPlayer : null;

        for (Mob mob : level.getEntitiesOfClass(Mob.class, effectArea, Mob::isAlive)) {
            Vec3 mobCenter = mob.position().add(0.0, mob.getBbHeight() * 0.5, 0.0);
            Vec3 pushDirection = mobCenter.subtract(center);
            double distanceSquared = pushDirection.lengthSqr();

            if (distanceSquared < 0.0001) {
                pushDirection = directionHint;
                distanceSquared = 1.0;
            }

            double distance = Math.sqrt(distanceSquared);
            double clampedDistance = Math.min(distance, RED_RADIUS);
            double strength = RED_PUSH_STRENGTH + ((RED_RADIUS - clampedDistance) / RED_RADIUS) * RED_MAX_PUSH_BONUS;
            Vec3 push = pushDirection.normalize().scale(strength);

            mob.push(push.x, 0.08 + (push.y * 0.08), push.z);
            mob.setDeltaMovement(clampVelocity(mob.getDeltaMovement(), 0.6, 0.32));

            if (this.hitMobIds.add(mob.getUUID())) {
                if (playerOwner != null) {
                    mob.hurtServer(level, level.damageSources().playerAttack(playerOwner), RED_IMPACT_DAMAGE);
                } else {
                    mob.hurtServer(level, level.damageSources().magic(), RED_IMPACT_DAMAGE);
                }
            }

            if (shouldDamage) {
                if (playerOwner != null) {
                    mob.hurtServer(level, level.damageSources().playerAttack(playerOwner), RED_DAMAGE_AMOUNT);
                } else {
                    mob.hurtServer(level, level.damageSources().magic(), RED_DAMAGE_AMOUNT);
                }
            }
        }

        for (FallingBlockEntity fallingBlock : level.getEntitiesOfClass(FallingBlockEntity.class, effectArea, FallingBlockEntity::isAlive)) {
            Vec3 pushDirection = fallingBlock.position().subtract(center);

            if (pushDirection.lengthSqr() < 0.0001) {
                pushDirection = directionHint;
            }

            Vec3 push = pushDirection.normalize().scale(RED_BLOCK_PUSH_STRENGTH);
            Vec3 updatedVelocity = fallingBlock.getDeltaMovement().scale(0.82).add(push.x, 0.18 + (push.y * 0.15), push.z);

            fallingBlock.setNoGravity(true);
            fallingBlock.setDeltaMovement(clampVelocity(updatedVelocity, 1.75, 1.0));
        }
    }

    private static void breakBlocksAlongPath(ServerLevel level, Vec3 from, Vec3 to) {
        List<BlockPos> candidates = new ArrayList<>();
        BlockPos fromPos = BlockPos.containing(from);
        BlockPos toPos = BlockPos.containing(to);
        int radius = (int) Math.ceil(RED_BREAK_RADIUS);

        for (BlockPos pos : BlockPos.betweenClosed(
            Math.min(fromPos.getX(), toPos.getX()) - radius,
            Math.min(fromPos.getY(), toPos.getY()) - radius,
            Math.min(fromPos.getZ(), toPos.getZ()) - radius,
            Math.max(fromPos.getX(), toPos.getX()) + radius,
            Math.max(fromPos.getY(), toPos.getY()) + radius,
            Math.max(fromPos.getZ(), toPos.getZ()) + radius
        )) {
            BlockPos immutablePos = pos.immutable();

            if (!isBreakableForRed(level, immutablePos, from, to)) {
                continue;
            }

            candidates.add(immutablePos);
        }

        candidates.sort(Comparator.comparingDouble(pos -> pos.getCenter().distanceToSqr(to)));

        int broken = 0;

        for (BlockPos pos : candidates) {
            BlockState state = level.getBlockState(pos);

            if (state.isAir()) {
                continue;
            }

            breakBlock(level, pos, state);
            broken++;

            if (broken >= RED_BLOCKS_PER_TICK) {
                break;
            }
        }
    }

    private static boolean isBreakableForRed(ServerLevel level, BlockPos pos, Vec3 from, Vec3 to) {
        BlockState state = level.getBlockState(pos);

        if (state.isAir()) {
            return false;
        }

        if (InfiniteDomainTechniqueHandler.isProtectedDomainBlock(level, pos)) {
            return false;
        }

        if (!state.getFluidState().isEmpty()) {
            return false;
        }

        if (level.getBlockEntity(pos) != null) {
            return false;
        }

        float destroySpeed = state.getDestroySpeed(level, pos);

        if (destroySpeed < 0.0F || destroySpeed > 3.0F) {
            return false;
        }

        return distancePointToSegment(pos.getCenter(), from, to) <= RED_BREAK_RADIUS;
    }

    private static void breakBlock(ServerLevel level, BlockPos pos, BlockState state) {
        Vec3 center = pos.getCenter();
        BlockParticleOption blockParticle = new BlockParticleOption(ParticleTypes.BLOCK, state);

        level.sendParticles(blockParticle, center.x, center.y, center.z, 16, 0.18, 0.18, 0.18, 0.03);
        level.sendParticles(RED_DUST_SMALL, center.x, center.y, center.z, 10, 0.08, 0.08, 0.08, 0.0);
        level.playSound(null, pos, state.getSoundType().getBreakSound(), SoundSource.BLOCKS, state.getSoundType().getVolume(), state.getSoundType().getPitch());
        level.destroyBlock(pos, false);
    }

    private static void explodeAtEnd(ServerLevel level, Vec3 center) {
        level.playSound(
            null,
            center.x,
            center.y,
            center.z,
            SoundEvents.GENERIC_EXPLODE,
            SoundSource.PLAYERS,
            1.1F,
            1.05F
        );
        spawnRedParticles(level, center, true);
        sendShakeToNearby(level, center, 2.5F, 8);
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

    private static void sendShakeToNearby(ServerLevel level, Vec3 center, float intensity, int durationTicks) {
        double range = 32.0;
        for (ServerPlayer nearby : level.getPlayers(p -> p.position().distanceTo(center) < range)) {
            float dist = (float) nearby.position().distanceTo(center);
            float falloff = Math.max(0.0F, 1.0F - (dist / (float) range));
            if (falloff > 0.05F) {
                ServerPlayNetworking.send(nearby, new ScreenShakePayload(intensity * falloff, durationTicks));
            }
        }
    }

    private static Vec3 clampVelocity(Vec3 velocity, double maxHorizontalSpeed, double maxVerticalSpeed) {
        double horizontalSpeed = Math.sqrt((velocity.x * velocity.x) + (velocity.z * velocity.z));
        double horizontalScale = horizontalSpeed > maxHorizontalSpeed ? maxHorizontalSpeed / horizontalSpeed : 1.0;
        double verticalSpeed = Math.max(-maxVerticalSpeed, Math.min(maxVerticalSpeed, velocity.y));

        return new Vec3(velocity.x * horizontalScale, verticalSpeed, velocity.z * horizontalScale);
    }

    private static void spawnRedParticles(ServerLevel level, Vec3 center, boolean burst) {
        int coreCount = burst ? 45 : 10;
        int sparkCount = burst ? 24 : 6;
        double spread = burst ? 1.2 : 0.22;

        level.sendParticles(RED_DUST, center.x, center.y, center.z, coreCount, spread, spread, spread, 0.0);
        level.sendParticles(RED_DUST_SMALL, center.x, center.y, center.z, coreCount / 2, spread * 0.55, spread * 0.55, spread * 0.55, 0.0);
        level.sendParticles(ParticleTypes.FLAME, center.x, center.y, center.z, sparkCount, spread * 0.6, spread * 0.6, spread * 0.6, 0.01);
        level.sendParticles(ParticleTypes.LAVA, center.x, center.y, center.z, burst ? 8 : 2, spread * 0.4, spread * 0.4, spread * 0.4, 0.0);
        if (burst) {
            level.sendParticles(ParticleTypes.EXPLOSION, center.x, center.y, center.z, 4, 0.8, 0.8, 0.8, 0.0);
            level.sendParticles(ParticleTypes.SMOKE, center.x, center.y, center.z, 12, 0.9, 0.9, 0.9, 0.03);
        }
    }

}
