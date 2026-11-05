package com.sourceofmystery.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.*;
import java.util.*;

/** Collect by material without retaining live consumers across texture switches. */
final class DivineCGMesh implements MultiBufferSource {
    private record V(double x,double y,double z,int r,int g,int b,int a,float u,float v,int ou,int ov,int lu,int lv,float nx,float ny,float nz){}
    private final Map<RenderType,Collector> materials=new LinkedHashMap<>();
    @Override public VertexConsumer getBuffer(RenderType type){return materials.computeIfAbsent(type,t->new Collector());}
    void draw(MultiBufferSource.BufferSource target){
        for(var entry:materials.entrySet()){
            if(entry.getValue().vertices.isEmpty())continue;
            var out=target.getBuffer(entry.getKey());
            for(var v:entry.getValue().vertices)out.vertex(v.x,v.y,v.z).color(v.r,v.g,v.b,v.a).uv(v.u,v.v).overlayCoords(v.ou,v.ov).uv2(v.lu,v.lv).normal(v.nx,v.ny,v.nz).endVertex();
            target.endBatch();entry.getValue().vertices.clear();
        }
    }
    private static final class Collector implements VertexConsumer {
        final List<V> vertices=new ArrayList<>();double x,y,z;int r=255,g=255,b=255,a=255,ou,ov,lu,lv;float u,v,nx,ny,nz;
        public VertexConsumer vertex(double x,double y,double z){this.x=x;this.y=y;this.z=z;return this;}
        public VertexConsumer color(int r,int g,int b,int a){this.r=r;this.g=g;this.b=b;this.a=a;return this;}
        public VertexConsumer uv(float u,float v){this.u=u;this.v=v;return this;}
        public VertexConsumer overlayCoords(int u,int v){ou=u;ov=v;return this;}
        public VertexConsumer uv2(int u,int v){lu=u;lv=v;return this;}
        public VertexConsumer normal(float x,float y,float z){nx=x;ny=y;nz=z;return this;}
        public void endVertex(){vertices.add(new V(x,y,z,r,g,b,a,u,v,ou,ov,lu,lv,nx,ny,nz));}
        public void defaultColor(int r,int g,int b,int a){color(r,g,b,a);}
        public void unsetDefaultColor(){}
    }
}
