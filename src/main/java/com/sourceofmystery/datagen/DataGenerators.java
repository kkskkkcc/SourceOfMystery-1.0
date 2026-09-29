package com.sourceofmystery.datagen;

import com.sourceofmystery.SourceOfMystery;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.DataGenerator;
import net.minecraft.data.PackOutput;
import net.minecraft.data.loot.LootTableProvider;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraftforge.common.data.ExistingFileHelper;
import net.minecraftforge.data.event.GatherDataEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * 数据生成入口：运行 ./gradlew runData，输出到 src/generated/resources。
 * 语言文件、成就和祭坛方块的自定义模型仍在 src/main/resources 中手写维护。
 */
@Mod.EventBusSubscriber(modid = SourceOfMystery.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class DataGenerators {

    private DataGenerators() {
    }

    @SubscribeEvent
    public static void gatherData(GatherDataEvent event) {
        DataGenerator generator = event.getGenerator();
        PackOutput output = generator.getPackOutput();
        ExistingFileHelper existingFiles = event.getExistingFileHelper();
        CompletableFuture<HolderLookup.Provider> lookup = event.getLookupProvider();

        generator.addProvider(event.includeClient(), new ModBlockStateProvider(output, existingFiles));
        generator.addProvider(event.includeClient(), new ModItemModelProvider(output, existingFiles));

        generator.addProvider(event.includeServer(), new ModBlockTagsProvider(output, lookup, existingFiles));
        generator.addProvider(event.includeServer(), new LootTableProvider(output, Set.of(),
                List.of(new LootTableProvider.SubProviderEntry(ModBlockLootTables::new, LootContextParamSets.BLOCK))));
        generator.addProvider(event.includeServer(), new ModRecipeProvider(output));
    }
}
