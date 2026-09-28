package com.sourceofmystery.creativetab;

import com.sourceofmystery.SourceOfMystery;
import com.sourceofmystery.block.ModBlocks;
import com.sourceofmystery.item.ModItems;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, SourceOfMystery.MOD_ID);

    public static final RegistryObject<CreativeModeTab> SOURCE_OF_MYSTERY_TAB = CREATIVE_TABS.register("source_of_mystery",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("creativetab.source_of_mystery"))
                    .icon(() -> new ItemStack(ModItems.MYSTERY_INGOT.get()))
                    .displayItems((parameters, output) -> {
                        // 基础物品 - 直接使用 .get()
                        output.accept(ModItems.MYSTERY_INGOT.get());
                        output.accept(ModItems.HEAVENLY_DAO_FRAGMENT.get());

                        // 方块物品 - 使用注册的 BlockItem
                        output.accept(ModItems.MYSTERY_SOURCE_ORE.get());
                        output.accept(ModItems.MYSTERY_ALTAR.get());

                        // 武器 - 直接使用 .get()
                        output.accept(ModItems.MYSTERY_SWORD.get());
                        output.accept(ModItems.SPIRIT_SOURCE_SWORD.get());
                        output.accept(ModItems.DARK_SOURCE_SWORD.get());
                        output.accept(ModItems.ORIGIN_DRAGON_SWORD.get());
                        output.accept(ModItems.DIVINE_PUNISHMENT_SWORD.get());

                        // 装备 - 直接使用 .get()
                        output.accept(ModItems.MYSTERY_CHESTPLATE.get());
                        output.accept(ModItems.SPIRIT_SOURCE_CHESTPLATE.get());
                        output.accept(ModItems.DARK_SOURCE_CHESTPLATE.get());
                        output.accept(ModItems.ORIGIN_DRAGON_CHESTPLATE.get());
                        output.accept(ModItems.DIVINE_BLESSING_CHESTPLATE.get());
                    })
                    .build());
}
