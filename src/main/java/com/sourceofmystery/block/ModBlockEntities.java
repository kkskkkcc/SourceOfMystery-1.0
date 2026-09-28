package com.sourceofmystery.block;

import com.sourceofmystery.SourceOfMystery;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, SourceOfMystery.MOD_ID);

    public static final RegistryObject<BlockEntityType<MysteryAltarTileEntity>> MYSTERY_ALTAR =
            BLOCK_ENTITIES.register("mystery_altar",
                    () -> BlockEntityType.Builder.of(
                            MysteryAltarTileEntity::new,
                            ModBlocks.MYSTERY_ALTAR.get()
                    ).build(null));
}
