package com.sourceofmystery.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.sourceofmystery.entity.ThrownScythe;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.util.Mth;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/**
 * 掷出的镰刀：像车轮一样在飞行方向所在的竖直平面里高速旋转，上半圈向前滚，刀尖在旋转中始终朝前
 * （刀刃领先，而不是刀背或刀柄打头）；插住时刀头朝下斜插着。
 * 模型 weeping_scythe：握柄沿 z 轴，刀头在 -z 端，刀刃从刀头向 -y 伸出。渲染时让 -z 指向飞行方向，
 * 绕镰刀的重心（ROTATION_CENTER）旋转。
 */
public class ThrownScytheRenderer extends GeoEntityRenderer<ThrownScythe> {

    /** 旋转中心（模型坐标，像素）：靠近刀头的三分之一处，刀刃比较重 */
    private static final float CENTER_Y = 12.0f;
    private static final float CENTER_Z = -4.0f;
    private static final float SPIN_SPEED = 48.0f;   // 度 / tick

    public ThrownScytheRenderer(EntityRendererProvider.Context context) {
        super(context, new ThrownScytheModel());
        this.withScale(1.25f);
        this.shadowRadius = 0.0f;
    }

    @Override
    protected void applyRotations(ThrownScythe scythe, PoseStack poseStack, float ageInTicks, float rotationYaw, float partialTick) {
        float yaw = Mth.rotLerp(partialTick, scythe.yRotO, scythe.getYRot());
        float pitch = Mth.lerp(partialTick, scythe.xRotO, scythe.getXRot());
        poseStack.translate(0, CENTER_Y / 16.0f, 0);
        poseStack.mulPose(Axis.YP.rotationDegrees(yaw + 180.0f));
        if (scythe.phase() == ThrownScythe.PHASE_STUCK) {
            poseStack.mulPose(Axis.XP.rotationDegrees(-55.0f));
        } else {
            poseStack.mulPose(Axis.XP.rotationDegrees(pitch));
            // 负角度：上半圈朝飞行方向（-z）转，刀刃在前
            poseStack.mulPose(Axis.XP.rotationDegrees(-(scythe.tickCount + partialTick) * SPIN_SPEED));
        }
        poseStack.translate(0, -CENTER_Y / 16.0f, -CENTER_Z / 16.0f);
    }
}
