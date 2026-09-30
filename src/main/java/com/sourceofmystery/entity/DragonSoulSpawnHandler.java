package com.sourceofmystery.entity;

import com.sourceofmystery.SourceOfMystery;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 龙魂 Boss 召唤流程：
 * 末影龙死亡 -> 等原版死亡动画结束 -> 3 秒末影龙吟 -> 死亡点 20 格内闪电群循环 5 秒
 * -> 紫色卫星环绕（10 颗） -> 龙魂 Boss 降临。
 * 流程状态保存在末影龙所在维度的 SavedData 中，服务器重启不丢进度。
 */
@Mod.EventBusSubscriber(modid = SourceOfMystery.MOD_ID)
public class DragonSoulSpawnHandler {

    // 阶段
    private static final int STAGE_IDLE = 0;
    private static final int STAGE_WAIT_DEATH = 1;   // 等末影龙死亡动画（10 秒）
    private static final int STAGE_ROAR = 2;         // 末影龙吟（3 秒）
    private static final int STAGE_LIGHTNING = 3;    // 闪电群（5 秒）
    private static final int STAGE_SATELLITES = 4;   // 卫星环绕（2 秒）

    // 各阶段时长（tick）
    private static final int DEATH_ANIM_TICKS = 200; // 10 秒
    private static final int ROAR_TICKS = 60;        // 3 秒
    private static final int LIGHTNING_TICKS = 100;  // 5 秒
    private static final int SATELLITE_TICKS = 40;   // 2 秒

    private static final int LIGHTNING_COUNT = 5;    // 每次落 5 道闪电
    private static final int SATELLITE_COUNT = 10;   // 10 颗卫星
    private static final double LIGHTNING_RANGE = 20.0; // 闪电 20 格范围
    private static final double SATELLITE_RADIUS = 3.0; // 卫星环绕半径

    /**
     * 末影龙死亡时启动召唤流程（等死亡动画结束后再走特效阶段）
     */
    @SubscribeEvent
    public static void onDragonDeath(LivingDeathEvent event) {
        if (event.getEntity().getType() != EntityType.ENDER_DRAGON) {
            return;
        }
        if (!(event.getEntity().level() instanceof ServerLevel level)) {
            return;
        }
        SpawnState state = SpawnState.get(level);
        if (state.stage != STAGE_IDLE) {
            return; // 该维度已有一场召唤在进行
        }
        // 记录死亡位置（用地面高度兜底，避免生成在半空）
        BlockPos deathPos = event.getEntity().blockPosition();
        int groundY = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, deathPos).getY();
        state.spawnPos = new BlockPos(deathPos.getX(), groundY, deathPos.getZ());
        state.setStage(STAGE_WAIT_DEATH);
        SourceOfMystery.LOGGER.info("Dragon Soul summoning started at {} in {}", state.spawnPos, level.dimension().location());
    }

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level)) {
            return;
        }
        SpawnState state = SpawnState.get(level);
        if (state.stage == STAGE_IDLE) {
            return;
        }
        state.stageTick++;
        state.setDirty();

        switch (state.stage) {
            case STAGE_WAIT_DEATH -> {
                if (state.stageTick >= DEATH_ANIM_TICKS) {
                    state.setStage(STAGE_ROAR);
                }
            }
            case STAGE_ROAR -> {
                tickRoar(level, state);
                if (state.stageTick >= ROAR_TICKS) {
                    state.setStage(STAGE_LIGHTNING);
                }
            }
            case STAGE_LIGHTNING -> {
                tickLightning(level, state);
                if (state.stageTick >= LIGHTNING_TICKS) {
                    state.setStage(STAGE_SATELLITES);
                }
            }
            case STAGE_SATELLITES -> {
                tickSatellites(level, state);
                if (state.stageTick >= SATELLITE_TICKS) {
                    spawnBoss(level, state.spawnPos);
                    state.setStage(STAGE_IDLE);
                }
            }
            default -> state.setStage(STAGE_IDLE);
        }
    }

    /**
     * 龙吟阶段：每秒播放一次末影龙吟，向附近玩家播报
     */
    private static void tickRoar(ServerLevel level, SpawnState state) {
        if (state.stageTick % 20 == 1) {
            level.playSound(null, state.spawnPos, SoundEvents.ENDER_DRAGON_GROWL,
                    SoundSource.HOSTILE, 3.0f, 0.8f);
            broadcast(level, Component.translatable("message.sourceofmystery.dragon_soul.roar")
                    .withStyle(ChatFormatting.LIGHT_PURPLE));
        }
    }

    /**
     * 闪电阶段：死亡点 20 格范围内随机落闪电（每 20 tick 一轮 5 道，循环 5 秒）
     */
    private static void tickLightning(ServerLevel level, SpawnState state) {
        if (state.stageTick % 20 == 0) {
            for (int i = 0; i < LIGHTNING_COUNT; i++) {
                spawnLightning(level, state.spawnPos);
            }
        }
    }

    /**
     * 卫星阶段：10 颗紫色卫星环绕死亡点，粒子较粗
     */
    private static void tickSatellites(ServerLevel level, SpawnState state) {
        double time = level.getGameTime() * 0.15;
        for (int i = 0; i < SATELLITE_COUNT; i++) {
            double angle = time + i * (2 * Math.PI / SATELLITE_COUNT);
            double x = state.spawnPos.getX() + 0.5 + Math.cos(angle) * SATELLITE_RADIUS;
            double z = state.spawnPos.getZ() + 0.5 + Math.sin(angle) * SATELLITE_RADIUS;
            double y = state.spawnPos.getY() + 1.5;
            // 每颗卫星叠加多个粒子，形成"粗"的卫星点
            for (int j = 0; j < 6; j++) {
                level.sendParticles(ParticleTypes.DRAGON_BREATH, x, y, z, 1, 0.35, 0.35, 0.35, 0.03);
            }
            level.sendParticles(ParticleTypes.END_ROD, x, y, z, 1, 0.2, 0.2, 0.2, 0);
        }
    }

    /**
     * 在死亡点 20 格范围内生成一道纯视觉闪电
     */
    private static void spawnLightning(ServerLevel level, BlockPos center) {
        double x = center.getX() + 0.5 + (level.random.nextDouble() - 0.5) * LIGHTNING_RANGE * 2;
        double z = center.getZ() + 0.5 + (level.random.nextDouble() - 0.5) * LIGHTNING_RANGE * 2;
        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
        if (bolt != null) {
            bolt.moveTo(x, center.getY(), z);
            bolt.setVisualOnly(true);
            level.addFreshEntity(bolt);
        }
    }

    private static void spawnBoss(ServerLevel level, BlockPos spawnPos) {
        DragonSoulBoss boss = ModEntities.DRAGON_SOUL_BOSS.get().spawn(level, spawnPos, MobSpawnType.EVENT);
        if (boss == null) {
            SourceOfMystery.LOGGER.warn("Failed to spawn Dragon Soul Boss");
            return;
        }
        SourceOfMystery.LOGGER.info("Dragon Soul Boss spawned at {} in {}", spawnPos, level.dimension().location());
        broadcast(level, Component.translatable("message.sourceofmystery.dragon_soul.arrived")
                .withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD));
    }

    private static void broadcast(ServerLevel level, Component message) {
        Component text = Component.empty()
                .append(Component.translatable("message.sourceofmystery.dragon_soul.prefix")
                        .withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.BOLD))
                .append(" ")
                .append(message);
        for (ServerPlayer player : level.players()) {
            player.sendSystemMessage(text);
        }
    }

    /**
     * 召唤流程的持久化状态（挂在末影龙所在维度上）
     */
    private static class SpawnState extends SavedData {
        private static final String DATA_NAME = SourceOfMystery.MOD_ID + "_dragon_soul_summon";

        private int stage = STAGE_IDLE;
        private int stageTick = 0;
        private BlockPos spawnPos = new BlockPos(0, 70, 0);

        static SpawnState get(ServerLevel level) {
            return level.getDataStorage().computeIfAbsent(SpawnState::load, SpawnState::new, DATA_NAME);
        }

        static SpawnState load(CompoundTag tag) {
            SpawnState state = new SpawnState();
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
