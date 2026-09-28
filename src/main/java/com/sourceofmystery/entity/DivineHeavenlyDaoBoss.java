package com.sourceofmystery.entity;

import com.sourceofmystery.SourceOfMystery;
import com.sourceofmystery.item.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
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
import net.minecraft.world.phys.AABB;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.ArrayList;
import java.util.List;
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

    private static final float SECOND_PHASE_HEALTH = 100.0f;
    private static final float SECOND_PHASE_HEAL_PER_SECOND = 10.0f;
    private static final float KILL_HEAL = 1000.0f;
    private static final int LIGHTNING_INTERVAL = 200; // 10 秒
    private static final double LIGHTNING_RANGE = 10.0;
    private static final double ENDERMAN_RANGE = 20.0;

    private int tickCounter = 0;
    private boolean secondPhaseTriggered = false;
    // 二阶段召唤的凋灵，Boss 死亡/消失时一并清理
    private final List<UUID> summonedWithers = new ArrayList<>();

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

        // 每秒检测一次：20 格范围内若有末影人，则让它们愤怒攻击玩家
        if (tickCounter % 20 == 0) {
            angerEndermen();
        }

        if (!secondPhaseTriggered && this.getHealth() < SECOND_PHASE_HEALTH) {
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

        // 召唤 3 只无敌凋灵
        for (int i = 0; i < 3; i++) {
            double offsetX = (random.nextDouble() - 0.5) * 10;
            double offsetZ = (random.nextDouble() - 0.5) * 10;
            WitherBoss wither = EntityType.WITHER.spawn(serverLevel,
                    BlockPos.containing(this.getX() + offsetX, this.getY(), this.getZ() + offsetZ),
                    MobSpawnType.MOB_SUMMONED);
            if (wither != null) {
                wither.setInvulnerable(true);
                summonedWithers.add(wither.getUUID());
            }
        }

        // 飞 30 格高
        this.setPos(this.getX(), this.getY() + 30, this.getZ());

        this.bossEvent.setColor(BossEvent.BossBarColor.PURPLE);

        SourceOfMystery.LOGGER.info("Boss entered Phase 2 - summoned {} Withers", summonedWithers.size());
    }

    /**
     * Boss 被击杀或被移除（如和平难度）时，清理二阶段召唤的无敌凋灵，避免它们永久残留
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
