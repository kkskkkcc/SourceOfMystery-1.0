package com.sourceofmystery.client;

import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Axis;
import com.sourceofmystery.staff.EasternRite;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import java.util.Random;

/** The gate has no mesh: every visible stroke is a pre-distributed luminous particle. */
public final class EasternGeometry {
    private static final ResourceLocation PARTICLE=tex("particle"),AURA=tex("aura"),SIGIL=tex("white_sigil");
    private static final ResourceLocation WHITE=new ResourceLocation("sourceofmystery","textures/entity/outsider/white.png");
    private static final int COUNT=32000;
    private static final float CORE_RADIUS=(float)EasternRite.RADIUS,SHELL_SCALE=1.16f;
    private static final float[] CLOUD=new float[COUNT*4];
    static {
        Random random=new Random(0x1e457);
        // Ratios follow the supplied screenshot: plain broad lintels and thick
        // square pillars. No rope, gold edging, plaque or pagoda-shaped roof.
        float[][] bars={{-25.35f,0,-2.5f,-20.15f,61,2.5f},{20.15f,0,-2.5f,25.35f,61,2.5f},{-36,60,-2.9f,36,64,2.9f},{-32,48.4f,-2.55f,32,52.4f,2.55f}};
        float[] volume=new float[bars.length];float sum=0;for(int i=0;i<bars.length;i++){var b=bars[i];sum+=volume[i]=(b[3]-b[0])*(b[4]-b[1])*(b[5]-b[2]);}
        for(int i=0;i<COUNT;i++){
            float choice=random.nextFloat()*sum;int index=0;while(index<volume.length-1&&(choice-=volume[index])>0)index++;var b=bars[index];int n=i*4;
            CLOUD[n]=Mth.lerp(random.nextFloat(),b[0],b[3]);CLOUD[n+1]=Mth.lerp(random.nextFloat(),b[1],b[4]);CLOUD[n+2]=Mth.lerp(random.nextFloat(),b[2],b[5]);CLOUD[n+3]=random.nextFloat();
        }
    }
    private EasternGeometry(){}
    private static ResourceLocation tex(String name){return new ResourceLocation("sourceofmystery","textures/effect/eastern/"+name+".png");}
    public static float ease(float value){float p=Mth.clamp(value,0,1);return p*p*(3-2*p);}
    private static VertexConsumer ink(MultiBufferSource buffers,ResourceLocation texture){return OutsiderShaderCompat.complete(buffers.getBuffer(texture.equals(AURA)?RenderType.entityTranslucentEmissive(texture):EasternMaterials.material(texture,true,false)));}
    private static void sprite(VertexConsumer v,PoseStack p,float x,float y,float z,float size,Vector3f right,Vector3f up,float red,float green,float blue,float alpha){
        float rx=right.x*size,ry=right.y*size,rz=right.z*size,ux=up.x*size,uy=up.y*size,uz=up.z*size;var m=p.last().pose();
        v.vertex(m,x-rx-ux,y-ry-uy,z-rz-uz).color(red,green,blue,alpha).uv(0,1).endVertex();
        v.vertex(m,x+rx-ux,y+ry-uy,z+rz-uz).color(red,green,blue,alpha).uv(1,1).endVertex();
        v.vertex(m,x+rx+ux,y+ry+uy,z+rz+uz).color(red,green,blue,alpha).uv(1,0).endVertex();
        v.vertex(m,x-rx+ux,y-ry+uy,z-rz+uz).color(red,green,blue,alpha).uv(0,0).endVertex();
    }
    private static Vector3f local(Vector3f world,float yaw,float pitch){return new Vector3f(world).rotateY(yaw*Mth.DEG_TO_RAD).rotateX(-pitch*Mth.DEG_TO_RAD);}
    public static float gateRise(float age,int index){return ease((age-EasternRite.GATE_START-index*EasternRite.GATE_INTERVAL)/EasternRite.GATE_RISE);}
    public static void render(PoseStack p,MultiBufferSource buffers,EasternVisuals.View view,float partial,Vec3 camera){
        var d=view.data();float age=view.age(partial),fade=ease((view.end()-age)/20),yaw=d.yaw();
        var rotation=Minecraft.getInstance().gameRenderer.getMainCamera().rotation();var right=local(new Vector3f(1,0,0).rotate(rotation),yaw,d.pitch());var up=local(new Vector3f(0,1,0).rotate(rotation),yaw,d.pitch());
        p.pushPose();p.mulPose(Axis.YP.rotationDegrees(-yaw));p.mulPose(Axis.XP.rotationDegrees(d.pitch()));
        var v=ink(buffers,PARTICLE);
        for(int gate=0;gate<EasternRite.GATES;gate++){
            float rise=gateRise(age,gate);if(rise<=0)continue;
            float z=(float)EasternRite.gateDistance(gate),base=(float)(d.bases()[gate]-d.origin().y),lift=-64*(1-rise);
            double distance=camera.distanceTo(d.origin().add(view.forward().scale(z)).add(0,base+25,0));int count=distance<110?32000:distance<170?18000:distance<250?10000:6000;
            float size=.26f*(float)Math.sqrt(COUNT/(double)count);
            for(int i=0;i<count;i++){
                int k=i*4;float grain=CLOUD[k+3],brightness=.85f+.15f*Mth.sin(grain*47+age*.045f);
                sprite(v,p,CLOUD[k],CLOUD[k+1]+base+lift,CLOUD[k+2]+z,size*(.78f+grain*.38f),right,up,brightness,.43f*brightness,.105f*brightness,.88f*fade);
            }
            // Embers leave the fixed outline; the completed gate itself never follows
            // the player, changes shape, or moves up and down.
            for(int i=0;i<160;i++){
                int k=(i*37%COUNT)*4;float life=(age*.012f+CLOUD[k+3])%1;
                sprite(v,p,CLOUD[k]+Mth.sin(i*1.7f)*life*2,CLOUD[k+1]+base+lift+life*4,CLOUD[k+2]+z+Mth.cos(i)*life*2,.07f+.1f*life,right,up,1,.54f,.16f,(1-life)*.75f*fade);
            }
        }
        if(age>=EasternRite.ORB_FORM_START&&age<EasternRite.LAUNCH){float charge=ease((age-EasternRite.ORB_FORM_START)/(float)(EasternRite.LAUNCH-EasternRite.ORB_FORM_START));orb(p,buffers,16,8,CORE_RADIUS*charge,age,right,up,fade);}
        if(age>=EasternRite.LAUNCH&&d.stoppedAt()<0&&view.travel(partial)<d.range()){orb(p,buffers,(float)(16+view.travel(partial)),8,CORE_RADIUS,age,right,up,fade);}
        if(age>=EasternRite.LAUNCH&&view.travel(partial)>0){float linger=d.stoppedAt()<0?1:ease((d.stoppedAt()+12-age)/12);if(linger>0)ribbons(p,buffers,view.travel(partial),age,fade*linger);}
        // The screenshot's white drifting flecks are part of the spell, not weather.
        v=ink(buffers,PARTICLE);
        for(int i=0;i<160;i++){float seed=(i*.6180339f)%1,x=Mth.sin(i*72.135f)*39,y=((i*3.37f-age*.06f)%36+36)%36,z=(i*7.93f)%240;sprite(v,p,x,y,z,.04f+.055f*seed,right,up,1,.94f,.83f,.65f*fade);}
        p.popPose();
    }
    public static void seal(PoseStack p,MultiBufferSource buffers,float radius,float degrees,float alpha){
        p.pushPose();p.mulPose(Axis.ZP.rotationDegrees(degrees));var v=ink(buffers,SIGIL);var m=p.last().pose();
        v.vertex(m,-radius,-radius,0).color(1f,1f,1f,alpha).uv(0,1).endVertex();v.vertex(m,radius,-radius,0).color(1f,1f,1f,alpha).uv(1,1).endVertex();v.vertex(m,radius,radius,0).color(1f,1f,1f,alpha).uv(1,0).endVertex();v.vertex(m,-radius,radius,0).color(1f,1f,1f,alpha).uv(0,0).endVertex();p.popPose();
    }
    private static void orb(PoseStack p,MultiBufferSource b,float along,float height,float radius,float age,Vector3f right,Vector3f up,float fade){
        if(radius<.02)return;p.pushPose();p.translate(0,height,along);
        sphere(p,b,radius,false,1);sphere(p,b,radius*SHELL_SCALE,true,.14f*fade);
        var aura=ink(b,AURA);sprite(aura,p,0,0,0,radius*SHELL_SCALE*1.45f,right,up,1,1,1,.35f*fade);
        p.popPose();
    }
    private static void sphere(PoseStack p,MultiBufferSource b,float r,boolean shell,float alpha){
        // The inner body is fully opaque and writes depth. The larger, textureless
        // shell blends white without depth writes, so the inner sphere stays solid.
        var v=OutsiderShaderCompat.complete(b.getBuffer(shell?RenderType.entityTranslucentEmissive(WHITE):EasternMaterials.material(WHITE,true,true)));var m=p.last().pose();int rings=32,slices=64;
        for(int y=0;y<rings;y++)for(int x=0;x<slices;x++)for(int corner=0;corner<4;corner++){
            float u=(x+(corner==1||corner==2?1:0))/(float)slices,t=(y+(corner>=2?1:0))/(float)rings;
            float latitude=t*Mth.PI,longitude=u*Mth.TWO_PI,xx=Mth.sin(latitude)*Mth.cos(longitude),yy=Mth.cos(latitude),zz=Mth.sin(latitude)*Mth.sin(longitude);
            v.vertex(m,xx*r,yy*r,zz*r).color(1f,1f,1f,shell?alpha:1).uv(u,t).endVertex();
        }
    }
    /** Continuous gray silk bands, with rotating helices and a vanishing tail. */
    private static void ribbons(PoseStack p,MultiBufferSource b,double travel,float age,float fade){
        var v=OutsiderShaderCompat.complete(b.getBuffer(EasternMaterials.material(WHITE,false,false)));var m=p.last().pose();
        float length=(float)Math.min(46,travel+1),head=(float)(16+travel),turn=age*.085f;int steps=96;
        for(int strand=0;strand<6;strand++)for(int i=0;i<steps;i++)for(int corner=0;corner<4;corner++){
            float t=(i+(corner>=2?1:0))/(float)steps,angle=turn-t*Mth.TWO_PI*2.1f+strand*Mth.TWO_PI/3;
            float radius=CORE_RADIUS*1.115f*(1-.32f*t)+(strand>=3?.45f:0),width=(strand<3?.58f:.12f)*(float)Math.pow(1-t,.75);
            float edge=corner==1||corner==2?1:-1,r=radius+edge*width;
            float alpha=fade*(strand<3?.62f:.4f)*(float)Math.pow(1-t,1.5)*(.8f+.2f*Mth.sin(age*.13f-t*13));
            float gray=.5f-.22f*t;
            v.vertex(m,Mth.cos(angle)*r,8+Mth.sin(angle)*r,head-2-length*t).color(gray,gray,gray,alpha).uv(corner==1||corner==2?1:0,t).endVertex();
        }
    }
}
