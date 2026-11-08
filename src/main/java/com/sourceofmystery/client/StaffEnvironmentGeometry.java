package com.sourceofmystery.client;

import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Axis;
import com.sourceofmystery.staff.EnvironmentPalette;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

/** Additive world-space spellwork, rendered with depth testing and no per-frame entity allocation. */
public final class StaffEnvironmentGeometry {
    private StaffEnvironmentGeometry() {}
    public static void charge(PoseStack poses,MultiBufferSource buffers,float age,int duration,EnvironmentPalette theme,float yaw) {
        float p=Mth.clamp(age/Math.max(1,duration),0,1),t=age/20;
        float open=smooth(p/.24f),ground=smooth((p-.12f)/.24f),gather=smooth((p-.64f)/.28f);
        poses.pushPose();poses.mulPose(Axis.YP.rotationDegrees(-yaw));
        // Origin is feet + 2.2. The control seal sits ahead of the free hand.
        poses.pushPose();poses.translate(.62,-.65,1.65);poses.mulPose(Axis.ZP.rotationDegrees(-8+6*gather));
        float r=1.05f*open*(1-.12f*gather);
        StaffArcaneMaterial.seal(poses,buffers,r,t*14,color(theme,open*.9f),true);
        VertexConsumer ink=OutsiderShaderCompat.glow(buffers,OutsiderRitualRenderer.GLOW);
        ring(ink,poses.last().pose(),r*.69f,.009f,0,color(theme,open*.85f));
        ring(ink,poses.last().pose(),r*.65f,.004f,0,color(theme,open*.5f));
        glyph(ink,poses.last().pose(),theme,r*.52f,t*.2f,color(theme,open*.65f));poses.popPose();
        poses.pushPose();poses.translate(0,-2.12,0);poses.mulPose(Axis.XP.rotationDegrees(90));
        StaffArcaneMaterial.seal(poses,buffers,2.85f*ground,-t*8,color(theme,ground*.68f),false);
        ink=OutsiderShaderCompat.glow(buffers,OutsiderRitualRenderer.GLOW);
        ring(ink,poses.last().pose(),1.96f*ground,.012f,0,color(theme,ground*.6f));
        for(int k=0;k<6;k++) {
            float on=smooth((p-.18f-k*.037f)/.12f),a=k*Mth.TWO_PI/6+.25f;
            float x=Mth.cos(a)*3.2f,z=Mth.sin(a)*3.2f;
            poses.pushPose();poses.translate(x,z,.01);
            StaffArcaneMaterial.seal(poses,buffers,.43f*on,t*12+k*60,color(theme,on*.8f),true);poses.popPose();
            ink=OutsiderShaderCompat.glow(buffers,OutsiderRitualRenderer.GLOW);
            line(ink,poses.last().pose(),x*.86f,z*.86f,x*.98f,z*.98f,.015f,color(theme,on*.65f));
        }
        poses.popPose();
        // Sampled continuous ribbons: no per-frame random positions or jumping beams.
        ink=OutsiderShaderCompat.glow(buffers,OutsiderRitualRenderer.GLOW);
        for(int k=0;k<6;k++) {
            float a=k*Mth.TWO_PI/6+.25f,on=smooth((p-.27f-k*.025f)/.15f);
            for(int j=0;j<28;j++) {
                float u=j/28f,v=(j+1)/28f,tail=(t*.48f+k*.17f)%1;
                float weight=(float)Math.pow(Math.max(0,1-Math.abs(u-tail)*4),2);
                ribbon(ink,poses.last().pose(),stream(a,u,t,k),stream(a,v,t,k),.009f+.018f*weight,
                        color(theme,on*(.06f+.48f*weight)*(1-.35f*gather)));
            }
        }
        poses.popPose();
    }
    private static float[] stream(float a,float u,float t,int k) {
        float x=Mth.cos(a)*3.2f,z=Mth.sin(a)*3.2f,arc=Mth.sin(u*Mth.PI);
        return new float[]{Mth.lerp(u,x,.62f)+arc*.22f*Mth.sin(u*7-t*2+k),
                -2.07f+1.42f*u+arc*(.7f+.12f*Mth.sin(k)),Mth.lerp(u,z,1.65f)+arc*.2f*Mth.cos(t+k)};
    }
    private static void ribbon(VertexConsumer c,Matrix4f m,float[] a,float[] b,float w,float[] color) {
        EffectGeometry.glowQuad(c,m,new float[]{a[0]-w,a[1],a[2]},new float[]{a[0]+w,a[1],a[2]},new float[]{b[0]+w,b[1],b[2]},new float[]{b[0]-w,b[1],b[2]},color,color);
        EffectGeometry.glowQuad(c,m,new float[]{a[0],a[1],a[2]-w},new float[]{a[0],a[1],a[2]+w},new float[]{b[0],b[1],b[2]+w},new float[]{b[0],b[1],b[2]-w},color,color);
    }
    public static void wave(PoseStack poses,MultiBufferSource buffers,float age,int duration,EnvironmentPalette theme,float radius,boolean front) {
        float life=Mth.clamp(age/Math.max(1,duration),0,1);
        float fade=front?(1-life)*.55f:(1-smooth(age/48));
        float r=front?radius:Math.min(radius,26)*(1-(float)Math.pow(1-Math.min(1,age/32),3));
        poses.pushPose();poses.translate(0,.12,0);poses.mulPose(Axis.XP.rotationDegrees(90));
        VertexConsumer ink=OutsiderShaderCompat.glow(buffers,OutsiderRitualRenderer.GLOW);Matrix4f m=poses.last().pose();
        ring(ink,m,r,front?.10f:.18f,0,color(theme,fade*.8f));
        ring(ink,m,Math.max(.1f,r-.7f),.035f,0,color(theme,fade*.45f));
        if(!front)StaffArcaneMaterial.seal(poses,buffers,3.15f+smooth(age/15)*.4f,-age*.5f,color(theme,(1-smooth(age/32))*.7f),true);
        ink=OutsiderShaderCompat.glow(buffers,OutsiderRitualRenderer.GLOW);
        for(int i=0;i<24;i++) {
            double a=i*Math.PI/12;
            diamond(ink,m,(float)Math.cos(a)*r,(float)Math.sin(a)*r,front?.22f:.4f,a,color(theme,fade));
        }
        poses.popPose();
    }
    private static void glyph(VertexConsumer c,Matrix4f m,EnvironmentPalette theme,float radius,float time,float[] color) {
        if(theme==EnvironmentPalette.OCEAN||theme==EnvironmentPalette.FROZEN_OCEAN) {
            for(int band=0;band<3;band++)for(int i=0;i<64;i++) {
                float a=i*(float)Math.PI/32,b=(i+1)*(float)Math.PI/32;
                float ra=radius*(.45f+band*.22f)+.07f*Mth.sin(a*5+time*3),rb=radius*(.45f+band*.22f)+.07f*Mth.sin(b*5+time*3);
                line(c,m,Mth.cos(a)*ra,Mth.sin(a)*ra,Mth.cos(b)*rb,Mth.sin(b)*rb,.012f,color);
            }
        } else if(theme==EnvironmentPalette.SNOW) {
            for(int i=0;i<6;i++) {
                double a=i*Math.PI/3+time*.15;
                float x=(float)Math.cos(a),y=(float)Math.sin(a);
                line(c,m,0,0,x*radius,y*radius,.009f,color);
                for(int side:new int[]{-1,1})line(c,m,x*radius*.62f,y*radius*.62f,
                        x*radius*.40f-y*side*radius*.23f,y*radius*.40f+x*side*radius*.23f,.007f,color);
            }
        } else if(theme.infernal()||theme==EnvironmentPalette.END||theme==EnvironmentPalette.DEEP_DARK) {
            int n=theme.infernal()?3:4;
            for(int i=0;i<n;i++) {
                double a=i*Math.PI*2/n+time*.3,b=(i+1)*Math.PI*2/n+time*.3;
                line(c,m,(float)Math.cos(a)*radius,(float)Math.sin(a)*radius,(float)Math.cos(b)*radius,(float)Math.sin(b)*radius,.010f,color);
                diamond(c,m,(float)Math.cos(a)*radius,(float)Math.sin(a)*radius,radius*.13f,a,color);
            }
            ring(c,m,radius*.4f,.022f,0,color);
        } else if(theme==EnvironmentPalette.DESERT||theme==EnvironmentPalette.BADLANDS||theme==EnvironmentPalette.ROCK) {
            ring(c,m,radius*.5f,.010f,0,color);
            for(int i=0;i<8;i++) {
                double a=i*Math.PI/4+time*.15;
                line(c,m,(float)Math.cos(a)*radius*.65f,(float)Math.sin(a)*radius*.65f,(float)Math.cos(a)*radius,(float)Math.sin(a)*radius,.010f,color);
            }
        } else {
            // Six leaf-shaped loops, or pale pink petals for cherry groves.
            for(int petal=0;petal<6;petal++)for(int i=0;i<20;i++) {
                double a=petal*Math.PI/3+time*.15,t=i*Math.PI/10,u=(i+1)*Math.PI/10;
                float x=(float)(radius*(.5+.5*Math.cos(t))),y=(float)(radius*.18*Math.sin(t));
                float X=(float)(radius*(.5+.5*Math.cos(u))),Y=(float)(radius*.18*Math.sin(u));
                line(c,m,(float)(x*Math.cos(a)-y*Math.sin(a)),(float)(x*Math.sin(a)+y*Math.cos(a)),
                        (float)(X*Math.cos(a)-Y*Math.sin(a)),(float)(X*Math.sin(a)+Y*Math.cos(a)),.008f,color);
            }
        }
    }
    private static float smooth(float v) {v=Mth.clamp(v,0,1);return v*v*(3-2*v);}
    private static float[] color(EnvironmentPalette theme,float alpha) {
        return new float[]{.22f+.78f*((theme.color>>16)&255)/255f,.22f+.78f*((theme.color>>8)&255)/255f,.22f+.78f*(theme.color&255)/255f,alpha};
    }
    private static void ring(VertexConsumer c,Matrix4f m,float r,float w,float angle,float[] color) {
        for(int i=0;i<96;i++) {
            double a=i*Math.PI/48+angle,b=(i+1)*Math.PI/48+angle;
            EffectGeometry.glowQuad(c,m,point(r-w,a),point(r+w,a),point(r+w,b),point(r-w,b),color,color);
        }
    }
    private static float[] point(float r,double a) {return new float[]{(float)Math.cos(a)*r,(float)Math.sin(a)*r,0};}
    private static void diamond(VertexConsumer c,Matrix4f m,float x,float y,float r,double angle,float[] color) {
        float[][] p=new float[4][];
        for(int i=0;i<4;i++) {p[i]=point(r,angle+i*Math.PI/2);p[i][0]+=x;p[i][1]+=y;}
        for(int i=0;i<4;i++)line(c,m,p[i][0],p[i][1],p[(i+1)%4][0],p[(i+1)%4][1],.008f,color);
    }
    private static void line(VertexConsumer c,Matrix4f m,float x,float y,float X,float Y,float width,float[] color) {
        float length=Math.max(.001f,Mth.sqrt((X-x)*(X-x)+(Y-y)*(Y-y))),dx=-(Y-y)*width/length,dy=(X-x)*width/length;
        EffectGeometry.glowQuad(c,m,new float[]{x+dx,y+dy,0},new float[]{x-dx,y-dy,0},new float[]{X-dx,Y-dy,0},new float[]{X+dx,Y+dy,0},color,color);
    }
}
