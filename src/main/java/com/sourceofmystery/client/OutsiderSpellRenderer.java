package com.sourceofmystery.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.sourceofmystery.entity.OutsiderSpell;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

/** Reuses the mod's portal geometry; effects do not emit particle packet storms. */
public class OutsiderSpellRenderer extends EntityRenderer<OutsiderSpell> {
    private static final float[] PURPLE={.42f,.06f,.78f,.8f}, PALE={.7f,.35f,1f,.85f};
    private static final float[][][] SPHERE=sphere(12,20);
    private static final float[][][] SMALL_SPHERE=sphere(6,10);
    public OutsiderSpellRenderer(EntityRendererProvider.Context context){super(context);}
    @Override public void render(OutsiderSpell spell,float yaw,float partial,PoseStack poses,MultiBufferSource buffers,int light){
        float age=spell.age(partial);int kind=spell.kind();
        if(usesWorldPass(kind))return; // Draw once, after entities, outside the compiled-section gate.
        if(kind==OutsiderSpell.RINGS)return; // Attached ritual is drawn with its owner's actual staff matrix.
        poses.pushPose();
        // Render at the same launch-time trajectory the server sweeps for hits,
        // rather than extrapolating packet velocity independently on the client.
        net.minecraft.world.phys.Vec3 exact=spell.trajectoryPosition(partial);
        poses.translate(exact.x-Mth.lerp(partial,spell.xOld,spell.getX()),
                exact.y-Mth.lerp(partial,spell.yOld,spell.getY()),exact.z-Mth.lerp(partial,spell.zOld,spell.getZ()));
        if(kind==OutsiderSpell.BOLT)orb(poses,buffers,.22f,age);
        else if(kind==OutsiderSpell.PILLAR){
            float active=Mth.clamp((age-20)/4,0,1)*Mth.clamp((80-age)/5,0,1);
            Matrix4f matrix=poses.last().pose();
            ring(buffers.getBuffer(RenderType.lightning()),matrix,1.08f,.06f,0,age*.012f,PALE,true);
            ring(buffers.getBuffer(RenderType.lightning()),matrix,.8f,.035f,.01f,-age*.01f,PURPLE,false);
            if(active>0){
                float height=Math.max(8,spell.level().getMaxBuildHeight()-(float)spell.getY());
                VertexConsumer portal=EffectGeometry.voidBuffer(buffers);
                for(int i=0;i<16;i++){
                    double a=i*Math.PI/8,b=(i+1)*Math.PI/8;
                    float[] p=point(.85f*active,0,a),q=point(.85f*active,0,b),r=q.clone(),s=p.clone();r[1]=s[1]=height;
                    EffectGeometry.voidQuad(portal,matrix,p,q,r,s);
                    EffectGeometry.voidQuad(portal,matrix,new float[]{0,.015f,0},p,q,new float[]{0,.015f,0});
                }
                VertexConsumer glow=buffers.getBuffer(RenderType.lightning());
                for(int i=0;i<4;i++){
                    float[] a=point(.88f*active,0,i*Math.PI/2),b=point(.91f*active,0,i*Math.PI/2+.025),c=b.clone(),d=a.clone();c[1]=d[1]=height;
                    EffectGeometry.glowQuad(glow,matrix,a,b,c,d,PURPLE,PURPLE);
                }
            }
        }else if(kind==OutsiderSpell.AFTERMATH&&age<45){
            float[] color={.38f,.06f,.65f,(1-age/45)*.7f};
            // The whole damage radius activates on impact; the moving ring is
            // only the trailing shockwave, not a delayed damage front.
            ring(buffers.getBuffer(RenderType.lightning()),poses.last().pose(),OutsiderSpell.BLAST_RADIUS,.9f,.16f,0,color,true);
            ring(buffers.getBuffer(RenderType.lightning()),poses.last().pose(),age/45*100,.5f,.12f,0,color,true);
        }
        poses.popPose();
        super.render(spell,yaw,partial,poses,buffers,light);
    }
    static boolean usesWorldPass(int kind){return kind==OutsiderSpell.SKY_SEAL||kind==OutsiderSpell.METEOR;}
    /** Pose is already at the exact camera-relative trajectory position. */
    static void renderHighEffect(OutsiderSpell spell,float partial,PoseStack poses,MultiBufferSource buffers){
        if(spell.kind()==OutsiderSpell.SKY_SEAL)OutsiderRitualRenderer.renderSky(poses,buffers,spell.age(partial)/20f);
        else if(spell.kind()==OutsiderSpell.METEOR)redMeteor(spell,poses,buffers,partial,spell.trajectoryPosition(partial));
    }
    private static void redMeteor(OutsiderSpell spell,PoseStack poses,MultiBufferSource buffers,float partial,net.minecraft.world.phys.Vec3 exact){
        float age=spell.age(partial),radius=com.sourceofmystery.entity.OutsiderRitual.ORB_RADIUS;
        // True opaque, depth-writing crimson sphere. This is deliberately not
        // the lightning blend used by the previous purple orb.
        poses.pushPose();poses.scale(radius,radius,radius);
        VertexConsumer solid=OutsiderShaderCompat.solid(buffers,OutsiderRitualRenderer.SOLID);
        Matrix4f m=poses.last().pose();
        for(int i=0;i<SPHERE.length;i++){
            float pulse=.5f+.5f*Mth.sin(age*.045f+i*.22f);
            float[] c={.075f+.075f*pulse,.002f+.005f*pulse,.013f+.022f*pulse,1};
            float[][] q=SPHERE[i];EffectGeometry.glowQuad(solid,m,q[0],q[1],q[2],q[3],c,c);
        }
        VertexConsumer shine=OutsiderShaderCompat.glow(buffers,OutsiderRitualRenderer.GLOW);
        for(int i=0;i<5;i++){
            poses.pushPose();poses.mulPose(Axis.XP.rotationDegrees(25+i*37));
            poses.mulPose(Axis.ZP.rotationDegrees(age*.6f+i*41));
            ring(shine,poses.last().pose(),1.012f,.006f,0,age*.012f,new float[]{.55f,.018f,.085f,.8f},true);
            poses.popPose();
        }
        poses.popPose();
        // Six past trajectory positions: a tapered wake follows the actual
        // fall rather than pointing upward independent of target direction.
        VertexConsumer trail=OutsiderShaderCompat.glow(buffers,OutsiderRitualRenderer.GLOW);
        for(int i=1;i<=6;i++){
            if(age<i*3)continue;
            var offset=spell.trajectoryPosition(partial-i*3).subtract(exact);
            poses.pushPose();poses.translate(offset.x,offset.y,offset.z);
            float r=radius*(1-i*.12f);poses.scale(r,r,r);
            float[] c={.32f,.006f,.035f,.14f*(1-i/7f)};
            Matrix4f matrix=poses.last().pose();
            for(float[][] q:SMALL_SPHERE)EffectGeometry.glowQuad(trail,matrix,q[0],q[1],q[2],q[3],c,c);
            poses.popPose();
        }
    }
    public static void orb(PoseStack poses,MultiBufferSource buffers,float radius,float age){
        if(radius<=0)return;
        poses.pushPose();poses.scale(radius,radius,radius);
        VertexConsumer portal=EffectGeometry.voidBuffer(buffers);Matrix4f matrix=poses.last().pose();
        for(float[][] quad:radius<1?SMALL_SPHERE:SPHERE)EffectGeometry.voidQuad(portal,matrix,quad[0],quad[1],quad[2],quad[3]);
        for(int i=0;i<3;i++){
            poses.pushPose();poses.mulPose(Axis.ZP.rotationDegrees(35+i*55));poses.mulPose(Axis.XP.rotationDegrees(i*37));
            ring(buffers.getBuffer(RenderType.lightning()),poses.last().pose(),1.012f,.012f,0,age*.01f*(i%2==0?1:-1),PURPLE,true);
            poses.popPose();
        }
        poses.popPose();
    }
    public static void violetOrb(PoseStack poses,MultiBufferSource buffers,float radius,float age){
        poses.pushPose();poses.scale(radius,radius,radius);
        VertexConsumer vc=buffers.getBuffer(RenderType.lightning());Matrix4f m=poses.last().pose();
        for(int i=0;i<SPHERE.length;i++){
            float wave=.5f+.5f*Mth.sin(age*.09f+i*.63f);
            // Opaque deep-amethyst body; the separate thin rings provide the
            // readable violet edge instead of washing the meteor pale.
            float[] color={.025f+.035f*wave,.001f+.004f*wave,.10f+.09f*wave,1};
            float[][] quad=SPHERE[i];EffectGeometry.glowQuad(vc,m,quad[0],quad[1],quad[2],quad[3],color,color);
        }
        for(int i=0;i<4;i++){
            poses.pushPose();poses.mulPose(Axis.XP.rotationDegrees(32+i*43));poses.mulPose(Axis.ZP.rotationDegrees(age*.35f+i*35));
            ring(vc,poses.last().pose(),1.008f+i*.003f,.008f,0,age*.012f*(i%2==0?1:-1),new float[]{.32f,.035f,.72f,.92f},true);
            poses.popPose();
        }
        poses.popPose();
    }
    private static void ring(VertexConsumer buffer,Matrix4f matrix,float radius,float width,float y,float rotation,float[] color,boolean runes){
        for(int i=0;i<64;i++){
            double a=i*Math.PI/32+rotation,b=(i+1)*Math.PI/32+rotation;
            float[] p=point(radius,y,a),q=point(radius,y,b),r=point(Math.max(0,radius-width),y,b),s=point(Math.max(0,radius-width),y,a);
            EffectGeometry.glowQuad(buffer,matrix,p,q,r,s,color,color);
            if(runes&&i%4==0){
                float outer=radius+width*3;
                EffectGeometry.glowQuad(buffer,matrix,point(radius,y,a),point(outer,y,a+.02),point(outer,y,a+.055),point(radius,y,a+.08),color,color);
            }
        }
    }
    private static float[] point(float radius,float y,double angle){return new float[]{radius*(float)Math.cos(angle),y,radius*(float)Math.sin(angle)};}
    private static float[][][] sphere(int lat,int lon){
        float[][][] quads=new float[lat*lon][4][];
        for(int i=0;i<lat;i++)for(int j=0;j<lon;j++){
            double a=-Math.PI/2+i*Math.PI/lat,b=a+Math.PI/lat,c=j*Math.PI*2/lon,d=c+Math.PI*2/lon;
            quads[i*lon+j]=new float[][]{point((float)Math.cos(a),(float)Math.sin(a),c),point((float)Math.cos(a),(float)Math.sin(a),d),
                    point((float)Math.cos(b),(float)Math.sin(b),d),point((float)Math.cos(b),(float)Math.sin(b),c)};
        }
        return quads;
    }
    @Override public ResourceLocation getTextureLocation(OutsiderSpell spell){return TextureAtlas.LOCATION_BLOCKS;}
}
