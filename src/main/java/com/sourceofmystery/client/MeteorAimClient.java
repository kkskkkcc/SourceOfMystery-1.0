package com.sourceofmystery.client;

import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Axis;
import com.sourceofmystery.client.*;
import com.sourceofmystery.entity.OutsiderSpell;
import com.sourceofmystery.network.MeteorAimPacket;
import com.sourceofmystery.staff.MeteorTargeting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.*;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.*;

/** Local render hints only: no entity glowing flags, scoreboard teams or broadcast particles. */
@Mod.EventBusSubscriber(modid="sourceofmystery",value=Dist.CLIENT)
public final class MeteorAimClient {
    public static final int WHITE=0xe6f5ff,RED=0xff2147;
    private static ClientLevel world;
    private static MeteorAimPacket confirmed;
    private static MeteorTargeting.Pick preview=new MeteorTargeting.Pick(null,null);
    private static final List<OutsiderSpell> FLIGHTS=new ArrayList<>();
    private static MultiBufferSource.BufferSource buffers;
    private MeteorAimClient(){}
    public static void receive(MeteorAimPacket p){
        var level=Minecraft.getInstance().level;
        if(world!=level){FLIGHTS.clear();preview=new MeteorTargeting.Pick(null,null);}
        world=level;confirmed=p;
    }
    public static boolean previewLocked(){return preview.entity()!=null&&confirmed!=null&&preview.entity().getUUID().equals(confirmed.entity())&&confirmed.dwell()>=MeteorTargeting.LOCK_TICKS;}
    public static int progress(){return preview.entity()!=null&&confirmed!=null&&preview.entity().getUUID().equals(confirmed.entity())?Math.min(40,confirmed.dwell()):0;}
    public static MeteorTargeting.Pick preview(){return preview;}
    public static int outlineColor(Entity entity){
        if(entity==null||!entity.level().isClientSide||entity.level()!=world)return -1;
        var mc=Minecraft.getInstance();if(mc.player==null||world!=mc.level||!entity.isAlive())return -1;
        for(var flight:FLIGHTS)if(!flight.isRemoved()&&flight.kind()==OutsiderSpell.METEOR&&flight.trackedTarget()==entity)return RED;
        if(preview.entity()==entity&&MeteorTargeting.enabled(mc.player)&&mc.screen==null)return previewLocked()?RED:WHITE;
        return -1;
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event){
        if(event.phase!=TickEvent.Phase.END)return;var mc=Minecraft.getInstance();
        if(world!=mc.level){world=mc.level;confirmed=null;FLIGHTS.clear();}
        preview=new MeteorTargeting.Pick(null,null);FLIGHTS.clear();
        if(world==null||mc.player==null||mc.isPaused())return;
        if(mc.screen==null&&MeteorTargeting.enabled(mc.player)&&!DomainDirector.active()&&!DivineCGDirector.active()&&!OfferingIntroDirector.active())preview=MeteorTargeting.pick(mc.player,1);
        for(var e:world.entitiesForRendering())if(e instanceof OutsiderSpell s&&s.kind()==OutsiderSpell.METEOR&&s.getOwner()==mc.player)FLIGHTS.add(s);
    }
    @SubscribeEvent public static void render(RenderLevelStageEvent event){
        if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_ENTITIES||world!=Minecraft.getInstance().level||OutsiderShaderCompat.shadowPass())return;
        if(buffers==null)buffers=MultiBufferSource.immediate(new BufferBuilder(1<<17));
        Vec3 camera=event.getCamera().getPosition();var poses=event.getPoseStack();float time=world==null?0:world.getGameTime()+event.getPartialTick();
        if(preview.valid())mark(poses,camera,preview.point(),previewLocked()?RED:WHITE,time,preview.entity()==null);
        for(var s:FLIGHTS)if(!s.isRemoved()&&s.kind()==OutsiderSpell.METEOR)mark(poses,camera,s.timedImpactPoint(),s.trackedTarget()==null?WHITE:RED,time,true);
        buffers.endBatch();
    }
    private static void mark(PoseStack poses,Vec3 camera,Vec3 point,int rgb,float time,boolean area){
        float r=((rgb>>16)&255)/255f,g=((rgb>>8)&255)/255f,b=(rgb&255)/255f;
        poses.pushPose();poses.translate(point.x-camera.x,point.y-camera.y+.06,point.z-camera.z);poses.mulPose(Axis.XP.rotationDegrees(90));
        StaffArcaneMaterial.seal(poses,buffers,area?2.6f:1.1f,-time*.5f,new float[]{r,g,b,.65f},true);
        if(area){
            var ink=OutsiderShaderCompat.glow(buffers,OutsiderRitualRenderer.GLOW);var m=poses.last().pose();
            for(int i=0;i<160;i++){
                double a=i*Math.PI/80,c=(i+.72)*Math.PI/80;float radius=OutsiderSpell.BLAST_RADIUS;
                EffectGeometry.glowQuad(ink,m,point(radius-.10f,a),point(radius+.10f,a),point(radius+.10f,c),point(radius-.10f,c),new float[]{r,g,b,.33f},new float[]{r,g,b,.33f});
            }
        }
        poses.popPose();
        if(area){
            // A horizontal impact disc is edge-on when aiming at a wall. Keep the exact
            // picked point readable with a small camera-facing beacon as well.
            Vec3 visible=point.add(camera.subtract(point).normalize().scale(.045));
            poses.pushPose();poses.translate(visible.x-camera.x,visible.y-camera.y,visible.z-camera.z);
            poses.mulPose(Minecraft.getInstance().getEntityRenderDispatcher().cameraOrientation());
            var ink=OutsiderShaderCompat.glow(buffers,OutsiderRitualRenderer.GLOW);var m=poses.last().pose();float[] color={r,g,b,.95f};
            for(int i=0;i<40;i++){
                double a=i*Math.PI/20,c=(i+1)*Math.PI/20;
                EffectGeometry.glowQuad(ink,m,point(.335f,a),point(.36f,a),point(.36f,c),point(.335f,c),color,color);
            }
            for(int i=0;i<4;i++){
                poses.pushPose();poses.mulPose(Axis.ZP.rotationDegrees(i*90));
                EffectGeometry.glowQuad(ink,poses.last().pose(),new float[]{-.018f,.28f,0},new float[]{.018f,.28f,0},new float[]{.018f,.52f,0},new float[]{-.018f,.52f,0},color,color);
                poses.popPose();
            }
            poses.popPose();
        }
    }
    private static float[] point(float radius,double angle){return new float[]{(float)Math.cos(angle)*radius,(float)Math.sin(angle)*radius,0};}
    @SubscribeEvent public static void gui(RenderGuiEvent.Post event){
        var mc=Minecraft.getInstance();if(mc.player==null||mc.options.hideGui||!MeteorTargeting.enabled(mc.player)||mc.screen!=null||DomainDirector.active()||DivineCGDirector.active()||OfferingIntroDirector.active())return;
        int width=event.getGuiGraphics().guiWidth(),height=event.getGuiGraphics().guiHeight(),x=width/2,y=height/2+21;
        boolean entity=preview.entity()!=null,locked=previewLocked();
        String key=!preview.valid()?"meteor.no_target":locked?"meteor.locked":entity?"meteor.locking":"meteor.area";
        var label=Component.translatable("hud.sourceofmystery."+key);
        int color=locked?RED:WHITE;
        event.getGuiGraphics().drawCenteredString(mc.font,label,x,y,color);
        if(entity){event.getGuiGraphics().fill(x-35,y+12,x+35,y+15,0x80000000);event.getGuiGraphics().fill(x-35,y+12,x-35+Math.round(progress()*70f/40),y+15,0xff000000|color);}
    }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event){world=null;confirmed=null;preview=new MeteorTargeting.Pick(null,null);FLIGHTS.clear();}
}
