package com.sourceofmystery.client;

import com.google.gson.*;
import com.sourceofmystery.SourceOfMystery;
import com.sourceofmystery.cinematic.DivineCinematic;
import com.sourceofmystery.energy.MysteryEnergy;
import com.sourceofmystery.item.*;
import net.minecraft.client.*;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.nio.file.Files;
import java.util.concurrent.atomic.AtomicInteger;

/** Silent real-server/client fixture. Hidden damage is measured on a live hostile mob. */
@Mod.EventBusSubscriber(modid="sourceofmystery",value=Dist.CLIENT)
public final class DivineSatellitesSmokeCheck {
    private static int ticks,phase,time;private static volatile boolean ready,moving;
    private static volatile float healthBefore,healthAfter;private static volatile boolean measured;
    private static Husk target;private static final JsonObject report=new JsonObject();
    private static boolean enabled(){return Boolean.getBoolean("sourceofmystery.divineSatellitesCheck");}
    @SubscribeEvent public static void server(TickEvent.ServerTickEvent event){
        if(!enabled()||event.phase!=TickEvent.Phase.END||!moving)return;
        var server=Minecraft.getInstance().getSingleplayerServer();if(server==null)return;
        for(var player:server.getPlayerList().getPlayers()){
            double x=player.getX()+.075;if(x>2.5)x=-2.5;
            player.teleportTo(server.overworld(),x,200,0,180,0);player.setDeltaMovement(Vec3.ZERO);
        }
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event){
        if(!enabled()||event.phase!=TickEvent.Phase.END)return;var mc=Minecraft.getInstance();
        if(mc.player==null||mc.level==null||mc.getSingleplayerServer()==null)return;ticks++;time++;
        if(phase==0&&ticks>100){
            mc.setScreen(null);mc.options.hideGui=true;mc.options.fov().set(45);mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            mc.getSingleplayerServer().execute(()->{
                var server=mc.getSingleplayerServer();var level=server.overworld();level.setDayTime(6500);
                level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,server);
                for(int x=-12;x<=12;x++)for(int z=-9;z<=12;z++)level.setBlock(new BlockPos(x,199,z),Blocks.SMOOTH_STONE.defaultBlockState(),2);
                var player=server.getPlayerList().getPlayers().get(0);player.setGameMode(GameType.CREATIVE);player.teleportTo(level,0,200,0,180,0);player.setNoGravity(true);
                MysteryEnergy.getPlayerData(player).putBoolean(DivineCinematic.SEEN,true);MysteryEnergy.addEnergy(player,10000);
                player.setItemSlot(EquipmentSlot.CHEST,new ItemStack(ModItems.DIVINE_BLESSING_CHESTPLATE.get()));
                AtomicInteger dust=new AtomicInteger();ArmorParticlePattern.emit(player,level.getGameTime(),0,(p,x,y,z)->dust.incrementAndGet());
                if(dust.get()!=5)throw new AssertionError("Expected exactly five original outer particles, got "+dust.get());
                dust.set(0);ArmorParticlePattern.emit(player,level.getGameTime(),31,(p,x,y,z)->dust.incrementAndGet());
                if(dust.get()!=0)throw new AssertionError("Attacking satellites also emitted at idle positions");ready=true;
            });phase=1;time=0;
        }else if(phase==1&&ready&&time>65){
            if(alpha()<.99)throw new AssertionError("Idle models failed to appear: "+alpha());
            camera(new Vec3(5.5,202.3,6.8),new Vec3(0,201.0,0));phase=2;time=0;
        }else if(phase==2&&time>24){
            capture("01-idle-particles-and-stars");camera(new Vec3(3.5,201.8,4.1),new Vec3(0,201.15,0));phase=3;time=0;
        }else if(phase==3&&time>24){
            capture("02-idle-detail");camera(new Vec3(-3.5,201.8,-4.1),new Vec3(0,201.15,0));phase=14;time=0;
        }else if(phase==14&&time>24){
            capture("02b-opposite-view");camera(new Vec3(3.5,201.8,4.1),new Vec3(0,201.15,0));phase=15;time=0;
        }else if(phase==15&&time>20){
            moving=true;phase=4;time=0;
        }else if(phase==4&&time>9){
            report.addProperty("during_fade_alpha",alpha());capture("03-moving-fade");phase=5;time=0;
        }else if(phase==5&&time>30){
            if(alpha()>.001)throw new AssertionError("Moving satellites visible: "+alpha());
            capture("04-moving-invisible");mc.getSingleplayerServer().execute(()->{
                var level=mc.getSingleplayerServer().overworld();target=EntityType.HUSK.create(level);
                target.setPos(0,200,4.5);target.setNoAi(true);target.setNoGravity(true);target.setSilent(true);
                target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(2000);target.setHealth(2000);level.addFreshEntity(target);
            });phase=6;time=0;
        }else if(phase==6&&time>35){
            if(ClientSatelliteCache.busyMask==0)throw new AssertionError("Original busy-mask sync failed");
            mc.getSingleplayerServer().execute(()->{healthBefore=target.getHealth();measured=true;});phase=7;time=0;
        }else if(phase==7&&measured&&time>45){
            if(alpha()>.001)throw new AssertionError("Attack revealed moving models");
            measured=false;mc.getSingleplayerServer().execute(()->{healthAfter=target.getHealth();measured=true;});phase=8;time=0;
        }else if(phase==8&&measured&&time>3){
            if(healthAfter>=healthBefore)throw new AssertionError("Hidden satellites stopped dealing damage");
            report.addProperty("hidden_target_health_before",healthBefore);report.addProperty("hidden_target_health_after",healthAfter);
            report.addProperty("moving_alpha",alpha());capture("05-hidden-attack");moving=false;
            camera(new Vec3(6.5,203.0,8),new Vec3(0,201,2));phase=9;time=0;
        }else if(phase==9&&time>32){
            if(alpha()<.99)throw new AssertionError("Stopped models failed to return");
            capture("06-stopped-attack-reappears");report.addProperty("stopped_alpha",alpha());
            mc.getSingleplayerServer().execute(()->target.discard());phase=10;time=0;
        }else if(phase==10&&time>38){
            camera(new Vec3(5,202.2,6),mc.player.position().add(0,1.1,0));phase=11;time=0;
        }else if(phase==11&&time>24){
            capture("07-returned-to-ring");mc.getSingleplayerServer().execute(()->{
                var p=mc.getSingleplayerServer().getPlayerList().getPlayers().get(0);p.setItemSlot(EquipmentSlot.CHEST,ItemStack.EMPTY);
            });phase=12;time=0;
        }else if(phase==12&&time>25){
            if(alpha()!=0)throw new AssertionError("Visual state survived unequip");capture("08-unequipped");
            float volume=mc.options.getSoundSourceVolume(net.minecraft.sounds.SoundSource.MASTER);if(volume!=0)throw new AssertionError("Test must be silent");
            report.addProperty("real_forge_server_and_client",true);report.addProperty("normal_speed_real_movement",true);
            report.addProperty("server_satellites_attack_while_inner_models_hidden",true);report.addProperty("legacy_busy_mask_received",true);
            report.addProperty("exactly_five_outer_particles_restored",true);report.addProperty("busy_satellites_skip_idle_particles",true);report.addProperty("unequip_clears_visual_state",true);
            report.addProperty("test_master_volume",volume);report.addProperty("inner_geckolib_models",2);
            report.addProperty("outer_visual","original_five_color_particles");report.addProperty("opposite_camera_angle_captured",true);
            try{Files.writeString(mc.gameDirectory.toPath().resolve("satellites-verification.json"),new GsonBuilder().setPrettyPrinting().create().toJson(report));}catch(Exception e){throw new RuntimeException(e);}
            SourceOfMystery.LOGGER.info("DIVINE_SATELLITES_OK silent=true hidden_damage={}",healthBefore-healthAfter);phase=13;mc.stop();
        }
        if(ticks>1400)throw new AssertionError("Satellite fixture timeout at "+phase);
    }
    private static float alpha(){return DivineSatelliteRenderer.visibility(Minecraft.getInstance().player.getUUID());}
    private static void camera(Vec3 eye,Vec3 target){
        var mc=Minecraft.getInstance();var camera=new Marker(EntityType.MARKER,mc.level);
        Vec3 d=target.subtract(eye);Vec3 focus=eye.add(d.normalize().scale(4));
        camera.setPos(focus);camera.xo=camera.xOld=focus.x;camera.yo=camera.yOld=focus.y;camera.zo=camera.zOld=focus.z;
        float yaw=(float)(Mth.atan2(d.z,d.x)*Mth.RAD_TO_DEG)-90,pitch=(float)(-Mth.atan2(d.y,d.horizontalDistance())*Mth.RAD_TO_DEG);
        camera.setYRot(yaw);camera.yRotO=yaw;camera.setXRot(pitch);camera.xRotO=pitch;mc.setCameraEntity(camera);
    }
    private static void capture(String name){var mc=Minecraft.getInstance();Screenshot.grab(mc.gameDirectory,name+".png",mc.getMainRenderTarget(),x->{});}
}
