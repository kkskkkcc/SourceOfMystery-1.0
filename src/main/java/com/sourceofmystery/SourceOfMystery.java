package com.sourceofmystery;

import com.sourceofmystery.block.ModBlocks;
import com.sourceofmystery.combat.ArmorLimits;
import com.sourceofmystery.config.MysteryConfig;
import com.sourceofmystery.creativetab.ModCreativeTabs;
import com.sourceofmystery.entity.ModEntities;
import com.sourceofmystery.item.ModItems;
import com.sourceofmystery.loot.ModLootModifiers;
import com.sourceofmystery.sound.ModSounds;
import com.sourceofmystery.network.ModNetwork;
import com.sourceofmystery.particle.ModParticles;
import com.sourceofmystery.recipe.ModRecipes;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(SourceOfMystery.MOD_ID)
public class SourceOfMystery {
    public static final String MOD_ID = "sourceofmystery";
    public static final Logger LOGGER = LogManager.getLogger();

    public SourceOfMystery() {
        var modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        ModItems.ITEMS.register(modEventBus);
        ModBlocks.BLOCKS.register(modEventBus);
        ModCreativeTabs.CREATIVE_TABS.register(modEventBus);
        ModEntities.ENTITIES.register(modEventBus);
        ModRecipes.TYPES.register(modEventBus);
        ModRecipes.SERIALIZERS.register(modEventBus);
        ModLootModifiers.SERIALIZERS.register(modEventBus);
        ModSounds.SOUNDS.register(modEventBus);
        ModParticles.PARTICLES.register(modEventBus);
        modEventBus.addListener(ModEntities::registerAttributes);
        modEventBus.addListener(this::commonSetup);

        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, MysteryConfig.COMMON_SPEC);
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, MysteryConfig.CLIENT_SPEC);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            ModNetwork.register();
            // COMMON 配置在通用设置之前已加载，可以读取
            ArmorLimits.raiseAttributeCaps();
        });
    }
}
