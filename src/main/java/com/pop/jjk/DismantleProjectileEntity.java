package com.pop.jjk;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class DismantleProjectileEntity extends Projectile {

    static final double SPEED = 4.0;
    static final double MAX_RANGE = 30.0;
    static final double HIT_RADIUS = 0.30;
    static final double BREAK_RADIUS = 0.35;
    static final int    BLOCKS_PER_TICK = 6;
    static final float  DAMAGE = 14.0F;
    static final double KNOCKBACK = 0.90;
    static final int    MAX_LIFETIME_TICKS = 120;

    private final Set<UUID> hitEntityIds = new HashSet<>();
    private double distanceTravelled;

    public DismantleProjectileEntity(EntityType<? extends DismantleProjectileEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    public DismantleProjectileEntity(ServerLevel level, ServerPlayer owner) {
        this(JJKMod.DISMANTLE_PROJECTILE, level);
        this.setOwner(owner);

        Vec3 dir = owner.getLookAngle().normalize();
        Vec3 origin = owner.getEyePosition().add(dir.scale(0.55));
        this.setPos(origin);
        this.setDeltaMovement(dir.scale(SPEED));
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

        if (this.tickCount > MAX_LIFETIME_TICKS) {
            this.discard();
            return;
        }

        Vec3 vel = this.getDeltaMovement();
        if (vel.lengthSqr() < 1.0E-6) { this.discard(); return; }

        Vec3 dir = vel.normalize();
        Vec3 from = this.position();
        Vec3 to   = from.add(dir.scale(SPEED));

        if (this.level() instanceof ServerLevel sl) {
            hitEntitiesAlongPath(sl, from, to, dir);
            breakBlocksAlongPath(sl, from, to);
            spawnTrail(sl, from, to);
        }

        this.setPos(to.x, to.y, to.z);
        this.distanceTravelled += SPEED;

        if (this.distanceTravelled >= MAX_RANGE) {
            if (this.level() instanceof ServerLevel sl) {
                spawnEndBurst(sl, to);
            }
            this.discard();
        }
    }

    private void hitEntitiesAlongPath(ServerLevel level, Vec3 from, Vec3 to, Vec3 dir) {
        double segLen = from.distanceTo(to);
        if (segLen < 1.0E-4) return;
        AABB box = new AABB(from, to).inflate(HIT_RADIUS);

        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, box,
            e -> e.isAlive() && e != this.getOwner())) {

            if (hitEntityIds.contains(entity.getUUID())) continue;

            Vec3 center  = entity.position().add(0.0, entity.getBbHeight() * 0.5, 0.0);
            Vec3 toEnt   = center.subtract(from);
            double proj  = Math.max(0, Math.min(segLen, toEnt.dot(dir)));
            Vec3 closest = from.add(dir.scale(proj));
            double allowed = HIT_RADIUS + entity.getBbWidth() * 0.5;

            if (closest.distanceTo(center) > allowed) continue;

            hitEntityIds.add(entity.getUUID());

            if (this.getOwner() instanceof ServerPlayer playerOwner) {
                entity.hurtServer(level, level.damageSources().playerAttack(playerOwner), DAMAGE);
            } else {
                entity.hurtServer(level, level.damageSources().magic(), DAMAGE);
            }

            entity.push(dir.x * KNOCKBACK, 0.10, dir.z * KNOCKBACK);
            entity.hurtMarked = true;

            level.sendParticles(ParticleTypes.CRIT, center.x, center.y, center.z, 8, 0.18, 0.22, 0.18, 0.10);
        }
    }

    private void breakBlocksAlongPath(ServerLevel level, Vec3 from, Vec3 to) {
        List<BlockPos> candidates = new ArrayList<>();
        BlockPos fromPos = BlockPos.containing(from);
        BlockPos toPos   = BlockPos.containing(to);
        int r = (int) Math.ceil(BREAK_RADIUS);

        for (BlockPos pos : BlockPos.betweenClosed(
            Math.min(fromPos.getX(), toPos.getX()) - r,
            Math.min(fromPos.getY(), toPos.getY()) - r,
            Math.min(fromPos.getZ(), toPos.getZ()) - r,
            Math.max(fromPos.getX(), toPos.getX()) + r,
            Math.max(fromPos.getY(), toPos.getY()) + r,
            Math.max(fromPos.getZ(), toPos.getZ()) + r
        )) {
            BlockPos immutable = pos.immutable();
            if (!isBreakable(level, immutable, from, to)) continue;
            candidates.add(immutable);
        }

        int broken = 0;
        for (BlockPos pos : candidates) {
            BlockState state = level.getBlockState(pos);
            if (state.isAir()) continue;
            Vec3 c = pos.getCenter();
            level.sendParticles(ParticleTypes.SWEEP_ATTACK, c.x, c.y + 0.1, c.z, 1, 0.02, 0.02, 0.02, 0.0);
            level.playSound(null, pos, state.getSoundType().getBreakSound(), SoundSource.BLOCKS,
                state.getSoundType().getVolume() * 0.5F, state.getSoundType().getPitch() * 1.3F);
            level.destroyBlock(pos, false);
            broken++;
            if (broken >= BLOCKS_PER_TICK) break;
        }
    }

    private static boolean isBreakable(ServerLevel level, BlockPos pos, Vec3 from, Vec3 to) {
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) return false;
        if (!state.getFluidState().isEmpty()) return false;
        if (level.getBlockEntity(pos) != null) return false;
        float speed = state.getDestroySpeed(level, pos);
        if (speed < 0.0F || speed > 2.0F) return false;
        return distancePointToSegment(pos.getCenter(), from, to) <= BREAK_RADIUS;
    }

    private static double distancePointToSegment(Vec3 point, Vec3 a, Vec3 b) {
        Vec3 ab = b.subtract(a);
        double len2 = ab.lengthSqr();
        if (len2 < 0.0001) return point.distanceTo(a);
        double t = Math.max(0.0, Math.min(1.0, point.subtract(a).dot(ab) / len2));
        return point.distanceTo(a.add(ab.scale(t)));
    }

    private static void spawnTrail(ServerLevel level, Vec3 from, Vec3 to) {
        double segLen = from.distanceTo(to);
        if (segLen < 0.05) return;
        Vec3 delta = to.subtract(from);
        int steps = Math.max(1, (int) (segLen / 0.40));
        for (int i = 0; i <= steps; i++) {
            double t = (double) i / steps;
            Vec3 p = from.add(delta.scale(t));
            level.sendParticles(ParticleTypes.SWEEP_ATTACK, p.x, p.y, p.z, 1, 0.02, 0.02, 0.02, 0.0);
            if ((i % 2) == 0) {
                level.sendParticles(ParticleTypes.CRIT, p.x, p.y + 0.05, p.z, 1, 0.01, 0.01, 0.01, 0.0);
            }
        }
    }

    private static void spawnEndBurst(ServerLevel level, Vec3 pos) {
        level.sendParticles(ParticleTypes.SWEEP_ATTACK, pos.x, pos.y, pos.z, 6, 0.15, 0.10, 0.15, 0.0);
        level.sendParticles(ParticleTypes.CRIT, pos.x, pos.y + 0.05, pos.z, 8, 0.10, 0.08, 0.10, 0.0);
    }
}
