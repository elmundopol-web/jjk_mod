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
import java.util.ArrayDeque;
import java.util.Deque;
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

    // Ajustables a través de FugaTechnique
    private static final int MAX_FLIGHT_TICKS = (int) Math.ceil(FugaTechnique.MAX_DISTANCE / FugaTechnique.SPEED) + 6;

    private double distanceTravelled = 0.0;
    private int ticksSinceLaunch = 0;
    private boolean launched = false;
    private final Set<UUID> hitEntities = new HashSet<>();
    private final Deque<BlockPos> ignitionQueue = new ArrayDeque<>();
    private float chargePower = 0.0F; // 0..1

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

        if (!launched) { return; }
        tickFlight(level, owner);
    }

    public void launchFrom(ServerPlayer owner) {
        if (!(this.level() instanceof ServerLevel level)) return;
        Vec3 dir = owner.getLookAngle().normalize();
        Vec3 anchor = computePalmAnchor(owner, dir);
        this.launched = true;
        this.ticksSinceLaunch = 0;
        this.distanceTravelled = 0.0;
        Vec3 start = anchor.add(dir.scale(1.0));
        this.setPos(start);
        this.setDeltaMovement(dir.scale(effSpeed()));
        this.setYRot(owner.getYRot());
        this.setXRot(owner.getXRot());
        level.playSound(null, start.x, start.y, start.z, SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 1.0F, 0.95F);
        sendShakeToNearby(level, start, 2.2F, 8);
    }

    private void tickFlight(ServerLevel level, ServerPlayer owner) {
        Vec3 vel = this.getDeltaMovement();
        if (vel.lengthSqr() < 1.0E-6) { finish(level, owner); return; }

        Vec3 from = this.position();
        double remain = effMaxDistance() - this.distanceTravelled;
        if (remain <= 0.0 || this.ticksSinceLaunch >= MAX_FLIGHT_TICKS) { finish(level, owner); return; }

        Vec3 movement = vel.length() > remain ? vel.normalize().scale(remain) : vel;
        Vec3 to = from.add(movement);

        // Daño y fuego a entidades en el trayecto
        burnEntitiesAlongPath(level, from, to, owner, vel.normalize());
        // Encender bloques y, en menor medida, romper blandos
        igniteBlocksAlongPath(level, from, to);
        // FX del rayo (cliente del dueño) — minimizar carga: cada 2 ticks
        if ((this.ticksSinceLaunch & 1) == 0) {
            sendBeamFX(level, owner, from, to);
        }

        // Avanzar
        this.setPos(to);
        this.distanceTravelled += movement.length();
        this.ticksSinceLaunch++;

        // Procesar igniciones distribuidas para evitar picos de lag
        int igniteBudget = 16 + (int) (chargePower * 16);
        while (igniteBudget-- > 0 && !ignitionQueue.isEmpty()) {
            BlockPos pos = ignitionQueue.pollFirst();
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
        double pr = effPathRadius();
        AABB area = new AABB(from, to).inflate(pr + 0.6);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, area, ent -> ent.isAlive() && ent != this.getOwner())) {
            Vec3 c = e.position().add(0.0, e.getBbHeight() * 0.5, 0.0);
            if (distancePointToSegment(c, from, to) > pr) continue;
            if (!hitEntities.add(e.getUUID())) continue;

            float dmg = effPathDamage();
            if (owner != null) {
                e.hurtServer(level, level.damageSources().playerAttack(owner), dmg);
            } else {
                e.hurtServer(level, level.damageSources().magic(), dmg);
            }
            e.push(dir.x * 0.35, 0.08, dir.z * 0.35);
            e.hurtMarked = true;
            // Encender durante unos segundos
            e.setRemainingFireTicks(120);
            level.sendParticles(JJKParticles.FIRE_TRAIL, c.x, c.y, c.z, 2, 0.08, 0.08, 0.08, 0.0);
        }
    }

    private void igniteBlocksAlongPath(ServerLevel level, Vec3 from, Vec3 to) {
        double ir = effIgniteRadius();
        AABB sweep = new AABB(from, to).inflate(ir + 0.6);
        List<BlockPos> candidates = new ArrayList<>();
        Vec3 d = to.subtract(from);
        double dlen = d.length();
        double sx = 0.0, sz = 0.0;
        if (dlen > 1.0E-4) {
            Vec3 nd = d.scale(1.0 / dlen);
            // vector lateral en XZ: (-dz, 0, dx)
            sx = -nd.z;
            sz = nd.x;
        }
        for (BlockPos pos : BlockPos.betweenClosed(
            BlockPos.containing(sweep.minX, sweep.minY, sweep.minZ),
            BlockPos.containing(sweep.maxX, sweep.maxY, sweep.maxZ)
        )) {
            BlockPos p = pos.immutable();
            Vec3 pc = p.getCenter();
            if (distancePointToSegment(pc, from, to) > ir + 0.35) continue;
            // Paridad para reducir densidad de escaneo sin perder cobertura
            if (((p.getX() ^ p.getZ()) & 1) != (this.ticksSinceLaunch & 1)) continue;
            candidates.add(p);
            // Añadir laterales cuando hay dirección válida
            if (dlen > 1.0E-4 && level.random.nextFloat() < 0.60F) {
                BlockPos left = BlockPos.containing(pc.x + sx, pc.y, pc.z + sz);
                BlockPos right = BlockPos.containing(pc.x - sx, pc.y, pc.z - sz);
                candidates.add(left);
                candidates.add(right);
            }
        }
        // Añadir a la cola para encendido diferido y suave
        int toQueue = 56; // más alto pero diferido para espectáculo
        for (BlockPos pos : candidates) {
            if (toQueue-- <= 0) break;
            BlockPos place = canPlaceFireAt(level, pos) ? pos : canPlaceFireAt(level, pos.above()) ? pos.above() : null;
            if (place == null) continue;
            if (level.random.nextFloat() > 0.40F) continue; // más abundante
            if (ignitionQueue.size() < 256) ignitionQueue.addLast(place);
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
        sendExplosionFX(level, owner, center, (float) effImpactRadius());
        sendShakeToNearby(level, center, 5.5F, 14);

        // Daño, empuje e ignición en área
        double irad = effImpactRadius();
        float kb = effKnockback();
        float idmg = effImpactDamage();
        AABB burst = new AABB(center, center).inflate(irad + 0.5);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, burst, ent -> ent.isAlive() && ent != this.getOwner())) {
            double dist = e.position().distanceTo(center);
            if (dist > irad + 0.8) continue;
            Vec3 push = e.position().subtract(center).normalize().scale(kb);
            e.push(push.x, 0.25 + (push.y * 0.1), push.z);
            e.hurtMarked = true;
            if (owner != null) {
                e.hurtServer(level, level.damageSources().playerAttack(owner), idmg);
            } else {
                e.hurtServer(level, level.damageSources().magic(), idmg);
            }
            e.setRemainingFireTicks(160);
        }

        // Encender el área con muestreo aleatorio (evitar freeze por triple-bucle gigante)
        int samples = (int) (180 + 80 * chargePower);
        BlockPos base = BlockPos.containing(center);
        for (int i = 0; i < samples; i++) {
            double theta = level.random.nextDouble() * Math.PI * 2.0;
            double phi = Math.acos(level.random.nextDouble() * 2.0 - 1.0);
            double rr = level.random.nextDouble() * (irad + 0.6);
            int ox = (int) Math.round(rr * Math.sin(phi) * Math.cos(theta));
            int oy = (int) Math.round(rr * Math.cos(phi));
            int oz = (int) Math.round(rr * Math.sin(phi) * Math.sin(theta));
            BlockPos pos = base.offset(ox, oy, oz);
            if (pos.getCenter().distanceTo(center) > irad + 0.6) continue;
            BlockPos place = canPlaceFireAt(level, pos) ? pos : canPlaceFireAt(level, pos.above()) ? pos.above() : null;
            if (place != null && level.random.nextFloat() < 0.65F) {
                level.setBlock(place, Blocks.FIRE.defaultBlockState(), 11);
            }
        }
    }

    private static void sendBeamFX(ServerLevel level, ServerPlayer owner, Vec3 from, Vec3 to) {
        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(owner,
            new FugaBeamFXPayload(from.x, from.y, from.z, to.x, to.y, to.z));
    }

    private static void spawnExplosionParticles(ServerLevel level, Vec3 center) {
        level.sendParticles(JJKParticles.FIRE_EXPLOSION, center.x, center.y, center.z, 120, 2.2, 1.8, 2.2, 0.0);
        level.sendParticles(JJKParticles.FIRE_TRAIL, center.x, center.y, center.z, 40, 1.2, 1.0, 1.2, 0.0);
    }

    private static void sendExplosionFX(ServerLevel level, ServerPlayer owner, Vec3 center, float radius) {
        if (owner != null) {
            net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(owner,
                new FugaExplosionFXPayload(center.x, center.y, center.z, radius));
        }
    }

    private static Vec3 computePalmAnchor(ServerPlayer owner, Vec3 dir) {
        // Aproximación de palma: un poco por delante de la cara y levemente abajo
        return owner.getEyePosition().add(dir.scale(0.85)).add(0.0, -0.28, 0.0);
    }

    public void setChargePower(float power) {
        this.chargePower = Mth.clamp(power, 0.0F, 1.0F);
    }

    private double effMaxDistance() {
        return FugaTechnique.MAX_DISTANCE + (FugaTechnique.OVR_MAX_DISTANCE - FugaTechnique.MAX_DISTANCE) * chargePower;
    }

    private double effSpeed() {
        return FugaTechnique.SPEED + (FugaTechnique.OVR_SPEED - FugaTechnique.SPEED) * chargePower;
    }

    private double effPathRadius() {
        return FugaTechnique.PATH_RADIUS + (FugaTechnique.OVR_PATH_RADIUS - FugaTechnique.PATH_RADIUS) * chargePower;
    }

    private double effIgniteRadius() {
        return FugaTechnique.IGNITE_RADIUS + (FugaTechnique.OVR_IGNITE_RADIUS - FugaTechnique.IGNITE_RADIUS) * chargePower;
    }

    private float effPathDamage() {
        return FugaTechnique.PATH_DAMAGE + (FugaTechnique.OVR_PATH_DAMAGE - FugaTechnique.PATH_DAMAGE) * chargePower;
    }

    private float effImpactDamage() {
        return FugaTechnique.IMPACT_DAMAGE + (FugaTechnique.OVR_IMPACT_DAMAGE - FugaTechnique.IMPACT_DAMAGE) * chargePower;
    }

    private double effImpactRadius() {
        return FugaTechnique.IMPACT_RADIUS + (FugaTechnique.OVR_IMPACT_RADIUS - FugaTechnique.IMPACT_RADIUS) * chargePower;
    }

    private float effKnockback() {
        return (float)(FugaTechnique.KNOCKBACK + (FugaTechnique.OVR_KNOCKBACK - FugaTechnique.KNOCKBACK) * chargePower);
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
