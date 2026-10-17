package com.sourceofmystery.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.sourceofmystery.entity.GuardianSpiritProjectile;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * 守护之灵蓝球的渲染器：两层面向镜头的蓝色发光球体（内层亮白蓝 + 外层淡蓝光晕）。
 */
public class GuardianSpiritProjectileRenderer extends EntityRenderer<GuardianSpiritProjectile> {

    private static final ResourceLocation ORB_TEX =
            new ResourceLocation("minecraft", "textures/entity/end_crystal/end_crystal_beam.png");

    public GuardianSpiritProjectileRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(GuardianSpiritProjectile proj, float yaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffers, int packedLight) {
        poseStack.pushPose();
        // 面向镜头（billboard）
        poseStack.mulPose(this.entityRenderDispatcher.cameraOrientation());
        // 旋转
        poseStack.mulPose(Axis.ZP.rotationDegrees((proj.tickCount + partialTick) * 12f));

        Matrix4f m = poseStack.last().pose();
        Matrix3f n = poseStack.last().normal();

        // 内层：亮白蓝核心
        drawDisc(buffers.getBuffer(RenderType.entityTranslucentEmissive(ORB_TEX)),
                m, n, 0.35f, 0.85f, 0.9f, 1.0f, 1.0f);
        // 外层：淡蓝光晕
        drawDisc(buffers.getBuffer(RenderType.entityTranslucentEmissive(ORB_TEX)),
                m, n, 0.7f, 0.5f, 0.7f, 1.0f, 0.5f);

        poseStack.popPose();
        super.render(proj, yaw, partialTick, poseStack, buffers, packedLight);
    }

    private void drawDisc(VertexConsumer vc, Matrix4f m, Matrix3f n,
                          float r, float red, float green, float blue, float alpha) {
        vc.vertex(m, -r, -r, 0f).color(red, green, blue, alpha).uv(0f, 0f)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT)
                .normal(n, 0f, 0f, 1f).endVertex();
        vc.vertex(m, -r,  r, 0f).color(red, green, blue, alpha).uv(0f, 1f)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT)
                .normal(n, 0f, 0f, 1f).endVertex();
        vc.vertex(m,  r,  r, 0f).color(red, green, blue, alpha).uv(1f, 1f)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT)
                .normal(n, 0f, 0f, 1f).endVertex();
        vc.vertex(m,  r, -r, 0f).color(red, green, blue, alpha).uv(1f, 0f)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT)
                .normal(n, 0f, 0f, 1f).endVertex();
    }

    @Override
    public ResourceLocation getTextureLocation(GuardianSpiritProjectile entity) {
        return ORB_TEX;
    }
}
