package com.sourceofmystery.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.sourceofmystery.cinematic.DivineCGTimeline;
import com.sourceofmystery.mixin.DivinePlayerLayersAccess;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraft.world.item.ItemStack;
import java.util.*;

/** Draw a freshly baked vanilla body, while retaining the real player's accessory layers. */
final class DivineCGPlayer {
    private PlayerModel<AbstractClientPlayer> model;
    private boolean slim;
    private Object bakedSet;
    private static List<ModelPart> parts(PlayerModel<?> m){return List.of(m.head,m.hat,m.body,m.rightArm,m.leftArm,m.rightLeg,m.leftLeg,m.jacket,m.rightSleeve,m.leftSleeve,m.rightPants,m.leftPants);}
    private record Saved(ModelPart part,net.minecraft.client.model.geom.PartPose pose,float sx,float sy,float sz,boolean visible,boolean skip){
        Saved(ModelPart p){this(p,p.storePose(),p.xScale,p.yScale,p.zScale,p.visible,p.skipDraw);}
        void restore(){part.loadPose(pose);part.xScale=sx;part.yScale=sy;part.zScale=sz;part.visible=visible;part.skipDraw=skip;}
    }
    @SuppressWarnings({"rawtypes","unchecked"})
    void render(PoseStack poses,MultiBufferSource.BufferSource buffers,float t,float partial,ItemStack armor){
        var mc=Minecraft.getInstance();var player=mc.player;if(player==null)return;
        boolean narrow=player.getModelName().equals("slim");
        if(model==null || narrow!=slim || bakedSet!=mc.getEntityModels()){
            slim=narrow;bakedSet=mc.getEntityModels();model=new PlayerModel<>(mc.getEntityModels().bakeLayer(slim?ModelLayers.PLAYER_SLIM:ModelLayers.PLAYER),slim);
        }
        var renderer=(PlayerRenderer)mc.getEntityRenderDispatcher().getSkinMap().get(slim?"slim":"default");
        if(renderer==null)return;
        var original=renderer.getModel();var originals=parts(original);var fresh=parts(model);
        List<Saved> saved=originals.stream().map(Saved::new).toList();
        boolean young=original.young,riding=original.riding,crouching=original.crouching;float attack=original.attackTime;
        var leftPose=original.leftArmPose;var rightPose=original.rightArmPose;
        ItemStack chest=player.getItemBySlot(EquipmentSlot.CHEST);
        try {
            // This is a render-only inventory substitution; no packet or server inventory is changed.
            player.getInventory().armor.set(2,t>=DivineCGTimeline.EQUIP?armor:ItemStack.EMPTY);
            pose(t,player);
            for(int i=0;i<fresh.size();i++){
                originals.get(i).copyFrom(fresh.get(i));originals.get(i).visible=fresh.get(i).visible;originals.get(i).skipDraw=false;
            }
            original.young=false;original.riding=false;original.crouching=false;original.attackTime=0;
            original.leftArmPose=original.rightArmPose=net.minecraft.client.model.HumanoidModel.ArmPose.EMPTY;
            poses.pushPose();
            try {
            poses.mulPose(Axis.YP.rotationDegrees(0)); // Player faces stage -Z (Minecraft yaw 180).
            poses.scale(-1,-1,1);poses.translate(0,-1.501,0);
            model.renderToBuffer(poses,buffers.getBuffer(RenderType.entityCutoutNoCull(player.getSkinTextureLocation())),LightTexture.FULL_BRIGHT,OverlayTexture.NO_OVERLAY,1,1,1,1);
            buffers.endBatch();
            for(RenderLayer layer:((DivinePlayerLayersAccess)renderer).som$divineLayers()){
                // MMD body replacement must never replace this scene's vanilla body.
                String name=layer.getClass().getName().toLowerCase(Locale.ROOT);
                if(name.contains("mmdskin") || name.contains("mmd_skin"))continue;
                layer.render(poses,buffers,LightTexture.FULL_BRIGHT,player,0,0,partial,t*20,model.head.yRot*Mth.RAD_TO_DEG,model.head.xRot*Mth.RAD_TO_DEG);
                buffers.endBatch();
            }
            } finally {poses.popPose();}
        } finally {
            player.getInventory().armor.set(2,chest);
            saved.forEach(Saved::restore);original.young=young;original.riding=riding;original.crouching=crouching;original.attackTime=attack;
            original.leftArmPose=leftPose;original.rightArmPose=rightPose;
        }
    }
    private void pose(float t,AbstractClientPlayer p){
        for(var part:parts(model)){part.resetPose();part.xScale=part.yScale=part.zScale=1;part.skipDraw=false;part.visible=true;}
        model.young=model.riding=model.crouching=false;model.attackTime=0;
        float motion=DivineCGTimeline.walkAmount(t),cycle=(t-12)*Mth.TWO_PI/2.1666667f;
        float breath=Mth.sin(Math.min(t,32)*1.65f);
        model.body.y=.055f*breath;model.body.xRot=.018f*breath+.035f*motion;
        model.rightLeg.xRot=Mth.cos(cycle)*.44f*motion;model.leftLeg.xRot=-model.rightLeg.xRot;
        model.rightLeg.y-=Math.max(0,Mth.sin(cycle))*.65f*motion;
        model.leftLeg.y-=Math.max(0,-Mth.sin(cycle))*.65f*motion;
        // Rigid vanilla legs have no knee; compensate the arc so a stance foot
        // reaches the step rather than letting both feet hover during the stride.
        model.rightLeg.y+=12*(1-Mth.cos(model.rightLeg.xRot));
        model.leftLeg.y+=12*(1-Mth.cos(model.leftLeg.xRot));
        model.rightArm.xRot=-.35f*model.rightLeg.xRot;model.leftArm.xRot=-model.rightArm.xRot;
        model.rightArm.zRot=.045f+.012f*breath;model.leftArm.zRot=-model.rightArm.zRot;
        model.head.xRot=DivineCGTimeline.headPitch(t);model.head.yRot=DivineCGTimeline.headYaw(t);
        float touch=DivineCGTimeline.ramp(t,21,22.3f)*(1-DivineCGTimeline.ramp(t,24.2f,25.3f));
        float inspect=DivineCGTimeline.ramp(t,25.8f,26.7f)*(1-DivineCGTimeline.ramp(t,29,31.5f));
        model.rightArm.xRot=DivineCGTimeline.mix(model.rightArm.xRot,-1.36f+.035f*Mth.sin(t*2),touch);
        model.leftArm.xRot=DivineCGTimeline.mix(model.leftArm.xRot,-1.36f-.035f*Mth.sin(t*2),touch);
        model.rightArm.yRot=-.13f*touch;model.leftArm.yRot=.13f*touch;
        model.rightArm.zRot=DivineCGTimeline.mix(model.rightArm.zRot,.16f,touch);
        model.leftArm.zRot=DivineCGTimeline.mix(model.leftArm.zRot,-.16f,touch);
        model.rightArm.xRot=DivineCGTimeline.mix(model.rightArm.xRot,-.85f,inspect);
        model.leftArm.xRot=DivineCGTimeline.mix(model.leftArm.xRot,-.62f,inspect);
        model.rightArm.zRot+=.28f*inspect;model.leftArm.zRot-=.33f*inspect;
        model.hat.copyFrom(model.head);model.jacket.copyFrom(model.body);model.rightSleeve.copyFrom(model.rightArm);model.leftSleeve.copyFrom(model.leftArm);
        model.rightPants.copyFrom(model.rightLeg);model.leftPants.copyFrom(model.leftLeg);
        model.hat.visible=p.isModelPartShown(PlayerModelPart.HAT);model.jacket.visible=p.isModelPartShown(PlayerModelPart.JACKET);
        model.rightSleeve.visible=p.isModelPartShown(PlayerModelPart.RIGHT_SLEEVE);model.leftSleeve.visible=p.isModelPartShown(PlayerModelPart.LEFT_SLEEVE);
        model.rightPants.visible=p.isModelPartShown(PlayerModelPart.RIGHT_PANTS_LEG);model.leftPants.visible=p.isModelPartShown(PlayerModelPart.LEFT_PANTS_LEG);
    }
}
