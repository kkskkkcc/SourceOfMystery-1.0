package com.sourceofmystery.client;

import com.sourceofmystery.SourceOfMystery;
import com.sourceofmystery.entity.DragonSoulBoss;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;

/**
 * 龙魂 Boss 的 GeckoLib 模型。
 * DefaultedEntityGeoModel 会自动加载：
 * - geo/entity/dragon_soul.geo.json
 * - textures/entity/dragon_soul.png
 * - animations/entity/dragon_soul.animation.json
 */
public class DragonSoulModel extends DefaultedEntityGeoModel<DragonSoulBoss> {
    public DragonSoulModel() {
        super(new ResourceLocation(SourceOfMystery.MOD_ID, "dragon_soul"), false);
    }
}
