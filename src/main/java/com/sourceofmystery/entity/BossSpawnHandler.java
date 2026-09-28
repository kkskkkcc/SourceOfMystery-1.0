package com.sourceofmystery.entity;

import com.sourceofmystery.SourceOfMystery;
import com.sourceofmystery.advancement.AdvancementHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 神威天道召唤流程：末影龙死亡 -> 20 秒倒计时 -> 10 道闪电 -> Boss 降临。
 * 流程状态保存在末地维度的 SavedData 中，服务器重启不会丢失进度。
 */
@Mod.EventBusSubscriber(modid = SourceOfMystery.MOD_ID)
public class BossSpawnHandler {

    private static final int COUNTDOWN_TICKS = 400; // 20 秒
    private static final int LIGHTNING_COUNT = 10; // 闪电数量
    private static final int LIGHTNING_INTERVAL_TICKS = 5; // 每 5 tick 落一道闪电
    private static final BlockPos SPAWN_POS = new BlockPos(0, 80, 0);
    private static final double ADVANCEMENT_RANGE = 100.0;

    private static final int STAGE_IDLE = 0;
    private static final int STAGE_COUNTDOWN = 1;
    private static final int STAGE_LIGHTNING = 2;

    @SubscribeEvent
    public static void onEnderDragonDeath(LivingDeathEvent event) {
        if (event.getEntity().getType() != EntityType.ENDER_DRAGON
                || !(event.getEntity().level() instanceof ServerLevel dragonLevel)) {
            return;
        }
        ServerLevel end = dragonLevel.getServer().getLevel(Level.END);
        if (end == null) {
            return;
        }
        SummonState state = SummonState.get(end);
        if (state.stage == STAGE_IDLE) {
            state.setStage(STAGE_COUNTDOWN);
            SourceOfMystery.LOGGER.info("Ender Dragon died. Boss summoning countdown started.");
            broadcastToEnd(end, Component.translatable("message.sourceofmystery.boss.awakening")
                    .withStyle(ChatFormatting.LIGHT_PURPLE));
        }
    }

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END
                || !(event.level instanceof ServerLevel level)
                || level.dimension() != Level.END) {
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
            broadcastToEnd(level, Component.translatable("message.sourceofmystery.boss.countdown",
                    Component.literal(String.valueOf(remainingSeconds)).withStyle(ChatFormatting.RED))
                    .withStyle(ChatFormatting.YELLOW));
        }

        // 倒计时结束，进入闪电阶段
        if (state.stageTick >= COUNTDOWN_TICKS) {
            state.setStage(STAGE_LIGHTNING);
            broadcastToEnd(level, Component.translatable("message.sourceofmystery.boss.lightning")
                    .withStyle(ChatFormatting.RED));
        }
    }

    /**
     * 闪电阶段：在 Boss 即将生成的位置落 10 道闪电，结束后 Boss 出现
     */
    private static void tickLightning(ServerLevel level, SummonState state) {
        if (state.stageTick % LIGHTNING_INTERVAL_TICKS == 0
                && state.stageTick / LIGHTNING_INTERVAL_TICKS <= LIGHTNING_COUNT) {
            spawnLightning(level, SPAWN_POS);
        }

        // 闪电阶段结束（10 道闪电 + 少量缓冲），生成 Boss
        if (state.stageTick >= LIGHTNING_COUNT * LIGHTNING_INTERVAL_TICKS + 10) {
            spawnBoss(level);
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
     * 广播消息给末地维度的所有玩家（聊天框）
     */
    private static void broadcastToEnd(ServerLevel end, Component message) {
        // 前缀样式不能挂在父节点上，否则会被正文继承（正文也变成粗体）
        Component text = Component.empty()
                .append(Component.translatable("message.sourceofmystery.boss.prefix")
                        .withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.BOLD))
                .append(" ")
                .append(message);
        for (ServerPlayer player : end.players()) {
            player.sendSystemMessage(text);
        }
    }

    private static void spawnBoss(ServerLevel level) {
        DivineHeavenlyDaoBoss boss = ModEntities.DIVINE_HEAVENLY_DAO_BOSS.get().spawn(level, SPAWN_POS, MobSpawnType.EVENT);
        if (boss == null) {
            SourceOfMystery.LOGGER.warn("Failed to spawn Divine Heavenly Dao Boss");
            return;
        }

        SourceOfMystery.LOGGER.info("Divine Heavenly Dao Boss spawned at End dimension");
        broadcastToEnd(level, Component.translatable("message.sourceofmystery.boss.arrived")
                .withStyle(ChatFormatting.RED, ChatFormatting.BOLD));

        // 授予"触怒天道"成就：Boss 出现在玩家周围 100 格范围内
        double rangeSq = ADVANCEMENT_RANGE * ADVANCEMENT_RANGE;
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(SPAWN_POS.getCenter()) <= rangeSq) {
                AdvancementHelper.grantAdvancement(player, "provoke_heaven");
            }
        }
    }

    /**
     * 召唤流程的持久化状态（挂在末地维度上）
     */
    private static class SummonState extends SavedData {
        private static final String DATA_NAME = SourceOfMystery.MOD_ID + "_boss_summon";

        private int stage = STAGE_IDLE;
        private int stageTick = 0;

        static SummonState get(ServerLevel level) {
            return level.getDataStorage().computeIfAbsent(SummonState::load, SummonState::new, DATA_NAME);
        }

        static SummonState load(CompoundTag tag) {
            SummonState state = new SummonState();
            state.stage = tag.getInt("Stage");
            state.stageTick = tag.getInt("StageTick");
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
            return tag;
        }
    }
}
