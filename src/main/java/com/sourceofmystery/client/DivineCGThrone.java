package com.sourceofmystery.client;

import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.core.animatable.GeoAnimatable;
import software.bernie.geckolib.core.animatable.instance.*;
import software.bernie.geckolib.core.animation.*;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoObjectRenderer;
import software.bernie.geckolib.cache.object.*;

/** User-supplied throne mesh carried by GeckoLib, rendered only on the local CG stage. */
final class DivineCGThrone implements GeoAnimatable {
    private final AnimatableInstanceCache cache=new SingletonAnimatableInstanceCache(this);
    private final DivineThroneModel mesh=new DivineThroneModel();
    private final GeoObjectRenderer<DivineCGThrone> renderer=new GeoObjectRenderer<>(mesh) {
        @Override public void renderCube(PoseStack poses,GeoCube cube,VertexConsumer vertices,int light,int overlay,float r,float g,float b,float a){
            for(GeoQuad face:cube.quads()){
                var smoothNormals=mesh.normals.get(face);var points=face.vertices();
                for(int i=0;i<points.length;i++){
                    var v=points[i];var n=smoothNormals[i];var pos=v.position();
                    vertices.vertex(poses.last().pose(),pos.x,pos.y,pos.z).color(r,g,b,a).uv(v.texU(),v.texV())
                            .overlayCoords(overlay).uv2(light).normal(poses.last().normal(),n.x,n.y,n.z).endVertex();
                }
            }
        }
    };
    private double ticks;
    private static ResourceLocation resource(String path){return new ResourceLocation("sourceofmystery",path);}
    @Override public AnimatableInstanceCache getAnimatableInstanceCache(){return cache;}
    @Override public double getTick(Object data){return ticks;}
    @Override public void registerControllers(AnimatableManager.ControllerRegistrar controllers){
        controllers.add(new AnimationController<>(this,"sanctum_idle",0,state->state.setAndContinue(RawAnimation.begin().thenLoop("animation.divine_throne.idle"))));
    }
    void render(PoseStack poses,MultiBufferSource.BufferSource buffers,float realSeconds){
        ticks=Math.floor(realSeconds*20);
        poses.pushPose();
        try {
            // GeoObjectRenderer supplies the standard block-centre offset internally.
            poses.translate(-.5,-.51,-.5);
            renderer.render(poses,this,buffers,RenderType.entityCutoutNoCull(renderer.getTextureLocation(this)),null,LightTexture.FULL_BRIGHT);
            buffers.endBatch();
        } finally {poses.popPose();}
    }
}
