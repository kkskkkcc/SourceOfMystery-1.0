package com.sourceofmystery.item;

import com.sourceofmystery.SourceOfMystery;
import com.sourceofmystery.capability.energy.MysteryEnergyCapability;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;

/**
 * 神秘系列胸甲的全部套装效果（原先分散在 5 个 handler 里）。
 * <ul>
 *     <li>秘源甲：无特效</li>
 *     <li>灵源秘甲：水下呼吸，免疫火焰</li>
 *     <li>暗源之甲：水下呼吸、跳跃、夜视、急迫、力量 III，免疫火焰/凋零</li>
 *     <li>始源龙甲：同上 + 生命上限 +100、飞行、清除负面效果，额外免疫龙息/弹射物</li>
 *     <li>神威天佑：同始源龙甲，生命上限 +200，其余伤害可消耗 1000 神秘之能完全抵挡</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = SourceOfMystery.MOD_ID)
public class ChestplateEffectHandler {

    private static final int EFFECT_INTERVAL = 20; // 每秒刷新一次效果
    private static final int EFFECT_DURATION = 40;
    // 夜视剩余不足 10 秒时屏幕会闪烁，所以持续时间要给足
    private static final int NIGHT_VISION_DURATION = 300;
    private static final long DIVINE_BLOCK_ENERGY_COST = 1000;
    private static final String ARMOR_FLIGHT_KEY = "sourceofmystery_armor_flight";

    // 生命加成 modifier 的 UUID 必须保持不变，否则老存档里已有的加成无法移除
    private static final HealthBonus ORIGIN_DRAGON_HEALTH = new HealthBonus(MysteryArmorMaterial.ORIGIN_DRAGON,
            UUID.fromString("5a3f1e2d-4b6c-7d8e-9f0a-1b2c3d4e5f6a"), "Origin Dragon Health Boost", 100.0);
    private static final HealthBonus DIVINE_BLESSING_HEALTH = new HealthBonus(MysteryArmorMaterial.DIVINE_BLESSING,
            UUID.fromString("6b4f2e3f-5c7d-8e9f-0b1c-2d3e4f5a6b7c"), "Divine Blessing Health Boost", 200.0);

    private record HealthBonus(ArmorMaterial material, UUID id, String name, double amount) {
    }

    /**
     * 当前胸甲的材料；没穿或不是护甲时返回 null
     */
    @Nullable
    public static ArmorMaterial getChestplateMaterial(Player player) {
        return player.getItemBySlot(EquipmentSlot.CHEST).getItem() instanceof ArmorItem armor ? armor.getMaterial() : null;
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide) {
            return;
        }

        Player player = event.player;
        ArmorMaterial material = getChestplateMaterial(player);

        updateHealthBonus(player, material, ORIGIN_DRAGON_HEALTH);
        updateHealthBonus(player, material, DIVINE_BLESSING_HEALTH);
        updateFlight(player, material == MysteryArmorMaterial.ORIGIN_DRAGON || material == MysteryArmorMaterial.DIVINE_BLESSING);

        if (player.tickCount % EFFECT_INTERVAL != 0) {
            return;
        }

        if (material == MysteryArmorMaterial.SPIRIT_SOURCE) {
            player.clearFire();
            addHiddenEffect(player, MobEffects.WATER_BREATHING, EFFECT_DURATION, 0);
        } else if (material == MysteryArmorMaterial.DARK_SOURCE) {
            player.clearFire();
            player.removeEffect(MobEffects.WITHER);
            applyPowerEffects(player);
        } else if (material == MysteryArmorMaterial.ORIGIN_DRAGON || material == MysteryArmorMaterial.DIVINE_BLESSING) {
            player.clearFire();
            removeHarmfulEffects(player);
            applyPowerEffects(player);
        }
    }

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof Player player) || player.level().isClientSide) {
            return;
        }
        ArmorMaterial material = getChestplateMaterial(player);
        DamageSource source = event.getSource();

        boolean immune = false;
        if (material == MysteryArmorMaterial.SPIRIT_SOURCE) {
            immune = source.is(DamageTypeTags.IS_FIRE);
        } else if (material == MysteryArmorMaterial.DARK_SOURCE) {
            immune = source.is(DamageTypeTags.IS_FIRE) || isWitherDamage(source);
        } else if (material == MysteryArmorMaterial.ORIGIN_DRAGON || material == MysteryArmorMaterial.DIVINE_BLESSING) {
            immune = source.is(DamageTypeTags.IS_FIRE) || isWitherDamage(source)
                    || source.is(DamageTypes.DRAGON_BREATH) || source.is(DamageTypeTags.IS_PROJECTILE);
        }

        if (immune) {
            event.setCanceled(true);
            return;
        }

        // 神威天佑：其他伤害消耗 1000 能量完全抵挡一次
        if (material == MysteryArmorMaterial.DIVINE_BLESSING
                && MysteryEnergyCapability.consumeEnergy(player, DIVINE_BLOCK_ENERGY_COST)) {
            event.setCanceled(true);
            spawnDivineProtectionEffect((ServerLevel) player.level(), player);
        }
    }

    private static boolean isWitherDamage(DamageSource source) {
        return source.is(DamageTypes.WITHER) || source.is(DamageTypes.WITHER_SKULL);
    }

    /**
     * 暗源之甲及以上共有的增益效果
     */
    private static void applyPowerEffects(Player player) {
        addHiddenEffect(player, MobEffects.WATER_BREATHING, EFFECT_DURATION, 0);
        addHiddenEffect(player, MobEffects.JUMP, EFFECT_DURATION, 0);
        addHiddenEffect(player, MobEffects.NIGHT_VISION, NIGHT_VISION_DURATION, 0);
        addHiddenEffect(player, MobEffects.DIG_SPEED, EFFECT_DURATION, 0);
        addHiddenEffect(player, MobEffects.DAMAGE_BOOST, EFFECT_DURATION, 2); // 力量 III
    }

    private static void addHiddenEffect(Player player, MobEffect effect, int duration, int amplifier) {
        player.addEffect(new MobEffectInstance(effect, duration, amplifier, false, false));
    }

    /**
     * 免疫所有负面 Buff：移除所有负面（HARMFUL）效果
     */
    private static void removeHarmfulEffects(Player player) {
        List<MobEffect> harmful = player.getActiveEffects().stream()
                .map(MobEffectInstance::getEffect)
                .filter(effect -> effect.getCategory() == MobEffectCategory.HARMFUL)
                .toList();
        harmful.forEach(player::removeEffect);
    }

    /**
     * 穿甲加生命上限，脱甲（或换成别的胸甲）移除
     */
    private static void updateHealthBonus(Player player, @Nullable ArmorMaterial material, HealthBonus bonus) {
        AttributeInstance maxHealth = player.getAttribute(Attributes.MAX_HEALTH);
        if (maxHealth == null) {
            return;
        }
        boolean active = maxHealth.getModifier(bonus.id()) != null;
        if (material == bonus.material()) {
            if (!active) {
                maxHealth.addPermanentModifier(new AttributeModifier(bonus.id(), bonus.name(), bonus.amount(),
                        AttributeModifier.Operation.ADDITION));
            }
        } else if (active) {
            maxHealth.removeModifier(bonus.id());
            // 把当前生命 clamp 到新上限，避免脱甲后血条残留
            if (player.getHealth() > player.getMaxHealth()) {
                player.setHealth(player.getMaxHealth());
            }
        }
    }

    /**
     * 胸甲赋予的飞行能力。
     * 是否由本模组授予记录在玩家 NBT 中（而不是内存里的静态集合）：
     * 死亡重生、切换游戏模式后能重新授予；在两件可飞行的胸甲之间直接切换也不会丢失飞行。
     */
    private static void updateFlight(Player player, boolean shouldFly) {
        CompoundTag data = MysteryEnergyCapability.getPlayerData(player);
        if (shouldFly) {
            if (!player.getAbilities().mayfly) {
                player.getAbilities().mayfly = true;
                player.onUpdateAbilities();
                data.putBoolean(ARMOR_FLIGHT_KEY, true);
            }
        } else if (data.getBoolean(ARMOR_FLIGHT_KEY)) {
            data.remove(ARMOR_FLIGHT_KEY);
            if (!player.isCreative() && !player.isSpectator()) {
                player.getAbilities().mayfly = false;
                player.getAbilities().flying = false;
                player.onUpdateAbilities();
            }
        }
    }

    private static void spawnDivineProtectionEffect(ServerLevel level, Player player) {
        spawnParticleCloud(level, ParticleTypes.ENCHANT, player, 30, 3.0, 0.05);
        spawnParticleCloud(level, ParticleTypes.WITCH, player, 20, 2.0, 0.02);
        spawnParticleCloud(level, ParticleTypes.FIREWORK, player, 15, 2.0, 0.02);
    }

    private static void spawnParticleCloud(ServerLevel level, ParticleOptions particle, Player player,
                                           int count, double size, double speed) {
        for (int i = 0; i < count; i++) {
            level.sendParticles(particle,
                    player.getX() + (level.random.nextDouble() - 0.5) * size,
                    player.getY() + level.random.nextDouble() * size,
                    player.getZ() + (level.random.nextDouble() - 0.5) * size,
                    1, 0, 0, 0, speed);
        }
    }
}
