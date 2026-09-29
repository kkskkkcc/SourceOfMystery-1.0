package com.sourceofmystery.item;

import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.Tier;

/**
 * 暗源之剑特效：命中目标燃烧 10 秒 + 凋零 10 秒（均累计）
 */
public class DarkSourceSwordItem extends MysterySwordItem {

    private static final int EFFECT_DURATION = 200; // 10秒

    public DarkSourceSwordItem(Tier tier, int attackDamageBonus, float attackSpeedBonus) {
        super(tier, attackDamageBonus, attackSpeedBonus, Rarity.EPIC);
    }

    @Override
    protected void onHit(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        addFireTicks(target, EFFECT_DURATION);
        stackEffect(target, MobEffects.WITHER, EFFECT_DURATION, 0);
    }
}
