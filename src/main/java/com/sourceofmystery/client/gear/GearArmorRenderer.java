package com.sourceofmystery.client.gear;

import com.sourceofmystery.SourceOfMystery;
import com.sourceofmystery.item.MysteryChestplateItem;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoArmorRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

public class GearArmorRenderer extends GeoArmorRenderer<MysteryChestplateItem> {
    public GearArmorRenderer(String armorId) {
        super(new GeoModel<>() {
            private final ResourceLocation model = resource("geo/armor/" + armorId + ".geo.json");
            private final ResourceLocation texture = resource("textures/armor/" + armorId + ".png");
            private final ResourceLocation animation = resource("animations/armor/" + armorId + ".animation.json");

            @Override public ResourceLocation getModelResource(MysteryChestplateItem item) { return model; }
            @Override public ResourceLocation getTextureResource(MysteryChestplateItem item) { return texture; }
            @Override public ResourceLocation getAnimationResource(MysteryChestplateItem item) { return animation; }
        });
        addRenderLayer(new AutoGlowingGeoLayer<>(this));
    }

    private static ResourceLocation resource(String path) {
        return new ResourceLocation(SourceOfMystery.MOD_ID, path);
    }

    @Override
    protected void applyBoneVisibilityBySlot(EquipmentSlot slot) {
        super.applyBoneVisibilityBySlot(slot);
        if (slot == EquipmentSlot.CHEST) {
            // Hip tassets follow the wearer's legs.
            setBoneVisible(getRightLegBone(), true);
            setBoneVisible(getLeftLegBone(), true);
        }
    }
}
