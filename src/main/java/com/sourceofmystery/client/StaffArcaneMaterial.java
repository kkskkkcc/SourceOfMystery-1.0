package com.sourceofmystery.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.lwjgl.opengl.GL11;

/** CC0 engraving artwork, split into independently moving bands; see STAFF_VFX_CREDITS.md. */
public final class StaffArcaneMaterial {
    private static final ResourceLocation SOURCE=id("textures/effect/staff/arcane_circle_cc0.png");
    private static final ResourceLocation RUNES=id("dynamic/staff_runes"),CORE=id("dynamic/staff_core");
    private static boolean loaded;
    private static final RenderType RUNIC=material(RUNES),CENTRAL=material(CORE);
    private StaffArcaneMaterial(){}
    private static ResourceLocation id(String s){return new ResourceLocation("sourceofmystery",s);}
    private static final class States extends RenderStateShard {
        static final TransparencyStateShard GLOW=new TransparencyStateShard("staff_additive",()->{
            RenderSystem.enableBlend();RenderSystem.blendFunc(GL11.GL_SRC_ALPHA,GL11.GL_ONE);
        },()->{RenderSystem.disableBlend();RenderSystem.defaultBlendFunc();});
        static final CullStateShard CULL=NO_CULL;
        // Emissive alpha-test discards the transparent gaps. Real stroke depth keeps
        // deferred clouds behind this near-field seal in Oculus/Complementary.
        static final WriteMaskStateShard WRITE=COLOR_DEPTH_WRITE;
        static final DepthTestStateShard TEST=LEQUAL_DEPTH_TEST;
        static final LightmapStateShard LIGHT=LIGHTMAP;
        static final OverlayStateShard OVER=OVERLAY;
        private States(){super("staff_seal",()->{},()->{});}
    }
    private static RenderType material(ResourceLocation texture){
        return RenderType.create("staff_"+texture.getPath(),DefaultVertexFormat.NEW_ENTITY,VertexFormat.Mode.QUADS,4096,false,true,
                RenderType.CompositeState.builder().setShaderState(new RenderStateShard.ShaderStateShard(GameRenderer::getRendertypeEntityTranslucentEmissiveShader))
                .setTextureState(new RenderStateShard.TextureStateShard(texture,true,false)).setTransparencyState(States.GLOW)
                .setCullState(States.CULL).setWriteMaskState(States.WRITE).setDepthTestState(States.TEST)
                .setLightmapState(States.LIGHT).setOverlayState(States.OVER).createCompositeState(false));
    }
    public static void invalidate(){loaded=false;}
    private static float ease(float p){p=Mth.clamp(p,0,1);return p*p*(3-2*p);}
    private static void prepare(){
        if(loaded)return;
        var mc=Minecraft.getInstance();
        try(var stream=mc.getResourceManager().getResourceOrThrow(SOURCE).open();var source=NativeImage.read(stream)){
            int w=source.getWidth(),h=source.getHeight();
            NativeImage runes=new NativeImage(w,h,false),core=new NativeImage(w,h,false);
            for(int y=0;y<h;y++)for(int x=0;x<w;x++){
                int pixel=source.getPixelRGBA(x,y);
                float dx=(x+.5f-w/2f)/(w/2f),dy=(y+.5f-h/2f)/(h/2f),radius=Mth.sqrt(dx*dx+dy*dy);
                // Black-to-alpha and neutral ink are prepared once. Exclude the original's dice belt.
                float luminance=(pixel&255)/255f;
                int outer=Math.round(luminance*255*ease((radius-.735f)/.025f));
                int inner=Math.round(luminance*255*(1-ease((radius-.49f)/.025f)));
                runes.setPixelRGBA(x,y,(outer<<24)|0xffffff);core.setPixelRGBA(x,y,(inner<<24)|0xffffff);
            }
            mc.getTextureManager().register(RUNES,new DynamicTexture(runes));
            mc.getTextureManager().register(CORE,new DynamicTexture(core));loaded=true;
        }catch(java.io.IOException e){throw new IllegalStateException("Cannot load staff rune artwork",e);}
    }
    public static void seal(PoseStack poses,MultiBufferSource buffers,float radius,float degrees,float[] color,boolean core){
        if(color[3]<.005||radius<.005)return;prepare();
        poses.pushPose();poses.mulPose(Axis.ZP.rotationDegrees(degrees));sprite(poses,buffers,RUNIC,radius,color);poses.popPose();
        if(core){poses.pushPose();poses.translate(0,0,.004);poses.mulPose(Axis.ZP.rotationDegrees(-degrees*.6f));
            sprite(poses,buffers,CENTRAL,radius*1.18f,new float[]{color[0],color[1],color[2],color[3]*.42f});poses.popPose();}
    }
    private static void sprite(PoseStack poses,MultiBufferSource buffers,RenderType type,float r,float[] c){
        var v=OutsiderShaderCompat.complete(buffers.getBuffer(type));var m=poses.last().pose();
        v.vertex(m,-r,-r,0).color(c[0],c[1],c[2],c[3]).uv(0,1).endVertex();
        v.vertex(m,r,-r,0).color(c[0],c[1],c[2],c[3]).uv(1,1).endVertex();
        v.vertex(m,r,r,0).color(c[0],c[1],c[2],c[3]).uv(1,0).endVertex();
        v.vertex(m,-r,r,0).color(c[0],c[1],c[2],c[3]).uv(0,0).endVertex();
    }
}
