package com.sourceofmystery.client;

import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Axis;
import com.sourceofmystery.entity.OutsiderEntity;
import com.sourceofmystery.entity.OutsiderRitual;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.joml.Matrix4f;

/** 48 deterministic world-space ribbons. No particle entities, chunk loads or packets. */
final class OutsiderChargeWind {
    private static final int STREAMS=48,SEGMENTS=10;
    private OutsiderChargeWind(){}
    static void render(OutsiderEntity owner,float partial,float seconds,Vector3f core,PoseStack poses,MultiBufferSource buffers){
        float strength=OutsiderRitual.chargeEnvelope(seconds);if(strength<.001f)return;
        Vec3 socket=owner.modelToWorld(new Vec3(core.x,core.y,core.z).scale(OutsiderEntity.MODEL_SCALE));
        Vec3 camera=Minecraft.getInstance().gameRenderer.getMainCamera().getPosition().subtract(socket);
        poses.pushPose();poses.mulPose(Axis.YP.rotationDegrees(owner.visualBodyYaw(partial)-180));
        draw(owner.getUUID().getLeastSignificantBits(),seconds,strength,camera,poses.last().pose(),buffers);
        poses.popPose();
    }
    static void draw(long seed,float seconds,float strength,Vec3 camera,Matrix4f matrix,MultiBufferSource buffers){
        if(strength<.001f)return;
        VertexConsumer vc=buffers.getBuffer(RenderType.lightning());
        for(int lane=0;lane<STREAMS;lane++){
            float duration=3.3f+EffectGeometry.hash(seed,lane*5)*2.7f;
            double phase=seconds/duration+lane*.61803398875;
            int cycle=(int)Math.floor(phase);float head=(float)(phase-cycle);
            long pathSeed=seed+lane*7919L+cycle*104729L;
            float opacity=strength*(float)Math.pow(Math.sin(head*Math.PI),.6)*(.50f+.35f*strength);
            float[] next=point(pathSeed,head),previous;
            for(int segment=0;segment<SEGMENTS;segment++){
                float u=Math.max(0,head-(segment+1)*.025f);previous=point(pathSeed,u);
                float dx=next[0]-previous[0],dy=next[1]-previous[1],dz=next[2]-previous[2];
                float vx=(float)camera.x-next[0],vy=(float)camera.y-next[1],vz=(float)camera.z-next[2];
                float sx=dy*vz-dz*vy,sy=dz*vx-dx*vz,sz=dx*vy-dy*vx;
                float length=(float)Math.sqrt(sx*sx+sy*sy+sz*sz);if(length<1e-5f){sx=1;sy=sz=0;length=1;}
                float taper=1-segment/(float)SEGMENTS,width=(.12f+.18f*strength)*taper/length;
                sx*=width;sy*=width;sz*=width;
                float alpha=opacity*taper*taper;
                float[] front={.47f,.10f,.82f,alpha},tail={.22f,.025f,.46f,alpha*.65f};
                EffectGeometry.glowQuad(vc,matrix,offset(next,sx,sy,sz),offset(next,-sx,-sy,-sz),
                        offset(previous,-sx,-sy,-sz),offset(previous,sx,sy,sz),front,tail);
                next=previous;if(u==0)break;
            }
        }
    }
    static float[] point(long seed,float u){
        double angle=EffectGeometry.hash(seed,0)*Math.PI*2;
        double radius=36+EffectGeometry.hash(seed,1)*112;
        double height=-12+EffectGeometry.hash(seed,2)*46;
        double x=Math.cos(angle)*radius,z=Math.sin(angle)*radius;
        double tx=-Math.sin(angle)*radius*.36,tz=Math.cos(angle)*radius*.36;
        double v=1-u;
        return new float[]{(float)(v*v*v*x+3*v*v*u*(x*.64+tx)+3*v*u*u*Math.cos(angle+1.3)*4),
                (float)(v*v*v*height+3*v*v*u*(height*.6+10)+3*v*u*u*3.5),
                (float)(v*v*v*z+3*v*v*u*(z*.64+tz)+3*v*u*u*Math.sin(angle+1.3)*4)};
    }
    private static float[] offset(float[] p,float x,float y,float z){return new float[]{p[0]+x,p[1]+y,p[2]+z};}
}
