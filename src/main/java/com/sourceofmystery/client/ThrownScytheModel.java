package com.sourceofmystery.client;

import com.sourceofmystery.SourceOfMystery;
import com.sourceofmystery.entity.ThrownScythe;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

/**
 * 掷出的镰刀：单独的 geo（枢轴在重心），贴图与泣死之主共用
 */
public class ThrownScytheModel extends GeoModel<ThrownScythe> {

    private static final ResourceLocation MODEL = new ResourceLocation(SourceOfMystery.MOD_ID, "geo/entity/weeping_scythe.geo.json");
    private static final ResourceLocation TEXTURE = new ResourceLocation(SourceOfMystery.MOD_ID, "textures/entity/weeping_death_lord.png");
    private static final ResourceLocation ANIMATION = new ResourceLocation(SourceOfMystery.MOD_ID, "animations/entity/weeping_scythe.animation.json");

    @Override
    public ResourceLocation getModelResource(ThrownScythe animatable) {
        return MODEL;
    }

    @Override
    public ResourceLocation getTextureResource(ThrownScythe animatable) {
        return TEXTURE;
    }

    @Override
    public ResourceLocation getAnimationResource(ThrownScythe animatable) {
        return ANIMATION;
    }
}
