package com.sourceofmystery.entity;

import com.sourceofmystery.SourceOfMystery;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, SourceOfMystery.MOD_ID);

    public static final RegistryObject<EntityType<DivineHeavenlyDaoBoss>> DIVINE_HEAVENLY_DAO_BOSS =
            ENTITIES.register("divine_heavenly_dao_boss",
                    () -> EntityType.Builder.of(DivineHeavenlyDaoBoss::new, MobCategory.MONSTER)
                            .sized(1.5f * DivineHeavenlyDaoBoss.SCALE, 4.0f * DivineHeavenlyDaoBoss.SCALE)
                            .fireImmune()
                            .clientTrackingRange(16)
                            .updateInterval(1)
                            .build(new ResourceLocation(SourceOfMystery.MOD_ID, "divine_heavenly_dao_boss").toString()));

    public static final RegistryObject<EntityType<DragonSoulBoss>> DRAGON_SOUL_BOSS =
            ENTITIES.register("dragon_soul_boss",
                    () -> EntityType.Builder.of(DragonSoulBoss::new, MobCategory.MONSTER)
                            .sized(1.5f, 4.0f)
                            .clientTrackingRange(100)
                            .updateInterval(1)
                            .build(new ResourceLocation(SourceOfMystery.MOD_ID, "dragon_soul_boss").toString()));

    // 原版凋灵碰撞箱 0.9 x 3.5，按倍数放大
    public static final RegistryObject<EntityType<GiantWither>> GIANT_WITHER =
            ENTITIES.register("giant_wither",
                    () -> EntityType.Builder.<GiantWither>of(GiantWither::new, MobCategory.MONSTER)
                            .sized(0.9f * GiantWither.SCALE, 3.5f * GiantWither.SCALE)
                            .fireImmune()
                            .clientTrackingRange(16)
                            .build(new ResourceLocation(SourceOfMystery.MOD_ID, "giant_wither").toString()));

    public static void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(DIVINE_HEAVENLY_DAO_BOSS.get(), DivineHeavenlyDaoBoss.createAttributes().build());
        event.put(DRAGON_SOUL_BOSS.get(), DragonSoulBoss.createAttributes().build());
        event.put(GIANT_WITHER.get(), WitherBoss.createAttributes().build());
    }
}
