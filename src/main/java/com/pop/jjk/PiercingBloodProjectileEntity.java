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
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class PiercingBloodProjectileEntity extends Projectile {

    // Velocidad inicial (altísima) y decaimiento exponencial por tick
    static final double INITIAL_SPEED     = 5.5;
    static final double SPEED_DECAY       = 0.88;   // factor multiplicativo por tick
    static final double MIN_SPEED         = 0.25;   // tap-only: se descarta al llegar aquí
    static final double MAX_RANGE         = 50.0;
    static final int    MAX_LIFETIME      = 32;     // ticks máximos de vida
    // Tracking de cámara: el rayo lerpea su dirección hacia donde mira el jugador
    // con un factor que decae exponencialmente SOLO cuando no está en hold
    static final double INITIAL_TRACKING  = 0.92;  // base para tap; fuerte
    static final double TRACKING_DECAY    = 0.88;  // se aplica sólo cuando held=false
    static final double HELD_TRACKING     = 0.90;  // factor constante y fuerte cuando held=true
    static final double HIT_RADIUS       = 0.70;
    static final double BREAK_RADIUS     = 0.40;
    static final int    BLOCKS_PER_TICK  = 6;
    // Hold: velocidad constante
    static final double HELD_SPEED       = 3.5;    // velocidad fija mientras se mantiene
    // Daño inicial (brutal) y mínimo (casi nulo al final)
    static final float  INITIAL_DAMAGE   = 12.0F;
    static final float  MIN_DAMAGE       = 1.0F;
    static final float  PIERCE_KNOCKBACK = 0.20F;

    private static final DustParticleOptions BREAK_DUST   = new DustParticleOptions(0xFF4040, 0.65F);

    private final Set<UUID> hitEntityIds = new HashSet<>();
    private double distanceTravelled;
    private boolean held;
    private int holdTicks;
    private int graceUntilTick = -1;
    private int releaseTicksRemaining = 0;

    public PiercingBloodProjectileEntity(EntityType<? extends PiercingBloodProjectileEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    public PiercingBloodProjectileEntity(ServerLevel level, ServerPlayer owner) {
        this(JJKMod.PIERCING_BLOOD_PROJECTILE, level);
        this.setOwner(owner);

        Vec3 dir    = owner.getLookAngle().normalize();
        Vec3 origin = owner.getEyePosition().add(dir.scale(0.55));

        this.setPos(origin);
        this.setDeltaMovement(dir.scale(INITIAL_SPEED));
        this.setYRot(owner.getYRot());
        this.setXRot(owner.getXRot());
        this.setOldPosAndRot();
        this.held = false;
        this.graceUntilTick = -1;
        this.holdTicks = 0;
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

        Vec3 vel = this.getDeltaMovement();
        if (vel.lengthSqr() < 0.0001) { if (this.level() instanceof ServerLevel) { notifyDied(); } this.discard(); return; }

        // ── Tracking de cámara ────────────────────────────────────────────────
        Vec3 currentDir = vel.normalize();
        if (this.held && this.getOwner() instanceof ServerPlayer ownerPlayer) {
            Vec3 lookDir = ownerPlayer.getLookAngle().normalize();
            currentDir = currentDir.add(lookDir.subtract(currentDir).scale(HELD_TRACKING)).normalize();
        }

        // ── Velocidad ────────────────────────────────────────────────────────
        double currentSpeed;
        if (this.releaseTicksRemaining > 0) {
            // Decaimiento rápido tras soltar: multiplicativo respecto a la velocidad previa
            double lastSpeed = vel.length();
            currentSpeed = Math.max(0.02, lastSpeed * 0.70);
            this.releaseTicksRemaining--;
            if (this.releaseTicksRemaining == 0) {
                if (this.level() instanceof ServerLevel sl) {
                    spawnTrailEnd(sl, this.position());
                    notifyDied();
                }
                this.discard();
                return;
            }
        } else {
            // Velocidad base con rampa inicial (siempre se aplica en ticks 1-2)
            double baseSpeed;
            if (this.tickCount == 1) {
                baseSpeed = INITIAL_SPEED * 2.2;
            } else if (this.tickCount == 2) {
                baseSpeed = INITIAL_SPEED * 1.4;
            } else {
                baseSpeed = INITIAL_SPEED * Math.pow(SPEED_DECAY, this.tickCount - 1);
            }

            if (this.held) {
                // En hold, tras la rampa inicial (>=tick 3), usar velocidad constante controlable
                currentSpeed = (this.tickCount >= 3) ? HELD_SPEED : baseSpeed;
            } else {
                currentSpeed = baseSpeed;
                if (currentSpeed <= MIN_SPEED) {
                    if (this.level() instanceof ServerLevel sl) { spawnTrailEnd(sl, this.position()); notifyDied(); }
                    this.discard(); return;
                }
            }
        }

        this.setDeltaMovement(currentDir.scale(currentSpeed));
        if (this.held) this.holdTicks++;

        // ── Movimiento ───────────────────────────────────────────────────────
        double remaining = (this.held || this.releaseTicksRemaining > 0)
            ? Double.POSITIVE_INFINITY : (MAX_RANGE - this.distanceTravelled);
        if (!(this.held || this.releaseTicksRemaining > 0) && remaining <= 0.0) {
            if (this.level() instanceof ServerLevel sl2) { spawnTrailEnd(sl2, this.position()); notifyDied(); }
            this.discard(); return; }

        double step = this.held ? currentSpeed : Math.min(currentSpeed, remaining);
        Vec3 prev = this.position();
        Vec3 next = prev.add(currentDir.scale(step));

        if (!(this.held || this.releaseTicksRemaining > 0)) this.distanceTravelled += step;
        this.setPos(next);
        this.move(MoverType.SELF, Vec3.ZERO);
        this.updateRotation();

        if (!(this.level() instanceof ServerLevel serverLevel)) return;

        // ── Intensidad global ────────────────────────────────────────────────
        float intensity;
        if (this.held) {
            intensity = 1.0F;
        } else if (this.releaseTicksRemaining > 0) {
            intensity = Math.max(0.0F, this.releaseTicksRemaining / 10.0F);
        } else {
            intensity = (float) Math.max(0.0, 1.0 - (double)(this.tickCount - 1) / MAX_LIFETIME);
        }
        float currentDamage = MIN_DAMAGE + (INITIAL_DAMAGE - MIN_DAMAGE) * intensity;

        if (this.tickCount == 1) {
            spawnLaunchBurst(serverLevel, prev, currentDir);
        }

        // BUG 1: limpiar la blacklist de entidades golpeadas cada tick en hold
        if (this.held) this.hitEntityIds.clear();
        hitEntitiesAlongPath(serverLevel, prev, next, currentDir, currentDamage);
        breakBlocksAlongPath(serverLevel, prev, next);

        // Rayo completo desde ojo del jugador hasta la punta (visual principal)
        if (this.getOwner() instanceof ServerPlayer ownerForVisual) {
            spawnFullBeamVisual(serverLevel, ownerForVisual.getEyePosition(), next, intensity);
        }
        // Visual único de rayo: no generamos trail adicional para evitar líneas duplicadas

        if (this.distanceTravelled >= MAX_RANGE && !(this.held || this.releaseTicksRemaining > 0)) {
            spawnTrailEnd(serverLevel, next);
            notifyDied();
            this.discard();
        } else if (!(this.held || this.releaseTicksRemaining > 0)) {
            if (this.tickCount >= MAX_LIFETIME) {
                spawnTrailEnd(serverLevel, next);
                notifyDied();
                this.discard();
            }
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
        AABB box = new AABB(from, to).inflate(HIT_RADIUS);

        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, box,
                e -> e.isAlive() && e != this.getOwner())) {
            if (hitEntityIds.contains(entity.getUUID())) continue;

            Vec3 center  = entity.position().add(0, entity.getBbHeight() * 0.5, 0);
            Vec3 toEnt   = center.subtract(from);
            double proj  = Math.max(0, Math.min(segLen, toEnt.dot(dir)));
            Vec3 closest = from.add(dir.scale(proj));

            if (closest.distanceTo(center) > HIT_RADIUS + entity.getBbWidth() * 0.5) continue;

            hitEntityIds.add(entity.getUUID());

            if (this.getOwner() instanceof ServerPlayer playerOwner) {
                entity.hurtServer(level, level.damageSources().playerAttack(playerOwner), currentDamage);
            } else {
                entity.hurtServer(level, level.damageSources().magic(), currentDamage);
            }

            entity.push(dir.x * PIERCE_KNOCKBACK, 0.0, dir.z * PIERCE_KNOCKBACK);
            entity.hurtMarked = true;

            spawnBloodExplosion(level, center, dir);
            level.playSound(null, entity.blockPosition(),
                SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 1.0F, 1.7F);
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
            if (!isBreakable(level, immutable, from, to)) {
                continue;
            }
            candidates.add(immutable);
        }

        candidates.sort(Comparator.comparingDouble(p -> p.getCenter().distanceToSqr(to)));

        int broken = 0;
        for (BlockPos pos : candidates) {
            BlockState state = level.getBlockState(pos);
            if (state.isAir()) {
                continue;
            }

            Vec3 c = pos.getCenter();
            level.sendParticles(BREAK_DUST, c.x, c.y, c.z, 5, 0.08, 0.08, 0.08, 0.0);
            level.playSound(null, pos, state.getSoundType().getBreakSound(), SoundSource.BLOCKS,
                state.getSoundType().getVolume() * 0.5F, state.getSoundType().getPitch() * 1.2F);
            level.destroyBlock(pos, false);
            broken++;
            if (broken >= BLOCKS_PER_TICK) {
                break;
            }
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

    /**
     * Dibuja el rayo completo desde el ojo del jugador hasta la punta cada tick.
     * Usa paso grueso (1.0 bloque) para no saturar de partículas.
     */
    private static void spawnFullBeamVisual(ServerLevel level, Vec3 from, Vec3 to, float intensity) {
        double segLen = from.distanceTo(to);
        if (segLen < 0.05) return;
        Vec3 delta = to.subtract(from);
        // Trazo continuo con alta densidad: puntos cada ~0.10 bloques (más sólido y volumétrico)
        int steps = Math.max(1, (int)(segLen / 0.10));
        for (int i = 0; i <= steps; i++) {
            double t = (double) i / steps;
            Vec3 p   = from.add(delta.scale(t));
            // Nueva partícula trail (fullbright + turbulencia) para línea viva y con bloom
            level.sendParticles(JJKParticles.BLOOD_TRAIL, p.x, p.y, p.z, 1, 0.0, 0.0, 0.0, 0.0);
            // Refuerzo del halo en alta intensidad (pocas unidades para no saturar)
            if (intensity > 0.6F && (i % 5 == 0)) {
                level.sendParticles(JJKParticles.BLOOD_CORE, p.x, p.y, p.z, 1, 0.0, 0.0, 0.0, 0.0);
            }
        }
    }


    private static void spawnBloodExplosion(ServerLevel level, Vec3 center, Vec3 dir) {
        Vec3 fwd = dir.scale(0.22);
        // Estallido denso y brillante
        level.sendParticles(JJKParticles.BLOOD_EXPLOSION,
            center.x + fwd.x, center.y + fwd.y, center.z + fwd.z,
            10, 0.10, 0.10, 0.10, 0.0);
        level.sendParticles(JJKParticles.BLOOD_EXPLOSION, center.x, center.y, center.z,
            12, 0.18, 0.18, 0.18, 0.0);
        // Núcleos rojos para glow volumétrico
        level.sendParticles(JJKParticles.BLOOD_CORE, center.x, center.y, center.z,
            3, 0.05, 0.05, 0.05, 0.0);
    }

    private static void spawnLaunchBurst(ServerLevel level, Vec3 origin, Vec3 dir) {
        Vec3 fwd = dir.scale(0.15);
        level.sendParticles(JJKParticles.BLOOD_CORE,
            origin.x + fwd.x, origin.y + fwd.y, origin.z + fwd.z,
            4, 0.04, 0.04, 0.04, 0.0);
        level.sendParticles(JJKParticles.BLOOD_TRAIL, origin.x, origin.y, origin.z,
            6, 0.06, 0.06, 0.06, 0.0);
    }

    private static void spawnTrailEnd(ServerLevel level, Vec3 pos) {
        level.sendParticles(JJKParticles.BLOOD_EXPLOSION, pos.x, pos.y, pos.z, 8, 0.10, 0.10, 0.10, 0.0);
        level.sendParticles(JJKParticles.BLOOD_CORE,      pos.x, pos.y, pos.z, 3, 0.06, 0.06, 0.06, 0.0);
    }

    private static double distancePointToSegment(Vec3 point, Vec3 a, Vec3 b) {
        Vec3 ab = b.subtract(a);
        double len2 = ab.lengthSqr();
        if (len2 < 0.0001) return point.distanceTo(a);
        double t = Math.max(0.0, Math.min(1.0, point.subtract(a).dot(ab) / len2));
        return point.distanceTo(a.add(ab.scale(t)));
    }

    public void setHeld(boolean held) {
        boolean wasHeld = this.held;
        this.held = held;
        if (held) {
            this.graceUntilTick = -1;
            this.releaseTicksRemaining = 0; // cancelar fade de liberación si se vuelve a mantener
        } else if (wasHeld && !held) {
            // Iniciar fade de liberación (10 ticks)
            this.releaseTicksRemaining = 10;
        }
    }

    public void requestMinLifetimeTicks(int ticks) {
        int target = this.tickCount + Math.max(0, ticks);
        if (this.graceUntilTick < target) this.graceUntilTick = target;
    }

    private void notifyDied() {
        if (this.getOwner() instanceof ServerPlayer sp) {
            PiercingBloodTechniqueHandler.onProjectileDied(sp.getUUID());
        }
    }
}
