package com.sourceofmystery.client.gear;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoItemRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

public class GearItemRenderer<T extends Item & GeoItem> extends GeoItemRenderer<T> {
    public GearItemRenderer(ResourceLocation model, ResourceLocation texture, ResourceLocation animation) {
        super(new GeoModel<>() {
            @Override public ResourceLocation getModelResource(T item) { return model; }
            @Override public ResourceLocation getTextureResource(T item) { return texture; }
            @Override public ResourceLocation getAnimationResource(T item) { return animation; }
        });
        addRenderLayer(new AutoGlowingGeoLayer<>(this));
    }
}
