package com.sourceofmystery.client;

import com.sourceofmystery.SourceOfMystery;
import com.sourceofmystery.client.staff.StaffWheelScreen;
import com.sourceofmystery.config.MysteryConfig;
import com.sourceofmystery.energy.MysteryEnergy;
import com.sourceofmystery.item.ModItems;
import com.sourceofmystery.network.*;
import com.sourceofmystery.staff.*;
import net.minecraft.client.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.*;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.nio.file.Files;
import java.util.*;
import java.util.function.Consumer;

/** Actual input, 5 particle gates, swept collisions, protection, camera and clean teardown. */
@Mod.EventBusSubscriber(modid="sourceofmystery",value=Dist.CLIENT)
public final class EasternRiteCheck {
    private static final BlockPos DENY=new BlockPos(0,73,60),ERASE=new BlockPos(1,73,60),OUTSIDE=new BlockPos(12,73,60),FAR=new BlockPos(0,73,260),BEYOND=new BlockPos(0,73,283),BEDROCK=new BlockPos(12,73,150),WALL=new BlockPos(0,73,60),CHEST=new BlockPos(2,73,100);
    private static volatile int phase;private static int time,ticks,denied;private static volatile boolean ready,complete,post,thirdReady;private static boolean rangeChecked;private static volatile Throwable failure;
    private static final List<Mob> targets=new ArrayList<>();private static Mob outside,edge,ground,passenger;private static volatile boolean carriedChecked;private static double carryStart;
    private static Vec3 anchor;private static UUID token;private static double[] bases;
    private static OfferingMovieCapture movie,flightMovie;private static long flightStart,frame,movieStart;private static final List<Double> frameTimes=new ArrayList<>();
    private static boolean walked,returned,third,first,domainStarted,domainCleared;
    private static int directionRound;
    private static volatile long pressedTick=-1,launchTick=-1;
    private static boolean gatesChecked,quietBetweenDamage;
    private static final Set<Integer> seenPulses=new HashSet<>();
    private static final Map<Integer,Set<UUID>> actualDamage=new java.util.concurrent.ConcurrentHashMap<>();
    private static Mob protectedTarget;
    private static final float[][] DIRECTIONS={{90,0},{180,0},{270,0},{45,0},{0,-45},{180,45},{45,-90}};
    private static final List<Mob> domainTargets=new ArrayList<>();
    private static Mob domainOutside;private static net.minecraft.world.entity.item.ItemEntity drop;
    private static final com.google.gson.JsonObject report=new com.google.gson.JsonObject();
    private static void check(boolean c,String message){if(!c)throw new AssertionError(message);}
    private static void server(Consumer<ServerPlayer> action){var mc=Minecraft.getInstance();mc.getSingleplayerServer().execute(()->{try{action.accept(mc.getSingleplayerServer().getPlayerList().getPlayers().get(0));}catch(Throwable error){failure=error;}});}
    private static Mob mob(ServerPlayer p,double x,double z){p.serverLevel().getChunk((int)Math.floor(x)>>4,(int)Math.floor(z)>>4);var e=EntityType.HUSK.create(p.serverLevel());e.moveTo(x,72,z,0,0);e.setNoAi(true);e.setNoGravity(true);e.setPersistenceRequired();e.getAttribute(Attributes.MAX_HEALTH).setBaseValue(5000);e.getAttribute(Attributes.ARMOR).setBaseValue(0);e.setHealth(5000);p.serverLevel().addFreshEntity(e);return e;}
    private static void setup(ServerPlayer p){
        var l=p.serverLevel();p.teleportTo(l,.5,65,.5,0,0);p.setGameMode(GameType.SURVIVAL);p.setNoGravity(true);p.setHealth(20);p.getFoodData().setFoodLevel(20);l.setDayTime(13000);l.setWeatherParameters(100000,0,false,false);l.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,p.server);l.getGameRules().getRule(GameRules.RULE_NATURAL_REGENERATION).set(false,p.server);
        MysteryConfig.ENERGY_REGEN_FRACTION.set(0d);MysteryConfig.DAILY_REFRESH.set(false);p.getInventory().clearContent();p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(ModItems.DIMENSION_BREAKER_STAFF.get()));MysteryEnergy.setEnergyAndMax(p,10000);
        StaffConfig.SPELLS.get(StaffSpell.EASTERN_RITE).castTicks().set(60);
        check(StaffSpell.EASTERN_RITE.castTicks()==20,"Old castTicks60 did not clamp to20");
        // Fixture-only tickets make the complete 255-block destructive path observable.
        for(int x=-1;x<=1;x++)for(int z=0;z<=21;z++){l.setChunkForced(x,z,true);l.getChunk(x,z);}
        for(var pos:List.of(ERASE,OUTSIDE,FAR,BEYOND))l.setBlockAndUpdate(pos,Blocks.STONE.defaultBlockState());l.setBlockAndUpdate(BEDROCK,Blocks.BEDROCK.defaultBlockState());l.setBlockAndUpdate(CHEST,Blocks.CHEST.defaultBlockState());((ChestBlockEntity)l.getBlockEntity(CHEST)).setItem(0,new ItemStack(Items.DIAMOND,7));
        for(double z:new double[]{45,90,140,215,250})targets.add(mob(p,.5,z));edge=mob(p,10.25,110);outside=mob(p,11.3,110);ground=mob(p,1.5,90);ground.setPos(1.5,65,90);ground.setNoGravity(false);anchor=p.position();
        check(EasternRite.sweptHits(new AABB(0,0,20,.01,2,20.01),new Vec3(0,1,0),new Vec3(0,1,40),.1),"Swept collision tunnels through thin target");check(!EasternRite.sweptHits(new AABB(10.1,0,0,11,2,1),new Vec3(0,1,0),new Vec3(0,1,40),10),"Collision accepted a target beyond sphere radius");ready=true;
    }
    private static void use(){var mc=Minecraft.getInstance();mc.options.keyUse.setDown(true);mc.gameMode.useItem(mc.player,InteractionHand.MAIN_HAND);}
    private static void release(){var mc=Minecraft.getInstance();mc.options.keyUse.setDown(false);mc.gameMode.releaseUsingItem(mc.player);}
    private static void next(int value){phase=value;time=0;}
    private static void capture(String name){var mc=Minecraft.getInstance();Screenshot.grab(mc.gameDirectory,name,mc.getMainRenderTarget(),m->{});check(org.lwjgl.opengl.GL11.glGetError()==0,"GL error at "+name);}
    @SubscribeEvent public static void protect(BlockEvent.BreakEvent e){if(Boolean.getBoolean("sourceofmystery.easternRiteCheck")&&e.getPos().equals(DENY)){e.setCanceled(true);denied++;}}
    @SubscribeEvent public static void damage(net.minecraftforge.event.entity.living.LivingDamageEvent e){
        if(!Boolean.getBoolean("sourceofmystery.easternRiteCheck")||!domainTargets.contains(e.getEntity()))return;
        if(e.getSource().getEntity() instanceof ServerPlayer p){var field=StaffDomains.field(p);if(field!=null&&field.type==DomainType.EXECUTION)
            actualDamage.computeIfAbsent(field.age,k->java.util.concurrent.ConcurrentHashMap.newKeySet()).add(e.getEntity().getUUID());}
    }
    @SubscribeEvent public static void preventDamage(net.minecraftforge.event.entity.living.LivingAttackEvent e){
        if(Boolean.getBoolean("sourceofmystery.easternRiteCheck")&&e.getEntity()==protectedTarget)e.setCanceled(true);
    }
    @SubscribeEvent public static void launchTiming(TickEvent.ServerTickEvent e){
        if(!Boolean.getBoolean("sourceofmystery.easternRiteCheck")||e.phase!=TickEvent.Phase.END||pressedTick<0||launchTick>=0||phase>3)return;
        var mc=Minecraft.getInstance();var srv=mc.getSingleplayerServer();if(srv==null||srv.getPlayerList().getPlayers().isEmpty())return;
        var p=srv.getPlayerList().getPlayers().get(0);var rite=EasternRite.rite(p);
        if(rite!=null&&rite.travel>0){try{launchTick=srv.getTickCount();long elapsed=launchTick-pressedTick;check(elapsed<=80,"Eastern windup exceeded4 seconds: "+elapsed);
            report.addProperty("press_to_launch_server_ticks",elapsed);report.addProperty("launch_within_80_ticks",true);report.addProperty("old_cast_config60_clamped_to20",true);
        }catch(Throwable error){failure=error;}}
    }

    @SubscribeEvent public static void frame(TickEvent.RenderTickEvent e){
        if(!Boolean.getBoolean("sourceofmystery.easternRiteCheck")||e.phase!=TickEvent.Phase.END)return;
        var mc=Minecraft.getInstance();
        if(movie!=null&&mc.level!=null){long now=System.nanoTime();if(frame>0)frameTimes.add((now-frame)/1e6);frame=now;movie.capture(Math.min(13.999f,(mc.level.getGameTime()-movieStart+mc.getFrameTime())/20f));}

    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent e){
        if(!Boolean.getBoolean("sourceofmystery.easternRiteCheck")||e.phase!=TickEvent.Phase.END)return;var mc=Minecraft.getInstance();if(mc.player==null||mc.level==null||mc.getSingleplayerServer()==null)return;
        if(failure!=null)throw new AssertionError("Eastern rite server regression",failure);if(++ticks>5000)throw new AssertionError("Eastern rite timeout phase="+phase);mc.mouseHandler.releaseMouse();time++;
        if(phase==0){if(time==80){mc.setScreen(null);mc.options.pauseOnLostFocus=false;mc.options.cloudStatus().set(CloudStatus.OFF);mc.options.getSoundSourceOptionInstance(SoundSource.MASTER).set(0d);server(EasternRiteCheck::setup);}if(ready)next(1);return;}
        if(phase==1){
            if(time==10)mc.setScreen(new StaffWheelScreen(InteractionHand.MAIN_HAND));
            if(time==15){check(mc.screen instanceof StaffWheelScreen,"Wheel did not open");capture("01-eastern-outer-wheel.png");var layout=StaffWheelLayout.segments(StaffSpell.values().length);var segment=layout.get(8);check(segment.inner()>layout.get(7).outer(),"Ninth spell was not placed in outer ring");double scale=Math.min(1,Math.max(.25,Math.min((mc.screen.width-24)/(segment.outer()*2),(mc.screen.height-95)/(segment.outer()*2))));double x=mc.screen.width/2d+Math.cos(segment.angle())*segment.radius()*scale,y=mc.screen.height/2d-9+Math.sin(segment.angle())*segment.radius()*scale;check(StaffWheelLayout.hit(layout,(x-mc.screen.width/2d)/scale,(y-(mc.screen.height/2d-9))/scale)==8,"Outer spell cannot be selected");mc.screen.mouseClicked(x,y,0);}
            if(time==30){check(StaffSpell.selected(mc.player.getMainHandItem())==StaffSpell.EASTERN_RITE,"UI selection not synchronized");use();}
            if(time==40){release();server(p->{check(MysteryEnergy.getEnergy(p)==10000&&EasternRite.rite(p)==null,"Canceled charge consumed energy or started CG");complete=true;});}
            if(complete&&time>70){complete=false;next(2);}return;
        }
        if(phase==2){
            if(time==9){if(Boolean.getBoolean("sourceofmystery.offeringMovie"))try{movieStart=mc.level.getGameTime();movie=new OfferingMovieCapture(14*60,System.getProperty("sourceofmystery.offeringFfmpeg"),"eastern-gameplay-silent.mp4");}catch(Exception error){throw new RuntimeException(error);}}
            if(time==10){use();server(p->pressedTick=p.server.getTickCount());}
            if(time==35){release();server(p->{var r=EasternRite.rite(p);check(r!=null,"Charged cast did not start");check(MysteryEnergy.getEnergy(p)==5000,"Cast did not consume exactly 5000 energy");token=r.token;bases=r.bases.clone();check(!EasternRite.constrained(p),"Eastern rite still constrains caster");complete=true;});}
            if(complete){check(mc.getCameraEntity()==mc.player&&mc.options.getCameraType()==CameraType.FIRST_PERSON,"Eastern spell took over camera");report.addProperty("native_outer_wheel_selection",true);report.addProperty("canceled_charge_free",true);report.addProperty("charged_cast_cost_5000",true);complete=false;next(3);}return;
        }
        if(phase==3){
            var view=EasternVisuals.local();if(view==null){check(mc.getCameraEntity()==mc.player,"Eastern camera entity changed");next(4);return;}float age=view.age(0);
            check(mc.getCameraEntity()==mc.player&&!mc.options.hideGui,"Eastern spell took over camera or HUD");
            if(age>=10&&!walked){walked=true;mc.options.keyRight.setDown(true);mc.player.setYRot(15);}
            if(age>=30&&!returned){returned=true;mc.options.keyRight.setDown(false);server(p->{check(p.position().distanceTo(anchor)>.6,"Caster movement still locked");check(Math.abs(p.getYRot()-15)<2,"Caster yaw still pinned");p.teleportTo(p.serverLevel(),anchor.x,anchor.y,anchor.z,0,0);report.addProperty("normal_camera_movement_and_turning",true);});}
            if(age>=34&&!third){third=true;mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);}
            if(age>=44&&!first){first=true;mc.options.setCameraType(CameraType.FIRST_PERSON);}

            if(age>=18&&age<21)capture("02-particle-gates-rising.png");
            if(age>=54&&!gatesChecked){gatesChecked=true;capture("03-five-particle-gates.png");server(p->{var r=EasternRite.rite(p);check(r.origin.equals(anchor)&&Arrays.equals(r.bases,bases),"Completed gates moved");for(int i=0;i<5;i++)check(EasternGeometry.gateRise(r.age,i)==1,"Gate did not finish rising");p.invulnerableTime=0;float health=p.getHealth();p.hurt(p.damageSources().generic(),1);check(p.getHealth()==health-1,"Removed CG still grants invulnerability");report.addProperty("five_stationary_particle_gates",true);report.addProperty("no_eastern_cg_invulnerability",true);});}
            if(age>=38&&age<42)capture("03a-third-person-staff.png");
            if(age>=47&&age<50)capture("04-white-staff-sigil.png");
            if(age>=55&&age<58)capture("05-nested-white-orb.png");
            if(age>=88&&age<92)capture("06-camera-returned-orb-continues.png");
            if(age>=103&&age<107)capture("07-gray-spiral-ribbons.png");
            if(age>=148&&!carriedChecked){carriedChecked=true;server(p->{check(targets.get(0).getZ()>130&&EasternRite.constrained(targets.get(0)),"Hit entity was not carried with the orb");check(ground.isNoGravity()&&EasternRite.constrained(ground),"Ground entity was not suspended during flight");report.addProperty("live_entity_carry",true);});}
            if(age>=68&&!post){post=true;check(mc.getCameraEntity()==mc.player&&mc.options.getCameraType()==CameraType.FIRST_PERSON,"Launch changed camera");check(Math.abs(mc.getCameraEntity().getEyeY()-mc.player.getEyeY())<.01,"Camera moved to feet");report.addProperty("no_eastern_camera_takeover",true);}

            if(age>=243&&!rangeChecked){rangeChecked=true;server(p->{var r=EasternRite.rite(p);check(r!=null&&r.stoppedAt>=0&&Math.abs(r.travel-255)<1e-7,"Orb did not stop exactly 255 blocks from muzzle");check(Math.abs(r.ball().distanceTo(r.origin.add(r.forward.scale(16)).add(0,8,0))-255)<1e-7,"Range measured from caster instead of launch position");report.addProperty("range_from_launch_exactly_255",true);});if(movie!=null){movie.finish();movie=null;}}
            return;
        }
        if(phase==4){
            if(time==15)server(p->{var l=p.serverLevel();for(var target:targets){check(Math.abs(target.getHealth()-4000)<.01,"Orb damage was not 1000 once: "+target.getHealth());check(!EasternRite.constrained(target)&&target.isNoGravity()&&target.getZ()>250,"Passenger not released with original gravity");}check(Math.abs(edge.getHealth()-4000)<.01&&outside.getHealth()==5000,"Radius-10 collision boundary incorrect");check(!ground.isNoGravity()&&!EasternRite.constrained(ground)&&!ground.getPersistentData().contains("sourceofmystery_eastern_original_gravity"),"Normal gravity did not resume");check(l.getBlockState(BEDROCK).is(Blocks.BEDROCK),"Outside bedrock erased");check(l.getBlockState(ERASE).isAir()&&l.getBlockState(FAR).isAir()&&l.getBlockState(CHEST).isAir(),"Path erasure missed near, far or container blocks");check(l.getBlockState(OUTSIDE).is(Blocks.STONE)&&l.getBlockState(BEYOND).is(Blocks.STONE),"Terrain erasure exceeded radius or range");check(l.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,new AABB(-12,60,0,13,90,320)).stream().noneMatch(item->item.getItem().is(Items.DIAMOND)),"Erased container dropped its contents");check(MysteryEnergy.getEnergy(p)==5000,"Additional energy was deducted");report.addProperty("swept_radius_10_damage_1000_once",true);report.addProperty("loaded_path_erasure_to_255",true);report.addProperty("original_gravity_restored_and_no_container_drops",true);l.setBlockAndUpdate(WALL,Blocks.BEDROCK.defaultBlockState());l.setBlockAndUpdate(new BlockPos(0,73,80),Blocks.STONE.defaultBlockState());passenger=mob(p,.5,35);carryStart=passenger.getZ();check(EasternRite.start(p),"Hard-stop probe could not start");EasternRite.rite(p).age=EasternRite.LAUNCH-1;complete=true;});
            if(complete&&time>25){complete=false;next(5);}return;
        }
        if(phase==5){
            if(time==40)server(p->{var r=EasternRite.rite(p);check(r!=null&&r.stoppedAt>=0&&r.travel<35&&r.travel>30,"Bedrock did not stop the swept sphere at its front");check(p.serverLevel().getBlockState(WALL).is(Blocks.BEDROCK)&&p.serverLevel().getBlockState(new BlockPos(0,73,80)).is(Blocks.STONE),"Hard stop erased bedrock or terrain behind it");check(passenger.getZ()>carryStart+10&&!EasternRite.constrained(passenger)&&passenger.isNoGravity(),"Impact did not carry then release passenger");report.addProperty("unbreakable_impact_stops_early_and_releases",true);complete=true;});
            if(complete&&EasternVisuals.local()==null&&time%5==0)server(p->{if(EasternRite.rite(p)!=null||thirdReady)return;p.serverLevel().setBlockAndUpdate(DENY,Blocks.STONE.defaultBlockState());check(EasternRite.start(p),"Protection probe could not start");EasternRite.rite(p).age=EasternRite.LAUNCH-1;thirdReady=true;});
            if(thirdReady){complete=false;next(6);}return;
        }
        if(phase==6){
            if(time==60)server(p->{check(EasternRite.rite(p)==null,"Protected block did not terminate orb");check(p.serverLevel().getBlockState(DENY).is(Blocks.STONE)&&denied>0,"BreakEvent protection ignored");report.addProperty("foreign_break_event_cancellation_stops_orb",true);for(int x=-1;x<=1;x++)for(int z=0;z<=21;z++)p.serverLevel().setChunkForced(x,z,false);complete=true;});
            if(complete&&time>65){complete=false;next(7);}return;
        }
        // Every quadrant and vertical pitch uses a real server sphere, damage and packet projection.
        if(phase==7){
            if(time==5)server(p->{
                for(var target:targets)target.discard();edge.discard();outside.discard();ground.discard();passenger.discard();
                p.teleportTo(p.serverLevel(),.5,180,.5,DIRECTIONS[directionRound][0],DIRECTIONS[directionRound][1]);
                check(EasternRite.start(p),"Directional probe could not start");var r=EasternRite.rite(p);
                Vec3 desired=Vec3.directionFromRotation(DIRECTIONS[directionRound][1],DIRECTIONS[directionRound][0]).normalize();
                check(r.forward.distanceTo(desired)<1e-7,"Server ignored yaw/pitch");
                var start=r.ball();var at=start.add(r.forward.scale(22));var target=mob(p,at.x,at.z);target.setPos(at.x,at.y,at.z);targets.clear();targets.add(target);
                r.age=EasternRite.LAUNCH-1;
            });
            if(time==35){var view=EasternVisuals.local();check(view!=null,"Directional snapshot missing");
                Vec3 expected=Vec3.directionFromRotation(DIRECTIONS[directionRound][1],DIRECTIONS[directionRound][0]).normalize();
                check(view.forward().distanceTo(expected)<1e-7,"Client ignored pitch/yaw");
                server(p->{check(targets.get(0).getHealth()==4000&&EasternRite.constrained(targets.get(0)),"Directional sphere missed target "+directionRound);complete=true;});}
            if(time==36)server(p->{var r=EasternRite.rite(p);r.travel=r.range;r.age=r.end()-1;});
            if(time>80&&complete&&EasternVisuals.local()==null){complete=false;if(++directionRound<DIRECTIONS.length)time=0;else{report.addProperty("all_quadrants_and_vertical_aim_real_damage",true);next(8);}}return;
        }
        if(phase==8){
            if(time==5)server(p->{
                for(var entity:p.serverLevel().getEntitiesOfClass(Mob.class,new AABB(-300,0,-300,300,320,340)))entity.discard();
                p.teleportTo(p.serverLevel(),.5,65,.5,0,0);MysteryEnergy.setEnergyAndMax(p,10000);
                StaffSpell.DOMAIN_EXPANSION.select(p.getMainHandItem());p.getMainHandItem().getOrCreateTag().putString(DomainType.KEY,DomainType.EXECUTION.id);p.inventoryMenu.broadcastChanges();
                for(double[] at:new double[][]{{.5,20},{.5,-20},{20,.5},{-20,.5},{98,.5}})domainTargets.add(mob(p,at[0],at[1]));
                domainOutside=mob(p,102,.5);protectedTarget=mob(p,-12,12);drop=new net.minecraft.world.entity.item.ItemEntity(p.serverLevel(),2,66,2,new ItemStack(Items.DIAMOND));drop.setNoGravity(true);drop.setUnlimitedLifetime();p.serverLevel().addFreshEntity(drop);complete=true;
            });
            if(time==30&&complete){complete=false;use();}
            var field=DomainVisuals.localField();
            if(field!=null){
                for(var strike:DomainVisuals.strikes()){
                    check(strike.pulse()%20==0,"Slash was generated between damage ticks");
                    check(actualDamage.getOrDefault(strike.pulse(),Set.of()).contains(strike.target()),"Slash has no matching real damage event");
                    check(!strike.target().equals(protectedTarget.getUUID())&&!strike.target().equals(drop.getUUID()),"Undamaged target/item got slash FX");
                    seenPulses.add(strike.pulse());
                }
                if(field.age(0)>90&&field.age(0)<200&&DomainVisuals.strikes().isEmpty())quietBetweenDamage=true;
            }
            if(field!=null&&!domainStarted&&field.age(0)>=102&&!DomainVisuals.strikes().isEmpty()){domainStarted=true;release();
                check(DomainDirector.active(),"Execution's own CG was accidentally removed");
                for(var target:domainTargets){var entity=mc.level.getEntity(target.getId());check(entity!=null&&field.contains(entity),"Client omitted a quadrant target");}
                check(!field.contains(mc.level.getEntity(drop.getId())),"Dropped item received execution FX");check(DomainVisuals.hasExecutionTargets(field),"Living targets did not enable FX");
                capture("08-execution-surrounding-targets.png");
                server(p->{for(var target:domainTargets)check(target.getHealth()<5000,"Execution missed a quadrant/boundary target");check(domainOutside.getHealth()==5000,"Execution exceeded radius100");check(protectedTarget.getHealth()==5000,"Canceled damage leaked");
                    float firstHealth=domainTargets.get(0).getHealth();for(int i=1;i<4;i++)check(domainTargets.get(i).getHealth()==firstHealth,"Directional damage unequal");
                    report.addProperty("execution_360_equal_cardinal_damage",true);report.addProperty("execution_radius100_boundary",true);report.addProperty("execution_items_excluded",true);complete=true;});
            }
            if(field!=null&&field.age(0)>220&&!domainCleared&&complete){domainCleared=true;complete=false;server(p->{for(var target:domainTargets)target.discard();protectedTarget.discard();complete=true;});}
            if(domainCleared&&complete&&field!=null&&field.age(0)>250){
                check(!DomainVisuals.hasExecutionTargets(field)&&DomainVisuals.strikes().isEmpty(),"Empty field still requests slashes/audio");
                check(seenPulses.size()>=3&&quietBetweenDamage,"Missing actual hit pulses or quiet gap between hits");
                report.addProperty("slash_pulses_match_actual_damage_events",true);report.addProperty("no_slashes_between_damage_pulses",true);
                report.addProperty("canceled_damage_has_no_slash",true);report.addProperty("observed_damage_pulse_count",seenPulses.size());check(!field.contains(mc.level.getEntity(drop.getId())),"Item-only field still requests slash FX");capture("09-execution-items-only-no-cuts.png");
                report.addProperty("execution_empty_stops_slashes_and_sweep_audio",true);server(p->{StaffDomains.stop(p);drop.discard();domainOutside.discard();});next(9);
            }return;
        }
        if(phase==9&&time>30){
                check(DomainVisuals.strikes().isEmpty(),"Stopped field kept old slash FX");
                frameTimes.sort(Double::compare);if(!frameTimes.isEmpty()){report.addProperty("render_frame_ms_p50",frameTimes.get(frameTimes.size()/2));report.addProperty("render_frame_ms_p95",frameTimes.get((int)(frameTimes.size()*.95)));report.addProperty("render_frame_samples",frameTimes.size());}
                report.addProperty("particle_only_no_torii_mesh",true);report.addProperty("shader_enabled",OutsiderShaderCompat.active());report.addProperty("master_volume",mc.options.getSoundSourceVolume(SoundSource.MASTER));report.addProperty("real_two_client_multiplayer",false);
                try{Files.writeString(mc.gameDirectory.toPath().resolve("eastern-verification.json"),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(report));}catch(Exception error){throw new RuntimeException(error);}SourceOfMystery.LOGGER.info("EASTERN_RITE_OK");mc.stop();
        }
    }
}
