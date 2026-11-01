package com.sourceofmystery.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.renderer.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import java.util.*;
import java.lang.reflect.*;

/** Complementary's real deferred cloud coverage, without editing the shader pack.
 * Depth enters before deferred fog/clouds; emissive color enters afterwards.
 * The optional target bridge keeps cloud distance in floating-point colortex5;
 * no shader pack edit, per-frame copy or disabled depth test is required.
 */
@Mod.EventBusSubscriber(modid="sourceofmystery",value=Dist.CLIENT)
final class OutsiderRitualComposite {
    private record Draw(ResourceLocation texture,Matrix4f matrix,float radius,float inner,float outer,float alpha,boolean gray){}
    private static final List<Draw> DRAWS=new ArrayList<>();
    private static final Map<ResourceLocation,RenderType> DEPTH=new HashMap<>(),COLOR=new HashMap<>();
    private static MultiBufferSource.BufferSource buffers;
    private static class States extends RenderStateShard {
        private States(){super("ritual_composite",()->{},()->{});}
        static final WriteMaskStateShard Z=COLOR_DEPTH_WRITE,RGB=COLOR_WRITE;
        static final CullStateShard CULL=NO_CULL;
        static final TransparencyStateShard NONE=NO_TRANSPARENCY,ALPHA=TRANSLUCENT_TRANSPARENCY;
        static final DepthTestStateShard TEST=LEQUAL_DEPTH_TEST;
    }
    static boolean enabled(){return OutsiderShaders.ritual()!=null&&OutsiderShaders.ritualDepth()!=null&&OutsiderShaderCompat.complementary()&&!OutsiderShaderCompat.shadowPass()&&Targets.texture()>0;}
    static void queue(ResourceLocation texture,Matrix4f matrix,float r,float inner,float outer,float alpha,boolean gray){
        DRAWS.add(new Draw(texture,new Matrix4f(matrix),r,inner,outer,alpha,gray));
    }
    static void reload(){DRAWS.clear();Targets.preparedTexture=0;}
    /** Iris deliberately suppresses unknown vanilla shaders. These two passes
     * explicitly select an Iris framebuffer and supply their own cloud/depth
     * contract, so restore only their write masks after ShaderInstance.apply.
     * The real LEQUAL depth test remains enabled throughout. */
    static final class RitualShader extends ShaderInstance {
        RitualShader(net.minecraft.server.packs.resources.ResourceProvider provider,String name)throws java.io.IOException{
            super(provider,new ResourceLocation("sourceofmystery",name),DefaultVertexFormat.POSITION_COLOR_TEX);
        }
        @Override public void apply(){super.apply();Targets.restoreMaterialState();}
    }
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void render(RenderLevelStageEvent event){
        if(OutsiderShaderCompat.shadowPass())return;
        if(event.getStage()==RenderLevelStageEvent.Stage.AFTER_SKY){DRAWS.clear();return;}
        if(DRAWS.isEmpty()||!enabled())return;
        if(buffers==null)buffers=MultiBufferSource.immediate(new BufferBuilder(65536));
        if(event.getStage()==RenderLevelStageEvent.Stage.AFTER_ENTITIES){
            Targets.preserveCloudDistance();
            ShaderInstance iris=GameRenderer.getRendertypeEntityTranslucentEmissiveShader();iris.apply();
            try{for(Draw draw:DRAWS)emit(draw,true);buffers.endBatch();}finally{iris.clear();}
        }else if(event.getStage()==RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS){
            // Iris binds the correct flipped colortex0 for this phase. The
            // emissive program itself emits no vertices; it only selects that
            // existing framebuffer. Our shader writes color attachment zero.
            ShaderInstance iris=GameRenderer.getRendertypeEntityTranslucentEmissiveShader();
            iris.apply();
            try{
                ShaderInstance shader=OutsiderShaders.ritual();shader.setSampler("CloudMask",Targets.texture());
                shader.safeGetUniform("RenderDistance").set(net.minecraft.client.Minecraft.getInstance().options.getEffectiveRenderDistance()*16f);
                for(Draw draw:DRAWS)emit(draw,false);buffers.endBatch();
            }finally{iris.clear();DRAWS.clear();}
        }
    }
    private static RenderType type(ResourceLocation texture,boolean depth){
        return (depth?DEPTH:COLOR).computeIfAbsent(texture,t->{
            var state=RenderType.CompositeState.builder()
                .setShaderState(new RenderStateShard.ShaderStateShard(depth?OutsiderShaders::ritualDepth:OutsiderShaders::ritual))
                .setTextureState(new RenderStateShard.TextureStateShard(t,true,false)).setCullState(States.CULL)
                .setTransparencyState(depth?States.NONE:States.ALPHA).setDepthTestState(States.TEST)
                .setWriteMaskState(depth?States.Z:States.RGB);
            return RenderType.create("outsider_ritual_"+(depth?"depth_":"composite_")+t.getPath(),
                DefaultVertexFormat.POSITION_COLOR_TEX,VertexFormat.Mode.QUADS,4096,false,!depth,state.createCompositeState(false));
        });
    }
    private static void emit(Draw d,boolean depth){
        VertexConsumer v=buffers.getBuffer(type(d.texture,depth));
        float red=d.gray?1/1.65f:.68f,green=d.gray?1/1.65f:.08f,blue=d.gray?1/1.65f:.88f;
        for(int i=0;i<192;i++){
            double a=i*Math.PI/96,b=(i+1)*Math.PI/96;
            vertex(v,d,d.inner,a,red,green,blue);vertex(v,d,d.outer,a,red,green,blue);
            vertex(v,d,d.outer,b,red,green,blue);vertex(v,d,d.inner,b,red,green,blue);
        }
    }
    private static void vertex(VertexConsumer v,Draw d,float factor,double angle,float r,float g,float b){
        float x=(float)Math.cos(angle)*factor,z=(float)Math.sin(angle)*factor;
        v.vertex(d.matrix,x*d.radius,0,z*d.radius).color(r,g,b,d.alpha).uv(.5f+x*.5f,.5f+z*.5f).endVertex();
    }
    /** Optional Oculus target access; unavailable versions use the normal renderer. */
    private static final class Targets {
        private static Method manager,pipeline,get,flipped,main,alt,unlock,blend,width,height;private static Field targets;
        private static int preparedTexture,preparedWidth,preparedHeight;
        static{
            try{
                manager=Class.forName("net.irisshaders.iris.Iris").getMethod("getPipelineManager");
                pipeline=Class.forName("net.irisshaders.iris.pipeline.PipelineManager").getMethod("getPipelineNullable");
                Class<?> p=Class.forName("net.irisshaders.iris.pipeline.IrisRenderingPipeline");
                targets=p.getDeclaredField("renderTargets");targets.setAccessible(true);flipped=p.getMethod("getFlippedAfterTranslucent");
                get=Class.forName("net.irisshaders.iris.targets.RenderTargets").getMethod("get",int.class);
                Class<?> t=Class.forName("net.irisshaders.iris.targets.RenderTarget");main=t.getMethod("getMainTexture");alt=t.getMethod("getAltTexture");
                width=t.getMethod("getWidth");height=t.getMethod("getHeight");
                unlock=Class.forName("net.irisshaders.iris.gl.blending.DepthColorStorage").getMethod("unlockDepthColor");
                blend=Class.forName("net.irisshaders.iris.gl.blending.BlendModeOverride").getMethod("restore");
            }catch(ReflectiveOperationException|LinkageError error){manager=null;}
        }
        static void restoreMaterialState(){
            if(unlock!=null)try{unlock.invoke(null);blend.invoke(null);}catch(ReflectiveOperationException|LinkageError ignored){}
        }
        static void preserveCloudDistance(){
            try{
                Object p=pipeline.invoke(manager.invoke(null)),t=get.invoke(targets.get(p),5);
                int first=(int)main.invoke(t),w=(int)width.invoke(t),h=(int)height.invoke(t);
                if(first==preparedTexture&&w==preparedWidth&&h==preparedHeight)return;
                int previous=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
                try{
                    // RGBA8 clamps sqrt(cloudDistance/far) to 1: at 8 chunks,
                    // a cloud at Y=192 becomes indistinguishable from clear sky.
                    // Retain the same two Iris-owned texture IDs/attachments,
                    // preserve their contents, and widen storage once per size.
                    // Shader reload/resize remains owned by Iris as before.
                    for(int texture:new int[]{first,(int)alt.invoke(t)}){
                        RenderSystem.bindTexture(texture);
                        if(GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D,0,GL11.GL_TEXTURE_INTERNAL_FORMAT)!=GL11.GL_RGBA8)continue;
                        java.nio.FloatBuffer data=org.lwjgl.system.MemoryUtil.memAllocFloat(Math.multiplyExact(w*h,4));
                        try{
                            GL11.glGetTexImage(GL11.GL_TEXTURE_2D,0,GL11.GL_RGBA,GL11.GL_FLOAT,data);
                            GL11.glTexImage2D(GL11.GL_TEXTURE_2D,0,org.lwjgl.opengl.GL30.GL_RGBA16F,w,h,0,GL11.GL_RGBA,GL11.GL_FLOAT,data);
                            org.lwjgl.opengl.GL30.glGenerateMipmap(GL11.GL_TEXTURE_2D);
                        }finally{org.lwjgl.system.MemoryUtil.memFree(data);}
                    }
                }finally{RenderSystem.bindTexture(previous);}
                preparedTexture=first;preparedWidth=w;preparedHeight=h;
            }catch(ReflectiveOperationException error){throw new IllegalStateException("Unable to retain ritual cloud depth",error);}
        }
        static int texture(){
            if(manager==null)return 0;
            try{
                Object p=pipeline.invoke(manager.invoke(null));if(p==null||!targets.getDeclaringClass().isInstance(p))return 0;
                Object t=get.invoke(targets.get(p),5);if(t==null)return 0;
                return (int)(((Set<?>)flipped.invoke(p)).contains(5)?alt:main).invoke(t);
            }catch(ReflectiveOperationException|LinkageError error){return 0;}
        }
    }
}
