package com.sourceofmystery.client;

import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

/** Original layered abyss surface. No Nether/End portal textures, shaders or render types. */
final class OfferingVoidSurface {
    private static final int SECTORS=112,RINGS=24;
    private static final float[] GRID=new float[(RINGS+1)*(SECTORS+1)*7];
    private static final float[] EDGE=new float[SECTORS+1];
    private static final double[] ANGLE=new double[SECTORS+1],COS=new double[SECTORS+1],SIN=new double[SECTORS+1];
    static{
        for(int i=0;i<=SECTORS;i++){ANGLE[i]=i*Math.PI*2/SECTORS;COS[i]=Math.cos(ANGLE[i]);SIN[i]=Math.sin(ANGLE[i]);}
    }
    static void render(PoseStack poses,MultiBufferSource buffers,float time,float radius,float opacity){
        Matrix4f m=poses.last().pose();
        VertexConsumer ink=OutsiderShaderCompat.solid(buffers,OutsiderRitualRenderer.SOLID);
        for(int i=0;i<=SECTORS;i++)EDGE[i]=edge(ANGLE[i],radius,time);
        // Evaluate each shared polar vertex once. The old path allocated eight
        // float arrays and evaluated four copies of the same cloud per quad.
        for(int band=0;band<=RINGS;band++)for(int sector=0;sector<=SECTORS;sector++){
            float u=band/(float)RINGS;int p=(band*(SECTORS+1)+sector)*7;
            GRID[p]=(float)COS[sector]*EDGE[sector]*u;GRID[p+1]=(float)SIN[sector]*EDGE[sector]*u;
            GRID[p+2]=-.16f*(1-u);
            shade(ANGLE[sector],u,time,p);
        }
        for(int band=0;band<RINGS;band++)for(int sector=0;sector<SECTORS;sector++){
            int a=(band*(SECTORS+1)+sector)*7,b=a+7,c=a+(SECTORS+1)*7,d=c+7;
            // One front-facing quad; the materials support this camera side.
            gridVertex(ink,m,a);gridVertex(ink,m,c);gridVertex(ink,m,d);gridVertex(ink,m,b);
        }
        VertexConsumer light=OutsiderShaderCompat.glow(buffers,OutsiderRitualRenderer.GLOW);
        // Slow, narrow inward filaments; the center remains almost completely black.
        for(int arm=0;arm<7;arm++)for(int k=0;k<76;k++){
            float u=k/76f,v=(k+1)/76f,r=radius*(.15f+.82f*u),s=radius*(.15f+.82f*v);
            double a=arm*2.39996+u*4.8+time*.12,b=arm*2.39996+v*4.8+time*.12;
            float alpha=opacity*u*u*(.08f+.10f*Mth.sin(u*14+arm+time*.3f));
            float width=.006f+.012f*u;
            EffectGeometry.glowQuad(light,m,point(a,r-width,.012f),point(b,s-width,.012f),point(b,s+width,.012f),point(a,r+width,.012f),
                    new float[]{.08f,.38f,.98f,alpha},new float[]{.015f,.12f,.42f,alpha*.25f});
        }
        // Broken glass-like rim, with sparse cold highlights rather than neon circles.
        for(int i=0;i<SECTORS;i++){
            double a=i*Math.PI*2/SECTORS,b=(i+1)*Math.PI*2/SECTORS;
            float r=edge(a,radius,time),s=edge(b,radius,time),f=EffectGeometry.hash(381,i);
            float width=.018f+.043f*f;
            EffectGeometry.glowQuad(light,m,point(a,r,.02f),point(b,s,.02f),point(b,s+width,.02f),point(a,r+width,.02f),
                    new float[]{.26f,.72f,1f,opacity*(.35f+.4f*f)},new float[]{.02f,.20f,.62f,0});
            if(i%5==0){
                float outer=r+.10f+f*.26f;double offset=.025+f*.018;
                float[] tint={.045f,.24f,.58f,opacity*.65f};
                EffectGeometry.glowQuad(light,m,point(a,r+.06f,.04f),point(a+offset,outer,.10f),point(a+offset*1.4,outer+.12f,.06f),point(a+.018,r+.17f,.04f),tint,new float[]{.005f,.04f,.15f,0});
            }
        }
        // A soft, uneven atmospheric lip separates the abyss from the world.
        // Several thin alpha bands provide falloff without a flat glowing plate.
        for(int layer=0;layer<5;layer++)for(int i=0;i<SECTORS;i++){
            double a=i*Math.PI*2/SECTORS,b=(i+1)*Math.PI*2/SECTORS;
            float ri=edge(a,radius,time)+layer*.028f,ro=edge(b,radius,time)+layer*.028f;
            float strength=opacity*(1-layer/5f)*(.025f+.025f*Mth.sin(i*.41f+time*.65f));
            EffectGeometry.glowQuad(light,m,point(a,ri,.018f),point(b,ro,.018f),
                    point(b,ro+.04f,.018f),point(a,ri+.04f,.018f),
                    new float[]{.08f,.36f,.88f,strength},new float[]{.02f,.10f,.32f,0});
        }
        // Infalling motes have a deterministic curved trajectory and a short tail.
        for(int i=0;i<48;i++){
            float u=(time*.16f+i*.618034f)%1, r=radius*(1-u),w=.009f+.009f*(1-u);
            double a=i*2.39996+u*3.3;
            float[] c={.20f,.60f,1f,opacity*.55f*(1-u)};
            EffectGeometry.glowQuad(light,m,point(a-.004,r-w,.025f),point(a+.004,r+w,.025f),point(a+.014,r+.07f,.025f),point(a+.006,r+.06f,.025f),c,new float[]{.01f,.10f,.32f,0});
        }
    }
    private static float edge(double a,float r,float t){return r*(1+.009f*(float)Math.sin(a*11+t*.23)+.012f*(float)Math.sin(a*5-t*.17));}
    private static float[] point(double a,float r,float z){return new float[]{(float)Math.cos(a)*r,(float)Math.sin(a)*r,z};}
    private static void shade(double a,float r,float t,int p){
        float swirl=(float)(Math.sin(a*4+r*19-t*.25)+.5*Math.sin(a*9-r*13+t*.17));
        float cloud=(float)Math.max(0,swirl/1.5);cloud=cloud*cloud*cloud*r*r;
        float mist=(float)Math.max(0,Math.sin(a*3-r*9+t*.12)*Math.sin(a*7+r*5-t*.09));mist=mist*mist*r*r;
        GRID[p+3]=.002f+.009f*cloud;GRID[p+4]=.006f+.035f*cloud+.017f*mist;
        GRID[p+5]=.023f+.13f*cloud+.08f*mist;GRID[p+6]=1;
    }
    private static void gridVertex(VertexConsumer v,Matrix4f m,int p){
        v.vertex(m,GRID[p],GRID[p+1],GRID[p+2]).color(GRID[p+3],GRID[p+4],GRID[p+5],GRID[p+6]).endVertex();
    }
}
