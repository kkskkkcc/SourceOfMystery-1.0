package com.sourceofmystery;

import com.sourceofmystery.block.ModBlockEntities;
import com.sourceofmystery.block.ModBlocks;
import com.sourceofmystery.creativetab.ModCreativeTabs;
import com.sourceofmystery.entity.ModEntities;
import com.sourceofmystery.item.ModItems;
import com.sourceofmystery.network.ModNetwork;
import com.sourceofmystery.recipe.altar.AltarRecipeRegistry;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(SourceOfMystery.MOD_ID)
public class SourceOfMystery {
    public static final String MOD_ID = "sourceofmystery";
    public static final Logger LOGGER = LogManager.getLogger();

    public SourceOfMystery() {
        LOGGER.info("SourceOfMystery mod initialized!");
        var modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        ModItems.ITEMS.register(modEventBus);
        ModBlocks.BLOCKS.register(modEventBus);
        ModBlockEntities.BLOCK_ENTITIES.register(modEventBus);
        ModCreativeTabs.CREATIVE_TABS.register(modEventBus);
        ModEntities.ENTITIES.register(modEventBus);
        modEventBus.addListener(ModEntities::registerAttributes);

        // 注册通用设置
        modEventBus.addListener(this::commonSetup);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            // 注册网络
            ModNetwork.register();
            // 注册神秘祭坛配方 - 在所有DeferredRegister完成后执行
            AltarRecipeRegistry.registerRecipes();
        });
    }
}
