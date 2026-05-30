package com.createsentryarm.entity;

import com.createsentryarm.CreateSentryArmMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

public class FlameProjectileEntity extends ThrowableItemProjectile {
    private static final EntityDataAccessor<Float> DAMAGE = SynchedEntityData.defineId(FlameProjectileEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> SOUL = SynchedEntityData.defineId(FlameProjectileEntity.class, EntityDataSerializers.BOOLEAN);

    public FlameProjectileEntity(EntityType<? extends FlameProjectileEntity> type, Level level) {
        super(type, level);
    }

    public FlameProjectileEntity(LivingEntity owner, Level level) {
        super(CreateSentryArmMod.FLAME_PROJECTILE.get(), owner, level);
        this.setDamage(4.0F);
        this.setSoul(false);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DAMAGE, 4.0F);
        this.entityData.define(SOUL, false);
    }

    @Override
    public void tick() {
        super.tick();

        if (this.level().isClientSide) {
            if (this.getSoul()) {
                this.level().addParticle(ParticleTypes.SOUL_FIRE_FLAME,
                        this.getX(), this.getY(), this.getZ(),
                        0.0, 0.0, 0.0);
            } else {
                this.level().addParticle(ParticleTypes.FLAME,
                        this.getX(), this.getY(), this.getZ(),
                        0.0, 0.0, 0.0);
            }
        }

        if (this.tickCount > 60) {
            this.discard();
        }
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        super.onHitEntity(result);
        if (this.level().isClientSide) return;

        if (!(this.getOwner() instanceof LivingEntity owner)) return;
        if (!(result.getEntity() instanceof LivingEntity target)) return;
        if (target.fireImmune()) return;

        float damage = this.getDamage();
        if (target.hurt(this.damageSources().mobProjectile(this, owner), damage)) {
            target.setSecondsOnFire(5);
        }
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        super.onHitBlock(result);
        if (this.level().isClientSide) return;

        if (this.getSoul()) return;

        if (this.level().getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) {
            BlockPos pos = result.getBlockPos().relative(result.getDirection());
            if (this.level().isEmptyBlock(pos)) {
                this.level().setBlockAndUpdate(pos, BaseFireBlock.getState(this.level(), pos));
            }
        }
    }

    @Override
    protected void onHit(HitResult result) {
        super.onHit(result);
        if (!this.level().isClientSide) {
            this.discard();
        }
    }

    @Override
    protected Item getDefaultItem() {
        return Items.AIR;
    }

    @Override
    protected float getGravity() {
        return 0.0F;
    }

    @Override
    public boolean isOnFire() {
        return false;
    }

    public void setDamage(float damage) {
        this.entityData.set(DAMAGE, damage);
    }

    public float getDamage() {
        return this.entityData.get(DAMAGE);
    }

    public void setSoul(boolean soul) {
        this.entityData.set(SOUL, soul);
    }

    public boolean getSoul() {
        return this.entityData.get(SOUL);
    }
}
