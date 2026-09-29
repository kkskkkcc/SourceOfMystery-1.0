package com.sourceofmystery.datagen;

import com.sourceofmystery.SourceOfMystery;
import com.sourceofmystery.block.ModBlocks;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.tags.BlockTags;
import net.minecraftforge.common.data.BlockTagsProvider;
import net.minecraftforge.common.data.ExistingFileHelper;

import javax.annotation.Nullable;
import java.util.concurrent.CompletableFuture;

public class ModBlockTagsProvider extends BlockTagsProvider {

    public ModBlockTagsProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> lookup,
                                @Nullable ExistingFileHelper existingFiles) {
        super(output, lookup, SourceOfMystery.MOD_ID, existingFiles);
    }

    @Override
    protected void addTags(HolderLookup.Provider provider) {
        tag(BlockTags.MINEABLE_WITH_PICKAXE).add(ModBlocks.MYSTERY_SOURCE_ORE.get(), ModBlocks.MYSTERY_ALTAR.get());
        tag(BlockTags.NEEDS_IRON_TOOL).add(ModBlocks.MYSTERY_SOURCE_ORE.get());
    }
}
