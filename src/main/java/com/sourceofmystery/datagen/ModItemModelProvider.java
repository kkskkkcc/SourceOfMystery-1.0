package com.sourceofmystery.datagen;

import com.sourceofmystery.SourceOfMystery;
import com.sourceofmystery.item.ModItems;
import net.minecraft.data.PackOutput;
import net.minecraft.world.item.Item;
import net.minecraftforge.client.model.generators.ItemModelProvider;
import net.minecraftforge.common.data.ExistingFileHelper;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.List;

public class ModItemModelProvider extends ItemModelProvider {

    public ModItemModelProvider(PackOutput output, ExistingFileHelper existingFiles) {
        super(output, SourceOfMystery.MOD_ID, existingFiles);
    }

    @Override
    protected void registerModels() {
        // 普通物品（包括神秘源矿，它使用单独的 2D 物品贴图）
        List.of(ModItems.MYSTERY_INGOT, ModItems.HEAVENLY_DAO_FRAGMENT, ModItems.DRAGON_SOUL,
                        ModItems.MYSTERY_SOURCE_ORE,
                        ModItems.MYSTERY_CHESTPLATE, ModItems.SPIRIT_SOURCE_CHESTPLATE, ModItems.DARK_SOURCE_CHESTPLATE,
                        ModItems.ORIGIN_DRAGON_CHESTPLATE, ModItems.DIVINE_BLESSING_CHESTPLATE)
                .forEach(item -> basicItem(item.get()));

        // 剑使用 handheld 父模型，手持时像原版剑一样斜握
        List.of(ModItems.MYSTERY_SWORD, ModItems.SPIRIT_SOURCE_SWORD, ModItems.DARK_SOURCE_SWORD,
                        ModItems.ORIGIN_DRAGON_SWORD, ModItems.DIVINE_PUNISHMENT_SWORD)
                .forEach(this::handheld);

        // 祭坛在物品栏里直接显示方块模型
        withExistingParent("mystery_altar", modLoc("block/mystery_altar"));
    }

    private void handheld(RegistryObject<Item> item) {
        String name = ForgeRegistries.ITEMS.getKey(item.get()).getPath();
        withExistingParent(name, mcLoc("item/handheld")).texture("layer0", modLoc("item/" + name));
    }
}
