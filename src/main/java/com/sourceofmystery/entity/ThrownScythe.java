package com.sourceofmystery.entity;

import com.sourceofmystery.network.ModNetwork;
import com.sourceofmystery.network.RootPacket;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
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
import net.minecraftforge.network.PacketDistributor;
import org.joml.Vector3f;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

import javax.annotation.Nullable;

/**
 * 泣死之主掷出的镰刀（招式「凋灵头」）：旋转着直线飞出。
 * 砸中生物：钉在原地并让对方无法移动（玩家由客户端锁定输入，其他生物给极高等级的缓慢）；
 * 没砸中：插在落点 / 停在空中。两种情况都要等凋灵头发射完，泣死之主调用 {@link #recall()} 才飞回她手中。
 */
public class ThrownScythe extends Projectile implements GeoEntity {

    private static final int MAX_FLIGHT_TICKS = 30;
    private static final int MAX_LIFE_TICKS = 400;
    private static final double RETURN_SPEED = 1.8;
    private static final double CATCH_DISTANCE = 2.0;
    private static final int ROOT_REFRESH = 20;
    private static final DustParticleOptions PURPLE = new DustParticleOptions(new Vector3f(0.55f, 0.15f, 0.9f), 1.4f);

    public static final int PHASE_FLYING = 0;
    public static final int PHASE_STUCK = 1;
    public static final int PHASE_RETURNING = 2;
    private static final EntityDataAccessor<Integer> DATA_PHASE =
            SynchedEntityData.defineId(ThrownScythe.class, EntityDataSerializers.INT);

    private int life;
    @Nullable
    private LivingEntity pinned;
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public ThrownScythe(EntityType<? extends ThrownScythe> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    public ThrownScythe(Level level, WeepingDeathLord owner, Vec3 start, Vec3 velocity) {
        this(ModEntities.THROWN_SCYTHE.get(), level);
        this.setOwner(owner);
        this.setPos(start);
        this.setDeltaMovement(velocity);
        faceVelocity(velocity, true);
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(DATA_PHASE, PHASE_FLYING);
    }

    public int phase() {
        return this.entityData.get(DATA_PHASE);
    }

    private void setPhase(int phase) {
        this.entityData.set(DATA_PHASE, phase);
    }

    /** 凋灵头发射完毕：飞回主人手中 */
    public void recall() {
        releasePinned();
        setPhase(PHASE_RETURNING);
        this.playSound(SoundEvents.TRIDENT_RETURN, 2.0f, 0.6f);
    }

    /** 解除定身 */
    public void releasePinned() {
        if (pinned != null) {
            if (pinned instanceof ServerPlayer sp) {
                ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> sp), new RootPacket(0));
            } else {
                pinned.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
            }
            pinned = null;
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide) {
            return;
        }
        life++;
        Entity owner = this.getOwner();
        if (!(owner instanceof WeepingDeathLord lord) || !owner.isAlive() || life > MAX_LIFE_TICKS) {
            if (owner instanceof WeepingDeathLord lord && owner.isAlive()) {
                lord.onScytheReturned();
            }
            releasePinned();
            this.discard();
            return;
        }
        ServerLevel level = (ServerLevel) this.level();
        switch (phase()) {
            case PHASE_FLYING -> tickFlying(level);
            case PHASE_STUCK -> tickStuck(level);
            default -> tickReturning(level, lord);
        }
        level.sendParticles(PURPLE, this.getX(), this.getY() + 0.5, this.getZ(), 2, 0.3, 0.3, 0.3, 0);
    }

    private void tickFlying(ServerLevel level) {
        Vec3 velocity = this.getDeltaMovement();
        Vec3 from = this.position();
        Vec3 to = from.add(velocity);
        HitResult blockHit = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        if (blockHit.getType() != HitResult.Type.MISS) {
            to = blockHit.getLocation();
        }
        EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(level, this, from, to,
                this.getBoundingBox().expandTowards(velocity).inflate(1.2), this::canPin);
        if (entityHit != null && entityHit.getEntity() instanceof LivingEntity victim) {
            pin(victim);
            this.setPos(victim.position().add(0, victim.getBbHeight() * 0.5, 0));
            stick();
            return;
        }
        this.setPos(to);
        if (blockHit.getType() != HitResult.Type.MISS || life > MAX_FLIGHT_TICKS) {
            stick();
        }
        if (life % 4 == 0) {
            this.playSound(SoundEvents.PLAYER_ATTACK_SWEEP, 1.5f, 0.5f);
        }
    }

    private void stick() {
        setPhase(PHASE_STUCK);
        this.setDeltaMovement(Vec3.ZERO);
        this.playSound(SoundEvents.TRIDENT_HIT_GROUND, 2.0f, 0.5f);
        if (this.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.CRIT, this.getX(), this.getY(), this.getZ(), 20, 0.4, 0.4, 0.4, 0.3);
        }
    }

    private void pin(LivingEntity victim) {
        pinned = victim;
        this.playSound(SoundEvents.TRIDENT_HIT, 2.0f, 0.5f);
        refreshRoot();
    }

    private void refreshRoot() {
        if (pinned instanceof ServerPlayer sp) {
            ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> sp), new RootPacket(ROOT_REFRESH + 5));
        } else if (pinned != null) {
            pinned.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ROOT_REFRESH + 5, 9, false, false));
        }
    }

    private void tickStuck(ServerLevel level) {
        if (pinned != null) {
            if (!pinned.isAlive() || pinned.isRemoved()) {
                releasePinned();
                return;
            }
            // 钉住的人留在原地（镰刀跟着他的位置，防止被别的东西推走后脱离）
            this.setPos(pinned.position().add(0, pinned.getBbHeight() * 0.5, 0));
            pinned.setDeltaMovement(0, Math.min(0, pinned.getDeltaMovement().y), 0);
            if (life % ROOT_REFRESH == 0) {
                refreshRoot();
            }
        }
    }

    private void tickReturning(ServerLevel level, WeepingDeathLord lord) {
        Vec3 toHand = lord.handPosition().subtract(this.position());
        if (toHand.length() < CATCH_DISTANCE) {
            lord.onScytheReturned();
            this.discard();
            return;
        }
        Vec3 velocity = toHand.normalize().scale(Math.min(RETURN_SPEED, toHand.length()));
        this.setPos(this.position().add(velocity));
        this.setDeltaMovement(velocity);
        faceVelocity(velocity, false);
    }

    private boolean canPin(Entity e) {
        return e != this.getOwner() && e instanceof LivingEntity living && living.isAlive() && !e.isSpectator()
                && !(e instanceof Player p && p.isCreative()) && !(e instanceof WeepingDeathLord);
    }

    private void faceVelocity(Vec3 velocity, boolean snap) {
        if (velocity.lengthSqr() < 1.0E-6) {
            return;
        }
        float yaw = (float) (Mth.atan2(velocity.x, velocity.z) * Mth.RAD_TO_DEG);
        if (snap) {
            this.yRotO = yaw;
        }
        this.setYRot(yaw);
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 128 * 128;
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        // 旋转由渲染器按 tickCount 计算，不需要动画
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }
}
