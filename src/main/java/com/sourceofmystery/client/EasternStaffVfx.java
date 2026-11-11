package com.sourceofmystery.client;

import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Axis;
import com.sourceofmystery.staff.EasternRite;
import net.minecraft.client.renderer.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

/** Small, layered energy shapes in the animated crystal's local coordinates. */
public final class EasternStaffVfx {
    private static final ResourceLocation WHITE=new ResourceLocation("sourceofmystery","textures/entity/outsider/white.png");
    private static final ResourceLocation AURA=new ResourceLocation("sourceofmystery","textures/effect/eastern/aura.png");
    private EasternStaffVfx(){}
    private static float ease(float x){x=Mth.clamp(x,0,1);return x*x*(3-2*x);}
    public static void render(PoseStack p,MultiBufferSource buffers,float charge,int duration,float ritual){
        boolean channel=ritual<0;
        float age=channel?charge:ritual+duration;
        float power=channel?ease(charge/Math.max(1,duration)):.35f+.65f*ease((ritual-EasternRite.ORB_CHARGE_START)/(float)(EasternRite.LAUNCH-EasternRite.ORB_CHARGE_START));
        float fade=channel?ease(charge/8):1-ease((ritual-EasternRite.LAUNCH)/20);
        if(fade<=0)return;
        float burst=channel?0:ease((ritual-EasternRite.LAUNCH)/2)*(1-ease((ritual-EasternRite.LAUNCH-2)/14));
        // A white core remains inside the purple crystal; the halo never covers the screen.
        for(int plane=0;plane<3;plane++){
            p.pushPose();p.mulPose(Axis.YP.rotationDegrees(plane*60));
            quad(OutsiderShaderCompat.complete(buffers.getBuffer(EasternMaterials.material(AURA,true,false))),
                    p.last().pose(),.28f+.15f*power+.24f*burst,.28f+.15f*power+.24f*burst,fade*(.23f+.13f*power));
            p.popPose();
        }
        for(int layer=0;layer<2;layer++){
            p.pushPose();p.mulPose(Axis.YP.rotationDegrees(30+layer*90));p.mulPose(Axis.XP.rotationDegrees(25+layer*34));
            EasternGeometry.seal(p,buffers,.46f+layer*.12f+power*.12f+burst*.22f,age*(layer==0?1.1f:-.76f),fade*(.35f+.25f*power));
            p.popPose();
        }
        var ink=OutsiderShaderCompat.complete(buffers.getBuffer(EasternMaterials.material(WHITE,true,false)));
        // Inward spiral filaments wind from the shaft to the head; at launch they open outward.
        for(int strand=0;strand<5;strand++)for(int segment=0;segment<32;segment++){
            float t=segment/32f,T=(segment+1)/32f;
            float[] a=point(t,strand,age,power,burst),b=point(T,strand,age,power,burst);
            float alpha=fade*(.24f+.5f*power)*Mth.sin(t*Mth.PI);
            ribbon(ink,p.last().pose(),a,b,.009f+.008f*power,alpha);
        }
        for(int i=0;i<20;i++){
            float progress=(age*.018f+i*.618034f)%1;
            float angle=age*.034f+i*2.399963f;
            float r=(1-progress)*(.62f+.25f*power)+.09f;
            p.pushPose();p.translate(Mth.cos(angle)*r,-1.1f*(1-progress),Mth.sin(angle)*r);
            p.mulPose(Axis.YP.rotationDegrees(-age*2-i*51));
            quad(ink,p.last().pose(),.012f+.018f*progress,.035f+.07f*progress,
                    fade*Mth.sin(progress*Mth.PI)*(.4f+.4f*power));p.popPose();
        }
        if(burst>.01f){
            for(int plane=0;plane<2;plane++){
                p.pushPose();p.mulPose(Axis.YP.rotationDegrees(plane*90));
                quad(ink,p.last().pose(),(.7f+.55f*burst),.012f,burst*fade*.8f);p.popPose();
            }
        }
    }
    private static float[] point(float t,int strand,float age,float power,float burst){
        float angle=strand*Mth.TWO_PI/5+age*.065f+t*Mth.TWO_PI*1.35f;
        float radius=(.18f+.33f*(1-t))*(.6f+.4f*power)+burst*.4f*t;
        return new float[]{Mth.cos(angle)*radius,-1.2f+1.45f*t,Mth.sin(angle)*radius};
    }
    private static void ribbon(VertexConsumer v,Matrix4f m,float[] a,float[] b,float width,float alpha){
        for(int axis=0;axis<2;axis++)for(int corner=0;corner<4;corner++){
            float[] point=corner<2?a:b;float edge=(corner==0||corner==3?-1:1)*width;
            v.vertex(m,point[0]+(axis==0?edge:0),point[1],point[2]+(axis==1?edge:0))
                    .color(.86f,.93f,1,alpha).uv(corner==0||corner==3?0:1,corner<2?0:1).endVertex();
        }
    }
    private static void quad(VertexConsumer v,Matrix4f m,float x,float y,float alpha){
        v.vertex(m,-x,-y,0).color(1f,1f,1f,alpha).uv(0,1).endVertex();
        v.vertex(m,x,-y,0).color(1f,1f,1f,alpha).uv(1,1).endVertex();
        v.vertex(m,x,y,0).color(1f,1f,1f,alpha).uv(1,0).endVertex();
        v.vertex(m,-x,y,0).color(1f,1f,1f,alpha).uv(0,0).endVertex();
    }
}
