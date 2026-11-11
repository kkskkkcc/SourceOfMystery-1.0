package com.sourceofmystery.block;

import com.sourceofmystery.SourceOfMystery;
import com.sourceofmystery.guardian.GuardianAltarBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, SourceOfMystery.MOD_ID);
    public static final RegistryObject<BlockEntityType<com.sourceofmystery.alchemy.AlchemistTableBlockEntity>> ALCHEMIST_TABLE =
            BLOCK_ENTITIES.register("alchemist_table",()->BlockEntityType.Builder.of(com.sourceofmystery.alchemy.AlchemistTableBlockEntity::new,ModBlocks.ALCHEMIST_TABLE.get()).build(null));
    public static final RegistryObject<BlockEntityType<com.sourceofmystery.enchanter.MysteryEnchanterBlockEntity>> MYSTERY_ENCHANTER =
            BLOCK_ENTITIES.register("mystery_enchanter",()->BlockEntityType.Builder.of(com.sourceofmystery.enchanter.MysteryEnchanterBlockEntity::new,ModBlocks.MYSTERY_ENCHANTER.get()).build(null));

    @SuppressWarnings("DataFlowIssue") // 方块实体类型不需要 DataFixer
    public static final RegistryObject<BlockEntityType<GuardianAltarBlockEntity>> GUARDIAN_ALTAR =
            BLOCK_ENTITIES.register("guardian_altar", () -> BlockEntityType.Builder
                    .of(GuardianAltarBlockEntity::new, ModBlocks.GUARDIAN_ALTAR.get()).build(null));

    public static final RegistryObject<BlockEntityType<DarkDimensionalOfferingBlockEntity>> DARK_DIMENSIONAL_OFFERING =
            BLOCK_ENTITIES.register("dark_dimensional_offering", () -> BlockEntityType.Builder
                    .of(DarkDimensionalOfferingBlockEntity::new, ModBlocks.DARK_DIMENSIONAL_OFFERING.get()).build(null));
}
