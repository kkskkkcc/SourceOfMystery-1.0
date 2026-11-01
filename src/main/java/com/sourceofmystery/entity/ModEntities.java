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
                            .sized(DivineHeavenlyDaoBoss.HITBOX_WIDTH, DivineHeavenlyDaoBoss.HITBOX_HEIGHT)
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

    public static final RegistryObject<EntityType<DragonSpear>> DRAGON_SPEAR =
            ENTITIES.register("dragon_spear",
                    () -> EntityType.Builder.<DragonSpear>of(DragonSpear::new, MobCategory.MISC)
                            .sized(0.6f, 0.6f)
                            .clientTrackingRange(8)
                            .updateInterval(1)
                            .build(new ResourceLocation(SourceOfMystery.MOD_ID, "dragon_spear").toString()));

    // 原版凋灵碰撞箱 0.9 x 3.5，按倍数放大
    public static final RegistryObject<EntityType<GiantWither>> GIANT_WITHER =
            ENTITIES.register("giant_wither",
                    () -> EntityType.Builder.<GiantWither>of(GiantWither::new, MobCategory.MONSTER)
                            .sized(0.9f * GiantWither.SCALE, 3.5f * GiantWither.SCALE)
                            .fireImmune()
                            .clientTrackingRange(16)
                            .build(new ResourceLocation(SourceOfMystery.MOD_ID, "giant_wither").toString()));

    // 出场演出 / 大招用的纯视觉实体：画面全部由各自的渲染器绘制
    public static final RegistryObject<EntityType<VoidRift>> VOID_RIFT =
            ENTITIES.register("void_rift",
                    () -> EntityType.Builder.<VoidRift>of(VoidRift::new, MobCategory.MISC)
                            .sized(0.5f, 0.5f)
                            .noSave()
                            .fireImmune()
                            .clientTrackingRange(16)
                            .updateInterval(20)
                            .build(new ResourceLocation(SourceOfMystery.MOD_ID, "void_rift").toString()));

    public static final RegistryObject<EntityType<HeavenSigil>> HEAVEN_SIGIL =
            ENTITIES.register("heaven_sigil",
                    () -> EntityType.Builder.<HeavenSigil>of(HeavenSigil::new, MobCategory.MISC)
                            .sized(0.5f, 0.5f)
                            .noSave()
                            .fireImmune()
                            .clientTrackingRange(16)
                            .updateInterval(20)
                            .build(new ResourceLocation(SourceOfMystery.MOD_ID, "heaven_sigil").toString()));

    public static final RegistryObject<EntityType<AbyssOrb>> ABYSS_ORB =
            ENTITIES.register("abyss_orb",
                    () -> EntityType.Builder.<AbyssOrb>of(AbyssOrb::new, MobCategory.MISC)
                            .sized(1.0f, 1.0f)
                            .noSave()
                            .fireImmune()
                            .clientTrackingRange(16)
                            .updateInterval(1)
                            .build(new ResourceLocation(SourceOfMystery.MOD_ID, "abyss_orb").toString()));

    public static final RegistryObject<EntityType<WeepingDeathLord>> WEEPING_DEATH_LORD =
            ENTITIES.register("weeping_death_lord",
                    () -> EntityType.Builder.of(WeepingDeathLord::new, MobCategory.MONSTER)
                            .sized(1.4f, 3.6f)
                            .fireImmune()
                            .clientTrackingRange(16)
                            .updateInterval(1)
                            .build(new ResourceLocation(SourceOfMystery.MOD_ID, "weeping_death_lord").toString()));

    public static final RegistryObject<EntityType<WitherHusk>> WITHER_HUSK =
            ENTITIES.register("wither_husk",
                    () -> EntityType.Builder.<WitherHusk>of(WitherHusk::new, MobCategory.MISC)
                            .sized(0.9f, 3.5f)
                            .noSave()
                            .fireImmune()
                            .clientTrackingRange(16)
                            .updateInterval(20)
                            .build(new ResourceLocation(SourceOfMystery.MOD_ID, "wither_husk").toString()));

    public static final RegistryObject<EntityType<ThrownScythe>> THROWN_SCYTHE =
            ENTITIES.register("thrown_scythe",
                    () -> EntityType.Builder.<ThrownScythe>of(ThrownScythe::new, MobCategory.MISC)
                            .sized(1.2f, 1.2f)
                            .noSave()
                            .fireImmune()
                            .clientTrackingRange(8)
                            .updateInterval(1)
                            .build(new ResourceLocation(SourceOfMystery.MOD_ID, "thrown_scythe").toString()));

    public static final RegistryObject<EntityType<DeathSkull>> DEATH_SKULL =
            ENTITIES.register("death_skull",
                    () -> EntityType.Builder.<DeathSkull>of(DeathSkull::new, MobCategory.MISC)
                            .sized(0.3125f, 0.3125f)
                            .noSave()
                            .clientTrackingRange(4)
                            .updateInterval(1)
                            .build(new ResourceLocation(SourceOfMystery.MOD_ID, "death_skull").toString()));

    public static final RegistryObject<EntityType<SpaceShatter>> SPACE_SHATTER =
            ENTITIES.register("space_shatter",
                    () -> EntityType.Builder.<SpaceShatter>of(SpaceShatter::new, MobCategory.MISC)
                            .sized(0.5f, 0.5f)
                            .noSave()
                            .fireImmune()
                            .clientTrackingRange(16)
                            .updateInterval(20)
                            .build(new ResourceLocation(SourceOfMystery.MOD_ID, "space_shatter").toString()));

    // 守护之灵：友好阵营，按村庄登记刷新，使用独立 GeckoLib 模型与动画。
    public static final RegistryObject<EntityType<GuardianSpirit>> GUARDIAN_SPIRIT =
            ENTITIES.register("guardian_spirit",
                    () -> EntityType.Builder.of(GuardianSpirit::new, MobCategory.CREATURE)
                            .sized(0.6F, 1.95F)  // 与村民一样大
                            .clientTrackingRange(64)
                            .updateInterval(1)
                            .build(new ResourceLocation(SourceOfMystery.MOD_ID, "guardian_spirit").toString()));

    // 守护之灵的投射物（蓝球追踪弹）
    public static final RegistryObject<EntityType<GuardianSpiritProjectile>> GUARDIAN_SPIRIT_PROJECTILE =
            ENTITIES.register("guardian_spirit_projectile",
                    () -> EntityType.Builder.<GuardianSpiritProjectile>of(GuardianSpiritProjectile::new, MobCategory.MISC)
                            .sized(0.3F, 0.3F)
                            .noSave()
                            .fireImmune()
                            .clientTrackingRange(64)
                            .updateInterval(1)
                            .build(new ResourceLocation(SourceOfMystery.MOD_ID, "guardian_spirit_projectile").toString()));

    public static final RegistryObject<EntityType<OutsiderSpell>> OUTSIDER_SPELL =
            ENTITIES.register("outsider_spell", () -> EntityType.Builder.<OutsiderSpell>of(OutsiderSpell::new, MobCategory.MISC)
                    .sized(.4F,.4F).noSave().fireImmune().clientTrackingRange(24).updateInterval(1)
                    .build(new ResourceLocation(SourceOfMystery.MOD_ID,"outsider_spell").toString()));

    // 手动召唤的飞行 BOSS；不登记任何自然生成规则。
    public static final RegistryObject<EntityType<OutsiderEntity>> OUTSIDER =
            ENTITIES.register("outsider",
                    () -> EntityType.Builder.of(OutsiderEntity::new, MobCategory.MONSTER)
                            // Match the vanilla Wither height while leaving
                            // enough width for the model's wings, hair and
                            // staff without making the hitbox absurdly wide.
                            .sized(1.25F, 3.5F)
                            .clientTrackingRange(32)
                            .updateInterval(1)
                            .build(new ResourceLocation(SourceOfMystery.MOD_ID, "outsider").toString()));

    public static void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(DIVINE_HEAVENLY_DAO_BOSS.get(), DivineHeavenlyDaoBoss.createAttributes().build());
        event.put(DRAGON_SOUL_BOSS.get(), DragonSoulBoss.createAttributes().build());
        event.put(GIANT_WITHER.get(), WitherBoss.createAttributes().build());
        event.put(WEEPING_DEATH_LORD.get(), WeepingDeathLord.createAttributes().build());
        event.put(GUARDIAN_SPIRIT.get(), GuardianSpirit.createAttributes().build());
        event.put(OUTSIDER.get(), OutsiderEntity.createAttributes().build());
        // 投射物没有自己的属性表，复用 snowball 默认的（MobCategory.MISC 会自动用 Projectile 默认）
    }
}
