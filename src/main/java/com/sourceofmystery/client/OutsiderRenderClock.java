package com.sourceofmystery.client;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** One stamp for the main view and every Oculus shadow pass of that frame. */
@Mod.EventBusSubscriber(modid="sourceofmystery",value=Dist.CLIENT)
public final class OutsiderRenderClock {
    private static long frame;
    static long frame(){return frame;}
    @SubscribeEvent public static void begin(TickEvent.RenderTickEvent event){
        if(event.phase==TickEvent.Phase.START)frame++;
    }
}
