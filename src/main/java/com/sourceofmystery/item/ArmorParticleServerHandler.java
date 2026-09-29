package com.sourceofmystery.item;

import com.sourceofmystery.SourceOfMystery;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 护甲环绕粒子 - 服务端广播（让其他玩家也能看到穿戴者的粒子特效）
 * - 只发给与穿戴者处于同一维度的其他玩家（sendParticles 自带 32 格距离过滤）
 * - 穿戴者自己的粒子由客户端 ArmorParticleEffect 渲染（第三人称显示，第一人称隐藏）
 */
@Mod.EventBusSubscriber(modid = SourceOfMystery.MOD_ID)
public class ArmorParticleServerHandler {

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level)) {
            return;
        }
        var players = level.players();
        if (players.size() < 2) {
            return;
        }

        long gameTime = level.getGameTime();
        for (ServerPlayer wearer : players) {
            ArmorParticlePattern.emit(wearer, gameTime, SatelliteAttackHandler.busyMask(wearer), (particle, x, y, z) -> {
                for (ServerPlayer viewer : players) {
                    if (viewer != wearer) {
                        level.sendParticles(viewer, particle, false, x, y, z, 1, 0, 0, 0, 0);
                    }
                }
            });
        }
    }
}
