package com.sourceofmystery.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.sourceofmystery.entity.DragonSpear;
import net.minecraft.client.model.TridentModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * 龙魂掷出的长枪：借用原版三叉戟模型，放大并染成暗紫色，带附魔光效
 */
public class DragonSpearRenderer extends EntityRenderer<DragonSpear> {

    private static final float SCALE = 1.8f;
    private final TridentModel model;

    public DragonSpearRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.model = new TridentModel(context.bakeLayer(ModelLayers.TRIDENT));
    }

    @Override
    public void render(DragonSpear spear, float entityYaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffers, int packedLight) {
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(Mth.lerp(partialTick, spear.yRotO, spear.getYRot()) - 90.0F));
        poseStack.mulPose(Axis.ZP.rotationDegrees(Mth.lerp(partialTick, spear.xRotO, spear.getXRot()) + 90.0F));
        if (spear.isReturning()) {
            // 飞回时枪尾朝前
            poseStack.mulPose(Axis.ZP.rotationDegrees(180.0F));
        }
        poseStack.scale(SCALE, SCALE, SCALE);
        VertexConsumer consumer = ItemRenderer.getFoilBufferDirect(buffers, this.model.renderType(getTextureLocation(spear)), false, true);
        this.model.renderToBuffer(poseStack, consumer, packedLight, OverlayTexture.NO_OVERLAY, 0.45F, 0.3F, 0.95F, 1.0F);
        poseStack.popPose();
        super.render(spear, entityYaw, partialTick, poseStack, buffers, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(DragonSpear spear) {
        return TridentModel.TEXTURE;
    }
}
