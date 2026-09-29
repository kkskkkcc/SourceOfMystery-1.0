package com.sourceofmystery.entity;

import com.sourceofmystery.SourceOfMystery;
import com.sourceofmystery.config.MysteryConfig;
import com.sourceofmystery.item.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.BossEvent;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class DivineHeavenlyDaoBoss extends Monster implements GeoEntity {

    private static final String ANIM_PREFIX = "animation.divine_heavenly_dao_boss.";
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop(ANIM_PREFIX + "idle");
    private static final RawAnimation ATTACK = RawAnimation.begin().thenPlay(ANIM_PREFIX + "attack");
    private static final RawAnimation THUNDER = RawAnimation.begin().thenPlay(ANIM_PREFIX + "thunder_strike");
    private static final String BASE_CONTROLLER = "base";
    private static final String ACTION_CONTROLLER = "action";
    private static final String TRIGGER_ATTACK = "attack";
    private static final String TRIGGER_THUNDER = "thunder_strike";

    private static final String TAG_SECOND_PHASE = "SecondPhase";
    private static final String TAG_SUMMONED_WITHERS = "SummonedWithers";

    /** 二阶段召唤的凋灵带有此标签：不掉落物品和经验（它们原先是无敌的，不会掉任何东西） */
    public static final String MINION_TAG = SourceOfMystery.MOD_ID + ".boss_minion";

    private static final float SECOND_PHASE_HEAL_PER_SECOND = 10.0f;
    private static final float KILL_HEAL = 1000.0f;
    private static final int LIGHTNING_INTERVAL = 200; // 10 秒
    private static final double LIGHTNING_RANGE = 10.0;
    private static final double ENDERMAN_RANGE = 20.0;

    // ==================== 技能（均有前摇，可以躲避） ====================
    private static final int SKILL_COOLDOWN = 100; // 两次技能之间 5 秒
    // 天罚雷印：标记目标脚下，前摇结束后在标记处落雷
    private static final int JUDGEMENT_WINDUP = 30;
    private static final double JUDGEMENT_RADIUS = 3.0;
    private static final float JUDGEMENT_DAMAGE = 30.0f;
    // 雷霆冲撞：蓄力后朝目标方向直线冲刺
    private static final double CHARGE_MIN_RANGE = 8.0;
    private static final double CHARGE_MAX_RANGE = 40.0;
    private static final int CHARGE_WINDUP = 20;
    private static final int CHARGE_DURATION = 15;
    private static final double CHARGE_SPEED = 1.4;
    private static final float CHARGE_DAMAGE = 40.0f;
    // 雷环震荡：目标贴身时蓄力，向四周释放冲击波并击退
    private static final double NOVA_TRIGGER_RANGE = 5.0;
    private static final int NOVA_WINDUP = 20;
    private static final double NOVA_RADIUS = 7.0;
    private static final float NOVA_DAMAGE = 20.0f;
    private static final double NOVA_KNOCKBACK = 2.5;

    private enum Skill { NONE, JUDGEMENT, CHARGE, NOVA }

    private int tickCounter = 0;
    private boolean secondPhaseTriggered = false;
    // 二阶段召唤的凋灵：存活期间 Boss 受到的伤害降低；Boss 死亡/消失时一并清理
    private final List<UUID> summonedWithers = new ArrayList<>();

    private Skill activeSkill = Skill.NONE;
    private int skillTicks = 0;
    private int skillCooldown = SKILL_COOLDOWN;
    private Vec3 skillTargetPos = Vec3.ZERO;
    private Vec3 chargeDirection = Vec3.ZERO;
    private final Set<Integer> chargeHits = new HashSet<>();

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    private final ServerBossEvent bossEvent;

    public DivineHeavenlyDaoBoss(EntityType<? extends DivineHeavenlyDaoBoss> type, Level level) {
        super(type, level);
        this.xpReward = 10000;
        Component name = Component.translatable("entity.sourceofmystery.divine_heavenly_dao_boss")
                .withStyle(ChatFormatting.RED, ChatFormatting.BOLD);
        this.setCustomName(name);
        this.setCustomNameVisible(true);

        // 飞行移动控制：像凋零一样浮空飞行，而不是在地上跑
        this.moveControl = new FlyingMoveControl(this, 10, true);
        this.navigation = new FlyingPathNavigation(this, level);
        this.setNoGravity(true);

        this.bossEvent = new ServerBossEvent(name, BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.PROGRESS);
    }

    public static AttributeSupplier.Builder createAttributes() {
        // FlyingMoveControl 需要 FLYING_SPEED 属性（否则报 Can't find attribute flying_speed）
        return Monster.createMonsterAttributes()
                .add(Attributes.ATTACK_DAMAGE, 50.0)
                .add(Attributes.ARMOR, 50.0)
                .add(Attributes.ARMOR_TOUGHNESS, 20.0)
                .add(Attributes.MAX_HEALTH, 5000.0)
                .add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.FLYING_SPEED, 0.4)
                .add(Attributes.FOLLOW_RANGE, 100.0);
    }

    /**
     * 生成时按配置文件设置生命上限（属性在注册时就固定了，读不到配置）
     */
    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType reason,
                                        @Nullable SpawnGroupData spawnData, @Nullable CompoundTag tag) {
        SpawnGroupData data = super.finalizeSpawn(level, difficulty, reason, spawnData, tag);
        var maxHealth = this.getAttribute(Attributes.MAX_HEALTH);
        if (maxHealth != null) {
            maxHealth.setBaseValue(MysteryConfig.BOSS_MAX_HEALTH.get());
            this.setHealth(this.getMaxHealth());
        }
        return data;
    }

    @Override
    protected void registerGoals() {
        // 攻击目标：玩家最高优先级，其次锁定所有其他 LivingEntity
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
        this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, LivingEntity.class, 10, true, false,
                e -> !(e instanceof Player)));

        this.goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.0, false));
        this.goalSelector.addGoal(2, new LookAtPlayerGoal(this, Player.class, 30.0F));
        this.goalSelector.addGoal(3, new RandomLookAroundGoal(this));
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide) {
            return;
        }
        tickCounter++;

        this.bossEvent.setProgress(this.getHealth() / this.getMaxHealth());

        // 每 10 秒召唤一次闪电攻击周围 10 格内的所有实体（除自己外）
        if (tickCounter % LIGHTNING_INTERVAL == 0) {
            lightningAttack();
        }

        // 每秒检测一次：20 格范围内若有末影人，则让它们愤怒攻击玩家；并清理已死亡的凋灵
        if (tickCounter % 20 == 0) {
            angerEndermen();
            refreshMinions();
        }

        tickSkills((ServerLevel) this.level());

        if (!secondPhaseTriggered && this.getHealth() < MysteryConfig.BOSS_SECOND_PHASE_HEALTH.get()) {
            enterSecondPhase();
        }

        // 第二阶段：每秒回血
        if (secondPhaseTriggered && tickCounter % 20 == 0) {
            this.heal(SECOND_PHASE_HEAL_PER_SECOND);
        }
    }

    /**
     * 末影人协同：若 Boss 20 格范围内存在末影人，则让它们全部愤怒攻击玩家
     */
    private void angerEndermen() {
        ServerLevel serverLevel = (ServerLevel) this.level();
        double rangeSq = ENDERMAN_RANGE * ENDERMAN_RANGE;
        List<EnderMan> endermen = serverLevel.getEntitiesOfClass(EnderMan.class,
                new AABB(this.blockPosition()).inflate(ENDERMAN_RANGE),
                e -> e.isAlive() && e.distanceToSqr(this) <= rangeSq);
        if (endermen.isEmpty()) {
            return;
        }

        // 优先 Boss 当前锁定的玩家，否则找最近的玩家
        Player targetPlayer = this.getTarget() instanceof Player player
                ? player
                : serverLevel.getNearestPlayer(this.getX(), this.getY(), this.getZ(), ENDERMAN_RANGE * 2, true);
        if (targetPlayer == null || !targetPlayer.isAlive()) {
            return;
        }

        for (EnderMan enderman : endermen) {
            enderman.setTarget(targetPlayer);
        }
    }

    /**
     * 闪电攻击：对周围 10 格范围内的所有实体（除自己外）各落一道原版闪电
     */
    private void lightningAttack() {
        ServerLevel serverLevel = (ServerLevel) this.level();

        // 触发闪电施法动画（GeckoLib 会同步到客户端）
        this.triggerAnim(ACTION_CONTROLLER, TRIGGER_THUNDER);

        double rangeSq = LIGHTNING_RANGE * LIGHTNING_RANGE;
        List<LivingEntity> targets = serverLevel.getEntitiesOfClass(LivingEntity.class,
                new AABB(this.blockPosition()).inflate(LIGHTNING_RANGE),
                e -> e != this && e.isAlive() && e.distanceToSqr(this) <= rangeSq
                        && !(e instanceof Player p && (p.isCreative() || p.isSpectator())));

        for (LivingEntity target : targets) {
            LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(serverLevel);
            if (bolt != null) {
                bolt.moveTo(target.getX(), target.getY(), target.getZ());
                serverLevel.addFreshEntity(bolt);
            }
        }
    }

    // ==================== 技能 ====================

    private void tickSkills(ServerLevel level) {
        if (activeSkill == Skill.NONE) {
            if (--skillCooldown > 0) {
                return;
            }
            LivingEntity target = this.getTarget();
            if (target == null || !target.isAlive()) {
                skillCooldown = 20;
                return;
            }
            double distance = this.distanceTo(target);
            if (distance <= NOVA_TRIGGER_RANGE) {
                startSkill(Skill.NOVA, TRIGGER_THUNDER);
            } else if (distance >= CHARGE_MIN_RANGE && distance <= CHARGE_MAX_RANGE && this.random.nextBoolean()) {
                startSkill(Skill.CHARGE, null);
            } else {
                skillTargetPos = target.position();
                startSkill(Skill.JUDGEMENT, TRIGGER_THUNDER);
            }
            return;
        }

        skillTicks++;
        switch (activeSkill) {
            case JUDGEMENT -> tickJudgement(level);
            case CHARGE -> tickCharge(level);
            case NOVA -> tickNova(level);
            default -> endSkill();
        }
    }

    private void startSkill(Skill skill, @Nullable String animation) {
        activeSkill = skill;
        skillTicks = 0;
        if (animation != null) {
            this.triggerAnim(ACTION_CONTROLLER, animation);
        }
        this.playSound(SoundEvents.BEACON_POWER_SELECT, 2.0f, 0.6f);
    }

    private void endSkill() {
        activeSkill = Skill.NONE;
        skillTicks = 0;
        skillCooldown = SKILL_COOLDOWN;
        chargeHits.clear();
    }

    /**
     * 天罚雷印：在目标原先的位置画出预警圈，前摇结束后落雷，圈内实体受到伤害
     */
    private void tickJudgement(ServerLevel level) {
        if (skillTicks < JUDGEMENT_WINDUP) {
            if (skillTicks % 2 == 0) {
                spawnRing(level, skillTargetPos.add(0, 0.2, 0), JUDGEMENT_RADIUS);
            }
            return;
        }

        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
        if (bolt != null) {
            bolt.moveTo(skillTargetPos.x, skillTargetPos.y, skillTargetPos.z);
            bolt.setVisualOnly(true);
            level.addFreshEntity(bolt);
        }
        for (LivingEntity victim : findVictims(level, new AABB(skillTargetPos, skillTargetPos).inflate(JUDGEMENT_RADIUS),
                skillTargetPos, JUDGEMENT_RADIUS)) {
            victim.hurt(this.damageSources().mobAttack(this), JUDGEMENT_DAMAGE);
        }
        endSkill();
    }

    /**
     * 雷霆冲撞：先原地蓄力（电火花环绕），再朝目标方向直线冲刺，撞到的实体受到伤害并被击退
     */
    private void tickCharge(ServerLevel level) {
        if (skillTicks < CHARGE_WINDUP) {
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, this.getX(), this.getY() + 2, this.getZ(),
                    8, 1.0, 1.5, 1.0, 0.1);
            return;
        }

        if (skillTicks == CHARGE_WINDUP) {
            LivingEntity target = this.getTarget();
            if (target == null || !target.isAlive()) {
                endSkill();
                return;
            }
            Vec3 toTarget = target.position().subtract(this.position());
            if (toTarget.lengthSqr() < 1.0E-4) {
                endSkill();
                return;
            }
            chargeDirection = toTarget.normalize();
            this.getNavigation().stop();
            this.triggerAnim(ACTION_CONTROLLER, TRIGGER_ATTACK);
            this.playSound(SoundEvents.LIGHTNING_BOLT_THUNDER, 1.5f, 1.4f);
        }

        this.move(MoverType.SELF, chargeDirection.scale(CHARGE_SPEED));
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, this.getX(), this.getY() + 2, this.getZ(),
                4, 0.5, 1.0, 0.5, 0.05);

        for (LivingEntity victim : findVictims(level, this.getBoundingBox().inflate(1.0), null, 0)) {
            if (chargeHits.add(victim.getId())) {
                victim.hurt(this.damageSources().mobAttack(this), CHARGE_DAMAGE);
                victim.knockback(1.5, this.getX() - victim.getX(), this.getZ() - victim.getZ());
            }
        }

        if (skillTicks >= CHARGE_WINDUP + CHARGE_DURATION || this.horizontalCollision) {
            endSkill();
        }
    }

    /**
     * 雷环震荡：蓄力时地面出现收缩的预警圈，结束后对周围释放冲击波，造成伤害并强力击退
     */
    private void tickNova(ServerLevel level) {
        if (skillTicks < NOVA_WINDUP) {
            if (skillTicks % 2 == 0) {
                double radius = NOVA_RADIUS * (1.0 - (double) skillTicks / NOVA_WINDUP) + 1.0;
                spawnRing(level, this.position().add(0, 0.2, 0), radius);
            }
            return;
        }

        level.sendParticles(ParticleTypes.EXPLOSION, this.getX(), this.getY() + 1, this.getZ(), 6, 2.0, 1.0, 2.0, 0);
        this.playSound(SoundEvents.GENERIC_EXPLODE, 2.0f, 0.8f);
        for (LivingEntity victim : findVictims(level, this.getBoundingBox().inflate(NOVA_RADIUS), this.position(), NOVA_RADIUS)) {
            victim.hurt(this.damageSources().mobAttack(this), NOVA_DAMAGE);
            victim.knockback(NOVA_KNOCKBACK, this.getX() - victim.getX(), this.getZ() - victim.getZ());
            victim.setDeltaMovement(victim.getDeltaMovement().add(0, 0.6, 0));
            victim.hurtMarked = true;
        }
        endSkill();
    }

    /**
     * 技能命中的实体：排除自己、召唤出的凋灵、创造/旁观模式玩家；center 不为 null 时再按球形距离过滤
     */
    private List<LivingEntity> findVictims(ServerLevel level, AABB area, @Nullable Vec3 center, double radius) {
        double radiusSq = radius * radius;
        return level.getEntitiesOfClass(LivingEntity.class, area, e -> e != this && e.isAlive()
                && !e.getTags().contains(MINION_TAG)
                && !(e instanceof Player p && (p.isCreative() || p.isSpectator()))
                && (center == null || e.position().distanceToSqr(center) <= radiusSq));
    }

    private static void spawnRing(ServerLevel level, Vec3 center, double radius) {
        int points = Math.max(12, (int) (radius * 8));
        for (int i = 0; i < points; i++) {
            double angle = i * 2 * Math.PI / points;
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                    center.x + radius * Math.cos(angle), center.y, center.z + radius * Math.sin(angle),
                    1, 0, 0, 0, 0);
        }
    }

    /**
     * Boss 自己的闪电会落在近身目标上，不能反过来劈到自己
     */
    @Override
    public void thunderHit(ServerLevel level, LightningBolt lightning) {
    }

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

    private void enterSecondPhase() {
        secondPhaseTriggered = true;
        ServerLevel serverLevel = (ServerLevel) this.level();

        // 召唤 3 只凋灵：可以被击杀，存活期间 Boss 受到的伤害降低
        for (int i = 0; i < 3; i++) {
            double offsetX = (random.nextDouble() - 0.5) * 10;
            double offsetZ = (random.nextDouble() - 0.5) * 10;
            WitherBoss wither = EntityType.WITHER.spawn(serverLevel,
                    BlockPos.containing(this.getX() + offsetX, this.getY(), this.getZ() + offsetZ),
                    MobSpawnType.MOB_SUMMONED);
            if (wither != null) {
                wither.addTag(MINION_TAG);
                summonedWithers.add(wither.getUUID());
            }
        }

        // 飞 30 格高
        this.setPos(this.getX(), this.getY() + 30, this.getZ());

        this.bossEvent.setColor(BossEvent.BossBarColor.PURPLE);

        SourceOfMystery.LOGGER.info("Boss entered Phase 2 - summoned {} Withers", summonedWithers.size());
    }

    /**
     * 移除已经死亡（或已不存在）的凋灵
     */
    private void refreshMinions() {
        ServerLevel serverLevel = (ServerLevel) this.level();
        summonedWithers.removeIf(id -> {
            Entity wither = serverLevel.getEntity(id);
            return wither == null || !wither.isAlive();
        });
    }

    /**
     * 凋灵存活期间，Boss 受到的伤害按配置比例降低
     */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (!this.level().isClientSide && !summonedWithers.isEmpty()
                && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            amount *= (float) (1.0 - MysteryConfig.BOSS_MINION_DAMAGE_REDUCTION.get());
        }
        return super.hurt(source, amount);
    }

    /**
     * Boss 被击杀或被移除（如和平难度）时，清理二阶段召唤的凋灵，避免它们残留
     */
    @Override
    public void remove(RemovalReason reason) {
        if (!this.level().isClientSide && reason.shouldDestroy()) {
            ServerLevel serverLevel = (ServerLevel) this.level();
            for (UUID id : summonedWithers) {
                Entity wither = serverLevel.getEntity(id);
                if (wither != null) {
                    wither.discard();
                }
            }
            summonedWithers.clear();
        }
        super.remove(reason);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean(TAG_SECOND_PHASE, secondPhaseTriggered);
        ListTag withers = new ListTag();
        for (UUID id : summonedWithers) {
            withers.add(NbtUtils.createUUID(id));
        }
        tag.put(TAG_SUMMONED_WITHERS, withers);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        // 不保存的话，重进存档后低血量会再次触发二阶段，重复召唤凋灵并再飞高 30 格
        secondPhaseTriggered = tag.getBoolean(TAG_SECOND_PHASE);
        if (secondPhaseTriggered) {
            this.bossEvent.setColor(BossEvent.BossBarColor.PURPLE);
        }
        summonedWithers.clear();
        for (Tag entry : tag.getList(TAG_SUMMONED_WITHERS, Tag.TAG_INT_ARRAY)) {
            summonedWithers.add(NbtUtils.loadUUID(entry));
        }
        this.bossEvent.setProgress(this.getHealth() / this.getMaxHealth());
    }

    @Override
    public boolean canChangeDimensions() {
        return false;
    }

    /**
     * 让凋灵等敌对生物不主动把 Boss 当作攻击目标（避免二阶段召唤的凋灵攻击 Boss）
     */
    @Override
    public boolean canBeSeenAsEnemy() {
        return false;
    }

    /**
     * 免疫凋零效果（避免被二阶段召唤的凋灵上凋零）
     */
    @Override
    public boolean canBeAffected(MobEffectInstance effect) {
        return effect.getEffect() != MobEffects.WITHER && super.canBeAffected(effect);
    }

    /**
     * 远程免疫 + 凋灵伤害免疫
     */
    @Override
    public boolean isInvulnerableTo(DamageSource damageSource) {
        if (damageSource.getDirectEntity() instanceof Projectile || damageSource.is(DamageTypeTags.IS_PROJECTILE)) {
            return true;
        }
        if (damageSource.is(DamageTypes.WITHER) || damageSource.is(DamageTypes.WITHER_SKULL)
                || damageSource.getEntity() instanceof WitherBoss) {
            return true;
        }
        return super.isInvulnerableTo(damageSource);
    }

    /**
     * 击杀回复 - 仅当 Boss 本次攻击真正击杀目标时回复 1000 HP，并播放攻击动画
     */
    @Override
    public boolean doHurtTarget(Entity target) {
        this.triggerAnim(ACTION_CONTROLLER, TRIGGER_ATTACK);

        boolean result = super.doHurtTarget(target);
        if (result && !this.level().isClientSide && target instanceof LivingEntity living && !living.isAlive()) {
            this.heal(KILL_HEAL);
        }
        return result;
    }

    @Override
    public void die(DamageSource damageSource) {
        this.bossEvent.removeAllPlayers();
        super.die(damageSource);
        if (!this.level().isClientSide) {
            SourceOfMystery.LOGGER.info("Divine Heavenly Dao Boss has been slain!");
        }
    }

    /**
     * 死亡掉落：天道碎片 4-8、附魔金苹果 16、龙蛋 1、下界之星 2
     * 放在 dropCustomDeathLoot 中，遵守 doMobLoot 游戏规则
     */
    @Override
    protected void dropCustomDeathLoot(DamageSource source, int looting, boolean recentlyHit) {
        super.dropCustomDeathLoot(source, looting, recentlyHit);
        dropScattered(new ItemStack(ModItems.HEAVENLY_DAO_FRAGMENT.get(), 4 + this.random.nextInt(5)), 3.0);
        dropScattered(new ItemStack(Items.ENCHANTED_GOLDEN_APPLE, 16), 2.0);
        dropScattered(new ItemStack(Items.DRAGON_EGG), 0.0);
        dropScattered(new ItemStack(Items.NETHER_STAR, 2), 2.0);
    }

    /**
     * 把一组物品逐个散落在 Boss 周围
     */
    private void dropScattered(ItemStack stack, double spread) {
        for (int i = 0; i < stack.getCount(); i++) {
            double offsetX = (random.nextDouble() - 0.5) * spread;
            double offsetZ = (random.nextDouble() - 0.5) * spread;
            ItemEntity item = new ItemEntity(this.level(),
                    this.getX() + offsetX, this.getY() + 2, this.getZ() + offsetZ,
                    stack.copyWithCount(1));
            item.setDefaultPickUpDelay();
            this.level().addFreshEntity(item);
        }
    }

    @Override
    public boolean isPersistenceRequired() {
        return true; // Boss 不会自然消失
    }

    /**
     * GeckoLib 动画控制器注册
     * base 控制器循环播放 idle 漂浮动画；action 控制器用于触发 attack / thunder_strike 一次性动画。
     */
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, BASE_CONTROLLER, 5, state -> state.setAndContinue(IDLE)));
        controllers.add(new AnimationController<>(this, ACTION_CONTROLLER, 0, state -> PlayState.STOP)
                .triggerableAnim(TRIGGER_ATTACK, ATTACK)
                .triggerableAnim(TRIGGER_THUNDER, THUNDER));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }
}
