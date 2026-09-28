package com.sourceofmystery.item;

import com.sourceofmystery.capability.energy.MysteryEnergyCapability;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public class DivineBlessingArmorItem extends ArmorItem {

    public DivineBlessingArmorItem(ArmorMaterial material, Type slot, Properties properties) {
        super(material, slot, properties);
    }

    @Override
    public boolean isEnchantable(ItemStack stack) {
        return true;
    }

    @Override
    public int getEnchantmentValue() {
        return 50;
    }
}
