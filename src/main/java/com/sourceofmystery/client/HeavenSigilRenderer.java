package com.sourceofmystery.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.sourceofmystery.SourceOfMystery;
import com.sourceofmystery.entity.HeavenSigil;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.Heightmap;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * 天上的金色法阵：两层自发光的法阵贴图朝相反方向旋转，正下方垂下一道金色光柱照到地面。
 */
public class HeavenSigilRenderer extends EntityRenderer<HeavenSigil> {

    private static final ResourceLocation OUTER = new ResourceLocation(SourceOfMystery.MOD_ID, "textures/entity/heaven_sigil.png");
    private static final ResourceLocation INNER = new ResourceLocation(SourceOfMystery.MOD_ID, "textures/entity/heaven_sigil_inner.png");
    private static final int FULL_BRIGHT = 0xF000F0;

    public HeavenSigilRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(HeavenSigil sigil, float yaw, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light) {
        float radius = sigil.radius(partialTick);
        float alpha = sigil.alpha(partialTick);
        if (radius < 0.1f || alpha <= 0.01f) {
            return;
        }
        float age = sigil.tickCount + partialTick;
        float pulse = 0.85f + 0.15f * Mth.sin(age * 0.15f);

        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(age * 0.6f));
        disk(poseStack, buffers.getBuffer(RenderType.eyes(OUTER)), radius, alpha * pulse);
        poseStack.popPose();

        poseStack.pushPose();
        poseStack.translate(0, -0.3, 0);
        poseStack.mulPose(Axis.YP.rotationDegrees(-age * 1.1f));
        disk(poseStack, buffers.getBuffer(RenderType.eyes(INNER)), radius * 0.62f, alpha);
        poseStack.popPose();

        // 光柱：从法阵中心垂到地面
        int ground = sigil.level().getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(sigil.getX()), Mth.floor(sigil.getZ()));
        float depth = (float) Math.max(4.0, sigil.getY() - ground);
        float beam = radius * 0.12f * pulse;
        VertexConsumer glow = buffers.getBuffer(RenderType.lightning());
        Matrix4f m = poseStack.last().pose();
        float[] top = {1.0f, 0.85f, 0.4f, 0.55f * alpha};
        float[] bottom = {1.0f, 0.7f, 0.2f, 0.15f * alpha};
        int sides = 12;
        for (int i = 0; i < sides; i++) {
            double a0 = i * Math.PI * 2 / sides + age * 0.05;
            double a1 = (i + 1) * Math.PI * 2 / sides + age * 0.05;
            float x0 = (float) Math.cos(a0) * beam;
            float z0 = (float) Math.sin(a0) * beam;
            float x1 = (float) Math.cos(a1) * beam;
            float z1 = (float) Math.sin(a1) * beam;
            EffectGeometry.glowQuad(glow, m,
                    new float[]{x1, 0, z1}, new float[]{x0, 0, z0},
                    new float[]{x0, -depth, z0}, new float[]{x1, -depth, z1}, top, bottom);
        }
        super.render(sigil, yaw, partialTick, poseStack, buffers, light);
    }

    private static void disk(PoseStack poseStack, VertexConsumer vc, float r, float alpha) {
        Matrix4f m = poseStack.last().pose();
        Matrix3f n = poseStack.last().normal();
        float c = alpha;
        // 正反两面（从下往上看也能看到）
        vertex(vc, m, n, -r, -r, 0, 0, c);
        vertex(vc, m, n, -r, r, 0, 1, c);
        vertex(vc, m, n, r, r, 1, 1, c);
        vertex(vc, m, n, r, -r, 1, 0, c);
        vertex(vc, m, n, r, -r, 1, 0, c);
        vertex(vc, m, n, r, r, 1, 1, c);
        vertex(vc, m, n, -r, r, 0, 1, c);
        vertex(vc, m, n, -r, -r, 0, 0, c);
    }

    private static void vertex(VertexConsumer vc, Matrix4f m, Matrix3f n, float x, float z, float u, float v, float c) {
        vc.vertex(m, x, 0, z).color(c, c, c, c).uv(u, v).overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(FULL_BRIGHT).normal(n, 0, 1, 0).endVertex();
    }

    @Override
    public ResourceLocation getTextureLocation(HeavenSigil entity) {
        return OUTER;
    }
}
