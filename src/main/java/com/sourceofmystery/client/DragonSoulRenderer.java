package com.sourceofmystery.client;

import com.sourceofmystery.entity.DragonSoulBoss;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/**
 * 龙魂 Boss 的 GeckoLib 渲染器。
 */
public class DragonSoulRenderer extends GeoEntityRenderer<DragonSoulBoss> {
    public DragonSoulRenderer(EntityRendererProvider.Context context) {
        super(context, new DragonSoulModel());
        this.shadowRadius = 1.0F;
    }
}
