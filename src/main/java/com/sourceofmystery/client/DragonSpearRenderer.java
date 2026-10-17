package com.sourceofmystery.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.sourceofmystery.entity.DragonSpear;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.util.Mth;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/** Uses the same blade geometry and texture as the weapon held by Dragon Soul. */
public class DragonSpearRenderer extends GeoEntityRenderer<DragonSpear> {
    public DragonSpearRenderer(EntityRendererProvider.Context context) {
        super(context, new DragonSpearModel());
        this.shadowRadius = 0.0f;
    }

    @Override
    protected void applyRotations(DragonSpear spear, PoseStack stack, float age, float yaw, float partialTick) {
        stack.mulPose(Axis.YP.rotationDegrees(Mth.rotLerp(partialTick, spear.yRotO, spear.getYRot()) + 180.0f));
        stack.mulPose(Axis.XP.rotationDegrees(Mth.lerp(partialTick, spear.xRotO, spear.getXRot())));
    }
}
