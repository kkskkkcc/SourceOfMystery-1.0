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
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.joml.Vector3f;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

/**
 * 泣死之主渲染器：
 * - 整体放大 1.25 倍（同 YSM 模型的 height_scale）；镰刀掷出后隐藏手里的镰刀骨骼
 * - 眼睛的白光（weeping_death_lord_glow.png，亮度随出场睁眼逐渐增强）
 * - 定位骨骼上的特效：背后的黑色神环（Halo）、吸附时左手的虚空黑球（DarkOrb）、大招的黑色魔法阵（MagicCircle）
 * - 大招激光：从魔法阵射到服务端同步的终点
 * - 快速攻击时的半透明残影
 * 定位骨骼的世界坐标会记到实体上（orbPos / bladePos / haloPos），实体在客户端 tick 里按这些位置放粒子。
 */
public class WeepingDeathLordRenderer extends GeoEntityRenderer<WeepingDeathLord> {

    private static final ResourceLocation GLOW = tex("weeping_death_lord_glow");
    private static final ResourceLocation HALO = tex("weeping_halo");
    private static final ResourceLocation HALO_GLOW = tex("weeping_halo_glow");
    private static final ResourceLocation CIRCLE = tex("weeping_magic_circle");
    private static final ResourceLocation CIRCLE_GLOW = tex("weeping_magic_circle_glow");
    private static final ResourceLocation VOID_ORB = tex("weeping_void_orb");
    private static final ResourceLocation LASER = tex("weeping_laser");

    private static final float HALO_RADIUS = 1.05f;    // 骨骼空间（已经乘上 1.25 倍缩放）
    private static final float CIRCLE_RADIUS = 1.1f;
    private static final float ORB_RADIUS = 0.75f;     // 视图空间，单位：格

    /** 本帧魔法阵骨骼相对实体渲染原点的位置（激光起点） */
    private Vec3 circleLocal;

    public WeepingDeathLordRenderer(EntityRendererProvider.Context context) {
        super(context, new WeepingDeathLordModel());
        this.shadowRadius = 0.9F;
        this.withScale(1.25f);
        this.addRenderLayer(new EyeGlowLayer(this));
        this.addRenderLayer(new EffectsLayer(this));
    }

    private static ResourceLocation tex(String name) {
        return new ResourceLocation(SourceOfMystery.MOD_ID, "textures/entity/" + name + ".png");
    }

    @Override
    public void renderRecursively(PoseStack poseStack, WeepingDeathLord entity, GeoBone bone, RenderType renderType,
                                  MultiBufferSource bufferSource, VertexConsumer buffer, boolean isReRender, float partialTick,
                                  int packedLight, int packedOverlay, float red, float green, float blue, float alpha) {
        String name = bone.getName();
        boolean locator = name.equals("DarkOrb") || name.equals("ScytheBlade") || name.equals("Halo") || name.equals("MagicCircle");
        if (name.equals("Scythe")) {
            bone.setHidden(entity.scytheOut());
        }
        if (locator) {
            bone.setTrackingMatrices(true);
        }
        super.renderRecursively(poseStack, entity, bone, renderType, bufferSource, buffer, isReRender, partialTick,
                packedLight, packedOverlay, red, green, blue, alpha);
        if (locator && !isReRender) {
            Vector3d w = bone.getWorldPosition();
            Vec3 world = new Vec3(w.x, w.y, w.z);
            switch (name) {
                case "DarkOrb" -> entity.orbPos = world;
                case "ScytheBlade" -> entity.bladePos = world;
                case "Halo" -> entity.haloPos = world;
                default -> {
                    Vector3d l = bone.getLocalPosition();
                    circleLocal = new Vec3(l.x, l.y, l.z);
                }
            }
        }
    }

    @Override
    public void render(WeepingDeathLord entity, float entityYaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight) {
        circleLocal = null;
        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
        if (entity.laserActive() && !entity.isInvisible()) {
            renderLaser(entity, partialTick, poseStack, bufferSource);
        }
        renderAfterimages(entity, partialTick, poseStack, bufferSource);
    }

    private void renderAfterimages(WeepingDeathLord entity, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource) {
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

    /**
     * 白色激光：从胸前的魔法阵射向终点。两层面向镜头的光带（加法混合）：细的白色核心 + 半径 1 格的淡紫色光晕，
     * 纹理沿激光方向流动；另外在胸口和终点各画一团光。
     */
    private void renderLaser(WeepingDeathLord entity, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource) {
        Vec3 origin = entity.getPosition(partialTick);
        Vec3 start = circleLocal;
        if (start == null) {
            float yaw = Mth.rotLerp(partialTick, entity.yRotO, entity.getYRot()) * Mth.DEG_TO_RAD;
            start = new Vec3(-Mth.sin(yaw) * 0.33, 2.56, Mth.cos(yaw) * 0.33);
        }
        Vec3 end = entity.laserEnd().subtract(origin);
        Vec3 axis = end.subtract(start);
        double length = axis.length();
        if (length < 0.1) {
            return;
        }
        Vec3 dir = axis.scale(1 / length);
        Vec3 toCamera = this.entityRenderDispatcher.camera.getPosition().subtract(origin).subtract(start);
        Vec3 side = dir.cross(toCamera);
        side = side.lengthSqr() < 1.0E-6 ? new Vec3(0, 1, 0) : side.normalize();
        float time = entity.tickCount + partialTick;
        float flicker = 0.9f + 0.1f * Mth.sin(time * 1.7f);
        VertexConsumer vc = bufferSource.getBuffer(RenderType.eyes(LASER));
        PoseStack.Pose pose = poseStack.last();
        float scroll = -time * 0.15f;
        ribbon(vc, pose, start, end, side, (float) WeepingDeathLord.LASER_RADIUS * 1.35f * flicker, scroll, (float) length / 6f, 0.55f);
        ribbon(vc, pose, start, end, side, (float) WeepingDeathLord.LASER_RADIUS * 0.7f * flicker, scroll * 1.6f, (float) length / 4f, 1.0f);
        // 和镜头方向垂直的第二层，让光柱从侧面看也是实心的
        Vec3 side2 = dir.cross(side).normalize();
        ribbon(vc, pose, start, end, side2, (float) WeepingDeathLord.LASER_RADIUS * 0.7f * flicker, scroll * 1.3f, (float) length / 4f, 0.6f);
        flare(vc, poseStack, start, 1.4f * flicker, time);
        flare(vc, poseStack, end, 1.8f * flicker, -time);
    }

    private static void ribbon(VertexConsumer vc, PoseStack.Pose pose, Vec3 a, Vec3 b, Vec3 side, float half,
                               float v0, float repeat, float bright) {
        Vec3 s = side.scale(half);
        Matrix4f m = pose.pose();
        Matrix3f n = pose.normal();
        float c = bright;
        // eyes 渲染类型开着背面剔除：正反两面都画
        vertex(vc, m, n, a.add(s), 0, v0, c);
        vertex(vc, m, n, a.subtract(s), 1, v0, c);
        vertex(vc, m, n, b.subtract(s), 1, v0 + repeat, c);
        vertex(vc, m, n, b.add(s), 0, v0 + repeat, c);
        vertex(vc, m, n, b.add(s), 0, v0 + repeat, c);
        vertex(vc, m, n, b.subtract(s), 1, v0 + repeat, c);
        vertex(vc, m, n, a.subtract(s), 1, v0, c);
        vertex(vc, m, n, a.add(s), 0, v0, c);
    }

    /** 一团面向镜头的光（用激光贴图的中心部分） */
    private void flare(VertexConsumer vc, PoseStack poseStack, Vec3 at, float size, float spin) {
        poseStack.pushPose();
        poseStack.translate(at.x, at.y, at.z);
        poseStack.mulPose(this.entityRenderDispatcher.cameraOrientation());
        poseStack.mulPose(Axis.ZP.rotationDegrees(spin * 4));
        PoseStack.Pose pose = poseStack.last();
        for (int i = 0; i < 2; i++) {
            float r = size * (i == 0 ? 1.0f : 0.55f);
            vertex(vc, pose.pose(), pose.normal(), new Vec3(-r, -r, 0), 0.25f, 0, 0.8f);
            vertex(vc, pose.pose(), pose.normal(), new Vec3(r, -r, 0), 0.75f, 0, 0.8f);
            vertex(vc, pose.pose(), pose.normal(), new Vec3(r, r, 0), 0.75f, 1, 0.8f);
            vertex(vc, pose.pose(), pose.normal(), new Vec3(-r, r, 0), 0.25f, 1, 0.8f);
            vertex(vc, pose.pose(), pose.normal(), new Vec3(-r, r, 0), 0.25f, 1, 0.8f);
            vertex(vc, pose.pose(), pose.normal(), new Vec3(r, r, 0), 0.75f, 1, 0.8f);
            vertex(vc, pose.pose(), pose.normal(), new Vec3(r, -r, 0), 0.75f, 0, 0.8f);
            vertex(vc, pose.pose(), pose.normal(), new Vec3(-r, -r, 0), 0.25f, 0, 0.8f);
        }
        poseStack.popPose();
    }

    private static void vertex(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 p, float u, float v, float bright) {
        vc.vertex(m, (float) p.x, (float) p.y, (float) p.z).color(bright, bright, bright, 1.0f).uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT).normal(n, 0, 1, 0).endVertex();
    }

    /** 在当前矩阵的 XY 平面上画一个以原点为中心的方形贴图；doubleSided：反面也画（eyes 渲染类型会剔除背面） */
    private static void quad(VertexConsumer vc, PoseStack.Pose pose, float r, float red, float green, float blue, float alpha,
                             boolean doubleSided) {
        float[][] corners = {{-r, -r, 0, 1}, {r, -r, 1, 1}, {r, r, 1, 0}, {-r, r, 0, 0}};
        for (int side = 0; side < (doubleSided ? 2 : 1); side++) {
            for (int i = 0; i < 4; i++) {
                float[] c = corners[side == 0 ? i : 3 - i];
                vc.vertex(pose.pose(), c[0], c[1], 0).color(red, green, blue, alpha).uv(c[2], c[3])
                        .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT)
                        .normal(pose.normal(), 0, 0, side == 0 ? 1 : -1).endVertex();
            }
        }
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

    /**
     * 定位骨骼上的特效。画完要重新取一次主渲染类型的缓冲（GeckoLib 的要求），后面的骨骼才能接着画。
     */
    private static class EffectsLayer extends GeoRenderLayer<WeepingDeathLord> {

        EffectsLayer(WeepingDeathLordRenderer renderer) {
            super(renderer);
        }

        @Override
        public void renderForBone(PoseStack poseStack, WeepingDeathLord entity, GeoBone bone, RenderType renderType,
                                  MultiBufferSource bufferSource, VertexConsumer buffer, float partialTick, int packedLight, int packedOverlay) {
            String name = bone.getName();
            if (!name.equals("Halo") && !name.equals("MagicCircle") && !name.equals("DarkOrb")) {
                return;
            }
            float time = entity.tickCount + partialTick;
            // GeckoLib 调用图层时矩阵已经移回模型原点（脚底），要自己挪到骨骼的枢轴点上
            poseStack.pushPose();
            poseStack.translate(bone.getPivotX() / 16f, bone.getPivotY() / 16f, bone.getPivotZ() / 16f);
            switch (name) {
                case "Halo" -> renderHalo(poseStack, entity, bufferSource, time);
                case "MagicCircle" -> renderCircle(poseStack, entity, bufferSource, time);
                default -> renderOrb(poseStack, entity, bufferSource, time);
            }
            poseStack.popPose();
            bufferSource.getBuffer(renderType);
        }

        /** 背后的黑色神环：随身体转动，缓慢自转；出场时随睁眼慢慢浮现 */
        private void renderHalo(PoseStack poseStack, WeepingDeathLord entity, MultiBufferSource bufferSource, float time) {
            float show = entity.eyeGlow();
            if (show <= 0.01f) {
                return;
            }
            poseStack.pushPose();
            poseStack.mulPose(Axis.ZP.rotationDegrees(time * 1.2f));
            float r = HALO_RADIUS * (0.9f + 0.1f * show) * (1.0f + 0.015f * Mth.sin(time * 0.1f));
            quad(bufferSource.getBuffer(RenderType.entityTranslucent(HALO)), poseStack.last(), r, 1, 1, 1, show, false);
            float pulse = (0.7f + 0.3f * Mth.sin(time * 0.08f)) * show;
            quad(bufferSource.getBuffer(RenderType.eyes(HALO_GLOW)), poseStack.last(), r, pulse, pulse, pulse, 1, true);
            poseStack.popPose();
        }

        /** 大招的黑色魔法阵：两层反向旋转，白紫色线条发光 */
        private void renderCircle(PoseStack poseStack, WeepingDeathLord entity, MultiBufferSource bufferSource, float time) {
            float size = entity.circleSize();
            if (size <= 0.01f) {
                return;
            }
            float r = CIRCLE_RADIUS * size;
            float glow = entity.laserActive() ? 1.0f : 0.55f + 0.25f * Mth.sin(time * 0.4f);
            poseStack.pushPose();
            poseStack.mulPose(Axis.ZP.rotationDegrees(time * 3.0f));
            quad(bufferSource.getBuffer(RenderType.entityTranslucent(CIRCLE)), poseStack.last(), r, 1, 1, 1, 0.95f, false);
            quad(bufferSource.getBuffer(RenderType.eyes(CIRCLE_GLOW)), poseStack.last(), r, glow, glow, glow, 1, true);
            poseStack.mulPose(Axis.ZP.rotationDegrees(-time * 7.0f));
            poseStack.translate(0, 0, -0.06f);
            quad(bufferSource.getBuffer(RenderType.eyes(CIRCLE_GLOW)), poseStack.last(), r * 0.62f, glow * 0.6f, glow * 0.6f, glow * 0.6f, 1, true);
            poseStack.popPose();
        }

        /** 吸附的虚空黑球：始终正对镜头的黑洞，外圈的吸积旋涡不停旋转 */
        private void renderOrb(PoseStack poseStack, WeepingDeathLord entity, MultiBufferSource bufferSource, float time) {
            float size = entity.orbSize();
            if (size <= 0.01f) {
                return;
            }
            Vector3f c = poseStack.last().pose().transformPosition(new Vector3f());
            PoseStack bill = new PoseStack();
            bill.translate(c.x(), c.y(), c.z());
            float r = ORB_RADIUS * size * (1.0f + 0.05f * Mth.sin(time * 0.6f));
            VertexConsumer vc = bufferSource.getBuffer(RenderType.entityTranslucent(VOID_ORB));
            bill.pushPose();
            bill.mulPose(Axis.ZP.rotationDegrees(time * 9.0f));
            quad(vc, bill.last(), r * 1.25f, 1, 1, 1, 0.75f, false);
            bill.popPose();
            bill.mulPose(Axis.ZP.rotationDegrees(-time * 5.0f));
            bill.translate(0, 0, 0.01f);
            quad(vc, bill.last(), r, 1, 1, 1, 1.0f, false);
        }
    }
}
