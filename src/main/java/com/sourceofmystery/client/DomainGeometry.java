package com.sourceofmystery.client;

import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Axis;
import com.sourceofmystery.staff.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import org.joml.Matrix4f;

/** One world-space vocabulary per field: ice, accretion disk, lift bands, chains, crimson cuts. */
public final class DomainGeometry {
    private static final ResourceLocation ICE=new ResourceLocation("minecraft","textures/block/blue_ice.png");
    private static final DomainShrine SHRINE=new DomainShrine();
    private DomainGeometry(){}
    private static float ease(float p){p=Mth.clamp(p,0,1);return p*p*(3-2*p);}
    private static float[] color(DomainType type,float alpha){return new float[]{((type.color>>16)&255)/255f,((type.color>>8)&255)/255f,(type.color&255)/255f,alpha};}
    private static VertexConsumer glow(MultiBufferSource b){return OutsiderShaderCompat.glow(b,OutsiderRitualRenderer.GLOW);}
    public static void charge(PoseStack p,MultiBufferSource b,float age,int duration,DomainType type){
        float open=ease(age/Math.max(1,duration*.6f));p.pushPose();p.translate(0,-2.1,0);p.mulPose(Axis.XP.rotationDegrees(90));
        StaffArcaneMaterial.seal(p,b,2.7f*open,-age*.8f,color(type,.8f*open),true);p.popPose();
    }
    public static void field(PoseStack p,MultiBufferSource.BufferSource b,DomainVisuals.View f,float partial,net.minecraft.world.phys.Vec3 camera){
        float age=f.age(partial),seconds=age/20,open=ease(age/36),fade=Math.min(1,(f.data().remaining()-f.elapsed())/20f);
        var type=f.type();
        if(type==DomainType.EXECUTION){
            p.pushPose();p.translate(0,.055,0);p.mulPose(Axis.XP.rotationDegrees(90));
            var ink=glow(b);for(int i=0;i<96;i++){double a=i*Math.PI/48,d=(i+1)*Math.PI/48;float[] dark={.08f,.008f,.016f,.64f*open*fade};
                EffectGeometry.glowQuad(ink,p.last().pose(),new float[]{0,0,0},point(30*open,a),point(30*open,d),new float[]{0,0,0},dark,dark);}
            p.popPose();
        }
        p.pushPose();p.translate(0,.075,0);p.mulPose(Axis.XP.rotationDegrees(90));
        if(type==DomainType.EXECUTION)StaffArcaneMaterial.seal(p,b,29*open,-seconds*.7f,color(type,.20f*open*fade),true);
        StaffArcaneMaterial.seal(p,b,(type==DomainType.EXECUTION?4.4f:2.7f)*open,-seconds*8,color(type,open*fade*.63f),true);
        ring(glow(b),p.last().pose(),type.radius*open,.035f,color(type,open*fade*.26f));p.popPose();
        switch(type){
            case FROST->snow(p,b,f,age,camera);
            case DARKNESS->blackHole(p,b,age,fade);
            case GRAVITY->{for(int i=0;i<3;i++){p.pushPose();p.translate(0,.5+(seconds*.8f+i*1.4f)%4.2,0);p.mulPose(Axis.XP.rotationDegrees(90));
                StaffArcaneMaterial.seal(p,b,3.3f+i*.32f,seconds*(12-i*9),color(type,.2f*open*fade),false);p.popPose();}}
            case BINDING->{for(int i=0;i<4;i++){double a=i*Math.PI/2+Math.PI/4;p.pushPose();p.translate(Math.cos(a)*4,0,Math.sin(a)*4);p.mulPose(Axis.YP.rotationDegrees(-f.yaw()));
                StaffArcaneMaterial.seal(p,b,.6f,seconds*5,color(type,open*.65f*fade),true);p.popPose();}}
            case EXECUTION->{
                float reveal=ease((age-32)/35)*fade;
                p.pushPose();p.mulPose(Axis.YP.rotationDegrees(-f.yaw()));p.translate(0,-(1-reveal)*3,-6.5);p.scale(reveal,reveal,reveal);
                SHRINE.render(p,b,seconds);p.popPose();
                p.pushPose();p.mulPose(Axis.YP.rotationDegrees(-f.yaw()));p.translate(0,6.2,-9);
                StaffArcaneMaterial.seal(p,b,3.5f*reveal,-seconds*7,color(type,.55f*reveal),true);p.popPose();
                if(age>=48)for(int i=0;i<18;i++){
                    float phase=(age*.065f+i*.173f)%1,alpha=Mth.sin(phase*Mth.PI)*.55f;
                    double angle=-Math.PI/2+i*2.399963;float r=7+i%7*4;
                    p.pushPose();p.mulPose(Axis.YP.rotationDegrees(-f.yaw()));p.translate(Math.cos(angle)*r,1.3+(i%3)*.7,Math.abs(Math.sin(angle))*r+2);
                    p.mulPose(Axis.YP.rotationDegrees(f.yaw()));p.mulPose(Minecraft.getInstance().getEntityRenderDispatcher().cameraOrientation());p.mulPose(Axis.ZP.rotationDegrees(-35+i%3*35));
                    cut(glow(b),p.last().pose(),10+i%4*3,phase,alpha);p.popPose();
                }
            }
        }
    }
    private static void snow(PoseStack p,MultiBufferSource b,DomainVisuals.View f,float age,net.minecraft.world.phys.Vec3 camera){
        var mc=Minecraft.getInstance();boolean owner=mc.player!=null&&f.data().owner().equals(mc.player.getUUID());
        var ink=glow(b);
        for(int i=0;i<270;i++){
            float dx=(EffectGeometry.hash(771,i)*2-1)*27,dz=(EffectGeometry.hash(773,i)*2-1)*27;
            double x=camera.x+dx,z=camera.z+dz;
            if(x*x+z*z>f.type().radius*f.type().radius||owner&&dx*dx+dz*dz<49)continue;
            double y=camera.y+((i*.773-age*.19)%12+12)%12-4;
            p.pushPose();p.translate(x,y,z);p.mulPose(mc.getEntityRenderDispatcher().cameraOrientation());
            float size=.035f+(i%4)*.012f;float[] c={.87f,.96f,1,.65f};
            EffectGeometry.glowQuad(ink,p.last().pose(),new float[]{-size,size,0},new float[]{size,size,0},new float[]{size,-size,0},new float[]{-size,-size,0},c,c);
            p.popPose();
        }
    }
    private static void blackHole(PoseStack p,MultiBufferSource b,float age,float fade){
        float open=ease(age/45),radius=2.5f*open,t=age/20;
        p.pushPose();p.translate(0,20,0);
        p.pushPose();p.mulPose(Minecraft.getInstance().getEntityRenderDispatcher().cameraOrientation());
        var dark=OutsiderShaderCompat.solid(b,OutsiderRitualRenderer.SOLID);var m=p.last().pose();
        for(int i=0;i<96;i++){double a=i*Math.PI/48,d=(i+1)*Math.PI/48;float[] c={.001f,.0002f,.003f,1};
            EffectGeometry.glowQuad(dark,m,new float[]{0,0,0},point(radius,a),point(radius,d),new float[]{0,0,0},c,c);}
        ring(glow(b),m,radius+.035f,.045f,new float[]{.70f,.38f,1,.9f*fade});
        ring(glow(b),m,radius+.12f,.025f,new float[]{.38f,.16f,1,.5f*fade});p.popPose();
        p.pushPose();p.mulPose(Axis.ZP.rotationDegrees(23));p.mulPose(Axis.XP.rotationDegrees(82));
        var ink=glow(b);m=p.last().pose();
        for(int band=0;band<9;band++)ring(ink,m,(2.65f+band*.16f)*open,.018f,new float[]{.8f-band*.05f,.4f-band*.03f,1,(1-band/10f)*.45f*fade});
        p.popPose();
        ink=glow(b);m=p.last().pose();
        for(int k=0;k<24;k++)for(int j=0;j<18;j++){
            float u=j/18f,v=(j+1)/18f;float a=k*Mth.TWO_PI/24+t*1.3f+u*2.5f;
            float r=(8*(1-u)+.5f)*open,R=(8*(1-v)+.5f)*open;
            float[] from={Mth.cos(a)*r,-5*(1-u)*(1-u),Mth.sin(a)*r};a+=(v-u)*2.5f;
            float[] to={Mth.cos(a)*R,-5*(1-v)*(1-v),Mth.sin(a)*R};
            ribbon(ink,m,from,to,.014f,new float[]{.58f,.35f,1,.22f*fade*u});
        }
        p.popPose();
    }
    public static void ice(PoseStack p,MultiBufferSource b,Entity e){
        float w=e.getBbWidth()/2+.07f,h=e.getBbHeight()+.09f;
        var v=OutsiderShaderCompat.complete(b.getBuffer(RenderType.entityTranslucentEmissive(ICE)));var m=p.last().pose();
        float[][] points={{-w,-.03f,-w},{w,-.03f,-w},{w,h,-w},{-w,h,-w},{-w,-.03f,w},{w,-.03f,w},{w,h,w},{-w,h,w}};
        for(int[] face:new int[][]{{0,1,2,3},{5,4,7,6},{4,0,3,7},{1,5,6,2},{3,2,6,7}})for(int i=0;i<4;i++){
            float[] a=points[face[i]];v.vertex(m,a[0],a[1],a[2]).color(.7f,.88f,1,.42f).uv(i==0||i==3?0:1,i<2?1:0).endVertex();
        }
        var ink=glow(b);float[] c={.4f,.78f,1,.9f};
        for(float x:new float[]{-w,w})for(float z:new float[]{-w,w})ribbon(ink,m,new float[]{x,0,z},new float[]{x,h,z},.009f,c);
    }
    public static void shackles(PoseStack p,MultiBufferSource b,Entity e,float age){
        p.pushPose();p.translate(0,.12,0);p.mulPose(Axis.XP.rotationDegrees(90));
        StaffArcaneMaterial.seal(p,b,e.getBbWidth()*.8f+.25f,0,color(DomainType.BINDING,.8f),true);p.popPose();
        var ink=glow(b);for(int i=0;i<4;i++){float a=i*Mth.HALF_PI;float r=e.getBbWidth()*.55f;
            ribbon(ink,p.last().pose(),new float[]{Mth.cos(a)*r,0,Mth.sin(a)*r},new float[]{Mth.cos(a+.5f)*r,e.getBbHeight()*.75f,Mth.sin(a+.5f)*r},.009f,color(DomainType.BINDING,.6f));}
    }
    public static void slashes(PoseStack p,MultiBufferSource b,Entity e,float age){
        p.pushPose();p.translate(0,e.getBbHeight()*.55,0);
        for(int i=0;i<5;i++){
            float cycle=age/7+i*.31f+e.getId()*.13f,phase=cycle-(int)cycle;
            float angle=(float)Math.floor(cycle)*73+i*33;
            p.pushPose();p.mulPose(Minecraft.getInstance().getEntityRenderDispatcher().cameraOrientation());p.mulPose(Axis.ZP.rotationDegrees(angle));
            cut(glow(b),p.last().pose(),Math.max(1.4f,e.getBbWidth()*2.3f),phase,Mth.sin(phase*Mth.PI));p.popPose();
        }p.popPose();
    }
    private static void cut(VertexConsumer c,Matrix4f m,float length,float phase,float alpha){
        for(int i=0;i<18;i++){
            float a=i/18f,b=(i+1)/18f,x=(a-.5f)*length,X=(b-.5f)*length;
            float y=.17f*length*(a*a-.25f),Y=.17f*length*(b*b-.25f),w=.10f*Mth.sqrt(length)*Mth.sin(a*Mth.PI),W=.10f*Mth.sqrt(length)*Mth.sin(b*Mth.PI);
            stripe(c,m,x,y,X,Y,w*2.3f,W*2.3f,new float[]{.012f,0,.003f,alpha*.9f});
            stripe(c,m,x,y,X,Y,w,W,new float[]{.94f,.015f,.095f,alpha});
            stripe(c,m,x,y,X,Y,w*.25f,W*.25f,new float[]{1,.65f,.65f,alpha*.8f});
        }
    }
    private static void stripe(VertexConsumer c,Matrix4f m,float x,float y,float X,float Y,float w,float W,float[] col){
        EffectGeometry.glowQuad(c,m,new float[]{x,y-w,0},new float[]{x,y+w,0},new float[]{X,Y+W,0},new float[]{X,Y-W,0},col,col);
    }
    private static void ring(VertexConsumer c,Matrix4f m,float r,float w,float[] color){
        for(int i=0;i<96;i++){double a=i*Math.PI/48,b=(i+1)*Math.PI/48;EffectGeometry.glowQuad(c,m,point(r-w,a),point(r+w,a),point(r+w,b),point(r-w,b),color,color);}
    }
    private static float[] point(float r,double a){return new float[]{(float)Math.cos(a)*r,(float)Math.sin(a)*r,0};}
    private static void ribbon(VertexConsumer c,Matrix4f m,float[] a,float[] b,float w,float[] color){
        EffectGeometry.glowQuad(c,m,new float[]{a[0]-w,a[1],a[2]},new float[]{a[0]+w,a[1],a[2]},new float[]{b[0]+w,b[1],b[2]},new float[]{b[0]-w,b[1],b[2]},color,color);
    }
}
