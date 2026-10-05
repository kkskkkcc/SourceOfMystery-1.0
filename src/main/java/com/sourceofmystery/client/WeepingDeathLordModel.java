package com.sourceofmystery.client;

import com.sourceofmystery.SourceOfMystery;
import com.sourceofmystery.entity.WeepingDeathLord;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;

/**
 * 泣死之主的 GeckoLib 模型（由 2ba/凋零守卫 转换而来，见 tools/weeping_death_lord）：
 * geo/entity/weeping_death_lord.geo.json、textures/entity/weeping_death_lord.png、
 * animations/entity/weeping_death_lord.animation.json
 */
public class WeepingDeathLordModel extends DefaultedEntityGeoModel<WeepingDeathLord> {
    public WeepingDeathLordModel() {
        super(new ResourceLocation(SourceOfMystery.MOD_ID, "weeping_death_lord"), false);
    }
}
