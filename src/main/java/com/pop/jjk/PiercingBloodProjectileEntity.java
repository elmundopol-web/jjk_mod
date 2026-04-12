package com.pop.jjk;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Entity.RemovalReason;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class PiercingBloodProjectileEntity extends Projectile {

    static final double INITIAL_SPEED = 5.5D;
    static final double SPEED_DECAY = 0.88D;
    static final double MIN_SPEED = 0.25D;
    static final double MAX_RANGE = 50.0D;
    static final int MAX_LIFETIME = 50;
    static final double INITIAL_TRACKING = 0.92D;
    static final double TRACKING_DECAY = 0.88D;
    static final double HELD_TRACKING = 0.90D;
    static final double HIT_RADIUS = 0.70D;
    static final double BREAK_RADIUS = 0.40D;
    static final int BLOCKS_PER_TICK = 6;
    static final double HELD_SPEED = 3.5D;
    static final float INITIAL_DAMAGE = 12.0F;
    static final float MIN_DAMAGE = 1.0F;
    static final float PIERCE_KNOCKBACK = 0.20F;

    private static final DustParticleOptions BREAK_DUST = new DustParticleOptions(0xFF4040, 0.65F);

    private final Set<UUID> hitEntityIds = new HashSet<>();
    private double distanceTravelled;
    private boolean held;
    private int holdTicks;
    private int graceUntilTick = -1;
    private int releaseTicksRemaining = 0;
    private float chargeDamageMultiplier = 1.0F;
    private int maxHeldTicks = 10;
    private double heldSpeed = 2.5D;
    private double hitRadius = 0.55D;
    private boolean cleanupNotified;
    private UUID ownerUuid;

    public PiercingBloodProjectileEntity(EntityType<? extends PiercingBloodProjectileEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    public PiercingBloodProjectileEntity(ServerLevel level, ServerPlayer owner) {
        this(JJKMod.PIERCING_BLOOD_PROJECTILE, level);
        this.setOwner(owner);

        Vec3 dir = owner.getLookAngle().normalize();
        Vec3 origin = owner.getEyePosition().add(dir.scale(0.55D));

        this.setPos(origin);
        this.setDeltaMovement(dir.scale(INITIAL_SPEED));
        this.setYRot(owner.getYRot());
        this.setXRot(owner.getXRot());
        this.setOldPosAndRot();
        this.held = false;
        this.graceUntilTick = -1;
        this.holdTicks = 0;
        this.cleanupNotified = false;
        this.ownerUuid = owner.getUUID();
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

        Vec3 velocity = this.getDeltaMovement();
        if (velocity.lengthSqr() < 0.0001D) {
            if (this.level() instanceof ServerLevel) {
                notifyDied();
            }
            this.discard();
            return;
        }

        Vec3 currentDir = velocity.normalize();
        if (this.held && this.getOwner() instanceof ServerPlayer ownerPlayer) {
            Vec3 lookDir = ownerPlayer.getLookAngle().normalize();
            currentDir = currentDir.add(lookDir.subtract(currentDir).scale(HELD_TRACKING)).normalize();
        }

        double currentSpeed;
        if (this.releaseTicksRemaining > 0) {
            double lastSpeed = velocity.length();
            currentSpeed = Math.max(0.02D, lastSpeed * 0.70D);
            this.releaseTicksRemaining--;
            if (this.releaseTicksRemaining == 0) {
                if (this.level() instanceof ServerLevel serverLevel) {
                    spawnTrailEnd(serverLevel, this.position());
                    notifyDied();
                }
                this.discard();
                return;
            }
        } else {
            double baseSpeed;
            if (this.tickCount == 1) {
                baseSpeed = INITIAL_SPEED * 2.2D;
            } else if (this.tickCount == 2) {
                baseSpeed = INITIAL_SPEED * 1.4D;
            } else {
                baseSpeed = INITIAL_SPEED * Math.pow(SPEED_DECAY, this.tickCount - 1);
            }

            if (this.held) {
                currentSpeed = this.tickCount >= 3 ? this.heldSpeed : baseSpeed;
            } else {
                currentSpeed = baseSpeed;
                if (currentSpeed <= MIN_SPEED) {
                    if (this.level() instanceof ServerLevel serverLevel) {
                        spawnTrailEnd(serverLevel, this.position());
                        notifyDied();
                    }
                    this.discard();
                    return;
                }
            }
        }

        this.setDeltaMovement(currentDir.scale(currentSpeed));
        if (this.held) {
            this.holdTicks++;
            if (this.holdTicks >= this.maxHeldTicks) {
                this.setHeld(false);
            }
        }

        double remaining = (this.held || this.releaseTicksRemaining > 0)
            ? Double.POSITIVE_INFINITY
            : (MAX_RANGE - this.distanceTravelled);
        if (!(this.held || this.releaseTicksRemaining > 0) && remaining <= 0.0D) {
            if (this.level() instanceof ServerLevel serverLevel) {
                spawnTrailEnd(serverLevel, this.position());
                notifyDied();
            }
            this.discard();
            return;
        }

        double step = this.held ? currentSpeed : Math.min(currentSpeed, remaining);
        Vec3 prev = this.position();
        Vec3 next = prev.add(currentDir.scale(step));

        if (!(this.held || this.releaseTicksRemaining > 0)) {
            this.distanceTravelled += step;
        }
        this.setPos(next);
        this.move(MoverType.SELF, Vec3.ZERO);
        this.updateRotation();

        if (!(this.level() instanceof ServerLevel serverLevel)) {
            return;
        }

        if (this.tickCount >= (this.maxHeldTicks + MAX_LIFETIME + 20)) {
            spawnTrailEnd(serverLevel, next);
            notifyDied();
            this.discard();
            return;
        }

        float intensity;
        if (this.held) {
            intensity = 1.0F;
        } else if (this.releaseTicksRemaining > 0) {
            intensity = Math.max(0.0F, this.releaseTicksRemaining / 10.0F);
        } else {
            intensity = (float) Math.max(0.0D, 1.0D - (double) (this.tickCount - 1) / MAX_LIFETIME);
        }
        float scaledInitialDamage = INITIAL_DAMAGE * this.chargeDamageMultiplier;
        float currentDamage = MIN_DAMAGE + (scaledInitialDamage - MIN_DAMAGE) * intensity;

        if (this.tickCount == 1) {
            spawnLaunchBurst(serverLevel, prev, currentDir);
        }

        this.hitEntityIds.clear();
        hitEntitiesAlongPath(serverLevel, prev, next, currentDir, currentDamage);
        breakBlocksAlongPath(serverLevel, prev, next);

        if (this.getOwner() instanceof ServerPlayer ownerForVisual) {
            spawnFullBeamVisual(serverLevel, ownerForVisual.getEyePosition(), next, intensity);
        }

        if (this.distanceTravelled >= MAX_RANGE && !(this.held || this.releaseTicksRemaining > 0)) {
            spawnTrailEnd(serverLevel, next);
            notifyDied();
            this.discard();
        } else if (!(this.held || this.releaseTicksRemaining > 0) && this.tickCount >= MAX_LIFETIME) {
            spawnTrailEnd(serverLevel, next);
            notifyDied();
            this.discard();
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

    private void hitEntitiesAlongPath(ServerLevel level, Vec3 from, Vec3 to, Vec3 dir, float currentDamage) {
        double segLen = from.distanceTo(to);
        AABB box = new AABB(from, to).inflate(this.hitRadius);

        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, box, candidate -> candidate.isAlive() && candidate != this.getOwner())) {
            if (this.hitEntityIds.contains(entity.getUUID())) {
                continue;
            }

            Vec3 center = entity.position().add(0.0D, entity.getBbHeight() * 0.5D, 0.0D);
            Vec3 toEntity = center.subtract(from);
            double projection = Math.max(0.0D, Math.min(segLen, toEntity.dot(dir)));
            Vec3 closest = from.add(dir.scale(projection));

            if (closest.distanceTo(center) > this.hitRadius + (entity.getBbWidth() * 0.5D)) {
                continue;
            }

            this.hitEntityIds.add(entity.getUUID());

            if (this.getOwner() instanceof ServerPlayer playerOwner) {
                entity.hurtServer(level, level.damageSources().playerAttack(playerOwner), currentDamage);
            } else {
                entity.hurtServer(level, level.damageSources().magic(), currentDamage);
            }

            entity.push(dir.x * PIERCE_KNOCKBACK, 0.0D, dir.z * PIERCE_KNOCKBACK);
            entity.hurtMarked = true;

            spawnBloodExplosion(level, center, dir);
            level.playSound(null, entity.blockPosition(), SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 1.0F, 1.7F);
        }
    }

    private void breakBlocksAlongPath(ServerLevel level, Vec3 from, Vec3 to) {
        List<BlockPos> candidates = new ArrayList<>();
        BlockPos fromPos = BlockPos.containing(from);
        BlockPos toPos = BlockPos.containing(to);
        int radius = (int) Math.ceil(BREAK_RADIUS);

        for (BlockPos pos : BlockPos.betweenClosed(
            Math.min(fromPos.getX(), toPos.getX()) - radius,
            Math.min(fromPos.getY(), toPos.getY()) - radius,
            Math.min(fromPos.getZ(), toPos.getZ()) - radius,
            Math.max(fromPos.getX(), toPos.getX()) + radius,
            Math.max(fromPos.getY(), toPos.getY()) + radius,
            Math.max(fromPos.getZ(), toPos.getZ()) + radius
        )) {
            BlockPos immutable = pos.immutable();
            if (!isBreakable(level, immutable, from, to)) {
                continue;
            }
            candidates.add(immutable);
        }

        candidates.sort(Comparator.comparingDouble(pos -> pos.getCenter().distanceToSqr(to)));

        int broken = 0;
        for (BlockPos pos : candidates) {
            BlockState state = level.getBlockState(pos);
            if (state.isAir()) {
                continue;
            }

            Vec3 center = pos.getCenter();
            level.sendParticles(BREAK_DUST, center.x, center.y, center.z, 5, 0.08D, 0.08D, 0.08D, 0.0D);
            level.playSound(
                null,
                pos,
                state.getSoundType().getBreakSound(),
                SoundSource.BLOCKS,
                state.getSoundType().getVolume() * 0.5F,
                state.getSoundType().getPitch() * 1.2F
            );
            level.destroyBlock(pos, false);
            broken++;
            if (broken >= BLOCKS_PER_TICK) {
                break;
            }
        }
    }

    private static boolean isBreakable(ServerLevel level, BlockPos pos, Vec3 from, Vec3 to) {
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) {
            return false;
        }
        if (!state.getFluidState().isEmpty()) {
            return false;
        }
        if (level.getBlockEntity(pos) != null) {
            return false;
        }
        float speed = state.getDestroySpeed(level, pos);
        if (speed < 0.0F || speed > 2.0F) {
            return false;
        }
        return distancePointToSegment(pos.getCenter(), from, to) <= BREAK_RADIUS;
    }

    private static void spawnFullBeamVisual(ServerLevel level, Vec3 from, Vec3 to, float intensity) {
        double segmentLength = from.distanceTo(to);
        if (segmentLength < 0.05D) {
            return;
        }

        Vec3 delta = to.subtract(from);
        int steps = Math.max(1, (int) (segmentLength / 0.10D));
        for (int i = 0; i <= steps; i++) {
            double t = (double) i / steps;
            Vec3 point = from.add(delta.scale(t));
            level.sendParticles(JJKParticles.BLOOD_TRAIL, point.x, point.y, point.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
            if (intensity > 0.6F && (i % 5 == 0)) {
                level.sendParticles(JJKParticles.BLOOD_CORE, point.x, point.y, point.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
            }
        }
    }

    private static void spawnBloodExplosion(ServerLevel level, Vec3 center, Vec3 dir) {
        Vec3 forward = dir.scale(0.22D);
        level.sendParticles(JJKParticles.BLOOD_EXPLOSION, center.x + forward.x, center.y + forward.y, center.z + forward.z, 10, 0.10D, 0.10D, 0.10D, 0.0D);
        level.sendParticles(JJKParticles.BLOOD_EXPLOSION, center.x, center.y, center.z, 12, 0.18D, 0.18D, 0.18D, 0.0D);
        level.sendParticles(JJKParticles.BLOOD_CORE, center.x, center.y, center.z, 3, 0.05D, 0.05D, 0.05D, 0.0D);
    }

    private static void spawnLaunchBurst(ServerLevel level, Vec3 origin, Vec3 dir) {
        Vec3 forward = dir.scale(0.15D);
        level.sendParticles(JJKParticles.BLOOD_CORE, origin.x + forward.x, origin.y + forward.y, origin.z + forward.z, 4, 0.04D, 0.04D, 0.04D, 0.0D);
        level.sendParticles(JJKParticles.BLOOD_TRAIL, origin.x, origin.y, origin.z, 6, 0.06D, 0.06D, 0.06D, 0.0D);
    }

    private static void spawnTrailEnd(ServerLevel level, Vec3 pos) {
        level.sendParticles(JJKParticles.BLOOD_EXPLOSION, pos.x, pos.y, pos.z, 8, 0.10D, 0.10D, 0.10D, 0.0D);
        level.sendParticles(JJKParticles.BLOOD_CORE, pos.x, pos.y, pos.z, 3, 0.06D, 0.06D, 0.06D, 0.0D);
    }

    private static double distancePointToSegment(Vec3 point, Vec3 a, Vec3 b) {
        Vec3 ab = b.subtract(a);
        double len2 = ab.lengthSqr();
        if (len2 < 0.0001D) {
            return point.distanceTo(a);
        }
        double t = Math.max(0.0D, Math.min(1.0D, point.subtract(a).dot(ab) / len2));
        return point.distanceTo(a.add(ab.scale(t)));
    }

    public void setHeld(boolean held) {
        boolean wasHeld = this.held;
        this.held = held;
        if (held) {
            this.graceUntilTick = -1;
            this.releaseTicksRemaining = 0;
            if (!wasHeld) {
                this.holdTicks = 0;
            }
        } else if (wasHeld) {
            this.releaseTicksRemaining = 10;
        }
    }

    public void requestMinLifetimeTicks(int ticks) {
        int target = this.tickCount + Math.max(0, ticks);
        if (this.graceUntilTick < target) {
            this.graceUntilTick = target;
        }
    }

    public void setChargeDamageMultiplier(float chargeDamageMultiplier) {
        this.chargeDamageMultiplier = Math.max(1.0F, chargeDamageMultiplier);
    }

    public void setMaxHeldTicks(int maxHeldTicks) {
        this.maxHeldTicks = Math.max(10, maxHeldTicks);
    }

    public void setHeldSpeed(float heldSpeed) {
        this.heldSpeed = Math.max(2.5D, Math.min(3.5D, heldSpeed));
    }

    public void setHitRadius(float hitRadius) {
        this.hitRadius = Math.max(0.55D, Math.min(0.85D, hitRadius));
    }

    public int getMaxHeldTicks() {
        return this.maxHeldTicks;
    }

    public boolean isHeldPhase() {
        return this.held;
    }

    @Override
    public void remove(RemovalReason reason) {
        if (this.level() instanceof ServerLevel) {
            notifyDied();
        }
        super.remove(reason);
    }

    private void notifyDied() {
        if (this.cleanupNotified) {
            return;
        }
        this.cleanupNotified = true;
        if (this.ownerUuid != null) {
            PiercingBloodTechniqueHandler.onProjectileDied(this.ownerUuid);
            return;
        }
        if (this.getOwner() instanceof ServerPlayer player) {
            PiercingBloodTechniqueHandler.onProjectileDied(player.getUUID());
        }
    }
}
