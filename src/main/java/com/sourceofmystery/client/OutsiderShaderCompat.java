package com.sourceofmystery.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import java.lang.reflect.Method;

/** Optional Iris API bridge. No Oculus classes or shaders are required to load the mod. */
final class OutsiderShaderCompat {
    private static final Method INSTANCE,ENABLED;
    private static final Method PACK_NAME,SHADOW;
    private static final ResourceLocation WHITE=new ResourceLocation("sourceofmystery","textures/entity/outsider/white.png");
    private static final ResourceLocation GRAY_BACK=new ResourceLocation("sourceofmystery","dynamic/outsider_back_gray");
    private static boolean backLoaded;
    static {
        Method instance=null,enabled=null,pack=null,shadow=null;
        try {
            Class<?> api=Class.forName("net.irisshaders.iris.api.v0.IrisApi");
            instance=api.getMethod("getInstance");enabled=api.getMethod("isShaderPackInUse");
            shadow=api.getMethod("isRenderingShadowPass");
            pack=Class.forName("net.irisshaders.iris.Iris").getMethod("getCurrentPackName");
        } catch(ReflectiveOperationException|LinkageError ignored){}
        INSTANCE=instance;ENABLED=enabled;
        PACK_NAME=pack;SHADOW=shadow;
    }
    static boolean active(){
        if(INSTANCE==null)return false;
        try{return (boolean)ENABLED.invoke(INSTANCE.invoke(null));}
        catch(ReflectiveOperationException|LinkageError error){return false;}
    }
    static void reload(){backLoaded=false;}
    static boolean complementary(){
        if(!active()||PACK_NAME==null)return false;
        try{return String.valueOf(PACK_NAME.invoke(null)).toLowerCase(java.util.Locale.ROOT).contains("complementary");}
        catch(ReflectiveOperationException|LinkageError error){return false;}
    }
    static boolean shadowPass(){
        if(SHADOW==null)return false;
        try{return (boolean)SHADOW.invoke(INSTANCE.invoke(null));}
        catch(ReflectiveOperationException|LinkageError error){return false;}
    }
    static ResourceLocation backTexture(ResourceLocation source){
        if(!backLoaded){
            var minecraft=Minecraft.getInstance();
            try(var stream=minecraft.getResourceManager().getResourceOrThrow(source).open()){
                NativeImage image=NativeImage.read(stream);
                for(int y=0;y<image.getHeight();y++)for(int x=0;x<image.getWidth();x++){
                    int pixel=image.getPixelRGBA(x,y);
                    int gray=Math.round(15.3f+.46f*(.2126f*(pixel&255)+.7152f*((pixel>>>8)&255)+.0722f*((pixel>>>16)&255)));
                    image.setPixelRGBA(x,y,(pixel&0xff000000)|gray|(gray<<8)|(gray<<16));
                }
                minecraft.getTextureManager().register(GRAY_BACK,new DynamicTexture(image));backLoaded=true;
            }catch(java.io.IOException error){throw new IllegalStateException("Unable to prepare monochrome halo",error);}
        }
        return GRAY_BACK;
    }
    static VertexConsumer solid(MultiBufferSource buffers,RenderType original){
        return active()?complete(buffers.getBuffer(RenderType.entitySolid(WHITE))):buffers.getBuffer(original);
    }
    static VertexConsumer glow(MultiBufferSource buffers,RenderType original){
        return active()?complete(buffers.getBuffer(RenderType.entityTranslucentEmissive(WHITE))):buffers.getBuffer(original);
    }
    /** Adapt color-only or position/color/UV effects to vanilla NEW_ENTITY.
     * Iris extends that known format and selects its emissive entity program.
     */
    static VertexConsumer complete(VertexConsumer target){
        return complete(target,255,255,255);
    }
    static VertexConsumer complete(VertexConsumer target,int red,int green,int blue){
        return new VertexConsumer(){
            boolean textured,colored;
            public VertexConsumer vertex(double x,double y,double z){target.vertex(x,y,z);return this;}
            public VertexConsumer color(int r,int g,int b,int a){target.color(r,g,b,a);colored=true;return this;}
            public VertexConsumer uv(float u,float v){target.uv(u,v);textured=true;return this;}
            public VertexConsumer overlayCoords(int u,int v){target.overlayCoords(u,v);return this;}
            public VertexConsumer uv2(int u,int v){target.uv2(u,v);return this;}
            public VertexConsumer normal(float x,float y,float z){target.normal(x,y,z);return this;}
            public void endVertex(){if(!colored)target.color(red,green,blue,255);if(!textured)target.uv(.5f,.5f);target.overlayCoords(OverlayTexture.NO_OVERLAY).uv2(15728880).normal(0,1,0).endVertex();textured=false;colored=false;}
            public void defaultColor(int r,int g,int b,int a){target.defaultColor(r,g,b,a);}
            public void unsetDefaultColor(){target.unsetDefaultColor();}
        };
    }
}
