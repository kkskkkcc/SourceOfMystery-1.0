package com.sourceofmystery.client;

import com.sourceofmystery.SourceOfMystery;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 客户端专用 FORGE 总线事件
 */
@Mod.EventBusSubscriber(modid = SourceOfMystery.MOD_ID, value = Dist.CLIENT)
public class ClientForgeEvents {

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientEnergyCache.reset();
        ClientSatelliteCache.busyMask = 0;
        ClientCinematicCache.reset();
    }
}
