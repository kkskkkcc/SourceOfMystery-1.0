package com.sourceofmystery.entity;

import com.sourceofmystery.SourceOfMystery;
import com.sourceofmystery.advancement.AdvancementHelper;
import com.sourceofmystery.particle.ModParticles;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.living.LivingExperienceDropEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 神威天道召唤流程：手持龙魂右键神秘祭坛 -> 20 秒倒计时 -> 10 道闪电 -> Boss 在祭坛上方降临。
 * 流程状态保存在祭坛所在维度的 SavedData 中，服务器重启不会丢失进度；
 * 同一维度同时只能进行一场召唤。
 */
@Mod.EventBusSubscriber(modid = SourceOfMystery.MOD_ID)
public class BossSpawnHandler {

    private static final int COUNTDOWN_TICKS = 400; // 20 秒
    private static final int LIGHTNING_COUNT = 10; // 闪电数量
    private static final int LIGHTNING_INTERVAL_TICKS = 5; // 每 5 tick 落一道闪电
    private static final int SPAWN_HEIGHT_ABOVE_ALTAR = 2;
    // 旧版本存档里进行到一半的召唤没有记录位置，沿用原来的末地主岛中央
    private static final BlockPos LEGACY_SPAWN_POS = new BlockPos(0, 80, 0);
    private static final double ADVANCEMENT_RANGE = 100.0;

    private static final int STAGE_IDLE = 0;
    private static final int STAGE_COUNTDOWN = 1;
    private static final int STAGE_LIGHTNING = 2;

    /**
     * 在祭坛处开始召唤。该维度已经有一场召唤在进行时返回 false（调用方不应消耗龙魂）。
     */
    public static boolean startSummon(ServerLevel level, BlockPos altarPos) {
        SummonState state = SummonState.get(level);
        if (state.stage != STAGE_IDLE) {
            return false;
        }
        state.spawnPos = altarPos.above(SPAWN_HEIGHT_ABOVE_ALTAR);
        state.setStage(STAGE_COUNTDOWN);
        SourceOfMystery.LOGGER.info("Boss summoning started at altar {} in {}", altarPos, level.dimension().location());
        broadcast(level, Component.translatable("message.sourceofmystery.boss.awakening")
                .withStyle(ChatFormatting.LIGHT_PURPLE));
        return true;
    }

    /**
     * 二阶段召唤的凋灵不掉落物品和经验，避免每场 Boss 战额外产出下界之星
     */
    @SubscribeEvent
    public static void onMinionDrops(LivingDropsEvent event) {
        if (event.getEntity().getTags().contains(DivineHeavenlyDaoBoss.MINION_TAG)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onMinionExperience(LivingExperienceDropEvent event) {
        if (event.getEntity().getTags().contains(DivineHeavenlyDaoBoss.MINION_TAG)) {
            event.setDroppedExperience(0);
        }
    }

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level)) {
            return;
        }

        SummonState state = SummonState.get(level);
        if (state.stage == STAGE_IDLE) {
            return;
        }

        state.stageTick++;
        state.setDirty();

        if (state.stage == STAGE_COUNTDOWN) {
            tickCountdown(level, state);
        } else if (state.stage == STAGE_LIGHTNING) {
            tickLightning(level, state);
        } else {
            // 未知状态（数据损坏），直接复位
            state.setStage(STAGE_IDLE);
        }
    }

    /**
     * 倒计时阶段：聊天框吟唱 + 倒计时
     */
    private static void tickCountdown(ServerLevel level, SummonState state) {
        int remainingSeconds = (COUNTDOWN_TICKS - state.stageTick) / 20;

        // 每 20 tick（1秒）在聊天框提示一次
        if (state.stageTick % 20 == 0 && remainingSeconds > 0) {
            broadcast(level, Component.translatable("message.sourceofmystery.boss.countdown",
                    Component.literal(String.valueOf(remainingSeconds)).withStyle(ChatFormatting.RED))
                    .withStyle(ChatFormatting.YELLOW));
        }

        spawnAltarMotes(level, state.spawnPos.below(SPAWN_HEIGHT_ABOVE_ALTAR), state.stageTick / (double) COUNTDOWN_TICKS);

        // 倒计时结束：神威天道降临（天上的金色法阵、雷霆、从天而降的演出由 Boss 自己完成）
        if (state.stageTick >= COUNTDOWN_TICKS) {
            broadcast(level, Component.translatable("message.sourceofmystery.boss.lightning")
                    .withStyle(ChatFormatting.RED));
            spawnBoss(level, state.spawnPos);
            state.setStage(STAGE_IDLE);
        }
    }

    /**
     * 倒计时期间，金色光点从四面八方不断汇入祭坛，越接近结束越密集
     */
    private static void spawnAltarMotes(ServerLevel level, BlockPos altarPos, double progress) {
        double cx = altarPos.getX() + 0.5;
        double cy = altarPos.getY() + 1.1;
        double cz = altarPos.getZ() + 0.5;
        int count = 3 + (int) (progress * 9);
        for (int i = 0; i < count; i++) {
            double yaw = level.random.nextDouble() * Math.PI * 2;
            double pitch = (level.random.nextDouble() - 0.3) * Math.PI * 0.6;
            double r = 4 + level.random.nextDouble() * 9;
            double x = cx + Math.cos(yaw) * Math.cos(pitch) * r;
            double y = cy + Math.sin(pitch) * r;
            double z = cz + Math.sin(yaw) * Math.cos(pitch) * r;
            // count = 0：最后三个参数是粒子的位移，金色光点会沿着它滑进祭坛
            level.sendParticles(ModParticles.GOLD_MOTE.get(), x, y, z, 0, cx - x, cy - y, cz - z, 1.0);
        }
        if (level.getGameTime() % 4 == 0) {
            level.sendParticles(ParticleTypes.END_ROD, cx, cy, cz, 2, 0.15, 0.3, 0.15, 0.02);
        }
    }

    /**
     * 旧版本存档里停在"闪电阶段"的召唤：落 10 道闪电后 Boss 出现
     */
    private static void tickLightning(ServerLevel level, SummonState state) {
        if (state.stageTick % LIGHTNING_INTERVAL_TICKS == 0
                && state.stageTick / LIGHTNING_INTERVAL_TICKS <= LIGHTNING_COUNT) {
            spawnLightning(level, state.spawnPos);
        }

        // 闪电阶段结束（10 道闪电 + 少量缓冲），生成 Boss
        if (state.stageTick >= LIGHTNING_COUNT * LIGHTNING_INTERVAL_TICKS + 10) {
            spawnBoss(level, state.spawnPos);
            state.setStage(STAGE_IDLE);
        }
    }

    /**
     * 在指定位置附近生成一道纯视觉闪电（模拟天雷轰击区域）
     */
    private static void spawnLightning(ServerLevel level, BlockPos center) {
        double x = center.getX() + 0.5 + (level.random.nextDouble() - 0.5) * 6;
        double z = center.getZ() + 0.5 + (level.random.nextDouble() - 0.5) * 6;

        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
        if (bolt != null) {
            bolt.moveTo(x, center.getY(), z);
            bolt.setVisualOnly(true); // 纯视觉效果，不额外伤害/着火
            level.addFreshEntity(bolt);
        }
    }

    /**
     * 广播消息给召唤所在维度的所有玩家（聊天框）
     */
    private static void broadcast(ServerLevel level, Component message) {
        // 前缀样式不能挂在父节点上，否则会被正文继承（正文也变成粗体）
        Component text = Component.empty()
                .append(Component.translatable("message.sourceofmystery.boss.prefix")
                        .withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.BOLD))
                .append(" ")
                .append(message);
        for (ServerPlayer player : level.players()) {
            player.sendSystemMessage(text);
        }
    }

    private static void spawnBoss(ServerLevel level, BlockPos spawnPos) {
        DivineHeavenlyDaoBoss boss = ModEntities.DIVINE_HEAVENLY_DAO_BOSS.get().create(level);
        if (boss == null) {
            SourceOfMystery.LOGGER.warn("Failed to spawn Divine Heavenly Dao Boss");
            return;
        }
        Vec3 ground = Vec3.atBottomCenterOf(spawnPos);
        boss.moveTo(ground.x, ground.y, ground.z, 0, 0);
        boss.setInvisible(true); // 降临前隐身，先展开法阵
        boss.finalizeSpawn(level, level.getCurrentDifficultyAt(spawnPos), MobSpawnType.EVENT, null, null);
        level.addFreshEntity(boss);
        boss.beginIntro(level, ground);

        SourceOfMystery.LOGGER.info("Divine Heavenly Dao Boss spawned at {} in {}", spawnPos, level.dimension().location());
        broadcast(level, Component.translatable("message.sourceofmystery.boss.arrived")
                .withStyle(ChatFormatting.RED, ChatFormatting.BOLD));

        // 授予"触怒天道"成就：Boss 出现在玩家周围 100 格范围内
        double rangeSq = ADVANCEMENT_RANGE * ADVANCEMENT_RANGE;
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(spawnPos.getCenter()) <= rangeSq) {
                AdvancementHelper.grantAdvancement(player, "provoke_heaven");
            }
        }
    }

    /**
     * 召唤流程的持久化状态（挂在祭坛所在的维度上）
     */
    private static class SummonState extends SavedData {
        private static final String DATA_NAME = SourceOfMystery.MOD_ID + "_boss_summon";

        private int stage = STAGE_IDLE;
        private int stageTick = 0;
        private BlockPos spawnPos = LEGACY_SPAWN_POS;

        static SummonState get(ServerLevel level) {
            return level.getDataStorage().computeIfAbsent(SummonState::load, SummonState::new, DATA_NAME);
        }

        static SummonState load(CompoundTag tag) {
            SummonState state = new SummonState();
            state.stage = tag.getInt("Stage");
            state.stageTick = tag.getInt("StageTick");
            if (tag.contains("SpawnX")) {
                state.spawnPos = new BlockPos(tag.getInt("SpawnX"), tag.getInt("SpawnY"), tag.getInt("SpawnZ"));
            }
            return state;
        }

        void setStage(int stage) {
            this.stage = stage;
            this.stageTick = 0;
            setDirty();
        }

        @Override
        public CompoundTag save(CompoundTag tag) {
            tag.putInt("Stage", stage);
            tag.putInt("StageTick", stageTick);
            tag.putInt("SpawnX", spawnPos.getX());
            tag.putInt("SpawnY", spawnPos.getY());
            tag.putInt("SpawnZ", spawnPos.getZ());
            return tag;
        }
    }
}
