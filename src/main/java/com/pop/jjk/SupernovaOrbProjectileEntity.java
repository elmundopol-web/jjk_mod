package com.pop.jjk;

import net.minecraft.core.BlockPos;
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
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.UUID;

/**
 * Pequeña esfera de sangre para Supernova.
 * Estados:
 * - ORBITING: orbita alrededor del owner mientras se mantiene la carga
 * - LAUNCHED: vuela hacia delante y explota al impactar
 */
public final class SupernovaOrbProjectileEntity extends Projectile {

    private static final double ORBIT_RADIUS = 1.35;
    private static final double ORBIT_BOB = 0.18;
    private static final double LAUNCH_SPEED = 2.2; // bloques/tick
    private static final double STEP_SEGMENT = 0.25; // para muestrear colisiones en vuelo
    private static final float  EXPLOSION_RADIUS = 3.0F;
    private static final float  DAMAGE = 8.0F;

    private UUID ownerUUID;
    private float baseAngleRad;
    private boolean launched;
    private int ageTicks;

    public SupernovaOrbProjectileEntity(EntityType<? extends SupernovaOrbProjectileEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
        this.launched = false;
        this.ageTicks = 0;
    }

    public SupernovaOrbProjectileEntity(ServerLevel level, ServerPlayer owner, float baseAngleRad) {
        this(JJKMod.SUPERNOVA_ORB_PROJECTILE, level);
        this.setOwner(owner);
        this.ownerUUID = owner.getUUID();
        this.baseAngleRad = baseAngleRad;
        // Posición inicial cerca del jugador (orbita en tick())
        Vec3 c = owner.position().add(0, owner.getBbHeight() * 0.5, 0);
        this.setPos(c);
        this.setOldPosAndRot();
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
    }

    @Override
    protected void readAdditionalSaveData(net.minecraft.world.level.storage.ValueInput input) {
    }

    @Override
    protected void addAdditionalSaveData(net.minecraft.world.level.storage.ValueOutput output) {
    }

    @Override
    public void tick() {
        super.tick();
        this.noPhysics = true;
        this.setNoGravity(true);
        ageTicks++;

        if (!(this.level() instanceof ServerLevel serverLevel)) return;

        if (!launched) {
            // ORBITAR
            ServerPlayer owner = serverLevel.getServer().getPlayerList().getPlayer(ownerUUID);
            if (owner == null || !owner.isAlive()) {
                this.discard();
                return;
            }
            double speed = SupernovaTechniqueHandler.getOrbitSpeed(ownerUUID);
            double angle = baseAngleRad + ageTicks * speed;
            double yBob = Math.sin(ageTicks * 0.18 + baseAngleRad) * ORBIT_BOB;
            Vec3 center = owner.position().add(0, owner.getBbHeight() * 0.5, 0);
            double x = center.x + Math.cos(angle) * ORBIT_RADIUS;
            double z = center.z + Math.sin(angle) * ORBIT_RADIUS;
            double y = center.y + yBob;
            this.setPos(x, y, z);

            // VFX: pequeño glow rojo + chispas
            serverLevel.sendParticles(JJKParticles.BLOOD_CORE, x, y, z, 1, 0.02, 0.02, 0.02, 0.0);
            if (ageTicks % 2 == 0) {
                serverLevel.sendParticles(JJKParticles.BLOOD_TRAIL, x, y, z, 1, 0.03, 0.03, 0.03, 0.0);
            }
            return;
        }

        // VUELO — colisión con bloques/entidades (muestreo en segmentos)
        Vec3 prev = this.position();
        Vec3 vel = this.getDeltaMovement();
        Vec3 next = prev.add(vel);

        int segments = Math.max(1, (int) Math.ceil(vel.length() / STEP_SEGMENT));
        Vec3 step = vel.scale(1.0 / segments);
        Vec3 p = prev;
        for (int i = 0; i < segments; i++) {
            p = p.add(step);
            // Bloques
            BlockPos bpos = BlockPos.containing(p);
            BlockState state = serverLevel.getBlockState(bpos);
            if (!state.isAir() && state.getCollisionShape(serverLevel, bpos) != net.minecraft.world.phys.shapes.Shapes.empty()) {
                explode(serverLevel, p);
                return;
            }
            // Entidades
            AABB aabb = new AABB(p, p).inflate(0.45);
            List<LivingEntity> entities = serverLevel.getEntitiesOfClass(LivingEntity.class, aabb,
                e -> e.isAlive() && e != this.getOwner());
            if (!entities.isEmpty()) {
                explode(serverLevel, p);
                return;
            }
        }

        // Mover y dejar rastro
        this.setPos(next);
        this.move(MoverType.SELF, Vec3.ZERO);
        serverLevel.sendParticles(JJKParticles.BLOOD_TRAIL, next.x, next.y, next.z, 1, 0.01, 0.01, 0.01, 0.0);
    }

    public void launch(Vec3 dir) {
        this.launched = true;
        this.setDeltaMovement(dir.normalize().scale(LAUNCH_SPEED));
        this.playLaunch();
    }

    private void playLaunch() {
        if (this.level() instanceof ServerLevel sl) {
            sl.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 0.35F, 1.9F);
        }
    }

    private void explode(ServerLevel level, Vec3 at) {
        // VFX denso y cinematográfico
        level.sendParticles(JJKParticles.BLOOD_EXPLOSION, at.x, at.y, at.z, 18, 0.8, 0.8, 0.8, 0.0);
        level.sendParticles(JJKParticles.BLOOD_EXPLOSION, at.x, at.y, at.z, 12, 1.4, 1.4, 1.4, 0.0);
        level.sendParticles(JJKParticles.BLOOD_CORE, at.x, at.y, at.z, 6, 0.2, 0.2, 0.2, 0.0);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 1.1F, 0.7F);

        // Daño en área
        AABB area = new AABB(at.x - EXPLOSION_RADIUS, at.y - EXPLOSION_RADIUS, at.z - EXPLOSION_RADIUS,
            at.x + EXPLOSION_RADIUS, at.y + EXPLOSION_RADIUS, at.z + EXPLOSION_RADIUS);
        List<LivingEntity> affected = level.getEntitiesOfClass(LivingEntity.class, area, e -> e.isAlive());
        for (LivingEntity le : affected) {
            if (this.getOwner() instanceof ServerPlayer sp) {
                le.hurtServer(level, level.damageSources().playerAttack(sp), DAMAGE);
            } else {
                le.hurtServer(level, level.damageSources().magic(), DAMAGE);
            }
        }

        // Notificar al handler para limpieza
        if (this.getOwner() instanceof ServerPlayer sp) {
            SupernovaTechniqueHandler.onOrbExploded(sp.getUUID(), this.getId());
        }
        this.discard();
    }
}
