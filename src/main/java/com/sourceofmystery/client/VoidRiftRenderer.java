package com.sourceofmystery.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.sourceofmystery.entity.VoidRift;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

/**
 * 空间裂缝：竖直的锯齿状裂口，里面是末地传送门那种星空虚空（原版 end_portal 着色器），
 * 边缘一圈紫色辉光，撕开前后周围的空间会迸出闪烁的裂纹。全部是模型几何，不用粒子。
 */
public class VoidRiftRenderer extends EntityRenderer<VoidRift> {

    private static final int POINTS = 36;
    private static final float[] EDGE_INNER = {0.85f, 0.45f, 1.0f, 0.95f};
    private static final float[] EDGE_OUTER = {0.35f, 0.05f, 0.75f, 0.0f};
    private static final float[] CRACK = {0.95f, 0.8f, 1.0f, 0.9f};

    public VoidRiftRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(VoidRift rift, float yaw, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light) {
        float height = rift.riftHeight(partialTick);
        float width = rift.riftWidth(partialTick);
        if (height < 0.05f) {
            return;
        }
        float age = rift.tickCount + partialTick;
        long seed = rift.getId() * 31L + 7;
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(-rift.getYRot()));
        Matrix4f m = poseStack.last().pose();

        // 裂口轮廓：上下两端尖、中间宽的锯齿椭圆，边缘随时间轻微抖动
        float[][] rim = new float[POINTS][];
        float[][] normal = new float[POINTS][];
        for (int i = 0; i < POINTS; i++) {
            double a = i * Math.PI * 2 / POINTS;
            float jag = 1.0f + (EffectGeometry.hash(seed, i) - 0.5f) * 0.45f
                    + Mth.sin(age * 0.6f + i * 1.7f) * 0.04f;
            float cos = (float) Math.cos(a);
            float sin = (float) Math.sin(a);
            float pinch = (float) Math.pow(Math.abs(cos), 0.8);   // 让上下两端更尖
            float x = cos * width / 2 * jag * (0.35f + 0.65f * pinch);
            float y = sin * height / 2;
            rim[i] = new float[]{x, y, 0};
            float nx = cos / Math.max(0.05f, width);
            float ny = sin / Math.max(0.05f, height);
            float len = Mth.sqrt(nx * nx + ny * ny);
            normal[i] = new float[]{nx / len, ny / len, 0};
        }

        VertexConsumer voidBuffer = buffers.getBuffer(RenderType.endPortal());
        float[] center = {0, 0, 0};
        for (int i = 0; i < POINTS; i++) {
            float[] p = rim[i];
            float[] q = rim[(i + 1) % POINTS];
            EffectGeometry.voidQuad(voidBuffer, m, center, p, q, q);
        }

        VertexConsumer glow = buffers.getBuffer(RenderType.lightning());
        float edge = 0.25f + 0.1f * Mth.sin(age * 0.8f);
        for (int i = 0; i < POINTS; i++) {
            float[] p = rim[i];
            float[] q = rim[(i + 1) % POINTS];
            float[] np = normal[i];
            float[] nq = normal[(i + 1) % POINTS];
            EffectGeometry.glowQuad(glow, m, q, p,
                    new float[]{p[0] + np[0] * edge, p[1] + np[1] * edge, 0.01f},
                    new float[]{q[0] + nq[0] * edge, q[1] + nq[1] * edge, 0.01f},
                    EDGE_INNER, EDGE_OUTER);
        }

        // 空间裂纹：细缝阶段从两端向外延伸，撕开时向四周炸开；每 2 tick 换一次形状，看起来在噼啪作响
        boolean tearing = age > VoidRift.GROW_TICKS - 10 && age < VoidRift.GROW_TICKS + VoidRift.TEAR_TICKS + 30;
        int cracks = tearing ? 9 : 4;
        long flicker = seed + (long) (age / 2) * 977L;
        float alpha = 1 - rift.closeProgress(partialTick);
        float[] crackColor = {CRACK[0], CRACK[1], CRACK[2], CRACK[3] * alpha};
        for (int k = 0; k < cracks; k++) {
            int idx = tearing ? (int) (EffectGeometry.hash(flicker, k) * POINTS)
                    : (k % 2 == 0 ? POINTS / 4 : POINTS * 3 / 4) + (int) ((EffectGeometry.hash(flicker, k) - 0.5f) * 4);
            idx = Math.floorMod(idx, POINTS);
            float[] n = normal[idx];
            float[] side = {-n[1], n[0], 0};
            EffectGeometry.crack(glow, m, rim[idx], n, side, 5, tearing ? 0.9f : 0.5f, 0.06f,
                    flicker * 13 + k, crackColor);
        }
        poseStack.popPose();
        super.render(rift, yaw, partialTick, poseStack, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(VoidRift entity) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
