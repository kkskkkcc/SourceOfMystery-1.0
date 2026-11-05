package com.sourceofmystery.block;

import com.sourceofmystery.SourceOfMystery;
import com.sourceofmystery.guardian.GuardianAltarBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModBlocks {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, SourceOfMystery.MOD_ID);

    public static final RegistryObject<Block> MYSTERY_SOURCE_ORE = BLOCKS.register("mystery_source_ore",
            () -> new Block(BlockBehaviour.Properties.copy(net.minecraft.world.level.block.Blocks.DIAMOND_ORE)
                    .lightLevel(state -> 15) // 发光，亮度参考萤石（15）
                    .requiresCorrectToolForDrops()));

    public static final RegistryObject<Block> MYSTERY_ALTAR = BLOCKS.register("mystery_altar",
            () -> new MysteryAltarBlock());

    // 守护祭坛：手持下界之星右键激活，展开 3x3 区块的守护结界
    public static final RegistryObject<Block> GUARDIAN_ALTAR = BLOCKS.register("guardian_altar",
            GuardianAltarBlock::new);

    public static final RegistryObject<Block> DARK_DIMENSIONAL_OFFERING = BLOCKS.register("dark_dimensional_offering",
            DarkDimensionalOfferingBlock::new);
    public static final RegistryObject<Block> MYSTERY_ENCHANTER = BLOCKS.register("mystery_enchanter",
            com.sourceofmystery.enchanter.MysteryEnchanterBlock::new);
}
