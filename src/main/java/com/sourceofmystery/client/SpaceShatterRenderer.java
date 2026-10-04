package com.sourceofmystery.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.sourceofmystery.SourceOfMystery;
import com.sourceofmystery.entity.SpaceShatter;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * 斩碎空间：一块朝向泣死之主的圆形空间像玻璃一样裂开（贴图 space_shatter.png），裂缝后面露出虚空；
 * 随后沿贴图上的放射裂纹切成碎片，碎片一边旋转一边向四周飞散、淡出。
 */
public class SpaceShatterRenderer extends EntityRenderer<SpaceShatter> {

    private static final ResourceLocation TEXTURE = new ResourceLocation(SourceOfMystery.MOD_ID, "textures/entity/space_shatter.png");
    /** 与 tools/weeping_death_lord/make_shatter_texture.py 的 CRACK_ANGLES 一致 */
    private static final float[] CRACK_ANGLES = {0, 27, 61, 88, 117, 149, 178, 206, 239, 268, 297, 331};

    public SpaceShatterRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(SpaceShatter shatter, float yaw, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light) {
        float age = shatter.tickCount + partialTick;
        float crack = Math.min(1.0f, age / SpaceShatter.CRACK_TICKS);
        crack = 1 - (1 - crack) * (1 - crack);
        float scatter = shatter.closeProgress(partialTick);
        float radius = SpaceShatter.SIZE / 2 * (0.6f + 0.4f * crack);
        int n = CRACK_ANGLES.length;

        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(-shatter.getYRot()));

        // 裂缝后面的虚空：碎片飞散时一起收拢
        float hole = radius * 0.92f * (1 - scatter * scatter);
        if (hole > 0.05f) {
            VertexConsumer voidBuffer = buffers.getBuffer(RenderType.endPortal());
            Matrix4f m = poseStack.last().pose();
            float[] c = {0, 0, 0.05f};
            for (int i = 0; i < n; i++) {
                float a0 = CRACK_ANGLES[i] * Mth.DEG_TO_RAD;
                float a1 = CRACK_ANGLES[(i + 1) % n] * Mth.DEG_TO_RAD;
                float[] p = {Mth.cos(a0) * hole, Mth.sin(a0) * hole, 0.05f};
                float[] q = {Mth.cos(a1) * hole, Mth.sin(a1) * hole, 0.05f};
                EffectGeometry.voidQuad(voidBuffer, m, c, p, q, q);
            }
        }

        // 碎片：每个扇区一块，沿角平分线飞出并旋转
        VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucentEmissive(TEXTURE));
        float alpha = (1 - scatter) * Math.min(1.0f, age / 2.0f);
        for (int i = 0; i < n; i++) {
            float d0 = CRACK_ANGLES[i];
            float d1 = CRACK_ANGLES[(i + 1) % n] + (i + 1 == n ? 360 : 0);
            float mid = (d0 + d1) / 2 * Mth.DEG_TO_RAD;
            float fly = scatter * (2.5f + EffectGeometry.hash(shatter.getId(), i) * 2.0f);
            float spin = scatter * (EffectGeometry.hash(shatter.getId(), i + 50) - 0.5f) * 160.0f;
            poseStack.pushPose();
            poseStack.translate(Mth.cos(mid) * fly, Mth.sin(mid) * fly, (EffectGeometry.hash(shatter.getId(), i + 99) - 0.5f) * fly);
            poseStack.mulPose(Axis.ZP.rotationDegrees(spin));
            poseStack.mulPose(Axis.XP.rotationDegrees(spin * 0.6f));
            Matrix4f m = poseStack.last().pose();
            Matrix3f normal = poseStack.last().normal();
            float a0 = d0 * Mth.DEG_TO_RAD;
            float a1 = d1 * Mth.DEG_TO_RAD;
            float[][] pts = {
                    {0, 0, 0.5f, 0.5f},
                    {Mth.cos(a0) * radius, Mth.sin(a0) * radius, 0.5f + 0.5f * Mth.cos(a0), 0.5f - 0.5f * Mth.sin(a0)},
                    {Mth.cos(a1) * radius, Mth.sin(a1) * radius, 0.5f + 0.5f * Mth.cos(a1), 0.5f - 0.5f * Mth.sin(a1)},
            };
            int[] order = {0, 1, 2, 2, 2, 2, 1, 0};
            for (int k = 0; k < order.length; k++) {
                float[] p = pts[order[k]];
                vc.vertex(m, p[0], p[1], 0).color(1.0f, 1.0f, 1.0f, alpha).uv(p[2], p[3])
                        .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT)
                        .normal(normal, 0, 0, k < 4 ? 1 : -1).endVertex();
            }
            poseStack.popPose();
        }
        poseStack.popPose();
        super.render(shatter, yaw, partialTick, poseStack, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(SpaceShatter shatter) {
        return TEXTURE;
    }
}
