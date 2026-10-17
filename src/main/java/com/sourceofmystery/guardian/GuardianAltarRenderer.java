package com.sourceofmystery.guardian;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.sourceofmystery.SourceOfMystery;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.model.DefaultedBlockGeoModel;
import software.bernie.geckolib.renderer.GeoBlockRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/**
 * 守护祭坛的渲染器。
 * <ul>
 *     <li>GeckoLib 模型 + 自动发光层（guardian_altar_glowmask.png）；法阵、光球是半透明的，用 entityTranslucent</li>
 *     <li>结界展开后：从模型里的光柱顶端继续往上画到 y = 255 的信标式光柱（guardian_altar_beam.png，竖向平铺滚动）</li>
 *     <li>护罩：3x3 区块（48x48 格）、世界底部到 y = 255 的半透明六边形能量罩（guardian_altar_shield.png，四面墙 + 顶）</li>
 * </ul>
 * 贴图、模型、动画由 F:\AAAmc模型自动化\3ba 的脚本生成。
 */
public class GuardianAltarRenderer extends GeoBlockRenderer<GuardianAltarBlockEntity> {

    private static final ResourceLocation BEAM = new ResourceLocation(SourceOfMystery.MOD_ID,
            "textures/block/guardian_altar_beam.png");
    private static final ResourceLocation SHIELD = new ResourceLocation(SourceOfMystery.MOD_ID,
            "textures/block/guardian_altar_shield.png");

    /** 模型里的光柱：从下界之星（13 像素高）往上 64 像素，半宽 1.5 像素；天空光柱从它的顶端接上 */
    private static final float BEAM_START = (13 + 64) / 16.0f;
    private static final float BEAM_HALF = 1.5f / 16.0f;
    /** 光柱贴图（16x64）每张对应的高度（格） */
    private static final float BEAM_TILE = 4.0f;
    /** 护罩贴图每张对应的边长（格） */
    private static final float SHIELD_TILE = 4.0f;
    private static final int FULL_BRIGHT = LightTexture.FULL_BRIGHT;

    public GuardianAltarRenderer(BlockEntityRendererProvider.Context context) {
        super(new DefaultedBlockGeoModel<>(new ResourceLocation(SourceOfMystery.MOD_ID, "guardian_altar")));
        addRenderLayer(new AutoGlowingGeoLayer<>(this));
    }

    @Override
    public RenderType getRenderType(GuardianAltarBlockEntity animatable, ResourceLocation texture,
                                    MultiBufferSource bufferSource, float partialTick) {
        return RenderType.entityTranslucent(texture);
    }

    /**
     * 模型画完、姿势栈恢复到方块原点之后画光柱和护罩。
     * GeoBlockRenderer 的 render() 参数是原始 BlockEntity 类型，子类没法覆盖，所以挂在 renderFinal 上
     */
    @Override
    public void renderFinal(PoseStack poseStack, GuardianAltarBlockEntity altar, BakedGeoModel model,
                            MultiBufferSource bufferSource, VertexConsumer buffer, float partialTick, int packedLight,
                            int packedOverlay, float red, float green, float blue, float alpha) {
        super.renderFinal(poseStack, altar, model, bufferSource, buffer, partialTick, packedLight, packedOverlay,
                red, green, blue, alpha);
        Level level = altar.getLevel();
        if (level == null || !altar.shieldDeployed(level)) {
            return;
        }
        float time = level.getGameTime() + partialTick;
        // 刚展开的 0.6 秒里光柱从下往上长出去、护罩渐显（和动画 7.4 秒的冲击环同步）
        float sinceDeploy = (altar.age(level) - GuardianShields.DEPLOY_TICKS + partialTick) / 20.0f;
        float grow = Mth.clamp(sinceDeploy / 0.6f, 0.0f, 1.0f);
        float fade = Mth.clamp(sinceDeploy / 1.0f, 0.0f, 1.0f);
        BlockPos pos = altar.getBlockPos();
        renderBeam(poseStack, bufferSource, pos, time, grow);
        renderShield(poseStack, bufferSource, level, pos, time, fade);
    }

    // ------------------------------------------------------------------ 光柱

    private void renderBeam(PoseStack poseStack, MultiBufferSource buffers, BlockPos pos, float time, float grow) {
        float top = GuardianShields.TOP_Y + 1 - pos.getY();
        float y0 = BEAM_START;
        float y1 = y0 + Math.max(0, top - y0) * grow;
        if (y1 <= y0) {
            return;
        }
        // 呼吸：和动画里 beam 骨骼 3 秒一次的粗细变化一致
        float r = BEAM_HALF * (1.0f + 0.08f * Mth.sin(time / 20.0f * Mth.TWO_PI / 3.0f));
        float v0 = -time * 0.025f;                 // 往上流动
        float v1 = v0 + (y1 - y0) / BEAM_TILE;
        poseStack.pushPose();
        poseStack.translate(0.5, 0, 0.5);
        Matrix4f m = poseStack.last().pose();
        Matrix3f n = poseStack.last().normal();
        VertexConsumer vc = buffers.getBuffer(RenderType.beaconBeam(BEAM, true));
        // 内芯（亮）+ 外层辉光（大一圈、淡）
        prism(vc, m, n, r, y0, y1, v0, v1, 255, 255, 255, 235);
        prism(vc, m, n, r * 2.6f, y0, y1, v0 * 0.7f, v0 * 0.7f + (y1 - y0) / BEAM_TILE, 190, 225, 255, 70);
        poseStack.popPose();
    }

    /** 竖直的四棱柱（四个侧面，正反面都画） */
    private static void prism(VertexConsumer vc, Matrix4f m, Matrix3f n, float r, float y0, float y1,
                              float v0, float v1, int cr, int cg, int cb, int ca) {
        float[][] corners = {{-r, -r}, {r, -r}, {r, r}, {-r, r}};
        for (int i = 0; i < 4; i++) {
            float[] a = corners[i], b = corners[(i + 1) % 4];
            wall(vc, m, n, a[0], a[1], b[0], b[1], y0, y1, 0, 1, v0, v1, cr, cg, cb, ca);
            wall(vc, m, n, b[0], b[1], a[0], a[1], y0, y1, 0, 1, v0, v1, cr, cg, cb, ca);
        }
    }

    // ------------------------------------------------------------------ 护罩

    private void renderShield(PoseStack poseStack, MultiBufferSource buffers, Level level, BlockPos pos, float time,
                              float fade) {
        AABB area = GuardianShields.area(level, pos);
        // 相对方块原点的坐标
        float x0 = (float) (area.minX - pos.getX()), x1 = (float) (area.maxX - pos.getX());
        float z0 = (float) (area.minZ - pos.getZ()), z1 = (float) (area.maxZ - pos.getZ());
        float y0 = (float) (area.minY - pos.getY()), y1 = (float) (area.maxY - pos.getY());
        // 轻微的明暗呼吸，六边形网格缓缓向上流动
        float pulse = 0.85f + 0.15f * Mth.sin(time / 20.0f * Mth.TWO_PI / 4.0f);
        int alpha = (int) (110 * fade * pulse);
        if (alpha <= 2) {
            return;
        }
        float scroll = time * 0.002f;
        float uw = (x1 - x0) / SHIELD_TILE;
        float vh = (y1 - y0) / SHIELD_TILE;
        Matrix4f m = poseStack.last().pose();
        Matrix3f n = poseStack.last().normal();
        VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucentEmissive(SHIELD));
        int r = 200, g = 232, b = 255;
        // 四面墙（entityTranslucent 系列不剔除背面，里外都看得到）
        wall(vc, m, n, x0, z0, x1, z0, y0, y1, 0, uw, scroll, scroll + vh, r, g, b, alpha);
        wall(vc, m, n, x1, z0, x1, z1, y0, y1, 0, uw, scroll, scroll + vh, r, g, b, alpha);
        wall(vc, m, n, x1, z1, x0, z1, y0, y1, 0, uw, scroll, scroll + vh, r, g, b, alpha);
        wall(vc, m, n, x0, z1, x0, z0, y0, y1, 0, uw, scroll, scroll + vh, r, g, b, alpha);
        // 顶
        float s = scroll * 0.5f;
        vertex(vc, m, n, x0, y1, z0, s, s, r, g, b, alpha, 0, -1, 0);
        vertex(vc, m, n, x1, y1, z0, s + uw, s, r, g, b, alpha, 0, -1, 0);
        vertex(vc, m, n, x1, y1, z1, s + uw, s + uw, r, g, b, alpha, 0, -1, 0);
        vertex(vc, m, n, x0, y1, z1, s, s + uw, r, g, b, alpha, 0, -1, 0);
    }

    // ------------------------------------------------------------------ 顶点工具

    /** 从 (ax, az) 到 (bx, bz) 的竖直矩形，y0..y1；贴图 u 沿水平方向、v 沿竖直方向（v1 在顶端） */
    private static void wall(VertexConsumer vc, Matrix4f m, Matrix3f n, float ax, float az, float bx, float bz,
                             float y0, float y1, float u0, float u1, float v0, float v1,
                             int r, int g, int b, int a) {
        float nx = bz - az, nz = ax - bx;
        float len = Mth.sqrt(nx * nx + nz * nz);
        if (len > 1.0E-6f) {
            nx /= len;
            nz /= len;
        }
        vertex(vc, m, n, ax, y0, az, u0, v1, r, g, b, a, nx, 0, nz);
        vertex(vc, m, n, bx, y0, bz, u1, v1, r, g, b, a, nx, 0, nz);
        vertex(vc, m, n, bx, y1, bz, u1, v0, r, g, b, a, nx, 0, nz);
        vertex(vc, m, n, ax, y1, az, u0, v0, r, g, b, a, nx, 0, nz);
    }

    private static void vertex(VertexConsumer vc, Matrix4f m, Matrix3f n, float x, float y, float z, float u, float v,
                               int r, int g, int b, int a, float nx, float ny, float nz) {
        vc.vertex(m, x, y, z).color(r, g, b, a).uv(u, v).overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(FULL_BRIGHT).normal(n, nx, ny, nz).endVertex();
    }

    // ------------------------------------------------------------------ 可见距离

    @Override
    public boolean shouldRenderOffScreen(GuardianAltarBlockEntity altar) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 256;
    }

    @Override
    public boolean shouldRender(GuardianAltarBlockEntity altar, Vec3 cameraPos) {
        // 激活后：光柱和护罩很大，只要在渲染距离内就画；待激活：和普通方块实体一样
        return altar.isActive() || super.shouldRender(altar, cameraPos);
    }
}
