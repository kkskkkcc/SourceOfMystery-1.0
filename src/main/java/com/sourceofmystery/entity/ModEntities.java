package com.sourceofmystery.entity;

import com.sourceofmystery.SourceOfMystery;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, SourceOfMystery.MOD_ID);

    public static final RegistryObject<EntityType<DivineHeavenlyDaoBoss>> DIVINE_HEAVENLY_DAO_BOSS =
            ENTITIES.register("divine_heavenly_dao_boss",
                    () -> EntityType.Builder.of(DivineHeavenlyDaoBoss::new, MobCategory.MONSTER)
                            .sized(1.5f, 4.0f)
                            .clientTrackingRange(100)
                            .updateInterval(1)
                            .build(new ResourceLocation(SourceOfMystery.MOD_ID, "divine_heavenly_dao_boss").toString()));

    public static void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(DIVINE_HEAVENLY_DAO_BOSS.get(), DivineHeavenlyDaoBoss.createAttributes().build());
    }
}
