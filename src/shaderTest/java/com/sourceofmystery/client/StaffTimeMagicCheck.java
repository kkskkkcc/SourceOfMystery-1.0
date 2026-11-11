package com.sourceofmystery.client;

import com.sourceofmystery.SourceOfMystery;
import com.sourceofmystery.client.staff.*;
import com.sourceofmystery.config.MysteryConfig;
import com.sourceofmystery.energy.MysteryEnergy;
import com.sourceofmystery.item.ModItems;
import com.sourceofmystery.network.TimeMagicSyncPacket;
import com.sourceofmystery.staff.*;
import net.minecraft.client.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.*;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.FurnaceBlockEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.nio.file.Files;
import java.util.*;
import java.util.function.Consumer;

/** Measures vanilla simulation, not merely the spell's own accumulator. Always silent. */
@Mod.EventBusSubscriber(modid="sourceofmystery",value=Dist.CLIENT)
public final class StaffTimeMagicCheck {
    private static final BlockPos FURNACE=new BlockPos(4,65,3),LAMP=new BlockPos(5,65,3);
    private static volatile int phase;
    private static int warmup,round,time,cookedStart,sampleTicks,levelTicks,startLevelTicks,playerTicks,mobTicks,weather,observerTicks,clientPlayerTicks,clientMobTicks;
    private static long game,day,other,energy;
    private static Mob mob;
    private static net.minecraftforge.common.util.FakePlayer observer;
    private static volatile boolean ready,measured,ended,postExpiryChecked;
    private static volatile Throwable failure;
    private static UUID token;
    private static final com.google.gson.JsonObject report=new com.google.gson.JsonObject();
    private static final TimeMagicType[] MODES=TimeMagicType.values();
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    private static void server(Consumer<ServerPlayer> work){var mc=Minecraft.getInstance();mc.getSingleplayerServer().execute(()->{try{work.accept(mc.getSingleplayerServer().getPlayerList().getPlayers().get(0));}catch(Throwable t){failure=t;}});}
    private static void setup(ServerPlayer p){
        var l=p.serverLevel();p.teleportTo(l,.5,65,.5,0,0);p.setGameMode(GameType.CREATIVE);p.getAbilities().flying=true;p.onUpdateAbilities();
        l.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,p.server);l.getGameRules().getRule(GameRules.RULE_WEATHER_CYCLE).set(true,p.server);l.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(true,p.server);l.setWeatherParameters(10000,0,false,false);
        for(int x=-2;x<=7;x++)for(int z=-2;z<=6;z++)l.setBlockAndUpdate(new BlockPos(x,64,z),Blocks.STONE.defaultBlockState());
        l.setBlockAndUpdate(FURNACE,Blocks.AIR.defaultBlockState());l.setBlockAndUpdate(FURNACE,Blocks.FURNACE.defaultBlockState());
        var f=(FurnaceBlockEntity)l.getBlockEntity(FURNACE);f.setItem(0,new ItemStack(Items.IRON_ORE,32));f.setItem(1,new ItemStack(Items.COAL,32));
        l.setBlock(LAMP,Blocks.REDSTONE_LAMP.defaultBlockState().setValue(RedstoneLampBlock.LIT,true),18);l.getBlockTicks().clearArea(new net.minecraft.world.level.levelgen.structure.BoundingBox(LAMP));l.scheduleTick(LAMP,Blocks.REDSTONE_LAMP,30);
        if(mob!=null)mob.discard();mob=EntityType.HUSK.create(l);mob.moveTo(3,65,0,0,0);mob.setNoAi(true);mob.setNoGravity(true);mob.setPersistenceRequired();l.addFreshEntity(mob);
        p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(ModItems.DIMENSION_BREAKER_STAFF.get()));StaffSpell.TIME_STOP.select(p.getMainHandItem());TimeMagicType.STOP.select(p.getMainHandItem());p.inventoryMenu.broadcastChanges();
        energy=MysteryEnergy.getEnergy(p);if(observer!=null)observer.discard();observer=new net.minecraftforge.common.util.FakePlayer(l,new com.mojang.authlib.GameProfile(UUID.randomUUID(),"TimeObserver"));observer.setGameMode(GameType.CREATIVE);observer.setNoGravity(true);observer.setPos(1,65,3);l.addNewPlayer(observer);
        sampleTicks=0;measured=ended=postExpiryChecked=false;ready=true;
    }
    @SubscribeEvent public static void level(TickEvent.LevelTickEvent e){if(Boolean.getBoolean("sourceofmystery.staffTimeMagicCheck")&&e.phase==TickEvent.Phase.END&&e.level instanceof ServerLevel l&&l.dimension()==Level.OVERWORLD)levelTicks++;}
    @SubscribeEvent public static void measure(TickEvent.ServerTickEvent e){
        if(!Boolean.getBoolean("sourceofmystery.staffTimeMagicCheck")||e.phase!=TickEvent.Phase.END||!ready||phase<2)return;
        var mc=Minecraft.getInstance();var srv=mc.getSingleplayerServer();if(srv==null||srv.getPlayerList().getPlayers().isEmpty())return;
        var p=srv.getPlayerList().getPlayers().get(0);var l=p.serverLevel();
        try{
            if(sampleTicks==0){if(!TimeStop.altered(l))return;game=l.getGameTime();day=l.getDayTime();other=srv.getLevel(Level.NETHER).getGameTime();playerTicks=p.tickCount;mobTicks=mob.tickCount;observerTicks=observer.tickCount;weather=((net.minecraft.world.level.storage.ServerLevelData)l.getLevelData()).getClearWeatherTime();startLevelTicks=levelTicks;var furnace=(FurnaceBlockEntity)l.getBlockEntity(FURNACE);cookedStart=furnace.getItem(2).getCount()*200+furnace.saveWithFullMetadata().getShort("CookTime");l.getBlockTicks().clearArea(new net.minecraft.world.level.levelgen.structure.BoundingBox(LAMP));l.setBlock(LAMP,Blocks.REDSTONE_LAMP.defaultBlockState().setValue(RedstoneLampBlock.LIT,true),18);l.scheduleTick(LAMP,Blocks.REDSTONE_LAMP,30);}
            if(sampleTicks++==100&&!measured){
                int expected=round==0?0:round==1?10:500;
                check(l.getGameTime()-game==expected,"World game time mismatch: "+(l.getGameTime()-game)+" expected "+expected);
                check(l.getDayTime()-day==expected,"Day/night speed mismatch");check(mob.tickCount-mobTicks==expected,"Mob simulation speed mismatch "+(mob.tickCount-mobTicks));
                check(observer.tickCount-observerTicks==(round==0?0:100),"Non-caster player exemption incorrect");check(p.tickCount-playerTicks==100,"Player was accelerated or slowed: "+(p.tickCount-playerTicks));check(srv.getLevel(Level.NETHER).getGameTime()-other==100,"Other dimension inherited multiplier");
                int elapsedWeather=weather-((net.minecraft.world.level.storage.ServerLevelData)l.getLevelData()).getClearWeatherTime();check(elapsedWeather==expected,"Weather duration mismatch "+elapsedWeather);
                check(levelTicks-startLevelTicks==(round==0?100:expected),"Forge level hooks use wrong clock");
                var f=(FurnaceBlockEntity)l.getBlockEntity(FURNACE);var saved=f.saveWithFullMetadata();int cooked=f.getItem(2).getCount()*200+saved.getShort("CookTime")-cookedStart;check(Math.abs(cooked-expected)<=12,"Furnace clock mismatch "+cooked+" / "+expected);
                check(l.getBlockState(LAMP).getValue(RedstoneLampBlock.LIT)==(expected<30),"Scheduled redstone used wrong clock");check(energy-MysteryEnergy.getEnergy(p)==1000,"Wrong energy fee");check(!TimeStop.canCast(p),"Simultaneous time spells accepted");
                CompoundTag clock=l.getDataStorage().get(tag->null,"sourceofmystery_time_stop").save(new CompoundTag());check(clock.contains("GameOffset")&&clock.contains("DayOffset"),"Clock persistence missing");
                var result=new com.google.gson.JsonObject();result.addProperty("server_ticks",100);result.addProperty("world_ticks",l.getGameTime()-game);result.addProperty("mob_ticks",mob.tickCount-mobTicks);result.addProperty("player_ticks",p.tickCount-playerTicks);result.addProperty("observer_player_ticks",observer.tickCount-observerTicks);result.addProperty("other_dimension_ticks",srv.getLevel(Level.NETHER).getGameTime()-other);result.addProperty("weather_ticks",elapsedWeather);result.addProperty("furnace_progress",cooked);result.addProperty("scheduled_redstone",true);result.addProperty("forge_level_hooks",true);report.add(MODES[round].id,result);measured=true;
            }
            if(measured&&!TimeStop.altered(l)&&!ended){game=l.getGameTime();day=srv.getTickCount();ended=true;check(!TimeStop.canCast(p),"Manual stop skipped cooldown");}
            if(ended&&srv.getTickCount()-day==70){check(l.getGameTime()-game==70,"Clock jumped or caught up after manual stop");check(TimeStop.canCast(p),"Cooldown did not recover");postExpiryChecked=true;}
        }catch(Throwable t){failure=t;}
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent e){
        if(!Boolean.getBoolean("sourceofmystery.staffTimeMagicCheck")||e.phase!=TickEvent.Phase.END)return;
        var mc=Minecraft.getInstance();if(mc.player==null||mc.level==null||mc.getSingleplayerServer()==null)return;if(failure!=null)throw new AssertionError("Time magic native regression",failure);if(++warmup>6000)throw new AssertionError("Time test timeout round="+round+" phase="+phase);
        mc.mouseHandler.releaseMouse();time++;
        if(phase==0&&time==60){mc.setScreen(null);mc.options.pauseOnLostFocus=false;mc.options.getSoundSourceOptionInstance(SoundSource.MASTER).set(0d);server(p->{MysteryConfig.ENERGY_REGEN_FRACTION.set(0d);MysteryConfig.DAILY_REFRESH.set(false);MysteryEnergy.setEnergyAndMax(p,100000);setup(p);});phase=1;time=0;return;}
        if(phase==1&&ready&&time==21&&round>0){
            mc.player.setShiftKeyDown(true);mc.getConnection().send(new net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket(mc.player,net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket.Action.PRESS_SHIFT_KEY));
            for(int i=0;i<round;i++)mc.gameMode.useItem(mc.player,InteractionHand.MAIN_HAND);
            mc.getConnection().send(new net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket(mc.player,net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket.Action.RELEASE_SHIFT_KEY));mc.player.setShiftKeyDown(false);return;
        }
        if(phase==1&&ready&&time>40){
            check(StaffSpell.selected(mc.player.getMainHandItem())==StaffSpell.TIME_STOP&&TimeMagicType.selected(mc.player.getMainHandItem())==MODES[round],"Native selection not synchronized");
            phase=2;time=0;mc.gameMode.useItem(mc.player,InteractionHand.MAIN_HAND);return;
        }
        if(phase==2&&time==40){check(ClientTimeMagic.localActive(),"Client time state did not synchronize");check(StaffVisuals.timeStopped()==(round==0),"Stop shader leaked into speed modes");
            if(round>0){check(!StaffVisuals.frozen(mc.player)&&ClientTimeMagic.partial(mc.player,.7f)==.7f,"Player interpolation altered");ClientTimeMagic.accept(new TimeMagicSyncPacket(mc.level.dimension().location(),UUID.randomUUID(),mc.player.getUUID(),round,0,0,0,0));check(ClientTimeMagic.localActive(),"Stale stop packet cleared active clock");}
            clientPlayerTicks=mc.player.tickCount;var entity=mc.level.getEntity(mob.getId());check(entity!=null,"Mob not synchronized");clientMobTicks=entity.tickCount;
            Screenshot.grab(mc.gameDirectory,"time-"+MODES[round].id+".png",mc.getMainRenderTarget(),m->{});check(org.lwjgl.opengl.GL11.glGetError()==0,"OpenGL error in time magic");}
        if(phase==2&&time==140){
            int expected=round==0?0:round==1?10:500;var entity=mc.level.getEntity(mob.getId());check(entity!=null&&entity.tickCount-clientMobTicks==expected,"Client mob animation clock mismatch "+(entity==null?-1:entity.tickCount-clientMobTicks)+" / "+expected);check(mc.player.tickCount-clientPlayerTicks==100,"Client player clock altered: "+(mc.player.tickCount-clientPlayerTicks));
        }
        if(phase==2&&time==400){
            check(ClientTimeMagic.localActive(),"Time magic expired at the old limit");
            check(StaffVisuals.timeStopped()==(round==0),"Indefinite stop shader/freeze state expired");
            server(p->{check(TimeStop.altered(p.level()),"Server time magic expired at the old limit");});
            mc.gameMode.useItem(mc.player,InteractionHand.MAIN_HAND);
        }
        if(phase==2&&measured&&ended&&postExpiryChecked&&time>480){
            check(!ClientTimeMagic.altered()&&!StaffVisuals.timeStopped(),"Client clock remained active after manual stop");ready=false;phase=3;time=0;
            if(++round<3){server(StaffTimeMagicCheck::setup);phase=1;}else{
                report.addProperty("native_item_input",true);report.addProperty("native_shift_cycle",true);report.addProperty("client_entity_simulation_rates",true);report.addProperty("other_players_remain_normal_in_speed_modes",true);report.addProperty("stale_finish_packet_rejected",true);report.addProperty("indefinite_beyond_old_240_ticks_manual_right_click_and_cooldown",true);report.addProperty("post_manual_stop_no_catchup",true);report.addProperty("client_state_and_player_interpolation",true);report.addProperty("master_volume",mc.options.getSoundSourceVolume(SoundSource.MASTER));
                try{Files.writeString(mc.gameDirectory.toPath().resolve("time-magic-verification.json"),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(report));}catch(Exception error){throw new RuntimeException(error);}SourceOfMystery.LOGGER.info("STAFF_TIME_MAGIC_OK");mc.stop();
            }
        }
    }
}
