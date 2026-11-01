package com.sourceofmystery.client;

import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.*;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

/** World-space meshes, without sword or portal entities. */
public final class StaffMagicGeometry {
    private StaffMagicGeometry() {}
    public static void ritual(PoseStack poses,MultiBufferSource buffers,float seconds,float height) {
        OutsiderRitualRenderer.render(poses,buffers,(seconds-3)*20,height);
    }
    public static void earthSeal(PoseStack poses,MultiBufferSource buffers,float seconds) {
        var consumer=buffers.getBuffer(OutsiderRitualRenderer.GLOW);Matrix4f matrix=poses.last().pose();
        float radius=.7f+.2f*Mth.sin(seconds*3);float[] color={.7f,.52f,.27f,.8f};
        ring(consumer,matrix,radius,.035f,seconds,color);
        ring(consumer,matrix,radius*.72f,.018f,-seconds,color);
    }
    private static void ring(VertexConsumer consumer,Matrix4f m,float r,float thickness,float angle,float[] color) {
        for(int i=0;i<64;i++) {
            double a=i*Math.PI/32+angle,b=(i+1)*Math.PI/32+angle;
            EffectGeometry.glowQuad(consumer,m,point(r-thickness,a),point(r+thickness,a),point(r+thickness,b),point(r-thickness,b),color,color);
        }
    }
    private static float[] point(float r,double a){return new float[]{(float)Math.cos(a)*r,(float)Math.sin(a)*r,0};}
    public static void portal(PoseStack poses,MultiBufferSource buffers,float age,float duration,int color,float width,float height,boolean sky) {
        float opening=Mth.clamp(age/12,0,1)*Mth.clamp((duration-age)/12,0,1);
        float[] tint=switch(color){case 1->new float[]{1,.22f,.18f,.8f};case 2->new float[]{.88f,.95f,1,.85f};default->new float[]{.25f,.6f,1,.85f};};
        poses.pushPose();if(sky)poses.mulPose(Axis.XP.rotationDegrees(90));
        Matrix4f matrix=poses.last().pose();VertexConsumer ink=buffers.getBuffer(OutsiderRitualRenderer.SOLID);
        float[] dark={0.005f,.008f,.018f,1};
        for(int i=0;i<64;i++) {
            float[] a=edge(i,width,height,opening),b=edge(i+1,width,height,opening);
            EffectGeometry.glowQuad(ink,matrix,new float[]{0,0,0},a,b,new float[]{0,0,0},dark,dark);
        }
        VertexConsumer rim=buffers.getBuffer(OutsiderRitualRenderer.GLOW);
        for(int i=0;i<64;i++) {
            float[] a=edge(i,width,height,opening),b=edge(i+1,width,height,opening);
            float[] aa={a[0]*1.035f,a[1]*1.035f,.02f},bb={b[0]*1.035f,b[1]*1.035f,.02f};
            EffectGeometry.glowQuad(rim,matrix,a,aa,bb,b,tint,tint);
        }
        poses.popPose();
    }
    private static float[] edge(int i,float width,float height,float opening) {
        double angle=i*Math.PI/32;float jitter=.87f+EffectGeometry.hash(8197,i%64)*.09f;
        return new float[]{(float)Math.cos(angle)*width*.5f*jitter*opening,(float)Math.sin(angle)*height*.5f*jitter*opening,0};
    }
    public static void blackSword(PoseStack poses,MultiBufferSource buffers,float age) {
        float descent=(float)com.sourceofmystery.entity.OutsiderRitual.smooth((age-40)/100f);
        poses.pushPose();poses.translate(0,130*(1-descent),0);
        VertexConsumer ink=buffers.getBuffer(OutsiderRitualRenderer.SOLID);
        box(ink,poses.last().pose(),-4,8,-1.1f,4,75,1.1f);
        box(ink,poses.last().pose(),-13,75,-2,13,79,2);
        box(ink,poses.last().pose(),-1.7f,79,-1.7f,1.7f,95,1.7f);
        float[] black={0,0,0,1};Matrix4f m=poses.last().pose();
        EffectGeometry.glowQuad(ink,m,new float[]{0,0,0},new float[]{-4,8,-1.1f},new float[]{4,8,-1.1f},new float[]{0,0,0},black,black);
        EffectGeometry.glowQuad(ink,m,new float[]{0,0,0},new float[]{4,8,1.1f},new float[]{-4,8,1.1f},new float[]{0,0,0},black,black);
        poses.popPose();
    }
    private static void box(VertexConsumer c,Matrix4f m,float x,float y,float z,float X,float Y,float Z) {
        float[][] p={{x,y,z},{X,y,z},{X,Y,z},{x,Y,z},{x,y,Z},{X,y,Z},{X,Y,Z},{x,Y,Z}};
        float[] black={0,0,0,1};for(int[] face:new int[][]{{0,1,2,3},{4,7,6,5},{0,4,5,1},{3,2,6,7},{0,3,7,4},{1,5,6,2}})
            EffectGeometry.glowQuad(c,m,p[face[0]],p[face[1]],p[face[2]],p[face[3]],black,black);
    }
}
