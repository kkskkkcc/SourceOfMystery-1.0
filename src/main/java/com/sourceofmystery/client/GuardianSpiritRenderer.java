package com.sourceofmystery.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.sourceofmystery.SourceOfMystery;
import com.sourceofmystery.entity.GuardianSpirit;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

/**
 * 守护之灵渲染器：GeckoLib 模型 + 祈祷时脚下蓝色法阵。
 */
public class GuardianSpiritRenderer extends GeoEntityRenderer<GuardianSpirit> {

    private static final ResourceLocation MAGIC_CIRCLE = new ResourceLocation(
            SourceOfMystery.MOD_ID, "textures/entity/guardian_spirit_magic_circle.png");

    public GuardianSpiritRenderer(EntityRendererProvider.Context context) {
        super(context, new GuardianSpiritModel());
        this.shadowRadius = 0.0F; // 幽灵没有影子
        this.addRenderLayer(new MagicCircleLayer(this));
    }

    @Override
    public net.minecraft.client.renderer.RenderType getRenderType(GuardianSpirit animatable,
            net.minecraft.resources.ResourceLocation texture, MultiBufferSource bufferSource, float partialTick) {
        // 半透明幽灵（配合半透明贴图）
        return net.minecraft.client.renderer.RenderType.entityTranslucent(texture);
    }

    /** 祈祷时脚下的蓝色法阵（贴图，不是粒子）。法阵在 XZ 平面、脚下，缓缓旋转 + 渐显 */
    private static class MagicCircleLayer extends GeoRenderLayer<GuardianSpirit> {
        MagicCircleLayer(GuardianSpiritRenderer renderer) {
            super(renderer);
        }

        @Override
        public void renderForBone(PoseStack poseStack, GuardianSpirit entity, GeoBone bone,
                                  RenderType renderType, MultiBufferSource bufferSource,
                                  VertexConsumer buffer, float partialTick, int packedLight, int packedOverlay) {
            // 只在根骨骼渲染一次（bone 是 Root）
            if (!"Root".equals(bone.getName()) && !bone.getName().isEmpty()) {
                return;
            }
            if (!entity.isPraying()) return;

            float time = entity.tickCount + partialTick;
            // 祈祷起手时由小到大展开，直径约 9 格，随后缓慢旋转。
            float unfold = Mth.clamp(entity.getStateAge() / 18.0f, 0.0f, 1.0f);
            float radius = (0.35f + 4.15f * unfold) + 0.12f * Mth.sin(time * 0.15f);
            float alpha = Mth.clamp(entity.getStateAge() / 12.0f, 0.0f, 0.85f);

            poseStack.pushPose();
            // 平移到脚下（法阵水平放在 XZ 平面）
            poseStack.translate(0, 0.05f, 0);
            // 让法阵平面转成水平（默认 quad 在 XY 平面，绕 X 转 -90° 变成 XZ 平面）
            poseStack.mulPose(com.mojang.math.Axis.XP.rotationDegrees(-90f));
            // 自转
            poseStack.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(time * 6f));

            drawCircle(bufferSource.getBuffer(RenderType.entityTranslucentEmissive(MAGIC_CIRCLE)),
                    poseStack, radius, alpha);
            poseStack.popPose();

            // 恢复主渲染缓冲（GeckoLib 要求）
            bufferSource.getBuffer(renderType);
        }

        private void drawCircle(VertexConsumer vc, PoseStack poseStack, float r, float alpha) {
            Matrix4f m = poseStack.last().pose();
            Matrix3f n = poseStack.last().normal();
            vc.vertex(m, -r, -r, 0f).color(1f, 1f, 1f, alpha).uv(0f, 0f)
                    .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT)
                    .normal(n, 0f, 0f, 1f).endVertex();
            vc.vertex(m, -r,  r, 0f).color(1f, 1f, 1f, alpha).uv(0f, 1f)
                    .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT)
                    .normal(n, 0f, 0f, 1f).endVertex();
            vc.vertex(m,  r,  r, 0f).color(1f, 1f, 1f, alpha).uv(1f, 1f)
                    .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT)
                    .normal(n, 0f, 0f, 1f).endVertex();
            vc.vertex(m,  r, -r, 0f).color(1f, 1f, 1f, alpha).uv(1f, 0f)
                    .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT)
                    .normal(n, 0f, 0f, 1f).endVertex();
        }
    }
}
