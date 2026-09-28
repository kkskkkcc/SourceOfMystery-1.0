package com.sourceofmystery.item;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;

/**
 * 暗源之剑特效：
 * - 命中目标：燃烧10秒 + 凋零10秒
 * - 两个效果都必须支持持续时间累计
 */
public class DarkSourceSwordItem extends SwordItem {

    private static final int EFFECT_DURATION = 200; // 10秒 (20 ticks/秒)

    public DarkSourceSwordItem(Tier tier, int attackDamageBonus, float attackSpeedBonus) {
        super(tier, attackDamageBonus, attackSpeedBonus,
                new net.minecraft.world.item.Item.Properties()
                        .rarity(net.minecraft.world.item.Rarity.EPIC)
                        .durability(-1)); // 耐久无限
    }

    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        boolean result = super.hurtEnemy(stack, target, attacker);

        if (result) {
            // 燃烧：累计着火时间
            target.setRemainingFireTicks(target.getRemainingFireTicks() + EFFECT_DURATION);

            // 处理凋零效果 (WITHER)
            accumulateEffect(target, MobEffects.WITHER, EFFECT_DURATION);
        }

        return result;
    }

    /**
     * 累计效果持续时间
     */
    private void accumulateEffect(LivingEntity target, net.minecraft.world.effect.MobEffect effect, int additionalDuration) {
        MobEffectInstance existing = target.getEffect(effect);
        int currentDuration = 0;

        if (existing != null) {
            currentDuration = existing.getDuration();
        }

        int newDuration = currentDuration + additionalDuration;

        target.removeEffect(effect);
        target.addEffect(new MobEffectInstance(effect, newDuration, 0, false, true, true));
    }

    @Override
    public boolean isEnchantable(ItemStack stack) {
        return true;
    }

    @Override
    public int getEnchantmentValue() {
        return 25;
    }
}
