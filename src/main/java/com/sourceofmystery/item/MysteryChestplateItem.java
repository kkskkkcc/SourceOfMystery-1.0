package com.sourceofmystery.item;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * 神秘系列胸甲。
 * <ul>
 *     <li>生命加成作为物品自带的属性修饰符，穿上 / 脱下时由原版自动添加 / 移除；</li>
 *     <li>可选"无限耐久"：不再使用 durability(-1) 的取巧写法，而是声明物品不可损坏。</li>
 * </ul>
 * 其余套装效果见 {@link ChestplateEffectHandler}。
 */
public class MysteryChestplateItem extends ArmorItem {

    private final boolean unbreakable;
    @Nullable
    private final AttributeModifier healthModifier;

    public MysteryChestplateItem(ArmorMaterial material, Properties properties, boolean unbreakable) {
        this(material, properties, unbreakable, null, 0);
    }

    public MysteryChestplateItem(ArmorMaterial material, Properties properties, boolean unbreakable,
                                 @Nullable UUID healthModifierId, double healthBonus) {
        super(material, Type.CHESTPLATE, properties);
        this.unbreakable = unbreakable;
        this.healthModifier = healthModifierId == null || healthBonus <= 0 ? null
                : new AttributeModifier(healthModifierId, "Chestplate health bonus", healthBonus, AttributeModifier.Operation.ADDITION);
    }

    @Override
    public Multimap<Attribute, AttributeModifier> getAttributeModifiers(EquipmentSlot slot, ItemStack stack) {
        Multimap<Attribute, AttributeModifier> modifiers = super.getAttributeModifiers(slot, stack);
        if (healthModifier == null || slot != getEquipmentSlot()) {
            return modifiers;
        }
        return ImmutableMultimap.<Attribute, AttributeModifier>builder()
                .putAll(modifiers)
                .put(Attributes.MAX_HEALTH, healthModifier)
                .build();
    }

    @Override
    public boolean isDamageable(ItemStack stack) {
        return !unbreakable && super.isDamageable(stack);
    }
}
