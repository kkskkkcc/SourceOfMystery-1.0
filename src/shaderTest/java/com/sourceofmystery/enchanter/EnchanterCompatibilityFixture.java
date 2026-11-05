package com.sourceofmystery.enchanter;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.enchantment.*;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.*;

@Mod.EventBusSubscriber(modid="sourceofmystery",bus=Mod.EventBusSubscriber.Bus.MOD)
public final class EnchanterCompatibilityFixture {
    @SubscribeEvent public static void register(RegisterEvent event){event.register(ForgeRegistries.Keys.ENCHANTMENTS,h->h.register(new ResourceLocation("enchantcompat","arcane_resonance"),new Enchantment(Enchantment.Rarity.RARE,EnchantmentCategory.WEAPON,new EquipmentSlot[]{EquipmentSlot.MAINHAND}){
        @Override public int getMaxLevel(){return 3;}
        @Override public boolean isTreasureOnly(){return true;}
        @Override public boolean isDiscoverable(){return false;}
    }));}
}
