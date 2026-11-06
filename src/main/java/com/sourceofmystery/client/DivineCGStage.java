package com.sourceofmystery.client;

import com.mojang.blaze3d.vertex.*;
import com.sourceofmystery.cinematic.DivineCGTimeline;
import net.minecraft.client.renderer.*;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.core.animatable.GeoAnimatable;
import software.bernie.geckolib.core.animatable.instance.*;
import software.bernie.geckolib.core.animation.*;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoObjectRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/** Authored stage geometry with story-time stair and landing reveals. */
final class DivineCGStage implements GeoAnimatable {
    private static final ResourceLocation GEO=resource("geo/cinematic/divine_stage.geo.json"),TEXTURE=resource("textures/cinematic/divine/stage.png"),ANIMATION=resource("animations/cinematic/divine_stage.animation.json");
    private static final RawAnimation IDLE=RawAnimation.begin().thenLoop("animation.divine_stage.idle");
    private final AnimatableInstanceCache cache=new SingletonAnimatableInstanceCache(this);
    private final GeoObjectRenderer<DivineCGStage> renderer=new GeoObjectRenderer<>(new GeoModel<DivineCGStage>() {
        @Override public ResourceLocation getModelResource(DivineCGStage stage){return GEO;}
        @Override public ResourceLocation getTextureResource(DivineCGStage stage){return TEXTURE;}
        @Override public ResourceLocation getAnimationResource(DivineCGStage stage){return ANIMATION;}
    }) {
        @Override public void renderRecursively(PoseStack poses,DivineCGStage stage,GeoBone bone,RenderType type,MultiBufferSource buffers,VertexConsumer vertices,boolean reRender,float partial,int light,int overlay,float r,float g,float b,float a){
            // GeoObjectRenderer has processed animations here; apply again for the glow pass.
            String name=bone.getName();
            if(name.equals("dais")){bone.setHidden(false);bone.setPosY(0);}
            else if(name.equals("landing"))reveal(bone,DivineCGTimeline.ramp(time,10.1f,11.6f),1.3f);
            else if(name.length()==6&&name.startsWith("step_")&&name.charAt(5)>='0'&&name.charAt(5)<='4'){
                int i=name.charAt(5)-'0';
                reveal(bone,DivineCGTimeline.ramp(time,10+i*.21f,10.8f+i*.21f),1.2f);
            }
            super.renderRecursively(poses,stage,bone,type,buffers,vertices,reRender,partial,light,overlay,r,g,b,a);
        }
    };
    private float time;
    private double ticks;
    DivineCGStage(){renderer.addRenderLayer(new AutoGlowingGeoLayer<>(renderer));}
    private static ResourceLocation resource(String path){return new ResourceLocation("sourceofmystery",path);}
    private static void reveal(GeoBone bone,float open,float drop){bone.setHidden(open<.001f);bone.setPosY(-(1-open)*drop*16);}
    @Override public AnimatableInstanceCache getAnimatableInstanceCache(){return cache;}
    @Override public double getTick(Object data){return ticks;}
    @Override public void registerControllers(AnimatableManager.ControllerRegistrar controllers){
        controllers.add(new AnimationController<>(this,"stage_idle",0,state->state.setAndContinue(IDLE)));
    }
    void render(PoseStack poses,MultiBufferSource.BufferSource buffers,float storyTime,float realSeconds){
        time=storyTime;ticks=Math.floor(realSeconds*20);
        poses.pushPose();
        try {
            poses.translate(-.5,-.51,-.5);
            renderer.render(poses,this,buffers,RenderType.entityCutoutNoCull(TEXTURE),null,LightTexture.FULL_BRIGHT);
            buffers.endBatch();
        } finally {poses.popPose();}
    }
}
