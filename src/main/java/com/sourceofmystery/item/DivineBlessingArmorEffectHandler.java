package com.sourceofmystery.item;

import com.sourceofmystery.capability.energy.MysteryEnergyCapability;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = com.sourceofmystery.SourceOfMystery.MOD_ID)
public class DivineBlessingArmorEffectHandler {

    private static final int EFFECT_INTERVAL = 20; // 每秒触发一次
    private static final long ENERGY_COST = 1000; // 消耗能量
    // 生命加成：神威天佑 +200
    private static final UUID HEALTH_BOOST_ID = UUID.fromString("6b4f2e3f-5c7d-8e9f-0b1c-2d3e4f5a6b7c");
    private static final double HEALTH_BONUS = 200.0;
    // 记录正在飞行的玩家（由本护甲赋予飞行），避免每 tick 重复设置干扰飞行
    private static final Set<UUID> FLYING_PLAYERS = new HashSet<>();

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        Player player = event.player;
        Level level = player.getCommandSenderWorld();

        // 只在服务器端处理
        if (level.isClientSide) {
            return;
        }

        boolean wearing = isWearingDivineBlessingChestplate(player);
        UUID playerId = player.getUUID();

        // ==================== 生命加成：穿甲加血，脱甲移除 ====================
        net.minecraft.world.entity.ai.attributes.AttributeInstance healthAttr = player.getAttribute(Attributes.MAX_HEALTH);
        if (healthAttr != null) {
            if (wearing) {
                if (healthAttr.getModifier(HEALTH_BOOST_ID) == null) {
                    healthAttr.addPermanentModifier(
                            new AttributeModifier(HEALTH_BOOST_ID, "Divine Blessing Health Boost", HEALTH_BONUS, AttributeModifier.Operation.ADDITION));
                }
            } else {
                if (healthAttr.getModifier(HEALTH_BOOST_ID) != null) {
                    healthAttr.removeModifier(HEALTH_BOOST_ID);
                    // 移除生命加成后，把当前生命 clamp 到新上限，避免脱甲后血条残留
                    if (player.getHealth() > player.getMaxHealth()) {
                        player.setHealth(player.getMaxHealth());
                    }
                }
            }
        }

        // ==================== 飞行：只在穿戴状态变化时设置，避免每 tick 重置 ====================
        if (wearing) {
            if (!FLYING_PLAYERS.contains(playerId)) {
                // 刚穿上：赋予飞行
                player.getAbilities().mayfly = true;
                player.onUpdateAbilities();
                FLYING_PLAYERS.add(playerId);
            }
        } else {
            if (FLYING_PLAYERS.contains(playerId)) {
                // 刚脱下：收回飞行（非创造/旁观模式）
                if (!player.isCreative() && !player.isSpectator()) {
                    player.getAbilities().mayfly = false;
                    player.getAbilities().flying = false;
                    player.onUpdateAbilities();
                }
                FLYING_PLAYERS.remove(playerId);
            }
            return;
        }

        // ==================== 以下效果每秒刷新一次 ====================
        if (level.getGameTime() % EFFECT_INTERVAL != 0) {
            return;
        }

        // 无视火焰：清除着火状态
        player.clearFire();
        // 无视凋零：移除凋零效果
        player.removeEffect(MobEffects.WITHER);
        // 免疫所有负面 Buff
        removeHarmfulEffects(player);
        // 水下呼吸
        player.addEffect(new MobEffectInstance(MobEffects.WATER_BREATHING, 40, 0, false, false));
        // 跳跃提升
        player.addEffect(new MobEffectInstance(MobEffects.JUMP, 40, 0, false, false));
        // 夜视
        player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 40, 0, false, false));
        // 急迫
        player.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, 40, 0, false, false));
        // 力量 III (amplifier 2 = level 3)
        player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 40, 2, false, false));
    }

    /**
     * 伤害处理：先判断免疫类型（直接取消，不耗能量），否则用 1000 能量完全抵挡一次伤害
     */
    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        if (event.getEntity() instanceof Player player && isWearingDivineBlessingChestplate(player)) {
            // 免疫的伤害类型：直接取消，不消耗能量
            if (isImmuneDamageSource(event.getSource())) {
                event.setCanceled(true);
                return;
            }

            // 其他伤害：消耗 1000 能量完全抵挡一次
            if (MysteryEnergyCapability.getEnergy(player) >= ENERGY_COST) {
                MysteryEnergyCapability.consumeEnergy(player, ENERGY_COST);
                event.setCanceled(true);
                spawnDivineProtectionEffect(player);
            }
        }
    }

    private static boolean isImmuneDamageSource(net.minecraft.world.damagesource.DamageSource source) {
        // 火焰
        if (source.is(DamageTypeTags.IS_FIRE)) return true;
        // 龙息
        if (source.is(DamageTypes.DRAGON_BREATH)) return true;
        // 弹射物
        if (source.is(DamageTypeTags.IS_PROJECTILE)) return true;
        // 凋零
        return source.is(DamageTypes.WITHER) || source.is(DamageTypes.WITHER_SKULL);
    }

    /**
     * 免疫所有负面 Buff：移除所有负面（HARMFUL）效果
     */
    private static void removeHarmfulEffects(Player player) {
        List<MobEffect> toRemove = new ArrayList<>();
        for (MobEffectInstance activeEffect : player.getActiveEffects()) {
            if (activeEffect.getEffect().getCategory() == MobEffectCategory.HARMFUL) {
                toRemove.add(activeEffect.getEffect());
            }
        }
        for (MobEffect effect : toRemove) {
            player.removeEffect(effect);
        }
    }

    /**
     * 播放神圣保护特效
     */
    private static void spawnDivineProtectionEffect(Player player) {
        Level level = player.level();
        if (level.isClientSide) {
            return;
        }

        net.minecraft.server.level.ServerLevel serverLevel = (net.minecraft.server.level.ServerLevel) level;
        double x = player.getX();
        double y = player.getY();
        double z = player.getZ();

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

            serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.WITCH,
                    x + offsetX, y + offsetY, z + offsetZ,
                    1, 0, 0, 0, 0.02);
        }

        for (int i = 0; i < 15; i++) {
            double offsetX = (level.random.nextDouble() - 0.5) * 2;
            double offsetY = level.random.nextDouble() * 2;
            double offsetZ = (level.random.nextDouble() - 0.5) * 2;

            serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.FIREWORK,
                    x + offsetX, y + offsetY, z + offsetZ,
                    1, 0, 0, 0, 0.02);
        }
    }

    private static boolean isWearingDivineBlessingChestplate(Player player) {
        ItemStack chestplate = player.getItemBySlot(EquipmentSlot.CHEST);
        return chestplate.getItem() instanceof ArmorItem armorItem &&
               armorItem.getMaterial() == MysteryArmorMaterial.DIVINE_BLESSING;
    }
}
