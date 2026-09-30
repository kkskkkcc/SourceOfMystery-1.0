package com.sourceofmystery.client;

import com.sourceofmystery.SourceOfMystery;
import com.sourceofmystery.entity.ModEntities;
import com.sourceofmystery.hud.MysteryEnergyOverlay;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 客户端专用 MOD 总线事件：注册实体渲染器和 HUD
 */
@Mod.EventBusSubscriber(modid = SourceOfMystery.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ClientModEvents {

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        // 神威天道使用 GeckoLib 模型渲染
        event.registerEntityRenderer(ModEntities.DIVINE_HEAVENLY_DAO_BOSS.get(), DivineHeavenlyDaoBossRenderer::new);
        event.registerEntityRenderer(ModEntities.DRAGON_SOUL_BOSS.get(), DragonSoulRenderer::new);
    }

    @SubscribeEvent
    public static void registerOverlays(RegisterGuiOverlaysEvent event) {
        event.registerAboveAll(MysteryEnergyOverlay.ID, new MysteryEnergyOverlay());
    }
}
