package com.sourceofmystery.item;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.Tier;

/**
 * 灵源秘剑特效：命中目标燃烧 10 秒（累计）
 */
public class SpiritSourceSwordItem extends MysterySwordItem {

    private static final int BURN_DURATION = 200; // 10秒

    public SpiritSourceSwordItem(Tier tier, int attackDamageBonus, float attackSpeedBonus) {
        super(tier, attackDamageBonus, attackSpeedBonus, Rarity.RARE);
    }

    @Override
    protected void onHit(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        addFireTicks(target, BURN_DURATION);
    }
}
