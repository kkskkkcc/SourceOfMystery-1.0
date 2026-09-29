package com.sourceofmystery.datagen;

import com.sourceofmystery.block.ModBlocks;
import net.minecraft.data.loot.BlockLootSubProvider;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.registries.RegistryObject;

import java.util.Set;

public class ModBlockLootTables extends BlockLootSubProvider {

    public ModBlockLootTables() {
        super(Set.of(), FeatureFlags.REGISTRY.allFlags());
    }

    @Override
    protected void generate() {
        dropSelf(ModBlocks.MYSTERY_ALTAR.get());
        // 精准采集掉落矿石本身，否则掉落矿石并受时运加成（矿石需要烧炼成秘源锭）
        add(ModBlocks.MYSTERY_SOURCE_ORE.get(), block -> createOreDrop(block, block.asItem()));
    }

    @Override
    protected Iterable<Block> getKnownBlocks() {
        return ModBlocks.BLOCKS.getEntries().stream().map(RegistryObject::get)::iterator;
    }
}
