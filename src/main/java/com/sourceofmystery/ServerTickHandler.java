package com.sourceofmystery;

import com.sourceofmystery.capability.energy.MysteryEnergyCapability;
import com.sourceofmystery.entity.BossSpawnHandler;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;

@Mod.EventBusSubscriber(modid = SourceOfMystery.MOD_ID)
public class ServerTickHandler {

    private static final int TICKS_PER_DAY = 24000; // Minecraft 每天 24000 tick
    // 记录每个玩家上次所在的游戏天数
    private static final Map<String, Integer> PLAYER_LAST_DAY = new HashMap<>();

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        MinecraftServer server = event.getServer();

        // ==================== 每日回满神秘之能 ====================
        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        if (overworld != null) {
            int currentDay = (int) (overworld.getGameTime() / TICKS_PER_DAY);
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                String playerId = player.getStringUUID();
                Integer lastDay = PLAYER_LAST_DAY.get(playerId);
                if (lastDay == null) {
                    // 首次见到该玩家：只记录当前天数，不刷新
                    PLAYER_LAST_DAY.put(playerId, currentDay);
                } else if (currentDay > lastDay) {
                    // 新的一天：回满到上限
                    MysteryEnergyCapability.dailyRefresh(player);
                    PLAYER_LAST_DAY.put(playerId, currentDay);
                }
            }
        }

        // ==================== 检查 Boss 生成 ====================
        for (ServerLevel level : server.getAllLevels()) {
            if (level.dimension() == Level.END) {
                BossSpawnHandler.tick(level);
            }
        }
    }
}
