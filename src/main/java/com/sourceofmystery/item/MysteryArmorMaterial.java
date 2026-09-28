package com.sourceofmystery.item;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;

public class MysteryArmorMaterial implements ArmorMaterial {
    private final String name;
    private final int durability;
    private final int[] protection;
    private final int enchantmentValue;
    private final SoundEvent sound;
    private final float toughness;
    private final float knockbackResistance;
    private final Ingredient repairIngredient;

    MysteryArmorMaterial(String name, int durability, int[] protection, int enchantmentValue,
                         SoundEvent sound, float toughness, float knockbackResistance,
                         Ingredient repairIngredient) {
        this.name = name;
        this.durability = durability;
        this.protection = protection;
        this.enchantmentValue = enchantmentValue;
        this.sound = sound;
        this.toughness = toughness;
        this.knockbackResistance = knockbackResistance;
        this.repairIngredient = repairIngredient;
    }

    @Override public int getDurabilityForType(ArmorItem.Type type) { return durability; }
    @Override
    public int getDefenseForType(ArmorItem.Type type) {
        return switch (type) {
            case BOOTS -> protection[0];
            case LEGGINGS -> protection[1];
            case CHESTPLATE -> protection[2];
            case HELMET -> protection[3];
        };
    }
    @Override public int getEnchantmentValue() { return enchantmentValue; }
    @Override public SoundEvent getEquipSound() { return sound; }
    @Override public Ingredient getRepairIngredient() { return repairIngredient; }
    @Override public String getName() { return name; }
    @Override public float getToughness() { return toughness; }
    @Override public float getKnockbackResistance() { return knockbackResistance; }

    // 预定义的材料（套装效果通过 == 比较这些常量识别，不要依赖 getName）
    // protection数组: [boots, leggings, chestplate, helmet]
    // getName 复用原版护甲材质名，避免自定义贴图缺失导致穿甲时模型变黑
    // 秘源甲: 胸甲护甲值 10
    public static final MysteryArmorMaterial MYSTERY = new MysteryArmorMaterial(
            "iron", 30, new int[]{4, 6, 10, 3}, 20, SoundEvents.ARMOR_EQUIP_GENERIC, 2.0f, 0.1f, Ingredient.of(Items.DIAMOND));
    // 灵源秘甲: 胸甲护甲值 20
    public static final MysteryArmorMaterial SPIRIT_SOURCE = new MysteryArmorMaterial(
            "gold", 35, new int[]{6, 10, 20, 5}, 25, SoundEvents.ARMOR_EQUIP_GENERIC, 2.5f, 0.15f, Ingredient.of(Items.DIAMOND));
    // 暗源之甲: 胸甲护甲值 60
    public static final MysteryArmorMaterial DARK_SOURCE = new MysteryArmorMaterial(
            "netherite", 40, new int[]{8, 15, 60, 7}, 30, SoundEvents.ARMOR_EQUIP_GENERIC, 3.0f, 0.2f, Ingredient.of(Items.NETHERITE_INGOT));
    // 始源龙甲: 胸甲护甲值 100
    public static final MysteryArmorMaterial ORIGIN_DRAGON = new MysteryArmorMaterial(
            "diamond", 50, new int[]{12, 25, 100, 10}, 40, SoundEvents.ARMOR_EQUIP_NETHERITE, 4.0f, 0.3f, Ingredient.of(Items.NETHERITE_INGOT));
    // 神威天佑: 胸甲护甲值 300，击退抗性 1.0（完全无视击退）
    public static final MysteryArmorMaterial DIVINE_BLESSING = new MysteryArmorMaterial(
            "netherite", 75, new int[]{20, 40, 300, 15}, 50, SoundEvents.ARMOR_EQUIP_NETHERITE, 5.0f, 1.0f, Ingredient.of(Items.NETHERITE_INGOT));
}
