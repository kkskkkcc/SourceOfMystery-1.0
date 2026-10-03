package com.sourceofmystery.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.sourceofmystery.entity.DragonSoulBoss;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/**
 * 龙魂 Boss 的 GeckoLib 渲染器。高速冲刺 / 瞬移时在身后画出半透明的紫色残影（用当前姿势在过去的位置重画模型）。
 */
public class DragonSoulRenderer extends GeoEntityRenderer<DragonSoulBoss> {

    public DragonSoulRenderer(EntityRendererProvider.Context context) {
        super(context, new DragonSoulModel());
        this.shadowRadius = 1.0F;
    }

    @Override
    public void render(DragonSoulBoss entity, float entityYaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight) {
        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
        if (entity.afterimages.isEmpty() || entity.isInvisible()) {
            return;
        }
        Vec3 current = entity.getPosition(partialTick);
        float bodyYaw = Mth.rotLerp(partialTick, entity.yBodyRotO, entity.yBodyRot);
        float now = entity.tickCount + partialTick;
        RenderType type = RenderType.entityTranslucent(getTextureLocation(entity));
        BakedGeoModel model = getGeoModel().getBakedModel(getGeoModel().getModelResource(entity));
        for (DragonSoulBoss.Afterimage ghost : entity.afterimages) {
            float age = now - ghost.born();
            if (age < 0 || age >= DragonSoulBoss.AFTERIMAGE_LIFE) {
                continue;
            }
            float alpha = 0.45f * (1 - age / DragonSoulBoss.AFTERIMAGE_LIFE);
            poseStack.pushPose();
            poseStack.translate(ghost.x() - current.x, ghost.y() - current.y, ghost.z() - current.z);
            // actuallyRender 会再按当前身体朝向旋转，这里补上残影与当前朝向的差
            poseStack.mulPose(Axis.YP.rotationDegrees(bodyYaw - ghost.yaw()));
            this.animatable = entity;
            reRender(model, poseStack, bufferSource, entity, type, bufferSource.getBuffer(type), partialTick,
                    LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 0.7f, 0.5f, 1.0f, alpha);
            poseStack.popPose();
        }
        this.animatable = null;
    }
}
