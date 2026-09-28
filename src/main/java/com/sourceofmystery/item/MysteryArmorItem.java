package com.sourceofmystery.item;

import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;

/**
 * 自定义护甲类（占位）。
 * 注意：生命加成已改由护甲 EffectHandler 在 tick 中管理（穿甲加血、脱甲移除），
 * 本类不再通过 attribute modifier 实现生命加成，避免与 handler 冲突。
 */
public class MysteryArmorItem extends ArmorItem {

    public MysteryArmorItem(ArmorMaterial material, Type slot, Properties properties, long healthBonus) {
        super(material, slot, properties);
        // healthBonus 参数保留以兼容构造调用，但不再在此处使用
    }
}
