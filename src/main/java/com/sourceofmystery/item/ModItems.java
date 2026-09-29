package com.sourceofmystery.item;

import com.sourceofmystery.SourceOfMystery;
import com.sourceofmystery.block.ModBlocks;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.ArmorItem;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, SourceOfMystery.MOD_ID);

    // 秘源锭
    public static final RegistryObject<Item> MYSTERY_INGOT = ITEMS.register("mystery_ingot",
            () -> new Item(new Item.Properties().rarity(Rarity.EPIC).stacksTo(64)));

    // 天道碎片
    public static final RegistryObject<Item> HEAVENLY_DAO_FRAGMENT = ITEMS.register("heavenly_dao_fragment",
            () -> new Item(new Item.Properties().rarity(Rarity.RARE).stacksTo(16)));

    // 方块物品 - 神秘源矿
    public static final RegistryObject<Item> MYSTERY_SOURCE_ORE = ITEMS.register("mystery_source_ore",
            () -> new BlockItem(ModBlocks.MYSTERY_SOURCE_ORE.get(), new Item.Properties()));

    // 方块物品 - 神秘祭坛
    public static final RegistryObject<Item> MYSTERY_ALTAR = ITEMS.register("mystery_altar",
            () -> new BlockItem(ModBlocks.MYSTERY_ALTAR.get(), new Item.Properties()));

    // ==================== 武器 ====================
    // 武器伤害（物品栏显示值）= 玩家基础 1 + MysteryTier.attackDamageBonus + 构造参数 attackDamageBonus
    // 攻击速度 = 玩家基础 4.0 + 构造参数 attackSpeedBonus
    // MYSTERY:           1 + 4   + 3  = 8   伤害, 1.6 攻速
    // SPIRIT_SOURCE:     1 + 10  + 2  = 13  伤害, 1.8 攻速
    // DARK_SOURCE:       1 + 40  + 7  = 48  伤害, 2.0 攻速
    // ORIGIN_DRAGON:     1 + 90  + 7  = 98  伤害, 3.0 攻速
    // DIVINE_PUNISHMENT: 1 + 210 + 47 = 258 伤害, 3.0 攻速

    // 秘源剑 - 无特效
    public static final RegistryObject<Item> MYSTERY_SWORD = ITEMS.register("mystery_sword",
            () -> new MysterySwordItem(MysteryTier.MYSTERY, 3, -2.4f, Rarity.EPIC));

    // 灵源秘剑 - 燃烧10秒累计
    public static final RegistryObject<Item> SPIRIT_SOURCE_SWORD = ITEMS.register("spirit_source_sword",
            () -> new SpiritSourceSwordItem(MysteryTier.SPIRIT_SOURCE, 2, -2.2f));

    // 暗源之剑 - 燃烧+凋零10秒累计
    public static final RegistryObject<Item> DARK_SOURCE_SWORD = ITEMS.register("dark_source_sword",
            () -> new DarkSourceSwordItem(MysteryTier.DARK_SOURCE, 7, -2.0f));

    // 始源龙剑 - 燃烧+凋零+高亮+虚弱III
    public static final RegistryObject<Item> ORIGIN_DRAGON_SWORD = ITEMS.register("origin_dragon_sword",
            () -> new OriginDragonSwordItem(MysteryTier.ORIGIN_DRAGON, 7, -1.0f));

    // 神威天罚 - 全效果+真实伤害
    public static final RegistryObject<Item> DIVINE_PUNISHMENT_SWORD = ITEMS.register("divine_punishment_sword",
            () -> new DivinePunishmentSwordItem(MysteryTier.DIVINE_PUNISHMENT, 47, -1.0f));

    // ==================== 胸甲 ====================
    // 护甲值由 MysteryArmorMaterial 定义，套装效果见 ChestplateEffectHandler
    // 秘源甲: 10 护甲
    public static final RegistryObject<Item> MYSTERY_CHESTPLATE = ITEMS.register("mystery_chestplate",
            () -> new ArmorItem(MysteryArmorMaterial.MYSTERY, ArmorItem.Type.CHESTPLATE,
                    new Item.Properties().rarity(Rarity.EPIC).durability(600)));

    // 灵源秘甲: 20 护甲 + 火焰免疫 + 水下呼吸
    public static final RegistryObject<Item> SPIRIT_SOURCE_CHESTPLATE = ITEMS.register("spirit_source_chestplate",
            () -> new ArmorItem(MysteryArmorMaterial.SPIRIT_SOURCE, ArmorItem.Type.CHESTPLATE,
                    new Item.Properties().rarity(Rarity.RARE).durability(1000)));

    // 暗源之甲: 60 护甲 + 增益效果 + 火焰/凋零免疫 + 耐久无限
    public static final RegistryObject<Item> DARK_SOURCE_CHESTPLATE = ITEMS.register("dark_source_chestplate",
            () -> new ArmorItem(MysteryArmorMaterial.DARK_SOURCE, ArmorItem.Type.CHESTPLATE,
                    new Item.Properties().rarity(Rarity.EPIC).durability(-1)));

    // 始源龙甲: 100 护甲 + 生命加成100 + 创造飞行 + 全免疫 + 耐久无限
    public static final RegistryObject<Item> ORIGIN_DRAGON_CHESTPLATE = ITEMS.register("origin_dragon_chestplate",
            () -> new ArmorItem(MysteryArmorMaterial.ORIGIN_DRAGON, ArmorItem.Type.CHESTPLATE,
                    new Item.Properties().rarity(Rarity.EPIC).durability(-1)));

    // 神威天佑: 300 护甲 + 生命加成200 + 飞行 + 全免疫 + 伤害抵消 + 耐久无限
    public static final RegistryObject<Item> DIVINE_BLESSING_CHESTPLATE = ITEMS.register("divine_blessing_chestplate",
            () -> new ArmorItem(MysteryArmorMaterial.DIVINE_BLESSING, ArmorItem.Type.CHESTPLATE,
                    new Item.Properties().rarity(Rarity.EPIC).durability(-1)));
}
