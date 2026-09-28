package com.sourceofmystery.item;

import com.sourceofmystery.capability.energy.MysteryEnergyCapability;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.level.Level;

/**
 * 神威天罚特效：
 * - 攻击伤害：100
 * - 攻击速度：3.0
 * - 耐久：无限
 * - 命中效果：燃烧 + 凋零 + 高亮 + 虚弱III + 缓慢III
 * - 特殊：正常攻击无法杀死目标时，消耗1000神秘之能，造成100点无视护甲真实伤害
 */
public class DivinePunishmentSwordItem extends SwordItem {

    private static final int BURN_DURATION = 200; // 10秒
    private static final int WITHER_DURATION = 200; // 10秒
    private static final int GLOWING_DURATION = 200; // 10秒
    private static final int WEAKNESS_DURATION = 200; // 10秒
    private static final int SLOWNESS_DURATION = 200; // 10秒
    private static final int TRUE_DAMAGE = 100; // 真实伤害
    private static final long ENERGY_COST = 1000; // 神秘之能消耗

    public DivinePunishmentSwordItem(Tier tier, int attackDamageBonus, float attackSpeedBonus) {
        super(tier, attackDamageBonus, attackSpeedBonus,
                new net.minecraft.world.item.Item.Properties()
                        .rarity(net.minecraft.world.item.Rarity.EPIC)
                        .durability(-1)); // 无限耐久
    }

    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        boolean result = super.hurtEnemy(stack, target, attacker);

        if (result && attacker instanceof Player player) {
            Level level = target.level();

            // 应用所有效果
            // 燃烧：累计着火时间
            target.setRemainingFireTicks(target.getRemainingFireTicks() + BURN_DURATION);
            // 凋零 (WITHER)
            accumulateEffect(target, MobEffects.WITHER, WITHER_DURATION);
            // 高亮 (GLOWING)
            accumulateEffect(target, MobEffects.GLOWING, GLOWING_DURATION);
            // 虚弱 III (WEAKNESS - amplifier 2 = level 3)
            accumulateEffectWithLevel(target, MobEffects.WEAKNESS, WEAKNESS_DURATION, 2);
            // 缓慢 III (SLOWNESS - amplifier 2 = level 3)
            accumulateEffectWithLevel(target, MobEffects.MOVEMENT_SLOWDOWN, SLOWNESS_DURATION, 2);

            // 检查是否需要真实伤害
            // 如果目标在正常攻击后还活着，说明无法杀死
            if (target.isAlive()) {
                // 消耗神秘之能
                if (MysteryEnergyCapability.consumeEnergy(player, ENERGY_COST)) {
                    // 造成100点无视护甲的真实伤害
                    // 使用 indirectMagic 伤害源：magic 类型无视护甲，且归属玩家（触发击杀+10神秘之能、击杀Boss成就）
                    target.hurt(level.damageSources().indirectMagic(player, player), TRUE_DAMAGE);

                    // 播放特效
                    spawnDivineEffect(level, target);
                }
            }
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
        int maxAmplifier = Math.max(existingAmplifier, amplifier);

        target.removeEffect(effect);
        target.addEffect(new MobEffectInstance(effect, newDuration, maxAmplifier, false, true, true));
    }

    /**
     * 播放神圣特效
     */
    private void spawnDivineEffect(Level level, Entity target) {
        if (level.isClientSide) {
            return;
        }

        net.minecraft.server.level.ServerLevel serverLevel = (net.minecraft.server.level.ServerLevel) level;
        double x = target.getX();
        double y = target.getY();
        double z = target.getZ();

        // 生成神圣粒子效果
        for (int i = 0; i < 30; i++) {
            double offsetX = (level.random.nextDouble() - 0.5) * 3;
            double offsetY = level.random.nextDouble() * 3;
            double offsetZ = (level.random.nextDouble() - 0.5) * 3;

            serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.ENCHANT,
                    x + offsetX, y + offsetY, z + offsetZ,
                    1, 0, 0, 0, 0.05);
        }

        for (int i = 0; i < 20; i++) {
            double offsetX = (level.random.nextDouble() - 0.5) * 2;
            double offsetY = level.random.nextDouble() * 2;
            double offsetZ = (level.random.nextDouble() - 0.5) * 2;

            serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.FIREWORK,
                    x + offsetX, y + offsetY, z + offsetZ,
                    1, 0, 0, 0, 0.02);
        }

        for (int i = 0; i < 15; i++) {
            double offsetX = (level.random.nextDouble() - 0.5) * 2;
            double offsetY = level.random.nextDouble() * 2;
            double offsetZ = (level.random.nextDouble() - 0.5) * 2;

            serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.WITCH,
                    x + offsetX, y + offsetY, z + offsetZ,
                    1, 0, 0, 0, 0.02);
        }
    }

    @Override
    public boolean isEnchantable(ItemStack stack) {
        return true;
    }

    @Override
    public int getEnchantmentValue() {
        return 35;
    }

    @Override
    public boolean isDamaged(ItemStack stack) {
        return false; // 无限耐久
    }

    @Override
    public int getDamage(ItemStack stack) {
        return 0; // 无限耐久
    }

    @Override
    public void setDamage(ItemStack stack, int damage) {
        // 什么都不做 - 无限耐久
    }
}
