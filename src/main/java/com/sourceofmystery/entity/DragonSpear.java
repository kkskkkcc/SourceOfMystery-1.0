package com.sourceofmystery.entity;

import com.sourceofmystery.sound.ModSounds;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * 龙魂掷出的长枪：直线飞出，命中第一个实体（或撞墙、飞够距离）后掉头飞回龙魂手中。
 * 位置全部由服务端计算后同步，客户端只负责渲染（DragonSpearRenderer）。
 */
public class DragonSpear extends Projectile {

    private static final int MAX_OUTBOUND_TICKS = 30;
    private static final int MAX_LIFE_TICKS = 160;
    private static final double RETURN_SPEED = 1.6;
    private static final double CATCH_DISTANCE = 1.8;
    private static final float DAMAGE = 18.0f;

    private static final EntityDataAccessor<Boolean> DATA_RETURNING =
            SynchedEntityData.defineId(DragonSpear.class, EntityDataSerializers.BOOLEAN);

    private int life;
    private boolean returning;

    public DragonSpear(EntityType<? extends DragonSpear> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    public DragonSpear(Level level, DragonSoulBoss owner, Vec3 start, Vec3 velocity) {
        this(ModEntities.DRAGON_SPEAR.get(), level);
        this.setOwner(owner);
        this.setPos(start);
        this.setDeltaMovement(velocity);
        this.faceVelocity(velocity, true);
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(DATA_RETURNING, false);
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide) {
            return;
        }
        life++;
        Entity owner = this.getOwner();
        if (!(owner instanceof DragonSoulBoss boss) || !owner.isAlive() || life > MAX_LIFE_TICKS) {
            if (owner instanceof DragonSoulBoss boss && owner.isAlive()) {
                boss.onSpearReturned();
            }
            this.discard();
            return;
        }

        Vec3 velocity = this.getDeltaMovement();
        if (!returning) {
            Vec3 from = this.position();
            Vec3 to = from.add(velocity);
            HitResult blockHit = this.level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
            if (blockHit.getType() != HitResult.Type.MISS) {
                to = blockHit.getLocation();
            }
            EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(this.level(), this, from, to,
                    this.getBoundingBox().expandTowards(velocity).inflate(1.0), e -> canHit(e, owner));
            if (entityHit != null) {
                Entity victim = entityHit.getEntity();
                victim.hurt(this.damageSources().trident(this, owner), DAMAGE);
                this.playSound(ModSounds.DRAGON_SOUL_HIT.get(), 1.5f, 1.0f);
                this.setPos(entityHit.getLocation());
                returning = true;
            } else {
                this.setPos(to);
                if (blockHit.getType() != HitResult.Type.MISS || life > MAX_OUTBOUND_TICKS) {
                    returning = true;
                }
            }
        }
        if (returning) {
            this.entityData.set(DATA_RETURNING, true);
            Vec3 hand = boss.handPosition();
            Vec3 toHand = hand.subtract(this.position());
            if (toHand.length() < CATCH_DISTANCE) {
                boss.onSpearReturned();
                this.discard();
                return;
            }
            velocity = toHand.normalize().scale(Math.min(RETURN_SPEED, toHand.length()));
            this.setPos(this.position().add(velocity));
        }
        this.setDeltaMovement(velocity);
        this.faceVelocity(velocity, false);

        ServerLevel level = (ServerLevel) this.level();
        level.sendParticles(ParticleTypes.DRAGON_BREATH, this.getX(), this.getY(), this.getZ(), 2, 0.1, 0.1, 0.1, 0.01);
        level.sendParticles(ParticleTypes.END_ROD, this.getX(), this.getY(), this.getZ(), 1, 0.05, 0.05, 0.05, 0.0);
    }

    private boolean canHit(Entity entity, Entity owner) {
        return entity != owner && entity instanceof LivingEntity living && living.isAlive() && !entity.isSpectator()
                && !(entity instanceof Player player && player.isCreative());
    }

    private void faceVelocity(Vec3 velocity, boolean snap) {
        if (velocity.lengthSqr() < 1.0E-6) {
            return;
        }
        float yaw = (float) (Mth.atan2(velocity.x, velocity.z) * Mth.RAD_TO_DEG);
        float pitch = (float) (Mth.atan2(velocity.y, velocity.horizontalDistance()) * Mth.RAD_TO_DEG);
        if (snap) {
            this.yRotO = yaw;
            this.xRotO = pitch;
        }
        this.setYRot(yaw);
        this.setXRot(pitch);
    }

    public boolean isReturning() {
        return this.entityData.get(DATA_RETURNING);
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 128 * 128;
    }
}
