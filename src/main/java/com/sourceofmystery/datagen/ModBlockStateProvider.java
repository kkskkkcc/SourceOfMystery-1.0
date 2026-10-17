package com.sourceofmystery.datagen;

import com.sourceofmystery.SourceOfMystery;
import com.sourceofmystery.block.ModBlocks;
import net.minecraft.data.PackOutput;
import net.minecraftforge.client.model.generators.BlockStateProvider;
import net.minecraftforge.common.data.ExistingFileHelper;

public class ModBlockStateProvider extends BlockStateProvider {

    public ModBlockStateProvider(PackOutput output, ExistingFileHelper existingFiles) {
        super(output, SourceOfMystery.MOD_ID, existingFiles);
    }

    @Override
    protected void registerStatesAndModels() {
        simpleBlock(ModBlocks.MYSTERY_SOURCE_ORE.get(), cubeAll(ModBlocks.MYSTERY_SOURCE_ORE.get()));
        // 祭坛是手写的多层模型（src/main/resources/assets/sourceofmystery/models/block/mystery_altar.json）
        simpleBlock(ModBlocks.MYSTERY_ALTAR.get(), models().getExistingFile(modLoc("block/mystery_altar")));
        // 守护祭坛由 GeckoLib 方块实体渲染，方块模型只提供破坏粒子
        simpleBlock(ModBlocks.GUARDIAN_ALTAR.get(), models().getBuilder("guardian_altar")
                .texture("particle", modLoc("block/guardian_altar_particle")));
    }
}
