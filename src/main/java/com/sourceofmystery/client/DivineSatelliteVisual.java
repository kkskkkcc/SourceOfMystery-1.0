package com.sourceofmystery.client;

import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.renderer.*;
import software.bernie.geckolib.core.animatable.GeoAnimatable;
import software.bernie.geckolib.core.animatable.instance.*;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.renderer.GeoObjectRenderer;
import software.bernie.geckolib.cache.object.GeoCube;

final class DivineSatelliteVisual implements GeoAnimatable {
    private static final DivineSatelliteModel MODEL=new DivineSatelliteModel();
    final String variant;final boolean star;
    private float opacity=1;private double tick;
    private final AnimatableInstanceCache cache=new SingletonAnimatableInstanceCache(this);
    private final GeoObjectRenderer<DivineSatelliteVisual> renderer=new GeoObjectRenderer<>(MODEL){
        @Override public void renderCube(PoseStack poses,GeoCube cube,VertexConsumer vertices,int light,int overlay,float r,float g,float b,float alpha){
            for(var quad:cube.quads()){
                var normals=MODEL.normals.get(quad);var points=quad.vertices();
                for(int i=0;i<points.length;i++){
                    var v=points[i];var p=v.position();var n=normals[i];
                    vertices.vertex(poses.last().pose(),p.x,p.y,p.z).color(r,g,b,alpha*opacity).uv(v.texU(),v.texV())
                            .overlayCoords(overlay).uv2(light).normal(poses.last().normal(),n.x,n.y,n.z).endVertex();
                }
            }
        }
    };
    DivineSatelliteVisual(String variant,boolean star){this.variant=variant;this.star=star;}
    @Override public AnimatableInstanceCache getAnimatableInstanceCache(){return cache;}
    @Override public double getTick(Object data){return tick;}
    @Override public void registerControllers(AnimatableManager.ControllerRegistrar controllers){}
    void render(PoseStack poses,MultiBufferSource.BufferSource buffers,float alpha,double time){
        opacity=alpha;tick=time;
        poses.pushPose();
        try{
            poses.translate(-.5,-.51,-.5);
            var texture=renderer.getTextureLocation(this);
            // Some shader packs replace emissive-pass RGB with white. Keep the black star
            // on the albedo-preserving pass so its dark core remains black under those packs.
            var type=variant.equals("white_star")?RenderType.entityTranslucentEmissive(texture):RenderType.entityTranslucentCull(texture);
            renderer.render(poses,this,buffers,type,null,LightTexture.FULL_BRIGHT);
        }finally{poses.popPose();}
    }
}
