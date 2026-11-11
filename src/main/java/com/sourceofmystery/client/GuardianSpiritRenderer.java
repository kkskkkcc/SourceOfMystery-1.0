package com.sourceofmystery.client;

import com.sourceofmystery.entity.GuardianSpirit;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/**
 * 守护之灵渲染器：半透明 GeckoLib 模型。
 */
public class GuardianSpiritRenderer extends GeoEntityRenderer<GuardianSpirit> {


    public GuardianSpiritRenderer(EntityRendererProvider.Context context) {
        super(context, new GuardianSpiritModel());
        this.shadowRadius = 0.0F; // 幽灵没有影子
    }

    @Override
    public net.minecraft.client.renderer.RenderType getRenderType(GuardianSpirit animatable,
            net.minecraft.resources.ResourceLocation texture, MultiBufferSource bufferSource, float partialTick) {
        // 半透明幽灵（配合半透明贴图）
        return net.minecraft.client.renderer.RenderType.entityTranslucent(texture);
    }

}
