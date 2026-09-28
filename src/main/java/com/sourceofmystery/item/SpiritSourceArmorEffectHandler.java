package com.sourceofmystery.item;

import net.minecraft.tags.DamageTypeTags;
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
public class SpiritSourceArmorEffectHandler {

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

        // 检查是否穿着灵源秘甲胸甲
        if (!isWearingSpiritSourceChestplate(player)) {
            return;
        }

        // 水下呼吸
        player.addEffect(new MobEffectInstance(MobEffects.WATER_BREATHING, 40, 0, false, false));
        // 无视火焰：清除着火状态
        player.clearFire();
    }

    /**
     * 真正的火焰伤害免疫：取消火焰类伤害
     */
    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        if (event.getEntity() instanceof Player player && isWearingSpiritSourceChestplate(player)) {
            if (event.getSource().is(DamageTypeTags.IS_FIRE)) {
                event.setCanceled(true);
            }
        }
    }

    private static boolean isWearingSpiritSourceChestplate(Player player) {
        ItemStack chestplate = player.getItemBySlot(EquipmentSlot.CHEST);
        return chestplate.getItem() instanceof ArmorItem armorItem &&
               armorItem.getMaterial() == MysteryArmorMaterial.SPIRIT_SOURCE;
    }
}
