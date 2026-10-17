package com.sourceofmystery.entity;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import javax.annotation.Nullable;
import java.util.UUID;

/** A 2-second-charge, 50-damage homing orb that can hit only its hostile target. */
public class GuardianSpiritProjectile extends Projectile {
    private static final DustParticleOptions BLUE =
            new DustParticleOptions(new Vector3f(0.12F, 0.55F, 1.0F), 1.0F);
    @Nullable private UUID targetId;
    private int lifetime;

    public GuardianSpiritProjectile(EntityType<? extends Projectile> type, Level level) {
        super(type, level);
        setNoGravity(true);
    }

    public void setTarget(LivingEntity target) { targetId = target.getUUID(); }

    @Override
    protected void defineSynchedData() {}

    @Override
    public void tick() {
        super.tick();
        if (++lifetime > 100) {
            discard();
            return;
        }
        if (!(level() instanceof ServerLevel server)) return;
        Entity target = targetId == null ? null : server.getEntity(targetId);
        if (!(target instanceof LivingEntity living) || !living.isAlive()) {
            discard();
            return;
        }

        Vec3 toward = living.getEyePosition().subtract(position());
        if (toward.lengthSqr() > 0.001) {
            Vec3 desired = toward.normalize().scale(0.70);
            setDeltaMovement(getDeltaMovement().scale(0.28).add(desired.scale(0.72)));
        }
        HitResult hit = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);
        if (hit.getType() != HitResult.Type.MISS) {
            onHit(hit);
            if (isRemoved()) return;
        }
        move(MoverType.SELF, getDeltaMovement());
        hasImpulse = true;
        for (int i = 0; i < 4; i++) {
            double angle = (tickCount * 0.5) + i * Math.PI / 2.0;
            server.sendParticles(BLUE, getX() + Math.cos(angle) * 0.18, getY(),
                    getZ() + Math.sin(angle) * 0.18, 1, 0, 0, 0, 0);
        }
        server.sendParticles(ParticleTypes.END_ROD, getX(), getY(), getZ(),
                2, 0.12, 0.12, 0.12, 0);
    }

    @Override
    protected boolean canHitEntity(Entity entity) {
        return super.canHitEntity(entity) && targetId != null && targetId.equals(entity.getUUID());
    }

    @Override
    protected void onHitEntity(EntityHitResult hit) {
        super.onHitEntity(hit);
        Entity target = hit.getEntity();
        if (!level().isClientSide && target instanceof LivingEntity living && canHitEntity(target)) {
            living.hurt(damageSources().indirectMagic(this, getOwner()), 50.0F);
        }
        discard();
    }

    @Override
    protected void onHit(HitResult hit) {
        super.onHit(hit);
        if (hit.getType() == HitResult.Type.BLOCK) discard();
    }
}
