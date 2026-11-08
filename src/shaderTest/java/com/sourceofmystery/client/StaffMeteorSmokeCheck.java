package com.sourceofmystery.client;

import com.sourceofmystery.SourceOfMystery;
import com.sourceofmystery.energy.MysteryEnergy;
import com.sourceofmystery.entity.OutsiderSpell;
import com.sourceofmystery.item.ModItems;
import com.sourceofmystery.staff.*;
import net.minecraft.client.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.nio.file.Files;
import java.util.function.Consumer;

/** Native crosshair, owner-only outline, use-item, moving-target impact and cancellation checks. */
@Mod.EventBusSubscriber(modid="sourceofmystery",value=Dist.CLIENT)
public final class StaffMeteorSmokeCheck {
    private static int warmup,time;private static volatile boolean ready;private static volatile Throwable failure;
    private static Mob target,decoy;private static double ground;private static OutsiderSpell moving,fixed,lost;
    private static Vec3 fixedPoint,lostPoint;private static int primaryId;
    private static final com.google.gson.JsonObject report=new com.google.gson.JsonObject();
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    private static void server(Consumer<ServerPlayer> action){Minecraft.getInstance().getSingleplayerServer().execute(()->{try{action.accept(Minecraft.getInstance().getSingleplayerServer().getPlayerList().getPlayers().get(0));}catch(Throwable error){failure=error;}});}
    private static Mob mob(ServerPlayer p,double x,double z){
        var e=EntityType.HUSK.create(p.serverLevel());e.moveTo(x,ground,z,180,0);e.setNoAi(true);e.setNoGravity(true);e.setPersistenceRequired();
        e.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);e.getAttribute(Attributes.ARMOR).setBaseValue(0);e.setHealth(1000);p.serverLevel().addFreshEntity(e);return e;
    }
    private static void look(Vec3 point){
        var p=Minecraft.getInstance().player;Vec3 d=point.subtract(p.getEyePosition());
        float yaw=(float)(Math.atan2(d.z,d.x)*180/Math.PI)-90,pitch=(float)(-Math.atan2(d.y,d.horizontalDistance())*180/Math.PI);
        p.setYRot(yaw);p.yRotO=yaw;p.setXRot(pitch);p.xRotO=pitch;
    }
    private static void use(){var mc=Minecraft.getInstance();mc.options.keyUse.setDown(true);mc.gameMode.useItem(mc.player,InteractionHand.MAIN_HAND);}
    private static void release(){var mc=Minecraft.getInstance();mc.options.keyUse.setDown(false);mc.gameMode.releaseUsingItem(mc.player);}
    private static OutsiderSpell flight(ServerPlayer p){return p.serverLevel().getEntitiesOfClass(OutsiderSpell.class,new AABB(-10,ground-5,-10,180,320,180),s->s.kind()==OutsiderSpell.METEOR&&s.getOwner()==p).stream().findFirst().orElseThrow(()->new AssertionError("Native cast did not spawn meteor"));}
    private static void capture(String name){var mc=Minecraft.getInstance();Screenshot.grab(mc.gameDirectory,name,mc.getMainRenderTarget(),m->{});check(org.lwjgl.opengl.GL11.glGetError()==0,"GL error: "+name);}
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event){
        if(!Boolean.getBoolean("sourceofmystery.staffMeteorCheck")||event.phase!=TickEvent.Phase.END)return;
        var mc=Minecraft.getInstance();if(mc.player==null||mc.level==null||mc.getSingleplayerServer()==null)return;
        if(failure!=null)throw new AssertionError("Meteor server checks",failure);
        mc.mouseHandler.releaseMouse();if(warmup++<80)return;
        if(!ready){if(warmup!=81)return;mc.setScreen(null);mc.options.getSoundSourceOptionInstance(SoundSource.MASTER).set(0d);mc.options.pauseOnLostFocus=false;
            server(p->{
                var level=p.serverLevel();ground=level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING,0,0);
                level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,p.server);level.getGameRules().getRule(GameRules.RULE_MOBGRIEFING).set(false,p.server);level.setDayTime(12500);level.setWeatherParameters(100000,0,false,false);
                com.sourceofmystery.config.MysteryConfig.ENERGY_REGEN_PER_SECOND.set(0d);com.sourceofmystery.config.MysteryConfig.DAILY_REFRESH.set(false);
                StaffConfig.SPELLS.get(StaffSpell.ANNIHILATION).castTicks().set(60); // Only this isolated fixture; the shipped default stays 480.
                p.setGameMode(GameType.CREATIVE);p.teleportTo(level,.5,ground,.5,0,0);p.getAbilities().flying=true;p.onUpdateAbilities();
                p.getInventory().clearContent();p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(ModItems.DIMENSION_BREAKER_STAFF.get()));StaffSpell.ANNIHILATION.select(p.getMainHandItem());
                MysteryEnergy.setEnergyAndMax(p,100000);MysteryEnergy.setEnergy(p,100000);
                target=mob(p,.5,14.5);decoy=mob(p,6.5,8.5);primaryId=target.getId();
                level.setChunkForced(8,0,true);level.getChunk(8,0);ready=true;
            });return;
        }
        time++;mc.options.setCameraType(CameraType.FIRST_PERSON);mc.player.setDeltaMovement(Vec3.ZERO);
        for(var k:new KeyMapping[]{mc.options.keyUp,mc.options.keyDown,mc.options.keyLeft,mc.options.keyRight,mc.options.keyJump,mc.options.keyShift})k.setDown(false);
        if(time<25||time>=40&&time<100||time>=110&&time<235)look(new Vec3(.5,ground+1.35,14.5));
        else if(time>=25&&time<40||time>=235&&time<349||time>=420&&time<505)look(new Vec3(6.5,ground+1.35,8.5));
        else if(time>=349&&time<420)look(new Vec3(.5,ground,9.5));
        else look(mc.player.getEyePosition().add(0,60,10));
        if(time==20){
            var e=mc.level.getEntity(primaryId);check(e!=null&&MeteorAimClient.outlineColor(e)==MeteorAimClient.WHITE,"Unconfirmed target not white");
            check(mc.shouldEntityAppearGlowing(e)&&e.getTeamColor()==MeteorAimClient.WHITE&&!e.isCurrentlyGlowing(),"Preview is not a local-only white outline");capture("01-white-target-before-two-seconds.png");
        }
        if(time==35)server(p->{var s=MeteorTargeting.current(p);check(s!=null&&s.pick().entity()==decoy&&s.dwell()<20,"Changing target kept prior dwell");report.addProperty("target_switch_resets_dwell",true);});
        if(time==40)server(p->{for(int y=0;y<4;y++)p.serverLevel().setBlockAndUpdate(new BlockPos(0,(int)ground+y,5),Blocks.STONE.defaultBlockState());});
        if(time==48){capture("02-private-area-and-occlusion.png");server(p->{var s=MeteorTargeting.forCast(p);check(s.pick().entity()==null&&s.pick().valid()&&s.dwell()==0,"Ray selected an entity through solid blocks");report.addProperty("solid_occlusion_and_area_selection",true);});}
        if(time==50)server(p->{for(int y=0;y<4;y++)p.serverLevel().removeBlock(new BlockPos(0,(int)ground+y,5),false);});
        if(time==95){
            var e=mc.level.getEntity(primaryId);check(e!=null&&MeteorAimClient.outlineColor(e)==MeteorAimClient.RED&&e.getTeamColor()==MeteorAimClient.RED,"Two-second target not red");capture("03-red-locked-target.png");
            server(p->{var s=MeteorTargeting.current(p);check(s.locked()&&s.pick().entity()==target,"Server did not confirm forty ticks");check(!target.isCurrentlyGlowing()&&target.getTeam()==null,"Target hint leaked into shared entity/team state");report.addProperty("forty_tick_lock_and_private_red_outline",true);});
        }
        if(time==105)server(p->{var s=MeteorTargeting.forCast(p);check(!s.pick().valid()&&s.dwell()==0,"Looking at sky retained a lock or invented a target");report.addProperty("miss_clears_lock_without_auto_selection",true);});
        if(time==160)use();
        if(time==230){release();server(p->{moving=flight(p);check(moving.trackedTarget()==target,"Locked cast did not bind exact target UUID");check(MysteryEnergy.getEnergy(p)==90000,"Meteor cost changed");});}
        if(time==245)server(p->target.setPos(130.5,ground,14.5));
        if(time==260){capture("04-homing-flight.png");server(p->{check(moving.trackedTarget()==target&&moving.timedImpactPoint().distanceToSqr(target.position())<.1,"Moving target not followed: age="+moving.age(0)+" kind="+moving.kind()+" point="+moving.timedImpactPoint()+" target="+target.position()+" removed="+target.isRemoved()+" alive="+target.isAlive());check(moving.trackedTarget()!=decoy,"Moving the crosshair hijacked an in-flight meteor");report.addProperty("in_flight_target_updates_and_uuid_stability",true);});}
        if(time==335)server(p->{
            check(moving.hasImpacted()&&moving.position().distanceToSqr(target.position())<.1,"Meteor did not land at moved target");
            check(target.getHealth()==800&&decoy.getHealth()==1000,"Moving impact damaged the abandoned target area");report.addProperty("moving_impact_and_old_area_untouched",true);
        });
        if(time==350){capture("02b-ground-area.png");use();}
        if(time==420){release();server(p->{fixed=flight(p);check(fixed.trackedTarget()==null,"Area cast acquired an entity automatically");fixedPoint=fixed.timedImpactPoint();report.addProperty("area_cast_has_no_homing_target",true);});}
        if(time==430)server(p->target.setPos(.5,ground,18.5));
        if(time==510)server(p->{
            check(fixed.hasImpacted()&&fixed.position().distanceToSqr(fixedPoint)<.1,"Area cast retargeted after release");
            check(MysteryEnergy.getEnergy(p)==80000,"Two casts did not cost exactly 20000");
            long before=MysteryEnergy.getEnergy(p);check(StaffCasting.begin(p,InteractionHand.MAIN_HAND,p.getMainHandItem()),"Failed to begin miss probe");
            check(!StaffCasting.finish(p,p.getMainHandItem())&&MysteryEnergy.getEnergy(p)==before,"Empty-sky release spent energy or auto-attacked");
            report.addProperty("fixed_area_and_no_target_no_cost",true);
            lost=OutsiderSpell.spawn(p.serverLevel(),p,OutsiderSpell.METEOR,p.position().add(0,100,0),Vec3.ZERO);lost.setTrackedImpact(target,40);
        });
        if(time==520)server(p->{lostPoint=target.position();target.discard();});
        if(time==560)server(p->{check(lost.hasImpacted()&&lost.trackedTarget()==null&&lost.position().distanceToSqr(lostPoint)<.1,"Lost target was reacquired or did not use last point");report.addProperty("dead_target_falls_to_last_position",true);StaffSpell.EARTH_SPLIT.select(p.getMainHandItem());p.inventoryMenu.broadcastChanges();});
        if(time==570){
            check(!MeteorAimClient.preview().valid()&&MeteorAimClient.outlineColor(mc.level.getEntity(primaryId))<0,"Changing spell retained its local reference");capture("05-targeting-cleanup.png");
            server(p->{check(MeteorTargeting.current(p)==null,"Server aim state was not cleared");p.serverLevel().setChunkForced(8,0,false);report.addProperty("switch_spell_cleanup",true);});
        }
        if(time==580){
            report.addProperty("master_volume",mc.options.getSoundSourceVolume(SoundSource.MASTER));report.addProperty("shader_enabled",OutsiderShaderCompat.active());report.addProperty("fixture_cast_ticks",60);report.addProperty("shipped_default_cast_ticks",StaffSpell.ANNIHILATION.defaultCastTicks());
            try{Files.writeString(mc.gameDirectory.toPath().resolve("meteor-verification.json"),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(report));}catch(Exception error){throw new RuntimeException(error);}
            SourceOfMystery.LOGGER.info("STAFF_METEOR_OK");mc.stop();
        }
    }
}
