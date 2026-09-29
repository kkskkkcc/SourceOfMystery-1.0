package com.sourceofmystery.item;

import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.Tier;

/**
 * 始源龙剑特效（均累计 10 秒）：燃烧 + 凋零 + 高亮 + 虚弱 III
 */
public class OriginDragonSwordItem extends MysterySwordItem {

    private static final int EFFECT_DURATION = 200; // 10秒
    private static final int WEAKNESS_AMPLIFIER = 2; // 虚弱 III

    public OriginDragonSwordItem(Tier tier, int attackDamageBonus, float attackSpeedBonus) {
        super(tier, attackDamageBonus, attackSpeedBonus, Rarity.EPIC);
    }

    @Override
    protected void onHit(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        addFireTicks(target, EFFECT_DURATION);
        stackEffect(target, MobEffects.WITHER, EFFECT_DURATION, 0);
        stackEffect(target, MobEffects.GLOWING, EFFECT_DURATION, 0);
        stackEffect(target, MobEffects.WEAKNESS, EFFECT_DURATION, WEAKNESS_AMPLIFIER);
    }
}
