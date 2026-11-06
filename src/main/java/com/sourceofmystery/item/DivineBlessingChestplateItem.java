package com.sourceofmystery.item;

import java.util.UUID;

/** The divine chestplate; its worn glory effects are chosen by GearClient. */
public final class DivineBlessingChestplateItem extends MysteryChestplateItem {
    public DivineBlessingChestplateItem(Properties properties) {
        super(MysteryArmorMaterial.DIVINE_BLESSING, properties, true,
                UUID.fromString("4d22fa12-dc4f-45ea-a2dd-2c94cb7defdb"), 200.0);
    }
}
