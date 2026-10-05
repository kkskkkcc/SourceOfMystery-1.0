package com.sourceofmystery.entity;

import com.sourceofmystery.network.ModNetwork;
import com.sourceofmystery.network.RootPacket;
import com.sourceofmystery.network.ScreenEffectPacket;
import com.sourceofmystery.network.WeepingIntroPacket;
import com.sourceofmystery.particle.ModParticles;
import com.sourceofmystery.sound.ModSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.network.PacketDistributor;
import org.joml.Vector3f;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 泣死之主：原版凋灵死亡后，在它爆开的位置诞生的中立 Boss。
 * <p>
 * 出场（22 秒，按现实时间计时，期间附近玩家进入电影镜头，她无敌，天空日夜交替越来越快）：
 * 凋灵变白、加速旋转 2 秒后爆炸 → 她蜷缩抱膝漂浮 2 秒（镰刀竖着漂浮在身旁冒紫色粒子）→ 脸部特写，整整 5 秒缓缓睁眼，
 * 眼睛发出白光（亮度同火把，完全睁开时最亮）→ 猛地张开双臂，凋灵咆哮 3 秒 → 镰刀飞回手中，扛在肩上看向玩家
 * → 镜头从玩家身前绕到身后，定格 2 秒。整段出场是一个连续的动画（wl_intro），镜头由客户端 WeepingIntroDirector 播放。
 * <p>
 * 中立：玩家不攻击她，她就不攻击玩家；但会主动攻击出生点 120 格内的动物和怪物。平时在出生点 120 格内像水母一样漂荡。
 * <p>
 * 招式（扛着笨重的镰刀甩打，前摇都在 2 秒内，后摇都在 3 秒以上）：
 * 劈斩（镰刀变大砸地，地震 30）、凋灵头（掷出镰刀定身，双手两个凋灵头每秒 3 发、持续 5 秒，每发 10）、
 * 吸附（10 秒内每秒把人拉近 2 格，进入 3 格就抓住斩碎空间，100）、快速攻击（5 秒带残影，每秒 3 刀，每刀 5）、
 * 大招死亡激光（凝聚黑色魔法阵，胸口发射半径 1 格、长 30 格的白色激光 5 秒，慢一个身位追踪玩家，会破坏地形）。
 * <p>
 * 动画：所有姿势都由同一个 main 控制器播放，服务端只同步「当前动画编号 + 序号」。控制器每次换动画都有
 * {@link #BLEND} tick 的过渡，GeckoLib 会把每根骨骼从当前姿势平滑地接到新动画的第一帧，不会穿模错位。
 * 因为有这段过渡，动画里的时间点在服务端都要加上 BLEND（见 {@link #at}）。
 */
public class WeepingDeathLord extends PathfinderMob implements GeoEntity {

    // ==================== 动画 ====================
    private static final String MAIN_CONTROLLER = "main";
    private static final String BLINK_CONTROLLER = "blink";
    /** main 控制器换动画时的过渡时长（tick），与 tools/weeping_death_lord/anims.py 的 BLEND 一致 */
    public static final int BLEND = 6;

    /** 动画编号 = 下标；0 是待机（客户端按是否在漂荡 / 战斗选择四种待机动画之一） */
    private static final String[] ANIMS = {"idle", "intro", "toss", "cleave", "skull_throw", "skull_raise", "skull_fire",
            "skull_end", "absorb_start", "absorb_hold", "absorb_grab", "absorb_slash", "absorb_end", "rapid_start",
            "rapid_loop", "rapid_end", "laser_start", "laser_fire", "laser_end"};
    private static final RawAnimation[] ANIM_RAW = new RawAnimation[ANIMS.length];

    static {
        // thenPlay 使用 json 里写的循环方式：循环动画循环，其余停在最后一帧（等服务端切下一个动画）
        for (int i = 1; i < ANIMS.length; i++) {
            ANIM_RAW[i] = RawAnimation.begin().thenPlay("wl_" + ANIMS[i]);
        }
    }

    private static final RawAnimation HOVER = RawAnimation.begin().thenLoop("wl_hover");
    private static final RawAnimation HOVER_COMBAT = RawAnimation.begin().thenLoop("wl_hover_combat");
    private static final RawAnimation DRIFT = RawAnimation.begin().thenLoop("wl_drift");
    private static final RawAnimation DRIFT_COMBAT = RawAnimation.begin().thenLoop("wl_drift_combat");
    private static final RawAnimation BLINK = RawAnimation.begin().thenLoop("wl_blink");

    // ==================== 同步数据 ====================
    private static final EntityDataAccessor<Boolean> DATA_HIDDEN =
            SynchedEntityData.defineId(WeepingDeathLord.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_DRIFTING =
            SynchedEntityData.defineId(WeepingDeathLord.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_COMBAT =
            SynchedEntityData.defineId(WeepingDeathLord.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_SCYTHE_OUT =
            SynchedEntityData.defineId(WeepingDeathLord.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_TRAIL =
            SynchedEntityData.defineId(WeepingDeathLord.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Float> DATA_ORB =
            SynchedEntityData.defineId(WeepingDeathLord.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_EYE_GLOW =
            SynchedEntityData.defineId(WeepingDeathLord.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> DATA_ANIM =
            SynchedEntityData.defineId(WeepingDeathLord.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_ANIM_SEQ =
            SynchedEntityData.defineId(WeepingDeathLord.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DATA_SCYTHE_CHARGE =
            SynchedEntityData.defineId(WeepingDeathLord.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_CIRCLE =
            SynchedEntityData.defineId(WeepingDeathLord.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> DATA_LASER =
            SynchedEntityData.defineId(WeepingDeathLord.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Vector3f> DATA_LASER_END =
            SynchedEntityData.defineId(WeepingDeathLord.class, EntityDataSerializers.VECTOR3);

    // ==================== 出场时间表（秒，现实时间） ====================
    public static final float INTRO_EXPLODE = 2.0f;      // 凋灵变白旋转 2 秒后爆炸，她出现，蜷缩漂浮
    public static final float INTRO_WAKE = 4.0f;         // 蜷缩 2 秒后：脸部特写，开始睁眼
    public static final float INTRO_ROAR = 9.0f;         // 睁眼整整 5 秒后：张开双臂咆哮
    public static final float INTRO_SHOULDER = 12.0f;    // 咆哮 3 秒后：镰刀飞向她
    public static final float INTRO_CATCH = 13.0f;       // 接住镰刀，扛到肩上
    public static final float INTRO_SETTLE = 14.5f;      // 扛着镰刀看向玩家；镜头开始运镜
    public static final float INTRO_DOLLY_END = 19.0f;   // 镜头到达玩家身后，定格
    public static final float INTRO_FREEZE_END = 21.0f;  // 定格 2 秒后镜头回到玩家
    public static final float INTRO_LENGTH = 22.0f;
    public static final int INTRO_TICKS = Math.round(INTRO_LENGTH * 20);
    public static final int DAY_SPIN_DAYS = 4;           // 出场期间天空转过整整 4 天（客户端表现，结束时回到原来的时间）
    private static final double CAMERA_RANGE = 128.0;

    // ==================== 移动 ====================
    public static final double WANDER_RADIUS = 120.0;
    private static final double LEASH_RADIUS = WANDER_RADIUS + 40.0;
    private static final double HOVER_HEIGHT = 1.6;    // 脚离地高度
    private static final double DRIFT_SPEED = 0.16;
    private static final double CHASE_SPEED = 0.32;

    // ==================== 招式（动画时间换算成服务端 tick：at(秒) = BLEND + 秒 * 20） ====================
    private static final float SCALE = 1.25f;          // 渲染缩放（模型约 3.6 格高）

    // 劈斩：wl_cleave 5.0 秒，1.65 秒砸地
    private static final int CLEAVE_LENGTH = at(5.0f);
    private static final int CLEAVE_AIM_LOCK = at(1.2f);
    private static final int CLEAVE_HIT = at(1.65f);
    private static final float CLEAVE_DAMAGE = 30.0f;
    private static final double CLEAVE_REACH = 6.5;     // 变大的镰刀砸在身前这么远
    private static final double QUAKE_RADIUS = 7.0;
    private static final double CLEAVE_RANGE = 8.0;     // 进入这个距离才起手

    // 凋灵头：掷镰刀 1.6 秒（1.15 秒出手）→ 抬手 1 秒 → 发射 5 秒 → 镰刀飞回 → 后摇 3.4 秒
    private static final int THROW_LENGTH = at(1.6f);
    private static final int THROW_RELEASE = at(1.15f);
    private static final int RAISE_LENGTH = at(1.0f);
    private static final int FIRE_TICKS = 100 + BLEND;
    private static final int SKULL_SHOTS = 15;           // 每秒 3 发 × 5 秒
    private static final float SKULL_DAMAGE = 10.0f;
    private static final int SKULL_END_LENGTH = at(3.4f);
    private static final int SCYTHE_RETURN_TIMEOUT = 60;

    // 吸附：起手 1.5 秒，蓄力 10 秒；抓住后 0.5 秒斩下（斩击动画 0.25 秒命中）
    private static final int ABSORB_START_LENGTH = at(1.5f);
    private static final int ABSORB_CHARGE_TICKS = 200;
    private static final double PULL_SPEED = 0.1;        // 每秒 2 格
    private static final double PULL_RANGE = 24.0;
    private static final double GRAB_RANGE = 3.0;
    private static final int GRAB_HOLD = at(0.5f);
    private static final int SLASH_HIT = at(0.25f);
    private static final int SLASH_LENGTH = at(4.0f);
    private static final int ABSORB_END_LENGTH = at(3.2f);
    private static final float SLASH_DAMAGE = 100.0f;

    // 快速攻击：起手 0.5 秒，5 秒内每秒 3 刀（每秒第 4 / 11 / 17 tick 命中），后摇 3.2 秒
    private static final int RAPID_START_LENGTH = at(0.5f);
    private static final int RAPID_TICKS = 100 + BLEND;
    private static final int[] RAPID_HITS = {4, 11, 17};
    private static final float RAPID_DAMAGE = 5.0f;
    private static final double RAPID_RANGE = 4.5;
    private static final int RAPID_END_LENGTH = at(3.2f);

    // 大招死亡激光：起手 1.6 秒（高举凝聚魔法阵 → 放到胸前 → 张开双臂发射），发射 5 秒，后摇 3.2 秒
    private static final int LASER_START_LENGTH = at(1.6f);
    private static final int LASER_CIRCLE_FULL = at(0.7f);
    private static final int LASER_TICKS = 100;
    private static final int LASER_END_LENGTH = at(3.2f);
    public static final double LASER_LENGTH = 30.0;
    public static final double LASER_RADIUS = 1.0;
    private static final float LASER_DAMAGE = 6.0f;      // 每 5 tick 一次（无视护甲）
    private static final int LASER_DAMAGE_INTERVAL = 5;
    private static final double LASER_TRACKING = 0.25;   // 每 tick 追上目标位置的比例：奔跑的玩家会被落下约一个身位
    private static final double LASER_RANGE = 26.0;
    private static final int LASER_COOLDOWN = 600;
    private static final int LASER_BLOCKS_PER_TICK = 48;
    private static final double CHEST_HEIGHT = 2.56;     // 魔法阵 / 激光发射点：胸前（anims.py 输出的 chest point × 1.25 / 16）
    private static final double CHEST_FORWARD = 0.33;

    private static final int TOSS_LENGTH = at(4.6f);

    private static final DustParticleOptions PURPLE = new DustParticleOptions(new Vector3f(0.62f, 0.2f, 0.95f), 1.6f);

    private enum Attack { CLEAVE, SKULL, ABSORB, RAPID, LASER }

    private enum State {
        IDLE, APPROACH, CLEAVE, SKULL_THROW, SKULL_RAISE, SKULL_FIRE, SKULL_WAIT_RETURN, SKULL_END,
        ABSORB_START, ABSORB_CHARGE, ABSORB_GRAB, ABSORB_SLASH, ABSORB_END, RAPID_START, RAPID, RAPID_END,
        LASER_START, LASER_FIRE, LASER_END, TOSS
    }

    // ==================== 状态 ====================
    private boolean introDone = true;     // /summon 直接召唤时没有出场
    private long introStartNanos;
    private float introTime;
    private Vec3 home = Vec3.ZERO;
    private boolean homeSet;
    @Nullable
    private UUID focusPlayer;
    @Nullable
    private BlockPos lightPos;
    @Nullable
    private WitherHusk husk;

    private State state = State.IDLE;
    private int stateTick;
    private Attack pending = Attack.CLEAVE;
    private int cooldown = 40;
    private int tossCooldown = 300;
    private int laserCooldown = 300;
    @Nullable
    private Vec3 wanderTarget;
    private int wanderWait;
    private float driftPhase;
    private Vec3 cleaveDir = new Vec3(0, 0, 1);
    private int skullShots;
    @Nullable
    private ThrownScythe thrownScythe;
    @Nullable
    private LivingEntity grabbed;
    private int trailTicks;
    private int tickCounter;
    private Vec3 laserAim = Vec3.ZERO;

    // 客户端：残影
    public record Afterimage(double x, double y, double z, float yaw, float born) {
    }

    public static final int AFTERIMAGE_LIFE = 8;
    public final List<Afterimage> afterimages = new ArrayList<>();
    public boolean renderingAfterimages;
    private int seenAnimSeq = -1;

    // 客户端：渲染器记下的定位骨骼位置（世界坐标），用于在正确的位置放粒子；没渲染过时为 null
    @Nullable
    public Vec3 orbPos;
    @Nullable
    public Vec3 bladePos;
    @Nullable
    public Vec3 haloPos;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final ServerBossEvent bossEvent;

    public WeepingDeathLord(EntityType<? extends WeepingDeathLord> type, Level level) {
        super(type, level);
        this.xpReward = 500;
        Component name = Component.translatable("entity.sourceofmystery.weeping_death_lord")
                .withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD);
        this.setCustomName(name);
        this.setNoGravity(true);
        this.noPhysics = true;
        this.bossEvent = new ServerBossEvent(name, BossEvent.BossBarColor.WHITE, BossEvent.BossBarOverlay.NOTCHED_10);
        this.bossEvent.setVisible(false);
    }

    /** 动画里第 seconds 秒对应的服务端 tick（加上 main 控制器的过渡时间） */
    private static int at(float seconds) {
        return BLEND + Math.round(seconds * 20);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 2000.0)
                .add(Attributes.ARMOR, 20.0)
                .add(Attributes.ATTACK_DAMAGE, CLEAVE_DAMAGE)
                .add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.FOLLOW_RANGE, 64.0);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_HIDDEN, false);
        this.entityData.define(DATA_DRIFTING, false);
        this.entityData.define(DATA_COMBAT, false);
        this.entityData.define(DATA_SCYTHE_OUT, false);
        this.entityData.define(DATA_TRAIL, false);
        this.entityData.define(DATA_ORB, 0.0f);
        this.entityData.define(DATA_EYE_GLOW, 1.0f);
        this.entityData.define(DATA_ANIM, 0);
        this.entityData.define(DATA_ANIM_SEQ, 0);
        this.entityData.define(DATA_SCYTHE_CHARGE, 0.0f);
        this.entityData.define(DATA_CIRCLE, 0.0f);
        this.entityData.define(DATA_LASER, false);
        this.entityData.define(DATA_LASER_END, new Vector3f());
    }

    /**
     * 中立：只有伤害过她的才会成为目标（玩家也一样）；另外主动攻击家附近的动物和怪物（不打其他 Boss）
     */
    @Override
    protected void registerGoals() {
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Animal.class, 20, true, false, this::isPrey));
        this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, Mob.class, 20, true, false,
                e -> e instanceof Enemy && isPrey(e)));
    }

    private boolean isPrey(LivingEntity e) {
        return introDone && !(e instanceof WeepingDeathLord) && !(e instanceof WitherBoss) && !(e instanceof EnderDragon)
                && !(e instanceof DragonSoulBoss) && !(e instanceof DivineHeavenlyDaoBoss)
                && e.position().distanceTo(home()) < WANDER_RADIUS;
    }

    // ==================== 同步数据访问 ====================

    public void prepareHidden() {
        this.entityData.set(DATA_HIDDEN, true);
    }

    private void setHidden(boolean hidden) {
        this.entityData.set(DATA_HIDDEN, hidden);
    }

    public boolean hidden() {
        return this.entityData.get(DATA_HIDDEN);
    }

    public boolean potionInvisible() {
        return super.isInvisible();
    }

    @Override
    public boolean isInvisible() {
        return (hidden() && !renderingAfterimages) || super.isInvisible();
    }

    public boolean drifting() {
        return this.entityData.get(DATA_DRIFTING);
    }

    public boolean combat() {
        return this.entityData.get(DATA_COMBAT);
    }

    public boolean scytheOut() {
        return this.entityData.get(DATA_SCYTHE_OUT);
    }

    public boolean trail() {
        return this.entityData.get(DATA_TRAIL);
    }

    /** 吸附时左手虚空黑球的大小 0~1 */
    public float orbSize() {
        return this.entityData.get(DATA_ORB);
    }

    public float eyeGlow() {
        return this.entityData.get(DATA_EYE_GLOW);
    }

    /** 吸附蓄力时镰刀的黑色蓄力特效强度 0~1 */
    public float scytheCharge() {
        return this.entityData.get(DATA_SCYTHE_CHARGE);
    }

    /** 大招魔法阵大小 0~1 */
    public float circleSize() {
        return this.entityData.get(DATA_CIRCLE);
    }

    public boolean laserActive() {
        return this.entityData.get(DATA_LASER);
    }

    /** 激光终点（世界坐标） */
    public Vec3 laserEnd() {
        Vector3f v = this.entityData.get(DATA_LASER_END);
        return new Vec3(v.x(), v.y(), v.z());
    }

    private void startTrail(int ticks) {
        trailTicks = Math.max(trailTicks, ticks);
        this.entityData.set(DATA_TRAIL, true);
    }

    private Vec3 home() {
        if (!homeSet) {
            home = this.position();
            homeSet = true;
        }
        return home;
    }

    // ==================== 出场 ====================

    /**
     * 由 WeepingDeathLordSpawnHandler 在原版凋灵死亡的瞬间调用（生成前已经 prepareHidden）
     */
    public void beginIntro(ServerLevel level, WitherBoss wither, @Nullable Player focus) {
        this.introDone = false;
        this.introStartNanos = System.nanoTime();
        this.introTime = 0;
        this.home = this.position();
        this.homeSet = true;
        this.setHidden(true);
        this.entityData.set(DATA_EYE_GLOW, 0.0f);
        this.focusPlayer = focus == null ? null : focus.getUUID();
        faceFocus(level, 1.0f);
        // 整段出场是一个连续的动画；前 2 秒她还隐藏着
        play("intro");

        husk = ModEntities.WITHER_HUSK.get().create(level);
        if (husk != null) {
            husk.moveTo(wither.getX(), wither.getY(), wither.getZ(), wither.yBodyRot, 0);
            level.addFreshEntity(husk);
        }
        level.playSound(null, wither.getX(), wither.getY() + 2, wither.getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.HOSTILE, 4.0f, 0.5f);
        level.playSound(null, wither.getX(), wither.getY() + 2, wither.getZ(), SoundEvents.WITHER_DEATH, SoundSource.HOSTILE, 4.0f, 0.6f);
        Vec3 at = wither.position();
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(at) < CAMERA_RANGE * CAMERA_RANGE) {
                ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                        new WeepingIntroPacket(this.getId(), at.x, at.y, at.z, INTRO_TICKS));
            }
        }
    }

    /** 出场按现实时间推进（服务端卡顿也不会拖慢或缩短），和客户端的镜头时间表一致 */
    private void tickIntro(ServerLevel level) {
        float prev = introTime;
        introTime = (System.nanoTime() - introStartNanos) / 1.0e9f;
        float t = introTime;
        faceFocus(level, 0.2f);
        this.setDeltaMovement(Vec3.ZERO);

        if (t < INTRO_EXPLODE) {
            if (husk != null && tickCounter % 2 == 0) {
                level.sendParticles(ParticleTypes.END_ROD, husk.getX(), husk.getY() + 2.0, husk.getZ(),
                        2 + (int) (t * 6), 0.8, 1.2, 0.8, 0.02);
            }
            return;
        }
        if (prev < INTRO_EXPLODE) {
            explodeHusk(level);
            this.setHidden(false);
        }
        if (t < INTRO_CATCH) {
            floatingScytheParticles(level);
        }
        if (t >= INTRO_WAKE && prev <= INTRO_ROAR) {
            // 与 wl_intro 的睁眼进度一致：5 秒内慢慢睁开，完全睁开时光照 14（同火把）
            float u = Mth.clamp((t - INTRO_WAKE) / (INTRO_ROAR - INTRO_WAKE), 0.0f, 1.0f);
            float glow = (float) Math.pow(u, 1.15);
            this.entityData.set(DATA_EYE_GLOW, glow);
            updateEyeLight(level, Math.round(14 * glow));
        }
        if (prev < INTRO_ROAR && t >= INTRO_ROAR) {
            // 咆哮的声音由客户端镜头播放（不随距离衰减，很大声）；这里只给没进镜头的远处玩家放一份
            playFar(level, SoundEvents.WITHER_SPAWN, 0.8f);
            screenEffect(level, 4, 60, 3.0f, 64);
            level.sendParticles(ParticleTypes.SOUL, this.getX(), this.getY() + 2, this.getZ(), 80, 1.5, 1.5, 1.5, 0.25);
            level.sendParticles(ParticleTypes.LARGE_SMOKE, this.getX(), this.getY() + 2, this.getZ(), 60, 1.0, 1.0, 1.0, 0.2);
            level.sendParticles(ParticleTypes.SONIC_BOOM, this.getX(), this.getY() + 2.4, this.getZ(), 1, 0, 0, 0, 0);
        }
        if (prev < INTRO_SHOULDER && t >= INTRO_SHOULDER) {
            this.playSound(ModSounds.WEEPING_THROW.get(), 2.0f, 0.6f);
        }
        if (prev < INTRO_CATCH && t >= INTRO_CATCH) {
            this.playSound(SoundEvents.ARMOR_EQUIP_NETHERITE, 2.5f, 0.6f);
            this.playSound(SoundEvents.ANVIL_PLACE, 1.0f, 0.5f);
        }
        if (t >= INTRO_LENGTH) {
            finishIntro(level);
        }
    }

    private void explodeHusk(ServerLevel level) {
        Vec3 at = husk != null ? husk.position().add(0, 1.8, 0) : this.position().add(0, 1.8, 0);
        if (husk != null) {
            husk.discard();
            husk = null;
        }
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, at.x, at.y, at.z, 2, 0.5, 0.5, 0.5, 0);
        level.sendParticles(ParticleTypes.END_ROD, at.x, at.y, at.z, 120, 0.4, 0.4, 0.4, 0.45);
        level.sendParticles(ParticleTypes.WHITE_ASH, at.x, at.y, at.z, 200, 3.0, 3.0, 3.0, 0.05);
        level.playSound(null, at.x, at.y, at.z, ModSounds.WEEPING_HUSK_BURST.get(), SoundSource.HOSTILE, 6.0f, 1.0f);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 6.0f, 0.5f);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.WITHER_BREAK_BLOCK, SoundSource.HOSTILE, 4.0f, 0.6f);
        screenEffect(level, 12, 25, 2.5f, 96);
    }

    private void floatingScytheParticles(ServerLevel level) {
        Vec3 right = rightVec();
        Vec3 base = this.position().add(right.scale(1.9)).add(forward().scale(0.6));
        for (int i = 0; i < 2; i++) {
            double h = 0.6 + this.random.nextDouble() * 3.6;
            level.sendParticles(PURPLE, base.x, base.y + h, base.z, 1, 0.12, 0.1, 0.12, 0);
        }
        if (tickCounter % 3 == 0) {
            level.sendParticles(ParticleTypes.WITCH, base.x, base.y + 3.2, base.z, 2, 0.4, 0.3, 0.4, 0);
            level.sendParticles(ParticleTypes.REVERSE_PORTAL, base.x, base.y + 2.0, base.z, 3, 0.2, 1.2, 0.2, 0.02);
        }
    }

    /** 眼睛的白光：在头部放一个光源方块，亮度随睁眼程度增加（最亮 14，同火把） */
    private void updateEyeLight(ServerLevel level, int brightness) {
        BlockPos pos = BlockPos.containing(this.getX(), this.getY() + 2.6, this.getZ());
        if (lightPos != null && !lightPos.equals(pos)) {
            removeEyeLight(level);
        }
        BlockState current = level.getBlockState(pos);
        if (!current.isAir() && !current.is(Blocks.LIGHT)) {
            return;
        }
        if (brightness <= 0) {
            removeEyeLight(level);
            return;
        }
        if (!current.is(Blocks.LIGHT) || current.getValue(LightBlock.LEVEL) != brightness) {
            level.setBlock(pos, Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, brightness), 3);
        }
        lightPos = pos;
    }

    private void removeEyeLight(ServerLevel level) {
        if (lightPos != null && level.getBlockState(lightPos).is(Blocks.LIGHT)) {
            level.removeBlock(lightPos, false);
        }
        lightPos = null;
    }

    private void finishIntro(ServerLevel level) {
        introDone = true;
        this.setHidden(false);
        this.entityData.set(DATA_EYE_GLOW, 1.0f);
        removeEyeLight(level);
        play("idle");
        enterState(State.IDLE);
        cooldown = 40;
        WeepingDeathLordSpawnHandler.announceArrival(level, this);
    }

    private void faceFocus(ServerLevel level, float speed) {
        Player focus = focusPlayer == null ? null : level.getPlayerByUUID(focusPlayer);
        if (focus == null || focus.distanceToSqr(this) > CAMERA_RANGE * CAMERA_RANGE) {
            focus = level.getNearestPlayer(this, CAMERA_RANGE);
        }
        if (focus != null) {
            faceDirection(focus.position().subtract(this.position()), speed);
        }
    }

    private void screenEffect(ServerLevel level, int flash, int shake, float strength, double range) {
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(this) < range * range) {
                ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new ScreenEffectPacket(flash, shake, strength));
            }
        }
    }

    /** 只给出场镜头范围外的玩家播放（镜头里的玩家由客户端播放不衰减的版本） */
    private void playFar(ServerLevel level, SoundEvent sound, float pitch) {
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(this) >= CAMERA_RANGE * CAMERA_RANGE) {
                player.playNotifySound(sound, SoundSource.HOSTILE, 8.0f, pitch);
            }
        }
    }

    /** 在某处播放一层声音；重要的瞬间会同时叠几层，听起来更厚重 */
    private void sound(Vec3 at, SoundEvent sound, float volume, float pitch) {
        this.level().playSound(null, at.x, at.y, at.z, sound, SoundSource.HOSTILE, volume, pitch);
    }

    // ==================== 主循环 ====================

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide) {
            tickAfterimages();
            tickClientEffects();
            return;
        }
        ServerLevel level = (ServerLevel) this.level();
        tickCounter++;
        home();
        if (trailTicks > 0 && --trailTicks == 0) {
            this.entityData.set(DATA_TRAIL, false);
        }
        if (!introDone) {
            tickIntro(level);
            return;
        }
        LivingEntity target = validTarget();
        boolean fighting = target != null || state != State.IDLE && state != State.TOSS;
        this.entityData.set(DATA_COMBAT, fighting);
        this.bossEvent.setVisible(target instanceof Player);
        this.bossEvent.setProgress(this.getHealth() / this.getMaxHealth());
        if (tossCooldown > 0) tossCooldown--;
        if (laserCooldown > 0) laserCooldown--;

        stateTick++;
        switch (state) {
            case IDLE -> tickIdle(level, target);
            case APPROACH -> tickApproach(level, target);
            case TOSS -> {
                hoverInPlace(level);
                if (stateTick >= TOSS_LENGTH) {
                    play("idle");
                    enterState(State.IDLE);
                }
            }
            case CLEAVE -> tickCleave(level, target);
            case SKULL_THROW, SKULL_RAISE, SKULL_FIRE, SKULL_WAIT_RETURN, SKULL_END -> tickSkull(level, target);
            case ABSORB_START, ABSORB_CHARGE, ABSORB_GRAB, ABSORB_SLASH, ABSORB_END -> tickAbsorb(level, target);
            case RAPID_START, RAPID, RAPID_END -> tickRapid(level, target);
            case LASER_START, LASER_FIRE, LASER_END -> tickLaser(level, target);
        }
        this.entityData.set(DATA_DRIFTING, state == State.IDLE && this.getDeltaMovement().horizontalDistanceSqr() > 0.0016
                || state == State.APPROACH);
    }

    @Nullable
    private LivingEntity validTarget() {
        LivingEntity target = this.getTarget();
        if (target == null) {
            return null;
        }
        boolean invalid = !target.isAlive() || target.isRemoved()
                || target instanceof Player p && (p.isCreative() || p.isSpectator())
                || target.position().distanceTo(home()) > LEASH_RADIUS
                || target.distanceToSqr(this) > 80 * 80;
        if (invalid) {
            this.setTarget(null);
            return null;
        }
        return target;
    }

    private void enterState(State next) {
        state = next;
        stateTick = 0;
    }

    /** 切换 main 控制器的动画（客户端带过渡地接过去）；同一个动画再播一次也会从头开始 */
    private void play(String anim) {
        int id = 0;
        for (int i = 0; i < ANIMS.length; i++) {
            if (ANIMS[i].equals(anim)) {
                id = i;
                break;
            }
        }
        this.entityData.set(DATA_ANIM, id);
        this.entityData.set(DATA_ANIM_SEQ, this.entityData.get(DATA_ANIM_SEQ) + 1);
    }

    /**
     * 客户端：快速移动时在上一位置和当前位置之间补残影
     */
    private void tickAfterimages() {
        float now = this.tickCount;
        afterimages.removeIf(a -> now - a.born() > AFTERIMAGE_LIFE);
        boolean moved = this.distanceToSqr(this.xo, this.yo, this.zo) > 0.01;
        if (trail() && !potionInvisible()) {
            int count = moved ? 3 : 1;
            for (int i = 0; i < count; i++) {
                double u = i / (double) count;
                afterimages.add(new Afterimage(Mth.lerp(u, this.xo, this.getX()), Mth.lerp(u, this.yo, this.getY()),
                        Mth.lerp(u, this.zo, this.getZ()), this.yBodyRot, now - 1 + (float) u));
            }
        }
    }

    /**
     * 客户端特效（位置来自渲染器记下的骨骼位置，跟着动画走）：
     * 背后神环的死亡气息和偶尔冒出的骷髅头、吸附时被吸进虚空黑球的黑色粒子、镰刀蓄力的黑色粒子
     */
    private void tickClientEffects() {
        if (hidden() || potionInvisible()) {
            return;
        }
        Level level = this.level();
        if (haloPos != null) {
            if (this.random.nextInt(3) == 0) {
                double a = this.random.nextDouble() * Math.PI * 2;
                double r = 0.9 + this.random.nextDouble() * 0.3;
                Vec3 side = rightVec();
                Vec3 p = haloPos.add(side.scale(Math.cos(a) * r)).add(0, Math.sin(a) * r, 0);
                level.addParticle(ParticleTypes.SMOKE, p.x, p.y, p.z, 0, 0.015, 0);
            }
            if (this.random.nextInt(40) == 0) {
                double a = this.random.nextDouble() * Math.PI * 2;
                Vec3 p = haloPos.add(rightVec().scale(Math.cos(a) * 1.0)).add(0, Math.sin(a) * 1.0, 0);
                level.addParticle(ModParticles.DEATH_SKULL.get(), p.x, p.y, p.z, 0, 0.03, 0);
            }
        }
        float orb = orbSize();
        if (orb > 0.01f && orbPos != null) {
            int n = 2 + (int) (orb * 6);
            for (int i = 0; i < n; i++) {
                Vec3 dir = new Vec3(this.random.nextGaussian(), this.random.nextGaussian(), this.random.nextGaussian()).normalize();
                double r = 1.2 + orb * 2.0 + this.random.nextDouble();
                Vec3 from = orbPos.add(dir.scale(r));
                level.addParticle(ModParticles.DARK_MOTE.get(), from.x, from.y, from.z,
                        orbPos.x - from.x, orbPos.y - from.y, orbPos.z - from.z);
            }
        }
        float charge = scytheCharge();
        if (charge > 0.01f && bladePos != null) {
            // 黑色的火焰从刀刃上升腾，周围的黑气不断汇入刀刃
            int n = 2 + (int) (charge * 5);
            for (int i = 0; i < n; i++) {
                Vec3 dir = new Vec3(this.random.nextGaussian(), this.random.nextGaussian() * 0.6, this.random.nextGaussian()).normalize();
                Vec3 from = bladePos.add(dir.scale(1.4 + this.random.nextDouble() * 1.2));
                level.addParticle(ModParticles.DARK_MOTE.get(), from.x, from.y, from.z,
                        bladePos.x - from.x, bladePos.y - from.y, bladePos.z - from.z);
            }
            for (int i = 0; i < 2; i++) {
                double ox = (this.random.nextDouble() - 0.5) * 1.2;
                double oy = (this.random.nextDouble() - 0.5) * 1.2;
                double oz = (this.random.nextDouble() - 0.5) * 1.2;
                level.addParticle(ParticleTypes.LARGE_SMOKE, bladePos.x + ox, bladePos.y + oy, bladePos.z + oz, 0, 0.05 + charge * 0.05, 0);
            }
            if (this.random.nextInt(3) == 0) {
                level.addParticle(ParticleTypes.SQUID_INK, bladePos.x, bladePos.y, bladePos.z,
                        (this.random.nextDouble() - 0.5) * 0.1, 0.08, (this.random.nextDouble() - 0.5) * 0.1);
            }
            if (this.random.nextInt(5) == 0) {
                level.addParticle(ParticleTypes.REVERSE_PORTAL, bladePos.x, bladePos.y, bladePos.z, 0, 0.05, 0);
            }
        }
    }

    /** 不用原版移动，位移全部在各状态里显式计算 */
    @Override
    public void travel(Vec3 travelVector) {
    }

    // ==================== 待机 / 漂荡 ====================

    private void tickIdle(ServerLevel level, @Nullable LivingEntity target) {
        if (target == null) {
            wander(level);
            if (tossCooldown <= 0 && this.random.nextInt(200) == 0) {
                startToss();
            }
            return;
        }
        faceTowards(target, 0.25f);
        if (cooldown > 0) {
            cooldown--;
            // 出招间隙：保持距离漂浮，偶尔抛接镰刀
            keepDistance(level, target, 9.0, DRIFT_SPEED);
            if (cooldown > TOSS_LENGTH && tossCooldown <= 0 && this.random.nextInt(4) == 0) {
                startToss();
            }
            return;
        }
        pending = chooseAttack(target);
        enterState(State.APPROACH);
    }

    private void startToss() {
        tossCooldown = 400 + this.random.nextInt(400);
        this.setDeltaMovement(Vec3.ZERO);
        play("toss");
        enterState(State.TOSS);
    }

    private void wander(ServerLevel level) {
        if (wanderTarget == null || this.position().distanceTo(wanderTarget) < 2.0) {
            this.setDeltaMovement(this.getDeltaMovement().scale(0.85));
            hoverInPlace(level);
            if (--wanderWait <= 0) {
                double a = this.random.nextDouble() * Math.PI * 2;
                double r = Math.sqrt(this.random.nextDouble()) * WANDER_RADIUS;
                Vec3 h = home();
                double x = h.x + Math.cos(a) * r;
                double z = h.z + Math.sin(a) * r;
                wanderTarget = new Vec3(x, groundY(level, x, z, h.y) + HOVER_HEIGHT + this.random.nextDouble() * 4, z);
                wanderWait = 60 + this.random.nextInt(140);
            }
            return;
        }
        driftTowards(level, wanderTarget, DRIFT_SPEED);
    }

    /**
     * 水母式漂浮：速度随 2.4 秒的收缩 / 舒张节奏起伏，收缩时向前冲
     */
    private void driftTowards(ServerLevel level, Vec3 dest, double speed) {
        driftPhase = (driftPhase + 1.0f / 48.0f) % 1.0f;
        double pulse = driftPhase < 0.3 ? Math.sin(driftPhase / 0.3 * Math.PI / 2) : Math.cos((driftPhase - 0.3) / 0.7 * Math.PI / 2);
        double v = speed * (0.35 + 0.9 * pulse);
        Vec3 to = dest.subtract(this.position());
        double ground = groundY(level, this.getX(), this.getZ(), this.getY());
        double minY = ground + HOVER_HEIGHT;
        if (this.getY() + to.y < minY) {
            to = new Vec3(to.x, minY - this.getY(), to.z);
        }
        Vec3 step = to.length() > v ? to.normalize().scale(v) : to;
        moveBy(step);
        faceDirection(step, 0.12f);
    }

    private void hoverInPlace(ServerLevel level) {
        double ground = groundY(level, this.getX(), this.getZ(), this.getY());
        double dy = 0;
        if (this.getY() < ground + HOVER_HEIGHT) {
            dy = Math.min(0.1, ground + HOVER_HEIGHT - this.getY());
        }
        moveBy(new Vec3(0, dy, 0));
        this.setDeltaMovement(Vec3.ZERO);
    }

    private void keepDistance(ServerLevel level, LivingEntity target, double distance, double speed) {
        Vec3 from = horizontal(this.position().subtract(target.position()));
        if (from.lengthSqr() < 1.0E-4) {
            from = new Vec3(1, 0, 0);
        }
        Vec3 spot = target.position().add(from.normalize().scale(distance));
        spot = new Vec3(spot.x, Math.max(target.getY() + HOVER_HEIGHT, groundY(level, spot.x, spot.z, target.getY()) + HOVER_HEIGHT), spot.z);
        if (spot.distanceTo(home()) > LEASH_RADIUS) {
            hoverInPlace(level);
            return;
        }
        if (this.position().distanceTo(spot) > 1.0) {
            Vec3 step = spot.subtract(this.position());
            moveBy(step.length() > speed ? step.normalize().scale(speed) : step);
        } else {
            hoverInPlace(level);
        }
    }

    private void moveBy(Vec3 step) {
        this.setDeltaMovement(step);
        this.setPos(this.getX() + step.x, this.getY() + step.y, this.getZ() + step.z);
    }

    // ==================== 出招选择 ====================

    private Attack chooseAttack(LivingEntity target) {
        double d = this.distanceTo(target);
        if (!(target instanceof Player)) {
            return this.random.nextBoolean() ? Attack.CLEAVE : Attack.RAPID;
        }
        if (laserCooldown <= 0 && d < LASER_RANGE && this.random.nextInt(3) == 0) {
            return Attack.LASER;
        }
        int roll = this.random.nextInt(10);
        if (d > 14) {
            return roll < 5 ? Attack.SKULL : roll < 8 ? Attack.ABSORB : Attack.CLEAVE;
        }
        return roll < 3 ? Attack.CLEAVE : roll < 6 ? Attack.RAPID : roll < 8 ? Attack.ABSORB : Attack.SKULL;
    }

    private double attackRange(Attack attack) {
        return switch (attack) {
            case CLEAVE -> CLEAVE_RANGE;
            case RAPID -> 4.0;
            case SKULL -> 26.0;
            case ABSORB -> PULL_RANGE - 4;
            case LASER -> LASER_RANGE - 4;
        };
    }

    private void tickApproach(ServerLevel level, @Nullable LivingEntity target) {
        if (target == null) {
            play("idle");
            enterState(State.IDLE);
            return;
        }
        faceTowards(target, 0.3f);
        double range = attackRange(pending);
        if (this.distanceTo(target) <= range || stateTick > 160) {
            startAttack(level, target);
            return;
        }
        Vec3 dest = target.position().add(0, HOVER_HEIGHT, 0);
        if (dest.distanceTo(home()) > LEASH_RADIUS) {
            play("idle");
            enterState(State.IDLE);
            cooldown = 40;
            return;
        }
        driftTowards(level, dest, CHASE_SPEED);
        faceTowards(target, 0.3f);
    }

    private void startAttack(ServerLevel level, LivingEntity target) {
        this.setDeltaMovement(Vec3.ZERO);
        switch (pending) {
            case CLEAVE -> {
                play("cleave");
                sound(this.position(), ModSounds.WEEPING_SWING.get(), 2.0f, 0.45f);
                sound(this.position(), SoundEvents.WITHER_AMBIENT, 2.0f, 0.5f);
                enterState(State.CLEAVE);
            }
            case SKULL -> {
                play("skull_throw");
                enterState(State.SKULL_THROW);
            }
            case ABSORB -> {
                play("absorb_start");
                sound(this.position(), ModSounds.WEEPING_CHARGE.get(), 2.0f, 0.6f);
                sound(this.position(), SoundEvents.RESPAWN_ANCHOR_CHARGE, 2.0f, 0.5f);
                enterState(State.ABSORB_START);
            }
            case RAPID -> {
                play("rapid_start");
                enterState(State.RAPID_START);
            }
            case LASER -> {
                play("laser_start");
                laserCooldown = LASER_COOLDOWN;
                laserAim = target.position().add(0, target.getBbHeight() * 0.5, 0);
                sound(this.position(), ModSounds.WEEPING_LASER_CHARGE.get(), 3.0f, 1.0f);
                sound(this.position(), SoundEvents.BEACON_ACTIVATE, 3.0f, 0.5f);
                enterState(State.LASER_START);
            }
        }
    }

    /** 收招：所有招式特效归零，回到待机 */
    private void endAttack(int extraCooldown) {
        clearEffects();
        play("idle");
        enterState(State.IDLE);
        cooldown = 50 + this.random.nextInt(50) + extraCooldown;
    }

    private void clearEffects() {
        this.entityData.set(DATA_ORB, 0.0f);
        this.entityData.set(DATA_SCYTHE_CHARGE, 0.0f);
        this.entityData.set(DATA_CIRCLE, 0.0f);
        this.entityData.set(DATA_LASER, false);
    }

    // ==================== 劈斩 ====================

    private void tickCleave(ServerLevel level, @Nullable LivingEntity target) {
        if (stateTick < CLEAVE_AIM_LOCK) {
            faceTowards(target, 0.3f);
            cleaveDir = forward();
        }
        hoverInPlace(level);
        if (stateTick == CLEAVE_HIT - 6) {
            sound(this.position(), ModSounds.WEEPING_SWING.get(), 2.5f, 0.4f);
            sound(this.position(), ModSounds.WEEPING_WHOOSH.get(), 2.5f, 0.5f);
        }
        if (stateTick == CLEAVE_HIT) {
            cleaveImpact(level);
        }
        if (stateTick > CLEAVE_HIT && stateTick < CLEAVE_HIT + 14 && stateTick % 3 == 0) {
            quakeRing(level, (stateTick - CLEAVE_HIT) / 14.0 * QUAKE_RADIUS);
        }
        if (stateTick >= CLEAVE_LENGTH) {
            endAttack(0);
        }
    }

    private Vec3 cleaveImpactPoint(ServerLevel level) {
        Vec3 p = this.position().add(cleaveDir.scale(CLEAVE_REACH));
        double ground = groundY(level, p.x, p.z, this.getY() - 2);
        if (ground > this.getY() + 2 || ground < this.getY() - 8) {
            ground = this.getY() - HOVER_HEIGHT;
        }
        return new Vec3(p.x, ground, p.z);
    }

    /** 镰刀砸地：落点一圈的地面上的生物受到地震伤害，并被震起 */
    private void cleaveImpact(ServerLevel level) {
        Vec3 impact = cleaveImpactPoint(level);
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, impact.x, impact.y + 0.5, impact.z, 1, 0, 0, 0, 0);
        level.sendParticles(ParticleTypes.SWEEP_ATTACK, impact.x, impact.y + 1.0, impact.z, 6, 2.0, 0.3, 2.0, 0);
        level.sendParticles(ParticleTypes.SOUL, impact.x, impact.y + 0.5, impact.z, 30, 2.0, 0.4, 2.0, 0.08);
        sound(impact, ModSounds.WEEPING_QUAKE.get(), 4.0f, 1.0f);
        sound(impact, SoundEvents.ANVIL_LAND, 4.0f, 0.5f);
        sound(impact, ModSounds.WEEPING_IMPACT.get(), 4.0f, 0.5f);
        screenEffect(level, 0, 25, 3.0f, 32);
        List<LivingEntity> victims = level.getEntitiesOfClass(LivingEntity.class,
                new AABB(impact, impact).inflate(QUAKE_RADIUS + 1, 4, QUAKE_RADIUS + 1),
                e -> canHit(e) && horizontal(e.position().subtract(impact)).length() <= QUAKE_RADIUS + e.getBbWidth() / 2
                        && e.getY() - impact.y < 3.0 && e.getY() - impact.y > -3.0);
        for (LivingEntity victim : victims) {
            if (victim.hurt(this.damageSources().mobAttack(this), CLEAVE_DAMAGE)) {
                Vec3 push = horizontal(victim.position().subtract(impact));
                push = push.lengthSqr() < 1.0E-4 ? Vec3.ZERO : push.normalize().scale(0.6);
                victim.push(push.x, 0.55, push.z);
                victim.hurtMarked = true;
            }
        }
    }

    private void quakeRing(ServerLevel level, double radius) {
        Vec3 c = cleaveImpactPoint(level);
        BlockState ground = level.getBlockState(BlockPos.containing(c.x, c.y - 0.5, c.z));
        if (ground.isAir()) {
            ground = Blocks.STONE.defaultBlockState();
        }
        BlockParticleOption dust = new BlockParticleOption(ParticleTypes.BLOCK, ground);
        int points = 10 + (int) (radius * 5);
        for (int i = 0; i < points; i++) {
            double a = i * Math.PI * 2 / points;
            level.sendParticles(dust, c.x + Math.cos(a) * radius, c.y + 0.1, c.z + Math.sin(a) * radius, 3, 0.2, 0.1, 0.2, 0.15);
        }
    }

    // ==================== 凋灵头 ====================

    private void tickSkull(ServerLevel level, @Nullable LivingEntity target) {
        hoverInPlace(level);
        switch (state) {
            case SKULL_THROW -> {
                if (stateTick <= THROW_RELEASE) {
                    faceTowards(target, 0.35f);
                }
                if (stateTick == THROW_RELEASE - 4) {
                    sound(this.position(), ModSounds.WEEPING_SWING.get(), 2.0f, 0.6f);
                }
                if (stateTick == THROW_RELEASE) {
                    throwScythe(level, target);
                }
                if (stateTick >= THROW_LENGTH) {
                    play("skull_raise");
                    sound(this.position(), SoundEvents.WITHER_AMBIENT, 2.0f, 0.6f);
                    sound(this.position(), SoundEvents.SOUL_ESCAPE, 2.0f, 0.6f);
                    enterState(State.SKULL_RAISE);
                }
            }
            case SKULL_RAISE -> {
                faceTowards(target, 0.3f);
                if (stateTick >= RAISE_LENGTH) {
                    play("skull_fire");
                    skullShots = 0;
                    enterState(State.SKULL_FIRE);
                }
            }
            case SKULL_FIRE -> {
                faceTowards(target, 0.3f);
                // 每秒 3 发：第 k 发在 BLEND + k * 20 / 3 tick，右、左手交替（和 wl_skull_fire 的后坐力一致）
                while (skullShots < SKULL_SHOTS && stateTick >= BLEND + Math.round(skullShots * 20 / 3.0f)) {
                    fireSkull(level, target, skullShots % 2 == 0);
                    skullShots++;
                }
                if (stateTick >= FIRE_TICKS) {
                    recallScythe();
                    if (state == State.SKULL_FIRE) {
                        enterState(State.SKULL_WAIT_RETURN);
                    }
                }
            }
            case SKULL_WAIT_RETURN -> {
                faceTowards(target, 0.2f);
                if (stateTick > SCYTHE_RETURN_TIMEOUT) {
                    onScytheReturned();
                }
            }
            case SKULL_END -> {
                if (stateTick >= SKULL_END_LENGTH) {
                    endAttack(20);
                }
            }
            default -> {
            }
        }
    }

    private void throwScythe(ServerLevel level, @Nullable LivingEntity target) {
        Vec3 origin = this.position().add(0, 2.6, 0).add(rightVec().scale(0.6));
        Vec3 aim = target != null ? target.position().add(0, target.getBbHeight() * 0.5, 0).add(target.getDeltaMovement().scale(6))
                : origin.add(forward().scale(16));
        Vec3 velocity = aim.subtract(origin).normalize().scale(1.5);
        thrownScythe = new ThrownScythe(level, this, origin, velocity);
        level.addFreshEntity(thrownScythe);
        this.entityData.set(DATA_SCYTHE_OUT, true);
        sound(origin, ModSounds.WEEPING_THROW.get(), 2.0f, 0.7f);
        sound(origin, ModSounds.WEEPING_WHOOSH.get(), 2.0f, 0.6f);
    }

    private void recallScythe() {
        if (thrownScythe != null && thrownScythe.isAlive()) {
            thrownScythe.recall();
        } else {
            onScytheReturned();
        }
    }

    /** 镰刀飞回手中（ThrownScythe 到达时调用，超时也会调用） */
    public void onScytheReturned() {
        if (thrownScythe != null) {
            thrownScythe.releasePinned();
            if (thrownScythe.isAlive()) {
                thrownScythe.discard();
            }
            thrownScythe = null;
        }
        if (!scytheOut()) {
            return;
        }
        this.entityData.set(DATA_SCYTHE_OUT, false);
        sound(handPosition(), SoundEvents.TRIDENT_RETURN, 2.0f, 0.6f);
        sound(handPosition(), SoundEvents.ARMOR_EQUIP_NETHERITE, 2.0f, 0.7f);
        if (state == State.SKULL_WAIT_RETURN || state == State.SKULL_FIRE || state == State.SKULL_RAISE || state == State.SKULL_THROW) {
            play("skull_end");
            enterState(State.SKULL_END);
        }
    }

    /** 镰刀握在右手时的大致位置（ThrownScythe 飞回的目标） */
    public Vec3 handPosition() {
        return this.position().add(rightVec().scale(0.7)).add(forward().scale(0.6)).add(0, 1.9, 0);
    }

    private void fireSkull(ServerLevel level, @Nullable LivingEntity target, boolean rightHand) {
        Vec3 side = rightVec().scale(rightHand ? 0.9 : -0.9);
        Vec3 origin = this.position().add(side).add(forward().scale(1.6)).add(0, 2.3, 0);
        Vec3 aim = target != null ? target.position().add(0, target.getBbHeight() * 0.55, 0)
                : origin.add(forward().scale(20));
        Vec3 dir = aim.subtract(origin).normalize();
        DeathSkull skull = new DeathSkull(level, this, origin, dir, SKULL_DAMAGE);
        level.addFreshEntity(skull);
        level.playSound(null, origin.x, origin.y, origin.z, ModSounds.WEEPING_SKULL.get(), SoundSource.HOSTILE, 1.5f, 0.9f + this.random.nextFloat() * 0.2f);
        level.sendParticles(ParticleTypes.SMOKE, origin.x, origin.y, origin.z, 6, 0.15, 0.15, 0.15, 0.02);
    }

    // ==================== 吸附 ====================

    private void tickAbsorb(ServerLevel level, @Nullable LivingEntity target) {
        hoverInPlace(level);
        switch (state) {
            case ABSORB_START -> {
                faceTowards(target, 0.3f);
                float u = stateTick / (float) ABSORB_START_LENGTH;
                this.entityData.set(DATA_ORB, 0.2f * u);
                this.entityData.set(DATA_SCYTHE_CHARGE, 0.4f * u);
                if (stateTick >= ABSORB_START_LENGTH) {
                    play("absorb_hold");
                    enterState(State.ABSORB_CHARGE);
                }
            }
            case ABSORB_CHARGE -> {
                faceTowards(target, 0.2f);
                float progress = stateTick / (float) ABSORB_CHARGE_TICKS;
                this.entityData.set(DATA_ORB, 0.2f + 0.8f * progress);
                this.entityData.set(DATA_SCYTHE_CHARGE, 0.4f + 0.6f * progress);
                if (stateTick % 40 == 1) {
                    sound(this.position(), ModSounds.WEEPING_CHARGE.get(), 2.0f, 0.6f + progress * 0.5f);
                    sound(this.position(), SoundEvents.PORTAL_AMBIENT, 2.0f, 0.5f + progress * 0.3f);
                }
                Player caught = pullVictims(level);
                if (caught != null) {
                    grab(level, caught);
                    return;
                }
                if (stateTick >= ABSORB_CHARGE_TICKS) {
                    // 没有抓到人：黑球消散
                    Vec3 orb = orbPosition();
                    level.sendParticles(ParticleTypes.LARGE_SMOKE, orb.x, orb.y, orb.z, 30, 0.3, 0.3, 0.3, 0.05);
                    level.sendParticles(ParticleTypes.SQUID_INK, orb.x, orb.y, orb.z, 20, 0.3, 0.3, 0.3, 0.1);
                    sound(orb, SoundEvents.FIRE_EXTINGUISH, 2.0f, 0.5f);
                    sound(orb, SoundEvents.SOUL_ESCAPE, 2.0f, 0.5f);
                    clearEffects();
                    play("absorb_end");
                    enterState(State.ABSORB_END);
                }
            }
            case ABSORB_GRAB -> {
                holdGrabbed();
                if (stateTick >= GRAB_HOLD) {
                    play("absorb_slash");
                    sound(this.position(), ModSounds.WEEPING_SWING.get(), 2.5f, 0.45f);
                    sound(this.position(), ModSounds.WEEPING_WHOOSH.get(), 2.5f, 0.6f);
                    enterState(State.ABSORB_SLASH);
                }
            }
            case ABSORB_SLASH -> {
                if (stateTick < SLASH_HIT) {
                    holdGrabbed();
                }
                if (stateTick == SLASH_HIT) {
                    slashGrabbed(level);
                }
                if (stateTick >= SLASH_LENGTH) {
                    endAttack(40);
                }
            }
            case ABSORB_END -> {
                if (stateTick >= ABSORB_END_LENGTH) {
                    endAttack(20);
                }
            }
            default -> {
            }
        }
    }

    private Vec3 orbPosition() {
        return this.position().add(rightVec().scale(-0.6)).add(forward().scale(1.0)).add(0, 1.8, 0);
    }

    /**
     * 把 PULL_RANGE 内的生物每秒往她身边拉 2 格（拼命跑还是跑得掉）；有玩家进入 3 格就返回该玩家
     */
    @Nullable
    private Player pullVictims(ServerLevel level) {
        Player caught = null;
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, this.getBoundingBox().inflate(PULL_RANGE), this::canHit)) {
            Vec3 to = this.position().add(0, 0.5, 0).subtract(e.position());
            double dist = horizontal(to).length() - this.getBbWidth() / 2;
            if (e instanceof Player p && dist <= GRAB_RANGE) {
                caught = p;
                continue;
            }
            if (to.length() > PULL_RANGE) {
                continue;
            }
            Vec3 pull = to.normalize().scale(PULL_SPEED);
            e.setDeltaMovement(pull.x, e.getDeltaMovement().y + pull.y * 0.5, pull.z);
            e.hurtMarked = true;
            if (tickCounter % 4 == 0) {
                level.sendParticles(ParticleTypes.SMOKE, e.getX(), e.getY() + 1, e.getZ(), 2, 0.2, 0.4, 0.2, 0.01);
            }
        }
        return caught;
    }

    private Vec3 holdSpot() {
        return this.position().add(forward().scale(2.2)).add(0, 1.2, 0);
    }

    private void grab(ServerLevel level, Player victim) {
        grabbed = victim;
        faceTowards(victim, 1.0f);
        victim.setNoGravity(true);
        if (victim instanceof ServerPlayer sp) {
            ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> sp), new RootPacket(GRAB_HOLD + SLASH_HIT + 10));
        }
        holdGrabbed();
        sound(holdSpot(), ModSounds.WEEPING_GRAB.get(), 2.0f, 0.8f);
        sound(holdSpot(), ModSounds.WEEPING_HEARTBEAT.get(), 2.0f, 0.6f);
        // 抓住的瞬间黑球被捏碎，镰刀上的黑气收进刀刃
        Vec3 orb = orbPosition();
        level.sendParticles(ParticleTypes.SQUID_INK, orb.x, orb.y, orb.z, 25, 0.3, 0.3, 0.3, 0.12);
        this.entityData.set(DATA_ORB, 0.0f);
        play("absorb_grab");
        enterState(State.ABSORB_GRAB);
    }

    /** 被抓的人强制悬浮在她身前，动弹不得 */
    private void holdGrabbed() {
        if (grabbed == null || !grabbed.isAlive()) {
            return;
        }
        Vec3 spot = holdSpot();
        if (grabbed instanceof ServerPlayer sp) {
            if (sp.position().distanceToSqr(spot) > 0.04) {
                sp.connection.teleport(spot.x, spot.y, spot.z, sp.getYRot(), sp.getXRot());
            }
        } else {
            grabbed.teleportTo(spot.x, spot.y, spot.z);
        }
        grabbed.setDeltaMovement(Vec3.ZERO);
        grabbed.fallDistance = 0;
    }

    private void slashGrabbed(ServerLevel level) {
        Vec3 at = grabbed != null ? grabbed.position().add(0, grabbed.getBbHeight() * 0.5, 0) : holdSpot();
        SpaceShatter shatter = ModEntities.SPACE_SHATTER.get().create(level);
        if (shatter != null) {
            shatter.moveTo(at.x, at.y, at.z, this.getYRot(), 0);
            level.addFreshEntity(shatter);
        }
        sound(at, ModSounds.WEEPING_SHATTER.get(), 3.0f, 1.0f);
        sound(at, ModSounds.WEEPING_SHATTER_RING.get(), 3.0f, 0.5f);
        sound(at, SoundEvents.GENERIC_EXPLODE, 3.0f, 0.7f);
        screenEffect(level, 6, 20, 2.5f, 32);
        if (grabbed != null && grabbed.isAlive()) {
            grabbed.hurt(this.damageSources().mobAttack(this), SLASH_DAMAGE);
        }
        releaseGrabbed();
        clearEffects();
    }

    private void releaseGrabbed() {
        if (grabbed != null) {
            grabbed.setNoGravity(false);
            if (grabbed instanceof ServerPlayer sp) {
                ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> sp), new RootPacket(0));
            }
            grabbed = null;
        }
    }

    // ==================== 快速攻击 ====================

    private void tickRapid(ServerLevel level, @Nullable LivingEntity target) {
        switch (state) {
            case RAPID_START -> {
                faceTowards(target, 0.5f);
                hoverInPlace(level);
                if (stateTick >= RAPID_START_LENGTH) {
                    play("rapid_loop");
                    startTrail(RAPID_TICKS + 4);
                    enterState(State.RAPID);
                }
            }
            case RAPID -> {
                startTrail(4);
                if (target != null) {
                    faceTowards(target, 0.6f);
                    // 贴着目标左右游走，拖出残影
                    Vec3 from = horizontal(this.position().subtract(target.position()));
                    from = from.lengthSqr() < 1.0E-4 ? forward().scale(-1) : from.normalize();
                    double sway = Math.sin(stateTick * 0.35) * 40.0;
                    Vec3 spot = target.position().add(rotateY(from, sway).scale(2.6)).add(0, 0.4, 0);
                    Vec3 step = spot.subtract(this.position());
                    moveBy(step.length() > 0.6 ? step.normalize().scale(0.6) : step);
                } else {
                    hoverInPlace(level);
                }
                if (stateTick > BLEND) {
                    int inSecond = (stateTick - BLEND - 1) % 20;
                    for (int hit : RAPID_HITS) {
                        if (inSecond == hit) {
                            rapidHit(level);
                        }
                        if (inSecond == hit - 3) {
                            this.playSound(ModSounds.WEEPING_SWING.get(), 1.4f, 0.9f + this.random.nextFloat() * 0.3f);
                        }
                    }
                }
                if (stateTick >= RAPID_TICKS) {
                    play("rapid_end");
                    enterState(State.RAPID_END);
                }
            }
            case RAPID_END -> {
                hoverInPlace(level);
                if (stateTick >= RAPID_END_LENGTH) {
                    endAttack(10);
                }
            }
            default -> {
            }
        }
    }

    private void rapidHit(ServerLevel level) {
        Vec3 look = forward();
        for (int i = -2; i <= 2; i++) {
            Vec3 p = this.position().add(rotateY(look, i * 35.0).scale(2.5)).add(0, 1.6, 0);
            level.sendParticles(ParticleTypes.SWEEP_ATTACK, p.x, p.y, p.z, 1, 0, 0, 0, 0);
        }
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, this.getBoundingBox().inflate(RAPID_RANGE), this::canHit)) {
            Vec3 to = horizontal(e.position().subtract(this.position()));
            if (to.length() > RAPID_RANGE + e.getBbWidth() / 2 || Math.abs(e.getY() - this.getY()) > 4) {
                continue;
            }
            if (to.lengthSqr() < 0.25 || to.normalize().dot(look) > -0.2) {
                if (e.hurt(this.damageSources().mobAttack(this), RAPID_DAMAGE)) {
                    e.invulnerableTime = 0; // 每秒 3 刀，每刀都要算
                }
            }
        }
    }

    // ==================== 大招：死亡激光 ====================

    private void tickLaser(ServerLevel level, @Nullable LivingEntity target) {
        hoverInPlace(level);
        switch (state) {
            case LASER_START -> {
                if (target != null) {
                    faceTowards(target, 0.3f);
                    laserAim = target.position().add(0, target.getBbHeight() * 0.5, 0);
                }
                float u = Mth.clamp(stateTick / (float) LASER_CIRCLE_FULL, 0.0f, 1.0f);
                this.entityData.set(DATA_CIRCLE, u * u * (3 - 2 * u));
                Vec3 chest = chestPosition();
                if (stateTick % 2 == 0) {
                    // 黑色粒子从四周汇入魔法阵
                    for (int i = 0; i < 4; i++) {
                        Vec3 from = chest.add((this.random.nextDouble() - 0.5) * 6, (this.random.nextDouble() - 0.2) * 5,
                                (this.random.nextDouble() - 0.5) * 6);
                        level.sendParticles(ModParticles.DARK_MOTE.get(), from.x, from.y, from.z, 0,
                                chest.x - from.x, chest.y + 1.5 - from.y, chest.z - from.z, 1.0);
                    }
                }
                if (stateTick >= LASER_START_LENGTH) {
                    play("laser_fire");
                    this.entityData.set(DATA_LASER, true);
                    updateLaserEnd(chest);
                    sound(chest, ModSounds.WEEPING_LASER_BOOM.get(), 4.0f, 0.7f);
                    sound(chest, ModSounds.WEEPING_LASER.get(), 4.0f, 1.0f);
                    screenEffect(level, 5, 20, 2.0f, 48);
                    enterState(State.LASER_FIRE);
                }
            }
            case LASER_FIRE -> {
                if (target != null) {
                    // 追踪：每 tick 只追上一部分，奔跑的玩家会被落下大约一个身位
                    Vec3 wanted = target.position().add(0, target.getBbHeight() * 0.5, 0);
                    laserAim = laserAim.add(wanted.subtract(laserAim).scale(LASER_TRACKING));
                }
                faceDirection(laserAim.subtract(this.position()), 0.5f);
                Vec3 chest = chestPosition();
                Vec3 end = updateLaserEnd(chest);
                if (stateTick % LASER_DAMAGE_INTERVAL == 0) {
                    laserDamage(level, chest, end);
                }
                laserTerrain(level, chest, end);
                if (stateTick % 2 == 0) {
                    level.sendParticles(ParticleTypes.END_ROD, end.x, end.y, end.z, 4, 0.4, 0.4, 0.4, 0.15);
                    level.sendParticles(ParticleTypes.LARGE_SMOKE, end.x, end.y, end.z, 2, 0.5, 0.5, 0.5, 0.02);
                }
                if (stateTick % 20 == 0) {
                    sound(chest, ModSounds.WEEPING_LASER.get(), 3.0f, 0.9f + this.random.nextFloat() * 0.2f);
                    sound(end, SoundEvents.FIRE_EXTINGUISH, 2.0f, 0.5f);
                }
                if (stateTick % 10 == 0) {
                    screenEffect(level, 0, 10, 0.8f, 24);
                }
                if (stateTick >= LASER_TICKS) {
                    this.entityData.set(DATA_LASER, false);
                    sound(chest, SoundEvents.BEACON_DEACTIVATE, 2.5f, 0.5f);
                    play("laser_end");
                    enterState(State.LASER_END);
                }
            }
            case LASER_END -> {
                float u = Mth.clamp(1.0f - stateTick / 16.0f, 0.0f, 1.0f);
                this.entityData.set(DATA_CIRCLE, u);
                if (stateTick >= LASER_END_LENGTH) {
                    endAttack(40);
                }
            }
            default -> {
            }
        }
    }

    /** 胸前的魔法阵（激光发射点），与 wl_laser_fire 里 MagicCircle 骨骼的位置一致 */
    private Vec3 chestPosition() {
        return this.position().add(0, CHEST_HEIGHT, 0).add(forward().scale(CHEST_FORWARD));
    }

    private Vec3 updateLaserEnd(Vec3 chest) {
        Vec3 dir = laserAim.subtract(chest);
        dir = dir.lengthSqr() < 1.0E-4 ? forward() : dir.normalize();
        Vec3 end = chest.add(dir.scale(LASER_LENGTH));
        this.entityData.set(DATA_LASER_END, new Vector3f((float) end.x, (float) end.y, (float) end.z));
        return end;
    }

    /** 以胸口为圆心、半径 1 格、长 30 格的圆柱体内的生物受到伤害（无视护甲） */
    private void laserDamage(ServerLevel level, Vec3 from, Vec3 to) {
        AABB box = new AABB(from, to).inflate(LASER_RADIUS + 1.0);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, this::canHit)) {
            Vec3 c = e.getBoundingBox().getCenter();
            if (distanceToSegment(c, from, to) <= LASER_RADIUS + e.getBbWidth() / 2) {
                if (e.hurt(this.damageSources().indirectMagic(this, this), LASER_DAMAGE)) {
                    e.invulnerableTime = 0;
                    e.setSecondsOnFire(2);
                }
            }
        }
    }

    /** 激光经过的地形被烧穿（遵守 mobGriefing；基岩、黑曜石等凋灵也破坏不了的方块不受影响） */
    private void laserTerrain(ServerLevel level, Vec3 from, Vec3 to) {
        if (!ForgeEventFactory.getMobGriefingEvent(level, this)) {
            return;
        }
        Vec3 dir = to.subtract(from);
        double length = dir.length();
        dir = dir.normalize();
        Set<BlockPos> seen = new HashSet<>();
        int broken = 0;
        for (double s = 1.0; s <= length && broken < LASER_BLOCKS_PER_TICK; s += 0.5) {
            Vec3 p = from.add(dir.scale(s));
            BlockPos center = BlockPos.containing(p);
            for (BlockPos pos : BlockPos.betweenClosed(center.offset(-1, -1, -1), center.offset(1, 1, 1))) {
                if (broken >= LASER_BLOCKS_PER_TICK || !seen.add(pos.immutable())) {
                    continue;
                }
                if (distanceToSegment(Vec3.atCenterOf(pos), from, to) > LASER_RADIUS) {
                    continue;
                }
                BlockState block = level.getBlockState(pos);
                float hardness = block.getDestroySpeed(level, pos);
                if (block.isAir() || block.is(BlockTags.WITHER_IMMUNE) || hardness < 0 || hardness >= 50
                        || !block.canEntityDestroy(level, pos, this) || !ForgeEventFactory.onEntityDestroyBlock(this, pos, block)) {
                    continue;
                }
                if (broken % 4 == 0) {
                    level.destroyBlock(pos, false, this);
                } else {
                    level.removeBlock(pos, false);
                }
                broken++;
            }
        }
    }

    private static double distanceToSegment(Vec3 p, Vec3 a, Vec3 b) {
        Vec3 ab = b.subtract(a);
        double len2 = ab.lengthSqr();
        double t = len2 < 1.0E-8 ? 0 : Mth.clamp(p.subtract(a).dot(ab) / len2, 0.0, 1.0);
        return p.distanceTo(a.add(ab.scale(t)));
    }

    /** 激光有 30 格长：发射时把视锥剔除的范围扩大到激光终点，不然 Boss 在画面外时激光会消失 */
    @Override
    public AABB getBoundingBoxForCulling() {
        AABB box = super.getBoundingBoxForCulling();
        if (laserActive()) {
            Vec3 end = laserEnd();
            box = box.minmax(new AABB(end, end).inflate(2.0));
        }
        return box;
    }

    // ==================== 工具 ====================

    private boolean canHit(LivingEntity e) {
        return e != this && e.isAlive() && !e.isSpectator() && !(e instanceof Player p && p.isCreative())
                && !(e instanceof WeepingDeathLord);
    }

    private Vec3 forward() {
        float yaw = this.getYRot() * Mth.DEG_TO_RAD;
        return new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw));
    }

    private Vec3 rightVec() {
        Vec3 f = forward();
        return new Vec3(-f.z, 0, f.x);
    }

    private void faceTowards(@Nullable Entity target, float speed) {
        if (target != null) {
            faceDirection(target.position().subtract(this.position()), speed);
        }
    }

    private void faceDirection(Vec3 direction, float speed) {
        Vec3 flat = horizontal(direction);
        if (flat.lengthSqr() < 1.0E-4) {
            return;
        }
        float wanted = (float) (Mth.atan2(flat.z, flat.x) * Mth.RAD_TO_DEG) - 90.0f;
        float next = Mth.rotLerp(speed, this.getYRot(), wanted);
        this.setYRot(next);
        this.yBodyRot = next;
        this.yHeadRot = next;
        this.setXRot(0);
    }

    private static double groundY(ServerLevel level, double x, double z, double fallback) {
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(x), Mth.floor(z));
        return y <= level.getMinBuildHeight() ? fallback : y;
    }

    private static Vec3 horizontal(Vec3 v) {
        return new Vec3(v.x, 0, v.z);
    }

    private static Vec3 rotateY(Vec3 v, double angleDeg) {
        double rad = Math.toRadians(angleDeg);
        double cos = Math.cos(rad);
        double sin = Math.sin(rad);
        return new Vec3(v.x * cos + v.z * sin, v.y, -v.x * sin + v.z * cos);
    }

    public float renderScale() {
        return SCALE;
    }

    // ==================== 伤害 ====================

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (!introDone && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return false;
        }
        return super.hurt(source, amount);
    }

    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        if (source.is(DamageTypes.WITHER) || source.is(DamageTypes.WITHER_SKULL) || source.is(DamageTypes.IN_WALL)
                || source.is(DamageTypes.FALL) || source.getEntity() == this) {
            return true;
        }
        return super.isInvulnerableTo(source);
    }

    @Override
    public boolean canBeAffected(MobEffectInstance effect) {
        return effect.getEffect() != MobEffects.WITHER && effect.getEffect() != MobEffects.POISON
                && effect.getEffect() != MobEffects.MOVEMENT_SLOWDOWN && super.canBeAffected(effect);
    }

    @Override
    public void knockback(double strength, double x, double z) {
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
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
        return false;
    }

    @Nullable
    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.WITHER_HURT;
    }

    @Nullable
    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.WITHER_DEATH;
    }

    @Nullable
    @Override
    protected SoundEvent getAmbientSound() {
        return introDone && this.random.nextInt(3) == 0 ? SoundEvents.SOUL_ESCAPE : null;
    }

    @Override
    protected float getSoundVolume() {
        return 1.5f;
    }

    @Override
    public float getVoicePitch() {
        return 0.8f + this.random.nextFloat() * 0.1f;
    }

    // ==================== 掉落 ====================

    /** 3 个下界之星、5~10 个下界合金锭、15~32 个金胡萝卜（遵守 doMobLoot 游戏规则） */
    @Override
    protected void dropCustomDeathLoot(DamageSource source, int looting, boolean recentlyHit) {
        super.dropCustomDeathLoot(source, looting, recentlyHit);
        this.spawnAtLocation(new ItemStack(Items.NETHER_STAR, 3));
        this.spawnAtLocation(new ItemStack(Items.NETHERITE_INGOT, 5 + this.random.nextInt(6)));
        this.spawnAtLocation(new ItemStack(Items.GOLDEN_CARROT, 15 + this.random.nextInt(18)));
    }

    // ==================== 血条 / 生命周期 ====================

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
    public void die(DamageSource source) {
        this.bossEvent.removeAllPlayers();
        cleanup();
        super.die(source);
    }

    @Override
    public void remove(RemovalReason reason) {
        cleanup();
        super.remove(reason);
    }

    private void cleanup() {
        if (this.level() instanceof ServerLevel level) {
            releaseGrabbed();
            removeEyeLight(level);
            clearEffects();
            if (thrownScythe != null) {
                thrownScythe.releasePinned();
                thrownScythe.discard();
                thrownScythe = null;
            }
            if (husk != null) {
                husk.discard();
                husk = null;
            }
        }
    }

    @Override
    public boolean isPersistenceRequired() {
        return true;
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    protected boolean shouldDespawnInPeaceful() {
        return false;
    }

    @Override
    public boolean canChangeDimensions() {
        return false;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 160 * 160;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("IntroDone", introDone);
        Vec3 h = home();
        tag.putDouble("HomeX", h.x);
        tag.putDouble("HomeY", h.y);
        tag.putDouble("HomeZ", h.z);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("HomeX")) {
            home = new Vec3(tag.getDouble("HomeX"), tag.getDouble("HomeY"), tag.getDouble("HomeZ"));
            homeSet = true;
        }
        // 出场演出不存档：读档时直接进入正常状态
        introDone = true;
        this.setHidden(false);
        this.entityData.set(DATA_EYE_GLOW, 1.0f);
        this.entityData.set(DATA_SCYTHE_OUT, false);
        clearEffects();
        play("idle");
    }

    // ==================== GeckoLib ====================

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        // blink：先左眼再右眼（出场动画自己控制眼皮，main 排在后面会盖过它）
        controllers.add(new AnimationController<>(this, BLINK_CONTROLLER, 0, s -> s.setAndContinue(BLINK)));
        // main：待机、出场、出招、后摇全部在这里，换动画时 BLEND tick 平滑过渡
        controllers.add(new AnimationController<>(this, MAIN_CONTROLLER, BLEND, this::mainAnimation));
    }

    private PlayState mainAnimation(AnimationState<WeepingDeathLord> state) {
        int seq = this.entityData.get(DATA_ANIM_SEQ);
        if (seq != seenAnimSeq) {
            seenAnimSeq = seq;
            state.getController().forceAnimationReset();
        }
        int id = this.entityData.get(DATA_ANIM);
        RawAnimation anim;
        if (id <= 0 || id >= ANIM_RAW.length) {
            anim = drifting() ? (combat() ? DRIFT_COMBAT : DRIFT) : (combat() ? HOVER_COMBAT : HOVER);
        } else {
            anim = ANIM_RAW[id];
        }
        return state.setAndContinue(anim);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }
}
