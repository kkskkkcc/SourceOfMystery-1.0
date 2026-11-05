package com.sourceofmystery.client;

import com.mojang.math.Axis;
import com.sourceofmystery.item.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.*;

/** Persistent visual state; movement changes opacity only, never server combat or satellite lifetime. */
@Mod.EventBusSubscriber(modid="sourceofmystery",value=Dist.CLIENT)
public final class DivineSatelliteRenderer {
    private static final Map<UUID,State> STATES=new HashMap<>();
    private static final DivineSatelliteVisual[] INNER={new DivineSatelliteVisual("white_star",true),new DivineSatelliteVisual("black_star",true)};
    private static ClientLevel world;
    private static final class State {
        Vec3 last;float previous,alpha;int still;
        State(Vec3 position){last=position;}
    }
    public static float visibility(UUID player){State state=STATES.get(player);return state==null?0:state.alpha;}
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event){
        if(event.phase!=TickEvent.Phase.END)return;var mc=Minecraft.getInstance();
        if(world!=mc.level){STATES.clear();world=mc.level;}
        if(world==null||mc.isPaused())return;
        Set<UUID> present=new HashSet<>();
        for(Player player:world.players()){
            if(!wearsDivine(player))continue;present.add(player.getUUID());
            State state=STATES.computeIfAbsent(player.getUUID(),id->new State(player.position()));
            boolean moving=player.position().distanceToSqr(state.last)>.0001;
            state.last=player.position();state.previous=state.alpha;state.still=moving?0:state.still+1;
            float target=state.still>=3?1:0;
            state.alpha=Mth.clamp(state.alpha+(target>state.alpha?1f/24:target<state.alpha?-1f/16:0),0,1);
        }
        STATES.keySet().removeIf(id->!present.contains(id));
    }
    private static boolean wearsDivine(Player player){return player.isAlive()&&!player.isSpectator()
            &&ChestplateEffectHandler.getChestplateMaterial(player)==MysteryArmorMaterial.DIVINE_BLESSING;}
    @SubscribeEvent public static void render(RenderLevelStageEvent event){
        if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_ENTITIES)return;
        var mc=Minecraft.getInstance();if(mc.level==null)return;
        var poses=event.getPoseStack();var buffers=mc.renderBuffers().bufferSource();var camera=event.getCamera().getPosition();
        float partial=event.getPartialTick();double time=mc.level.getGameTime()+partial;
        boolean rendered=false;
        poses.pushPose();poses.translate(-camera.x,-camera.y,-camera.z);
        try{
            for(Player player:mc.level.players()){
                if(!wearsDivine(player)||player.distanceToSqr(camera)>4096)continue;
                if(player==mc.player&&mc.getCameraEntity()==player&&mc.options.getCameraType().isFirstPerson())continue;
                State state=STATES.get(player.getUUID());if(state==null)continue;
                float alpha=Mth.lerp(partial,state.previous,state.alpha);if(alpha<=.001f)continue;
                rendered=true;
                Vec3 origin=new Vec3(Mth.lerp(partial,player.xo,player.getX()),Mth.lerp(partial,player.yo,player.getY()),Mth.lerp(partial,player.zo,player.getZ()));
                for(int i=0;i<2;i++){
                    double angle=time*.07+i*Math.PI;
                    Vec3 position=origin.add(Math.cos(angle),1.2+.09*Math.sin(time*.045+i*Math.PI),Math.sin(angle));
                    float twinkle=.66f+.34f*(float)Math.pow(.5+.5*Math.sin(time*.19+i*2.3),2);
                    poses.pushPose();poses.translate(position.x,position.y,position.z);
                    poses.mulPose(Axis.YP.rotationDegrees((float)(time*1.8+i*90)));
                    INNER[i].render(poses,buffers,alpha*twinkle,time);poses.popPose();
                }
            }
            if(rendered)buffers.endBatch();
        }finally{poses.popPose();}
    }
}
