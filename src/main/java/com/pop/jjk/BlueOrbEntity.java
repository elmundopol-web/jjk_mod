package com.pop.jjk;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

public class BlueOrbEntity extends Entity {

    private static final EntityDataAccessor<Float> POWER =
        SynchedEntityData.defineId(BlueOrbEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> LAUNCHED =
        SynchedEntityData.defineId(BlueOrbEntity.class, EntityDataSerializers.BOOLEAN);
    private static final int MAX_LIFETIME_TICKS = 600;

    public BlueOrbEntity(EntityType<? extends BlueOrbEntity> entityType, Level level) {
        super(entityType, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(POWER, 0.0F);
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
        this.setDeltaMovement(Vec3.ZERO);
        
        if (this.tickCount > MAX_LIFETIME_TICKS) {
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

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 4096.0;
    }

    public void syncVisualState(Vec3 position, double power, boolean launched) {
        this.setPos(position);
        this.setPower((float) power);
        this.setLaunched(launched);
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource damageSource, float amount) {
        return false;
    }

    public float getPower() {
        return this.entityData.get(POWER);
    }

    public boolean isLaunched() {
        return this.entityData.get(LAUNCHED);
    }

    private void setPower(float power) {
        this.entityData.set(POWER, power);
    }

    private void setLaunched(boolean launched) {
        this.entityData.set(LAUNCHED, launched);
    }
}
