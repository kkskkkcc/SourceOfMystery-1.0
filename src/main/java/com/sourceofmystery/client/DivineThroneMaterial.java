package com.sourceofmystery.client;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/** A 3D material colour transform. The user's source texture and UVs are retained. */
final class DivineThroneMaterial {
    private static final ResourceLocation SOURCE=new ResourceLocation("sourceofmystery","textures/cinematic/divine/throne_source.png");
    private static final ResourceLocation PALETTE=new ResourceLocation("sourceofmystery","dynamic/divine_throne_pearl");
    private static volatile boolean loaded;
    static void invalidate(){loaded=false;}
    static ResourceLocation texture(){
        if(!loaded){
            var mc=Minecraft.getInstance();
            try(var stream=mc.getResourceManager().getResourceOrThrow(SOURCE).open()){
                NativeImage image=NativeImage.read(stream);
                for(int y=0;y<image.getHeight();y++)for(int x=0;x<image.getWidth();x++){
                    int pixel=image.getPixelRGBA(x,y);float r=(pixel&255)/255f,g=((pixel>>>8)&255)/255f,b=((pixel>>>16)&255)/255f;
                    float luminance=.2126f*r+.7152f*g+.0722f*b;
                    float warm=Mth.clamp((r-b-.045f)/.085f,0,1);warm=warm*warm*(3-2*warm);
                    float pearl=.26f+.72f*(float)Math.pow(Mth.clamp(luminance/.58f,0,1),.72);
                    float gray=.18f+.30f*(float)Math.pow(Mth.clamp(luminance/.48f,0,1),1.1);
                    float shade=Mth.lerp(warm,pearl,gray);
                    // Preserve carved relief and wear without any blue or gold tint.
                    shade*=.38f+.62f*Mth.clamp(luminance/.065f,0,1);
                    int neutral=Mth.clamp(Math.round(shade*255),0,255);
                    image.setPixelRGBA(x,y,(pixel&0xff000000)|neutral|(neutral<<8)|(neutral<<16));
                }
                mc.getTextureManager().register(PALETTE,new DynamicTexture(image));loaded=true;
            }catch(java.io.IOException e){throw new IllegalStateException("Unable to load supplied throne material",e);}
        }
        return PALETTE;
    }
    private DivineThroneMaterial(){}
}
