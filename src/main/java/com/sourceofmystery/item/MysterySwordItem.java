package com.sourceofmystery.item;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;

/**
 * 神秘系列剑的基类：无限耐久，子类通过 {@link #onHit} 实现命中特效。
 */
public class MysterySwordItem extends SwordItem {

    public MysterySwordItem(Tier tier, int attackDamageBonus, float attackSpeedBonus, Rarity rarity) {
        super(tier, attackDamageBonus, attackSpeedBonus, new Item.Properties().rarity(rarity));
    }

    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        boolean result = super.hurtEnemy(stack, target, attacker);
        if (result && !target.level().isClientSide) {
            onHit(stack, target, attacker);
        }
        return result;
    }

    /**
     * 命中目标后的额外效果（仅服务端）
     */
    protected void onHit(ItemStack stack, LivingEntity target, LivingEntity attacker) {
    }

    /**
     * 无限耐久：物品本身保留正常的耐久值（因此可以附魔），但永远不会损耗
     */
    @Override
    public boolean isDamageable(ItemStack stack) {
        return false;
    }

    /**
     * 累计着火时间（再次命中时叠加，而非重置）
     */
    protected static void addFireTicks(LivingEntity target, int ticks) {
        target.setRemainingFireTicks(target.getRemainingFireTicks() + ticks);
    }

    /**
     * 累计药水效果持续时间，等级取已有效果与新效果中较高者
     */
    protected static void stackEffect(LivingEntity target, MobEffect effect, int additionalDuration, int amplifier) {
        MobEffectInstance existing = target.getEffect(effect);
        int duration = additionalDuration;
        if (existing != null) {
            duration += existing.getDuration();
            amplifier = Math.max(amplifier, existing.getAmplifier());
        }
        target.removeEffect(effect);
        target.addEffect(new MobEffectInstance(effect, duration, amplifier, false, true, true));
    }
}
