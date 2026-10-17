package com.sourceofmystery.guardian;

import com.sourceofmystery.block.ModBlockEntities;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

import javax.annotation.Nullable;

/**
 * 守护祭坛的方块实体。
 * <p>
 * 服务端：激活后第 {@link GuardianShields#DEPLOY_TICKS} tick 展开结界，之后
 * <ul>
 *     <li>每 tick：结界里的 Boss 被弹到结界外，Boss 发出的弹射物 / 法术实体进入结界即被抹去</li>
 *     <li>每秒：结界里的敌对生物直接死亡，玩家获得生命恢复和饱和</li>
 * </ul>
 * 客户端：GeckoLib 动画（待激活循环 -> 激活 9 秒 -> 运转循环），光柱和护罩由 GuardianAltarRenderer 画。
 */
public class GuardianAltarBlockEntity extends BlockEntity implements GeoBlockEntity {

    private static final RawAnimation DORMANT = RawAnimation.begin().thenLoop("guardian_altar.dormant");
    // ACTIVATE 只 thenPlay，不链 thenLoop：activate 动画的 JSON 是 hold_on_last_frame，
    // GeckoLib 在 hold 模式下不会自动跳到下一段，会卡在最后一帧永远不动。
    // 改用手动切换：age < ACTIVATE_TICKS 时播 activate，age >= ACTIVATE_TICKS 时切到 ACTIVE。
    private static final RawAnimation ACTIVATE = RawAnimation.begin().thenPlay("guardian_altar.activate");
    private static final RawAnimation ACTIVE = RawAnimation.begin().thenLoop("guardian_altar.active");

    /** 修复动画里碎块逐个落位的时刻（tick），配合石块落位的音效 */
    private static final int[] REPAIR_TICKS = {64, 72, 80, 88, 96, 104, 112, 120, 128, 136, 144};
    private static final int BUFF_DURATION = 60;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    private boolean active;
    private long activatedAt;
    /** 客户端：激活后选定的动画（刚激活时播完整的激活动画，进入区块时已经激活很久的直接播循环），选定后不再变 */
    @Nullable
    private RawAnimation clientAnimation;

    public GuardianAltarBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.GUARDIAN_ALTAR.get(), pos, state);
    }

    public boolean isActive() {
        return active;
    }

    public long getActivatedAt() {
        return activatedAt;
    }

    /** 激活以来经过的 tick（客户端用自己的 gameTime，和服务端最多差几 tick） */
    public long age(Level level) {
        return level.getGameTime() - activatedAt;
    }

    public boolean shieldDeployed(Level level) {
        return active && age(level) >= GuardianShields.DEPLOY_TICKS;
    }

    // ------------------------------------------------------------------ 激活

    void activate(ServerLevel level) {
        active = true;
        activatedAt = level.getGameTime();
        GuardianShields.register(level, worldPosition, activatedAt);
        level.setBlock(worldPosition, getBlockState().setValue(GuardianAltarBlock.ACTIVE, true), Block.UPDATE_ALL);
        setChanged();
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        Vec3 c = Vec3.atCenterOf(worldPosition);
        level.playSound(null, worldPosition, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 1.5F, 1.2F);
        level.playSound(null, worldPosition, SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.BLOCKS, 1.5F, 0.8F);
        level.sendParticles(ParticleTypes.END_ROD, c.x, c.y + 0.4, c.z, 20, 0.2, 0.2, 0.2, 0.03);
    }

    // ------------------------------------------------------------------ 服务端 tick

    public static void serverTick(Level level, BlockPos pos, BlockState state, GuardianAltarBlockEntity altar) {
        if (!altar.active || !(level instanceof ServerLevel server)) {
            return;
        }
        long age = altar.age(level);
        if (age < 0) {
            // 时间被指令往回调过：当成刚展开
            altar.activatedAt = level.getGameTime() - GuardianShields.DEPLOY_TICKS;
            altar.setChanged();
            age = GuardianShields.DEPLOY_TICKS;
        }
        if (age == 1) {
            // 区块加载 / 重启后保证登记在册（正常激活时已经登记过，这里只是兜底）
            GuardianShields.register(server, pos, altar.activatedAt);
        }
        if (age < GuardianShields.DEPLOY_TICKS) {
            altar.activationEffects(server, (int) age);
            return;
        }
        if (age == GuardianShields.DEPLOY_TICKS) {
            altar.deploy(server);
        }
        if (level.getGameTime() % 200 == Math.floorMod(pos.asLong(), 200)) {
            GuardianShields.register(server, pos, altar.activatedAt);
        }
        AABB area = GuardianShields.area(level, pos);
        repelBosses(server, pos, area);
        if (Math.floorMod(level.getGameTime() + pos.hashCode(), 20) == 0) {
            guard(server, area);
        }
        if (level.getGameTime() % 80 == 0) {
            level.playSound(null, pos, SoundEvents.BEACON_AMBIENT, SoundSource.BLOCKS, 1.0F, 1.1F);
        }
    }

    private void activationEffects(ServerLevel level, int age) {
        for (int t : REPAIR_TICKS) {
            if (age == t) {
                level.playSound(null, worldPosition, SoundEvents.DEEPSLATE_BRICKS_PLACE, SoundSource.BLOCKS, 1.0F,
                        0.8F + level.random.nextFloat() * 0.3F);
            }
        }
        // 五圈魔法阵展开（3.0 / 3.9 / 4.8 / 5.7 / 6.6 秒）
        for (int i = 0; i < 5; i++) {
            if (age == 60 + 18 * i) {
                level.playSound(null, worldPosition, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 2.0F,
                        0.8F + 0.15F * i);
            }
        }
        if (age == 20) {
            level.playSound(null, worldPosition, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 1.5F, 1.0F);
        }
    }

    private void deploy(ServerLevel level) {
        level.playSound(null, worldPosition, SoundEvents.BEACON_POWER_SELECT, SoundSource.BLOCKS, 3.0F, 0.8F);
        level.playSound(null, worldPosition, SoundEvents.TRIDENT_THUNDER, SoundSource.BLOCKS, 1.5F, 1.4F);
        Vec3 c = Vec3.atCenterOf(worldPosition);
        level.sendParticles(ParticleTypes.FLASH, c.x, c.y + 0.4, c.z, 1, 0, 0, 0, 0);
        level.sendParticles(ParticleTypes.END_ROD, c.x, c.y + 0.4, c.z, 60, 0.3, 0.3, 0.3, 0.25);
        Component msg = Component.translatable("message.sourceofmystery.guardian_altar.deployed")
                .withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD);
        AABB area = GuardianShields.area(level, worldPosition);
        for (Player player : level.getEntitiesOfClass(Player.class, area)) {
            player.displayClientMessage(msg, true);
        }
        setChanged();
    }

    /** Boss 碰到结界就被弹出去；Boss 的弹射物 / 法术实体进入结界即被抹去 */
    private static void repelBosses(ServerLevel level, BlockPos pos, AABB area) {
        for (Entity e : level.getEntities((Entity) null, area.inflate(8), GuardianShields::isBossRelated)) {
            if (!e.isAlive()) {
                continue;
            }
            if (GuardianShields.isBoss(e)) {
                if (e.getBoundingBox().intersects(area)) {
                    pushOut(level, e, area);
                }
                // 盯着结界里的目标也够不着：放弃这个目标
                if (e instanceof Mob mob && mob.getTarget() != null && GuardianShields.isProtected(mob.getTarget())) {
                    mob.setTarget(null);
                }
            } else if (!(e instanceof LivingEntity) && area.contains(e.getBoundingBox().getCenter())) {
                Vec3 p = e.position();
                level.sendParticles(ParticleTypes.END_ROD, p.x, p.y, p.z, 12, 0.2, 0.2, 0.2, 0.08);
                level.playSound(null, p.x, p.y, p.z, SoundEvents.AMETHYST_BLOCK_HIT, SoundSource.BLOCKS, 2.0F, 1.5F);
                e.discard();
            }
        }
    }

    /** 沿穿进结界最浅的那个水平方向把 Boss 推到结界外，并给一个向外的速度 */
    private static void pushOut(ServerLevel level, Entity boss, AABB area) {
        AABB bb = boss.getBoundingBox();
        double west = bb.maxX - area.minX;   // 往 -x 推出去需要的距离
        double east = area.maxX - bb.minX;
        double north = bb.maxZ - area.minZ;
        double south = area.maxZ - bb.minZ;
        double min = Math.min(Math.min(west, east), Math.min(north, south));
        double margin = 1.0;
        double dx = 0, dz = 0, vx = 0, vz = 0;
        if (min == west) {
            dx = -(west + margin);
            vx = -1;
        } else if (min == east) {
            dx = east + margin;
            vx = 1;
        } else if (min == north) {
            dz = -(north + margin);
            vz = -1;
        } else {
            dz = south + margin;
            vz = 1;
        }
        boss.teleportTo(boss.getX() + dx, boss.getY(), boss.getZ() + dz);
        boss.setDeltaMovement(vx * 1.2, 0.3, vz * 1.2);
        boss.hurtMarked = true;
        // 撞在结界上的位置：一圈白光
        double hx = vx < 0 ? area.minX : vx > 0 ? area.maxX : boss.getX();
        double hz = vz < 0 ? area.minZ : vz > 0 ? area.maxZ : boss.getZ();
        double hy = boss.getY() + boss.getBbHeight() * 0.5;
        level.sendParticles(ParticleTypes.END_ROD, hx, hy, hz, 30, Math.abs(vz) * 1.5, boss.getBbHeight() * 0.3,
                Math.abs(vx) * 1.5, 0.05);
        level.playSound(null, hx, hy, hz, SoundEvents.SHIELD_BLOCK, SoundSource.HOSTILE, 3.0F, 0.6F);
    }

    /** 每秒一次：敌对生物直接死亡，玩家获得生命恢复和饱和 */
    private static void guard(ServerLevel level, AABB area) {
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, area, LivingEntity::isAlive)) {
            if (e instanceof Player player) {
                if (!player.isSpectator()) {
                    player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, BUFF_DURATION, 1, true, false, true));
                    player.addEffect(new MobEffectInstance(MobEffects.SATURATION, BUFF_DURATION, 0, true, false, true));
                }
            } else if (e instanceof Enemy && !GuardianShields.isBossRelated(e)) {
                Vec3 p = e.position();
                level.sendParticles(ParticleTypes.END_ROD, p.x, p.y + e.getBbHeight() * 0.5, p.z, 10,
                        e.getBbWidth() * 0.4, e.getBbHeight() * 0.4, e.getBbWidth() * 0.4, 0.04);
                e.kill();
            }
        }
    }

    // ------------------------------------------------------------------ 存档与同步

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putBoolean("Active", active);
        tag.putLong("ActivatedAt", activatedAt);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        active = tag.getBoolean("Active");
        activatedAt = tag.getLong("ActivatedAt");
    }

    @Override
    public CompoundTag getUpdateTag() {
        return saveWithoutMetadata();
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public AABB getRenderBoundingBox() {
        // 激活后有直通天空的光柱和 3x3 区块的护罩；待激活时只有祭坛本身和漂浮的碎块
        return active ? INFINITE_EXTENT_AABB : new AABB(worldPosition).inflate(1.0, 1.5, 1.0);
    }

    // ------------------------------------------------------------------ GeckoLib

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 5, this::animate));
    }

    private PlayState animate(AnimationState<GuardianAltarBlockEntity> state) {
        if (!active || level == null) {
            clientAnimation = null;
            return state.setAndContinue(DORMANT);
        }
        // 手动切换：age 跨过 ACTIVATE_TICKS 时从 ACTIVATE 切到 ACTIVE；
        // 这样不依赖 GeckoLib 的 thenPlay→thenLoop 自动跳转（activate 动画的 loop 是 hold_on_last_frame，
        // 在 hold 模式下不会触发跳转，会卡在最后一帧）。
        RawAnimation target = age(level) < GuardianShields.ACTIVATE_TICKS ? ACTIVATE : ACTIVE;
        if (clientAnimation != target) {
            clientAnimation = target;
        }
        return state.setAndContinue(target);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
