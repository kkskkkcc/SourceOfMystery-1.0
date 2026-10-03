package com.sourceofmystery.entity;

import com.sourceofmystery.SourceOfMystery;
import com.sourceofmystery.config.MysteryConfig;
import com.sourceofmystery.item.ModItems;
import com.sourceofmystery.network.BossIntroPacket;
import com.sourceofmystery.network.ModNetwork;
import com.sourceofmystery.network.ScreenEffectPacket;
import com.sourceofmystery.particle.ModParticles;
import com.sourceofmystery.sound.ModSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
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
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
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
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class DivineHeavenlyDaoBoss extends Monster implements GeoEntity {

    private static final String ANIM_PREFIX = "animation.divine_heavenly_dao_boss.";
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop(ANIM_PREFIX + "idle");
    private static final RawAnimation ATTACK = RawAnimation.begin().thenPlay(ANIM_PREFIX + "attack");
    private static final RawAnimation THUNDER = RawAnimation.begin().thenPlay(ANIM_PREFIX + "thunder_strike");
    private static final RawAnimation SLAM = RawAnimation.begin().thenPlay(ANIM_PREFIX + "slam");
    private static final RawAnimation ORB_CHARGE = RawAnimation.begin().thenPlay(ANIM_PREFIX + "orb_charge");
    private static final RawAnimation ORB_THROW = RawAnimation.begin().thenPlay(ANIM_PREFIX + "orb_throw");
    private static final RawAnimation DESCEND = RawAnimation.begin().thenPlay(ANIM_PREFIX + "descend");
    private static final String BASE_CONTROLLER = "base";
    private static final String ACTION_CONTROLLER = "action";
    private static final String TRIGGER_ATTACK = "attack";
    private static final String TRIGGER_THUNDER = "thunder_strike";
    private static final String TRIGGER_SLAM = "slam";
    private static final String TRIGGER_ORB_CHARGE = "orb_charge";
    private static final String TRIGGER_ORB_THROW = "orb_throw";
    private static final String TRIGGER_DESCEND = "descend";

    private static final String TAG_SECOND_PHASE = "SecondPhase";
    private static final String TAG_SUMMONED_WITHERS = "SummonedWithers";

    /** 二阶段召唤的凋灵带有此标签：不掉落物品和经验 */
    public static final String MINION_TAG = SourceOfMystery.MOD_ID + ".boss_minion";

    /**
     * 体型倍数（相对模型原始大小约 4 格高）：约 50 格高，接近凋零风暴完全体那样遮天蔽日。
     * 碰撞箱在 ModEntities 里按同样倍数放大，渲染器用 GeckoLib 的 withScale 放大模型。
     */
    public static final float SCALE = 12.0f;

    private static final float SECOND_PHASE_HEAL_PER_SECOND = 10.0f;
    private static final float KILL_HEAL = 1000.0f;
    private static final double ENDERMAN_RANGE = 20.0;

    // ==================== 移动 ====================
    // 体型太大，寻路必然失败，所以不用导航：直接穿过地形，悬停在目标面前，尾巴扫着地面
    private static final double HOVER_GAP = 6.0;        // 身体边缘和目标之间保持的水平距离
    private static final double HOVER_HEIGHT = 4.0;     // 脚底（模型原点）比目标高多少
    private static final double MAX_SPEED = 0.45;

    // ==================== 技能（均有前摇，可以躲避） ====================
    private static final int SKILL_COOLDOWN = 60;
    private static final int SKILL_COOLDOWN_PHASE_TWO = 40;
    // 天威一击：直接攻击目标
    private static final double STRIKE_REACH = 10.0;     // 身体边缘之外的攻击距离
    private static final int STRIKE_WINDUP = 16;
    private static final int STRIKE_HIT_DELAY = 4;       // attack 动画第 0.2 秒挥到最低点
    private static final float STRIKE_DAMAGE = 50.0f;
    // 撼地锤：双手高举锤击地面，站在地上的实体受到伤害并被抛上 20 格高空（跳起来可以躲开）
    private static final int SLAM_WINDUP = 30;           // 对应 slam 动画 1.5 秒处砸到地面
    private static final int SLAM_RECOVER = 20;
    private static final double SLAM_REACH = 10.0;       // 身体边缘之外的波及范围
    private static final float SLAM_DAMAGE = 50.0f;
    private static final double SLAM_LAUNCH_SPEED = 2.0; // 竖直初速度 2.0 约等于升到 20 格高
    // 天雷：5 秒内落下 18 道闪电，其中一道必定劈中当前目标，其余随机劈向周围的生物，周围没有生物就随机落下
    private static final int STORM_WINDUP = 20;
    private static final int STORM_DURATION = 100;
    private static final int STORM_BOLTS = 18;
    private static final double STORM_RANGE = 48.0;        // 随机劈向这个范围内的生物
    private static final double STORM_MIN_RADIUS = 6.0;    // 没有生物时随机落点（从身体边缘算起）
    private static final double STORM_MAX_RADIUS = 30.0;
    private static final double BOLT_HIT_RADIUS = 2.5;
    private static final float BOLT_DAMAGE = 100.0f;
    // 大招「天渊」：双手高举凝聚黑色球体 10 秒，然后掷向玩家引发巨大爆炸（见 AbyssOrb）
    private static final int ABYSS_CHARGE = 200;
    private static final int ABYSS_RELEASE = ABYSS_CHARGE + 8;   // orb_throw 动画 0.4 秒出手
    private static final int ABYSS_END = ABYSS_CHARGE + 30;
    private static final int ABYSS_COOLDOWN = 1800;              // 90 秒
    private static final double ABYSS_CHANCE = 0.35;
    private static final float ABYSS_MAX_RADIUS = 7.0f;
    private static final double FAR_PARTICLE_RANGE = 160.0;

    // ==================== 降临 ====================
    // 天上展开金色法阵 → 神威天道穿过法阵、伴着漫天雷霆从天而降 → 落地震屏，开战
    private static final double SIGIL_HEIGHT = 80.0;          // 法阵离地高度
    private static final int SIGIL_OPEN = 60;                 // 法阵展开、天雷滚滚
    private static final int DESCEND_TICKS = 100;             // 5 秒降临
    private static final int LANDED = SIGIL_OPEN + DESCEND_TICKS;
    public static final int INTRO_TICKS = LANDED + 40;
    private static final double INTRO_CAMERA_RANGE = 160.0;

    private enum Skill { NONE, STRIKE, SLAM, THUNDER_STORM, ABYSS }

    private int tickCounter = 0;
    private boolean secondPhaseTriggered = false;
    // 二阶段召唤的凋灵：存活期间 Boss 受到的伤害降低；Boss 死亡/消失时一并清理
    private final List<UUID> summonedWithers = new ArrayList<>();

    private Skill activeSkill = Skill.NONE;
    private int skillTicks = 0;
    private int skillCooldown = SKILL_COOLDOWN;
    private final int[] boltDelays = new int[STORM_BOLTS];
    // 这一轮天雷已经劈中过的生物：每个生物一轮最多被劈一次（必中玩家的那一道也只有一下）
    private final java.util.Set<UUID> stormStruck = new java.util.HashSet<>();
    private int abyssCooldown = 600;   // 开战 30 秒内不放大招
    @Nullable
    private AbyssOrb orb;

    private boolean introDone = true;  // /summon 直接召唤时没有降临演出
    private int introTick;
    private Vec3 groundPos = Vec3.ZERO;
    @Nullable
    private HeavenSigil sigil;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    private final ServerBossEvent bossEvent;

    public DivineHeavenlyDaoBoss(EntityType<? extends DivineHeavenlyDaoBoss> type, Level level) {
        super(type, level);
        this.xpReward = 10000;
        Component name = Component.translatable("entity.sourceofmystery.divine_heavenly_dao_boss")
                .withStyle(ChatFormatting.RED, ChatFormatting.BOLD);
        this.setCustomName(name);
        this.setCustomNameVisible(true);

        this.setNoGravity(true);
        this.noPhysics = true;
        this.noCulling = true;

        this.bossEvent = new ServerBossEvent(name, BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.PROGRESS);
        this.bossEvent.setDarkenScreen(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.ATTACK_DAMAGE, STRIKE_DAMAGE)
                .add(Attributes.ARMOR, 50.0)
                .add(Attributes.ARMOR_TOUGHNESS, 20.0)
                .add(Attributes.MAX_HEALTH, 10000.0)
                .add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.FOLLOW_RANGE, 128.0);
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
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, false));
        this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, LivingEntity.class, 10, false, false,
                e -> !(e instanceof Player) && !e.getTags().contains(MINION_TAG)));
    }

    /**
     * 不走原版移动（会和地形碰撞），位置由 tickHover 直接控制
     */
    @Override
    public void travel(Vec3 travelVector) {
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public void knockback(double strength, double x, double z) {
    }

    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
        return false;
    }

    @Override
    public void tick() {
        this.noPhysics = true;
        super.tick();
        if (this.level().isClientSide) {
            return;
        }
        tickCounter++;

        this.bossEvent.setProgress(this.getHealth() / this.getMaxHealth());
        if (!introDone) {
            tickIntro((ServerLevel) this.level());
            return;
        }
        if (abyssCooldown > 0) {
            abyssCooldown--;
        }

        // 每秒检测一次：20 格范围内若有末影人，则让它们愤怒攻击玩家；并清理已死亡的凋灵
        if (tickCounter % 20 == 0) {
            angerEndermen();
            refreshMinions();
        }

        tickHover();
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
     * 悬停在目标面前：身体边缘与目标保持 HOVER_GAP，高度跟随目标；出招期间原地不动
     */
    private void tickHover() {
        LivingEntity target = this.getTarget();
        if (target == null || !target.isAlive()) {
            this.setDeltaMovement(Vec3.ZERO);
            return;
        }
        Vec3 toTarget = new Vec3(target.getX() - this.getX(), 0, target.getZ() - this.getZ());
        double horizontal = toTarget.length();
        if (horizontal > 1.0E-3) {
            float yaw = (float) (Mth.atan2(toTarget.z, toTarget.x) * Mth.RAD_TO_DEG) - 90.0f;
            this.setYRot(Mth.rotLerp(0.15f, this.getYRot(), yaw));
            this.yBodyRot = this.getYRot();
            this.yHeadRot = this.getYRot();
        }
        if (activeSkill != Skill.NONE) {
            this.setDeltaMovement(Vec3.ZERO);
            return;
        }

        double keep = this.getBbWidth() / 2 + HOVER_GAP;
        Vec3 desired = horizontal > 1.0E-3
                ? target.position().subtract(toTarget.normalize().scale(keep))
                : this.position();
        desired = new Vec3(desired.x, target.getY() + HOVER_HEIGHT, desired.z);
        Vec3 delta = desired.subtract(this.position());
        Vec3 velocity = delta.scale(0.06);
        if (velocity.length() > MAX_SPEED) {
            velocity = velocity.normalize().scale(MAX_SPEED);
        }
        this.setDeltaMovement(velocity);
        this.setPos(this.getX() + velocity.x, this.getY() + velocity.y, this.getZ() + velocity.z);
    }

    /**
     * 末影人协同：若 Boss 20 格范围内存在末影人，则让它们全部愤怒攻击玩家
     */
    private void angerEndermen() {
        ServerLevel serverLevel = (ServerLevel) this.level();
        List<EnderMan> endermen = serverLevel.getEntitiesOfClass(EnderMan.class,
                this.getBoundingBox().inflate(ENDERMAN_RANGE), LivingEntity::isAlive);
        if (endermen.isEmpty()) {
            return;
        }

        // 优先 Boss 当前锁定的玩家，否则找最近的玩家
        Player targetPlayer = this.getTarget() instanceof Player player
                ? player
                : serverLevel.getNearestPlayer(this.getX(), this.getY(), this.getZ(), this.getBbWidth() + ENDERMAN_RANGE * 2, true);
        if (targetPlayer == null || !targetPlayer.isAlive()) {
            return;
        }

        for (EnderMan enderman : endermen) {
            enderman.setTarget(targetPlayer);
        }
    }

    // ==================== 技能 ====================

    /**
     * 目标与身体边缘的水平距离
     */
    private double gapTo(Entity entity) {
        double dx = entity.getX() - this.getX();
        double dz = entity.getZ() - this.getZ();
        return Math.sqrt(dx * dx + dz * dz) - this.getBbWidth() / 2;
    }

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
            double gap = gapTo(target);
            double roll = this.random.nextDouble();
            if (abyssCooldown <= 0 && this.random.nextDouble() < ABYSS_CHANCE) {
                startSkill(Skill.ABYSS);
            } else if (gap <= STRIKE_REACH) {
                startSkill(roll < 0.45 ? Skill.STRIKE : roll < 0.75 ? Skill.SLAM : Skill.THUNDER_STORM);
            } else {
                startSkill(roll < 0.6 ? Skill.THUNDER_STORM : Skill.SLAM);
            }
            return;
        }

        skillTicks++;
        switch (activeSkill) {
            case STRIKE -> tickStrike(level);
            case SLAM -> tickSlam(level);
            case THUNDER_STORM -> tickThunderStorm(level);
            case ABYSS -> tickAbyss(level);
            default -> endSkill();
        }
    }

    private void startSkill(Skill skill) {
        activeSkill = skill;
        skillTicks = 0;
        switch (skill) {
            case SLAM -> this.triggerAnim(ACTION_CONTROLLER, TRIGGER_SLAM);
            case THUNDER_STORM -> {
                this.triggerAnim(ACTION_CONTROLLER, TRIGGER_THUNDER);
                planThunderStorm();
            }
            case ABYSS -> {
                abyssCooldown = ABYSS_COOLDOWN;
                this.triggerAnim(ACTION_CONTROLLER, TRIGGER_ORB_CHARGE);
                if (this.level() instanceof ServerLevel level) {
                    notifyNearby(level, ModSounds.DIVINE_HEAVENLY_DAO_ORB_CHARGE.get(), 0.8f, 200.0);
                    broadcastLine(level, Component.translatable("message.sourceofmystery.boss.abyss")
                            .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD));
                }
            }
            default -> {
            }
        }
        this.playSound(ModSounds.DIVINE_HEAVENLY_DAO_CHARGE.get(), 4.0f, 1.0f);
    }

    private void endSkill() {
        activeSkill = Skill.NONE;
        skillTicks = 0;
        skillCooldown = secondPhaseTriggered ? SKILL_COOLDOWN_PHASE_TWO : SKILL_COOLDOWN;
    }

    /**
     * 天威一击：蓄力后挥击当前目标，50 点伤害
     */
    private void tickStrike(ServerLevel level) {
        LivingEntity target = this.getTarget();
        if (target == null || !target.isAlive()) {
            endSkill();
            return;
        }
        if (skillTicks < STRIKE_WINDUP) {
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, target.getX(), target.getY() + 1, target.getZ(),
                    4, 0.6, 1.0, 0.6, 0.05);
            return;
        }
        if (skillTicks == STRIKE_WINDUP) {
            this.triggerAnim(ACTION_CONTROLLER, TRIGGER_ATTACK);
            return;
        }
        if (skillTicks == STRIKE_WINDUP + STRIKE_HIT_DELAY) {
            if (gapTo(target) <= STRIKE_REACH + 2.0 && isValidVictim(target)) {
                boolean hurt = target.hurt(this.damageSources().mobAttack(this), STRIKE_DAMAGE);
                if (hurt) {
                    Vec3 push = horizontalDirection(this.position(), target.position()).scale(1.2);
                    target.setDeltaMovement(push.x, 0.5, push.z);
                    target.hurtMarked = true;
                    if (!target.isAlive()) {
                        this.heal(KILL_HEAL);
                    }
                }
            }
            level.sendParticles(ParticleTypes.EXPLOSION, target.getX(), target.getY() + 1, target.getZ(), 2, 0.5, 0.5, 0.5, 0);
            this.playSound(SoundEvents.GENERIC_EXPLODE, 3.0f, 0.6f);
        }
        if (skillTicks >= STRIKE_WINDUP + 12) {
            endSkill();
        }
    }

    /**
     * 撼地锤：前摇期间地面出现收缩的预警圈；砸下时站在地上的实体受到 50 点伤害并被抛上 20 格高空
     */
    private void tickSlam(ServerLevel level) {
        double radius = this.getBbWidth() / 2 + SLAM_REACH;
        double groundY = groundYBelow(level, this.getX(), this.getZ(), this.getY());
        if (skillTicks < SLAM_WINDUP) {
            if (skillTicks % 3 == 0) {
                spawnRing(level, new Vec3(this.getX(), groundY + 0.2, this.getZ()), radius);
            }
            return;
        }
        if (skillTicks == SLAM_WINDUP) {
            this.playSound(ModSounds.DIVINE_HEAVENLY_DAO_SLAM.get(), 6.0f, 1.0f);
            this.playSound(SoundEvents.GENERIC_EXPLODE, 6.0f, 0.5f);
            for (int i = 0; i < 24; i++) {
                double angle = i * Math.PI * 2 / 24;
                level.sendParticles(ParticleTypes.EXPLOSION,
                        this.getX() + Math.cos(angle) * radius * 0.6, groundY + 0.5, this.getZ() + Math.sin(angle) * radius * 0.6,
                        1, 1.0, 0.2, 1.0, 0);
            }
            double radiusSq = radius * radius;
            for (LivingEntity victim : findVictims(level, new AABB(this.getX() - radius, groundY - 3, this.getZ() - radius,
                    this.getX() + radius, groundY + 3, this.getZ() + radius), null, 0)) {
                double dx = victim.getX() - this.getX();
                double dz = victim.getZ() - this.getZ();
                if (!victim.onGround() || dx * dx + dz * dz > radiusSq) {
                    continue; // 只打站在地上的：起跳可以躲开
                }
                victim.hurt(this.damageSources().mobAttack(this), SLAM_DAMAGE);
                Vec3 current = victim.getDeltaMovement();
                victim.setDeltaMovement(current.x * 0.2, SLAM_LAUNCH_SPEED, current.z * 0.2);
                victim.hurtMarked = true;
            }
        }
        if (skillTicks >= SLAM_WINDUP + SLAM_RECOVER) {
            endSkill();
        }
    }

    /**
     * 为天雷规划 18 道闪电的落下时间：均匀分布在 5 秒内并带一点随机；第 0 道是必中目标的那一道
     */
    private void planThunderStorm() {
        stormStruck.clear();
        for (int i = 0; i < STORM_BOLTS; i++) {
            boltDelays[i] = (int) ((i + this.random.nextDouble()) * STORM_DURATION / STORM_BOLTS);
        }
        // 必中的那一道放在前 3 秒中的随机时刻，玩家没法靠拖时间躲过
        boltDelays[0] = 10 + this.random.nextInt(50);
    }

    private void tickThunderStorm(ServerLevel level) {
        if (skillTicks < STORM_WINDUP) {
            sendFar(level, ParticleTypes.ELECTRIC_SPARK, this.getX(), this.getY() + this.getBbHeight() * 0.7, this.getZ(),
                    10, this.getBbWidth() * 0.3, 3.0, this.getBbWidth() * 0.3, 0.2);
            return;
        }
        int t = skillTicks - STORM_WINDUP;
        LivingEntity target = this.getTarget();
        for (int i = 0; i < STORM_BOLTS; i++) {
            if (boltDelays[i] != t) {
                continue;
            }
            if (i == 0 && target != null && target.isAlive() && isValidVictim(target)) {
                // 必中：直接劈在目标当前的位置上
                strikeBolt(level, target.getX(), target.getY(), target.getZ(), target);
                continue;
            }
            LivingEntity victim = randomStormVictim(level, target);
            if (victim != null) {
                strikeBolt(level, victim.getX(), victim.getY(), victim.getZ(), victim);
            } else {
                double angle = this.random.nextDouble() * Math.PI * 2;
                double distance = this.getBbWidth() / 2 + STORM_MIN_RADIUS
                        + this.random.nextDouble() * (STORM_MAX_RADIUS - STORM_MIN_RADIUS);
                double x = this.getX() + Math.cos(angle) * distance;
                double z = this.getZ() + Math.sin(angle) * distance;
                strikeBolt(level, x, groundYBelow(level, x, z, this.getY()), z, null);
            }
        }
        if (t >= STORM_DURATION + 10) {
            endSkill();
        }
    }

    /**
     * 随机挑一个周围还没被劈过的生物：当前目标只吃那一道必中的雷，不会被随机的雷再选中
     */
    @Nullable
    private LivingEntity randomStormVictim(ServerLevel level, @Nullable LivingEntity target) {
        List<LivingEntity> pool = findVictims(level, this.getBoundingBox().inflate(STORM_RANGE), this.position(),
                this.getBbWidth() / 2 + STORM_RANGE);
        pool.removeIf(e -> e == target || stormStruck.contains(e.getUUID()));
        return pool.isEmpty() ? null : pool.get(this.random.nextInt(pool.size()));
    }

    /**
     * 一道天雷：画面为原版闪电（不点火、不伤害），伤害由这里结算，Boss 自己不受影响。
     * aimed 不为 null 时这道雷是冲着它去的，无论它此刻在不在落点范围内都会受到伤害。
     * 伤害类型是原版闪电，不无视护甲（护甲、保护附魔、本模组的额外减伤都照常生效）；
     * 每个生物一轮天雷最多被劈一次
     */
    private void strikeBolt(ServerLevel level, double x, double y, double z, @Nullable LivingEntity aimed) {
        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
        if (bolt != null) {
            bolt.moveTo(x, y, z);
            bolt.setVisualOnly(true);
            level.addFreshEntity(bolt);
        }
        Vec3 impact = new Vec3(x, y, z);
        DamageSource source = new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
                .getHolderOrThrow(DamageTypes.LIGHTNING_BOLT), this);
        AABB area = new AABB(x - BOLT_HIT_RADIUS, y - 2, z - BOLT_HIT_RADIUS, x + BOLT_HIT_RADIUS, y + 5, z + BOLT_HIT_RADIUS);
        List<LivingEntity> victims = new ArrayList<>(findVictims(level, area, null, 0));
        if (aimed != null && !victims.contains(aimed)) {
            victims.add(aimed);
        }
        for (LivingEntity victim : victims) {
            double dx = victim.getX() - impact.x;
            double dz = victim.getZ() - impact.z;
            boolean inRange = victim == aimed || dx * dx + dz * dz <= BOLT_HIT_RADIUS * BOLT_HIT_RADIUS;
            if (inRange && stormStruck.add(victim.getUUID())) {
                victim.invulnerableTime = 0; // 不被其他伤害的受击无敌帧吞掉
                victim.hurt(source, BOLT_DAMAGE);
            }
        }
    }

    // ==================== 大招：天渊 ====================

    /**
     * 双手高举凝聚黑色球体 10 秒（四周浓烈的黑色烟尘向球体汇聚），然后掷向目标
     */
    private void tickAbyss(ServerLevel level) {
        LivingEntity target = this.getTarget();
        if (skillTicks <= ABYSS_CHARGE) {
            float u = skillTicks / (float) ABYSS_CHARGE;
            float radius = 0.5f + (ABYSS_MAX_RADIUS - 0.5f) * (float) Math.sqrt(u);
            Vec3 center = orbCenter(radius);
            if (orb == null && skillTicks >= 15) {
                orb = new AbyssOrb(level, this, center);
                level.addFreshEntity(orb);
            }
            if (orb != null) {
                orb.hold(center, radius);
            }
            // 黑色烟尘从四周汇入球体，越到后面越浓
            int count = 6 + (int) (u * 18);
            for (int i = 0; i < count; i++) {
                Vec3 dir = new Vec3(this.random.nextGaussian(), this.random.nextGaussian() * 0.7, this.random.nextGaussian());
                if (dir.lengthSqr() < 1.0E-4) {
                    continue;
                }
                Vec3 from = center.add(dir.normalize().scale(radius + 12 + this.random.nextDouble() * 22));
                Vec3 to = center.subtract(from);
                sendFar(level, ModParticles.DARK_MOTE.get(), from.x, from.y, from.z, 0, to.x, to.y, to.z, 1.0);
            }
            if (skillTicks % 40 == 20) {
                notifyNearby(level, ModSounds.DIVINE_HEAVENLY_DAO_ORB_CHARGE.get(), 0.6f + u * 0.4f, 200.0);
            }
            if (skillTicks == ABYSS_CHARGE) {
                this.triggerAnim(ACTION_CONTROLLER, TRIGGER_ORB_THROW);
            }
        } else if (skillTicks == ABYSS_RELEASE) {
            if (orb != null && orb.isAlive()) {
                Vec3 aim = target != null && target.isAlive() ? target.position() : this.position().add(forward().scale(30));
                orb.launch(aim);
                this.playSound(SoundEvents.WITHER_SHOOT, 8.0f, 0.4f);
            }
            orb = null;
        }
        if (skillTicks >= ABYSS_END) {
            endSkill();
        }
    }

    /** 黑球托在高举的双手上方、略微靠前，越大越往上 */
    private Vec3 orbCenter(float radius) {
        return this.position().add(forward().scale(this.getBbWidth() * 0.15))
                .add(0, this.getBbHeight() * 0.97 + radius * 0.85, 0);
    }

    private Vec3 forward() {
        float yaw = this.getYRot() * Mth.DEG_TO_RAD;
        return new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw));
    }

    // ==================== 降临演出 ====================

    /**
     * 由 BossSpawnHandler 在生成后调用：groundPos 为祭坛上方的落点。
     * 生成前已经设成隐身；Boss 先停在法阵上方，附近玩家的镜头对准她（天上的法阵）
     */
    public void beginIntro(ServerLevel level, Vec3 groundPos) {
        this.groundPos = groundPos;
        this.introDone = false;
        this.introTick = 0;
        this.setInvisible(true);
        this.bossEvent.setVisible(false);
        double sigilY = groundPos.y + SIGIL_HEIGHT;
        this.setPos(groundPos.x, sigilY + 2, groundPos.z);
        sigil = ModEntities.HEAVEN_SIGIL.get().create(level);
        if (sigil != null) {
            sigil.moveTo(groundPos.x, sigilY, groundPos.z, 0, 0);
            level.addFreshEntity(sigil);
        }
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(groundPos) < INTRO_CAMERA_RANGE * INTRO_CAMERA_RANGE) {
                ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new BossIntroPacket(this.getId(), INTRO_TICKS));
            }
        }
        notifyNearby(level, ModSounds.DIVINE_HEAVENLY_DAO_DESCEND.get(), 1.0f, INTRO_CAMERA_RANGE);
    }

    private void tickIntro(ServerLevel level) {
        introTick++;
        this.setDeltaMovement(Vec3.ZERO);
        double sigilY = groundPos.y + SIGIL_HEIGHT;
        double landY = groundPos.y + HOVER_HEIGHT;
        Player nearest = level.getNearestPlayer(groundPos.x, groundPos.y, groundPos.z, INTRO_CAMERA_RANGE, false);
        if (nearest != null) {
            Vec3 to = nearest.position().subtract(this.position());
            float yaw = (float) (Mth.atan2(to.z, to.x) * Mth.RAD_TO_DEG) - 90.0f;
            this.setYRot(Mth.rotLerp(0.1f, this.getYRot(), yaw));
            this.yBodyRot = this.getYRot();
            this.yHeadRot = this.getYRot();
        }

        // 法阵展开期间：天雷在远处滚滚，金光沿光柱落下
        if (introTick < SIGIL_OPEN) {
            if (introTick % 20 == 10) {
                notifyNearby(level, ModSounds.DIVINE_HEAVENLY_DAO_DESCEND.get(), 0.8f, INTRO_CAMERA_RANGE);
            }
            for (int i = 0; i < 4; i++) {
                double a = this.random.nextDouble() * Math.PI * 2;
                double r = this.random.nextDouble() * HeavenSigil.RADIUS * 0.8;
                double x = groundPos.x + Math.cos(a) * r;
                double z = groundPos.z + Math.sin(a) * r;
                sendFar(level, ModParticles.GOLD_MOTE.get(), x, sigilY - 1, z, 0, 0, -(sigilY - groundPos.y) * 0.5, 0, 1.0);
            }
        }
        if (introTick == SIGIL_OPEN) {
            this.setInvisible(false);
            this.triggerAnim(ACTION_CONTROLLER, TRIGGER_DESCEND);
            notifyNearby(level, ModSounds.DIVINE_HEAVENLY_DAO_CHARGE.get(), 0.7f, INTRO_CAMERA_RANGE);
        }
        if (introTick >= SIGIL_OPEN && introTick < LANDED) {
            double u = (introTick - SIGIL_OPEN) / (double) DESCEND_TICKS;
            double eased = u * u * (3 - 2 * u);
            this.setPos(groundPos.x, Mth.lerp(eased, sigilY + 2, landY), groundPos.z);
            // 无边闪电：降临途中四面八方雷霆不断
            if (introTick % 2 == 0) {
                double a = this.random.nextDouble() * Math.PI * 2;
                double r = 10 + this.random.nextDouble() * 70;
                double x = groundPos.x + Math.cos(a) * r;
                double z = groundPos.z + Math.sin(a) * r;
                LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
                if (bolt != null) {
                    bolt.moveTo(x, groundYBelow(level, x, z, groundPos.y), z);
                    bolt.setVisualOnly(true);
                    level.addFreshEntity(bolt);
                }
            }
        }
        if (introTick == LANDED) {
            this.setPos(groundPos.x, landY, groundPos.z);
            if (sigil != null) {
                sigil.close();
            }
            double ground = groundYBelow(level, groundPos.x, groundPos.z, groundPos.y);
            for (int i = 0; i < 36; i++) {
                double angle = i * Math.PI * 2 / 36;
                double radius = this.getBbWidth() / 2 + 4;
                sendFar(level, ParticleTypes.EXPLOSION, groundPos.x + Math.cos(angle) * radius, ground + 0.5,
                        groundPos.z + Math.sin(angle) * radius, 1, 1.0, 0.2, 1.0, 0);
            }
            notifyNearby(level, ModSounds.DIVINE_HEAVENLY_DAO_SLAM.get(), 0.8f, INTRO_CAMERA_RANGE);
            notifyNearby(level, SoundEvents.GENERIC_EXPLODE, 0.5f, INTRO_CAMERA_RANGE);
            for (ServerPlayer player : level.players()) {
                double d = player.position().distanceTo(this.position());
                if (d < INTRO_CAMERA_RANGE) {
                    float near = (float) (1.0 - d / INTRO_CAMERA_RANGE);
                    ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                            new ScreenEffectPacket(0, (int) (15 + 25 * near), 1.0f + 2.0f * near));
                }
            }
        }
        if (introTick >= INTRO_TICKS) {
            introDone = true;
            this.setInvisible(false);
            this.bossEvent.setVisible(true);
            skillCooldown = SKILL_COOLDOWN;
        }
    }

    private void notifyNearby(ServerLevel level, SoundEvent sound, float pitch, double range) {
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(this) < range * range) {
                player.playNotifySound(sound, SoundSource.HOSTILE, 1.0f, pitch);
            }
        }
    }

    private void broadcastLine(ServerLevel level, Component message) {
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(this) < INTRO_CAMERA_RANGE * INTRO_CAMERA_RANGE) {
                player.sendSystemMessage(message);
            }
        }
    }

    /**
     * 远距离也能看到的粒子：神威天道有 50 格高，原版 sendParticles 只发给 32 格内的玩家，站在地上会看不到头顶的效果
     */
    private static void sendFar(ServerLevel level, ParticleOptions type, double x, double y, double z, int count,
                                double dx, double dy, double dz, double speed) {
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(x, y, z) < FAR_PARTICLE_RANGE * FAR_PARTICLE_RANGE) {
                level.sendParticles(player, type, true, x, y, z, count, dx, dy, dz, speed);
            }
        }
    }

    /**
     * 地面高度；脚下是虚空（末地边缘）时用参考高度兜底
     */
    private static double groundYBelow(ServerLevel level, double x, double z, double fallbackY) {
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(x), Mth.floor(z));
        return y <= level.getMinBuildHeight() ? fallbackY : y;
    }

    private static Vec3 horizontalDirection(Vec3 from, Vec3 to) {
        Vec3 d = new Vec3(to.x - from.x, 0, to.z - from.z);
        return d.lengthSqr() < 1.0E-6 ? new Vec3(1, 0, 0) : d.normalize();
    }

    private boolean isValidVictim(LivingEntity e) {
        return e != this && e.isAlive() && !e.getTags().contains(MINION_TAG)
                && !(e instanceof Player p && (p.isCreative() || p.isSpectator()));
    }

    /**
     * 技能命中的实体：排除自己、召唤出的凋灵、创造/旁观模式玩家；center 不为 null 时再按球形距离过滤
     */
    private List<LivingEntity> findVictims(ServerLevel level, AABB area, @Nullable Vec3 center, double radius) {
        double radiusSq = radius * radius;
        return level.getEntitiesOfClass(LivingEntity.class, area, e -> isValidVictim(e)
                && (center == null || e.position().distanceToSqr(center) <= radiusSq));
    }

    private static void spawnRing(ServerLevel level, Vec3 center, double radius) {
        int points = Math.max(12, (int) (radius * 4));
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

        // 召唤 3 只末影龙大小的巨型凋灵：可以被击杀，存活期间 Boss 受到的伤害降低
        for (int i = 0; i < 3; i++) {
            double angle = i * Math.PI * 2 / 3 + random.nextDouble();
            double distance = this.getBbWidth() / 2 + 12;
            WitherBoss wither = ModEntities.GIANT_WITHER.get().spawn(serverLevel,
                    BlockPos.containing(this.getX() + Math.cos(angle) * distance, this.getY() + 8,
                            this.getZ() + Math.sin(angle) * distance),
                    MobSpawnType.MOB_SUMMONED);
            if (wither != null) {
                wither.addTag(MINION_TAG);
                summonedWithers.add(wither.getUUID());
            }
        }

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
        // 降临演出期间无敌
        if (!introDone && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return false;
        }
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
        if (!this.level().isClientSide) {
            if (orb != null && orb.isAlive() && orb.isCharging()) {
                orb.discard();
            }
            if (sigil != null && sigil.isAlive()) {
                sigil.discard();
            }
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
        tag.putBoolean("IntroDone", introDone);
        tag.putInt("AbyssCooldown", abyssCooldown);
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
        // 降临演出中途存档：法阵实体不存档，直接开战
        introDone = true;
        this.setInvisible(false);
        abyssCooldown = tag.contains("AbyssCooldown") ? tag.getInt("AbyssCooldown") : abyssCooldown;
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
                || damageSource.getEntity() instanceof WitherBoss || damageSource.is(DamageTypes.LIGHTNING_BOLT)
                || damageSource.getEntity() == this
                || damageSource.is(DamageTypes.IN_WALL)) {
            return true;
        }
        return super.isInvulnerableTo(damageSource);
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
     * base 控制器循环播放 idle 漂浮动画；action 控制器用于触发 attack / thunder_strike / slam 一次性动画。
     */
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, BASE_CONTROLLER, 5, state -> state.setAndContinue(IDLE)));
        controllers.add(new AnimationController<>(this, ACTION_CONTROLLER, 0, state -> PlayState.STOP)
                .triggerableAnim(TRIGGER_ATTACK, ATTACK)
                .triggerableAnim(TRIGGER_THUNDER, THUNDER)
                .triggerableAnim(TRIGGER_SLAM, SLAM)
                .triggerableAnim(TRIGGER_ORB_CHARGE, ORB_CHARGE)
                .triggerableAnim(TRIGGER_ORB_THROW, ORB_THROW)
                .triggerableAnim(TRIGGER_DESCEND, DESCEND));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }
}
