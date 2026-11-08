package com.sourceofmystery.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.sourceofmystery.mixin.DivinePlayerLayersAccess;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.*;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.entity.layers.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import java.util.*;

/** A vanilla skin-and-equipment actor. Held items are omitted by skipping their layer, never deleting stacks. */
final class DomainActor {
    private PlayerModel<AbstractClientPlayer> model;private boolean slim;private Object baked;
    private static List<ModelPart> parts(PlayerModel<?> m){return List.of(m.head,m.hat,m.body,m.rightArm,m.leftArm,m.rightLeg,m.leftLeg,m.jacket,m.rightSleeve,m.leftSleeve,m.rightPants,m.leftPants);}
    private record Saved(ModelPart p,PartPose pose,boolean visible){Saved(ModelPart p){this(p,p.storePose(),p.visible);}void restore(){p.loadPose(pose);p.visible=visible;}}
    @SuppressWarnings({"rawtypes","unchecked"})
    void render(PoseStack p,MultiBufferSource.BufferSource b,AbstractClientPlayer actor,float t,float partial,float yaw){
        var mc=Minecraft.getInstance();boolean narrow=actor.getModelName().equals("slim");
        if(model==null||slim!=narrow||baked!=mc.getEntityModels()){slim=narrow;baked=mc.getEntityModels();model=new PlayerModel<>(mc.getEntityModels().bakeLayer(slim?ModelLayers.PLAYER_SLIM:ModelLayers.PLAYER),slim);}
        var renderer=(PlayerRenderer)mc.getEntityRenderDispatcher().getSkinMap().get(slim?"slim":"default");if(renderer==null)return;
        var original=renderer.getModel();var before=parts(original).stream().map(Saved::new).toList();
        boolean young=original.young,riding=original.riding,crouching=original.crouching;float attack=original.attackTime;
        var left=original.leftArmPose;var right=original.rightArmPose;
        try{
            for(var part:parts(model)){part.resetPose();part.visible=true;}
            // EntityModel defaults young=true; this actor is an adult player, including its head pass.
            model.young=model.riding=model.crouching=false;model.attackTime=0;
            float gather=ease(t/.7f),seal=ease((t-.65f)/.9f),release=ease((t-2.25f)/.6f),settle=ease((t-3.1f)/1.2f);
            float breath=.016f*Mth.sin(t*1.5f);
            model.body.xRot=.045f*gather+.025f*Mth.sin(t*1.2f);model.body.yRot=.13f*gather*(1-release);
            model.head.xRot=.15f*seal-.22f*release+breath;
            model.head.yRot=-.1f*ease((t-1.6f)/.4f)*(1-release);
            model.rightArm.xRot=-1.1f*gather-.15f*seal+.8f*release-.2f*settle;
            model.leftArm.xRot=-1.16f*gather-.09f*seal+.8f*release-.2f*settle;
            model.rightArm.yRot=-.43f*seal*(1-release);model.leftArm.yRot=.43f*seal*(1-release);
            model.rightArm.zRot=.1f*gather+.38f*release-.25f*settle;
            model.leftArm.zRot=-.1f*gather-.38f*release+.25f*settle;
            model.rightLeg.zRot=.055f;model.leftLeg.zRot=-.055f;model.rightLeg.xRot=.05f;model.leftLeg.xRot=-.05f;
            model.hat.copyFrom(model.head);model.jacket.copyFrom(model.body);model.rightSleeve.copyFrom(model.rightArm);model.leftSleeve.copyFrom(model.leftArm);model.rightPants.copyFrom(model.rightLeg);model.leftPants.copyFrom(model.leftLeg);
            var a=parts(original);var fresh=parts(model);for(int i=0;i<a.size();i++){a.get(i).copyFrom(fresh.get(i));a.get(i).visible=true;}
            original.young=original.riding=original.crouching=false;original.attackTime=0;original.leftArmPose=original.rightArmPose=net.minecraft.client.model.HumanoidModel.ArmPose.EMPTY;
            p.pushPose();p.mulPose(Axis.YP.rotationDegrees(180-yaw));p.scale(-1,-1,1);p.translate(0,-1.501,0);
            try{
                model.renderToBuffer(p,b.getBuffer(RenderType.entityCutoutNoCull(actor.getSkinTextureLocation())),LightTexture.FULL_BRIGHT,OverlayTexture.NO_OVERLAY,1,1,1,1);b.endBatch();
                for(RenderLayer layer:((DivinePlayerLayersAccess)renderer).som$divineLayers()){
                    String name=layer.getClass().getName().toLowerCase(Locale.ROOT);
                    if(layer instanceof ItemInHandLayer||name.contains("iteminhand")||name.contains("mmdskin")||name.contains("mmd_skin"))continue;
                    layer.render(p,b,LightTexture.FULL_BRIGHT,actor,0,0,partial,t*20,model.head.yRot*Mth.RAD_TO_DEG,model.head.xRot*Mth.RAD_TO_DEG);b.endBatch();
                }
            }finally{p.popPose();}
        }finally{before.forEach(Saved::restore);original.young=young;original.riding=riding;original.crouching=crouching;original.attackTime=attack;original.leftArmPose=left;original.rightArmPose=right;}
    }
    private static float ease(float x){x=Mth.clamp(x,0,1);return x*x*(3-2*x);}
}
