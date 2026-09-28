package com.sourceofmystery.item;

import net.minecraft.world.item.Tier;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;

public class MysteryTier implements Tier {
    private final int level;
    private final int durability;
    private final float speed;
    private final float damage;
    private final int enchantmentValue;
    private final Ingredient repairIngredient;

    MysteryTier(int level, int durability, float speed, float damage, int enchantmentValue, Ingredient repairIngredient) {
        this.level = level;
        this.durability = durability;
        this.speed = speed;
        this.damage = damage;
        this.enchantmentValue = enchantmentValue;
        this.repairIngredient = repairIngredient;
    }

    @Override public int getUses() { return durability; }
    @Override public float getSpeed() { return speed; }
    @Override public float getAttackDamageBonus() { return damage; }
    @Override public int getLevel() { return level; }
    @Override public int getEnchantmentValue() { return enchantmentValue; }
    @Override public Ingredient getRepairIngredient() { return repairIngredient; }

    // 预定义的等级；各把剑的最终伤害见 ModItems 中的注释
    public static final MysteryTier MYSTERY = new MysteryTier(4, 1500, 8.0f, 4.0f, 15, Ingredient.of(Items.DIAMOND));
    public static final MysteryTier SPIRIT_SOURCE = new MysteryTier(5, 2500, 9.0f, 10.0f, 20, Ingredient.of(Items.DIAMOND));
    public static final MysteryTier DARK_SOURCE = new MysteryTier(6, 4000, 10.0f, 40.0f, 25, Ingredient.of(Items.NETHERITE_INGOT));
    public static final MysteryTier ORIGIN_DRAGON = new MysteryTier(7, 8000, 12.0f, 90.0f, 30, Ingredient.of(Items.NETHERITE_INGOT));
    public static final MysteryTier DIVINE_PUNISHMENT = new MysteryTier(8, 15000, 15.0f, 210.0f, 35, Ingredient.of(Items.NETHERITE_INGOT));
}
