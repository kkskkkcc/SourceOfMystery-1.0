package com.sourceofmystery.client;

import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Axis;
import com.sourceofmystery.entity.OutsiderRitual;
import net.minecraft.client.renderer.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import java.util.HashMap;
import java.util.Map;

/** Reference-art V2 ritual. All dimensions here are blocks, except the back halo. */
final class OutsiderRitualRenderer {
    private static final ResourceLocation BASE=texture("base"),ADVANCED=texture("advanced"),
            SUPER=texture("super"),SATELLITE=texture("satellite"),SKY=texture("sky"),BACK=texture("back");
    private static final Map<ResourceLocation,RenderType> TYPES=new HashMap<>();
    private static final Map<ResourceLocation,RenderType> COMPAT_INK=new HashMap<>(),COMPAT_GLOW=new HashMap<>();
    static final RenderType SOLID=colorType("outsider_meteor_solid",false);
    static final RenderType GLOW=colorType("outsider_ritual_glow",true);
    private static ResourceLocation texture(String name){return new ResourceLocation("sourceofmystery","textures/entity/outsider/ritual_v2_"+name+".png");}
    private static class States extends RenderStateShard {
        static final TransparencyStateShard ALPHA=TRANSLUCENT_TRANSPARENCY,NONE=NO_TRANSPARENCY;
        static final CullStateShard CULL=NO_CULL;
        static final WriteMaskStateShard COLOR=COLOR_WRITE,DEPTH=COLOR_DEPTH_WRITE;
        static final DepthTestStateShard TEST=LEQUAL_DEPTH_TEST;
        static final LightmapStateShard LIGHT=LIGHTMAP;
        static final OverlayStateShard OVER=OVERLAY;
        private States(){super("ritual_v2",()->{},()->{});}
    }
    private static RenderType colorType(String name,boolean glow){
        return RenderType.create(name,DefaultVertexFormat.POSITION_COLOR,VertexFormat.Mode.QUADS,4096,false,glow,
                RenderType.CompositeState.builder().setShaderState(new RenderStateShard.ShaderStateShard(GameRenderer::getPositionColorShader))
                        .setTransparencyState(glow?States.ALPHA:States.NONE).setCullState(States.CULL)
                        .setDepthTestState(States.TEST).setWriteMaskState(glow?States.COLOR:States.DEPTH).createCompositeState(false));
    }
    private static RenderType type(ResourceLocation texture){
        if(OutsiderShaderCompat.active())return compatibleType(texture.equals(BACK)?OutsiderShaderCompat.backTexture(texture):texture,false);
        return TYPES.computeIfAbsent(texture,t->RenderType.create("outsider_seal_"+t.getPath(),
                DefaultVertexFormat.POSITION_COLOR_TEX,VertexFormat.Mode.QUADS,4096,false,true,
                RenderType.CompositeState.builder()
                        .setShaderState(new RenderStateShard.ShaderStateShard(t.equals(BACK)?OutsiderShaders::halo:GameRenderer::getPositionColorTexShader))
                        .setTextureState(new RenderStateShard.TextureStateShard(t,true,false))
                        .setTransparencyState(States.ALPHA).setCullState(States.CULL).setDepthTestState(States.TEST)
                        .setWriteMaskState(States.COLOR).createCompositeState(false)));
    }
    private static RenderType compatibleType(ResourceLocation texture,boolean glow){
        return (glow?COMPAT_GLOW:COMPAT_INK).computeIfAbsent(texture,t->RenderType.create(
                "outsider_compat_"+(glow?"glow_":"ink_")+t.getPath(),DefaultVertexFormat.NEW_ENTITY,VertexFormat.Mode.QUADS,4096,false,true,
                RenderType.CompositeState.builder()
                        .setShaderState(new RenderStateShard.ShaderStateShard(glow?GameRenderer::getRendertypeEntityTranslucentEmissiveShader:GameRenderer::getRendertypeEntityTranslucentShader))
                        .setTextureState(new RenderStateShard.TextureStateShard(t,true,false))
                        .setTransparencyState(States.ALPHA).setCullState(States.CULL).setDepthTestState(States.TEST)
                        .setLightmapState(States.LIGHT).setOverlayState(States.OVER)
                        // Both pigment and emissive strokes need real depth.
                        // This also covers the gray back halo's single pass;
                        // entity alpha testing preserves the clear sheet gaps.
                        .setWriteMaskState(States.DEPTH).createCompositeState(false)));
    }
    static void render(PoseStack poses,MultiBufferSource buffers,float age,float height){
        float t=OutsiderRitual.STAFF_END+age/20f;
        float fade=1-ease(t,OutsiderRitual.BEAM_HOLD_END,OutsiderRitual.RINGS_FADE_END);
        if(fade>0){
            layer(poses,buffers,BASE,0,5*ease(t,3,7),t*28,fade);
            layer(poses,buffers,ADVANCED,3,7.5f*ease(t,7,7.65f),-t*92,fade);
            float superOpen=ease(t,9,9.8f);
            if(superOpen>0){
                // Four independently rotating engraved regions preserve every
                // element of the complex sheet without stacking whole copies.
                float[] edges={0,.32f,.60f,.81f,1};
                float[] rates={16,-38,27,-19};
                for(int i=0;i<4;i++){
                    poses.pushPose();poses.translate(0,6+i*.018,0);
                    poses.mulPose(Axis.YP.rotationDegrees(t*rates[i]));
                    annulus(poses,buffers,SUPER,10*superOpen,edges[i],edges[i+1],fade);
                    poses.popPose();
                }
            }
            layer(poses,buffers,ADVANCED,9,7.5f*ease(t,11,11.65f),t*92,fade);
            float sat=fade*ease(t,11,12);
            if(sat>0)for(int k=0;k<4;k++){
                float a=t*.38f+k*Mth.HALF_PI;
                float x=10*Mth.cos(a),z=10*Mth.sin(a),y=9;
                poses.pushPose();poses.translate(x,y,z);
                poses.mulPose(new Quaternionf().rotationTo(new Vector3f(0,1,0),new Vector3f(-x,6-y,-z).normalize()));
                poses.mulPose(Axis.YP.rotationDegrees(t*12+k*90));
                disk(poses,buffers,SATELLITE,5,sat);
                poses.popPose();
            }
        }
        if(t>=OutsiderRitual.CHANT_END&&t<OutsiderRitual.BEAM_END){
            float beamAlpha=(1-ease(t,20,22))*ease(t,15,15.25f);
            VertexConsumer vc=OutsiderShaderCompat.glow(buffers,GLOW);Matrix4f m=poses.last().pose();
            for(int ring=0;ring<3;ring++)for(int i=0;i<48;i++){
                float r=.28f+ring*.27f;double a=i*Math.PI/24,b=(i+1)*Math.PI/24;
                float[] c={.36f,.035f,.66f,beamAlpha*(ring==0?.96f:.22f)};
                EffectGeometry.glowQuad(vc,m,p(r,0,a),p(r,0,b),p(r,height,b),p(r,height,a),c,c);
            }
        }
    }
    static void renderSky(PoseStack poses,MultiBufferSource buffers,float seconds){
        float open=ease(seconds,0,2),alpha=1-ease(seconds,3,5);
        layer(poses,buffers,SKY,0,OutsiderRitual.SKY_RADIUS*open,seconds*9,alpha);
    }
    static void renderBackHalo(PoseStack poses,MultiBufferSource buffers,Matrix4f root,float age){
        renderBackHalo(poses,buffers,root,age,1);
    }
    static void renderBackHalo(PoseStack poses,MultiBufferSource buffers,Matrix4f root,float age,float opacity){
        poses.pushPose();poses.mulPoseMatrix(root);poses.translate(0,14,3.5);
        poses.mulPose(Axis.XP.rotationDegrees(90));
        poses.mulPose(Axis.YP.rotationDegrees(age*.5f)); // 10 degrees per second, continuous across poses.
        disk(poses,buffers,BACK,9.8f,.60f*opacity);
        poses.popPose();
    }
    private static float ease(float t,float a,float b){return (float)OutsiderRitual.smooth((t-a)/(b-a));}
    private static void layer(PoseStack poses,MultiBufferSource buffers,ResourceLocation tex,float y,float r,float angle,float alpha){
        if(r<.002f||alpha<.001f)return;
        poses.pushPose();poses.translate(0,y,0);poses.mulPose(Axis.YP.rotationDegrees(angle));
        disk(poses,buffers,tex,r,alpha);poses.popPose();
    }
    private static void disk(PoseStack poses,MultiBufferSource buffers,ResourceLocation tex,float radius,float alpha){
        // A circular mesh has no square border even if a filter samples the
        // texture margin. No cached consumer survives a RenderType switch.
        annulus(poses,buffers,tex,radius,0,1,alpha);
    }
    private static void annulus(PoseStack poses,MultiBufferSource buffers,ResourceLocation tex,float radius,float inner,float outer,float alpha){
        boolean shaders=OutsiderShaderCompat.active();
        if(shaders&&OutsiderShaderCompat.shadowPass())return;
        Matrix4f m=poses.last().pose();
        if(!tex.equals(BACK)&&OutsiderRitualComposite.enabled()){
            boolean gray=tex.equals(BACK);
            OutsiderRitualComposite.queue(gray?OutsiderShaderCompat.backTexture(tex):tex,m,radius,inner,outer,alpha,gray);
            return;
        }
        // Circular geometry maps the entire HD sheet without introducing a
        // rectangular background plane.
        boolean gray=shaders&&tex.equals(BACK);
        int passes=shaders&&!gray?2:1;
        for(int pass=0;pass<passes;pass++){
            // A saturated pigment pass carries the engraving's actual color.
            // A restrained emissive pass supplies the halo. Feeding the whole
            // bright purple sheet through spider-eyes alone triggers shader
            // packs' Enderman eye amplification and washes it towards white.
            boolean glow=pass==1;
            RenderType material=glow?compatibleType(tex,true):type(tex);
            VertexConsumer vc=buffers.getBuffer(material);if(shaders)vc=OutsiderShaderCompat.complete(vc);
            float red=gray?1:!shaders?.68f:tex.equals(SKY)?.9f:glow?.48f:.62f;
            float green=gray?1:!shaders?.40f:glow?.018f:.045f;
            float blue=gray?1:!shaders?.88f:tex.equals(SKY)?1:glow?.55f:.65f;
            float opacity=alpha*(glow?(tex.equals(SKY)?.85f:.28f):1);
            for(int i=0;i<192;i++){
                double a=i*Math.PI/96,b=(i+1)*Math.PI/96;
                texVertex(vc,m,radius,inner,a,opacity,red,green,blue);texVertex(vc,m,radius,outer,a,opacity,red,green,blue);
                texVertex(vc,m,radius,outer,b,opacity,red,green,blue);texVertex(vc,m,radius,inner,b,opacity,red,green,blue);
            }
        }
    }
    private static void texVertex(VertexConsumer vc,Matrix4f m,float radius,float factor,double a,float alpha,float red,float green,float blue){
        float x=(float)Math.cos(a)*factor,z=(float)Math.sin(a)*factor;
        vc.vertex(m,x*radius,0,z*radius).color(red,green,blue,alpha).uv(.5f+x*.5f,.5f+z*.5f).endVertex();
    }
    private static float[] p(float r,float y,double a){return new float[]{r*(float)Math.cos(a),y,r*(float)Math.sin(a)};}
}
