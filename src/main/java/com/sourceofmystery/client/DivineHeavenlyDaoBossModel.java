package com.sourceofmystery.client;

import com.sourceofmystery.SourceOfMystery;
import com.sourceofmystery.entity.DivineHeavenlyDaoBoss;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;

/**
 * 神威天道 Boss 的 GeckoLib 模型。
 * DefaultedEntityGeoModel 会自动加载：
 * - geo/entity/divine_heavenly_dao_boss.geo.json
 * - textures/entity/divine_heavenly_dao_boss.png
 * - animations/entity/divine_heavenly_dao_boss.animation.json
 */
public class DivineHeavenlyDaoBossModel extends DefaultedEntityGeoModel<DivineHeavenlyDaoBoss> {
    public DivineHeavenlyDaoBossModel() {
        super(new ResourceLocation(SourceOfMystery.MOD_ID, "divine_heavenly_dao_boss"), true);
    }
}
