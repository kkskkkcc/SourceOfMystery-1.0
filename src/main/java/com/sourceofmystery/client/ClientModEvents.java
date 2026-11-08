package com.sourceofmystery.client;

import com.sourceofmystery.SourceOfMystery;
import com.sourceofmystery.block.ModBlockEntities;
import com.sourceofmystery.entity.ModEntities;
import com.sourceofmystery.guardian.GuardianAltarRenderer;
import com.sourceofmystery.hud.MysteryEnergyOverlay;
import com.sourceofmystery.particle.ModParticles;
import net.minecraft.client.renderer.entity.WitherSkullRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 客户端专用 MOD 总线事件：注册实体渲染器和 HUD
 */
@Mod.EventBusSubscriber(modid = SourceOfMystery.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ClientModEvents {
    @SubscribeEvent public static void divineMaterials(net.minecraftforge.client.event.RegisterClientReloadListenersEvent event){
        event.registerReloadListener((net.minecraft.server.packs.resources.ResourceManagerReloadListener)manager->{DivineThroneMaterial.invalidate();StaffArcaneMaterial.invalidate();});
    }
    @SubscribeEvent public static void setupMenus(net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent event) {
        event.enqueueWork(()->net.minecraft.client.gui.screens.MenuScreens.register(com.sourceofmystery.offering.OfferingMenus.LOTTERY.get(),OfferingLotteryScreen::new));
        event.enqueueWork(()->net.minecraft.client.gui.screens.MenuScreens.register(com.sourceofmystery.enchanter.EnchanterMenus.ENCHANTER.get(),MysteryEnchanterScreen::new));
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        // 神威天道使用 GeckoLib 模型渲染
        event.registerEntityRenderer(ModEntities.DIVINE_HEAVENLY_DAO_BOSS.get(), DivineHeavenlyDaoBossRenderer::new);
        event.registerEntityRenderer(ModEntities.DRAGON_SOUL_BOSS.get(), DragonSoulRenderer::new);
        event.registerEntityRenderer(ModEntities.GIANT_WITHER.get(), GiantWitherRenderer::new);
        event.registerEntityRenderer(ModEntities.DRAGON_SPEAR.get(), DragonSpearRenderer::new);
        event.registerEntityRenderer(ModEntities.VOID_RIFT.get(), VoidRiftRenderer::new);
        event.registerEntityRenderer(ModEntities.HEAVEN_SIGIL.get(), HeavenSigilRenderer::new);
        event.registerEntityRenderer(ModEntities.ABYSS_ORB.get(), AbyssOrbRenderer::new);
        event.registerEntityRenderer(ModEntities.WEEPING_DEATH_LORD.get(), WeepingDeathLordRenderer::new);
        event.registerEntityRenderer(ModEntities.WITHER_HUSK.get(), WitherHuskRenderer::new);
        event.registerEntityRenderer(ModEntities.THROWN_SCYTHE.get(), ThrownScytheRenderer::new);
        event.registerEntityRenderer(ModEntities.DEATH_SKULL.get(), WitherSkullRenderer::new);
        event.registerEntityRenderer(ModEntities.SPACE_SHATTER.get(), SpaceShatterRenderer::new);
        event.registerEntityRenderer(ModEntities.GUARDIAN_SPIRIT.get(), GuardianSpiritRenderer::new);
        event.registerEntityRenderer(ModEntities.GUARDIAN_SPIRIT_PROJECTILE.get(), GuardianSpiritProjectileRenderer::new);
        event.registerEntityRenderer(ModEntities.OUTSIDER.get(), OutsiderRenderer::new);
        event.registerEntityRenderer(ModEntities.OUTSIDER_SPELL.get(), OutsiderSpellRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.GUARDIAN_ALTAR.get(), GuardianAltarRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.DARK_DIMENSIONAL_OFFERING.get(), DarkDimensionalOfferingRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.MYSTERY_ENCHANTER.get(),MysteryEnchanterRenderer::new);

    }

    @SubscribeEvent
    public static void addArmorDecorations(EntityRenderersEvent.AddLayers event) {
        for (String skin : event.getSkins()) {
            PlayerRenderer renderer = event.getSkin(skin);
            addHalo(renderer);
        }
        addHalo(event.getRenderer(EntityType.ARMOR_STAND));
    }

    private static <T extends LivingEntity, M extends EntityModel<T>>
    void addHalo(LivingEntityRenderer<T, M> renderer) {
        if (renderer != null && renderer.getModel() instanceof HumanoidModel<?>) {
            renderer.addLayer(new DivineBlessingHaloRenderer<>(renderer));
        }
    }

    @SubscribeEvent
    public static void registerParticles(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(ModParticles.GOLD_MOTE.get(), sprites -> new ConvergeParticle.Provider(sprites, true));
        event.registerSpriteSet(ModParticles.DARK_MOTE.get(), sprites -> new ConvergeParticle.Provider(sprites, false));
        event.registerSpriteSet(ModParticles.DEATH_SKULL.get(), DeathSkullParticle.Provider::new);
    }

    @SubscribeEvent
    public static void registerOverlays(RegisterGuiOverlaysEvent event) {
        event.registerAboveAll(MysteryEnergyOverlay.ID, new MysteryEnergyOverlay());
    }
}
