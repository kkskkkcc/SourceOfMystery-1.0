package com.sourceofmystery.client.staff;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.sourceofmystery.item.DimensionBreakerStaffItem;
import com.sourceofmystery.staff.StaffSpell;
import com.sourceofmystery.staff.StaffEnvironment;
import com.sourceofmystery.staff.EnvironmentPalette;
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
                ItemStack active=entity.isUsingItem()?entity.getUseItem():entity.getItemInHand(arm==entity.getMainArm()?InteractionHand.MAIN_HAND:InteractionHand.OFF_HAND);
                StaffSpell spell = StaffSpell.selected(active);
                float age = (entity.getTicksUsingItem()+Minecraft.getInstance().getFrameTime()) / 20f;
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
                } else if(spell==StaffSpell.WEATHER_CONTROL){
                    float p=Mth.clamp(age/Math.max(.1f,spell.castTicks()/20f),0,1),rise=ease(p/.4f),turn=ease((p-.55f)/.4f);
                    dominant.xRot=-.3f-2.0f*rise+.65f*turn;dominant.zRot=-side*(.12f+.2f*rise);dominant.yRot=side*.15f*turn;
                    support.xRot=-.3f-1.3f*rise;support.yRot=side*(.5f-.8f*turn);support.zRot=side*.16f;
                    model.body.yRot=side*.18f*Mth.sin(p*Mth.PI);model.head.xRot-=.15f*rise;
                } else if (spell == StaffSpell.ENVIRONMENT_SHIFT) {
                    float clock=StaffVisuals.environmentPoseAge(entity,Minecraft.getInstance().getFrameTime());
                    if(clock<0)clock=entity.getTicksUsingItem()+Minecraft.getInstance().getFrameTime();
                    float p=Mth.clamp(clock/Math.max(1f,spell.castTicks()),0,1);
                    float rise=ease(p/.25f),strike=ease((p-.76f)/.19f);
                    // Staff held beside the shoulder, free palm forward into the seal.
                    dominant.xRot=-.22f-.63f*rise-.22f*strike;
                    dominant.yRot=-side*.10f;
                    dominant.zRot=-side*(.12f+.14f*rise);
                    support.xRot=-.25f-1.05f*rise-.12f*strike;
                    support.yRot=-side*.08f;
                    support.zRot=side*(.08f+.14f*rise);
                    model.body.yRot=side*(.12f*rise-.22f*strike);
                    model.body.xRot+=.035f*rise+.065f*strike;
                    model.head.xRot-=.035f*rise;
                    model.rightLeg.zRot=.035f*rise;model.leftLeg.zRot=-.035f*rise;
                    model.rightLeg.xRot+=.10f*strike;model.leftLeg.xRot-=.06f*strike;
                    EnvironmentPalette theme=EnvironmentPalette.of(StaffEnvironment.selected(active));
                    float flourish=Mth.sin(p*Mth.PI)*rise*(1-strike);
                    if(theme.aquatic()||theme==EnvironmentPalette.SWAMP) {
                        support.yRot+=side*.45f*Mth.sin(p*Mth.TWO_PI);support.xRot+=.18f*Mth.cos(p*Mth.TWO_PI);
                        dominant.zRot-=side*.18f*flourish;
                    } else if(theme.infernal()) {
                        support.xRot-=.3f*flourish;support.zRot+=side*.3f*flourish;
                        model.body.xRot+=.10f*strike;
                    } else if(theme==EnvironmentPalette.END||theme==EnvironmentPalette.DEEP_DARK) {
                        support.yRot-=side*.5f*flourish;dominant.yRot+=side*.35f*flourish;
                        model.body.yRot+=side*.15f*flourish;
                    } else if(theme==EnvironmentPalette.SNOW) {
                        support.xRot-=.25f*flourish;support.zRot+=side*.2f*flourish;
                    } else if(theme==EnvironmentPalette.DESERT||theme==EnvironmentPalette.BADLANDS) {
                        dominant.yRot+=side*.2f*flourish;support.xRot+=.25f*flourish;
                    } else {
                        support.zRot+=side*.22f*flourish;support.xRot-=.15f*flourish;
                    }
                    float recover=1-ease((clock-spell.castTicks())/12);
                    dominant.xRot*=recover;dominant.yRot*=recover;dominant.zRot*=recover;
                    support.xRot*=recover;support.yRot*=recover;support.zRot*=recover;
                    model.body.xRot*=recover;model.body.yRot*=recover;
                } else if (spell == StaffSpell.SPACE_RIFT) {
                    dominant.xRot = -Mth.HALF_PI;
                    dominant.yRot = 0;
                    support.xRot = support.yRot = support.zRot = 0;
                }
            });
    private static float ease(float p) {p=Mth.clamp(p,0,1);return p*p*(3-2*p);}

    @Override public HumanoidModel.ArmPose getArmPose(LivingEntity entity, InteractionHand hand, ItemStack stack) {
        boolean channel=entity.isUsingItem()&&entity.getUsedItemHand()==hand;
        boolean recovery=StaffSpell.selected(stack)==StaffSpell.ENVIRONMENT_SHIFT&&StaffVisuals.environmentPoseAge(entity,Minecraft.getInstance().getFrameTime())>=0;
        return channel||recovery?CAST:HumanoidModel.ArmPose.EMPTY;
    }

    @Override public boolean applyForgeHandTransform(PoseStack poses, LocalPlayer player, HumanoidArm arm,
                                                     ItemStack item, float partial, float equip, float swing) {
        if (!player.isUsingItem() || player.getUseItem() != item) return false;
        float side = arm == HumanoidArm.RIGHT ? 1 : -1;
        if(StaffSpell.selected(item)==StaffSpell.ENVIRONMENT_SHIFT) {
            float p=Mth.clamp((player.getTicksUsingItem()+partial)/Math.max(1f,StaffSpell.ENVIRONMENT_SHIFT.castTicks()),0,1);
            float rise=ease(p/.25f),strike=ease((p-.76f)/.19f);
            poses.translate(side*(.56+.03*rise-.11*strike),-.62-equip*.6+.13*rise-.04*strike,-.88-.14*strike);
            poses.mulPose(Axis.ZP.rotationDegrees(side*(6+6*rise-10*strike)));
            poses.mulPose(Axis.XP.rotationDegrees(-4*rise+10*strike));
            return true;
        }
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
