package com.sourceofmystery.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.sourceofmystery.entity.OutsiderEntity;
import com.sourceofmystery.entity.OutsiderRitual;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LightningBoltRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/** Reuses the vanilla branching lightning mesh. Nothing is spawned or ticked. */
final class OutsiderSkyLightning {
    private final LightningBoltRenderer renderer;
    private LightningBolt visual;

    OutsiderSkyLightning(EntityRendererProvider.Context context) { renderer=new LightningBoltRenderer(context); }

    void render(OutsiderEntity owner,float partial,float seconds,Vector3f core,PoseStack poses,MultiBufferSource buffers){
        if(seconds>=OutsiderRitual.END)return;
        if(visual==null||visual.level()!=owner.level()){
            visual=new LightningBolt(EntityType.LIGHTNING_BOLT,owner.level());
            visual.setVisualOnly(true);
        }
        float yaw=owner.visualBodyYaw(partial);
        double angle=Math.toRadians(180-yaw),c=Math.cos(angle),s=Math.sin(angle);
        Vec3 worldCore=new Vec3(Mth.lerp(partial,owner.xOld,owner.getX())+(core.x*c+core.z*s)*OutsiderEntity.MODEL_SCALE,
                Mth.lerp(partial,owner.yOld,owner.getY())+core.y*OutsiderEntity.MODEL_SCALE,
                Mth.lerp(partial,owner.zOld,owner.getZ())+(core.z*c-core.x*s)*OutsiderEntity.MODEL_SCALE);
        float clock=seconds*20;
        // Three staggered strikes per 1.5s. Each seed/impact stays fixed through
        // its flash; the previous short random cracks are no longer rendered.
        for(int i=0;i<3;i++){
            float time=clock-i*10;
            if(time<0)continue;
            int sequence=(int)Math.floor(time/30);
            float age=time-sequence*30;
            if(age>=8)continue;
            long seed=owner.getUUID().getLeastSignificantBits()+sequence*971L+i*371L;
            double a=EffectGeometry.hash(seed,0)*Math.PI*2;
            double radius=12+EffectGeometry.hash(seed,1)*12;
            double dx=Math.cos(a)*radius,dz=Math.sin(a)*radius;
            BlockPos column=BlockPos.containing(worldCore.x+dx,owner.getY(),worldCore.z+dz);
            double ground=owner.level().hasChunkAt(column)
                    ?owner.level().getHeight(Heightmap.Types.MOTION_BLOCKING,column.getX(),column.getZ()):owner.getY()-10;
            double sky=Math.max(owner.level().getMaxBuildHeight()+32,worldCore.y+96);
            double bottom=OutsiderRitual.lightningBottom(sky,ground,age);
            double length=sky-bottom;
            if(length<.01)continue;
            visual.seed=seed;
            float alpha=(age<5?1:age<6?.3f:1)*Mth.clamp((8-age)/2,0,1);
            MultiBufferSource tinted=type->new Tint(buffers.getBuffer(type),alpha);
            poses.pushPose();
            poses.mulPose(Axis.YP.rotationDegrees(yaw-180));
            poses.translate(dx,bottom-worldCore.y,dz);
            // Vanilla mesh spans y=0..128. Keep its top fixed in the sky while
            // the leading end travels downward to the terrain in two ticks.
            poses.scale(.5f,(float)(length/128),.5f);
            renderer.render(visual,0,partial,poses,tinted,15728880);
            poses.popPose();
        }
    }

    private record Tint(VertexConsumer delegate,float alpha) implements VertexConsumer {
        @Override public VertexConsumer vertex(double x,double y,double z){delegate.vertex(x,y,z);return this;}
        @Override public VertexConsumer color(int r,int g,int b,int a){delegate.color(Math.min(255,(int)(r*1.35)),(int)(g*.85),Math.min(255,(int)(b*1.8)),(int)(a*alpha));return this;}
        @Override public VertexConsumer uv(float u,float v){delegate.uv(u,v);return this;}
        @Override public VertexConsumer overlayCoords(int u,int v){delegate.overlayCoords(u,v);return this;}
        @Override public VertexConsumer uv2(int u,int v){delegate.uv2(u,v);return this;}
        @Override public VertexConsumer normal(float x,float y,float z){delegate.normal(x,y,z);return this;}
        @Override public void endVertex(){delegate.endVertex();}
        @Override public void defaultColor(int r,int g,int b,int a){delegate.defaultColor(r,g,b,(int)(a*alpha));}
        @Override public void unsetDefaultColor(){delegate.unsetDefaultColor();}
    }
}
