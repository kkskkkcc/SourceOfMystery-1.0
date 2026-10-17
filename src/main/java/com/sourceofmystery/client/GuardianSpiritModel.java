package com.sourceofmystery.client;

import com.sourceofmystery.SourceOfMystery;
import com.sourceofmystery.entity.GuardianSpirit;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;

/**
 * 守护之灵使用从 4ba 模型转换的无腿幽灵骨骼和独立 GeckoLib 动画。
 */
public class GuardianSpiritModel extends DefaultedEntityGeoModel<GuardianSpirit> {
    public GuardianSpiritModel() {
        super(new ResourceLocation(SourceOfMystery.MOD_ID, "guardian_spirit"), false);
    }
}
