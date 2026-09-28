package com.sourceofmystery.client;

import com.sourceofmystery.entity.DivineHeavenlyDaoBoss;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/**
 * 神威天道 Boss 的 GeckoLib 渲染器。
 * AutoGlowingGeoLayer 会自动加载 divine_heavenly_dao_boss_glowmask.png 作为发光层（眼睛等部位发光）。
 */
public class DivineHeavenlyDaoBossRenderer extends GeoEntityRenderer<DivineHeavenlyDaoBoss> {
    public DivineHeavenlyDaoBossRenderer(EntityRendererProvider.Context context) {
        super(context, new DivineHeavenlyDaoBossModel());
        this.shadowRadius = 1.0F;
        this.addRenderLayer(new AutoGlowingGeoLayer<>(this));
    }
}
