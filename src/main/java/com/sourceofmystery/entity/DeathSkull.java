package com.sourceofmystery.entity;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.WitherSkull;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * 泣死之主手上两个凋灵头发射的高速凋灵骷髅头：每发固定伤害并附带凋零，命中后只有爆炸特效，不破坏方块。
 * 外观沿用原版凋灵之首（WitherSkullRenderer）。
 */
public class DeathSkull extends WitherSkull {

    private static final double SPEED = 1.6;
    private static final double ACCELERATION = 0.12;
    private static final int MAX_LIFE = 80;

    private float damage = 10.0f;

    public DeathSkull(EntityType<? extends DeathSkull> type, Level level) {
        super(type, level);
    }

    public DeathSkull(Level level, LivingEntity owner, Vec3 start, Vec3 direction, float damage) {
        this(ModEntities.DEATH_SKULL.get(), level);
        this.setOwner(owner);
        this.moveTo(start.x, start.y, start.z, owner.getYRot(), owner.getXRot());
        Vec3 dir = direction.normalize();
        this.setDeltaMovement(dir.scale(SPEED));
        this.xPower = dir.x * ACCELERATION;
        this.yPower = dir.y * ACCELERATION;
        this.zPower = dir.z * ACCELERATION;
        this.damage = damage;
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.level().isClientSide && this.tickCount > MAX_LIFE) {
            this.discard();
        }
    }

    @Override
    protected boolean canHitEntity(Entity entity) {
        return super.canHitEntity(entity) && !(entity instanceof DeathSkull) && !(entity instanceof WeepingDeathLord);
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        if (this.level().isClientSide) {
            return;
        }
        Entity owner = this.getOwner();
        Entity victim = result.getEntity();
        if (victim.hurt(this.damageSources().witherSkull(this, owner), damage) && victim instanceof LivingEntity living) {
            living.addEffect(new MobEffectInstance(MobEffects.WITHER, 100, 1), owner);
        }
    }

    /** 不调用原版的爆炸，只有特效 */
    @Override
    protected void onHit(HitResult result) {
        if (result.getType() == HitResult.Type.ENTITY) {
            this.onHitEntity((EntityHitResult) result);
        } else if (result.getType() == HitResult.Type.BLOCK) {
            this.onHitBlock((BlockHitResult) result);
        }
        if (this.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.EXPLOSION, this.getX(), this.getY(), this.getZ(), 1, 0, 0, 0, 0);
            level.sendParticles(ParticleTypes.SMOKE, this.getX(), this.getY(), this.getZ(), 8, 0.2, 0.2, 0.2, 0.05);
            this.playSound(SoundEvents.GENERIC_EXPLODE, 0.6f, 1.5f);
            this.discard();
        }
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean hurt(net.minecraft.world.damagesource.DamageSource source, float amount) {
        return false;
    }
}
