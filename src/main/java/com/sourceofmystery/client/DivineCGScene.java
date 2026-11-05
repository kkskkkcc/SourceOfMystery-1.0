package com.sourceofmystery.client;

import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Axis;
import com.sourceofmystery.cinematic.DivineCGTimeline;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import java.util.*;

/** A client-only stage: no chunks, blocks, entities or world time are changed. */
final class DivineCGScene extends RenderType {
    private static ResourceLocation texture(String name){return new ResourceLocation("sourceofmystery","textures/cinematic/divine/"+name+".png");}
    private static final ResourceLocation SPACE=texture("space"),EARTH=texture("earth"),STONE=texture("chaos_stone"),METAL=texture("metal"),WHITE=texture("white"),HALO=texture("mandala");
    private static final RenderType SKY=RenderType.create("som_divine_cosmos",DefaultVertexFormat.NEW_ENTITY,VertexFormat.Mode.QUADS,262144,false,false,
            CompositeState.builder().setShaderState(RENDERTYPE_ENTITY_TRANSLUCENT_SHADER)
                    .setTextureState(new TextureStateShard(SPACE,true,false)).setTransparencyState(NO_TRANSPARENCY)
                    .setCullState(NO_CULL).setLightmapState(LIGHTMAP).setOverlayState(OVERLAY)
                    .setDepthTestState(NO_DEPTH_TEST).setWriteMaskState(COLOR_WRITE).createCompositeState(false));
    // Entity presets force nearest filtering; a photographed planet needs linear sampling.
    private static final RenderType EARTH_SURFACE=RenderType.create("som_divine_earth",DefaultVertexFormat.NEW_ENTITY,VertexFormat.Mode.QUADS,262144,false,false,
            CompositeState.builder().setShaderState(RENDERTYPE_ENTITY_TRANSLUCENT_EMISSIVE_SHADER)
                    .setTextureState(new TextureStateShard(EARTH,true,false)).setTransparencyState(NO_TRANSPARENCY)
                    .setCullState(NO_CULL).setLightmapState(LIGHTMAP).setOverlayState(OVERLAY)
                    .setWriteMaskState(COLOR_DEPTH_WRITE).createCompositeState(false));
    private final DivineCGPlayer player=new DivineCGPlayer();
    private ArmorStand mannequin;
    private HumanoidModel<net.minecraft.world.entity.LivingEntity> armorPose;
    private final DivineCGMesh architecture=new DivineCGMesh();
    private static final Map<Long,float[]> SPHERES=new HashMap<>();
    private DivineCGThrone throne;
    private DivineCGScene(String n,VertexFormat f,VertexFormat.Mode m,int size,boolean cr,boolean sorted,Runnable a,Runnable b){super(n,f,m,size,cr,sorted,a,b);}
    DivineCGScene(){this("som_divine_scene",DefaultVertexFormat.NEW_ENTITY,VertexFormat.Mode.QUADS,256,false,false,()->{},()->{});}
    void clear(){mannequin=null;armorPose=null;throne=null;}
    void prepare(){
        // Upload before starting the camera clock, rather than at the cloud exit.
        var textures=Minecraft.getInstance().getTextureManager();
        for(var resource:new ResourceLocation[]{SPACE,EARTH,STONE,METAL,WHITE,HALO,texture("ascent_clouds")})textures.bindForSetup(resource);
        textures.bindForSetup(DivineThroneMaterial.texture());
        sphereGeometry(48,24);sphereGeometry(96,48);
    }
    void render(RenderLevelStageEvent e,Vec3 origin,float time,ItemStack armor){
        var mc=Minecraft.getInstance();var p=e.getPoseStack();var b=mc.renderBuffers().bufferSource();Vec3 cam=e.getCamera().getPosition();
        p.pushPose();
        try {
            // No depth writes: this replaces the sky without hiding the near stage.
            float stars=DivineCGTimeline.ramp(DivineCGDirector.seconds(),4.3f,6.7f);
            sphere(p,b.getBuffer(SKY),Vec3.ZERO,380,48,24,stars,stars,stars,1,false,null);
            b.endBatch();
            p.translate(origin.x-cam.x,origin.y-cam.y,origin.z-cam.z);
            Vec3 earth=DivineCGTimeline.EARTH;
            sphere(p,b.getBuffer(EARTH_SURFACE),earth,DivineCGTimeline.EARTH_RADIUS,96,48,1,1,1,1,true,null);
            b.endBatch();
            sphere(p,b.getBuffer(RenderType.entityTranslucent(WHITE)),earth,93.4f,96,48,.17f,.48f,1,.4f,false,cam.subtract(origin));
            b.endBatch();
            stage(p,architecture,time);architecture.draw(b);
            if(time>10.1f){
                if(throne==null)throne=new DivineCGThrone();
                float reveal=DivineCGTimeline.ramp(time,10.1f,11.6f);
                p.pushPose();p.translate(0,1.8-(1-reveal)*1.3,-7.7);
                throne.render(p,b,DivineCGDirector.seconds());p.popPose();
            }
            sanctumLight(p,b,time);
            if(time<DivineCGTimeline.EQUIP)floatingArmor(p,b,time,armor);
            p.pushPose();var pos=DivineCGTimeline.player(time);p.translate(pos.x,pos.y,pos.z);
            player.render(p,b,Math.min(time,32),e.getPartialTick(),armor);p.popPose();
            godlight(p,b,time);
            b.endBatch();
        } finally {p.popPose();}
    }
    private void sanctumLight(PoseStack p,MultiBufferSource.BufferSource b,float t){
        float open=DivineCGTimeline.ramp(t,10,12),crown=DivineCGTimeline.ramp(t,23,25.5f);
        if(open<.001)return;
        var glow=b.getBuffer(RenderType.entityTranslucentEmissive(WHITE));
        // Faint pillars frame the procession; they never cover the face close-ups.
        for(int side:new int[]{-1,1})for(int i=0;i<3;i++){
            float x=side*(2.65f+i*.20f),z=-2.6f-i*2.1f;
            p.pushPose();p.translate(x,1,-.5f+z);
            quad(p,glow,-.025f,0,.025f,8+i*.8f,0,1,.90f,.68f,.075f*open);
            p.mulPose(Axis.YP.rotationDegrees(90));quad(p,glow,-.025f,0,.025f,8+i*.8f,0,1,.90f,.68f,.075f*open);p.popPose();
        }
        // A halo of low, fine motes implies a sacred space rather than a storm.
        for(int i=0;i<38;i++){
            double a=i*2.39996+t*.035;double r=2.6+(i%6)*.35;
            float y=1.7f+((i*.29f+t*.065f)%3.2f),s=.009f+(i%4)*.004f;
            p.pushPose();p.translate(Math.cos(a)*r,y,-4.5+Math.sin(a)*r);
            p.mulPose(Minecraft.getInstance().getEntityRenderDispatcher().cameraOrientation());
            quad(p,glow,-s,-s,s,s,0,1,.92f,.74f,.34f*open);p.popPose();
        }
        b.endBatch();
    }
    private void stage(PoseStack p,MultiBufferSource b,float t){
        var stone=b.getBuffer(RenderType.entityCutoutNoCull(STONE));
        // Small arrival dais remains under the player while the stairway resolves.
        box(p,stone,-2.5f,-.34f,3.25f,2.5f,-.04f,5.8f,.62f,.66f,.74f);
        var metal=b.getBuffer(RenderType.entityCutoutNoCull(METAL));
        box(p,metal,-2.55f,-.08f,3.2f,2.55f,0,5.85f,.72f,.63f,.4f);
        for(int i=0;i<5;i++){
            float open=DivineCGTimeline.ramp(t,10+i*.21f,10.8f+i*.21f);if(open<.001)continue;
            float top=(i+1)*.36f-(1-open)*1.2f,back=2.65f-i*1.4f,front=back+1.4f;
            float half=2.4f-i*.06f;
            box(p,stone,-half,top-.36f,back,half,top,front,.66f,.69f,.78f);
            box(p,metal,-half-.035f,top-.045f,front-.10f,half+.035f,top+.008f,front,.8f,.70f,.46f);
            box(p,metal,-half,top-.14f,back,-half+.065f,top+.012f,front,.48f,.51f,.60f);
            box(p,metal,half-.065f,top-.14f,back,half,top+.012f,front,.48f,.51f,.60f);
        }
        float reveal=DivineCGTimeline.ramp(t,10.1f,11.6f);if(reveal<.001)return;
        p.pushPose();p.translate(0,-(1-reveal)*1.3,0);
        box(p,stone,-3.25f,1.44f,-10.8f,3.25f,1.8f,-2.95f,.85f,.87f,.95f);
        box(p,metal,-3.3f,1.68f,-10.85f,3.3f,1.76f,-2.92f,.70f,.65f,.48f);
        p.popPose();
        // A warm low-intensity floor inlay leads the eye toward the waiting armor.
        var glow=b.getBuffer(RenderType.entityTranslucentEmissive(WHITE));
        for(int i=0;i<5;i++){
            float y=(i+1)*.36f+.014f,z=3.98f-i*1.4f;
            box(p,glow,-.14f,y,z-.25f,.14f,y+.008f,z-.18f,.9f,.82f,.55f,.7f);
        }
    }
    private void floatingArmor(PoseStack p,MultiBufferSource.BufferSource b,float t,ItemStack armor){
        var mc=Minecraft.getInstance();
        if(mannequin==null || mannequin.level()!=mc.level){
            mannequin=new ArmorStand(mc.level,0,0,0);mannequin.setNoGravity(true);mannequin.setInvisible(true);
            armorPose=new HumanoidModel<>(mc.getEntityModels().bakeLayer(ModelLayers.PLAYER));
        }
        mannequin.tickCount=(int)(t*20);mannequin.setItemSlot(EquipmentSlot.CHEST,armor);
        armorPose.setAllVisible(true);armorPose.young=false;
        armorPose.rightArm.zRot=.10f;armorPose.leftArm.zRot=-.10f;
        float appear=DivineCGTimeline.ramp(t,4,5),bob=.032f*Mth.sin(t*1.6f);
        p.pushPose();p.translate(0,1.98+bob,-4.55);p.scale(appear,appear,appear);
        p.mulPose(Axis.YP.rotationDegrees(180));p.scale(-1,-1,1);p.translate(0,-1.501,0);
        var rendered=IClientItemExtensions.of(armor).getHumanoidArmorModel(mannequin,armor,EquipmentSlot.CHEST,armorPose);
        rendered.renderToBuffer(p,b.getBuffer(RenderType.entityCutoutNoCull(new ResourceLocation("sourceofmystery","textures/armor/divine_blessing.png"))),LightTexture.FULL_BRIGHT,OverlayTexture.NO_OVERLAY,1,1,1,1);
        p.popPose();b.endBatch();
        // Slow counter-rotating dark/white discs form the chaos aura behind the armor.
        for(int i=0;i<2;i++){
            p.pushPose();p.translate(0,2.94,-4.87-i*.015);p.mulPose(Axis.ZP.rotationDegrees(t*(i==0?3:-4)));
            float r=i==0?1.42f:1.05f;
            quad(p,b.getBuffer(RenderType.entityTranslucentEmissive(HALO)),-r,-r,r,r,0,i==0?.73f:.38f,i==0?.70f:.44f,i==0?.60f:.66f,(i==0?.24f:.19f)*appear);
            p.popPose();
        }
        var glow=b.getBuffer(RenderType.entityTranslucentEmissive(WHITE));
        for(int i=0;i<22;i++){
            double a=i*2.399963+t*.13;float r=.55f+(i%4)*.12f,y=2.1f+((i*.17f+t*.14f)%1.9f);
            float size=.009f+(i%3)*.004f;
            p.pushPose();p.translate(Math.cos(a)*r,y,-4.65+Math.sin(a)*.4);p.mulPose(Minecraft.getInstance().getEntityRenderDispatcher().cameraOrientation());
            quad(p,glow,-size,-size,size,size,0,.93f,.86f,.67f,.6f);p.popPose();
        }
    }
    private void godlight(PoseStack p,MultiBufferSource b,float t){
        float age=t-24.12f;if(age<0||age>2.5)return;
        float a=(1-DivineCGTimeline.smooth(age/2.5f))*.7f,extent=age*5+.3f;
        var v=b.getBuffer(RenderType.entityTranslucentEmissive(WHITE));
        p.pushPose();p.translate(0,2.95,-4.1);
        for(int i=0;i<24;i++){
            p.pushPose();p.mulPose(Axis.YP.rotationDegrees(i*137.508f));p.mulPose(Axis.ZP.rotationDegrees(i*47.3f));
            quad(p,v,-.015f,0,.015f,extent,0,1,.93f,.72f,a);p.popPose();
        }
        p.popPose();
    }
    private static void sphere(PoseStack p,VertexConsumer v,Vec3 c,float radius,int slices,int rings,float r,float g,float b,float alpha,boolean lit,Vec3 view){
        float[] unit=sphereGeometry(slices,rings);
        for(int i=0;i<unit.length;i+=5){
            float nx=unit[i],ny=unit[i+1],nz=unit[i+2];
            float x=(float)c.x+nx*radius,y=(float)c.y+ny*radius,z=(float)c.z+nz*radius;
            float shade=lit?.12f+.88f*Math.max(0,nx*.45f+ny*.68f+nz*.58f):1,a=alpha;
            if(view!=null){
                double dx=view.x-x,dy=view.y-y,dz=view.z-z,inv=1/Math.sqrt(dx*dx+dy*dy+dz*dz);
                a*=Math.pow(Math.max(0,1-Math.abs((nx*dx+ny*dy+nz*dz)*inv)),3.5);
            }
            vertex(p,v,x,y,z,unit[i+3],unit[i+4],r*shade,g*shade,b*shade,a,nx,ny,nz);
        }
    }
    private static float[] sphereGeometry(int slices,int rings){
        long key=((long)slices<<32)|rings;
        return SPHERES.computeIfAbsent(key,unused->{
            float[] mesh=new float[slices*rings*4*5];int cursor=0;
            for(int j=0;j<rings;j++)for(int i=0;i<slices;i++)for(int corner=0;corner<4;corner++){
                float u=(i+(corner==1||corner==2?1:0))/(float)slices,v=(j+(corner>=2?1:0))/(float)rings;
                double lon=u*Math.PI*2,lat=v*Math.PI;
                mesh[cursor++]=(float)(Math.sin(lat)*Math.cos(lon));mesh[cursor++]=(float)Math.cos(lat);
                mesh[cursor++]=(float)(Math.sin(lat)*Math.sin(lon));mesh[cursor++]=u;mesh[cursor++]=v;
            }
            return mesh;
        });
    }
    private static void box(PoseStack p,VertexConsumer v,float x,float y,float z,float X,float Y,float Z,float r,float g,float b){box(p,v,x,y,z,X,Y,Z,r,g,b,1);}
    private static void box(PoseStack p,VertexConsumer v,float x,float y,float z,float X,float Y,float Z,float r,float g,float b,float a){
        face(p,v,new float[][]{{x,y,z},{x,Y,z},{X,Y,z},{X,y,z}},r*.85f,g*.85f,b*.85f,a,0,0,-1);
        face(p,v,new float[][]{{X,y,Z},{X,Y,Z},{x,Y,Z},{x,y,Z}},r,g,b,a,0,0,1);
        face(p,v,new float[][]{{x,y,Z},{x,Y,Z},{x,Y,z},{x,y,z}},r*.8f,g*.8f,b*.8f,a,-1,0,0);
        face(p,v,new float[][]{{X,y,z},{X,Y,z},{X,Y,Z},{X,y,Z}},r*.9f,g*.9f,b*.9f,a,1,0,0);
        face(p,v,new float[][]{{x,Y,z},{x,Y,Z},{X,Y,Z},{X,Y,z}},r,g,b,a,0,1,0);
        face(p,v,new float[][]{{x,y,Z},{x,y,z},{X,y,z},{X,y,Z}},r*.65f,g*.65f,b*.65f,a,0,-1,0);
    }
    private static void face(PoseStack p,VertexConsumer v,float[][] points,float r,float g,float b,float a,float nx,float ny,float nz){
        for(int i=0;i<4;i++)vertex(p,v,points[i][0],points[i][1],points[i][2],i<2?0:1,i==0||i==3?1:0,r,g,b,a,nx,ny,nz);
    }
    private static void quad(PoseStack p,VertexConsumer v,float x,float y,float X,float Y,float z,float r,float g,float b,float a){face(p,v,new float[][]{{x,y,z},{x,Y,z},{X,Y,z},{X,y,z}},r,g,b,a,0,0,1);}
    private static void vertex(PoseStack p,VertexConsumer v,float x,float y,float z,float u,float vv,float r,float g,float b,float a,float nx,float ny,float nz){
        v.vertex(p.last().pose(),x,y,z).color(r,g,b,a).uv(u,vv).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT).normal(p.last().normal(),nx,ny,nz).endVertex();
    }
}
