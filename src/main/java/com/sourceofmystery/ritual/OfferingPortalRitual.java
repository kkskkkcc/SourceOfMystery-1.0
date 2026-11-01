package com.sourceofmystery.ritual;

import com.sourceofmystery.SourceOfMystery;
import com.sourceofmystery.entity.ModEntities;
import com.sourceofmystery.entity.OutsiderEntity;
import com.sourceofmystery.item.ModItems;
import com.sourceofmystery.network.DimensionalOfferingPacket;
import com.sourceofmystery.network.ModNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Evoker;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

import java.util.*;

/** Server clock for the Dark Dimensional Offering portal and its one-player intro. */
@Mod.EventBusSubscriber(modid=SourceOfMystery.MOD_ID)
public final class OfferingPortalRitual {
    /** The cinematic is deliberately driven by one server clock (20 ticks/s). */
    public static final int APPROACH_TICKS = 60;
    public static final int PORTAL_SHOT_TICKS = 60;
    public static final int BREAK_TICK = PORTAL_SHOT_TICKS;
    public static final int FRAME_END_TICK = 160;
    public static final int FACE_END_TICK = 200;
    public static final int CONVERGE_END_TICK = 200;
    public static final int RIFT_END_TICK = 260;
    public static final int BURST_END_TICK = 280;
    public static final int EMERGE_START_TICK = BURST_END_TICK;
    public static final int EMERGE_END_TICK = 440;
    public static final int HOLD_END_TICK = 480;
    public static final int SNAP_TICK = 498;
    public static final int SEAL_TICK = 503;
    public static final int PORTAL_ERASE_TICK = 518;
    public static final int GLANCE_END_TICK = 560;
    public static final int INTRO_END_TICK = 640;
    public static final int TOTAL_TICKS = INTRO_END_TICK;
    private static final Map<UUID,Active> ACTIVE=new HashMap<>();
    private OfferingPortalRitual(){}

    public static boolean tryStart(Level level,BlockPos clicked,Player player,InteractionHand hand,ItemStack stack){
        if(!(level instanceof ServerLevel server)||!(player instanceof ServerPlayer owner))return false;
        if(ACTIVE.containsKey(owner.getUUID()))return false;
        Frame frame=findFrame(server,clicked);if(frame==null)return false;
        if(ACTIVE.values().stream().anyMatch(a->a.level()==server&&a.frame.blocks().contains(clicked)))return false;
        OutsiderEntity boss=new OutsiderEntity(ModEntities.OUTSIDER.get(),server);
        Vec3 center=frame.center();
        Vec3 normal=frame.axis?new Vec3(owner.getX()<center.x?-1:1,0,0):new Vec3(0,0,owner.getZ()<center.z?-1:1);
        float yaw=(float)Math.toDegrees(Math.atan2(-normal.x,normal.z));
        Vec3 origin=new Vec3(center.x,frame.y0+1.04,center.z);
        Vec3 spawn=origin.add(normal.scale(entranceDistance(0)));
        boss.moveTo(spawn.x,spawn.y,spawn.z,yaw,0);boss.yBodyRot=yaw;boss.yHeadRot=yaw;
        boss.setOfferingIntro(true);
        if(!server.addFreshEntity(boss))return false;
        if(!owner.getAbilities().instabuild)stack.shrink(1);
        Active active=new Active(owner.getUUID(),boss.getId(),frame,server.getGameTime(),normal,origin,yaw);ACTIVE.put(owner.getUUID(),active);
        ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(()->owner),new DimensionalOfferingPacket(
                boss.getId(),frame.x0,frame.y0,frame.z0,frame.axis,TOTAL_TICKS));
        owner.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.sourceofmystery.offering.started"),true);
        return true;
    }

    @SubscribeEvent public static void death(LivingDeathEvent event){
        LivingEntity victim=event.getEntity();
        if(!(victim instanceof Evoker)||victim.level().isClientSide||!victim.level().getGameRules().getBoolean(net.minecraft.world.level.GameRules.RULE_DOMOBLOOT))return;
        if(!(event.getSource().getEntity() instanceof Player killer)||!killer.isAlive())return;
        if(victim.getRandom().nextFloat()>=.30f)return;
        victim.spawnAtLocation(new ItemStack(ModItems.DARK_DIMENSIONAL_OFFERING.get()),0.25f);
    }

    @SubscribeEvent public static void serverTick(TickEvent.ServerTickEvent event){
        if(event.phase!=TickEvent.Phase.END||ACTIVE.isEmpty())return;
        Iterator<Map.Entry<UUID,Active>> it=ACTIVE.entrySet().iterator();
        while(it.hasNext()){
            Active a=it.next().getValue();ServerLevel level=a.level();int age=(int)(level.getGameTime()-a.start);
            ServerPlayer player=level.getServer().getPlayerList().getPlayer(a.player);
            net.minecraft.world.entity.Entity raw=level.getEntity(a.bossId);
            OutsiderEntity boss=raw instanceof OutsiderEntity outsider?outsider:null;
            if(player==null||player.level()!=level||!player.isAlive()||boss==null||age> TOTAL_TICKS+20){
                if(boss!=null)boss.setOfferingIntro(false);
                it.remove();
                continue;
            }
            if(age==BREAK_TICK){for(BlockPos pos:a.frame.blocks())if(level.getBlockState(pos).is(Blocks.OBSIDIAN))level.removeBlock(pos,false);}
            net.minecraft.sounds.SoundEvent cue=null;
            if(age==160)cue=com.sourceofmystery.sound.ModSounds.ARCHMAGE_VOID_WAKE.get();
            if(age==260)cue=com.sourceofmystery.sound.ModSounds.ARCHMAGE_VOID_BREAK.get();
            if(age==318||age==364||age==404||age==440)cue=com.sourceofmystery.sound.ModSounds.ARCHMAGE_HEEL_STEP.get();
            if(age==SNAP_TICK)cue=com.sourceofmystery.sound.ModSounds.ARCHMAGE_FINGER_SNAP.get();
            if(age==SEAL_TICK)cue=com.sourceofmystery.sound.ModSounds.ARCHMAGE_VOID_SEAL.get();
            if(cue!=null)level.playSound(null,boss.getX(),boss.getY()+1,boss.getZ(),cue,net.minecraft.sounds.SoundSource.HOSTILE,1.2f,1f);
            if(age<INTRO_END_TICK){
                boss.setOfferingIntro(true);
                Vec3 p=a.origin.add(a.normal.scale(entranceDistance(age/20f)));
                boss.setPos(p.x,p.y,p.z);boss.setYRot(a.yaw);boss.yBodyRot=a.yaw;boss.yHeadRot=a.yaw;
            }
            if(age==PORTAL_ERASE_TICK){
                // The floating frame is a client-side cinematic prop. The
                // actual obsidian is erased only after the caster has fully
                // stepped through and raised the small void sigil.
                for(BlockPos pos:a.frame.blocks())if(level.getBlockState(pos).is(Blocks.OBSIDIAN))level.removeBlock(pos,false);
            }
            if(age==INTRO_END_TICK){boss.setOfferingIntro(false);boss.setNoGravity(true);}
            if(age>=TOTAL_TICKS){it.remove();}
        }
    }

    private static Frame findFrame(ServerLevel level,BlockPos clicked){
        for(boolean axis:new boolean[]{false,true}){
            int minPlane=axis?clicked.getZ()-3:clicked.getX()-3;
            int maxPlane=axis?clicked.getZ():clicked.getX();
            for(int plane=minPlane;plane<=maxPlane;plane++)for(int y=clicked.getY()-4;y<=clicked.getY();y++){
            Frame f=axis?new Frame(clicked.getX(),y,plane,axis,level):new Frame(plane,y,clicked.getZ(),axis,level);
            boolean hit=false,ok=true;
            for(BlockPos p:f.blocks()){if(level.getBlockState(p).is(Blocks.OBSIDIAN))hit|=p.equals(clicked);else{ok=false;break;}}
            if(hit&&ok){
                boolean empty=true;
                for(BlockPos p:f.inner())if(!level.getBlockState(p).isAir()){empty=false;break;}
                if(empty)return f;
            }
            }
        }
        return null;
    }

    public static float entranceDistance(float seconds){
        float u=net.minecraft.util.Mth.clamp((seconds-EMERGE_START_TICK/20f)/8f,0,1);
        return -.45f+2.20f*u*u*(3-2*u);
    }
    /** The authored pelvis balances over the supports, independently of entity-root travel. */
    public static float entranceBodyDistance(float seconds){
        float t=seconds-EMERGE_START_TICK/20f;
        float left=.65f*step(t,0,1.9f)+1.30f*step(t,4,6.2f);
        float right=1.30f*step(t,1.85f,4.2f)+.90f*step(t,6.15f,8);
        return -.45f+(left+right)*.5f;
    }
    private static float step(float t,float a,float b){
        float u=net.minecraft.util.Mth.clamp((t-a)/(b-a),0,1);
        return u*u*u*(10+u*(-15+6*u));
    }
    @SubscribeEvent public static void stopped(net.minecraftforge.event.server.ServerStoppedEvent event){ACTIVE.clear();}
    private record Active(UUID player,int bossId,Frame frame,long start,Vec3 normal,Vec3 origin,float yaw){ServerLevel level(){return frame.level;}}
    public static final class Frame {
        final int x0,y0,z0;final boolean axis;private final ServerLevel level;
        Frame(int x,int y,int z,boolean axis){x0=x;y0=y;z0=z;this.axis=axis;level=null;}
        Frame(int x,int y,int z,boolean axis,ServerLevel level){x0=x;y0=y;z0=z;this.axis=axis;this.level=level;}
        List<BlockPos> blocks(){List<BlockPos> out=new ArrayList<>();for(int u=0;u<4;u++)for(int v=0;v<5;v++)if(u==0||u==3||v==0||v==4)out.add(pos(u,v));return out;}
        List<BlockPos> inner(){List<BlockPos> out=new ArrayList<>();for(int u=1;u<3;u++)for(int v=1;v<4;v++)out.add(pos(u,v));return out;}
        BlockPos pos(int u,int v){return axis?new BlockPos(x0,y0+v,z0+u):new BlockPos(x0+u,y0+v,z0);}
        Vec3 center(){return axis?new Vec3(x0+.5,y0+3.45,z0+2):new Vec3(x0+2,y0+3.45,z0+.5);}
    }
}
