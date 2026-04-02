package com.pop.jjk;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Open: Fuga — Proyectil de fuego con breve carga en la palma y rayo flamígero de largo alcance.
 * - CHARGE: se ancla a la mano durante pocos ticks y genera partículas incandescentes.
 * - LAUNCHED: viaja muy rápido, quema entidades y enciende bloques a lo largo del trayecto.
 * - IMPACT: explosión masiva de fuego con gran daño de área, empuje y fuego persistente en el área.
 */
public class FugaProjectileEntity extends Projectile {

    // Ajustables para balance
    private static final int CHARGE_TICKS = 12;
    private static final int MAX_FLIGHT_TICKS = 30;
    private static final double SPEED = 2.8;
    private static final double MAX_DISTANCE = 44.0;
    private static final double PATH_RADIUS = 1.15;          // grosor del rayo
    private static final double IGNITE_RADIUS = 0.9;         // ignición de bloques/entidades cercana al eje
    private static final double IMPACT_RADIUS = 8.0;         // radio de explosión
    private static final float PATH_DAMAGE = 10.0F;          // daño por contacto con el rayo
    private static final float IMPACT_DAMAGE = 26.0F;        // daño en la explosión final
    private static final double KNOCKBACK = 1.25;            // empuje en explosión

    private static final DustParticleOptions FIRE_CORE = new DustParticleOptions(0xFF7A00, 1.35F);
    private static final DustParticleOptions FIRE_GLOW = new DustParticleOptions(0xFFA040, 1.10F);

    private double distanceTravelled = 0.0;
    private int ticksSinceLaunch = 0;
    private boolean launched = false;
    private final Set<UUID> hitEntities = new HashSet<>();

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
    protected void defineSynchedData(SynchedEntityData.Builder builder) {}

    @Override
    protected void readAdditionalSaveData(ValueInput input) {}

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {}

    @Override
    public boolean isPickable() { return false; }

    @Override
    public boolean canBeHitByProjectile() { return false; }

    @Override
    public void tick() {
        super.tick();
        this.noPhysics = true;
        this.setNoGravity(true);

        if (!(this.level() instanceof ServerLevel level)) return;

        ServerPlayer owner = this.getOwner() instanceof ServerPlayer sp ? sp : null;
        if (owner == null || !owner.isAlive() || owner.level() != level) {
            this.discard();
            return;
        }

        if (!launched) {
            tickCharge(level, owner);
            return;
        }

        tickFlight(level, owner);
    }

    private void tickCharge(ServerLevel level, ServerPlayer owner) {
        Vec3 dir = owner.getLookAngle().normalize();
        Vec3 anchor = computePalmAnchor(owner, dir);
        this.setPos(anchor);
        this.setDeltaMovement(Vec3.ZERO);
        this.setYRot(owner.getYRot());
        this.setXRot(owner.getXRot());

        // Sonido de preparación
        if (this.tickCount == 1) {
            level.playSound(null, anchor.x, anchor.y, anchor.z, SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 0.7F, 1.6F);
        }

        // Partículas de carga muy brillantes
        double spread = 0.12 + (this.tickCount * 0.01);
        level.sendParticles(FIRE_GLOW, anchor.x, anchor.y, anchor.z, 6, spread, spread * 0.55, spread, 0.0);
        level.sendParticles(FIRE_CORE, anchor.x, anchor.y, anchor.z, 3, spread * 0.6, spread * 0.4, spread * 0.6, 0.0);
        level.sendParticles(ParticleTypes.FLAME, anchor.x, anchor.y, anchor.z, 4, spread * 0.5, spread * 0.5, spread * 0.5, 0.01);

        if (this.tickCount >= CHARGE_TICKS) {
            launch(level, owner, anchor, dir);
        }
    }

    private void launch(ServerLevel level, ServerPlayer owner, Vec3 anchor, Vec3 dir) {
        this.launched = true;
        this.ticksSinceLaunch = 0;
        Vec3 start = anchor.add(dir.scale(1.0));
        this.setPos(start);
        this.setDeltaMovement(dir.scale(SPEED));
        this.setYRot(owner.getYRot());
        this.setXRot(owner.getXRot());
        level.playSound(null, start.x, start.y, start.z, SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 1.0F, 0.95F);
        sendShakeToNearby(level, start, 2.2F, 8);
    }

    private void tickFlight(ServerLevel level, ServerPlayer owner) {
        Vec3 vel = this.getDeltaMovement();
        if (vel.lengthSqr() < 1.0E-6) { finish(level, owner); return; }

        Vec3 from = this.position();
        double remain = MAX_DISTANCE - this.distanceTravelled;
        if (remain <= 0.0 || this.ticksSinceLaunch >= MAX_FLIGHT_TICKS) { finish(level, owner); return; }

        Vec3 movement = vel.length() > remain ? vel.normalize().scale(remain) : vel;
        Vec3 to = from.add(movement);

        // Daño y fuego a entidades en el trayecto
        burnEntitiesAlongPath(level, from, to, owner, vel.normalize());
        // Encender bloques y, en menor medida, romper blandos
        igniteBlocksAlongPath(level, from, to);
        // Partículas volumétricas del rayo
        spawnBeamParticles(level, from, to);

        // Avanzar
        this.setPos(to);
        this.distanceTravelled += movement.length();
        this.ticksSinceLaunch++;

        if (this.distanceTravelled >= MAX_DISTANCE || this.ticksSinceLaunch >= MAX_FLIGHT_TICKS) {
            finish(level, owner);
        }
    }

    private void finish(ServerLevel level, ServerPlayer owner) {
        Vec3 center = this.position();
        explode(level, center, owner);
        this.discard();
    }

    private void burnEntitiesAlongPath(ServerLevel level, Vec3 from, Vec3 to, ServerPlayer owner, Vec3 dir) {
        AABB area = new AABB(from, to).inflate(PATH_RADIUS + 0.6);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, area, ent -> ent.isAlive() && ent != this.getOwner())) {
            Vec3 c = e.position().add(0.0, e.getBbHeight() * 0.5, 0.0);
            if (distancePointToSegment(c, from, to) > PATH_RADIUS) continue;
            if (!hitEntities.add(e.getUUID())) continue;

            if (owner != null) {
                e.hurtServer(level, level.damageSources().playerAttack(owner), PATH_DAMAGE);
            } else {
                e.hurtServer(level, level.damageSources().magic(), PATH_DAMAGE);
            }
            e.push(dir.x * 0.35, 0.08, dir.z * 0.35);
            e.hurtMarked = true;
            // Encender durante unos segundos
            e.setRemainingFireTicks(120);
            level.sendParticles(ParticleTypes.LAVA, c.x, c.y, c.z, 3, 0.1, 0.1, 0.1, 0.02);
        }
    }

    private void igniteBlocksAlongPath(ServerLevel level, Vec3 from, Vec3 to) {
        AABB sweep = new AABB(from, to).inflate(IGNITE_RADIUS + 0.6);
        List<BlockPos> candidates = new ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(
            BlockPos.containing(sweep.minX, sweep.minY, sweep.minZ),
            BlockPos.containing(sweep.maxX, sweep.maxY, sweep.maxZ)
        )) {
            BlockPos p = pos.immutable();
            Vec3 pc = p.getCenter();
            if (distancePointToSegment(pc, from, to) > IGNITE_RADIUS + 0.35) continue;
            candidates.add(p);
        }

        int ignited = 0;
        for (BlockPos pos : candidates) {
            if (ignited >= 8) break;
            // Preferir encender el bloque de aire si el de abajo es sólido
            BlockPos place = canPlaceFireAt(level, pos) ? pos : canPlaceFireAt(level, pos.above()) ? pos.above() : null;
            if (place == null) continue;
            if (level.random.nextFloat() > 0.45F) continue;
            level.setBlock(place, Blocks.FIRE.defaultBlockState(), 11);
            ignited++;
        }
    }

    private static boolean canPlaceFireAt(ServerLevel level, BlockPos pos) {
        if (!level.getBlockState(pos).isAir()) return false;
        BlockState fire = Blocks.FIRE.defaultBlockState();
        return fire.canSurvive(level, pos);
    }

    private void explode(ServerLevel level, Vec3 center, ServerPlayer owner) {
        // Sonidos y partículas masivas
        level.playSound(null, center.x, center.y, center.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 1.6F, 0.85F);
        level.playSound(null, center.x, center.y, center.z, SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 1.3F, 0.72F);
        spawnExplosionParticles(level, center);
        sendShakeToNearby(level, center, 5.5F, 14);

        // Daño, empuje e ignición en área
        AABB burst = new AABB(center, center).inflate(IMPACT_RADIUS + 0.5);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, burst, ent -> ent.isAlive() && ent != this.getOwner())) {
            double dist = e.position().distanceTo(center);
            if (dist > IMPACT_RADIUS + 0.8) continue;
            Vec3 push = e.position().subtract(center).normalize().scale(KNOCKBACK);
            e.push(push.x, 0.25 + (push.y * 0.1), push.z);
            e.hurtMarked = true;
            if (owner != null) {
                e.hurtServer(level, level.damageSources().playerAttack(owner), IMPACT_DAMAGE);
            } else {
                e.hurtServer(level, level.damageSources().magic(), IMPACT_DAMAGE);
            }
            e.setRemainingFireTicks(160);
        }

        // Encender el área con manchas de fuego
        int r = Mth.ceil(IMPACT_RADIUS);
        BlockPos cpos = BlockPos.containing(center);
        for (BlockPos pos : BlockPos.betweenClosed(cpos.offset(-r, -r, -r), cpos.offset(r, r, r))) {
            if (pos.getCenter().distanceTo(center) > IMPACT_RADIUS + 0.6) continue;
            if (level.random.nextFloat() > 0.18F) continue;
            BlockPos place = canPlaceFireAt(level, pos) ? pos : canPlaceFireAt(level, pos.above()) ? pos.above() : null;
            if (place != null) {
                level.setBlock(place, Blocks.FIRE.defaultBlockState(), 11);
            }
        }
    }

    private static void spawnBeamParticles(ServerLevel level, Vec3 from, Vec3 to) {
        Vec3 delta = to.subtract(from);
        double len = delta.length();
        if (len < 0.05) return;
        int steps = Math.max(1, (int) (len / 0.35));
        Vec3 step = delta.scale(1.0 / steps);
        for (int i = 0; i <= steps; i++) {
            Vec3 p = from.add(step.scale(i));
            level.sendParticles(FIRE_CORE, p.x, p.y, p.z, 4, 0.25, 0.15, 0.25, 0.0);
            level.sendParticles(FIRE_GLOW, p.x, p.y, p.z, 4, 0.32, 0.18, 0.32, 0.0);
            if ((i % 2) == 0) {
                level.sendParticles(ParticleTypes.FLAME, p.x, p.y, p.z, 3, 0.14, 0.14, 0.14, 0.01);
                level.sendParticles(ParticleTypes.LAVA, p.x, p.y, p.z, 1, 0.10, 0.10, 0.10, 0.02);
            }
        }
    }

    private static void spawnExplosionParticles(ServerLevel level, Vec3 center) {
        level.sendParticles(FIRE_CORE, center.x, center.y, center.z, 90, 2.0, 1.6, 2.0, 0.0);
        level.sendParticles(FIRE_GLOW, center.x, center.y, center.z, 70, 1.6, 1.3, 1.6, 0.0);
        level.sendParticles(ParticleTypes.FLAME, center.x, center.y, center.z, 120, 2.2, 1.8, 2.2, 0.02);
        level.sendParticles(ParticleTypes.LAVA, center.x, center.y, center.z, 20, 1.2, 1.0, 1.2, 0.0);
        level.sendParticles(ParticleTypes.EXPLOSION, center.x, center.y, center.z, 6, 1.5, 1.5, 1.5, 0.0);
        level.sendParticles(ParticleTypes.SMOKE, center.x, center.y, center.z, 18, 1.4, 1.2, 1.4, 0.03);
    }

    private static Vec3 computePalmAnchor(ServerPlayer owner, Vec3 dir) {
        // Aproximación de palma: un poco por delante de la cara y levemente abajo
        return owner.getEyePosition().add(dir.scale(0.85)).add(0.0, -0.28, 0.0);
    }

    private static double distancePointToSegment(Vec3 point, Vec3 a, Vec3 b) {
        Vec3 ab = b.subtract(a);
        double len2 = ab.lengthSqr();
        if (len2 < 1.0E-6) return point.distanceTo(a);
        double t = Math.max(0.0, Math.min(1.0, point.subtract(a).dot(ab) / len2));
        return point.distanceTo(a.add(ab.scale(t)));
    }

    private static void sendShakeToNearby(ServerLevel level, Vec3 center, float intensity, int durationTicks) {
        double range = 48.0;
        for (ServerPlayer nearby : level.getPlayers(p -> p.position().distanceTo(center) < range)) {
            float dist = (float) nearby.position().distanceTo(center);
            float falloff = Math.max(0.0F, 1.0F - (dist / (float) range));
            if (falloff > 0.05F) {
                net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(nearby, new ScreenShakePayload(intensity * falloff, durationTicks));
            }
        }
    }
}
