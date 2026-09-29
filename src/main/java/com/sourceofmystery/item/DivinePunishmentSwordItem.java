package com.sourceofmystery.item;

import com.sourceofmystery.config.MysteryConfig;
import com.sourceofmystery.energy.MysteryEnergy;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.Tier;

/**
 * 神威天罚特效：
 * - 命中效果（均累计 10 秒）：燃烧 + 凋零 + 高亮 + 虚弱 III + 缓慢 III
 * - 正常攻击无法杀死目标时，消耗神秘之能（默认 100）召唤天雷：目标脚下出现法阵，
 *   1 秒后落下闪电并造成无视护甲的真实伤害（默认 200），见 {@link DivineStrikeScheduler}
 */
public class DivinePunishmentSwordItem extends MysterySwordItem {

    private static final int EFFECT_DURATION = 200; // 10秒
    private static final int DEBUFF_AMPLIFIER = 2; // III 级

    public DivinePunishmentSwordItem(Tier tier, int attackDamageBonus, float attackSpeedBonus) {
        super(tier, attackDamageBonus, attackSpeedBonus, Rarity.EPIC);
    }

    @Override
    protected void onHit(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        addFireTicks(target, EFFECT_DURATION);
        stackEffect(target, MobEffects.WITHER, EFFECT_DURATION, 0);
        stackEffect(target, MobEffects.GLOWING, EFFECT_DURATION, 0);
        stackEffect(target, MobEffects.WEAKNESS, EFFECT_DURATION, DEBUFF_AMPLIFIER);
        stackEffect(target, MobEffects.MOVEMENT_SLOWDOWN, EFFECT_DURATION, DEBUFF_AMPLIFIER);

        long energyCost = MysteryConfig.DIVINE_PUNISHMENT_STRIKE_COST.get();
        if (!(attacker instanceof ServerPlayer player) || !target.isAlive() || DivineStrikeScheduler.isPending(target)) {
            return;
        }
        if (energyCost > 0 && !MysteryEnergy.consumeEnergy(player, energyCost)) {
            return;
        }
        DivineStrikeScheduler.schedule(player, target, energyCost);
    }
}
