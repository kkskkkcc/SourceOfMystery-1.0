package com.sourceofmystery.client;

import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Axis;
import com.sourceofmystery.staff.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import java.util.*;

/** Deterministic, flowing turbulence sheets, cloud billows and multi-vortex debris skirt. */
public final class TornadoGeometry {
    private static final Map<ResourceLocation,RenderType> MATERIALS=new HashMap<>();
    private static final class States extends RenderStateShard{
        static final TransparencyStateShard BLEND=TRANSLUCENT_TRANSPARENCY;static final CullStateShard CULL=NO_CULL;
        static final WriteMaskStateShard WRITE=COLOR_WRITE;static final DepthTestStateShard DEPTH=LEQUAL_DEPTH_TEST;
        private States(){super("storm",()->{},()->{});}
    }
    private static RenderType material(TornadoVariant variant,String name){
        var texture=new ResourceLocation("sourceofmystery",name.equals("glow")?"textures/entity/outsider/white.png":"textures/effect/weather/"+variant.id+"_"+name+".png");
        return MATERIALS.computeIfAbsent(texture,id->RenderType.create("tornado_"+variant.id+"_"+name,DefaultVertexFormat.POSITION_COLOR_TEX,VertexFormat.Mode.QUADS,1<<18,false,true,
                RenderType.CompositeState.builder().setShaderState(new RenderStateShard.ShaderStateShard(WeatherShaders::wind))
                        .setTextureState(new RenderStateShard.TextureStateShard(id,true,false)).setTransparencyState(States.BLEND).setCullState(States.CULL).setWriteMaskState(States.WRITE)
                        .setDepthTestState(States.DEPTH).createCompositeState(false)));
    }
    private TornadoGeometry(){}
    private static float ease(float n){n=Mth.clamp(n,0,1);return n*n*(3-2*n);}
    private static float hash(int i){return EffectGeometry.hash(819,i);}
    public static void charge(PoseStack p,MultiBufferSource b,float age,int duration){
        float open=ease(age/Math.max(1,duration*.6f));
        p.pushPose();p.translate(0,.4,0);p.mulPose(Axis.XP.rotationDegrees(90));StaffArcaneMaterial.seal(p,b,2.8f*open,age*1.1f,new float[]{.61f,.83f,1,open*.7f},true);p.popPose();
        for(int i=0;i<3;i++){p.pushPose();p.translate(0,2.5+i*.45,0);p.mulPose(Axis.XP.rotationDegrees(90));StaffArcaneMaterial.seal(p,b,(1.2f+i*.4f)*open,age*(-1+i*.8f),new float[]{.63f,.82f,1,.4f*open},false);p.popPose();}
    }
    public static void render(PoseStack p,MultiBufferSource.BufferSource b,WeatherVisuals.View s,float partial,Vec3 camera){
        float age=s.age(partial),t=age/20,fade=ease(s.remaining(partial)/40),formed=ease((age-25)/95)*fade,cloud=ease(age/45)*fade;
        var variant=s.variant();float[] tint=switch(variant){case NORMAL->new float[]{.32f,.36f,.38f};case SAND->new float[]{.60f,.42f,.23f};case WATER->new float[]{.23f,.39f,.45f};case FIRE->new float[]{.31f,.22f,.19f};};
        var ink=b.getBuffer(material(variant,"sheet"));
        for(int layer=0;layer<3;layer++)funnel(p,ink,t,formed,layer,tint,0,0,StaffWeather.HEIGHT,1);
        for(int k=0;k<3;k++){
            float a=t*.21f+k*Mth.TWO_PI/3,r=22+4*Mth.sin(t*.31f+k);
            funnel(p,ink,t*1.7f,formed,0,tint,Mth.cos(a)*r,Mth.sin(a)*r,52,.19f);
        }
        ink=b.getBuffer(material(variant,"cloud"));
        // Ground debris wall, then rising billows, then a low mesocyclone cloud shelf.
        for(int i=0;i<125;i++){
            float a=i*2.399963f+t*(.19f+hash(i)*.16f),r=(10+hash(i+400)*72)*formed;
            float y=(1+hash(i+700)*10)*formed;
            puff(p,ink,Mth.cos(a)*r,y,Mth.sin(a)*r,6+hash(i+800)*11,tint,.22f*formed);
        }
        for(int i=0;i<150;i++){
            float u=hash(i+1100),r=(12+46*u*u)*(1+hash(i+1200)*.32f)*formed,a=i*2.399963f+t*(.3f+.7f*(1-u));
            float y=10+u*158+(1-formed)*StaffWeather.HEIGHT;
            puff(p,ink,Mth.cos(a)*r+Mth.sin(t*.21f+u*6)*u*10,y,Mth.sin(a)*r,8+u*13,tint,.25f*formed);
        }
        float[] cloudTint={.23f,.27f,.29f};
        ink=b.getBuffer(material(TornadoVariant.NORMAL,"cloud"));
        for(int i=0;i<90;i++){
            float a=i*2.399963f+t*.035f,r=(15+hash(i+1600)*135)*cloud;
            puff(p,ink,Mth.cos(a)*r,172+hash(i+1700)*21,Mth.sin(a)*r,23+hash(i+1800)*29,cloudTint,.34f*cloud);
        }
        if(variant==TornadoVariant.FIRE||variant==TornadoVariant.WATER){
            // Stay inside the custom material pass: switching to an Iris entity
            // program midway leaves its active-program cache inconsistent.
            var glow=b.getBuffer(material(variant,"glow"));float[] c=variant==TornadoVariant.FIRE?new float[]{1,.25f,.025f,.6f*formed}:new float[]{.48f,.86f,1,.35f*formed};
            for(int k=0;k<22;k++){
                float u=(t*.12f+k*.071f)%1,a=t*(1.8f-u)+k*2.399963f,r=(10+46*u*u)*formed;
                float[] from={Mth.cos(a)*r,u*165,Mth.sin(a)*r};a+=.09f;float[] to={Mth.cos(a)*r,u*165+2.4f,Mth.sin(a)*r};
                line(glow,p.last().pose(),from,to,variant==TornadoVariant.FIRE?.25f:.10f,c);
            }
        }
    }
    private static void funnel(PoseStack p,VertexConsumer v,float t,float formed,int layer,float[] color,float x,float z,float height,float scale){
        if(formed<=0)return;var m=p.last().pose();int sides=64,rings=28;
        for(int j=0;j<rings;j++){
            float u=j/(float)rings,U=(j+1f)/rings,y=(1-formed)*height+u*height*formed,Y=(1-formed)*height+U*height*formed;
            float r=(11+48*u*u+(float)Math.sin(u*12+t*.6)*2*u)*(1-layer*.17f)*scale*formed;
            float R=(11+48*U*U+(float)Math.sin(U*12+t*.6)*2*U)*(1-layer*.17f)*scale*formed;
            float offset=Mth.sin(u*5+t*.21f)*u*11*scale,Offset=Mth.sin(U*5+t*.21f)*U*11*scale;
            for(int i=0;i<sides;i++){
                float a=i*Mth.TWO_PI/sides+t*(.45f+layer*.16f)+u*5,A=(i+1)*Mth.TWO_PI/sides+t*(.45f+layer*.16f)+u*5;
                float c=i*Mth.TWO_PI/sides+t*(.45f+layer*.16f)+U*5,C=(i+1)*Mth.TWO_PI/sides+t*(.45f+layer*.16f)+U*5;
                float uv=i/(float)sides*6,UV=(i+1f)/sides*6,vv=u*4-t*.16f,VV=U*4-t*.16f;
                float alpha=(.55f+layer*.045f)*formed*ease((1-u)*8);
                vertex(v,m,x+offset+Mth.cos(a)*r,y,z+Mth.sin(a)*r,uv,vv,color,alpha);
                vertex(v,m,x+offset+Mth.cos(A)*r,y,z+Mth.sin(A)*r,UV,vv,color,alpha);
                vertex(v,m,x+Offset+Mth.cos(C)*R,Y,z+Mth.sin(C)*R,UV,VV,color,alpha);
                vertex(v,m,x+Offset+Mth.cos(c)*R,Y,z+Mth.sin(c)*R,uv,VV,color,alpha);
            }
        }
    }
    private static void vertex(VertexConsumer v,Matrix4f m,float x,float y,float z,float u,float w,float[] c,float a){v.vertex(m,x,y,z).color(1,1,1,a).uv(u,w).endVertex();}
    private static void puff(PoseStack p,VertexConsumer v,float x,float y,float z,float size,float[] tint,float alpha){
        p.pushPose();p.translate(x,y,z);p.mulPose(Minecraft.getInstance().getEntityRenderDispatcher().cameraOrientation());var m=p.last().pose();
        vertex(v,m,-size,size,0,0,0,tint,alpha);vertex(v,m,size,size,0,1,0,tint,alpha);vertex(v,m,size,-size,0,1,1,tint,alpha);vertex(v,m,-size,-size,0,0,1,tint,alpha);p.popPose();
    }
    private static void line(VertexConsumer v,Matrix4f m,float[] a,float[] b,float width,float[] c){
        v.vertex(m,a[0]-width,a[1],a[2]).color(c[0],c[1],c[2],c[3]).uv(0,0).endVertex();
        v.vertex(m,a[0]+width,a[1],a[2]).color(c[0],c[1],c[2],c[3]).uv(1,0).endVertex();
        v.vertex(m,b[0]+width,b[1],b[2]).color(c[0],c[1],c[2],c[3]).uv(1,1).endVertex();
        v.vertex(m,b[0]-width,b[1],b[2]).color(c[0],c[1],c[2],c[3]).uv(0,1).endVertex();
    }
}
