package com.sourceofmystery.item;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;

/**
 * 灵源秘剑特效：
 * - 命中目标：燃烧10秒
 * - 燃烧时间必须累计（不能刷新）
 */
public class SpiritSourceSwordItem extends SwordItem {

    private static final int BURN_DURATION = 200; // 10秒 (20 ticks/秒)

    public SpiritSourceSwordItem(Tier tier, int attackDamageBonus, float attackSpeedBonus) {
        super(tier, attackDamageBonus, attackSpeedBonus,
                new net.minecraft.world.item.Item.Properties()
                        .rarity(net.minecraft.world.item.Rarity.RARE)
                        .durability(-1)); // 耐久无限
    }

    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        boolean result = super.hurtEnemy(stack, target, attacker);

        if (result) {
            // 燃烧10秒：累计着火时间（设计：再次攻击时着火时间叠加，而非重置）
            int newFireTicks = target.getRemainingFireTicks() + BURN_DURATION;
            target.setRemainingFireTicks(newFireTicks);
        }

        return result;
    }

    @Override
    public boolean isEnchantable(ItemStack stack) {
        return true;
    }

    @Override
    public int getEnchantmentValue() {
        return 20;
    }
}
