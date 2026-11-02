package com.sourceofmystery.client.staff;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.sourceofmystery.item.DimensionBreakerStaffItem;
import com.sourceofmystery.staff.StaffSpell;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import software.bernie.geckolib.model.DefaultedItemGeoModel;
import software.bernie.geckolib.renderer.GeoItemRenderer;

public final class StaffItemExtensions implements IClientItemExtensions {
    private GeoItemRenderer<DimensionBreakerStaffItem> renderer;
    private static final HumanoidModel.ArmPose CAST = HumanoidModel.ArmPose.create("SOM_STAFF_CAST", true,
            (model, entity, arm) -> {
                StaffSpell spell = StaffSpell.selected(entity.getUseItem());
                float age = entity.getTicksUsingItem() / 20f;
                var dominant = arm == HumanoidArm.RIGHT ? model.rightArm : model.leftArm;
                var support = arm == HumanoidArm.RIGHT ? model.leftArm : model.rightArm;
                float side = arm == HumanoidArm.RIGHT ? 1 : -1;
                float lift = Mth.clamp(age / .7f, 0, 1);
                dominant.xRot = -1.3f - .8f * lift;
                dominant.yRot = -.15f * side;
                dominant.zRot = -.12f * side;
                support.xRot = -1.8f * lift;
                support.yRot = .7f * side;
                support.zRot = .2f * side;
                if (spell == StaffSpell.EARTH_SPLIT || spell == StaffSpell.EARTH_LIFT) {
                    float strike = Mth.clamp((age - 2.15f) / .55f, 0, 1);
                    dominant.xRot = Mth.lerp(strike, -2.6f, -.45f);
                    support.xRot = Mth.lerp(strike, -2.35f, -.55f);
                    if (spell == StaffSpell.EARTH_SPLIT) {
                        support.xRot = -.2f;
                        support.yRot = 0;
                    }
                } else if (spell == StaffSpell.SPACE_RIFT) {
                    dominant.xRot = -Mth.HALF_PI;
                    dominant.yRot = 0;
                    support.xRot = support.yRot = support.zRot = 0;
                }
            });

    @Override public HumanoidModel.ArmPose getArmPose(LivingEntity entity, InteractionHand hand, ItemStack stack) {
        return entity.isUsingItem() && entity.getUsedItemHand() == hand ? CAST : HumanoidModel.ArmPose.EMPTY;
    }

    @Override public boolean applyForgeHandTransform(PoseStack poses, LocalPlayer player, HumanoidArm arm,
                                                     ItemStack item, float partial, float equip, float swing) {
        if (!player.isUsingItem() || player.getUseItem() != item) return false;
        float side = arm == HumanoidArm.RIGHT ? 1 : -1;
        float progress = Mth.clamp((player.getTicksUsingItem() + partial) / 14f, 0, 1);
        poses.translate(side * .56, -.52 - equip * .6 + .15 * progress, -.72);
        poses.mulPose(Axis.ZP.rotationDegrees(side * (10 + 8 * progress)));
        poses.mulPose(Axis.XP.rotationDegrees(-6 * progress));
        return true;
    }

    @Override public BlockEntityWithoutLevelRenderer getCustomRenderer() {
        if (renderer == null) renderer = new StaffRenderer();
        return renderer;
    }

    private static final class StaffRenderer extends GeoItemRenderer<DimensionBreakerStaffItem> {
        private org.joml.Matrix4f crystal;
        StaffRenderer(){
            super(new DefaultedItemGeoModel<>(new ResourceLocation("sourceofmystery","dimension_breaker_staff")));
            addRenderLayer(new software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer<>(this));
        }
        @Override public void renderRecursively(PoseStack poses,DimensionBreakerStaffItem item,software.bernie.geckolib.cache.object.GeoBone bone,
                net.minecraft.client.renderer.RenderType type,net.minecraft.client.renderer.MultiBufferSource buffers,
                com.mojang.blaze3d.vertex.VertexConsumer vertices,boolean reRender,float partial,int light,int overlay,float r,float g,float b,float a) {
            if(bone.getName().equals("crystal"))crystal=new org.joml.Matrix4f(poses.last().pose())
                    .translate(bone.getPivotX()/16,bone.getPivotY()/16,bone.getPivotZ()/16);
            super.renderRecursively(poses,item,bone,type,buffers,vertices,reRender,partial,light,overlay,r,g,b,a);
        }
        @Override public void actuallyRender(PoseStack poses,DimensionBreakerStaffItem item,software.bernie.geckolib.cache.object.BakedGeoModel model,
                net.minecraft.client.renderer.RenderType type,net.minecraft.client.renderer.MultiBufferSource buffers,
                com.mojang.blaze3d.vertex.VertexConsumer vertices,boolean reRender,float partial,int light,int overlay,float r,float g,float b,float a) {
            crystal=null;super.actuallyRender(poses,item,model,type,buffers,vertices,reRender,partial,light,overlay,r,g,b,a);
            float age=StaffVisuals.chargeAge(getCurrentItemStack(),StaffSpell.EARTH_SPLIT,partial);
            if(crystal!=null&&age>=0&&!reRender) {
                PoseStack seal=new PoseStack();seal.last().pose().set(crystal);
                com.sourceofmystery.client.StaffMagicGeometry.earthSeal(seal,buffers,age/20f);
            }
        }
    }
}
