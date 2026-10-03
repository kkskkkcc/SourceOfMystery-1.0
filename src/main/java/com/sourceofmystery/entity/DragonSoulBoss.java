package com.sourceofmystery.entity;

import com.sourceofmystery.item.ModItems;
import com.sourceofmystery.network.BossIntroPacket;
import com.sourceofmystery.network.ModNetwork;
import com.sourceofmystery.sound.ModSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
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
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.DragonFireball;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;
import org.joml.Vector3f;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 龙魂 Boss：末影龙死亡动画完全播完后，在它消失的位置撕开空间裂缝出场，之后是一位敏捷的持枪女将军。
 * <p>
 * 出场：威压音效 → 空间裂缝（VoidRift，虚空模型）裂开、撕开 → 龙魂化作残影高速冲出 → 雷鸣中盘旋两圈
 * → 缓缓降到低空 → 单手持枪俯指玩家、龙吟、喊出台词、定格 2 秒。期间附近玩家的镜头会自动对准她（BossIntroPacket），她本人无敌。
 * <p>
 * 战斗（魂类）：每一招都有前摇和后摇。近战（劈 20 / 刺 15 / 砍 10，均为范围判定）、踢（击飞 10 格）、
 * 抓（带上 20 格高空后抛下）离得远时先瞬移：通常有蓄力前摇，但也会突然无前摇瞬移、直接出快招。
 * 远程有法阵龙息（5 发）和掷枪（命中后飞回手中）。出招后按概率接转枪、挑衅或喘息等后摇，这些时间就是玩家的反击窗口。
 * 玩家吃东西 / 喝药时她一定会大前摇瞬移过去抓人。
 * <p>
 * 大招「龙魂解放」：每次出招有 20% 概率改为大招（冷却 2 分钟）——瞬移贴身，5 秒内每秒 3 击，期间无敌，
 * 被打中的玩家陷入黑暗直到大招结束。
 * <p>
 * 语音：日语女声台词，播放时触发对应表情和口型（face 控制器），并在动作栏显示中文字幕。
 * <p>
 * 和末影龙一样，32 格内有末地水晶时会被水晶治疗（每 0.5 秒 1 点），正在治疗她的水晶被打爆时她受到 10 点伤害。
 */
public class DragonSoulBoss extends Monster implements GeoEntity {

    // ==================== 动画 ====================
    private static final String BASE_CONTROLLER = "base";
    private static final String ACTION_CONTROLLER = "action";
    private static final String BLINK_CONTROLLER = "blink";
    private static final String SPEAR_CONTROLLER = "spear";
    private static final String FACE_CONTROLLER = "face";

    private static final RawAnimation HOVER = RawAnimation.begin().thenLoop("boss_hover");
    private static final RawAnimation STANCE = RawAnimation.begin().thenLoop("boss_stance");
    private static final RawAnimation FLY = RawAnimation.begin().thenLoop("boss_fly");
    private static final RawAnimation CARRY = RawAnimation.begin().thenLoop("boss_carry");
    private static final RawAnimation BLINK = RawAnimation.begin().thenLoop("boss_blink");
    private static final RawAnimation SPEAR_HIDDEN = RawAnimation.begin().thenLoop("boss_spear_hidden");

    // 一次性动作：触发名与动画名一致（动画名去掉 boss_ 前缀）
    private static final String[] ACTIONS = {
            "teleport_charge", "teleport_arrive", "cleave", "thrust", "slash", "kick", "grab", "grab_throw",
            "magic", "spear_throw", "spear_catch", "flourish", "taunt", "pant", "intro_emerge", "intro_point",
            "cleave_fast", "thrust_fast", "slash_fast", "grab_fast"
    };
    private static final String ULTIMATE_ANIM = "ultimate";

    // ==================== 同步数据 ====================
    private static final int MODE_HIDDEN = 0; // 出场：还在裂缝里
    private static final int MODE_FLY = 1;    // 出场：飞行
    private static final int MODE_HOVER = 2;  // 悬停（出场定格 / 没有目标）
    private static final int MODE_FIGHT = 3;  // 战斗姿态
    private static final int MODE_CARRY = 4;  // 抓着玩家飞上高空
    private static final EntityDataAccessor<Integer> DATA_MODE =
            SynchedEntityData.defineId(DragonSoulBoss.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_SPEAR_OUT =
            SynchedEntityData.defineId(DragonSoulBoss.class, EntityDataSerializers.BOOLEAN);
    /** 高速移动中：客户端留下残影，并且不做位置插值的"瞬移判定" */
    private static final EntityDataAccessor<Boolean> DATA_TRAIL =
            SynchedEntityData.defineId(DragonSoulBoss.class, EntityDataSerializers.BOOLEAN);

    // ==================== 出场时间轴（tick） ====================
    private static final int RIFT_START = 30;                                          // 前 1.5 秒只有威压音效
    private static final int RIFT_TORN = RIFT_START + VoidRift.GROW_TICKS + VoidRift.TEAR_TICKS; // 裂缝被撕开
    private static final int DASH_START = RIFT_TORN + 22;                              // 撕开后虚空静止约 1 秒，她才冲出来
    private static final int DASH_TICKS = 5;                                           // 0.25 秒冲出 20 格：只看得见残影
    private static final int DASH_END = DASH_START + DASH_TICKS;
    private static final int RIFT_CLOSE = DASH_END + 20;
    private static final int CIRCLE_TICKS = 200;                                       // 盘旋两圈
    private static final int CIRCLE_END = DASH_END + CIRCLE_TICKS;
    private static final int DESCEND_TICKS = 70;                                       // 缓缓下降到低空
    private static final int DESCEND_END = CIRCLE_END + DESCEND_TICKS;
    private static final int POINT_START = DESCEND_END;
    private static final int ROAR_TICK = POINT_START + 8;
    private static final int LINE_TICK = POINT_START + 22;
    public static final int INTRO_TICKS = POINT_START + 76;                            // 举枪 + 台词 + 定格约 2 秒
    private static final double CIRCLE_RADIUS = 20.0;
    private static final double LANDING_DISTANCE = 9.0;   // 降落点距玩家的水平距离
    private static final double LANDING_HEIGHT = 5.0;     // 降落后离地高度（居高临下）
    private static final double CAMERA_RANGE = 160.0;

    // ==================== 战斗参数 ====================
    private static final double PREFERRED_DISTANCE = 8.5;   // 不出招时和目标保持的距离
    private static final double HOVER_ABOVE_TARGET = 1.2;
    private static final double MAX_MOVE_SPEED = 0.45;
    private static final double TELEPORT_TRIGGER_DISTANCE = 4.5; // 比这远就先瞬移
    private static final double TELEPORT_FACE_DISTANCE = 2.6;    // 瞬移落点离目标的距离
    private static final int TELEPORT_CHARGE_TICKS = 16;    // 普通瞬移蓄力 0.8 秒（期间落点出现预警）
    private static final int TELEPORT_LOCK_TICK = 8;        // 这一刻锁定落点，之后玩家移动就能躲开
    private static final int PUNISH_CHARGE_TICKS = 20;      // 吃东西被抓：1 秒大前摇
    private static final int PUNISH_LOCK_TICK = 17;         // 落点几乎到最后才锁定，吃东西时很难躲
    private static final int ARRIVE_TICKS = 4;              // 落地后稍作停顿，再起手出招
    private static final double QUICK_BLINK_CHANCE = 0.3;   // 无前摇瞬移直接出快招的概率
    private static final double QUICK_BLINK_CHANCE_LOW_HP = 0.45;
    private static final int FAST_HIT_TICK = 5;             // 快招 0.25 秒就命中

    private static final float CLEAVE_DAMAGE = 20.0f;
    private static final float THRUST_DAMAGE = 15.0f;
    private static final float SLASH_DAMAGE = 10.0f;
    private static final float KICK_DAMAGE = 6.0f;
    private static final float GRAB_DAMAGE = 6.0f;
    private static final double CLEAVE_RADIUS = 3.8;         // 劈：枪尖砸地，落点周围一圈
    private static final double SLASH_RANGE = 5.0;           // 砍：身前约 230 度的大横扫
    private static final double THRUST_RANGE = 6.0;          // 刺：一条直线上的都会被贯穿
    private static final double KICK_HORIZONTAL = 1.0;       // 约击飞 10 格
    private static final double KICK_VERTICAL = 0.45;
    private static final double CARRY_HEIGHT = 20.0;
    private static final double CARRY_SPEED = 0.5;
    private static final int CARRY_MAX_TICKS = 60;
    private static final int MAGIC_SHOTS = 5;
    private static final int SPEAR_CATCH_TIMEOUT = 140;

    private static final int BASE_COOLDOWN = 30;
    private static final int BASE_COOLDOWN_LOW_HP = 18;
    private static final int GRAB_COOLDOWN = 400;
    private static final int PUNISH_COOLDOWN = 200;
    private static final int MAGIC_COOLDOWN = 160;
    private static final int SPEAR_COOLDOWN = 200;
    private static final double COMBO_CHANCE = 0.45;       // 半血以下近战后接连招的概率
    private static final int MAX_COMBO = 2;

    // 大招「龙魂解放」
    private static final double ULTIMATE_CHANCE = 0.2;
    private static final int ULTIMATE_COOLDOWN = 2400;     // 2 分钟
    private static final int ULT_START_TICKS = 10;         // 起手（boss_ult_start 0.5 秒）
    private static final int ULT_FLURRY_TICKS = 100;       // 5 秒乱舞（boss_ult_flurry 1 秒 × 5）
    private static final int ULT_FINISH_TICKS = 16;        // 收招（boss_ult_finish 0.8 秒）
    private static final int ULT_TOTAL = ULT_START_TICKS + ULT_FLURRY_TICKS + ULT_FINISH_TICKS;
    private static final int[] ULT_HIT_TICKS = {3, 10, 16}; // 乱舞每秒的三次命中（对应动画 0.15 / 0.48 / 0.81 秒）
    private static final float ULT_HIT_DAMAGE = 3.0f;
    private static final float ULT_FINISH_DAMAGE = 8.0f;
    private static final double ULT_RANGE = 4.0;

    private static final float DRAGON_BREATH_HEAL = 10.0f; // 站在龙息里每秒回 10 点
    private static final double CRYSTAL_RANGE = 32.0;      // 同末影龙
    private static final float CRYSTAL_DESTROYED_DAMAGE = 10.0f;

    // 语音：台词时长（tick），说话期间不会被别的台词打断（强制台词除外）
    private static final Map<String, Integer> VOICE_TICKS = Map.ofEntries(
            Map.entry("intro", 52), Map.entry("engage", 42), Map.entry("quick", 14), Map.entry("grab", 19),
            Map.entry("punish", 17), Map.entry("throw", 15), Map.entry("spear", 15), Map.entry("magic", 50),
            Map.entry("taunt", 26), Map.entry("pant", 36), Map.entry("half", 50), Map.entry("ultimate", 43),
            Map.entry("kill", 44), Map.entry("death", 36), Map.entry("kiai1", 12), Map.entry("kiai2", 14));
    private static final double VOICE_SUBTITLE_RANGE = 48.0;

    private static final DustParticleOptions MAGIC_CIRCLE = new DustParticleOptions(new Vector3f(0.85f, 0.4f, 1.0f), 1.5f);

    /**
     * 出招种类，带各自动画里命中帧和总时长（tick）；快招版本见 {@link #hitTick()} / {@link #length()}
     */
    private enum Attack {
        CLEAVE("cleave", 17, 34, true),
        THRUST("thrust", 14, 28, true),
        SLASH("slash", 11, 24, true),
        KICK("kick", 12, 24, true),
        GRAB("grab", 12, 20, true),
        MAGIC("magic", 16, 48, false),
        SPEAR("spear_throw", 15, 26, false);

        final String anim;
        final int hitTick;
        final int length;
        final boolean melee;

        Attack(String anim, int hitTick, int length, boolean melee) {
            this.anim = anim;
            this.hitTick = hitTick;
            this.length = length;
            this.melee = melee;
        }

        /** 劈 / 刺 / 砍 / 抓有压缩了前摇的快招动画 */
        boolean hasFast() {
            return this == CLEAVE || this == THRUST || this == SLASH || this == GRAB;
        }
    }

    private enum State { IDLE, TELEPORT_CHARGE, TELEPORT_ARRIVE, ATTACK, CARRY_UP, THROW, WAIT_SPEAR, CATCH, RECOVERY, ULTIMATE }

    // ==================== 状态 ====================
    private boolean introDone = true; // 用 /summon 直接召唤时没有出场动画
    private int introTick;
    private Vec3 riftPos = Vec3.ZERO;
    private Vec3 emergeDir = new Vec3(1, 0, 0);
    private Vec3 descendFrom = Vec3.ZERO;
    private Vec3 descendTo = Vec3.ZERO;
    @Nullable
    private VoidRift rift;

    private State state = State.IDLE;
    private int stateTick;
    private Attack attack = Attack.SLASH;
    private boolean fastAttack;
    private boolean punishing;
    private int cooldown = BASE_COOLDOWN;
    private int comboCount;
    private int grabCooldown = GRAB_COOLDOWN / 2;
    private int punishCooldown;
    private int magicCooldown;
    private int spearCooldown;
    private int ultimateCooldown = 600;   // 开战 30 秒内不放大招
    private int strafeSign = 1;
    private int strafeTimer;
    @Nullable
    private Vec3 teleportDest;
    private int recoveryTicks;
    private boolean releasing;
    private double carryStartY;
    private boolean hitLanded;
    private int trailTicks;
    private final Set<UUID> darkened = new HashSet<>();

    private int voiceBusy;
    private boolean engageSpoken;
    private boolean halfSpoken;

    @Nullable
    private EndCrystal healingCrystal;
    private int tickCounter;

    /** 客户端：残影（位置、朝向、产生时刻） */
    public record Afterimage(double x, double y, double z, float yaw, float born) {
    }

    public static final int AFTERIMAGE_LIFE = 8;
    public final List<Afterimage> afterimages = new ArrayList<>();

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final ServerBossEvent bossEvent;

    public DragonSoulBoss(EntityType<? extends DragonSoulBoss> type, Level level) {
        super(type, level);
        this.xpReward = 500;
        Component name = Component.translatable("entity.sourceofmystery.dragon_soul")
                .withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD);
        this.setCustomName(name);
        this.setNoGravity(true);
        this.bossEvent = new ServerBossEvent(name, BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.PROGRESS);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.ATTACK_DAMAGE, CLEAVE_DAMAGE)
                .add(Attributes.ARMOR, 30.0)
                .add(Attributes.MAX_HEALTH, 500.0)
                .add(Attributes.MOVEMENT_SPEED, 0.28)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.FOLLOW_RANGE, 100.0);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_MODE, MODE_FIGHT);
        this.entityData.define(DATA_SPEAR_OUT, false);
        this.entityData.define(DATA_TRAIL, false);
    }

    @Override
    protected void registerGoals() {
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, false));
    }

    private int mode() {
        return this.entityData.get(DATA_MODE);
    }

    private void setMode(int mode) {
        this.entityData.set(DATA_MODE, mode);
    }

    private boolean spearOut() {
        return this.entityData.get(DATA_SPEAR_OUT);
    }

    public boolean trail() {
        return this.entityData.get(DATA_TRAIL);
    }

    /** 开启残影 ticks 个 tick */
    private void startTrail(int ticks) {
        trailTicks = Math.max(trailTicks, ticks);
        this.entityData.set(DATA_TRAIL, true);
    }

    // ==================== 出场 ====================

    /**
     * 由 DragonSoulSpawnHandler 在生成后立即调用：从 riftPos 撕开裂缝出场。
     * （生成前就已经设成隐身，避免客户端在第一帧看到她）
     */
    public void beginIntro(ServerLevel level, Vec3 riftPos) {
        this.riftPos = riftPos;
        this.introDone = false;
        this.introTick = 0;
        this.setPos(riftPos);
        this.setInvisible(true);
        this.setMode(MODE_HIDDEN);
        this.bossEvent.setVisible(false);

        Player nearest = level.getNearestPlayer(riftPos.x, riftPos.y, riftPos.z, CAMERA_RANGE, false);
        if (nearest != null) {
            Vec3 toPlayer = horizontal(nearest.position().subtract(riftPos));
            if (toPlayer.lengthSqr() > 1.0E-4) {
                emergeDir = toPlayer.normalize();
            }
        }
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(riftPos) < CAMERA_RANGE * CAMERA_RANGE) {
                ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new BossIntroPacket(this.getId(), INTRO_TICKS));
            }
        }
    }

    private void tickIntro(ServerLevel level) {
        introTick++;
        if (introTick == 1) {
            notifyNearby(level, ModSounds.DRAGON_SOUL_OMEN.get(), 1.0f);
        }
        if (introTick == RIFT_START) {
            // 空间裂缝：虚空模型，平面朝向玩家
            rift = ModEntities.VOID_RIFT.get().create(level);
            if (rift != null) {
                rift.moveTo(riftPos.x, riftPos.y + 2.0, riftPos.z, yawOf(emergeDir), 0);
                level.addFreshEntity(rift);
            }
            notifyNearby(level, ModSounds.DRAGON_SOUL_RIFT.get(), 0.7f);
        }
        if (introTick == RIFT_TORN) {
            notifyNearby(level, ModSounds.DRAGON_SOUL_RIFT.get(), 1.0f);
            notifyNearby(level, SoundEvents.GLASS_BREAK, 0.5f);
        }
        if (introTick == RIFT_CLOSE && rift != null) {
            rift.close();
        }
        if (introTick == DASH_START - 1) {
            startTrail(DASH_TICKS + 6); // 先通知客户端，冲刺的每一步都按高速移动处理
        }

        Vec3 dashEnd = riftPos.add(emergeDir.scale(CIRCLE_RADIUS));
        if (introTick < DASH_START) {
            this.setPos(riftPos);
            faceDirection(emergeDir, 1.0f);
        } else if (introTick < DASH_END) {
            if (introTick == DASH_START) {
                this.setInvisible(false);
                this.setMode(MODE_FLY);
                this.playSound(ModSounds.DRAGON_SOUL_TELEPORT.get(), 3.0f, 0.8f);
                this.playSound(ModSounds.DRAGON_SOUL_WINGS.get(), 3.0f, 0.7f);
            }
            double u = easeOut((introTick - DASH_START + 1) / (double) DASH_TICKS);
            moveSmoothlyTo(riftPos.lerp(dashEnd, u));
            faceDirection(emergeDir, 1.0f);
        } else if (introTick < CIRCLE_END) {
            if (introTick == DASH_END) {
                this.triggerAnim(ACTION_CONTROLLER, "intro_emerge"); // 急停、展翼
                this.playSound(ModSounds.DRAGON_SOUL_WINGS.get(), 3.0f, 0.9f);
            }
            tickCircle(level);
        } else if (introTick < DESCEND_END) {
            if (introTick == CIRCLE_END) {
                descendFrom = this.position();
                descendTo = landingPoint(level);
            }
            double u = smoothstep((introTick - CIRCLE_END) / (double) DESCEND_TICKS);
            Vec3 pos = descendFrom.lerp(descendTo, u);
            // 轻微的弧线，看起来像滑翔而不是直线平移
            Vec3 side = horizontal(descendTo.subtract(descendFrom)).cross(new Vec3(0, 1, 0));
            if (side.lengthSqr() > 1.0E-4) {
                pos = pos.add(side.normalize().scale(Math.sin(u * Math.PI) * 4.0));
            }
            Vec3 previous = this.position();
            moveSmoothlyTo(pos);
            Player nearest = nearestPlayer(level);
            if (u > 0.7 && nearest != null) {
                faceDirection(nearest.position().subtract(this.position()), 0.2f);
            } else {
                faceDirection(pos.subtract(previous), 0.3f);
            }
            if (introTick % 16 == 0) {
                this.playSound(ModSounds.DRAGON_SOUL_WINGS.get(), 2.5f, 0.9f);
            }
        } else {
            if (introTick == POINT_START) {
                this.setMode(MODE_HOVER);
                this.triggerAnim(ACTION_CONTROLLER, "intro_point");
            }
            this.setDeltaMovement(Vec3.ZERO);
            Player nearest = nearestPlayer(level);
            if (nearest != null) {
                faceDirection(nearest.position().subtract(this.position()), 0.25f);
            }
            if (introTick == ROAR_TICK) {
                notifyNearby(level, ModSounds.DRAGON_SOUL_ROAR.get(), 1.0f);
                level.sendParticles(ParticleTypes.SONIC_BOOM, this.getX(), this.getY() + 2, this.getZ(), 1, 0, 0, 0, 0);
            }
            if (introTick == LINE_TICK) {
                speak("intro", true);
            }
        }

        if (introTick >= INTRO_TICKS) {
            finishIntro();
        }
    }

    private void tickCircle(ServerLevel level) {
        double u = (introTick - DASH_END) / (double) CIRCLE_TICKS;
        double startAngle = Math.atan2(emergeDir.z, emergeDir.x);
        double angle = startAngle + u * Math.PI * 4; // 两圈
        double y = riftPos.y + 3.0 * Math.sin(u * Math.PI * 4);
        Vec3 pos = new Vec3(riftPos.x + Math.cos(angle) * CIRCLE_RADIUS, y, riftPos.z + Math.sin(angle) * CIRCLE_RADIUS);
        moveSmoothlyTo(pos);
        faceDirection(new Vec3(-Math.sin(angle), 0, Math.cos(angle)), 0.5f);

        // 盘旋期间天空中雷声轰鸣
        if (introTick % 8 == 0) {
            double a = this.random.nextDouble() * Math.PI * 2;
            double r = 8 + this.random.nextDouble() * 30;
            double x = riftPos.x + Math.cos(a) * r;
            double z = riftPos.z + Math.sin(a) * r;
            LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
            if (bolt != null) {
                bolt.moveTo(x, groundY(level, x, z, riftPos.y - 25), z);
                bolt.setVisualOnly(true);
                level.addFreshEntity(bolt);
            }
        }
        if (introTick % 16 == 0) {
            this.playSound(ModSounds.DRAGON_SOUL_WINGS.get(), 3.0f, 0.9f);
        }
    }

    /**
     * 降落点：最近玩家与当前位置连线上、距玩家 9 格、离地 5 格的位置，这样定格时正好居高临下地俯视玩家
     */
    private Vec3 landingPoint(ServerLevel level) {
        Player player = nearestPlayer(level);
        if (player == null) {
            return new Vec3(riftPos.x, groundY(level, riftPos.x, riftPos.z, riftPos.y - 10) + LANDING_HEIGHT, riftPos.z);
        }
        Vec3 dir = horizontal(this.position().subtract(player.position()));
        dir = dir.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : dir.normalize();
        Vec3 spot = player.position().add(dir.scale(LANDING_DISTANCE));
        double ground = groundY(level, spot.x, spot.z, player.getY());
        return new Vec3(spot.x, Math.max(ground, player.getY()) + LANDING_HEIGHT, spot.z);
    }

    private void finishIntro() {
        introDone = true;
        engageSpoken = true; // 出场已经喊过台词
        this.setInvisible(false);
        this.setMode(MODE_FIGHT);
        this.bossEvent.setVisible(true);
        this.cooldown = 20;
        if (rift != null && rift.isAlive()) {
            rift.close();
        }
        if (this.level() instanceof ServerLevel level) {
            DragonSoulSpawnHandler.announceArrival(level);
        }
    }

    private void notifyNearby(ServerLevel level, SoundEvent sound, float pitch) {
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(this) < CAMERA_RANGE * CAMERA_RANGE) {
                player.playNotifySound(sound, SoundSource.HOSTILE, 1.0f, pitch);
            }
        }
    }

    // ==================== 语音 ====================

    /**
     * 说一句台词：播放语音、触发同名表情 / 口型动画，并在附近玩家的动作栏显示字幕。
     * 说话期间（voiceBusy）普通台词会被跳过，force 的台词总会说出来。
     */
    private void speak(String id, boolean force) {
        if (!force && voiceBusy > 0) {
            return;
        }
        var sound = ModSounds.DRAGON_SOUL_VOICES.get(id);
        if (sound == null || !(this.level() instanceof ServerLevel level)) {
            return;
        }
        level.playSound(null, this.getX(), this.getEyeY(), this.getZ(), sound.get(), SoundSource.HOSTILE, 3.0f, 1.0f);
        this.triggerAnim(FACE_CONTROLLER, "voice_" + id);
        voiceBusy = VOICE_TICKS.getOrDefault(id, 30) + 10;
        Component line = Component.translatable("voice.sourceofmystery.dragon_soul.format",
                Component.translatable("entity.sourceofmystery.dragon_soul"),
                Component.translatable("voice.sourceofmystery.dragon_soul." + id))
                .withStyle(ChatFormatting.LIGHT_PURPLE);
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(this) < VOICE_SUBTITLE_RANGE * VOICE_SUBTITLE_RANGE) {
                player.displayClientMessage(line, true);
            }
        }
    }

    private void maybeSpeak(String id, double chance) {
        if (this.random.nextDouble() < chance) {
            speak(id, false);
        }
    }

    /** 由 DragonSoulSpawnHandler 在她击杀玩家时调用 */
    public void onKilledPlayer() {
        speak("kill", true);
    }

    // ==================== 主循环 ====================

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide) {
            tickAfterimages();
            return;
        }
        ServerLevel level = (ServerLevel) this.level();
        tickCounter++;
        this.bossEvent.setProgress(this.getHealth() / this.getMaxHealth());
        if (voiceBusy > 0) voiceBusy--;
        if (trailTicks > 0 && --trailTicks == 0) {
            this.entityData.set(DATA_TRAIL, false);
        }

        if (!introDone) {
            tickIntro(level);
            return;
        }

        if (tickCounter % 20 == 0) {
            healFromDragonBreath(level);
        }
        tickCrystalHealing(level);

        if (grabCooldown > 0) grabCooldown--;
        if (punishCooldown > 0) punishCooldown--;
        if (magicCooldown > 0) magicCooldown--;
        if (spearCooldown > 0) spearCooldown--;
        if (ultimateCooldown > 0) ultimateCooldown--;

        if (!halfSpoken && this.getHealth() < this.getMaxHealth() * 0.5f) {
            halfSpoken = true;
            speak("half", true);
        }

        tickFight(level);
    }

    /**
     * 客户端：高速移动时每 tick 在上一位置和当前位置之间补三个残影，残影 8 tick 内淡出
     */
    private void tickAfterimages() {
        float now = this.tickCount;
        afterimages.removeIf(a -> now - a.born() > AFTERIMAGE_LIFE);
        if (trail() && !this.isInvisible()) {
            for (int i = 0; i < 3; i++) {
                double u = i / 3.0;
                afterimages.add(new Afterimage(Mth.lerp(u, this.xo, this.getX()), Mth.lerp(u, this.yo, this.getY()),
                        Mth.lerp(u, this.zo, this.getZ()), this.yBodyRot, now - 1 + (float) u));
            }
        }
    }

    /**
     * 不使用原版移动（会被 FlyingMoveControl 之类拽走），所有位移都在 tickFight / tickIntro 里显式计算
     */
    @Override
    public void travel(Vec3 travelVector) {
    }

    private void tickFight(ServerLevel level) {
        LivingEntity target = this.getTarget();
        boolean hasTarget = target != null && target.isAlive();
        if (state == State.IDLE) {
            this.setMode(hasTarget ? MODE_FIGHT : MODE_HOVER);
        }
        if (!hasTarget && state != State.CARRY_UP && state != State.THROW && state != State.WAIT_SPEAR
                && state != State.CATCH && state != State.ULTIMATE) {
            if (state != State.IDLE && state != State.RECOVERY) {
                enterState(State.IDLE);
            }
            if (state == State.IDLE) {
                hoverInPlace();
                return;
            }
        }
        if (hasTarget && !engageSpoken) {
            engageSpoken = true;
            speak("engage", false);
        }
        // 玩家吃东西 / 喝药：立刻大前摇瞬移过去抓人
        if (hasTarget && (state == State.IDLE || state == State.RECOVERY) && isEatingPlayer(target)) {
            startPunish(target);
        }
        stateTick++;
        switch (state) {
            case IDLE -> tickIdle(level, target);
            case TELEPORT_CHARGE -> tickTeleportCharge(level, target);
            case TELEPORT_ARRIVE -> {
                faceTowards(target, 1.0f);
                if (stateTick >= ARRIVE_TICKS) {
                    startAttack(attack, fastAttack);
                }
            }
            case ATTACK -> tickAttack(level, target);
            case CARRY_UP -> tickCarry(level);
            case THROW -> tickThrow();
            case WAIT_SPEAR -> {
                faceTowards(target, 0.3f);
                if (stateTick > SPEAR_CATCH_TIMEOUT) {
                    onSpearReturned();
                }
            }
            case CATCH -> {
                if (stateTick >= 12) {
                    beginRecovery(0.35, 0.0, 0.0);
                }
            }
            case RECOVERY -> {
                if (stateTick >= recoveryTicks) {
                    enterState(State.IDLE);
                    cooldown = (this.getHealth() < this.getMaxHealth() * 0.5 ? BASE_COOLDOWN_LOW_HP : BASE_COOLDOWN)
                            + this.random.nextInt(20);
                }
            }
            case ULTIMATE -> tickUltimate(level, target);
        }
    }

    private void enterState(State next) {
        state = next;
        stateTick = 0;
    }

    private void hoverInPlace() {
        this.setDeltaMovement(this.getDeltaMovement().scale(0.8));
        double bob = Math.sin(tickCounter * 0.08) * 0.02;
        this.move(MoverType.SELF, new Vec3(0, bob, 0));
    }

    /**
     * 不出招时：与目标保持约 8.5 格距离，绕着目标缓慢横移，时不时换方向
     */
    private void tickIdle(ServerLevel level, LivingEntity target) {
        faceTowards(target, 0.4f);
        if (--strafeTimer <= 0) {
            strafeSign = this.random.nextBoolean() ? 1 : -1;
            strafeTimer = 60 + this.random.nextInt(60);
        }
        Vec3 fromTarget = horizontal(this.position().subtract(target.position()));
        if (fromTarget.lengthSqr() < 1.0E-4) {
            fromTarget = new Vec3(1, 0, 0);
        }
        fromTarget = fromTarget.normalize();
        Vec3 tangent = new Vec3(-fromTarget.z, 0, fromTarget.x).scale(strafeSign * 0.35);
        Vec3 desired = target.position().add(fromTarget.add(tangent).normalize().scale(PREFERRED_DISTANCE))
                .add(0, HOVER_ABOVE_TARGET + Math.sin(tickCounter * 0.08) * 0.4, 0);
        Vec3 velocity = desired.subtract(this.position()).scale(0.08);
        if (velocity.length() > MAX_MOVE_SPEED) {
            velocity = velocity.normalize().scale(MAX_MOVE_SPEED);
        }
        this.setDeltaMovement(velocity);
        this.move(MoverType.SELF, velocity);

        if (--cooldown <= 0) {
            chooseAttack(level, target);
        }
    }

    private void chooseAttack(ServerLevel level, LivingEntity target) {
        comboCount = 0;
        // 大招：每次出招都有 20% 概率改为龙魂解放（冷却 2 分钟）
        if (ultimateCooldown <= 0 && this.random.nextDouble() < ULTIMATE_CHANCE && canBeUltimateTarget(target)) {
            startUltimate(level, target);
            return;
        }
        double distance = this.distanceTo(target);
        boolean far = distance > 16.0;
        boolean canGrab = grabCooldown <= 0 && target instanceof Player player && !player.isCreative()
                && !player.isSpectator() && !target.isPassenger();
        double cleave = far ? 10 : 18;
        double thrust = far ? 10 : 18;
        double slash = far ? 10 : 18;
        double kick = far ? 4 : 10;
        double grab = canGrab ? (far ? 4 : 8) : 0;
        double magic = magicCooldown <= 0 ? (far ? 22 : 12) : 0;
        double spear = spearCooldown <= 0 ? (far ? 22 : 12) : 0;
        double roll = this.random.nextDouble() * (cleave + thrust + slash + kick + grab + magic + spear);
        Attack next;
        if ((roll -= cleave) < 0) next = Attack.CLEAVE;
        else if ((roll -= thrust) < 0) next = Attack.THRUST;
        else if ((roll -= slash) < 0) next = Attack.SLASH;
        else if ((roll -= kick) < 0) next = Attack.KICK;
        else if ((roll -= grab) < 0) next = Attack.GRAB;
        else if ((roll -= magic) < 0) next = Attack.MAGIC;
        else next = Attack.SPEAR;
        engage(level, next, target);
    }

    /**
     * 近身招式离得远就先瞬移：大多数时候有蓄力前摇，但有一定概率无前摇瞬移、落地直接出快招
     */
    private void engage(ServerLevel level, Attack next, LivingEntity target) {
        attack = next;
        punishing = false;
        if (next.melee && this.distanceTo(target) > TELEPORT_TRIGGER_DISTANCE) {
            boolean lowHp = this.getHealth() < this.getMaxHealth() * 0.5;
            double quick = lowHp ? QUICK_BLINK_CHANCE_LOW_HP : QUICK_BLINK_CHANCE;
            if (next.hasFast() && this.random.nextDouble() < quick) {
                Vec3 spot = findTeleportSpot(level, target);
                if (spot != null) {
                    blinkTo(level, spot, target);
                    maybeSpeak("quick", 0.5);
                    startAttack(next, true);
                    return;
                }
            }
            enterState(State.TELEPORT_CHARGE);
            teleportDest = null;
            this.triggerAnim(ACTION_CONTROLLER, "teleport_charge");
            this.playSound(ModSounds.DRAGON_SOUL_TELEPORT_CHARGE.get(), 2.0f, 1.0f);
        } else {
            startAttack(next, false);
        }
    }

    // ---------- 吃东西时的惩罚抓取 ----------

    private boolean isEatingPlayer(LivingEntity target) {
        if (punishCooldown > 0 || !(target instanceof Player player) || player.isCreative() || player.isSpectator()
                || player.isPassenger() || !player.isUsingItem()) {
            return false;
        }
        UseAnim anim = player.getUseItem().getUseAnimation();
        return anim == UseAnim.EAT || anim == UseAnim.DRINK;
    }

    private void startPunish(LivingEntity target) {
        punishCooldown = PUNISH_COOLDOWN;
        punishing = true;
        attack = Attack.GRAB;
        speak("punish", true);
        enterState(State.TELEPORT_CHARGE);
        teleportDest = null;
        this.triggerAnim(ACTION_CONTROLLER, "teleport_charge");
        this.playSound(ModSounds.DRAGON_SOUL_TELEPORT_CHARGE.get(), 2.5f, 0.8f);
    }

    private void tickTeleportCharge(ServerLevel level, LivingEntity target) {
        faceTowards(target, 0.5f);
        this.setDeltaMovement(Vec3.ZERO);
        int charge = punishing ? PUNISH_CHARGE_TICKS : TELEPORT_CHARGE_TICKS;
        int lock = punishing ? PUNISH_LOCK_TICK : TELEPORT_LOCK_TICK;
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, this.getX(), this.getY() + 1.5, this.getZ(),
                punishing ? 14 : 6, 0.4, 0.9, 0.4, 0.05);
        if (stateTick == lock) {
            teleportDest = findTeleportSpot(level, target);
            if (teleportDest == null) {
                // 玩家周围没有落脚点：改用远程
                punishing = false;
                attack = magicCooldown <= 0 ? Attack.MAGIC : Attack.SPEAR;
                startAttack(attack, false);
                return;
            }
        }
        if (teleportDest != null) {
            // 落点预警：在锁定的位置出现紫色漩涡，看到后横向移动就能躲开这次贴脸
            level.sendParticles(ParticleTypes.PORTAL, teleportDest.x, teleportDest.y + 1.0, teleportDest.z,
                    10, 0.3, 0.8, 0.3, 0.6);
        }
        if (stateTick >= charge && teleportDest != null) {
            blinkTo(level, teleportDest, target);
            if (punishing) {
                startAttack(Attack.GRAB, true); // 吃东西被抓：落地立刻伸手
            } else {
                this.triggerAnim(ACTION_CONTROLLER, "teleport_arrive");
                fastAttack = false;
                enterState(State.TELEPORT_ARRIVE);
            }
        }
    }

    /** 瞬移到 spot 并面向目标，原地留下残影和粒子 */
    private void blinkTo(ServerLevel level, Vec3 spot, @Nullable LivingEntity target) {
        spawnTeleportBurst(level);
        this.teleportTo(spot.x, spot.y, spot.z);
        this.setDeltaMovement(Vec3.ZERO);
        faceTowards(target, 1.0f);
        spawnTeleportBurst(level);
        this.playSound(ModSounds.DRAGON_SOUL_TELEPORT.get(), 2.0f, 1.0f);
    }

    @Nullable
    private Vec3 findTeleportSpot(ServerLevel level, LivingEntity target) {
        Vec3 fromTarget = horizontal(this.position().subtract(target.position()));
        double baseAngle = fromTarget.lengthSqr() < 1.0E-4 ? 0 : Math.atan2(fromTarget.z, fromTarget.x);
        for (int i = 0; i < 8; i++) {
            double angle = baseAngle + (i % 2 == 0 ? 1 : -1) * ((i + 1) / 2) * Math.PI / 4;
            Vec3 spot = target.position().add(Math.cos(angle) * TELEPORT_FACE_DISTANCE, 0, Math.sin(angle) * TELEPORT_FACE_DISTANCE);
            AABB box = this.getBoundingBox().move(spot.subtract(this.position()));
            if (level.noCollision(this, box)) {
                return spot;
            }
        }
        return null;
    }

    private void spawnTeleportBurst(ServerLevel level) {
        level.sendParticles(ParticleTypes.PORTAL, this.getX(), this.getY() + 1.5, this.getZ(), 50, 0.5, 1.2, 0.5, 0.4);
        level.sendParticles(ParticleTypes.DRAGON_BREATH, this.getX(), this.getY() + 1.0, this.getZ(), 15, 0.4, 0.8, 0.4, 0.02);
    }

    // ==================== 出招 ====================

    private int hitTick() {
        return fastAttack ? FAST_HIT_TICK : attack.hitTick;
    }

    private int attackLength() {
        return fastAttack ? attack.length - attack.hitTick + FAST_HIT_TICK : attack.length;
    }

    private void startAttack(Attack next, boolean fast) {
        attack = next;
        fastAttack = fast && next.hasFast();
        hitLanded = false;
        enterState(State.ATTACK);
        this.setDeltaMovement(Vec3.ZERO);
        this.triggerAnim(ACTION_CONTROLLER, next.anim + (fastAttack ? "_fast" : ""));
        switch (next) {
            case MAGIC -> {
                magicCooldown = MAGIC_COOLDOWN;
                this.playSound(ModSounds.DRAGON_SOUL_MAGIC_CHARGE.get(), 2.0f, 1.0f);
                maybeSpeak("magic", 0.6);
            }
            case SPEAR -> {
                spearCooldown = SPEAR_COOLDOWN;
                maybeSpeak("spear", 0.6);
            }
            case GRAB -> grabCooldown = GRAB_COOLDOWN;
            default -> {
                if (next.melee) {
                    maybeSpeak(this.random.nextBoolean() ? "kiai1" : "kiai2", 0.3);
                }
            }
        }
    }

    private void tickAttack(ServerLevel level, LivingEntity target) {
        int hit = hitTick();
        // 前摇前 70% 会跟着目标转身，之后锁定方向：看准时机侧移就能躲开
        if (stateTick < hit * 0.7) {
            faceTowards(target, fastAttack ? 0.8f : 0.35f);
        }
        this.setDeltaMovement(Vec3.ZERO);

        switch (attack) {
            case CLEAVE -> {
                if (stateTick == hit - 3) this.playSound(ModSounds.DRAGON_SOUL_CLEAVE.get(), 1.5f, 1.0f);
                if (stateTick == hit) cleaveImpact(level);
            }
            case THRUST -> {
                if (stateTick == hit - 2) this.playSound(ModSounds.DRAGON_SOUL_THRUST.get(), 1.5f, 1.0f);
                if (stateTick >= hit - 2 && stateTick <= hit + 1) {
                    // 突刺时向前冲一小段
                    this.move(MoverType.SELF, forward().scale(0.55));
                }
                if (stateTick == hit) {
                    Vec3 tip = this.position().add(forward().scale(THRUST_RANGE * 0.6)).add(0, 1.2, 0);
                    level.sendParticles(ParticleTypes.CRIT, tip.x, tip.y, tip.z, 14, 1.6, 0.3, 1.6, 0.1);
                    hitAll(victimsInFront(level, THRUST_RANGE, 0.75), THRUST_DAMAGE);
                }
            }
            case SLASH -> {
                if (stateTick == hit - 2) this.playSound(ModSounds.DRAGON_SOUL_SLASH.get(), 1.5f, 1.0f);
                if (stateTick == hit) {
                    sweepParticles(level, SLASH_RANGE * 0.7);
                    hitAll(victimsInFront(level, SLASH_RANGE, -0.4), SLASH_DAMAGE);
                }
            }
            case KICK -> {
                if (stateTick == hit) {
                    this.playSound(ModSounds.DRAGON_SOUL_KICK.get(), 1.5f, 1.0f);
                    for (LivingEntity victim : victimsInFront(level, 3.8, 0.4)) {
                        if (victim.hurt(this.damageSources().mobAttack(this), KICK_DAMAGE)) {
                            Vec3 push = horizontal(victim.position().subtract(this.position()));
                            push = push.lengthSqr() < 1.0E-4 ? forward() : push.normalize();
                            victim.setDeltaMovement(push.x * KICK_HORIZONTAL, KICK_VERTICAL, push.z * KICK_HORIZONTAL);
                            victim.hurtMarked = true;
                            hitLanded = true;
                        }
                    }
                }
            }
            case GRAB -> {
                if (stateTick == hit - 4) this.playSound(ModSounds.DRAGON_SOUL_GRAB.get(), 1.5f, 1.0f);
                if (stateTick == hit && tryGrab(level, target)) {
                    return;
                }
            }
            case MAGIC -> tickMagic(level, target);
            case SPEAR -> {
                if (stateTick == hit) {
                    throwSpear(level, target);
                    return;
                }
            }
        }

        if (stateTick >= attackLength()) {
            finishAttack(level, target);
        }
    }

    private void finishAttack(ServerLevel level, LivingEntity target) {
        // 半血以下：近战有概率直接接下一招，最多连两段
        boolean lowHp = this.getHealth() < this.getMaxHealth() * 0.5;
        if (attack.melee && attack != Attack.GRAB && lowHp && comboCount < MAX_COMBO
                && target != null && target.isAlive() && this.random.nextDouble() < COMBO_CHANCE) {
            comboCount++;
            Attack[] chain = {Attack.CLEAVE, Attack.THRUST, Attack.SLASH, Attack.KICK};
            Attack next = chain[this.random.nextInt(chain.length)];
            if (next == attack) {
                next = chain[(next.ordinal() + 1) % chain.length];
            }
            engage(level, next, target);
            return;
        }
        switch (attack) {
            case KICK -> beginRecovery(0.0, 0.35, 0.0);
            case GRAB -> beginRecovery(0.0, 0.0, 0.5);     // 抓空：喘口气，好反击
            case MAGIC -> beginRecovery(0.0, 0.0, 0.3);
            default -> beginRecovery(0.3, 0.1, 0.0);
        }
    }

    /**
     * 后摇：按概率播放转枪 / 挑衅 / 喘息，期间原地不动，是玩家的反击窗口；残血时更容易喘息
     */
    private void beginRecovery(double flourishChance, double tauntChance, double pantChance) {
        enterState(State.RECOVERY);
        if (this.getHealth() < this.getMaxHealth() * 0.3) {
            pantChance += 0.15;
        }
        double roll = this.random.nextDouble();
        if ((roll -= flourishChance) < 0) {
            this.triggerAnim(ACTION_CONTROLLER, "flourish");
            this.playSound(ModSounds.DRAGON_SOUL_FLOURISH.get(), 1.2f, 1.0f);
            recoveryTicks = 28;
        } else if ((roll -= tauntChance) < 0) {
            this.triggerAnim(ACTION_CONTROLLER, "taunt");
            speak("taunt", false);
            recoveryTicks = 32;
        } else if (roll - pantChance < 0) {
            this.triggerAnim(ACTION_CONTROLLER, "pant");
            maybeSpeak("pant", 0.5);
            recoveryTicks = 40;
        } else {
            recoveryTicks = 10;
        }
    }

    /**
     * 能被攻击判定命中的实体：除她自己、她抓着的人、创造 / 旁观模式玩家外的所有生物（范围攻击会波及周围所有人）
     */
    private boolean canHit(LivingEntity e) {
        return e != this && e.isAlive() && !e.isSpectator() && !this.hasPassenger(e)
                && !(e instanceof Player p && p.isCreative());
    }

    /**
     * 身前扇形范围内的目标：range 为距离，minDot 为与朝向夹角余弦的下限（越小扇形越宽，-1 为一整圈）
     */
    private List<LivingEntity> victimsInFront(ServerLevel level, double range, double minDot) {
        Vec3 look = forward();
        return level.getEntitiesOfClass(LivingEntity.class, this.getBoundingBox().inflate(range), e -> {
            if (!canHit(e)) {
                return false;
            }
            Vec3 to = e.position().subtract(this.position());
            Vec3 flat = horizontal(to);
            if (flat.length() > range + e.getBbWidth() / 2 || Math.abs(to.y) > 4.0) {
                return false;
            }
            return flat.lengthSqr() < 0.25 || flat.normalize().dot(look) >= minDot;
        });
    }

    private void hitAll(List<LivingEntity> victims, float damage) {
        for (LivingEntity victim : victims) {
            if (victim.hurt(this.damageSources().mobAttack(this), damage)) {
                hitLanded = true;
                this.playSound(ModSounds.DRAGON_SOUL_HIT.get(), 1.2f, 1.0f);
            }
        }
    }

    /**
     * 劈：枪尖砸进地面，以落点为圆心一圈都受到伤害
     */
    private void cleaveImpact(ServerLevel level) {
        Vec3 impact = this.position().add(forward().scale(2.2));
        double surface = groundY(level, impact.x, impact.z, this.getY() - 1);
        double ground = surface > this.getY() || surface < this.getY() - 6 ? this.getY() : surface;
        int points = 20;
        for (int i = 0; i < points; i++) {
            double a = i * Math.PI * 2 / points;
            level.sendParticles(ParticleTypes.CLOUD, impact.x + Math.cos(a) * CLEAVE_RADIUS, ground + 0.2,
                    impact.z + Math.sin(a) * CLEAVE_RADIUS, 1, 0.1, 0.05, 0.1, 0.02);
        }
        level.sendParticles(ParticleTypes.EXPLOSION, impact.x, ground + 0.5, impact.z, 1, 0, 0, 0, 0);
        level.sendParticles(ParticleTypes.SWEEP_ATTACK, impact.x, ground + 1.0, impact.z, 4, 1.5, 0.2, 1.5, 0);
        this.playSound(SoundEvents.GENERIC_EXPLODE, 1.0f, 1.4f);
        double radius = CLEAVE_RADIUS;
        List<LivingEntity> victims = level.getEntitiesOfClass(LivingEntity.class,
                new AABB(impact, impact).inflate(radius + 1, 4, radius + 1),
                e -> canHit(e) && horizontal(e.position().subtract(impact)).length() <= radius + e.getBbWidth() / 2
                        && Math.abs(e.getY() - ground) < 4.0);
        hitAll(victims, CLEAVE_DAMAGE);
    }

    private void sweepParticles(ServerLevel level, double radius) {
        Vec3 look = forward();
        for (int i = -3; i <= 3; i++) {
            Vec3 dir = rotateY(look, i * 30.0);
            Vec3 p = this.position().add(dir.scale(radius)).add(0, 1.2, 0);
            level.sendParticles(ParticleTypes.SWEEP_ATTACK, p.x, p.y, p.z, 1, 0, 0, 0, 0);
        }
    }

    // ---------- 抓 ----------

    private boolean tryGrab(ServerLevel level, LivingEntity target) {
        if (!(target instanceof Player player) || player.isCreative() || player.isSpectator()) {
            return false;
        }
        // 惩罚抓取刚瞬移到身边，判定更宽
        boolean inReach = punishing ? this.distanceTo(target) <= 4.5 : victimsInFront(level, 3.5, 0.4).contains(target);
        punishing = false;
        if (!inReach) {
            return false; // 抓空，按普通动作收尾（后摇里喘息）
        }
        target.hurt(this.damageSources().mobAttack(this), GRAB_DAMAGE);
        if (!target.isAlive() || !target.startRiding(this, true)) {
            return false;
        }
        player.stopUsingItem(); // 嘴里的东西掉了
        releasing = false;
        carryStartY = this.getY();
        this.setMode(MODE_CARRY);
        enterState(State.CARRY_UP);
        this.playSound(ModSounds.DRAGON_SOUL_WINGS.get(), 2.0f, 1.0f);
        speak("grab", false);
        return true;
    }

    private void tickCarry(ServerLevel level) {
        Vec3 velocity = new Vec3(0, CARRY_SPEED, 0);
        this.move(MoverType.SELF, velocity);
        this.setDeltaMovement(velocity);
        if (stateTick % 8 == 0) {
            this.playSound(ModSounds.DRAGON_SOUL_WINGS.get(), 2.0f, 1.1f);
        }
        boolean high = this.getY() - carryStartY >= CARRY_HEIGHT;
        if (high || this.verticalCollision || stateTick > CARRY_MAX_TICKS || this.getPassengers().isEmpty()) {
            this.triggerAnim(ACTION_CONTROLLER, "grab_throw");
            speak("throw", true);
            enterState(State.THROW);
        }
    }

    private void tickThrow() {
        this.setDeltaMovement(Vec3.ZERO);
        if (stateTick == 5) {
            releasePassengers(true);
        }
        if (stateTick >= 16) {
            this.setMode(MODE_FIGHT);
            beginRecovery(0.0, 0.5, 0.0);
        }
    }

    private void releasePassengers(boolean fling) {
        releasing = true;
        for (Entity passenger : List.copyOf(this.getPassengers())) {
            passenger.stopRiding();
            if (fling) {
                Vec3 out = forward().scale(0.6);
                passenger.setDeltaMovement(out.x, 0.2, out.z);
                passenger.hurtMarked = true;
            }
        }
        releasing = false;
    }

    /**
     * 抓着的玩家能否自己挣脱（按 Shift 下坐骑）：抓取期间不行
     */
    public boolean isHolding(Entity passenger) {
        return !releasing && (state == State.CARRY_UP || state == State.THROW) && this.hasPassenger(passenger);
    }

    @Override
    protected void positionRider(Entity passenger, Entity.MoveFunction moveFunction) {
        // 抓在左手里：身前 0.9 格，手的高度
        Vec3 front = forward().scale(0.9);
        moveFunction.accept(passenger, this.getX() + front.x, this.getY() + 1.25 - passenger.getBbHeight() * 0.7,
                this.getZ() + front.z);
    }

    @Override
    public boolean shouldRiderSit() {
        return false;
    }

    // ---------- 大招：龙魂解放 ----------

    private boolean canBeUltimateTarget(LivingEntity target) {
        return !(target instanceof Player p && (p.isCreative() || p.isSpectator())) && !target.isPassenger();
    }

    private void startUltimate(ServerLevel level, LivingEntity target) {
        ultimateCooldown = ULTIMATE_COOLDOWN;
        enterState(State.ULTIMATE);
        darkened.clear();
        Vec3 spot = findTeleportSpot(level, target);
        if (spot != null) {
            startTrail(4);
            blinkTo(level, spot, target);
        }
        this.triggerAnim(ACTION_CONTROLLER, ULTIMATE_ANIM);
        this.playSound(ModSounds.DRAGON_SOUL_ULTIMATE.get(), 3.0f, 1.0f);
        speak("ultimate", true);
        level.sendParticles(ParticleTypes.SONIC_BOOM, this.getX(), this.getY() + 1.5, this.getZ(), 1, 0, 0, 0, 0);
    }

    /**
     * 5 秒乱舞：紧贴目标（目标跑开就瞬移跟上），每秒三击范围伤害；被打中的玩家陷入黑暗直到大招结束。期间她无敌。
     */
    private void tickUltimate(ServerLevel level, @Nullable LivingEntity target) {
        this.setDeltaMovement(Vec3.ZERO);
        boolean hasTarget = target != null && target.isAlive();
        if (hasTarget) {
            faceTowards(target, 0.6f);
            if (stateTick < ULT_START_TICKS + ULT_FLURRY_TICKS
                    && horizontal(target.position().subtract(this.position())).length() > 3.2) {
                Vec3 spot = findTeleportSpot(level, target);
                if (spot != null) {
                    startTrail(3);
                    blinkTo(level, spot, target);
                }
            }
        }
        level.sendParticles(ParticleTypes.DRAGON_BREATH, this.getX(), this.getY() + 1.2, this.getZ(), 3, 0.5, 0.8, 0.5, 0.01);

        int t = stateTick - ULT_START_TICKS;
        if (t >= 0 && t < ULT_FLURRY_TICKS) {
            int inCycle = t % 20;
            for (int hit : ULT_HIT_TICKS) {
                if (inCycle == hit) {
                    this.playSound(inCycle == 10 ? ModSounds.DRAGON_SOUL_SLASH.get() : ModSounds.DRAGON_SOUL_THRUST.get(), 1.5f, 1.2f);
                    sweepParticles(level, 2.5);
                    ultimateHit(level, ULT_HIT_DAMAGE, ULT_TOTAL - stateTick);
                }
            }
        }
        if (stateTick == ULT_START_TICKS + ULT_FLURRY_TICKS + 6) {
            this.playSound(ModSounds.DRAGON_SOUL_CLEAVE.get(), 2.0f, 0.8f);
            cleaveImpact(level);
            ultimateHit(level, ULT_FINISH_DAMAGE, 0);
        }
        if (stateTick >= ULT_TOTAL) {
            endUltimate(level);
        }
    }

    private void ultimateHit(ServerLevel level, float damage, int darknessTicks) {
        for (LivingEntity victim : victimsInFront(level, ULT_RANGE, -0.3)) {
            victim.invulnerableTime = 0; // 一秒三击，不能被受击无敌帧吃掉
            if (victim.hurt(this.damageSources().mobAttack(this), damage)) {
                this.playSound(ModSounds.DRAGON_SOUL_HIT.get(), 1.2f, 1.1f);
                if (victim instanceof Player && darknessTicks > 0) {
                    victim.addEffect(new MobEffectInstance(MobEffects.DARKNESS, darknessTicks + 10, 0, false, false), this);
                    darkened.add(victim.getUUID());
                }
            }
        }
    }

    private void endUltimate(ServerLevel level) {
        for (UUID id : darkened) {
            Entity e = level.getEntity(id);
            if (e instanceof LivingEntity living) {
                living.removeEffect(MobEffects.DARKNESS);
            }
        }
        darkened.clear();
        // 大招后一定喘口气：这是全场最好的反击机会
        beginRecovery(0.0, 0.0, 1.0);
    }

    // ---------- 法阵龙息 ----------

    private void tickMagic(ServerLevel level, LivingEntity target) {
        Vec3 hand = handPosition();
        if (stateTick >= 10 && stateTick <= 36) {
            drawMagicCircle(level, hand.add(forward().scale(1.0)));
        }
        int first = attack.hitTick;
        if (stateTick >= first && (stateTick - first) % 4 == 0 && (stateTick - first) / 4 < MAGIC_SHOTS && target != null) {
            int shot = (stateTick - first) / 4;
            Vec3 origin = hand.add(forward().scale(1.2));
            Vec3 aim = target.getEyePosition().subtract(origin);
            // 五发呈扇形依次射出：-20, -10, 0, 10, 20 度
            Vec3 dir = rotateY(aim.normalize(), (shot - 2) * 10.0);
            DragonFireball fireball = new DragonFireball(level, this, dir.x * 0.1, dir.y * 0.1, dir.z * 0.1);
            fireball.setPos(origin.x, origin.y, origin.z);
            level.addFreshEntity(fireball);
            this.playSound(SoundEvents.ENDER_DRAGON_SHOOT, 1.5f, 1.1f);
        }
    }

    private void drawMagicCircle(ServerLevel level, Vec3 center) {
        Vec3 look = forward();
        Vec3 right = new Vec3(-look.z, 0, look.x);
        double spin = tickCounter * 0.2;
        int points = 16;
        for (int i = 0; i < points; i++) {
            double a = spin + i * Math.PI * 2 / points;
            Vec3 p = center.add(right.scale(Math.cos(a) * 1.2)).add(0, Math.sin(a) * 1.2, 0);
            level.sendParticles(MAGIC_CIRCLE, p.x, p.y, p.z, 1, 0, 0, 0, 0);
        }
        level.sendParticles(ParticleTypes.DRAGON_BREATH, center.x, center.y, center.z, 2, 0.3, 0.3, 0.3, 0.01);
    }

    // ---------- 掷枪 ----------

    private void throwSpear(ServerLevel level, LivingEntity target) {
        Vec3 origin = handPosition().add(0, 0.6, 0);
        // 预判：朝目标 8 tick 后的位置投掷
        Vec3 aimAt = target.getEyePosition().add(target.getDeltaMovement().scale(8)).subtract(0, 0.4, 0);
        Vec3 velocity = aimAt.subtract(origin).normalize().scale(1.8);
        level.addFreshEntity(new DragonSpear(level, this, origin, velocity));
        this.entityData.set(DATA_SPEAR_OUT, true);
        this.playSound(ModSounds.DRAGON_SOUL_SPEAR_THROW.get(), 1.5f, 1.0f);
        enterState(State.WAIT_SPEAR);
    }

    /**
     * 长枪飞回手中（由 DragonSpear 调用；超时也会调用）
     */
    public void onSpearReturned() {
        if (!spearOut()) {
            return;
        }
        this.entityData.set(DATA_SPEAR_OUT, false);
        this.playSound(ModSounds.DRAGON_SOUL_SPEAR_RETURN.get(), 1.5f, 1.0f);
        if (state == State.WAIT_SPEAR) {
            this.triggerAnim(ACTION_CONTROLLER, "spear_catch");
            enterState(State.CATCH);
        }
    }

    /**
     * 右手（持枪手）在世界中的大致位置
     */
    public Vec3 handPosition() {
        Vec3 look = forward();
        Vec3 right = new Vec3(-look.z, 0, look.x);
        return this.position().add(right.scale(-0.35)).add(look.scale(0.3)).add(0, 1.25, 0);
    }

    // ==================== 末地水晶 ====================

    /**
     * 同末影龙：32 格内最近的末地水晶每 10 tick 治疗 1 点并连出光束；该水晶被打爆时受到 10 点伤害
     */
    private void tickCrystalHealing(ServerLevel level) {
        if (healingCrystal != null) {
            if (healingCrystal.isRemoved() || !healingCrystal.isAlive()) {
                if (healingCrystal.getRemovalReason() == RemovalReason.KILLED) {
                    this.hurt(this.damageSources().explosion(null, null), CRYSTAL_DESTROYED_DAMAGE);
                }
                healingCrystal = null;
            } else if (healingCrystal.distanceToSqr(this) > CRYSTAL_RANGE * CRYSTAL_RANGE * 1.5) {
                healingCrystal.setBeamTarget(null);
                healingCrystal = null;
            } else {
                if (tickCounter % 10 == 0 && this.getHealth() < this.getMaxHealth()) {
                    this.heal(1.0f);
                }
                if (tickCounter % 5 == 0) {
                    healingCrystal.setBeamTarget(BlockPos.containing(this.getX(), this.getY() + 1.0, this.getZ()));
                }
            }
        }
        if (tickCounter % 20 == 0) {
            List<EndCrystal> crystals = level.getEntitiesOfClass(EndCrystal.class, this.getBoundingBox().inflate(CRYSTAL_RANGE));
            EndCrystal nearest = crystals.stream().min(Comparator.comparingDouble(c -> c.distanceToSqr(this))).orElse(null);
            if (nearest != healingCrystal) {
                if (healingCrystal != null && healingCrystal.isAlive()) {
                    healingCrystal.setBeamTarget(null);
                }
                healingCrystal = nearest;
            }
        }
    }

    private void healFromDragonBreath(ServerLevel level) {
        if (this.getHealth() >= this.getMaxHealth()) {
            return;
        }
        for (AreaEffectCloud cloud : level.getEntitiesOfClass(AreaEffectCloud.class, this.getBoundingBox().inflate(0.5))) {
            if (cloud.getParticle() == ParticleTypes.DRAGON_BREATH) {
                this.heal(DRAGON_BREATH_HEAL);
                break;
            }
        }
    }

    // ==================== 位移与朝向工具 ====================

    private Vec3 forward() {
        float yaw = this.getYRot() * Mth.DEG_TO_RAD;
        return new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw));
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
        float next = Mth.rotLerp(speed, this.getYRot(), yawOf(flat));
        this.setYRot(next);
        this.yBodyRot = next;
        this.yHeadRot = next;
        this.setXRot(0);
    }

    private static float yawOf(Vec3 direction) {
        return (float) (Mth.atan2(direction.z, direction.x) * Mth.RAD_TO_DEG) - 90.0f;
    }

    private void moveSmoothlyTo(Vec3 pos) {
        this.setDeltaMovement(pos.subtract(this.position()));
        this.setPos(pos.x, pos.y, pos.z);
    }

    @Nullable
    private Player nearestPlayer(ServerLevel level) {
        return level.getNearestPlayer(this.getX(), this.getY(), this.getZ(), CAMERA_RANGE, false);
    }

    private static double groundY(ServerLevel level, double x, double z, double fallback) {
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(x), Mth.floor(z));
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

    private static double easeOut(double u) {
        return 1 - (1 - u) * (1 - u);
    }

    private static double smoothstep(double u) {
        return u * u * (3 - 2 * u);
    }

    /**
     * 客户端位置插值：
     * - 正常移动（包括出场盘旋，每 tick 约 1 格）用 2 步插值，连续平滑；
     * - 高速冲刺（DATA_TRAIL）用 1 步插值，每 tick 精确到位，渲染时在两 tick 之间平滑过渡并留下残影；
     * - 和上一个目标点相差 4 格以上视为瞬移，直接到位（不然会看到她分几段"滑"过去），原地留一个残影。
     */
    @Override
    public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps, boolean teleport) {
        if (this.level().isClientSide) {
            boolean lerping = this.lerpSteps > 0;
            double fromX = lerping ? this.lerpX : this.getX();
            double fromY = lerping ? this.lerpY : this.getY();
            double fromZ = lerping ? this.lerpZ : this.getZ();
            double jumpSqr = (x - fromX) * (x - fromX) + (y - fromY) * (y - fromY) + (z - fromZ) * (z - fromZ);
            if (!trail() && jumpSqr > 16.0) {
                if (!this.isInvisible()) {
                    afterimages.add(new Afterimage(this.getX(), this.getY(), this.getZ(), this.yBodyRot, this.tickCount));
                }
                this.setPos(x, y, z);
                this.xo = x;
                this.yo = y;
                this.zo = z;
                this.xOld = x;
                this.yOld = y;
                this.zOld = z;
                super.lerpTo(x, y, z, yRot, xRot, 1, teleport);
                return;
            }
            steps = trail() ? 1 : 2;
        }
        super.lerpTo(x, y, z, yRot, xRot, steps, teleport);
    }

    // ==================== 伤害与免疫 ====================

    @Override
    public boolean hurt(DamageSource source, float amount) {
        // 出场期间、大招期间无敌（/kill 之类绕过无敌的伤害除外）
        if ((!introDone || state == State.ULTIMATE) && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return false;
        }
        return super.hurt(source, amount);
    }

    @Override
    public boolean canBeAffected(MobEffectInstance effect) {
        return false;
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

    @Override
    public boolean isInvulnerableTo(DamageSource damageSource) {
        if (damageSource.is(DamageTypes.WITHER) || damageSource.is(DamageTypes.WITHER_SKULL)
                || damageSource.is(DamageTypes.DRAGON_BREATH) || damageSource.is(DamageTypes.IN_WALL)
                || damageSource.getEntity() == this) {
            return true;
        }
        return super.isInvulnerableTo(damageSource);
    }

    @Nullable
    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return ModSounds.DRAGON_SOUL_HURT.get();
    }

    @Override
    protected float getSoundVolume() {
        return 1.5f;
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
    public void die(DamageSource damageSource) {
        this.bossEvent.removeAllPlayers();
        if (!this.level().isClientSide && !this.dead) {
            speak("death", true);
        }
        if (!this.level().isClientSide && this.level() instanceof ServerLevel level && state == State.ULTIMATE) {
            endUltimate(level);
        }
        super.die(damageSource);
    }

    @Override
    public void remove(RemovalReason reason) {
        if (!this.level().isClientSide) {
            releasePassengers(false);
            if (healingCrystal != null && healingCrystal.isAlive()) {
                healingCrystal.setBeamTarget(null);
            }
            if (rift != null && rift.isAlive()) {
                rift.discard();
            }
        }
        super.remove(reason);
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

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("IntroDone", introDone);
        tag.putInt("IntroTick", introTick);
        tag.putDouble("RiftX", riftPos.x);
        tag.putDouble("RiftY", riftPos.y);
        tag.putDouble("RiftZ", riftPos.z);
        tag.putDouble("EmergeX", emergeDir.x);
        tag.putDouble("EmergeZ", emergeDir.z);
        tag.putInt("UltimateCooldown", ultimateCooldown);
        tag.putBoolean("HalfSpoken", halfSpoken);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        // 旧版本存档里的龙魂没有这些字段：直接进入战斗
        introDone = !tag.contains("IntroDone") || tag.getBoolean("IntroDone");
        introTick = tag.getInt("IntroTick");
        riftPos = new Vec3(tag.getDouble("RiftX"), tag.getDouble("RiftY"), tag.getDouble("RiftZ"));
        if (tag.contains("EmergeX")) {
            emergeDir = new Vec3(tag.getDouble("EmergeX"), 0, tag.getDouble("EmergeZ"));
        }
        if (tag.contains("UltimateCooldown")) {
            ultimateCooldown = tag.getInt("UltimateCooldown");
        }
        halfSpoken = tag.getBoolean("HalfSpoken");
        engageSpoken = true;
        if (introDone) {
            this.setInvisible(false);
            this.setMode(MODE_FIGHT);
        } else {
            this.bossEvent.setVisible(false);
            if (introTick < DASH_START) {
                // 裂缝实体不存档：从头重新撕开一次
                introTick = Math.min(introTick, RIFT_START - 1);
                this.setInvisible(true);
                this.setMode(MODE_HIDDEN);
            }
        }
        this.entityData.set(DATA_SPEAR_OUT, false);
    }

    // ==================== GeckoLib 动画 ====================

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        // base：按模式循环播放 悬停 / 战斗姿态 / 飞行 / 抓人飞行（头发、裙甲、翅膀的自然摆动都在这里）
        controllers.add(new AnimationController<>(this, BASE_CONTROLLER, 6, state -> switch (mode()) {
            case MODE_FLY -> state.setAndContinue(FLY);
            case MODE_CARRY -> state.setAndContinue(CARRY);
            case MODE_FIGHT -> state.setAndContinue(STANCE);
            default -> state.setAndContinue(HOVER);
        }));
        // action：一次性动作（出招、后摇、出场、大招），覆盖 base 的同名骨骼
        AnimationController<DragonSoulBoss> action = new AnimationController<>(this, ACTION_CONTROLLER, 3, state -> PlayState.STOP);
        for (String name : ACTIONS) {
            action.triggerableAnim(name, RawAnimation.begin().thenPlay("boss_" + name));
        }
        action.triggerableAnim(ULTIMATE_ANIM, RawAnimation.begin().thenPlay("boss_ult_start")
                .thenPlayXTimes("boss_ult_flurry", 5).thenPlay("boss_ult_finish"));
        controllers.add(action);
        // blink：只动眼睛，每隔几秒眨一次
        controllers.add(new AnimationController<>(this, BLINK_CONTROLLER, 0, state -> state.setAndContinue(BLINK)));
        // spear：长枪掷出后到飞回之前，手里的枪隐藏
        controllers.add(new AnimationController<>(this, SPEAR_CONTROLLER, 0,
                state -> spearOut() ? state.setAndContinue(SPEAR_HIDDEN) : PlayState.STOP));
        // face：说台词时的表情（眉毛、眼睛）和口型，说话期间覆盖眨眼
        AnimationController<DragonSoulBoss> face = new AnimationController<>(this, FACE_CONTROLLER, 2, state -> PlayState.STOP);
        for (String id : ModSounds.DRAGON_SOUL_VOICE_IDS) {
            face.triggerableAnim("voice_" + id, RawAnimation.begin().thenPlay("boss_voice_" + id));
        }
        controllers.add(face);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }
}
