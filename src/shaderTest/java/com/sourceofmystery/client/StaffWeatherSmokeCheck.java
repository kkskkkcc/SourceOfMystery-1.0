package com.sourceofmystery.client;

import com.sourceofmystery.SourceOfMystery;
import com.sourceofmystery.energy.MysteryEnergy;
import com.sourceofmystery.item.ModItems;
import com.sourceofmystery.network.*;
import com.sourceofmystery.staff.*;
import net.minecraft.client.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.*;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.nio.file.Files;
import java.util.*;
import java.util.function.Consumer;

/** Real weather flags, native item input, four environments, actual wind/debris and native GPU frames. */
@Mod.EventBusSubscriber(modid="sourceofmystery",value=Dist.CLIENT)
public final class StaffWeatherSmokeCheck {
    private static int warmup,stage,time;private static volatile boolean ready;private static volatile Throwable failure;
    private static Mob target,outside;private static Vec3 initial;private static int denied,hits;private static boolean deny;
    private static UUID oldToken;
    private static OfferingMovieCapture movie;
    private static net.minecraft.nbt.CompoundTag savedDebris;
    private static final com.google.gson.JsonObject report=new com.google.gson.JsonObject();
    private static final WeatherType[] TYPES={WeatherType.CLEAR,WeatherType.RAIN,WeatherType.THUNDER,WeatherType.TORNADO,WeatherType.TORNADO,WeatherType.TORNADO,WeatherType.TORNADO,WeatherType.TORNADO};
    private static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    private static void server(Consumer<ServerPlayer> action){var mc=Minecraft.getInstance();mc.getSingleplayerServer().execute(()->{try{action.accept(mc.getSingleplayerServer().getPlayerList().getPlayers().get(0));}catch(Throwable error){failure=error;}});}
    private static Mob mob(ServerPlayer p,double x,double z){var m=EntityType.HUSK.create(p.serverLevel());m.moveTo(x,65,z,0,0);m.setNoAi(true);m.setNoGravity(true);m.setPersistenceRequired();m.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);m.getAttribute(Attributes.ARMOR).setBaseValue(0);m.setHealth(1000);p.serverLevel().addFreshEntity(m);return m;}
    private static void setup(ServerPlayer p){
        check(!StaffWeather.active(p),"Previous storm remained");p.stopUsingItem();p.teleportTo(p.serverLevel(),.5,65,.5,0,1.16f);p.setGameMode(GameType.CREATIVE);p.getAbilities().flying=true;p.onUpdateAbilities();
        if(target!=null)target.discard();if(outside!=null)outside.discard();
        if(stage>=3){
            var block=switch(stage){case 4->Blocks.SAND;case 5->Blocks.WATER;case 6->Blocks.MAGMA_BLOCK;default->Blocks.GRASS_BLOCK;};
            for(int x=-12;x<=12;x++)for(int z=68;z<=92;z++)p.serverLevel().setBlockAndUpdate(new BlockPos(x,64,z),block.defaultBlockState());
            target=mob(p,30.5,80.5);outside=mob(p,107,80.5);initial=target.position();
            if(stage==5)target.setRemainingFireTicks(200);
            var expected=stage==7?TornadoVariant.NORMAL:TornadoVariant.index(stage-3);
            check(TornadoVariant.detect(p.serverLevel(),new Vec3(.5,65,80.5))==expected,"Scene did not select "+expected);
            if(stage==7)StaffConfig.TORNADO_DURATION.set(200);
            deny=stage==3;denied=hits=0;
        }
        StaffSpell.WEATHER_CONTROL.select(p.getMainHandItem());p.getMainHandItem().getOrCreateTag().putString(WeatherType.KEY,TYPES[stage].id);p.inventoryMenu.broadcastChanges();ready=true;
    }
    private static void use(){var mc=Minecraft.getInstance();mc.options.keyUse.setDown(true);mc.gameMode.useItem(mc.player,InteractionHand.MAIN_HAND);}
    private static void release(){var mc=Minecraft.getInstance();mc.options.keyUse.setDown(false);mc.gameMode.releaseUsingItem(mc.player);}
    private static void click(){var mc=Minecraft.getInstance();mc.options.keyUse.setDown(true);var e=new InputEvent.InteractionKeyMappingTriggered(1,mc.options.keyUse,InteractionHand.MAIN_HAND);if(!MinecraftForge.EVENT_BUS.post(e))mc.gameMode.useItem(mc.player,InteractionHand.MAIN_HAND);}
    private static void capture(String name){var mc=Minecraft.getInstance();Screenshot.grab(mc.gameDirectory,name,mc.getMainRenderTarget(),m->{});int error=org.lwjgl.opengl.GL11.glGetError();check(error==0,"GL failure "+error+": "+name);}
    private static void wide(){
        var mc=Minecraft.getInstance();var view=WeatherVisuals.localStorm();if(view==null)return;
        Vec3 d=view.center(1).add(0,90,0).subtract(mc.player.getEyePosition());
        mc.setCameraEntity(mc.player);mc.player.setYRot((float)(Math.atan2(d.z,d.x)*180/Math.PI)-90);mc.player.yRotO=mc.player.getYRot();
        mc.player.setXRot((float)(-Math.atan2(d.y,d.horizontalDistance())*180/Math.PI));mc.player.xRotO=mc.player.getXRot();
    }
    @SubscribeEvent public static void frame(TickEvent.RenderTickEvent event){
        if(!Boolean.getBoolean("sourceofmystery.staffWeatherCheck")||!ready)return;
        if(stage>=3&&time>=155&&event.phase==TickEvent.Phase.START)wide();
        if(movie!=null&&stage>=3&&stage<=6&&time>=170&&time<250&&event.phase==TickEvent.Phase.END)movie.capture((stage-3)*4+(time-170+Minecraft.getInstance().getFrameTime())/20f);
    }
    @SubscribeEvent public static void hand(net.minecraftforge.client.event.RenderHandEvent event){if(Boolean.getBoolean("sourceofmystery.staffWeatherCheck")&&stage>=3&&time>=90)event.setCanceled(true);}
    @SubscribeEvent public static void hurt(LivingHurtEvent event){if(Boolean.getBoolean("sourceofmystery.staffWeatherCheck")&&event.getSource().getMsgId().equals("tornado_wind")){check(event.getAmount()==5,"Variant added extra tornado damage");if(event.getEntity()==target)hits++;}}
    @SubscribeEvent public static void terrain(BlockEvent.BreakEvent event){if(Boolean.getBoolean("sourceofmystery.staffWeatherCheck")&&deny){denied++;event.setCanceled(true);}}
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event){
        if(!Boolean.getBoolean("sourceofmystery.staffWeatherCheck")||event.phase!=TickEvent.Phase.END)return;var mc=Minecraft.getInstance();if(mc.level==null||mc.player==null||mc.getSingleplayerServer()==null)return;
        if(failure!=null)throw new AssertionError("Weather server checks",failure);mc.mouseHandler.releaseMouse();if(warmup++<80)return;
        if(!ready){if(warmup!=81)return;mc.setScreen(null);mc.options.pauseOnLostFocus=false;mc.options.getSoundSourceOptionInstance(SoundSource.MASTER).set(0d);
            server(p->{
                p.serverLevel().getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,p.server);p.serverLevel().getGameRules().getRule(GameRules.RULE_DOFIRETICK).set(false,p.server);p.serverLevel().setDayTime(9000);p.serverLevel().setWeatherParameters(100000,0,false,false);
                com.sourceofmystery.config.MysteryConfig.ENERGY_REGEN_PER_SECOND.set(0d);com.sourceofmystery.config.MysteryConfig.DAILY_REFRESH.set(false);p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(ModItems.DIMENSION_BREAKER_STAFF.get()));p.getInventory().clearContent();p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(ModItems.DIMENSION_BREAKER_STAFF.get()));
                var nether=p.server.getLevel(Level.NETHER);check(nether!=null,"Missing ceiling test dimension");nether.setBlockAndUpdate(new BlockPos(0,63,0),Blocks.MAGMA_BLOCK.defaultBlockState());nether.setBlockAndUpdate(new BlockPos(0,127,0),Blocks.BEDROCK.defaultBlockState());
                check(StaffWeather.surface(nether,0,0,64,false)==64,"Ceiling dimension selected the roof");report.addProperty("ceiling_surface_ignores_roof",true);
                MysteryEnergy.setEnergyAndMax(p,100000);MysteryEnergy.setEnergy(p,100000);StaffSpell.WEATHER_CONTROL.select(p.getMainHandItem());Set<WeatherType> choices=new HashSet<>();p.setShiftKeyDown(true);for(int i=0;i<4;i++){p.getMainHandItem().getItem().use(p.level(),p,InteractionHand.MAIN_HAND);choices.add(WeatherType.selected(p.getMainHandItem()));}p.setShiftKeyDown(false);check(choices.size()==4&&MysteryEnergy.getEnergy(p)==100000,"Weather cycle missing/charged energy");report.addProperty("four_shift_right_click_choices",true);setup(p);
            });return;
        }
        time++;mc.options.setCameraType(CameraType.FIRST_PERSON);mc.player.setYRot(0);mc.player.yRotO=0;mc.player.setXRot(1.16f);mc.player.xRotO=1.16f;
        for(var key:new KeyMapping[]{mc.options.keyUp,mc.options.keyDown,mc.options.keyLeft,mc.options.keyRight,mc.options.keyJump,mc.options.keyShift})key.setDown(false);
        if(stage==0&&time==2)mc.setScreen(new com.sourceofmystery.client.staff.StaffWheelScreen(InteractionHand.MAIN_HAND));
        if(stage==0&&time==4)capture("00-eight-spell-wheel.png");if(stage==0&&time==6)mc.setScreen(null);
        if(time==10)use();
        if(stage<3){
            if(time==85){release();server(p->{var data=p.serverLevel().getLevelData();check(data.isRaining()==(stage!=0)&&data.isThundering()==(stage==2),"Native weather flags incorrect "+stage);check(MysteryEnergy.getEnergy(p)==100000-(stage+1)*2000,"Weather cost not 2000");report.addProperty("vanilla_weather_"+TYPES[stage].id,true);});}
            if(time==95)capture("01-weather-"+TYPES[stage].id+".png");if(time==110)next();return;
        }
        if(time==80){server(p->{var s=StaffWeather.field(p);check(s!=null,"Holding use dismissed new tornado");if(stage<7)check(s.remaining>5900,"Default duration not 6000 ticks");check(StaffWeather.RADIUS==100,"Radius changed");check(MysteryEnergy.getEnergy(p)==94000-(stage-2)*5000,"Tornado cost not 5000");});}
        if(time==80&&stage==3&&Boolean.getBoolean("sourceofmystery.offeringMovie")){try{movie=new OfferingMovieCapture(960,System.getProperty("sourceofmystery.offeringFfmpeg"),"weather-tornadoes-silent.mp4");}catch(Exception e){throw new RuntimeException(e);}}
        if(time==90){release();mc.options.hideGui=true;}
        if(time>=155)wide();
        if(time==100&&stage==3)capture("02-tornado-forming.png");
        if(time==150)server(p->{
            check(target.position().distanceToSqr(initial)>1&&target.getY()>initial.y+.5,"Wind did not rotate/lift target");check(outside.getHealth()==1000&&outside.position().distanceToSqr(new Vec3(107,65,80.5))<.1,"Storm escaped radius100");check(p.getHealth()==p.getMaxHealth()&&!p.hasEffect(MobEffects.BLINDNESS),"Caster affected by own wind");
            var storm=StaffWeather.field(p);Vec3 observer=storm.center.add(-85,76.38,-125);p.teleportTo(p.serverLevel(),observer.x,observer.y,observer.z,0,0);p.getAbilities().flying=true;p.onUpdateAbilities();
            if(stage==3){check(denied>0,"No terrain protection event");check(p.serverLevel().getEntitiesOfClass(FallingBlockEntity.class,new AABB(-110,60,-30,110,260,190)).isEmpty(),"Canceled block break spawned debris");deny=false;report.addProperty("block_break_veto_respected",true);}
            if(stage==4)check(target.hasEffect(MobEffects.BLINDNESS)&&target.hasEffect(MobEffects.MOVEMENT_SLOWDOWN),"Sand special missing");
            if(stage==5)check(!target.isOnFire(),"Waterspout did not extinguish target");
            if(stage==6)check(target.hasEffect(MobEffects.WEAKNESS)&&target.getEffect(MobEffects.WEAKNESS).getAmplifier()==1&&!target.isOnFire(),"Fire variant effect/extra fire damage incorrect");
        });
        if(time==210&&stage<7){
            var view=WeatherVisuals.localStorm();check(view!=null,"No client storm snapshot");Vec3 eye=mc.gameRenderer.getMainCamera().getPosition();check(eye.distanceTo(view.center(1).add(-85,78,-125))<3,"Wide camera did not reach shot");
            check(mc.options.getCloudsType()==CloudStatus.OFF,"Vanilla clouds intersect custom shelf");report.addProperty("effective_clouds_suppressed_without_option_write",true);
            SourceOfMystery.LOGGER.info("WEATHER_SHOT stage={} center={} eye={} age={} fields={}",stage,view.center(1),eye,view.age(1),WeatherVisuals.storms().size());
            capture("03-tornado-"+TornadoVariant.index(Math.min(3,stage-3)).id+".png");server(p->{check(hits>=2,"No real 5HP pulses");check(target.getHealth()<1000,"No wind damage");report.addProperty("variant_"+TornadoVariant.index(Math.min(3,stage-3)).id+"_physics_and_damage",true);if(stage==3){check(!p.serverLevel().getEntitiesOfClass(FallingBlockEntity.class,new AABB(-110,60,-30,110,260,190)).isEmpty(),"No real debris after allowing breaks");report.addProperty("real_terrain_debris",true);}});
        }
        if(stage<7){
            if(time==245&&oldToken!=null)ModNetwork.CHANNEL.sendToServer(new WeatherStopPacket(oldToken));
            if(time==255){server(p->{check(StaffWeather.field(p)!=null&&!StaffWeather.field(p).stopping,"Stale stop token dismissed new storm");oldToken=StaffWeather.field(p).token;
                if(stage==3){var pieces=p.serverLevel().getEntitiesOfClass(FallingBlockEntity.class,new AABB(-110,60,-30,110,260,190));check(!pieces.isEmpty(),"No debris for reload probe");savedDebris=pieces.get(0).saveWithoutId(new net.minecraft.nbt.CompoundTag());}
            });click();}
            if(time==265){release();server(p->check(StaffWeather.field(p)!=null&&StaffWeather.field(p).stopping,"Early stop did not begin fade"));}
            if(time==310){check(!WeatherVisuals.windActive(),"Wind audio remained after dismissal");server(p->{check(!StaffWeather.active(p),"Storm did not finish fading");check(p.serverLevel().getEntitiesOfClass(FallingBlockEntity.class,new AABB(-110,60,-30,110,260,190)).isEmpty(),"Debris survived cancellation");
                if(savedDebris!=null){var piece=new FallingBlockEntity(EntityType.FALLING_BLOCK,p.serverLevel());piece.load(savedDebris);piece.setUUID(UUID.randomUUID());check(!p.serverLevel().addFreshEntity(piece),"Reloaded orphan debris was not removed");savedDebris=null;report.addProperty("unloaded_debris_recovery_cleanup",true);}
            });next();}
        }else{
            if(time==295){server(p->{check(!StaffWeather.active(p),"Configured 200-tick storm did not expire");report.addProperty("configured_natural_expiry",true);report.addProperty("early_stop_fade_debris_audio_cleanup",true);});}
            if(time==310)finish();
        }
    }
    private static void next(){release();var mc=Minecraft.getInstance();mc.setCameraEntity(mc.player);mc.options.hideGui=false;if(stage==6&&movie!=null){movie.finish();movie=null;}stage++;time=0;server(StaffWeatherSmokeCheck::setup);}
    private static void finish(){var mc=Minecraft.getInstance();mc.setCameraEntity(mc.player);report.addProperty("master_volume",mc.options.getSoundSourceVolume(SoundSource.MASTER));report.addProperty("shader_enabled",OutsiderShaderCompat.active());report.addProperty("tornado_radius",StaffWeather.RADIUS);report.addProperty("default_tornado_duration",6000);try{Files.writeString(mc.gameDirectory.toPath().resolve("weather-verification.json"),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(report));}catch(Exception e){throw new RuntimeException(e);}SourceOfMystery.LOGGER.info("STAFF_WEATHER_OK");mc.stop();}
}
