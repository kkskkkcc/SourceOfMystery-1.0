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
                        ModItems.WOOD_SOURCE, ModItems.GOLD_SOURCE, ModItems.WATER_SOURCE,
                        ModItems.FIRE_SOURCE, ModItems.EARTH_SOURCE,
                        ModItems.MYSTERY_SOURCE_ORE)
                .forEach(item -> basicItem(item.get()));

        // Gear uses the shared GeckoLib display parents.
        List.of(ModItems.MYSTERY_CHESTPLATE, ModItems.SPIRIT_SOURCE_CHESTPLATE, ModItems.DARK_SOURCE_CHESTPLATE,
                        ModItems.ORIGIN_DRAGON_CHESTPLATE, ModItems.DIVINE_BLESSING_CHESTPLATE)
                .forEach(item -> gear(item, "gear_chestplate_display"));
        List.of(ModItems.MYSTERY_SWORD, ModItems.SPIRIT_SOURCE_SWORD, ModItems.DARK_SOURCE_SWORD,
                        ModItems.ORIGIN_DRAGON_SWORD, ModItems.DIVINE_PUNISHMENT_SWORD)
                .forEach(item -> gear(item, "gear_sword_display"));

        // 祭坛在物品栏里直接显示方块模型
        withExistingParent("mystery_altar", modLoc("block/mystery_altar"));
    }

    private void gear(RegistryObject<Item> item, String displayParent) {
        String name = ForgeRegistries.ITEMS.getKey(item.get()).getPath();
        // The flat icon remains the break-particle texture.
        withExistingParent(name, modLoc("item/" + displayParent)).texture("particle", modLoc("item/" + name));
    }
}
