package com.sourceofmystery.entity;

import com.sourceofmystery.SourceOfMystery;
import com.sourceofmystery.advancement.AdvancementHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

@Mod.EventBusSubscriber(modid = SourceOfMystery.MOD_ID)
public class BossSpawnHandler {

    // 倒计时阶段：20 秒 = 400 tick
    private static final int COUNTDOWN_TICKS = 400; // 20 秒
    private static final int LIGHTNING_COUNT = 10; // 闪电数量
    private static final int LIGHTNING_INTERVAL_TICKS = 5; // 每 5 tick 落一道闪电

    // 状态机：0=空闲，1=倒计时，2=闪电阶段
    private static int stage = 0;
    private static int stageTick = 0;

    @SubscribeEvent
    public static void onEnderDragonDeath(LivingDeathEvent event) {
        if (event.getEntity().getType() == EntityType.ENDER_DRAGON) {
            if (stage == 0) {
                stage = 1; // 进入倒计时阶段
                stageTick = 0;
                SourceOfMystery.LOGGER.info("Ender Dragon died. Boss summoning countdown started.");
                broadcastToEnd((ServerLevel) event.getEntity().level(), "§5§l【神威天道】§r§d 末影龙已陨落，天道之力开始苏醒……");
            }
        }
    }

    public static void tick(ServerLevel level) {
        if (level.dimension() != Level.END) {
            return;
        }

        if (stage == 0) {
            return;
        }

        stageTick++;

        if (stage == 1) {
            tickCountdown(level);
        } else if (stage == 2) {
            tickLightning(level);
        }
    }

    /**
     * 倒计时阶段：聊天框吟唱 + 倒计时
     */
    private static void tickCountdown(ServerLevel level) {
        int remainingSeconds = (COUNTDOWN_TICKS - stageTick) / 20 + 1;

        // 每 20 tick（1秒）在聊天框提示一次
        if (stageTick % 20 == 0 && remainingSeconds >= 0) {
            broadcastToEnd(level, "§5§l【神威天道】§r §e距离天道降临还有 §c" + remainingSeconds + " §e秒……");
        }

        // 倒计时结束，进入闪电阶段
        if (stageTick >= COUNTDOWN_TICKS) {
            stage = 2;
            stageTick = 0;
            broadcastToEnd(level, "§5§l【神威天道】§r §c天穹撕裂，雷霆万钧！");
        }
    }

    /**
     * 闪电阶段：在 Boss 即将生成的位置落 10 道闪电，结束后 Boss 出现
     */
    private static void tickLightning(ServerLevel level) {
        BlockPos spawnPos = new BlockPos(0, 80, 0);

        // 每 LIGHTNING_INTERVAL_TICKS tick 落一道闪电
        if (stageTick % LIGHTNING_INTERVAL_TICKS == 0) {
            int lightningIndex = stageTick / LIGHTNING_INTERVAL_TICKS;
            if (lightningIndex < LIGHTNING_COUNT) {
                spawnLightning(level, spawnPos);
            }
        }

        // 闪电阶段结束（10 道闪电 + 少量缓冲），生成 Boss
        if (stageTick >= LIGHTNING_COUNT * LIGHTNING_INTERVAL_TICKS + 10) {
            spawnBoss(level);
            stage = 0;
            stageTick = 0;
        }
    }

    /**
     * 在指定位置附近生成一道闪电
     */
    private static void spawnLightning(ServerLevel level, BlockPos center) {
        // 闪电落在中心位置附近随机偏移处（模拟天雷轰击区域）
        double offsetX = (level.random.nextDouble() - 0.5) * 6;
        double offsetZ = (level.random.nextDouble() - 0.5) * 6;
        double x = center.getX() + 0.5 + offsetX;
        double z = center.getZ() + 0.5 + offsetZ;

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
    private static void broadcastToEnd(ServerLevel level, String message) {
        Component text = Component.literal(message);
        for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
            player.sendSystemMessage(text);
        }
    }

    private static void spawnBoss(ServerLevel level) {
        BlockPos spawnPos = new BlockPos(0, 80, 0);

        DivineHeavenlyDaoBoss boss = ModEntities.DIVINE_HEAVENLY_DAO_BOSS.get().spawn(level, spawnPos, MobSpawnType.COMMAND);

        if (boss != null) {
            SourceOfMystery.LOGGER.info("Divine Heavenly Dao Boss spawned at End dimension");

            broadcastToEnd(level, "§5§l【神威天道】§r §c§l神威天道已降临！");

            // 授予"触怒天道"成就：Boss 出现在玩家周围 100 格范围内
            final double RANGE = 100.0;
            final double RANGE_SQ = RANGE * RANGE;
            AABB area = new AABB(spawnPos).inflate(RANGE);
            List<ServerPlayer> nearbyPlayers = level.getEntitiesOfClass(ServerPlayer.class, area);
            for (ServerPlayer nearby : nearbyPlayers) {
                double dx = nearby.getX() - (spawnPos.getX() + 0.5);
                double dy = nearby.getY() - (spawnPos.getY() + 0.5);
                double dz = nearby.getZ() - (spawnPos.getZ() + 0.5);
                double distSq = dx * dx + dy * dy + dz * dz;
                if (distSq <= RANGE_SQ) {
                    AdvancementHelper.grantAdvancement(nearby, "provoke_heaven");
                }
            }
        } else {
            SourceOfMystery.LOGGER.warn("Failed to spawn Divine Heavenly Dao Boss");
        }
    }
}
