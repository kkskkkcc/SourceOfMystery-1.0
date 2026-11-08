package com.sourceofmystery.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.*;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.core.animatable.GeoAnimatable;
import software.bernie.geckolib.core.animatable.instance.*;
import software.bernie.geckolib.core.animation.*;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoObjectRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/** Original blood-moon sanctum, authored as a small GeckoLib scene rather than changing world blocks. */
final class DomainShrine implements GeoAnimatable {
    private static ResourceLocation id(String s){return new ResourceLocation("sourceofmystery",s);}
    private final AnimatableInstanceCache cache=new SingletonAnimatableInstanceCache(this);
    private double ticks;
    private final GeoObjectRenderer<DomainShrine> renderer=new GeoObjectRenderer<>(new GeoModel<DomainShrine>(){
        @Override public ResourceLocation getModelResource(DomainShrine s){return id("geo/cinematic/domain_shrine.geo.json");}
        @Override public ResourceLocation getTextureResource(DomainShrine s){return id("textures/cinematic/domain/shrine.png");}
        @Override public ResourceLocation getAnimationResource(DomainShrine s){return id("animations/cinematic/domain_shrine.animation.json");}
    });
    DomainShrine(){renderer.addRenderLayer(new AutoGlowingGeoLayer<>(renderer));}
    @Override public AnimatableInstanceCache getAnimatableInstanceCache(){return cache;}
    @Override public double getTick(Object data){return ticks;}
    @Override public void registerControllers(AnimatableManager.ControllerRegistrar c){c.add(new AnimationController<>(this,"shrine",0,s->s.setAndContinue(RawAnimation.begin().thenLoop("shrine.idle"))));}
    void render(PoseStack p,MultiBufferSource.BufferSource b,float seconds){
        ticks=seconds*20;p.pushPose();p.translate(-.5,-.51,-.5);renderer.render(p,this,b,RenderType.entityCutoutNoCull(renderer.getTextureLocation(this)),null,LightTexture.FULL_BRIGHT);b.endBatch();p.popPose();
    }
}
