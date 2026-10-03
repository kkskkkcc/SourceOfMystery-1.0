package com.sourceofmystery.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.sourceofmystery.entity.AbyssOrb;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

/**
 * 天渊黑球：一个漆黑的球体（内部是末地传送门那样的虚空星点），表面缠绕着噼啪闪烁的紫黑色电弧。
 */
public class AbyssOrbRenderer extends EntityRenderer<AbyssOrb> {

    private static final int LAT = 12;
    private static final int LON = 18;
    private static final float[] ARC = {0.55f, 0.15f, 0.85f, 0.9f};

    public AbyssOrbRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(AbyssOrb orb, float yaw, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light) {
        float r = orb.radius();
        if (r < 0.05f) {
            return;
        }
        float age = orb.tickCount + partialTick;
        r *= 1.0f + 0.03f * Mth.sin(age * 0.9f);
        Matrix4f m = poseStack.last().pose();

        VertexConsumer voidBuffer = buffers.getBuffer(RenderType.endPortal());
        for (int i = 0; i < LAT; i++) {
            double t0 = Math.PI * i / LAT - Math.PI / 2;
            double t1 = Math.PI * (i + 1) / LAT - Math.PI / 2;
            for (int j = 0; j < LON; j++) {
                double p0 = Math.PI * 2 * j / LON;
                double p1 = Math.PI * 2 * (j + 1) / LON;
                EffectGeometry.voidQuad(voidBuffer, m, point(r, t0, p0), point(r, t0, p1), point(r, t1, p1), point(r, t1, p0));
            }
        }

        // 电弧：从球面向外伸出的折线，每 2 tick 换一次形状
        VertexConsumer glow = buffers.getBuffer(RenderType.lightning());
        long flicker = orb.getId() * 131L + (long) (age / 2) * 7919L;
        int arcs = 10;
        for (int k = 0; k < arcs; k++) {
            double theta = (EffectGeometry.hash(flicker, k * 3) - 0.5) * Math.PI;
            double phi = EffectGeometry.hash(flicker, k * 3 + 1) * Math.PI * 2;
            float[] start = point(r * 0.98f, theta, phi);
            float[] dir = point(1.0f, theta, phi);
            float[] side = {(float) -Math.sin(phi), 0, (float) Math.cos(phi)};
            EffectGeometry.crack(glow, m, start, dir, side, 4, r * 0.22f, Math.max(0.05f, r * 0.03f), flicker + k, ARC);
        }
        super.render(orb, yaw, partialTick, poseStack, buffers, light);
    }

    private static float[] point(float r, double theta, double phi) {
        return new float[]{
                (float) (Math.cos(theta) * Math.cos(phi) * r),
                (float) (Math.sin(theta) * r),
                (float) (Math.cos(theta) * Math.sin(phi) * r)};
    }

    @Override
    public ResourceLocation getTextureLocation(AbyssOrb entity) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
