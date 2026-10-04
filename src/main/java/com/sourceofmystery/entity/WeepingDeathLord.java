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
import net.minecraft.network.protocol.game.ClientboundSetTimePacket;
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
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.state.BlockState;
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
import java.util.List;
import java.util.UUID;

/**
 * 泣死之主：原版凋灵死亡后，在它爆开的位置诞生的中立 Boss。
 * <p>
 * 出场（约 19.5 秒，期间附近玩家进入电影镜头，她无敌，天空日夜交替越来越快）：
 * 凋灵变白、加速旋转 2 秒后爆炸 → 她蜷缩抱膝漂浮 2 秒（镰刀竖着漂浮在身旁冒紫色粒子）→ 脸部特写，5 秒内缓缓睁眼，
 * 眼睛发出白光（亮度同火把，完全睁开时最亮）→ 猛地张开双臂，凋灵咆哮 3 秒 → 镰刀飞回手中，扛在肩上看向玩家
 * → 镜头从玩家身前绕到身后，定格 2 秒。镜头由客户端 WeepingIntroDirector 按 {@link #INTRO_TICKS} 的时间表播放。
 * <p>
 * 中立：玩家不攻击她，她就不攻击玩家；但会主动攻击出生点 120 格内的动物和怪物。平时在出生点 120 格内像水母一样漂荡。
 * <p>
 * 招式（扛着笨重的镰刀甩打，前摇都在 2 秒内，后摇都在 3 秒以上）：
 * 劈斩（镰刀变大砸地，地震 30）、凋灵头（掷出镰刀定身，双手两个凋灵头每秒 3 发、持续 5 秒，每发 10）、
 * 吸附（10 秒内每秒把人拉近 2 格，进入 3 格就抓住斩碎空间，100）、快速攻击（5 秒带残影，每秒 3 刀，每刀 5）。
 */
public class WeepingDeathLord extends PathfinderMob implements GeoEntity {

    // ==================== 动画 ====================
    private static final String PHYSICS_CONTROLLER = "physics";
    private static final String BASE_CONTROLLER = "base";
    private static final String BLINK_CONTROLLER = "blink";
    private static final String ACTION_CONTROLLER = "action";

    private static final RawAnimation PHYS_CALM = RawAnimation.begin().thenLoop("wl_phys_calm");
    private static final RawAnimation PHYS_COMBAT = RawAnimation.begin().thenLoop("wl_phys_combat");
    private static final RawAnimation HOVER = RawAnimation.begin().thenLoop("wl_hover");
    private static final RawAnimation DRIFT = RawAnimation.begin().thenLoop("wl_drift");
    private static final RawAnimation BLINK = RawAnimation.begin().thenLoop("wl_blink");

    private static final String[] PLAY_ONCE = {"toss", "cleave", "skull_end", "absorb_slash", "absorb_end", "rapid_end"};
    private static final String[] PLAY_AND_HOLD = {"intro_wake", "intro_roar", "intro_shoulder", "skull_throw", "skull_raise",
            "absorb_start", "absorb_grab", "rapid_start"};
    private static final String[] LOOPS = {"intro_curl", "skull_fire", "absorb_hold", "rapid_loop"};

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

    // ==================== 出场时间表（tick） ====================
    public static final int HUSK_TICKS = 40;          // 凋灵变白旋转 2 秒后爆炸
    public static final int CLOSEUP_START = 80;       // 蜷缩漂浮 2 秒后：脸部特写，开始睁眼
    public static final int ROAR_START = 180;         // 5 秒睁眼后：张开双臂咆哮
    public static final int SHOULDER_START = 240;     // 3 秒咆哮后：镰刀飞回手中，扛在肩上
    private static final int SHOULDER_CATCH = SHOULDER_START + 14;
    public static final int DOLLY_START = 280;        // 镜头从玩家身前绕到身后
    public static final int FREEZE_START = 350;       // 定格 2 秒
    public static final int INTRO_TICKS = 390;
    private static final int DAY_SPIN_DAYS = 4;       // 出场期间天空转过整整 4 天，结束时回到原来的时间
    private static final double CAMERA_RANGE = 128.0;

    // ==================== 移动 ====================
    public static final double WANDER_RADIUS = 120.0;
    private static final double LEASH_RADIUS = WANDER_RADIUS + 40.0;
    private static final double HOVER_HEIGHT = 1.6;    // 脚离地高度
    private static final double DRIFT_SPEED = 0.16;
    private static final double CHASE_SPEED = 0.32;

    // ==================== 招式 ====================
    private static final float SCALE = 1.25f;          // 渲染缩放（模型约 3.6 格高）

    // 劈斩：wl_cleave 5.2 秒，1.8 秒砸地
    private static final int CLEAVE_LENGTH = 104;
    private static final int CLEAVE_AIM_LOCK = 28;
    private static final int CLEAVE_HIT = 36;
    private static final float CLEAVE_DAMAGE = 30.0f;
    private static final double CLEAVE_REACH = 6.5;     // 变大的镰刀砸在身前这么远
    private static final double QUAKE_RADIUS = 7.0;
    private static final double CLEAVE_RANGE = 8.0;     // 进入这个距离才起手

    // 凋灵头：掷镰刀 1.6 秒（1.15 秒出手）→ 抬手 1 秒 → 发射 5 秒 → 镰刀飞回 → 后摇 3.4 秒
    private static final int THROW_LENGTH = 32;
    private static final int THROW_RELEASE = 23;
    private static final int RAISE_LENGTH = 20;
    private static final int FIRE_TICKS = 100;
    private static final int SKULL_SHOTS = 15;           // 每秒 3 发 × 5 秒
    private static final float SKULL_DAMAGE = 10.0f;
    private static final int SKULL_END_LENGTH = 68;
    private static final int SCYTHE_RETURN_TIMEOUT = 60;

    // 吸附：起手 1.5 秒，蓄力 10 秒；抓住后 0.5 秒斩下（斩击动画第 5 tick 命中）
    private static final int ABSORB_START_LENGTH = 30;
    private static final int ABSORB_CHARGE_TICKS = 200;
    private static final double PULL_SPEED = 0.1;        // 每秒 2 格
    private static final double PULL_RANGE = 24.0;
    private static final double GRAB_RANGE = 3.0;
    private static final int GRAB_HOLD = 10;
    private static final int SLASH_HIT = 5;
    private static final int SLASH_LENGTH = 80;
    private static final int ABSORB_END_LENGTH = 64;
    private static final float SLASH_DAMAGE = 100.0f;

    // 快速攻击：起手 0.5 秒，5 秒内每秒 3 刀（每秒第 4 / 11 / 17 tick 命中），后摇 3.2 秒
    private static final int RAPID_START_LENGTH = 10;
    private static final int RAPID_TICKS = 100;
    private static final int[] RAPID_HITS = {4, 11, 17};
    private static final float RAPID_DAMAGE = 5.0f;
    private static final double RAPID_RANGE = 4.5;
    private static final int RAPID_END_LENGTH = 64;

    private static final int TOSS_LENGTH = 92;

    private static final DustParticleOptions PURPLE = new DustParticleOptions(new Vector3f(0.62f, 0.2f, 0.95f), 1.6f);
    private static final DustParticleOptions BLACK = new DustParticleOptions(new Vector3f(0.05f, 0.02f, 0.08f), 2.2f);

    private enum Attack { CLEAVE, SKULL, ABSORB, RAPID }

    private enum State {
        IDLE, APPROACH, CLEAVE, SKULL_THROW, SKULL_RAISE, SKULL_FIRE, SKULL_WAIT_RETURN, SKULL_END,
        ABSORB_START, ABSORB_CHARGE, ABSORB_GRAB, ABSORB_SLASH, ABSORB_END, RAPID_START, RAPID, RAPID_END, TOSS
    }

    // ==================== 状态 ====================
    private boolean introDone = true;     // /summon 直接召唤时没有出场
    private int introTick;
    private Vec3 home = Vec3.ZERO;
    private boolean homeSet;
    @Nullable
    private UUID focusPlayer;
    private long dayTimeStart = -1;
    @Nullable
    private BlockPos lightPos;
    @Nullable
    private WitherHusk husk;

    private State state = State.IDLE;
    private int stateTick;
    private Attack pending = Attack.CLEAVE;
    private int cooldown = 40;
    private int tossCooldown = 300;
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

    // 客户端：残影
    public record Afterimage(double x, double y, double z, float yaw, float born) {
    }

    public static final int AFTERIMAGE_LIFE = 8;
    public final List<Afterimage> afterimages = new ArrayList<>();
    public boolean renderingAfterimages;

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

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 8000.0)
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

    public float orbSize() {
        return this.entityData.get(DATA_ORB);
    }

    public float eyeGlow() {
        return this.entityData.get(DATA_EYE_GLOW);
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
        this.introTick = 0;
        this.home = this.position();
        this.homeSet = true;
        this.setHidden(true);
        this.entityData.set(DATA_EYE_GLOW, 0.0f);
        this.focusPlayer = focus == null ? null : focus.getUUID();
        faceFocus(level, 1.0f);
        if (level.dimensionType().hasSkyLight() && !level.dimensionType().hasFixedTime()) {
            dayTimeStart = level.getDayTime();
        }

        husk = ModEntities.WITHER_HUSK.get().create(level);
        if (husk != null) {
            husk.moveTo(wither.getX(), wither.getY(), wither.getZ(), wither.yBodyRot, 0);
            level.addFreshEntity(husk);
        }
        Vec3 at = wither.position();
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(at) < CAMERA_RANGE * CAMERA_RANGE) {
                ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                        new WeepingIntroPacket(this.getId(), at.x, at.y, at.z, INTRO_TICKS));
            }
        }
    }

    private void tickIntro(ServerLevel level) {
        introTick++;
        faceFocus(level, 0.2f);
        spinSky(level);
        this.setDeltaMovement(Vec3.ZERO);

        if (introTick < HUSK_TICKS) {
            if (husk != null && introTick % 2 == 0) {
                level.sendParticles(ParticleTypes.END_ROD, husk.getX(), husk.getY() + 2.0, husk.getZ(),
                        2 + introTick / 8, 0.8, 1.2, 0.8, 0.02);
            }
            return;
        }
        if (introTick == HUSK_TICKS) {
            explodeHusk(level);
            this.setHidden(false);
            this.triggerAnim(ACTION_CONTROLLER, "intro_curl");
        }
        if (introTick < SHOULDER_CATCH) {
            floatingScytheParticles(level);
        }
        if (introTick == CLOSEUP_START) {
            this.triggerAnim(ACTION_CONTROLLER, "intro_wake");
        }
        if (introTick >= CLOSEUP_START && introTick <= ROAR_START) {
            float u = (introTick - CLOSEUP_START) / (float) (ROAR_START - CLOSEUP_START);
            float glow = u * u * (3 - 2 * u);
            this.entityData.set(DATA_EYE_GLOW, glow);
            updateEyeLight(level, Math.round(14 * glow));
        }
        if (introTick == ROAR_START) {
            this.triggerAnim(ACTION_CONTROLLER, "intro_roar");
            level.playSound(null, this.getX(), this.getY() + 2, this.getZ(), ModSounds.WEEPING_ROAR.get(), SoundSource.HOSTILE, 8.0f, 1.0f);
            level.playSound(null, this.getX(), this.getY() + 2, this.getZ(), SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 8.0f, 0.8f);
            screenEffect(level, 4, 60, 3.0f, 64);
            level.sendParticles(ParticleTypes.SOUL, this.getX(), this.getY() + 2, this.getZ(), 80, 1.5, 1.5, 1.5, 0.25);
            level.sendParticles(ParticleTypes.LARGE_SMOKE, this.getX(), this.getY() + 2, this.getZ(), 60, 1.0, 1.0, 1.0, 0.2);
        }
        if (introTick == SHOULDER_START) {
            this.triggerAnim(ACTION_CONTROLLER, "intro_shoulder");
            this.playSound(ModSounds.WEEPING_THROW.get(), 1.5f, 0.6f);
        }
        if (introTick == SHOULDER_CATCH) {
            this.playSound(SoundEvents.ARMOR_EQUIP_NETHERITE, 2.0f, 0.6f);
        }
        if (introTick >= INTRO_TICKS) {
            finishIntro(level);
        }
    }

    /** 出场：天空日夜交替越来越快，总共转过整整几天，结束时回到原来的时间 */
    private void spinSky(ServerLevel level) {
        if (dayTimeStart < 0) {
            return;
        }
        double u = Math.min(1.0, introTick / (double) INTRO_TICKS);
        long target = dayTimeStart + Math.round(u * u * DAY_SPIN_DAYS * 24000L);
        level.setDayTime(target);
        boolean cycle = level.getGameRules().getBoolean(GameRules.RULE_DAYLIGHT);
        ClientboundSetTimePacket packet = new ClientboundSetTimePacket(level.getGameTime(), level.getDayTime(), cycle);
        for (ServerPlayer player : level.players()) {
            player.connection.send(packet);
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
        screenEffect(level, 12, 25, 2.5f, 96);
    }

    private void floatingScytheParticles(ServerLevel level) {
        Vec3 right = rightVec();
        Vec3 base = this.position().add(right.scale(1.9)).add(forward().scale(0.6));
        for (int i = 0; i < 2; i++) {
            double h = 0.6 + this.random.nextDouble() * 3.6;
            level.sendParticles(PURPLE, base.x, base.y + h, base.z, 1, 0.12, 0.1, 0.12, 0);
        }
        if (introTick % 3 == 0) {
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
        if (dayTimeStart >= 0) {
            level.setDayTime(dayTimeStart + DAY_SPIN_DAYS * 24000L);
            dayTimeStart = -1;
        }
        this.stopTriggeredAnimation(ACTION_CONTROLLER, null);
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

        stateTick++;
        switch (state) {
            case IDLE -> tickIdle(level, target);
            case APPROACH -> tickApproach(level, target);
            case TOSS -> {
                hoverInPlace(level);
                if (stateTick >= TOSS_LENGTH) enterState(State.IDLE);
            }
            case CLEAVE -> tickCleave(level, target);
            case SKULL_THROW, SKULL_RAISE, SKULL_FIRE, SKULL_WAIT_RETURN, SKULL_END -> tickSkull(level, target);
            case ABSORB_START, ABSORB_CHARGE, ABSORB_GRAB, ABSORB_SLASH, ABSORB_END -> tickAbsorb(level, target);
            case RAPID_START, RAPID, RAPID_END -> tickRapid(level, target);
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

    private void play(String anim) {
        this.triggerAnim(ACTION_CONTROLLER, anim);
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
        };
    }

    private void tickApproach(ServerLevel level, @Nullable LivingEntity target) {
        if (target == null) {
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
                this.playSound(ModSounds.WEEPING_SWING.get(), 2.0f, 0.5f);
                enterState(State.CLEAVE);
            }
            case SKULL -> {
                play("skull_throw");
                enterState(State.SKULL_THROW);
            }
            case ABSORB -> {
                play("absorb_start");
                this.playSound(ModSounds.WEEPING_CHARGE.get(), 2.0f, 0.6f);
                enterState(State.ABSORB_START);
            }
            case RAPID -> {
                play("rapid_start");
                enterState(State.RAPID_START);
            }
        }
    }

    private void endAttack(int extraCooldown) {
        enterState(State.IDLE);
        cooldown = 50 + this.random.nextInt(50) + extraCooldown;
    }

    // ==================== 劈斩 ====================

    private void tickCleave(ServerLevel level, @Nullable LivingEntity target) {
        if (stateTick < CLEAVE_AIM_LOCK) {
            faceTowards(target, 0.3f);
            cleaveDir = forward();
        }
        hoverInPlace(level);
        if (stateTick == CLEAVE_HIT - 6) {
            this.playSound(ModSounds.WEEPING_SWING.get(), 2.5f, 0.4f);
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
        level.playSound(null, impact.x, impact.y, impact.z, ModSounds.WEEPING_QUAKE.get(), SoundSource.HOSTILE, 4.0f, 1.0f);
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
                if (stateTick == THROW_RELEASE) {
                    throwScythe(level, target);
                }
                if (stateTick >= THROW_LENGTH) {
                    play("skull_raise");
                    this.playSound(SoundEvents.WITHER_AMBIENT, 2.0f, 0.6f);
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
                // 每秒 3 发：第 k 发在 k * 20 / 3 tick，右、左手交替（和 wl_skull_fire 的后坐力一致）
                while (skullShots < SKULL_SHOTS && stateTick >= Math.round(skullShots * 20 / 3.0f)) {
                    fireSkull(level, target, skullShots % 2 == 0);
                    skullShots++;
                }
                if (stateTick >= FIRE_TICKS) {
                    recallScythe();
                    enterState(State.SKULL_WAIT_RETURN);
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
        this.playSound(ModSounds.WEEPING_THROW.get(), 2.0f, 0.7f);
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
        this.playSound(SoundEvents.TRIDENT_RETURN, 2.0f, 0.6f);
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
                this.entityData.set(DATA_ORB, 0.15f * stateTick / ABSORB_START_LENGTH);
                chargeParticles(level);
                if (stateTick >= ABSORB_START_LENGTH) {
                    play("absorb_hold");
                    enterState(State.ABSORB_CHARGE);
                }
            }
            case ABSORB_CHARGE -> {
                faceTowards(target, 0.2f);
                float progress = stateTick / (float) ABSORB_CHARGE_TICKS;
                this.entityData.set(DATA_ORB, 0.15f + 0.85f * progress);
                chargeParticles(level);
                if (stateTick % 40 == 1) {
                    this.playSound(ModSounds.WEEPING_CHARGE.get(), 2.0f, 0.6f + progress * 0.5f);
                }
                Player caught = pullVictims(level);
                if (caught != null) {
                    grab(level, caught);
                    return;
                }
                if (stateTick >= ABSORB_CHARGE_TICKS) {
                    this.entityData.set(DATA_ORB, 0.0f);
                    level.sendParticles(ParticleTypes.LARGE_SMOKE, orbPosition().x, orbPosition().y, orbPosition().z, 30, 0.3, 0.3, 0.3, 0.05);
                    play("absorb_end");
                    enterState(State.ABSORB_END);
                }
            }
            case ABSORB_GRAB -> {
                holdGrabbed();
                if (stateTick >= GRAB_HOLD) {
                    play("absorb_slash");
                    this.playSound(ModSounds.WEEPING_SWING.get(), 2.5f, 0.45f);
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

    private void chargeParticles(ServerLevel level) {
        Vec3 blade = this.position().add(rightVec().scale(0.7)).add(0, 4.4, 0).subtract(forward().scale(0.8));
        level.sendParticles(BLACK, blade.x, blade.y, blade.z, 6, 0.8, 0.9, 0.8, 0);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, blade.x, blade.y, blade.z, 2, 0.6, 0.7, 0.6, 0.01);
        if (tickCounter % 2 == 0) {
            level.sendParticles(ParticleTypes.SQUID_INK, blade.x, blade.y, blade.z, 2, 0.5, 0.6, 0.5, 0.02);
        }
        // 黑色烟尘从四周汇入左手的黑球（汇聚粒子：count = 0，偏移 = 终点 - 起点）
        Vec3 orb = orbPosition();
        for (int i = 0; i < 3; i++) {
            Vec3 from = orb.add((this.random.nextDouble() - 0.5) * 3, (this.random.nextDouble() - 0.5) * 3,
                    (this.random.nextDouble() - 0.5) * 3);
            level.sendParticles(ModParticles.DARK_MOTE.get(), from.x, from.y, from.z, 0,
                    orb.x - from.x, orb.y - from.y, orb.z - from.z, 1.0);
        }
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
        this.playSound(ModSounds.WEEPING_GRAB.get(), 2.0f, 0.8f);
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
        level.playSound(null, at.x, at.y, at.z, ModSounds.WEEPING_SHATTER.get(), SoundSource.HOSTILE, 3.0f, 1.0f);
        screenEffect(level, 6, 20, 2.5f, 32);
        if (grabbed != null && grabbed.isAlive()) {
            grabbed.hurt(this.damageSources().mobAttack(this), SLASH_DAMAGE);
        }
        releaseGrabbed();
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
                int inSecond = (stateTick - 1) % 20;
                for (int hit : RAPID_HITS) {
                    if (inSecond == hit) {
                        rapidHit(level);
                    }
                    if (inSecond == hit - 3) {
                        this.playSound(ModSounds.WEEPING_SWING.get(), 1.4f, 0.9f + this.random.nextFloat() * 0.3f);
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
            if (thrownScythe != null) {
                thrownScythe.releasePinned();
                thrownScythe.discard();
                thrownScythe = null;
            }
            if (husk != null) {
                husk.discard();
                husk = null;
            }
            if (dayTimeStart >= 0) {
                level.setDayTime(dayTimeStart + DAY_SPIN_DAYS * 24000L);
                dayTimeStart = -1;
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
        if (dayTimeStart >= 0) {
            tag.putLong("DayTimeStart", dayTimeStart);
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("HomeX")) {
            home = new Vec3(tag.getDouble("HomeX"), tag.getDouble("HomeY"), tag.getDouble("HomeZ"));
            homeSet = true;
        }
        // 出场演出不存档：读档时直接进入正常状态，把天空时间还原
        introDone = true;
        if (tag.contains("DayTimeStart") && this.level() instanceof ServerLevel level) {
            level.setDayTime(tag.getLong("DayTimeStart") + DAY_SPIN_DAYS * 24000L);
        }
        this.setHidden(false);
        this.entityData.set(DATA_EYE_GLOW, 1.0f);
        this.entityData.set(DATA_SCYTHE_OUT, false);
        this.entityData.set(DATA_ORB, 0.0f);
    }

    // ==================== GeckoLib ====================

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        // physics：头发、胸部、裙甲、尾巴、凋灵头的循环摆动，战斗时更快更大
        controllers.add(new AnimationController<>(this, PHYSICS_CONTROLLER, 10,
                s -> s.setAndContinue(combat() ? PHYS_COMBAT : PHYS_CALM)));
        // base：待机漂浮 / 水母式漂荡（拖着镰刀）
        controllers.add(new AnimationController<>(this, BASE_CONTROLLER, 8,
                s -> s.setAndContinue(drifting() ? DRIFT : HOVER)));
        // blink：先左眼再右眼
        controllers.add(new AnimationController<>(this, BLINK_CONTROLLER, 0, s -> s.setAndContinue(BLINK)));
        // action：出场、出招、后摇、抛接镰刀
        AnimationController<WeepingDeathLord> action = new AnimationController<>(this, ACTION_CONTROLLER, 4, s -> PlayState.STOP);
        for (String name : PLAY_ONCE) {
            action.triggerableAnim(name, RawAnimation.begin().thenPlay("wl_" + name));
        }
        for (String name : PLAY_AND_HOLD) {
            action.triggerableAnim(name, RawAnimation.begin().thenPlayAndHold("wl_" + name));
        }
        for (String name : LOOPS) {
            action.triggerableAnim(name, RawAnimation.begin().thenLoop("wl_" + name));
        }
        controllers.add(action);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }
}
