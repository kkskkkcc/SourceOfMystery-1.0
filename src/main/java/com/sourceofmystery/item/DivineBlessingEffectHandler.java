package com.sourceofmystery.item;

import com.sourceofmystery.capability.energy.MysteryEnergyCapability;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = com.sourceofmystery.SourceOfMystery.MOD_ID)
public class DivineBlessingEffectHandler {

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

        // 每20tick（1秒）触发一次
        if (level.getGameTime() % EFFECT_INTERVAL != 0) {
            return;
        }

        // 检查是否穿着神威天佑胸甲
        ItemStack chestplate = player.getItemBySlot(EquipmentSlot.CHEST);
        if (!(chestplate.getItem() instanceof ArmorItem)) {
            return;
        }

        // 检查是否是神威天佑胸甲 (通过材料名称检查)
        if (!(chestplate.getItem() instanceof ArmorItem armorItem)) {
            return;
        }

        // 检查材料名称
        String materialName = armorItem.getMaterial().getName();
        if (!materialName.equals("divine_blessing")) {
            return;
        }

        // 消耗神秘之能
        if (MysteryEnergyCapability.consumeEnergy(player, 1)) {
            // 神威天佑效果：消耗神秘之能时
            // 生命恢复 V (5)
            player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 40, 4));
            // 抗性提升 III
            player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 40, 2));
            // 力量 III
            player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 40, 2));
            // 防火
            player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 40, 0));
            // 速度 II
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 40, 1));
        } else {
            // 神秘之能不足时，只提供基础效果
            player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 40, 1));
            player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 40, 0));
        }
    }
}
