package com.sourceofmystery.client;

import com.sourceofmystery.SourceOfMystery;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 客户端专用 FORGE 总线事件
 */
@Mod.EventBusSubscriber(modid = SourceOfMystery.MOD_ID, value = Dist.CLIENT)
public class ClientForgeEvents {

    /**
     * 原版凋灵死亡时由泣死之主出场的「凋灵躯壳」（WitherHusk）代替，原版倒地变红的死亡动画不画
     */
    @SubscribeEvent
    public static void onRenderLiving(RenderLivingEvent.Pre<?, ?> event) {
        LivingEntity entity = event.getEntity();
        if (entity.getType() == EntityType.WITHER && entity.isDeadOrDying()) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientEnergyCache.reset();
        ClientSatelliteCache.busyMask = 0;
        ClientCinematicCache.reset();
    }
}
