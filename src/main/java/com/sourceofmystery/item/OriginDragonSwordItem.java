package com.sourceofmystery.item;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;

/**
 * 始源龙剑特效：
 * - 燃烧累计
 * - 凋零累计
 * - 目标高亮 (Glowing)
 * - 目标虚弱 III
 */
public class OriginDragonSwordItem extends SwordItem {

    private static final int BURN_DURATION = 200; // 10秒
    private static final int WITHER_DURATION = 200; // 10秒
    private static final int GLOWING_DURATION = 200; // 10秒
    private static final int WEAKNESS_LEVEL = 2; // 虚弱 III (0=level 1, 1=level 2, 2=level 3)
    private static final int WEAKNESS_DURATION = 200; // 10秒

    public OriginDragonSwordItem(Tier tier, int attackDamageBonus, float attackSpeedBonus) {
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
            target.setRemainingFireTicks(target.getRemainingFireTicks() + BURN_DURATION);

            // 凋零累计 (WITHER)
            accumulateEffect(target, MobEffects.WITHER, WITHER_DURATION);

            // 高亮 (GLOWING) - 累计
            accumulateEffect(target, MobEffects.GLOWING, GLOWING_DURATION);

            // 虚弱 III (WEAKNESS) - 累计
            accumulateEffectWithLevel(target, MobEffects.WEAKNESS, WEAKNESS_DURATION, WEAKNESS_LEVEL);
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

    /**
     * 累计效果持续时间（带等级）
     */
    private void accumulateEffectWithLevel(LivingEntity target, net.minecraft.world.effect.MobEffect effect, int additionalDuration, int amplifier) {
        MobEffectInstance existing = target.getEffect(effect);
        int currentDuration = 0;
        int existingAmplifier = 0;

        if (existing != null) {
            currentDuration = existing.getDuration();
            existingAmplifier = existing.getAmplifier();
        }

        int newDuration = currentDuration + additionalDuration;
        // 使用最高等级
        int maxAmplifier = Math.max(existingAmplifier, amplifier);

        target.removeEffect(effect);
        target.addEffect(new MobEffectInstance(effect, newDuration, maxAmplifier, false, true, true));
    }

    @Override
    public boolean isEnchantable(ItemStack stack) {
        return true;
    }

    @Override
    public int getEnchantmentValue() {
        return 30;
    }
}
