package com.sourceofmystery.entity;

import com.sourceofmystery.SourceOfMystery;
import com.sourceofmystery.item.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.List;

public class DivineHeavenlyDaoBoss extends Monster implements GeoEntity {

    private static final String ANIM_PREFIX = "animation.divine_heavenly_dao_boss.";
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop(ANIM_PREFIX + "idle");
    private static final RawAnimation ATTACK = RawAnimation.begin().thenPlay(ANIM_PREFIX + "attack");
    private static final RawAnimation THUNDER = RawAnimation.begin().thenPlay(ANIM_PREFIX + "thunder_strike");
    private static final String BASE_CONTROLLER = "base";
    private static final String ACTION_CONTROLLER = "action";
    private static final String TRIGGER_ATTACK = "attack";
    private static final String TRIGGER_THUNDER = "thunder_strike";

    private int stage = 1;
    private int tickCounter = 0;
    private boolean secondPhaseTriggered = false;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    // Boss Bar - 使用 Forge 标准 ServerBossEvent
    private final ServerBossEvent bossEvent;

    public DivineHeavenlyDaoBoss(EntityType<? extends DivineHeavenlyDaoBoss> type, Level level) {
        super(type, level);
        this.xpReward = 10000; // 经验值
        this.setCustomName(Component.literal("§c§l神威天道"));
        this.setCustomNameVisible(true);

        // 飞行移动控制：像凋零一样浮空飞行，而不是在地上跑
        this.moveControl = new FlyingMoveControl(this, 10, true);
        this.navigation = new net.minecraft.world.entity.ai.navigation.FlyingPathNavigation(this, level);
        this.setNoGravity(true);

        // 创建 ServerBossEvent - 参考原版 Wither 实现
        this.bossEvent = new ServerBossEvent(
                Component.literal("§c§l神威天道").withStyle(ChatFormatting.RED),
                BossEvent.BossBarColor.RED,
                BossEvent.BossBarOverlay.PROGRESS
        );
        this.bossEvent.setProgress(this.getHealth() / this.getMaxHealth());
    }

    public static AttributeSupplier.Builder createAttributes() {
        // 使用 Monster.createMonsterAttributes() 作为基础，包含 attack_knockback 等 Monster 必需属性
        // 额外添加 FLYING_SPEED，因为 FlyingMoveControl 需要该属性（否则报 Can't find attribute flying_speed）
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
        tickCounter++;

        // 更新 Boss Bar 进度 + 免疫凋零效果（避免被二阶段召唤的凋零上凋零效果）
        if (!this.level().isClientSide) {
            this.bossEvent.setProgress(this.getHealth() / this.getMaxHealth());
            this.removeEffect(MobEffects.WITHER);
        }

        // 每 10 秒（200 tick）自动召唤一次闪电攻击周围 10 格内的所有实体（除自己外）
        if (!this.level().isClientSide && tickCounter % 200 == 0) {
            lightningAttack();
        }

        // 每秒检测一次：20 格圆形范围内若有末影人，则让它们愤怒攻击玩家
        if (!this.level().isClientSide && tickCounter % 20 == 0) {
            angerEndermen();
        }

        // 检查是否进入第二阶段
        if (!secondPhaseTriggered && this.getHealth() < 100.0) {
            secondPhaseTriggered = true;
            stage = 2;
            enterSecondPhase();
        }

        // 第二阶段：回血
        if (secondPhaseTriggered && tickCounter % 20 == 0) {
            this.heal(10.0f);
        }
    }

    /**
     * 末影人协同：若 Boss 20 格圆形范围内存在末影人，则让范围内所有末影人愤怒攻击玩家
     */
    private void angerEndermen() {
        ServerLevel serverLevel = (ServerLevel) this.level();

        final double RANGE = 20.0;
        final double RANGE_SQ = RANGE * RANGE;
        AABB area = new AABB(this.blockPosition()).inflate(RANGE);
        List<EnderMan> endermen = serverLevel.getEntitiesOfClass(EnderMan.class, area);

        boolean hasEnderman = false;
        for (EnderMan enderman : endermen) {
            if (!enderman.isAlive()) continue;
            double distSq = enderman.distanceToSqr(this);
            if (distSq <= RANGE_SQ) {
                hasEnderman = true;
                break;
            }
        }

        // 范围内没有末影人，不处理
        if (!hasEnderman) return;

        // 找最近的目标玩家（优先 Boss 当前锁定的玩家）
        Player targetPlayer = null;
        if (this.getTarget() instanceof Player player) {
            targetPlayer = player;
        } else {
            targetPlayer = serverLevel.getNearestPlayer(this.getX(), this.getY(), this.getZ(), RANGE * 2, null);
        }

        if (targetPlayer == null || !targetPlayer.isAlive()) return;

        // 让范围内所有末影人愤怒攻击该玩家
        for (EnderMan enderman : endermen) {
            if (!enderman.isAlive()) continue;
            double distSq = enderman.distanceToSqr(this);
            if (distSq <= RANGE_SQ) {
                enderman.setTarget(targetPlayer);
            }
        }
    }

    /**
     * 闪电攻击：对周围圆形 10 格范围内的所有实体（除自己外）施加原版闪电伤害
     */
    private void lightningAttack() {
        ServerLevel serverLevel = (ServerLevel) this.level();

        // 触发闪电施法动画（GeckoLib 会同步到客户端）
        this.triggerAnim(ACTION_CONTROLLER, TRIGGER_THUNDER);

        // 扫描周围 10 格（使用 AABB 扩大范围，再用距离平方精确判断圆形范围）
        final double RANGE = 10.0;
        final double RANGE_SQ = RANGE * RANGE;
        AABB area = new AABB(this.blockPosition()).inflate(RANGE);
        List<LivingEntity> entities = serverLevel.getEntitiesOfClass(LivingEntity.class, area);

        int hitCount = 0;
        for (LivingEntity target : entities) {
            if (target == this) continue; // 排除自己
            if (!target.isAlive()) continue;

            // 圆形距离判断
            double distSq = target.distanceToSqr(this);
            if (distSq > RANGE_SQ) continue;

            // 跳过创造/旁观玩家
            if (target instanceof Player player && (player.isCreative() || player.isSpectator())) continue;

            // 在每个目标位置生成真实闪电（自带原版闪电伤害 + 着火），确保周围实体被命中
            LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(serverLevel);
            if (bolt != null) {
                bolt.moveTo(target.getX(), target.getY(), target.getZ());
                serverLevel.addFreshEntity(bolt);
            }
            hitCount++;
        }

        SourceOfMystery.LOGGER.info("Divine Heavenly Dao Boss cast lightning strike, hit {} entities", hitCount);
    }

    /**
     * 玩家开始看到 Boss 时调用 - 添加玩家到 Boss Bar
     */
    @Override
    public void startSeenByPlayer(ServerPlayer player) {
        super.startSeenByPlayer(player);
        this.bossEvent.addPlayer(player);
    }

    /**
     * 玩家停止看到 Boss 时调用 - 从 Boss Bar 移除玩家
     */
    @Override
    public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        this.bossEvent.removePlayer(player);
    }

    private void enterSecondPhase() {
        if (this.level().isClientSide) return;

        ServerLevel serverLevel = (ServerLevel) this.level();

        // 召唤3只凋零
        for (int i = 0; i < 3; i++) {
            double offsetX = (random.nextDouble() - 0.5) * 10;
            double offsetZ = (random.nextDouble() - 0.5) * 10;
            Entity wither = EntityType.WITHER.spawn(serverLevel,
                    BlockPos.containing(this.getX() + offsetX, this.getY(), this.getZ() + offsetZ),
                    net.minecraft.world.entity.MobSpawnType.COMMAND);
            if (wither != null) {
                wither.setInvulnerable(true);
            }
        }

        // 飞30格高
        this.setPos(this.getX(), this.getY() + 30, this.getZ());

        // 第二阶段 Boss Bar 变为紫色
        this.bossEvent.setColor(BossEvent.BossBarColor.PURPLE);

        SourceOfMystery.LOGGER.info("Boss entered Phase 2 - summoned 3 Withers and flew up 30 blocks");
    }

    @Override
    public boolean canChangeDimensions() {
        return false;
    }

    /**
     * 让凋零等敌对生物不主动把 Boss 当作攻击目标（避免二阶段召唤的凋零攻击 Boss）
     */
    @Override
    public boolean canBeSeenAsEnemy() {
        return false;
    }

    /**
     * 远程免疫 - 完全免疫投射物
     */
    @Override
    public boolean isInvulnerableTo(DamageSource damageSource) {
        // 检查是否是投射物伤害
        Entity source = damageSource.getDirectEntity();
        if (source instanceof Projectile) {
            return true;
        }

        // 免疫凋零相关伤害（凋零骷髅头、凋零效果、凋零本体攻击），避免二阶段召唤的凋零伤害 Boss
        if (damageSource.is(DamageTypes.WITHER) || damageSource.is(DamageTypes.WITHER_SKULL)) {
            return true;
        }
        if (damageSource.getEntity() instanceof WitherBoss) {
            return true;
        }

        // 检查伤害来源是否是箭、三叉戟等
        String sourceName = damageSource.getMsgId();
        if (sourceName != null) {
            if (sourceName.contains("arrow") || sourceName.contains("trident") ||
                sourceName.contains("fireball") || sourceName.contains("projectile")) {
                return true;
            }
        }

        return super.isInvulnerableTo(damageSource);
    }

    /**
     * 击杀回复 - 仅当 Boss 本次攻击真正击杀目标时回复 1000 HP，并播放攻击动画
     */
    @Override
    public boolean doHurtTarget(Entity target) {
        // 触发近战攻击动画
        this.triggerAnim(ACTION_CONTROLLER, TRIGGER_ATTACK);

        boolean result = super.doHurtTarget(target);

        // 仅当目标被本次攻击击杀时才回复（避免每次命中都回血，导致回血数值异常）
        if (result && !this.level().isClientSide && target instanceof LivingEntity living && !living.isAlive()) {
            this.heal(1000.0f);
            SourceOfMystery.LOGGER.info("Boss killed a target and healed 1000 HP, current health: {}", this.getHealth());
        }

        return result;
    }

    /**
     * 死亡处理 - 生成掉落物
     */
    @Override
    public void die(DamageSource damageSource) {
        // 先移除 Boss Bar
        this.bossEvent.removeAllPlayers();

        super.die(damageSource);

        if (!this.level().isClientSide) {
            // 生成掉落物
            ServerLevel serverLevel = (ServerLevel) this.level();
            spawnDeathDrops(serverLevel);

            SourceOfMystery.LOGGER.info("Divine Heavenly Dao Boss has been slain!");
        }
    }

    /**
     * 生成死亡掉落物
     */
    private void spawnDeathDrops(ServerLevel level) {
        // 天道碎片 4-8
        int fragmentCount = 4 + this.random.nextInt(5); // 4-8
        for (int i = 0; i < fragmentCount; i++) {
            double offsetX = (random.nextDouble() - 0.5) * 3;
            double offsetZ = (random.nextDouble() - 0.5) * 3;
            net.minecraft.world.entity.item.ItemEntity fragment = new net.minecraft.world.entity.item.ItemEntity(
                    level, this.getX() + offsetX, this.getY() + 2, this.getZ() + offsetZ,
                    new net.minecraft.world.item.ItemStack(ModItems.HEAVENLY_DAO_FRAGMENT.get()));
            level.addFreshEntity(fragment);
        }

        // 附魔金苹果 16
        for (int i = 0; i < 16; i++) {
            double offsetX = (random.nextDouble() - 0.5) * 2;
            double offsetZ = (random.nextDouble() - 0.5) * 2;
            net.minecraft.world.entity.item.ItemEntity apple = new net.minecraft.world.entity.item.ItemEntity(
                    level, this.getX() + offsetX, this.getY() + 2, this.getZ() + offsetZ,
                    new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.ENCHANTED_GOLDEN_APPLE));
            level.addFreshEntity(apple);
        }

        // 龙蛋 1
        net.minecraft.world.entity.item.ItemEntity dragonEgg = new net.minecraft.world.entity.item.ItemEntity(
                level, this.getX(), this.getY() + 2, this.getZ(),
                new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DRAGON_EGG));
        level.addFreshEntity(dragonEgg);

        // 下界之星 2
        for (int i = 0; i < 2; i++) {
            double offsetX = (random.nextDouble() - 0.5) * 2;
            double offsetZ = (random.nextDouble() - 0.5) * 2;
            net.minecraft.world.entity.item.ItemEntity star = new net.minecraft.world.entity.item.ItemEntity(
                    level, this.getX() + offsetX, this.getY() + 2, this.getZ() + offsetZ,
                    new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.NETHER_STAR));
            level.addFreshEntity(star);
        }
    }

    @Override
    public boolean isPersistenceRequired() {
        return true; // Boss 不会被移除
    }

    @Override
    public boolean isInvulnerable() {
        // 远程免疫通过 isInvulnerableTo 处理
        return super.isInvulnerable();
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
