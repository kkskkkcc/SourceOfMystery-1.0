package com.sourceofmystery.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;

/** Optional Embeddium bulk submission of the documented Oculus ENTITY layout.
 * Fills UV centers, tangent, normal and entity IDs before pushing complete quads.
 * Other versions and consumers retain the ordinary VertexConsumer path.
 */
final class OutsiderVertexBatch implements VertexConsumer {
    private static final int CAPACITY=65536;
    private static int STRIDE=54;
    private static Method tryWriter,push,entityId,blockId,itemId;
    private static Object format,captured;
    private static boolean available,reported;
    static{
        try{
            Class<?> writer=Class.forName("net.caffeinemc.mods.sodium.api.vertex.buffer.VertexBufferWriter");
            Class<?> description=Class.forName("net.caffeinemc.mods.sodium.api.vertex.format.VertexFormatDescription");
            Class<?> vertex=Class.forName("net.irisshaders.iris.compat.sodium.impl.vertex_format.entity_xhfp.EntityVertex");
            format=vertex.getField("FORMAT").get(null);
            STRIDE=vertex.getField("STRIDE").getInt(null);
            if(STRIDE<54||STRIDE>96)throw new ReflectiveOperationException("Unsupported ENTITY stride "+STRIDE);
            String[] offsets={"OFFSET_POSITION","OFFSET_COLOR","OFFSET_TEXTURE","OFFSET_LIGHT","OFFSET_OVERLAY","OFFSET_NORMAL","OFFSET_MID_TEXTURE","OFFSET_TANGENT"};
            int[] expected={0,12,16,28,24,32,42,50};
            for(int i=0;i<offsets.length;i++){
                var field=vertex.getDeclaredField(offsets[i]);field.setAccessible(true);
                if(field.getInt(null)!=expected[i])throw new ReflectiveOperationException("Unsupported ENTITY field "+offsets[i]);
            }
            tryWriter=writer.getMethod("tryOf",VertexConsumer.class);
            push=writer.getMethod("push",MemoryStack.class,long.class,int.class,description);
            Class<?> state=Class.forName("net.irisshaders.iris.uniforms.CapturedRenderingState");
            captured=state.getField("INSTANCE").get(null);
            entityId=state.getMethod("getCurrentRenderedEntity");blockId=state.getMethod("getCurrentRenderedBlockEntity");itemId=state.getMethod("getCurrentRenderedItem");
            available=true;
        }catch(ReflectiveOperationException|LinkageError error){
            available=false;
            com.sourceofmystery.SourceOfMystery.LOGGER.info("OUTSIDER_BULK_VERTICES unavailable: {}",error.toString());
        }
    }
    private ByteBuffer memory;
    private long base;
    private int count,idsEntity,idsBlock,idsItem;
    private Object writer;
    private VertexConsumer fallback;
    private float x,y,z,u,v;
    private int color,overlay,light,normal;
    private float[] transformed;
    private int[] packedNormals;
    void prepare(float[] positions,float[] normals,Matrix4f matrix,Matrix3f normalMatrix){
        if(!available||!OutsiderShaderCompat.active())return;
        if(transformed==null||transformed.length!=positions.length){transformed=new float[positions.length];packedNormals=new int[positions.length/3];}
        for(int i=0;i<packedNormals.length;i++){
            int p=i*3;float x=positions[p],y=positions[p+1],z=positions[p+2];
            transformed[p]=matrix.m00()*x+matrix.m10()*y+matrix.m20()*z+matrix.m30();
            transformed[p+1]=matrix.m01()*x+matrix.m11()*y+matrix.m21()*z+matrix.m31();
            transformed[p+2]=matrix.m02()*x+matrix.m12()*y+matrix.m22()*z+matrix.m32();
            float nx=normals[p],ny=normals[p+1],nz=normals[p+2];
            packedNormals[i]=pack(normalMatrix.m00()*nx+normalMatrix.m10()*ny+normalMatrix.m20()*nz,
                    normalMatrix.m01()*nx+normalMatrix.m11()*ny+normalMatrix.m21()*nz,
                    normalMatrix.m02()*nx+normalMatrix.m12()*ny+normalMatrix.m22()*nz);
        }
    }
    void indexed(int index,float[] uvs,int color,int light){
        int p=index*3,uv=index*2;
        write(transformed[p],transformed[p+1],transformed[p+2],color,uvs[uv],uvs[uv+1],
                net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY,light,packedNormals[index]);
    }
    VertexConsumer begin(VertexConsumer consumer){
        if(!available||!OutsiderShaderCompat.active())return consumer;
        try{
            // Iris disables vanilla-format intrinsics because they lack its
            // extended attributes. This bridge supplies the complete ENTITY
            // format, so the raw writer is valid even when tryOf rejects it.
            writer=push.getDeclaringClass().isInstance(consumer)?consumer:tryWriter.invoke(null,consumer);
            if(writer==null){
                if(!reported){reported=true;com.sourceofmystery.SourceOfMystery.LOGGER.info("OUTSIDER_BULK_VERTICES consumer unavailable: {}",consumer.getClass().getName());}
                return consumer;
            }
            if(memory==null){memory=ByteBuffer.allocateDirect(CAPACITY*STRIDE);base=MemoryUtil.memAddress(memory);}
            idsEntity=(int)entityId.invoke(captured);idsBlock=(int)blockId.invoke(captured);idsItem=(int)itemId.invoke(captured);
            fallback=consumer;count=0;
            if(!reported){reported=true;com.sourceofmystery.SourceOfMystery.LOGGER.info("OUTSIDER_BULK_VERTICES active=true stride={} writer={}",STRIDE,writer.getClass().getName());}
            return this;
        }catch(ReflectiveOperationException|LinkageError error){available=false;return consumer;}
    }
    void flush(){
        if(count==0)return;
        try(MemoryStack stack=MemoryStack.stackPush()){
            push.invoke(writer,stack,base,count,format);
        }catch(ReflectiveOperationException|LinkageError error){
            available=false;com.sourceofmystery.SourceOfMystery.LOGGER.warn("Bulk entity submission unavailable; retaining standard vertices",error);
            for(int i=0;i<count;i++){
                long p=base+(long)i*STRIDE;int c=MemoryUtil.memGetInt(p+12),n=MemoryUtil.memGetInt(p+32);
                fallback.vertex(MemoryUtil.memGetFloat(p),MemoryUtil.memGetFloat(p+4),MemoryUtil.memGetFloat(p+8),
                        (c&255)/255f,((c>>>8)&255)/255f,((c>>>16)&255)/255f,((c>>>24)&255)/255f,
                        MemoryUtil.memGetFloat(p+16),MemoryUtil.memGetFloat(p+20),
                        MemoryUtil.memGetInt(p+24),MemoryUtil.memGetInt(p+28),
                        (byte)n/127f,(byte)(n>>>8)/127f,(byte)(n>>>16)/127f);
            }
        }
        count=0;
    }
    private void write(float x,float y,float z,int color,float u,float v,int overlay,int light,int normal){
        if(count==CAPACITY)flush();
        long p=base+(long)count*STRIDE;
        MemoryUtil.memPutFloat(p,x);MemoryUtil.memPutFloat(p+4,y);MemoryUtil.memPutFloat(p+8,z);
        MemoryUtil.memPutInt(p+12,color);MemoryUtil.memPutFloat(p+16,u);MemoryUtil.memPutFloat(p+20,v);
        MemoryUtil.memPutInt(p+24,overlay);MemoryUtil.memPutInt(p+28,light);MemoryUtil.memPutInt(p+32,normal);
        MemoryUtil.memPutShort(p+36,(short)idsEntity);MemoryUtil.memPutShort(p+38,(short)idsBlock);MemoryUtil.memPutShort(p+40,(short)idsItem);
        count++;
        if((count&3)==0)finishQuad(p-3L*STRIDE);
    }
    private static void finishQuad(long a){
        long b=a+STRIDE,c=b+STRIDE;
        float ex=MemoryUtil.memGetFloat(b)-MemoryUtil.memGetFloat(a),ey=MemoryUtil.memGetFloat(b+4)-MemoryUtil.memGetFloat(a+4),ez=MemoryUtil.memGetFloat(b+8)-MemoryUtil.memGetFloat(a+8);
        float fx=MemoryUtil.memGetFloat(c)-MemoryUtil.memGetFloat(a),fy=MemoryUtil.memGetFloat(c+4)-MemoryUtil.memGetFloat(a+4),fz=MemoryUtil.memGetFloat(c+8)-MemoryUtil.memGetFloat(a+8);
        float du=MemoryUtil.memGetFloat(b+16)-MemoryUtil.memGetFloat(a+16),dv=MemoryUtil.memGetFloat(b+20)-MemoryUtil.memGetFloat(a+20);
        float eu=MemoryUtil.memGetFloat(c+16)-MemoryUtil.memGetFloat(a+16),ev=MemoryUtil.memGetFloat(c+20)-MemoryUtil.memGetFloat(a+20);
        float determinant=du*ev-eu*dv,sign=determinant<0?-1:1;
        float tx=ex*ev-fx*dv,ty=ey*ev-fy*dv,tz=ez*ev-fz*dv;
        float length=(float)Math.sqrt(tx*tx+ty*ty+tz*tz);
        if(length<1e-8f){tx=1;ty=tz=0;length=1;}
        tx=tx/length*sign;ty=ty/length*sign;tz=tz/length*sign;
        int tangent=pack(tx,ty,tz)|((sign<0?129:127)<<24);
        float midU=0,midV=0;for(int i=0;i<4;i++){long p=a+(long)i*STRIDE;midU+=MemoryUtil.memGetFloat(p+16);midV+=MemoryUtil.memGetFloat(p+20);}
        for(int i=0;i<4;i++){long p=a+(long)i*STRIDE;MemoryUtil.memPutFloat(p+42,midU*.25f);MemoryUtil.memPutFloat(p+46,midV*.25f);MemoryUtil.memPutInt(p+50,tangent);}
    }
    private static int pack(float x,float y,float z){
        float length=(float)Math.sqrt(x*x+y*y+z*z);if(length>1e-8f){x/=length;y/=length;z/=length;}
        return ((int)(x*127)&255)|(((int)(y*127)&255)<<8)|(((int)(z*127)&255)<<16);
    }
    @Override public void vertex(float x,float y,float z,float r,float g,float b,float a,float u,float v,int overlay,int light,float nx,float ny,float nz){
        int rgba=((int)(r*255)&255)|(((int)(g*255)&255)<<8)|(((int)(b*255)&255)<<16)|(((int)(a*255)&255)<<24);
        write(x,y,z,rgba,u,v,overlay,light,pack(nx,ny,nz));
    }
    @Override public VertexConsumer vertex(double x,double y,double z){this.x=(float)x;this.y=(float)y;this.z=(float)z;return this;}
    @Override public VertexConsumer color(int r,int g,int b,int a){color=(r&255)|((g&255)<<8)|((b&255)<<16)|((a&255)<<24);return this;}
    @Override public VertexConsumer uv(float u,float v){this.u=u;this.v=v;return this;}
    @Override public VertexConsumer overlayCoords(int u,int v){overlay=(u&65535)|(v<<16);return this;}
    @Override public VertexConsumer uv2(int u,int v){light=(u&65535)|(v<<16);return this;}
    @Override public VertexConsumer normal(float x,float y,float z){normal=pack(x,y,z);return this;}
    @Override public void endVertex(){write(x,y,z,color,u,v,overlay,light,normal);}
    @Override public void defaultColor(int r,int g,int b,int a){color(r,g,b,a);}
    @Override public void unsetDefaultColor(){}
}
