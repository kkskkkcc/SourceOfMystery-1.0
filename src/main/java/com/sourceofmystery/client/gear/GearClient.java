package com.sourceofmystery.client.gear;

import com.sourceofmystery.SourceOfMystery;
import com.sourceofmystery.client.DivineBlessingArmorRenderer;
import com.sourceofmystery.item.DivineBlessingChestplateItem;
import com.sourceofmystery.item.MysteryChestplateItem;
import com.sourceofmystery.item.MysterySwordItem;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import net.minecraftforge.registries.ForgeRegistries;
import software.bernie.geckolib.renderer.GeoArmorRenderer;

/** Client-only renderer factories, so common item classes never name client types. */
public final class GearClient {
    private GearClient() {}

    private static ResourceLocation id(String path) {
        return new ResourceLocation(SourceOfMystery.MOD_ID, path);
    }

    public static IClientItemExtensions chestplate(MysteryChestplateItem item) {
        return new IClientItemExtensions() {
            private GeoArmorRenderer<?> armor;
            private GearItemRenderer<MysteryChestplateItem> display;

            @Override
            public HumanoidModel<?> getHumanoidArmorModel(LivingEntity wearer, ItemStack stack,
                                                         EquipmentSlot slot, HumanoidModel<?> original) {
                if (armor == null) {
                    armor = item instanceof DivineBlessingChestplateItem
                            ? new DivineBlessingArmorRenderer() : new GearArmorRenderer(item.getArmorId());
                }
                armor.prepForRender(wearer, stack, slot, original);
                return armor;
            }

            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (display == null) {
                    String armorId = item.getArmorId();
                    display = new GearItemRenderer<>(id("geo/armor/" + armorId + ".geo.json"),
                            id("textures/armor/" + armorId + ".png"),
                            id("animations/armor/" + armorId + ".animation.json"));
                }
                return display;
            }
        };
    }

    public static IClientItemExtensions sword(MysterySwordItem item) {
        return new IClientItemExtensions() {
            private GearItemRenderer<MysterySwordItem> display;

            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (display == null) {
                    String name = ForgeRegistries.ITEMS.getKey(item).getPath();
                    display = new GearItemRenderer<>(id("geo/item/" + name + ".geo.json"),
                            id("textures/gear/" + name + ".png"),
                            id("animations/item/" + name + ".animation.json"));
                }
                return display;
            }
        };
    }
}
