package com.sourceofmystery.item;

import com.sourceofmystery.config.MysteryConfig;
import com.sourceofmystery.energy.MysteryEnergy;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.Tier;

/**
 * 神威天罚特效：
 * - 命中效果（均累计 10 秒）：燃烧 + 凋零 + 高亮 + 虚弱 III + 缓慢 III
 * - 正常攻击无法杀死目标时，消耗神秘之能（默认 1000）追加无视护甲的真实伤害（默认 100），数值见配置文件
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

        long energyCost = MysteryConfig.DIVINE_PUNISHMENT_ENERGY_COST.get();
        if (!(attacker instanceof Player player) || !target.isAlive()
                || MysteryEnergy.getEnergy(player) < energyCost) {
            return;
        }

        // 刚刚的普通攻击让目标进入了受击无敌时间，期间更低的伤害会被直接忽略，
        // 不清零的话真实伤害永远打不出来，能量却白白扣掉
        target.invulnerableTime = 0;
        // indirectMagic：magic 类型无视护甲，且归属玩家（触发击杀 +10 神秘之能、击杀 Boss 成就）
        float trueDamage = MysteryConfig.DIVINE_PUNISHMENT_TRUE_DAMAGE.get().floatValue();
        if (target.hurt(target.level().damageSources().indirectMagic(player, player), trueDamage)) {
            MysteryEnergy.consumeEnergy(player, energyCost);
            spawnDivineEffect((ServerLevel) target.level(), target);
        }
    }

    private static void spawnDivineEffect(ServerLevel level, LivingEntity target) {
        spawnParticleCloud(level, ParticleTypes.ENCHANT, target, 30, 3.0, 0.05);
        spawnParticleCloud(level, ParticleTypes.FIREWORK, target, 20, 2.0, 0.02);
        spawnParticleCloud(level, ParticleTypes.WITCH, target, 15, 2.0, 0.02);
    }

    private static void spawnParticleCloud(ServerLevel level, ParticleOptions particle, LivingEntity target,
                                           int count, double size, double speed) {
        for (int i = 0; i < count; i++) {
            level.sendParticles(particle,
                    target.getX() + (level.random.nextDouble() - 0.5) * size,
                    target.getY() + level.random.nextDouble() * size,
                    target.getZ() + (level.random.nextDouble() - 0.5) * size,
                    1, 0, 0, 0, speed);
        }
    }
}
