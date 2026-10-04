package com.sourceofmystery.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.sourceofmystery.entity.ThrownScythe;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.util.Mth;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/**
 * 掷出的镰刀：飞行 / 飞回时像风车一样高速旋转，插住时斜插着不动
 */
public class ThrownScytheRenderer extends GeoEntityRenderer<ThrownScythe> {

    public ThrownScytheRenderer(EntityRendererProvider.Context context) {
        super(context, new ThrownScytheModel());
        this.withScale(1.25f);
        this.shadowRadius = 0.0f;
    }

    @Override
    protected void applyRotations(ThrownScythe scythe, PoseStack poseStack, float ageInTicks, float rotationYaw, float partialTick) {
        float yaw = Mth.rotLerp(partialTick, scythe.yRotO, scythe.getYRot());
        poseStack.mulPose(Axis.YP.rotationDegrees(yaw));
        if (scythe.phase() == ThrownScythe.PHASE_STUCK) {
            poseStack.mulPose(Axis.XP.rotationDegrees(35.0f));
        } else {
            // 绕竖直轴旋转：刀刃画出一个水平的圆
            poseStack.mulPose(Axis.YP.rotationDegrees((scythe.tickCount + partialTick) * 48.0f));
        }
    }
}
