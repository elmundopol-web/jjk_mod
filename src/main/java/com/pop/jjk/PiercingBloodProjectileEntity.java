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
import net.minecraft.world.entity.Entity.RemovalReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class PiercingBloodProjectileEntity extends Projectile {

    private static final double BASE_INITIAL_SPEED = 2.5D;
    private static final double EXTRA_INITIAL_SPEED = 1.0D;
    private static final double MIN_SPEED = 0.20D;
    private static final float BASE_INITIAL_DAMAGE = 12.0F;
    private static final float EXTRA_INITIAL_DAMAGE = 8.0F;
    private static final float MIN_DAMAGE = 1.0F;
    private static final double MAX_BEAM_LENGTH = 32.0D;
    private static final double HIT_RADIUS = 0.70D;
    private static final double BREAK_RADIUS = 0.40D;
    private static final int BLOCKS_PER_TICK = 6;
    private static final float PIERCE_KNOCKBACK = 0.20F;
    private static final int DEFAULT_MAX_LIFETIME = 300;

    private static final DustParticleOptions BREAK_DUST = new DustParticleOptions(0xFF4040, 0.65F);

    private final Set<UUID> hitThisTick = new HashSet<>();
    private int lifeTicks;
    private int maxLifetime = DEFAULT_MAX_LIFETIME;
    private double initialSpeed = BASE_INITIAL_SPEED;
    private float initialDamage = BASE_INITIAL_DAMAGE;
    private double beamReach;
    private boolean cleanupNotified;
    private boolean suppressCooldown;
    private UUID ownerUuid;

    public PiercingBloodProjectileEntity(EntityType<? extends PiercingBloodProjectileEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    public PiercingBloodProjectileEntity(ServerLevel level, ServerPlayer owner) {
        this(JJKMod.PIERCING_BLOOD_PROJECTILE, level);
        this.setOwner(owner);
        this.ownerUuid = owner.getUUID();

        Vec3 dir = owner.getLookAngle().normalize();
        Vec3 origin = owner.getEyePosition().add(dir.scale(0.55D));
        this.setPos(origin);
        this.setDeltaMovement(dir.scale(BASE_INITIAL_SPEED));
        this.setYRot(owner.getYRot());
        this.setXRot(owner.getXRot());
        this.setOldPosAndRot();
    }

    public void configure(float chargeFactor, int lifetimeTicks) {
        float clamped = Math.max(0.0F, Math.min(1.0F, chargeFactor));
        this.maxLifetime = Math.max(20, lifetimeTicks);
        this.initialSpeed = BASE_INITIAL_SPEED + clamped * EXTRA_INITIAL_SPEED;
        this.initialDamage = BASE_INITIAL_DAMAGE + clamped * EXTRA_INITIAL_DAMAGE;
    }

    public void suppressCooldownOnRemoval() {
        this.suppressCooldown = true;
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
        this.hitThisTick.clear();

        if (!(this.level() instanceof ServerLevel serverLevel)) {
            return;
        }

        if (!(this.getOwner() instanceof ServerPlayer owner) || !owner.isAlive()) {
            notifyDied();
            this.discard();
            return;
        }

        if (this.lifeTicks >= this.maxLifetime) {
            spawnTrailEnd(serverLevel, this.position());
            notifyDied();
            this.discard();
            return;
        }

        float progress = Math.min(1.0F, this.lifeTicks / (float) this.maxLifetime);
        double currentSpeed = this.initialSpeed + (MIN_SPEED - this.initialSpeed) * progress;
        float currentDamage = this.initialDamage + (MIN_DAMAGE - this.initialDamage) * progress;

        Vec3 lookDir = owner.getLookAngle().normalize();
        Vec3 origin = owner.getEyePosition();
        Vec3 prevHead = this.position();

        this.beamReach = Math.min(MAX_BEAM_LENGTH, this.beamReach + currentSpeed);
        Vec3 head = origin.add(lookDir.scale(this.beamReach));

        this.setPos(head);
        this.setDeltaMovement(lookDir.scale(currentSpeed));
        this.setYRot(owner.getYRot());
        this.setXRot(owner.getXRot());

        hitEntitiesAlongBeam(serverLevel, owner, origin, head, lookDir, currentDamage);
        breakBlocksAlongPath(serverLevel, prevHead, head);
        spawnFullBeamVisual(serverLevel, origin, head, 1.0F - progress * 0.5F);

        if (this.lifeTicks == 0) {
            spawnLaunchBurst(serverLevel, origin, lookDir);
        }

        this.lifeTicks++;
    }

    private void hitEntitiesAlongBeam(ServerLevel level, ServerPlayer owner, Vec3 from, Vec3 to, Vec3 dir, float damage) {
        double segLen = from.distanceTo(to);
        if (segLen < 0.05D) {
            return;
        }

        AABB box = new AABB(from, to).inflate(HIT_RADIUS);
        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, box,
                candidate -> candidate.isAlive() && candidate != owner)) {
            if (!this.hitThisTick.add(entity.getUUID())) {
                continue;
            }

            Vec3 center = entity.position().add(0.0D, entity.getBbHeight() * 0.5D, 0.0D);
            Vec3 toEntity = center.subtract(from);
            double projection = Math.max(0.0D, Math.min(segLen, toEntity.dot(dir)));
            Vec3 closest = from.add(dir.scale(projection));

            if (closest.distanceTo(center) > HIT_RADIUS + (entity.getBbWidth() * 0.5D)) {
                continue;
            }

            entity.hurtServer(level, level.damageSources().playerAttack(owner), damage);
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
        UUID id = this.ownerUuid;
        if (id == null && this.getOwner() instanceof ServerPlayer ownerPlayer) {
            id = ownerPlayer.getUUID();
        }
        if (id == null) {
            return;
        }
        if (this.suppressCooldown) {
            PiercingBloodTechniqueHandler.onProjectileReplaced(id);
        } else {
            PiercingBloodTechniqueHandler.onProjectileDied(id);
        }
    }
}
