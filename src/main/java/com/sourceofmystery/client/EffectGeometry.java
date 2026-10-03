package com.sourceofmystery.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Matrix4f;

/**
 * 出场演出渲染器共用的小工具：往 RenderType.endPortal()（只有坐标）和 RenderType.lightning()（坐标 + 颜色）里写四边形
 */
final class EffectGeometry {

    private EffectGeometry() {
    }

    /** 双面四边形（虚空填充用，endPortal 格式只有坐标） */
    static void voidQuad(VertexConsumer vc, Matrix4f m, float[] a, float[] b, float[] c, float[] d) {
        vc.vertex(m, a[0], a[1], a[2]).endVertex();
        vc.vertex(m, b[0], b[1], b[2]).endVertex();
        vc.vertex(m, c[0], c[1], c[2]).endVertex();
        vc.vertex(m, d[0], d[1], d[2]).endVertex();
        vc.vertex(m, d[0], d[1], d[2]).endVertex();
        vc.vertex(m, c[0], c[1], c[2]).endVertex();
        vc.vertex(m, b[0], b[1], b[2]).endVertex();
        vc.vertex(m, a[0], a[1], a[2]).endVertex();
    }

    /** 双面发光四边形：a、b 一侧用颜色 c1，c、d 一侧用颜色 c2（RGBA） */
    static void glowQuad(VertexConsumer vc, Matrix4f m, float[] a, float[] b, float[] c, float[] d, float[] c1, float[] c2) {
        vc.vertex(m, a[0], a[1], a[2]).color(c1[0], c1[1], c1[2], c1[3]).endVertex();
        vc.vertex(m, b[0], b[1], b[2]).color(c1[0], c1[1], c1[2], c1[3]).endVertex();
        vc.vertex(m, c[0], c[1], c[2]).color(c2[0], c2[1], c2[2], c2[3]).endVertex();
        vc.vertex(m, d[0], d[1], d[2]).color(c2[0], c2[1], c2[2], c2[3]).endVertex();
        vc.vertex(m, d[0], d[1], d[2]).color(c2[0], c2[1], c2[2], c2[3]).endVertex();
        vc.vertex(m, c[0], c[1], c[2]).color(c2[0], c2[1], c2[2], c2[3]).endVertex();
        vc.vertex(m, b[0], b[1], b[2]).color(c1[0], c1[1], c1[2], c1[3]).endVertex();
        vc.vertex(m, a[0], a[1], a[2]).color(c1[0], c1[1], c1[2], c1[3]).endVertex();
    }

    /** 确定性的伪随机数 [0,1)：同一实体同一编号每帧都一样，避免锯齿形状闪烁 */
    static float hash(long seed, int i) {
        long h = seed * 0x9E3779B97F4A7C15L + i * 0xC2B2AE3D27D4EB4FL;
        h ^= h >>> 29;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 32;
        return (h >>> 40) / (float) (1L << 24);
    }

    /** 一段折线状的电弧 / 裂纹，在给定平面内（u、v 为平面的两个方向，世界坐标系里的局部坐标） */
    static void crack(VertexConsumer vc, Matrix4f m, float[] start, float[] dir, float[] side, int segments,
                      float segLength, float width, long seed, float[] color) {
        float[] p = start.clone();
        float[] col0 = color;
        for (int s = 0; s < segments; s++) {
            float jitter = (hash(seed, s) - 0.5f) * 1.4f;
            float[] q = new float[]{
                    p[0] + (dir[0] + side[0] * jitter) * segLength,
                    p[1] + (dir[1] + side[1] * jitter) * segLength,
                    p[2] + (dir[2] + side[2] * jitter) * segLength};
            float w0 = width * (1 - s / (float) segments);
            float w1 = width * (1 - (s + 1) / (float) segments);
            float[] col1 = new float[]{color[0], color[1], color[2], color[3] * (1 - (s + 1) / (float) segments)};
            glowQuad(vc, m,
                    new float[]{p[0] - side[0] * w0, p[1] - side[1] * w0, p[2] - side[2] * w0},
                    new float[]{p[0] + side[0] * w0, p[1] + side[1] * w0, p[2] + side[2] * w0},
                    new float[]{q[0] + side[0] * w1, q[1] + side[1] * w1, q[2] + side[2] * w1},
                    new float[]{q[0] - side[0] * w1, q[1] - side[1] * w1, q[2] - side[2] * w1},
                    col0, col1);
            col0 = col1;
            p = q;
        }
    }
}
