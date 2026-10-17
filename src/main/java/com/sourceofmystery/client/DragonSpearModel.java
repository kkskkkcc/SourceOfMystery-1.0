package com.sourceofmystery.client;

import com.sourceofmystery.SourceOfMystery;
import com.sourceofmystery.entity.DragonSpear;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

/**
 * 龙魂掷出的长刃：与手持武器共用造型和贴图，枢轴位于握柄
 */
public class DragonSpearModel extends GeoModel<DragonSpear> {

    private static final ResourceLocation MODEL = new ResourceLocation(SourceOfMystery.MOD_ID, "geo/entity/dragon_spear.geo.json");
    private static final ResourceLocation TEXTURE = new ResourceLocation(SourceOfMystery.MOD_ID, "textures/entity/dragon_soul.png");
    private static final ResourceLocation ANIMATION = new ResourceLocation(SourceOfMystery.MOD_ID, "animations/entity/dragon_spear.animation.json");

    @Override
    public ResourceLocation getModelResource(DragonSpear animatable) {
        return MODEL;
    }

    @Override
    public ResourceLocation getTextureResource(DragonSpear animatable) {
        return TEXTURE;
    }

    @Override
    public ResourceLocation getAnimationResource(DragonSpear animatable) {
        return ANIMATION;
    }
}
