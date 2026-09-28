package com.sourceofmystery.item;

import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = com.sourceofmystery.SourceOfMystery.MOD_ID)
public class DarkSourceArmorEffectHandler {

    private static final int EFFECT_INTERVAL = 20; // 每秒触发一次

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

        // 每秒触发一次
        if (level.getGameTime() % EFFECT_INTERVAL != 0) {
            return;
        }

        // 检查是否穿着暗源之甲胸甲
        if (!isWearingDarkSourceChestplate(player)) {
            return;
        }

        // 无视火焰：清除着火状态
        player.clearFire();
        // 无视凋零：移除凋零效果
        player.removeEffect(MobEffects.WITHER);
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
     * 真正的伤害免疫：火焰、凋零
     */
    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        if (event.getEntity() instanceof Player player && isWearingDarkSourceChestplate(player)) {
            // 火焰免疫
            if (event.getSource().is(DamageTypeTags.IS_FIRE)) {
                event.setCanceled(true);
                return;
            }
            // 凋零免疫（凋零效果伤害 + 凋零骷髅头伤害）
            if (event.getSource().is(DamageTypes.WITHER) || event.getSource().is(DamageTypes.WITHER_SKULL)) {
                event.setCanceled(true);
            }
        }
    }

    private static boolean isWearingDarkSourceChestplate(Player player) {
        ItemStack chestplate = player.getItemBySlot(EquipmentSlot.CHEST);
        return chestplate.getItem() instanceof ArmorItem armorItem &&
               armorItem.getMaterial() == MysteryArmorMaterial.DARK_SOURCE;
    }
}
