package com.sourceofmystery.client;

import com.mojang.blaze3d.vertex.*;
import com.sourceofmystery.item.DimensionBreakerStaffItem;
import com.sourceofmystery.network.*;
import com.sourceofmystery.staff.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.resources.sounds.*;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.*;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod;
import java.util.*;

@Mod.EventBusSubscriber(modid="sourceofmystery",value=Dist.CLIENT)
public final class WeatherVisuals {
    public record View(WeatherSyncPacket.Storm data,Vec3 previous,long stamp){
        public Vec3 center(float partial){return previous.lerp(data.center(),Math.min(1,(elapsed()+partial)/5d));}
        public long elapsed(){return world==null?0:Math.max(0,world.getGameTime()-stamp);}
        public float age(float partial){return data.age()+elapsed()+partial;}
        public float remaining(float partial){return data.remaining()-elapsed()-partial;}
        public TornadoVariant variant(){return TornadoVariant.index(data.variant());}
        public float strength(float partial){return Math.max(0,Math.min(1,age(partial)/StaffWeather.FORM_TICKS)*Math.min(1,remaining(partial)/StaffWeather.FADE_TICKS));}
    }
    private static ClientLevel world;private static final Map<UUID,View> STORMS=new LinkedHashMap<>();
    private static MultiBufferSource.BufferSource buffers;private static boolean awaitRelease;private static Wind wind;
    private WeatherVisuals(){}
    public static Collection<View> storms(){return List.copyOf(STORMS.values());}
    public static View localStorm(){var p=Minecraft.getInstance().player;if(p==null)return null;return STORMS.values().stream().filter(s->s.data.owner().equals(p.getUUID())).findFirst().orElse(null);}
    public static boolean windActive(){return wind!=null;}
    public static boolean localCloudShelf(){
        var mc=Minecraft.getInstance();if(mc.level!=world||mc.getCameraEntity()==null)return false;Vec3 eye=mc.getCameraEntity().position();
        return STORMS.values().stream().anyMatch(s->s.strength(0)>.02f&&Math.pow(eye.x-s.data.center().x,2)+Math.pow(eye.z-s.data.center().z,2)<280*280);
    }
    public static void castFinished(){awaitRelease=true;}
    public static void receive(WeatherSyncPacket packet){
        var mc=Minecraft.getInstance();if(mc.level==null)return;if(world!=mc.level){reset();world=mc.level;}
        Map<UUID,View> next=new LinkedHashMap<>();for(var s:packet.storms()){var old=STORMS.get(s.token());next.put(s.token(),new View(s,old==null?s.center():old.center(mc.getFrameTime()),world.getGameTime()));}STORMS.clear();STORMS.putAll(next);
    }
    private static void reset(){STORMS.clear();awaitRelease=false;if(wind!=null)Minecraft.getInstance().getSoundManager().stop(wind);wind=null;}
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void click(InputEvent.InteractionKeyMappingTriggered e){
        var mc=Minecraft.getInstance();if(mc.player==null||!e.isUseItem())return;
        var stack=mc.player.getMainHandItem();if(!(stack.getItem() instanceof DimensionBreakerStaffItem)||StaffSpell.selected(stack)!=StaffSpell.WEATHER_CONTROL){stack=mc.player.getOffhandItem();if(!(stack.getItem() instanceof DimensionBreakerStaffItem)||StaffSpell.selected(stack)!=StaffSpell.WEATHER_CONTROL)return;}
        if(awaitRelease){e.setCanceled(true);e.setSwingHand(false);return;}
        var s=localStorm();if(s!=null&&!mc.player.isShiftKeyDown()&&WeatherType.selected(stack)==WeatherType.TORNADO){ModNetwork.CHANNEL.sendToServer(new WeatherStopPacket(s.data.token()));awaitRelease=true;e.setCanceled(true);e.setSwingHand(false);}
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent e){
        if(e.phase!=TickEvent.Phase.END)return;var mc=Minecraft.getInstance();if(world!=mc.level){reset();world=mc.level;}
        if(world==null||mc.player==null||mc.isPaused())return;if(!mc.options.keyUse.isDown())awaitRelease=false;
        STORMS.values().removeIf(s->s.remaining(0)<=0);
        Vec3 eye=mc.getCameraEntity()==null?mc.player.position():mc.getCameraEntity().position();
        var nearest=STORMS.values().stream().filter(s->s.center(1).distanceToSqr(eye)<300*300).min(Comparator.comparingDouble(s->s.center(1).distanceToSqr(eye))).orElse(null);
        if(nearest==null){if(wind!=null)mc.getSoundManager().stop(wind);wind=null;}
        else if(wind==null||wind.variant!=nearest.variant()){if(wind!=null)mc.getSoundManager().stop(wind);wind=new Wind(nearest.variant());mc.getSoundManager().play(wind);}
        if(wind!=null)wind.volumeFor(nearest,eye);
    }
    private static final class Wind extends AbstractTickableSoundInstance {
        final TornadoVariant variant;
        Wind(TornadoVariant variant){super(StaffSounds.wind(variant),SoundSource.WEATHER,RandomSource.create());this.variant=variant;looping=true;delay=0;relative=true;attenuation=SoundInstance.Attenuation.NONE;volume=.01f;pitch=1;}
        void volumeFor(View s,Vec3 eye){volume=s==null?0:(float)Math.max(0,1-s.center(1).distanceTo(eye)/300)*s.strength(0)*.9f;}
        @Override public void tick(){if(world==null||STORMS.isEmpty())stop();}
    }
    @SubscribeEvent public static void render(RenderLevelStageEvent e){
        if(e.getStage()!=RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS||world==null||world!=Minecraft.getInstance().level||OutsiderShaderCompat.shadowPass()||STORMS.isEmpty())return;
        if(buffers==null)buffers=MultiBufferSource.immediate(new BufferBuilder(1<<21));
        var iris=OutsiderShaderCompat.active()?net.minecraft.client.renderer.GameRenderer.getRendertypeEntityTranslucentEmissiveShader():null;
        if(iris!=null)iris.apply();
        try{
        Vec3 camera=e.getCamera().getPosition();int count=0;
        for(var s:STORMS.values().stream().sorted(Comparator.comparingDouble(s->s.data.center().distanceToSqr(camera))).toList()){
            if(s.center(e.getPartialTick()).distanceToSqr(camera)>640*640)continue;
            var p=e.getPoseStack();p.pushPose();Vec3 center=s.center(e.getPartialTick());p.translate(center.x-camera.x,center.y-camera.y,center.z-camera.z);
            TornadoGeometry.render(p,buffers,s,e.getPartialTick(),camera.subtract(center));p.popPose();buffers.endBatch();if(++count>=4)break;
        }
        }finally{if(iris!=null)iris.clear();}
    }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut e){reset();world=null;}
}
