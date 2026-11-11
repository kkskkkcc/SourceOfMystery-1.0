package com.sourceofmystery.client;

import com.sourceofmystery.network.*;
import com.sourceofmystery.staff.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.*;
import net.minecraft.client.renderer.*;
import net.minecraft.sounds.*;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.*;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.*;

@Mod.EventBusSubscriber(modid="sourceofmystery",value=Dist.CLIENT)
public final class DomainVisuals {
    public record View(DomainSyncPacket.Field data,long stamp){
        public DomainType type(){return DomainType.index(data.type());}
        public float age(float partial){return data.age()+elapsed()+partial;}
        public long elapsed(){return world==null?0:Math.max(0,world.getGameTime()-stamp);}
        public Vec3 center(float partial){var p=world==null?null:world.getPlayerByUUID(data.owner());return type().follows&&p!=null?p.getPosition(partial):data.center();}
        public float yaw(){var p=world==null?null:world.getPlayerByUUID(data.owner());return type().follows&&p!=null?p.getYRot():data.yaw();}
        public boolean contains(Entity entity){
            if(entity.getUUID().equals(data.owner())||entity.isSpectator())return false;
            Vec3 delta=entity.getBoundingBox().getCenter().subtract(center(1));
            if(delta.lengthSqr()>type().radius*type().radius)return false;
            return type()!=DomainType.EXECUTION||StaffDomains.executionTarget(entity,data.owner());
        }
    }
    private static ClientLevel world;
    private static final Map<UUID,View> FIELDS=new LinkedHashMap<>();
    public static final int SLASH_TICKS=8;
    public record Strike(UUID field,UUID target,int entityId,long stamp,int pulse){
        public float age(float partial){return world==null?SLASH_TICKS:Math.max(0,world.getGameTime()-stamp)+partial;}
    }
    private record StrikeKey(UUID field,UUID target){}
    private static final Map<StrikeKey,Strike> STRIKES=new LinkedHashMap<>();
    private static final Map<UUID,Integer> LAST_PULSE=new HashMap<>();
    public static Collection<Strike> strikes(){return List.copyOf(STRIKES.values());}
    public static void receiveSlash(DomainSlashPacket packet){
        var mc=Minecraft.getInstance();
        if(world==null||mc.level!=world||!world.dimension().location().equals(packet.dimension()))return;
        var field=FIELDS.get(packet.field());
        if(field==null||field.type()!=DomainType.EXECUTION||packet.pulse()<LAST_PULSE.getOrDefault(packet.field(),-1))return;
        boolean sound=packet.pulse()!=LAST_PULSE.getOrDefault(packet.field(),-1),added=false;
        LAST_PULSE.put(packet.field(),packet.pulse());
        for(var target:packet.targets()){
            var entity=world.getEntity(target.id());
            if(!(entity instanceof LivingEntity)||entity.isRemoved()||!entity.getUUID().equals(target.uuid())||entity.getUUID().equals(field.data.owner()))continue;
            var key=new StrikeKey(packet.field(),target.uuid());
            var old=STRIKES.get(key);if(old!=null&&old.pulse()>=packet.pulse())continue;
            STRIKES.put(key,new Strike(packet.field(),target.uuid(),target.id(),world.getGameTime(),packet.pulse()));added=true;
        }
        while(STRIKES.size()>2048)STRIKES.remove(STRIKES.keySet().iterator().next());
        if(added&&sound&&mc.player!=null&&field.center(1).distanceToSqr(mc.player.position())<4900)
            world.playLocalSound(mc.player.getX(),mc.player.getY(),mc.player.getZ(),SoundEvents.PLAYER_ATTACK_SWEEP,SoundSource.PLAYERS,.32f,.72f+world.random.nextFloat()*.3f,false);
    }
    private static MultiBufferSource.BufferSource buffers;
    private static Wind wind;
    private static boolean awaitRelease;
    private static final DomainActor ACTOR=new DomainActor();
    private DomainVisuals(){}
    public static void castFinished(){awaitRelease=true;}
    public static boolean windActive(){return wind!=null;}
    @SubscribeEvent(priority=net.minecraftforge.eventbus.api.EventPriority.HIGHEST)
    public static void click(InputEvent.InteractionKeyMappingTriggered e){
        var mc=Minecraft.getInstance();if(mc.player==null||!e.isUseItem())return;
        boolean staff=mc.player.getMainHandItem().getItem() instanceof com.sourceofmystery.item.DimensionBreakerStaffItem
                ||mc.player.getOffhandItem().getItem() instanceof com.sourceofmystery.item.DimensionBreakerStaffItem;
        if(!staff)return;
        if(awaitRelease){e.setCanceled(true);e.setSwingHand(false);return;}
        var local=localField();if(local!=null&&!mc.player.isShiftKeyDown()){
            ModNetwork.CHANNEL.sendToServer(new DomainStopPacket(local.data.token()));awaitRelease=true;e.setCanceled(true);e.setSwingHand(false);
        }
    }
    public static Collection<View> fields(){return List.copyOf(FIELDS.values());}
    public static View localField(){var p=Minecraft.getInstance().player;if(p==null)return null;for(var f:FIELDS.values())if(f.data.owner().equals(p.getUUID()))return f;return null;}
    public static boolean hasExecutionTargets(View field){
        if(world==null||field.type()!=DomainType.EXECUTION)return false;
        for(var entity:world.entitiesForRendering())if(field.contains(entity))return true;
        return false;
    }
    public static boolean constrained(Entity e){
        for(var f:FIELDS.values()){
            if(f.type()==DomainType.EXECUTION&&f.data.owner().equals(e.getUUID())&&f.age(0)<StaffDomains.CINEMATIC_TICKS)return true;
            if(f.contains(e)&&(f.type()==DomainType.FROST||f.type()==DomainType.BINDING||f.type()==DomainType.DARKNESS))return true;
        }
        return false;
    }
    public static void receive(DomainSyncPacket packet){
        var mc=Minecraft.getInstance();if(mc.level==null)return;
        if(world!=mc.level){reset();world=mc.level;}
        FIELDS.clear();for(var f:packet.fields())FIELDS.put(f.token(),new View(f,world.getGameTime()));
        STRIKES.keySet().removeIf(k->!FIELDS.containsKey(k.field()));LAST_PULSE.keySet().retainAll(FIELDS.keySet());
        var local=localField();
        if(local!=null&&local.type()==DomainType.EXECUTION&&local.age(0)<StaffDomains.CINEMATIC_TICKS)DomainDirector.start(local);
        else if(DomainDirector.active())DomainDirector.stop();
    }
    public static void reset(){
        FIELDS.clear();STRIKES.clear();LAST_PULSE.clear();world=null;awaitRelease=false;DomainDirector.stop();
        if(wind!=null)Minecraft.getInstance().getSoundManager().stop(wind);wind=null;
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event){
        if(event.phase!=TickEvent.Phase.END)return;var mc=Minecraft.getInstance();
        if(world!=mc.level){reset();world=mc.level;}if(world==null||mc.player==null||mc.isPaused())return;
        if(!mc.options.keyUse.isDown())awaitRelease=false;
        FIELDS.values().removeIf(f->f.elapsed()>=f.data.remaining());
        STRIKES.values().removeIf(hit->hit.age(0)>=SLASH_TICKS||!FIELDS.containsKey(hit.field()));
        LAST_PULSE.keySet().retainAll(FIELDS.keySet());
        boolean frost=FIELDS.values().stream().anyMatch(f->f.type()==DomainType.FROST&&f.center(1).distanceToSqr(mc.player.position())<(f.type().radius+10)*(f.type().radius+10));
        if(frost&&wind==null){wind=new Wind();mc.getSoundManager().play(wind);}
        if(!frost&&wind!=null){mc.getSoundManager().stop(wind);wind=null;}
        if(constrained(mc.player)){
            mc.player.setDeltaMovement(Vec3.ZERO);mc.player.fallDistance=0;
            boolean rooted=DomainDirector.active()||FIELDS.values().stream().anyMatch(f->f.contains(mc.player)&&(f.type()==DomainType.FROST||f.type()==DomainType.BINDING));
            for(var f:FIELDS.values())if(!rooted&&f.type()==DomainType.DARKNESS&&f.contains(mc.player)){
                Vec3 delta=f.center(1).add(0,20,0).subtract(mc.player.getBoundingBox().getCenter());
                // Match the server's five blocks/second prediction, rather than waiting for rubber-band corrections.
                mc.player.setPos(mc.player.position().add(delta.normalize().scale(Math.min(.25,delta.length()))));break;
            }
        }

    }
    private static final class Wind extends AbstractTickableSoundInstance {
        Wind(){super(SoundEvents.AMBIENT_SOUL_SAND_VALLEY_LOOP.value(),SoundSource.PLAYERS,RandomSource.create());looping=true;delay=0;relative=true;attenuation=SoundInstance.Attenuation.NONE;volume=.72f;pitch=1.12f;}
        @Override public void tick(){if(world==null||FIELDS.isEmpty())stop();}
    }
    @SubscribeEvent public static void input(MovementInputUpdateEvent event){
        if(!constrained(event.getEntity()))return;var i=event.getInput();i.forwardImpulse=i.leftImpulse=0;i.up=i.down=i.left=i.right=i.jumping=i.shiftKeyDown=false;
    }
    @SubscribeEvent public static void player(RenderPlayerEvent.Pre event){
        for(var f:FIELDS.values())if(f.type()==DomainType.EXECUTION&&f.age(event.getPartialTick())<StaffDomains.CINEMATIC_TICKS&&f.data.owner().equals(event.getEntity().getUUID())){event.setCanceled(true);return;}
    }
    @SubscribeEvent public static void render(RenderLevelStageEvent event){
        if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_ENTITIES||world==null||OutsiderShaderCompat.shadowPass())return;
        var mc=Minecraft.getInstance();if(mc.level!=world)return;
        if(buffers==null)buffers=MultiBufferSource.immediate(new com.mojang.blaze3d.vertex.BufferBuilder(1<<20));
        var poses=event.getPoseStack();var camera=event.getCamera().getPosition();float partial=event.getPartialTick();
        for(var f:FIELDS.values()){
            Vec3 center=f.center(partial);if(camera.distanceToSqr(center)>(f.type().radius+110)*(f.type().radius+110))continue;
            poses.pushPose();poses.translate(center.x-camera.x,center.y-camera.y,center.z-camera.z);
            DomainGeometry.field(poses,buffers,f,partial,camera.subtract(center));poses.popPose();
            if(f.type()==DomainType.EXECUTION&&f.age(partial)<StaffDomains.CINEMATIC_TICKS){
                var owner=world.getPlayerByUUID(f.data.owner());
                if(owner instanceof net.minecraft.client.player.AbstractClientPlayer actor&&camera.distanceToSqr(owner.getEyePosition(partial))>.3){
                    var p=owner.getPosition(partial);poses.pushPose();poses.translate(p.x-camera.x,p.y-camera.y,p.z-camera.z);
                    ACTOR.render(poses,buffers,actor,f.age(partial)/20,partial,f.yaw());poses.popPose();
                }
            }
        }
        int count=0;
        for(var entity:world.entitiesForRendering()){
            if(entity.isRemoved()||entity.position().distanceToSqr(camera)>14400)continue;
            for(var f:FIELDS.values())if((f.type()==DomainType.FROST||f.type()==DomainType.BINDING)&&f.contains(entity)){
                Vec3 p=entity.getPosition(partial);poses.pushPose();poses.translate(p.x-camera.x,p.y-camera.y,p.z-camera.z);
                if(f.type()==DomainType.FROST)DomainGeometry.ice(poses,buffers,entity);
                if(f.type()==DomainType.BINDING)DomainGeometry.shackles(poses,buffers,entity,f.age(partial));
                poses.popPose();if(++count>=256)break;
            }
            if(count>=256)break;
        }
        for(var hit:STRIKES.values()){
            if(count>=256)break;
            var entity=world.getEntity(hit.entityId());float age=hit.age(partial);
            if(!(entity instanceof LivingEntity)||entity.isRemoved()||!entity.getUUID().equals(hit.target())||age>=SLASH_TICKS||!FIELDS.containsKey(hit.field())||entity.position().distanceToSqr(camera)>14400)continue;
            Vec3 at=entity.getPosition(partial);poses.pushPose();poses.translate(at.x-camera.x,at.y-camera.y,at.z-camera.z);
            DomainGeometry.slashes(poses,buffers,entity,age,hit.pulse());poses.popPose();count++;
        }
        buffers.endBatch();
    }
}
