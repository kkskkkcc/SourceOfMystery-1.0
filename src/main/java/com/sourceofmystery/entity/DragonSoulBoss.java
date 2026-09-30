package com.sourceofmystery.entity;

import com.sourceofmystery.item.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.DragonFireball;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.List;

/**
 * 龙魂 Boss：末影龙死亡后由其死亡位置召唤，凋零式飞行飘浮。
 * <p>
 * 数值：500 血、30 护甲；免疫药水、击退、火焰、凋零；只主动攻击玩家；索敌 100 格。
 * 魂类 Boss AI：瞬移前摇（蹲）→ 瞬移贴脸 → 出招（刺击/旋转劈/单劈）→ 后摇转枪 → 瞬移离开；
 * 龙息为远程，挑衅在拉开距离/低血量时触发。残血(&lt;200)切换战斗姿态。
 * 龙魂站在龙息里每秒回复 10 HP。死亡掉落【龙魂】。
 */
public class DragonSoulBoss extends Monster implements GeoEntity {

    // ==================== 动画 ====================
    private static final RawAnimation FLY = RawAnimation.begin().thenLoop("boss_fly");
    private static final RawAnimation COMBAT = RawAnimation.begin().thenLoop("boss_combat");
    private static final RawAnimation CROUCH_ANIM = RawAnimation.begin().thenPlay("boss_crouch");
    private static final RawAnimation CHARGE_THRUST_ANIM = RawAnimation.begin().thenPlay("boss_charge_thrust");
    private static final RawAnimation SWEEP_ANIM = RawAnimation.begin().thenPlay("boss_melee_sweep");
    private static final RawAnimation SINGLE_SLASH_ANIM = RawAnimation.begin().thenPlay("boss_ranged_spear_throw");
    private static final RawAnimation RANGED_BREATH = RawAnimation.begin().thenPlay("boss_ranged_breath");
    private static final RawAnimation FLOURISH_ANIM = RawAnimation.begin().thenPlay("boss_spear_flourish");
    private static final RawAnimation TAUNT_ANIM = RawAnimation.begin().thenPlay("boss_taunt");
    private static final String BASE_CONTROLLER = "base";
    private static final String ACTION_CONTROLLER = "action";
    private static final String TRIGGER_CROUCH = "crouch";
    private static final String TRIGGER_THRUST = "thrust";
    private static final String TRIGGER_SWEEP = "sweep";
    private static final String TRIGGER_SLASH = "slash";
    private static final String TRIGGER_RANGED = "ranged";
    private static final String TRIGGER_FLOURISH = "flourish";
    private static final String TRIGGER_TAUNT = "taunt";

    // ==================== 攻击参数 ====================
    private static final double TELEPORT_RANGE = 30.0;     // 瞬移触发范围（30 格）
    private static final double LEAVE_DISTANCE = 10.0;     // 攻击后瞬移离开距离
    private static final double FACE_OFFSET = 1.5;         // 瞬移贴脸落点（玩家脸上 1.5 格）

    private static final float THRUST_DAMAGE = 30.0f;      // 刺击伤害
    private static final float SWEEP_DAMAGE = 20.0f;       // 旋转劈伤害
    private static final float SLASH_DAMAGE = 25.0f;       // 单劈伤害

    private static final int CROUCH_TICKS = 22;            // 瞬移前摇（蹲）
    private static final int CROUCH_PARTICLES = 10;        // 前摇后半段出现粒子
    private static final int TELEPORT_PAUSE_TICKS = 4;     // 瞬移落地停顿（让客户端同步，再播动作）
    private static final int ATTACK_HIT = 4;               // 瞬移后出招时刻
    private static final int RECOVER_TICKS = 12;           // 收招后摇

    private static final int RANGED_WINDUP = 12;           // 龙息抬手前摇
    private static final int RANGED_BREATH_COUNT = 3;      // 龙息发数
    private static final double RANGED_SPREAD_DEG = 15.0;  // 扇形散弹角度

    private static final float DRAGON_BREATH_HEAL = 10.0f; // 站龙息里每秒回 10 HP
    private static final float LOW_HP_THRESHOLD = 200.0f;  // 残血阈值

    private static final int ATTACK_COOLDOWN = 50;         // 攻击冷却

    // ==================== AI 概率 ====================
    private static final double FLOURISH_CHANCE = 0.40;    // 攻击后转枪概率
    private static final double RAMPAGE_CHANCE = 0.30;     // 狂暴连击触发概率
    private static final int RAMPAGE_DURATION = 100;       // 狂暴持续 5 秒
    private static final int RAMPAGE_HIT_INTERVAL = 10;    // 每 10 tick 攻击一次（每秒 2 次）
    private static final float RAMPAGE_HIT_DAMAGE = 15.0f; // 狂暴单次伤害
    private static final int RAMPAGE_COOLDOWN = 600;       // 狂暴冷却 30 秒
    private static final int FLOURISH_TICKS = 20;
    private static final int TAUNT_TICKS = 30;
    private static final double TAUNT_FAR_DIST = 15.0;
    private static final double TAUNT_FAR_CHANCE = 0.20;
    private static final double TAUNT_LOW_HP_CHANCE = 0.10;

    private enum AttackState { NONE, CROUCH, TELEPORT_IN, ATTACK, TELEPORT_OUT, FLOURISH, TAUNT, RANGED, RAMPAGE }
    private enum AttackType { CHARGE_THRUST, SWEEP, SINGLE_SLASH, RANGED }

    private AttackState attackState = AttackState.NONE;
    private AttackType pendingAttackType = AttackType.CHARGE_THRUST;
    private boolean pendingFlourish = false;
    private int rampageCooldown = 0;
    private int attackTick = 0;
    private int attackCooldown = ATTACK_COOLDOWN;
    private int tickCounter = 0;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final ServerBossEvent bossEvent;

    public DragonSoulBoss(EntityType<? extends DragonSoulBoss> type, Level level) {
        super(type, level);
        this.xpReward = 500;
        Component name = Component.translatable("entity.sourceofmystery.dragon_soul")
                .withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD);
        this.setCustomName(name);
        this.setCustomNameVisible(true);

        this.moveControl = new FlyingMoveControl(this, 10, true);
        this.navigation = new FlyingPathNavigation(this, level);
        this.setNoGravity(true);

        this.bossEvent = new ServerBossEvent(name, BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.PROGRESS);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.ATTACK_DAMAGE, THRUST_DAMAGE)
                .add(Attributes.ARMOR, 30.0)
                .add(Attributes.MAX_HEALTH, 500.0)
                .add(Attributes.MOVEMENT_SPEED, 0.28)
                .add(Attributes.FLYING_SPEED, 0.4)
                .add(Attributes.FOLLOW_RANGE, 100.0);
    }

    @Override
    protected void registerGoals() {
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));

        this.goalSelector.addGoal(1, new LookAtPlayerGoal(this, Player.class, 30.0F));
        this.goalSelector.addGoal(2, new RandomLookAroundGoal(this));
    }

    /**
     * 攻击流程（蹲/瞬移/出招/后摇/挑衅）期间不移动，避免 FlyingMoveControl 把瞬移后的 Boss 又拽走
     */
    @Override
    public void travel(Vec3 travelVector) {
        if (attackState == AttackState.NONE) {
            super.travel(travelVector);
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide) {
            return;
        }
        tickCounter++;
        this.bossEvent.setProgress(this.getHealth() / this.getMaxHealth());

        if (tickCounter % 20 == 0) {
            healFromDragonBreath();
        }

        if (rampageCooldown > 0) {
            rampageCooldown--;
        }

        this.tickCombat((ServerLevel) this.level());
    }

    // ==================== 战斗 AI ====================

    private void tickCombat(ServerLevel level) {
        if (attackCooldown > 0) {
            attackCooldown--;
        }

        if (attackState == AttackState.NONE) {
            if (attackCooldown > 0) {
                return;
            }
            LivingEntity target = this.getTarget();
            if (target == null || !target.isAlive()) {
                attackCooldown = 20;
                return;
            }

            double dist = this.distanceTo(target);
            double healthRatio = this.getHealth() / this.getMaxHealth();

            if (dist > TELEPORT_RANGE) {
                if (this.random.nextDouble() < 0.5) {
                    beginRanged();
                } else {
                    beginTaunt();
                }
            } else if (shouldTaunt(dist, healthRatio)) {
                beginTaunt();
            } else {
                AttackType type = chooseAttack(dist);
                if (type == AttackType.RANGED) {
                    beginRanged();
                } else {
                    beginCrouch(type);
                }
            }
            return;
        }

        attackTick++;
        switch (attackState) {
            case CROUCH -> tickCrouch(level);
            case TELEPORT_IN -> tickTeleport();
            case TELEPORT_OUT -> tickTeleportOut();
            case ATTACK -> tickAttack(level);
            case FLOURISH -> tickFlourish();
            case TAUNT -> tickTaunt(level);
            case RANGED -> tickRanged(level);
            case RAMPAGE -> tickRampage(level);
            default -> endAttack();
        }
    }

    /**
     * 按距离加权随机：贴脸多用旋转劈（范围），中远以刺击为主
     */
    private AttackType chooseAttack(double dist) {
        double r = this.random.nextDouble();
        if (dist < 4.0) {
            if (r < 0.30) return AttackType.CHARGE_THRUST;
            else if (r < 0.65) return AttackType.SWEEP;
            else if (r < 0.90) return AttackType.SINGLE_SLASH;
            else return AttackType.RANGED;
        } else {
            if (r < 0.50) return AttackType.CHARGE_THRUST;
            else if (r < 0.70) return AttackType.SWEEP;
            else if (r < 0.85) return AttackType.SINGLE_SLASH;
            else return AttackType.RANGED;
        }
    }

    private boolean shouldTaunt(double dist, double healthRatio) {
        if (dist > TAUNT_FAR_DIST && this.random.nextDouble() < TAUNT_FAR_CHANCE) {
            return true;
        }
        if (healthRatio < 0.5 && this.random.nextDouble() < TAUNT_LOW_HP_CHANCE) {
            return true;
        }
        return false;
    }

    private void beginCrouch(AttackType type) {
        this.pendingAttackType = type;
        attackState = AttackState.CROUCH;
        attackTick = 0;
        this.triggerAnim(ACTION_CONTROLLER, TRIGGER_CROUCH);
    }

    private void beginRanged() {
        attackState = AttackState.RANGED;
        attackTick = 0;
        this.triggerAnim(ACTION_CONTROLLER, TRIGGER_RANGED);
    }

    private void beginTaunt() {
        attackState = AttackState.TAUNT;
        attackTick = 0;
        this.getNavigation().stop();
        this.triggerAnim(ACTION_CONTROLLER, TRIGGER_TAUNT);
    }

    private void beginFlourish() {
        attackState = AttackState.FLOURISH;
        attackTick = 0;
        this.getNavigation().stop();
        this.triggerAnim(ACTION_CONTROLLER, TRIGGER_FLOURISH);
    }

    private void tickCrouch(ServerLevel level) {
        if (attackTick >= CROUCH_PARTICLES) {
            spawnCrouchParticles(level);
        }

        if (attackTick >= CROUCH_TICKS) {
            teleportToFace();
            // 瞬移后先停顿落地，不立即播放攻击动画，避免瞬移和动画叠在一起导致卡顿
            attackState = AttackState.TELEPORT_IN;
            attackTick = 0;
        }
    }

    /**
     * 瞬移落地停顿：等客户端同步完瞬移位置后，再播放攻击动画
     */
    private void tickTeleport() {
        if (attackTick >= TELEPORT_PAUSE_TICKS) {
            attackState = AttackState.ATTACK;
            attackTick = 0;
            switch (pendingAttackType) {
                case CHARGE_THRUST -> this.triggerAnim(ACTION_CONTROLLER, TRIGGER_THRUST);
                case SWEEP -> this.triggerAnim(ACTION_CONTROLLER, TRIGGER_SWEEP);
                case SINGLE_SLASH -> this.triggerAnim(ACTION_CONTROLLER, TRIGGER_SLASH);
            }
        }
    }

    private void tickAttack(ServerLevel level) {
        LivingEntity target = this.getTarget();
        if (target == null || !target.isAlive()) {
            endAttack();
            return;
        }

        this.lookAt(target, 360.0f, 360.0f);

        if (attackTick == ATTACK_HIT) {
            float damage = switch (pendingAttackType) {
                case CHARGE_THRUST -> THRUST_DAMAGE;
                case SWEEP -> SWEEP_DAMAGE;
                case SINGLE_SLASH -> SLASH_DAMAGE;
                default -> 0.0f;
            };
            target.hurt(this.damageSources().mobAttack(this), damage);
        }

        if (attackTick >= attackTotalTicks(pendingAttackType)) {
            // 每次攻击后 30% 概率触发狂暴连击（30 秒冷却）
            if (rampageCooldown <= 0 && this.random.nextDouble() < RAMPAGE_CHANCE) {
                beginRampage();
            } else {
                teleportAway(target);
                // 瞬移离开后先停顿，再决定是否转枪（避免瞬移和转枪动画叠在一起）
                pendingFlourish = this.random.nextDouble() < FLOURISH_CHANCE;
                attackState = AttackState.TELEPORT_OUT;
                attackTick = 0;
            }
        }
    }

    /**
     * 瞬移离开落地停顿：等客户端同步完位置，再转枪后摇或结束
     */
    private void tickTeleportOut() {
        if (attackTick >= TELEPORT_PAUSE_TICKS) {
            if (pendingFlourish) {
                beginFlourish();
            } else {
                endAttack();
            }
        }
    }

    /**
     * 狂暴连击：贴身持续攻击 5 秒，每秒 2 次，期间无敌
     */
    private void beginRampage() {
        attackState = AttackState.RAMPAGE;
        attackTick = 0;
        // 狂暴期间玩家失明，持续整个机制
        LivingEntity target = this.getTarget();
        if (target != null) {
            target.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, RAMPAGE_DURATION, 0));
        }
    }

    private void tickRampage(ServerLevel level) {
        LivingEntity target = this.getTarget();
        if (target == null || !target.isAlive()) {
            endRampage();
            return;
        }

        // 每 10 tick 攻击一次（每秒 2 次），先瞬移贴身再出招
        if (attackTick % RAMPAGE_HIT_INTERVAL == 0) {
            Vec3 dir = horizontalDir(target.position().subtract(this.position()));
            this.teleportTo(target.getX() - dir.x * FACE_OFFSET, target.getY(), target.getZ() - dir.z * FACE_OFFSET);
            this.setDeltaMovement(Vec3.ZERO);
            this.triggerAnim(ACTION_CONTROLLER, TRIGGER_THRUST);
            target.hurt(this.damageSources().mobAttack(this), RAMPAGE_HIT_DAMAGE);
        }

        if (attackTick >= RAMPAGE_DURATION) {
            endRampage();
        }
    }

    private void endRampage() {
        attackState = AttackState.NONE;
        attackTick = 0;
        attackCooldown = ATTACK_COOLDOWN;
        rampageCooldown = RAMPAGE_COOLDOWN;
    }

    private void tickFlourish() {
        if (attackTick >= FLOURISH_TICKS) {
            endAttack();
        }
    }

    private void tickTaunt(ServerLevel level) {
        if (attackTick == 5) {
            this.playSound(SoundEvents.ENDER_DRAGON_GROWL, 2.5f, 0.7f);
        }
        if (attackTick >= TAUNT_TICKS) {
            endAttack();
        }
    }

    private void tickRanged(ServerLevel level) {
        LivingEntity target = this.getTarget();
        if (target == null || !target.isAlive()) {
            endAttack();
            return;
        }

        this.lookAt(target, 360.0f, 360.0f);

        if (attackTick == RANGED_WINDUP) {
            fireDragonBreath(level, target);
        }
        if (attackTick >= RANGED_WINDUP + 20) {
            endAttack();
        }
    }

    private void endAttack() {
        attackState = AttackState.NONE;
        attackTick = 0;
        attackCooldown = ATTACK_COOLDOWN;
    }

    private int attackTotalTicks(AttackType type) {
        int animTicks = switch (type) {
            case CHARGE_THRUST -> 22;
            case SWEEP -> 26;
            case SINGLE_SLASH -> 24;
            default -> 20;
        };
        return animTicks + RECOVER_TICKS;
    }

    // ==================== 瞬移 ====================

    private void teleportToFace() {
        LivingEntity target = this.getTarget();
        if (target != null) {
            Vec3 dir = horizontalDir(target.position().subtract(this.position()));
            this.teleportTo(target.getX() - dir.x * FACE_OFFSET, target.getY(), target.getZ() - dir.z * FACE_OFFSET);
        }
        this.setDeltaMovement(Vec3.ZERO);
        spawnTeleportEffects();
    }

    private void teleportAway(LivingEntity target) {
        Vec3 awayDir = horizontalDir(this.position().subtract(target.position()));
        this.teleportTo(target.getX() + awayDir.x * LEAVE_DISTANCE, target.getY(), target.getZ() + awayDir.z * LEAVE_DISTANCE);
        this.setDeltaMovement(Vec3.ZERO);
        spawnTeleportEffects();
    }

    private void spawnCrouchParticles(ServerLevel level) {
        level.sendParticles(ParticleTypes.PORTAL,
                this.getX(), this.getY() + 1.0, this.getZ(),
                12, 0.6, 1.0, 0.6, 0.12);
        level.sendParticles(ParticleTypes.DRAGON_BREATH,
                this.getX(), this.getY() + 0.5, this.getZ(),
                6, 0.5, 0.5, 0.5, 0.02);
    }

    private void spawnTeleportEffects() {
        if (!(this.level() instanceof ServerLevel level)) {
            return;
        }
        this.playSound(SoundEvents.ENDER_DRAGON_AMBIENT, 2.0f, 1.0f);
        level.sendParticles(ParticleTypes.PORTAL,
                this.getX(), this.getY() + 1.0, this.getZ(),
                60, 0.6, 1.2, 0.6, 0.15);
        level.sendParticles(ParticleTypes.DRAGON_BREATH,
                this.getX(), this.getY() + 0.5, this.getZ(),
                20, 0.5, 0.5, 0.5, 0.02);
    }

    private Vec3 horizontalDir(Vec3 v) {
        Vec3 h = new Vec3(v.x, 0, v.z);
        if (h.lengthSqr() < 1.0E-4) {
            h = new Vec3(this.getViewVector(1.0F).x, 0, this.getViewVector(1.0F).z);
        }
        return h.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : h.normalize();
    }

    // ==================== 远程龙息 ====================

    private void fireDragonBreath(ServerLevel level, LivingEntity target) {
        Vec3 eye = this.getEyePosition();
        Vec3 toTarget = target.getEyePosition().subtract(eye);
        if (toTarget.lengthSqr() < 1.0E-4) {
            toTarget = this.getViewVector(1.0F);
        }
        Vec3 baseDir = toTarget.normalize();

        int half = (RANGED_BREATH_COUNT - 1) / 2;
        for (int i = -half; i <= half; i++) {
            Vec3 dir = rotateY(baseDir, i * RANGED_SPREAD_DEG);
            DragonFireball fireball = new DragonFireball(level, this, dir.x * 0.1, dir.y * 0.1, dir.z * 0.1);
            fireball.setPos(eye.x, eye.y - 0.5, eye.z);
            level.addFreshEntity(fireball);
        }
        this.playSound(SoundEvents.ENDER_DRAGON_SHOOT, 1.5f, 1.0f);
    }

    private static Vec3 rotateY(Vec3 v, double angleDeg) {
        double rad = Math.toRadians(angleDeg);
        double cos = Math.cos(rad);
        double sin = Math.sin(rad);
        return new Vec3(v.x * cos + v.z * sin, v.y, -v.x * sin + v.z * cos);
    }

    // ==================== 龙息治疗 ====================

    private void healFromDragonBreath() {
        if (this.getHealth() >= this.getMaxHealth()) {
            return;
        }
        ServerLevel level = (ServerLevel) this.level();
        List<AreaEffectCloud> clouds = level.getEntitiesOfClass(AreaEffectCloud.class,
                this.getBoundingBox().inflate(0.5));
        for (AreaEffectCloud cloud : clouds) {
            if (cloud.getParticle() == ParticleTypes.DRAGON_BREATH) {
                this.heal(DRAGON_BREATH_HEAL);
                break;
            }
        }
    }

    // ==================== 免疫 ====================

    @Override
    public boolean canBeAffected(MobEffectInstance effect) {
        return false;
    }

    /**
     * 狂暴连击期间完全无敌
     */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (attackState == AttackState.RAMPAGE) {
            return false;
        }
        return super.hurt(source, amount);
    }

    @Override
    public void knockback(double strength, double x, double z) {
        // 龙魂不会被击退
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean fireImmune() {
        return true;
    }

    @Override
    public boolean isInvulnerableTo(DamageSource damageSource) {
        if (damageSource.is(DamageTypes.WITHER) || damageSource.is(DamageTypes.WITHER_SKULL)) {
            return true;
        }
        return super.isInvulnerableTo(damageSource);
    }

    // ==================== 血条 ====================

    @Override
    public void startSeenByPlayer(ServerPlayer player) {
        super.startSeenByPlayer(player);
        this.bossEvent.addPlayer(player);
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        this.bossEvent.removePlayer(player);
    }

    @Override
    public void die(DamageSource damageSource) {
        this.bossEvent.removeAllPlayers();
        super.die(damageSource);
    }

    @Override
    protected void dropCustomDeathLoot(DamageSource source, int looting, boolean recentlyHit) {
        super.dropCustomDeathLoot(source, looting, recentlyHit);
        this.spawnAtLocation(new ItemStack(ModItems.DRAGON_SOUL.get()));
    }

    @Override
    public boolean isPersistenceRequired() {
        return true;
    }

    @Override
    public boolean canChangeDimensions() {
        return false;
    }

    @Override
    public boolean canBeSeenAsEnemy() {
        return false;
    }

    // ==================== GeckoLib 动画 ====================

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        // base：残血(<200)战斗姿态，否则飞行悬浮
        controllers.add(new AnimationController<>(this, BASE_CONTROLLER, 5, state -> {
            if (this.getHealth() < LOW_HP_THRESHOLD) {
                return state.setAndContinue(COMBAT);
            }
            return state.setAndContinue(FLY);
        }));
        // action：一次性动作
        controllers.add(new AnimationController<>(this, ACTION_CONTROLLER, 0, state -> PlayState.STOP)
                .triggerableAnim(TRIGGER_CROUCH, CROUCH_ANIM)
                .triggerableAnim(TRIGGER_THRUST, CHARGE_THRUST_ANIM)
                .triggerableAnim(TRIGGER_SWEEP, SWEEP_ANIM)
                .triggerableAnim(TRIGGER_SLASH, SINGLE_SLASH_ANIM)
                .triggerableAnim(TRIGGER_RANGED, RANGED_BREATH)
                .triggerableAnim(TRIGGER_FLOURISH, FLOURISH_ANIM)
                .triggerableAnim(TRIGGER_TAUNT, TAUNT_ANIM));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }
}
