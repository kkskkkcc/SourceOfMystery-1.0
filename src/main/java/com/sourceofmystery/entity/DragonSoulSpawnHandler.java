package com.sourceofmystery.entity;

import com.google.common.collect.ImmutableList;
import com.sourceofmystery.SourceOfMystery;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.SpikeFeature;
import net.minecraft.world.level.levelgen.feature.configurations.SpikeConfiguration;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityMountEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.UUID;

/**
 * 龙魂 Boss 召唤流程：
 * 末影龙死亡 -> 跟踪原版死亡动画（升空约 10 秒）直到末影龙消失，记下它最后的位置
 * -> 刷新末地的末地水晶 -> 龙魂在该位置撕开空间裂缝出场（出场动画见 DragonSoulBoss）。
 * 流程状态保存在末影龙所在维度的 SavedData 中，服务器重启不丢进度。
 */
@Mod.EventBusSubscriber(modid = SourceOfMystery.MOD_ID)
public class DragonSoulSpawnHandler {

    private static final int STAGE_IDLE = 0;
    private static final int STAGE_WAIT_DEATH = 1;   // 等末影龙死亡动画结束
    // 旧版本的 2~4 阶段（龙吟 / 闪电 / 卫星）已经由龙魂的出场动画取代，读到时直接生成

    // 原版死亡动画约 10 秒（200 tick）后末影龙被移除；超过这个时间还在就不再等
    private static final int DEATH_ANIM_TIMEOUT = 260;

    /**
     * 末影龙死亡时启动召唤流程
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
        state.dragon = event.getEntity().getUUID();
        state.spawnPos = event.getEntity().position();
        state.setStage(STAGE_WAIT_DEATH);
        SourceOfMystery.LOGGER.info("Dragon Soul summoning started in {}", level.dimension().location());
    }

    /**
     * 龙魂抓着玩家飞上高空时，玩家不能按 Shift 自己挣脱
     */
    @SubscribeEvent
    public static void onDismount(EntityMountEvent event) {
        if (event.isDismounting() && event.getEntityBeingMounted() instanceof DragonSoulBoss boss) {
            Entity rider = event.getEntityMounting();
            boolean gone = !rider.isAlive() || rider.isRemoved()
                    || rider instanceof ServerPlayer player && player.hasDisconnected();
            if (!gone && boss.isAlive() && boss.isHolding(rider)) {
                event.setCanceled(true);
            }
        }
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

        if (state.stage == STAGE_WAIT_DEATH) {
            // 末影龙死亡动画期间会缓缓升空：一直跟着它，直到它消失，用它最后的位置作为裂缝位置
            Entity dragon = state.dragon == null ? null : level.getEntity(state.dragon);
            if (dragon != null) {
                state.spawnPos = dragon.position();
                if (state.stageTick < DEATH_ANIM_TIMEOUT) {
                    return;
                }
            }
        }
        spawnBoss(level, state.spawnPos);
        state.dragon = null;
        state.setStage(STAGE_IDLE);
    }

    private static void spawnBoss(ServerLevel level, Vec3 riftPos) {
        if (level.dimension() == Level.END) {
            respawnEndCrystals(level);
        }
        DragonSoulBoss boss = ModEntities.DRAGON_SOUL_BOSS.get().create(level);
        if (boss == null) {
            SourceOfMystery.LOGGER.warn("Failed to spawn Dragon Soul Boss");
            return;
        }
        boss.moveTo(riftPos.x, riftPos.y, riftPos.z, 0, 0);
        boss.finalizeSpawn(level, level.getCurrentDifficultyAt(BlockPos.containing(riftPos)), MobSpawnType.EVENT, null, null);
        level.addFreshEntity(boss);
        boss.beginIntro(level, riftPos);
        SourceOfMystery.LOGGER.info("Dragon Soul Boss tearing through at {} in {}", riftPos, level.dimension().location());
        broadcast(level, Component.translatable("message.sourceofmystery.dragon_soul.roar")
                .withStyle(ChatFormatting.LIGHT_PURPLE));
    }

    /**
     * 出场动画结束、战斗开始时由 DragonSoulBoss 调用
     */
    public static void announceArrival(ServerLevel level) {
        broadcast(level, Component.translatable("message.sourceofmystery.dragon_soul.arrived")
                .withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD));
    }

    /**
     * 像原版重新召唤末影龙那样重建黑曜石柱顶的末地水晶（已有水晶的柱子跳过），龙魂可以被它们治疗
     */
    private static void respawnEndCrystals(ServerLevel level) {
        RandomSource random = RandomSource.create();
        int placed = 0;
        for (SpikeFeature.EndSpike spike : SpikeFeature.getSpikesForLevel(level)) {
            AABB top = new AABB(spike.getCenterX() - 3, spike.getHeight() - 3, spike.getCenterZ() - 3,
                    spike.getCenterX() + 4, spike.getHeight() + 5, spike.getCenterZ() + 4);
            if (!level.getEntitiesOfClass(EndCrystal.class, top).isEmpty()) {
                continue;
            }
            SpikeConfiguration config = new SpikeConfiguration(false, ImmutableList.of(spike), null);
            if (Feature.END_SPIKE.place(config, level, level.getChunkSource().getGenerator(), random,
                    new BlockPos(spike.getCenterX(), 45, spike.getCenterZ()))) {
                placed++;
            }
        }
        SourceOfMystery.LOGGER.info("Respawned {} end crystals for the Dragon Soul", placed);
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
        private Vec3 spawnPos = new Vec3(0.5, 90, 0.5);
        private UUID dragon;

        static SpawnState get(ServerLevel level) {
            return level.getDataStorage().computeIfAbsent(SpawnState::load, SpawnState::new, DATA_NAME);
        }

        static SpawnState load(CompoundTag tag) {
            SpawnState state = new SpawnState();
            state.stage = tag.getInt("Stage");
            state.stageTick = tag.getInt("StageTick");
            if (tag.contains("PosX")) {
                state.spawnPos = new Vec3(tag.getDouble("PosX"), tag.getDouble("PosY"), tag.getDouble("PosZ"));
            } else if (tag.contains("SpawnX")) {
                state.spawnPos = Vec3.atBottomCenterOf(new BlockPos(tag.getInt("SpawnX"), tag.getInt("SpawnY"), tag.getInt("SpawnZ")));
            }
            if (tag.hasUUID("Dragon")) {
                state.dragon = tag.getUUID("Dragon");
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
            tag.putDouble("PosX", spawnPos.x);
            tag.putDouble("PosY", spawnPos.y);
            tag.putDouble("PosZ", spawnPos.z);
            if (dragon != null) {
                tag.putUUID("Dragon", dragon);
            }
            return tag;
        }
    }
}
