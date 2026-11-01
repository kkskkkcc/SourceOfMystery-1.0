package com.sourceofmystery.client;

import com.sourceofmystery.entity.DivineHeavenlyDaoBoss;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.MultiBufferSource;
import com.mojang.blaze3d.vertex.PoseStack;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/**
 * 神威天道 Boss 的 GeckoLib 渲染器。
 * AutoGlowingGeoLayer 会自动加载 divine_heavenly_dao_boss_glowmask.png 作为发光层（眼睛等部位发光）。
 */
public class DivineHeavenlyDaoBossRenderer extends GeoEntityRenderer<DivineHeavenlyDaoBoss> {
    public DivineHeavenlyDaoBossRenderer(EntityRendererProvider.Context context) {
        super(context, new DivineHeavenlyDaoBossModel());
        // 和碰撞箱同样倍数放大（见 DivineHeavenlyDaoBoss.SCALE）
        this.withScale(DivineHeavenlyDaoBoss.SCALE);
        this.shadowRadius = 0.75F * DivineHeavenlyDaoBoss.SCALE;
        this.addRenderLayer(new AutoGlowingGeoLayer<>(this));
    }

    @Override
    public void render(DivineHeavenlyDaoBoss entity, float yaw, float partialTick, PoseStack pose,
                       MultiBufferSource buffers, int light) {
        // Mob body yaw is not a synchronized field. Match the server hurtbox yaw.
        entity.yBodyRotO = entity.yRotO;
        entity.yBodyRot = entity.getYRot();
        super.render(entity, yaw, partialTick, pose, buffers, light);
    }
}
