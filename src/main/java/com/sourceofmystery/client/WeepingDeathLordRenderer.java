package com.sourceofmystery.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.sourceofmystery.SourceOfMystery;
import com.sourceofmystery.entity.WeepingDeathLord;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

/**
 * 泣死之主渲染器：
 * - 整体放大 1.25 倍（同 YSM 模型的 height_scale）
 * - 镰刀掷出后隐藏手里的镰刀骨骼；左手黑球按蓄力进度缩放
 * - 眼睛的白光（weeping_death_lord_glow.png，亮度随出场睁眼逐渐增强）
 * - 快速攻击时的半透明残影
 */
public class WeepingDeathLordRenderer extends GeoEntityRenderer<WeepingDeathLord> {

    private static final ResourceLocation GLOW = new ResourceLocation(SourceOfMystery.MOD_ID, "textures/entity/weeping_death_lord_glow.png");

    public WeepingDeathLordRenderer(EntityRendererProvider.Context context) {
        super(context, new WeepingDeathLordModel());
        this.shadowRadius = 0.9F;
        this.withScale(1.25f);
        this.addRenderLayer(new EyeGlowLayer(this));
    }

    @Override
    public void renderRecursively(PoseStack poseStack, WeepingDeathLord entity, GeoBone bone, RenderType renderType,
                                  MultiBufferSource bufferSource, VertexConsumer buffer, boolean isReRender, float partialTick,
                                  int packedLight, int packedOverlay, float red, float green, float blue, float alpha) {
        String name = bone.getName();
        if (name.equals("Scythe")) {
            bone.setHidden(entity.scytheOut());
        } else if (name.equals("DarkOrb")) {
            float size = entity.orbSize();
            bone.setHidden(size <= 0.01f);
            float pulse = 1.0f + 0.06f * Mth.sin((entity.tickCount + partialTick) * 0.5f);
            bone.setScaleX(size * pulse);
            bone.setScaleY(size * pulse);
            bone.setScaleZ(size * pulse);
            if (size > 0.01f) {
                packedLight = LightTexture.FULL_BRIGHT;
            }
        }
        super.renderRecursively(poseStack, entity, bone, renderType, bufferSource, buffer, isReRender, partialTick,
                packedLight, packedOverlay, red, green, blue, alpha);
    }

    @Override
    public void render(WeepingDeathLord entity, float entityYaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight) {
        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
        if (entity.afterimages.isEmpty() || entity.potionInvisible()) {
            return;
        }
        Vec3 current = entity.getPosition(partialTick);
        float bodyYaw = Mth.rotLerp(partialTick, entity.yBodyRotO, entity.yBodyRot);
        float now = entity.tickCount + partialTick;
        RenderType type = RenderType.entityTranslucent(getTextureLocation(entity));
        BakedGeoModel model = getGeoModel().getBakedModel(getGeoModel().getModelResource(entity));
        entity.renderingAfterimages = true;
        for (WeepingDeathLord.Afterimage ghost : entity.afterimages) {
            float age = now - ghost.born();
            if (age < 0 || age >= WeepingDeathLord.AFTERIMAGE_LIFE) {
                continue;
            }
            float a = 0.4f * (1 - age / WeepingDeathLord.AFTERIMAGE_LIFE);
            poseStack.pushPose();
            poseStack.translate(ghost.x() - current.x, ghost.y() - current.y, ghost.z() - current.z);
            poseStack.mulPose(Axis.YP.rotationDegrees(bodyYaw - ghost.yaw()));
            this.animatable = entity;
            reRender(model, poseStack, bufferSource, entity, type, bufferSource.getBuffer(type), partialTick,
                    LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 0.85f, 0.85f, 1.0f, a);
            poseStack.popPose();
        }
        entity.renderingAfterimages = false;
        this.animatable = null;
    }

    /** 眼睛的白光：加法混合的发光层，亮度 = 实体同步的 eyeGlow */
    private static class EyeGlowLayer extends GeoRenderLayer<WeepingDeathLord> {

        EyeGlowLayer(WeepingDeathLordRenderer renderer) {
            super(renderer);
        }

        @Override
        public void render(PoseStack poseStack, WeepingDeathLord entity, BakedGeoModel bakedModel, RenderType renderType,
                           MultiBufferSource bufferSource, VertexConsumer buffer, float partialTick, int packedLight, int packedOverlay) {
            float glow = entity.eyeGlow();
            if (glow <= 0.01f || entity.isInvisible()) {
                return;
            }
            RenderType eyes = RenderType.eyes(GLOW);
            getRenderer().reRender(bakedModel, poseStack, bufferSource, entity, eyes, bufferSource.getBuffer(eyes), partialTick,
                    LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, glow, glow, glow, 1.0f);
        }
    }
}
