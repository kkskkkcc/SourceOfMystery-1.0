package com.sourceofmystery.client;

import com.sourceofmystery.SourceOfMystery;
import com.sourceofmystery.item.ArmorParticlePattern;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 穿戴者自己看到的护甲环绕粒子（纯客户端）。
 * 第一人称视角不显示（避免挡视野）；其他玩家的粒子由服务端 ArmorParticleServerHandler 发送。
 */
@Mod.EventBusSubscriber(modid = SourceOfMystery.MOD_ID, value = Dist.CLIENT)
public class ArmorParticleEffect {

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        ClientLevel level = mc.level;
        if (player == null || level == null || mc.isPaused() || mc.options.getCameraType().isFirstPerson()) {
            return;
        }

        ArmorParticlePattern.emit(player, level.getGameTime(),
                (particle, x, y, z) -> level.addParticle(particle, x, y, z, 0, 0, 0));
    }
}
