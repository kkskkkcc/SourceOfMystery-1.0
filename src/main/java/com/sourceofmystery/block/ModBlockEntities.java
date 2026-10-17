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

    @SuppressWarnings("DataFlowIssue") // 方块实体类型不需要 DataFixer
    public static final RegistryObject<BlockEntityType<GuardianAltarBlockEntity>> GUARDIAN_ALTAR =
            BLOCK_ENTITIES.register("guardian_altar", () -> BlockEntityType.Builder
                    .of(GuardianAltarBlockEntity::new, ModBlocks.GUARDIAN_ALTAR.get()).build(null));
}
