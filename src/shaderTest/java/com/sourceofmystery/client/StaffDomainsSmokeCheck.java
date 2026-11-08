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
import net.minecraft.world.effect.*;
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
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.nio.file.Files;
import java.util.*;
import java.util.function.Consumer;

/** Real Forge input/network, live entity damage, actual five-minute expiry, and GPU captures. */
@Mod.EventBusSubscriber(modid="sourceofmystery",value=Dist.CLIENT)
public final class StaffDomainsSmokeCheck {
    private static int warmup,time,stage;private static volatile boolean ready;private static volatile Throwable failure;
    private static Mob target,outside,percentTarget;private static Vec3 beforePull;private static UUID previousToken;
    private static net.minecraft.nbt.CompoundTag debrisSaved;
    private static boolean denyBreak;private static int denied;
    private static OfferingMovieCapture movie;private static boolean movieDone;private static double maxFov,maxFaceAngle;private static int faceSamples;
    private static final com.google.gson.JsonObject report=new com.google.gson.JsonObject();
    private static final DomainType[] TYPES={DomainType.FROST,DomainType.DARKNESS,DomainType.GRAVITY,DomainType.EXECUTION,DomainType.EXECUTION,DomainType.BINDING};
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    private static void server(Consumer<ServerPlayer> action){var mc=Minecraft.getInstance();mc.getSingleplayerServer().execute(()->{try{action.accept(mc.getSingleplayerServer().getPlayerList().getPlayers().get(0));}catch(Throwable e){failure=e;}});}
    private static Mob mob(ServerPlayer p,double x,double z){
        var m=net.minecraft.world.entity.EntityType.HUSK.create(p.serverLevel());m.moveTo(x,65,z,180,0);m.setNoAi(true);m.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);m.setHealth(1000);m.getAttribute(Attributes.ARMOR).setBaseValue(0);p.serverLevel().addFreshEntity(m);return m;
    }
    private static void setup(ServerPlayer p,DomainType type){
        if(StaffDomains.active(p))StaffDomains.stop(p);p.stopUsingItem();if(target!=null)target.discard();if(outside!=null)outside.discard();if(percentTarget!=null)percentTarget.discard();percentTarget=null;
        p.teleportTo(p.serverLevel(),.5,65,.5,0,-10);p.setGameMode(type==DomainType.EXECUTION?GameType.SURVIVAL:GameType.CREATIVE);
        p.getAbilities().flying=type!=DomainType.EXECUTION;p.onUpdateAbilities();p.setHealth(20);
        for(int x=-4;x<=4;x++)for(int z=-4;z<=4;z++){
            p.serverLevel().setBlockAndUpdate(new BlockPos(x,64,z),Blocks.STONE_BRICKS.defaultBlockState());
            for(int y=65;y<=69;y++)p.serverLevel().setBlockAndUpdate(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState());
        }
        StaffSpell.DOMAIN_EXPANSION.select(p.getMainHandItem());p.getMainHandItem().getOrCreateTag().putString(DomainType.KEY,type.id);p.inventoryMenu.broadcastChanges();
        target=mob(p,type==DomainType.FROST||type==DomainType.GRAVITY?-4:7,type==DomainType.FROST||type==DomainType.GRAVITY?-5:6);outside=mob(p,type.radius+5,0);
        if(type==DomainType.EXECUTION){
            p.teleportTo(p.serverLevel(),400.5,65,400.5,0,-10);target.setPos(target.position().add(400,0,400));outside.setPos(outside.position().add(400,0,400));
            percentTarget=mob(p,415,412);percentTarget.getAttribute(Attributes.MAX_HEALTH).setBaseValue(2000);percentTarget.setHealth(2000);percentTarget.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE,2000,4));percentTarget.getAttribute(Attributes.ARMOR).setBaseValue(20);
            target.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE,2000,4));target.getAttribute(Attributes.ARMOR).setBaseValue(20);}
        if(type==DomainType.GRAVITY){target.addEffect(new MobEffectInstance(MobEffects.GLOWING,2000,1));outside.moveTo(5,65,-6,0,0);}
        denyBreak=type==DomainType.DARKNESS;denied=0;ready=true;
    }
    private static void use(){var mc=Minecraft.getInstance();mc.options.keyUse.setDown(true);mc.gameMode.useItem(mc.player,InteractionHand.MAIN_HAND);}
    private static void release(){var mc=Minecraft.getInstance();mc.options.keyUse.setDown(false);mc.gameMode.releaseUsingItem(mc.player);}
    private static void click(){var mc=Minecraft.getInstance();mc.options.keyUse.setDown(true);
        var event=new InputEvent.InteractionKeyMappingTriggered(1,mc.options.keyUse,InteractionHand.MAIN_HAND);
        if(!MinecraftForge.EVENT_BUS.post(event))mc.gameMode.useItem(mc.player,InteractionHand.MAIN_HAND);
    }
    private static void capture(String name){var mc=Minecraft.getInstance();Screenshot.grab(mc.gameDirectory,name,mc.getMainRenderTarget(),m->{});check(org.lwjgl.opengl.GL11.glGetError()==0,"GL error: "+name);}
    @SubscribeEvent(priority=net.minecraftforge.eventbus.api.EventPriority.LOWEST) public static void fov(net.minecraftforge.client.event.ViewportEvent.ComputeFov e){if(Boolean.getBoolean("sourceofmystery.staffDomainsCheck")&&stage==3&&DomainDirector.active())maxFov=Math.max(maxFov,e.getFOV());}
    @SubscribeEvent public static void prevent(BlockEvent.BreakEvent e){if(Boolean.getBoolean("sourceofmystery.staffDomainsCheck")&&denyBreak){denied++;e.setCanceled(true);}}
    @SubscribeEvent public static void render(TickEvent.RenderTickEvent e){
        if(Boolean.getBoolean("sourceofmystery.staffDomainsCheck")&&e.phase==TickEvent.Phase.END&&stage==3&&DomainDirector.active()){
            float t=DomainDirector.seconds();var mc=Minecraft.getInstance();
            if(t>1.9f&&t<2.3f){
                var camera=mc.gameRenderer.getMainCamera();Vec3 aim=mc.player.getPosition(mc.getFrameTime()).add(0,1.73,0).subtract(camera.getPosition());
                double cosine=aim.normalize().dot(Vec3.directionFromRotation(camera.getXRot(),camera.getYRot()));
                double angle=Math.toDegrees(Math.acos(Math.max(-1,Math.min(1,cosine))));
                check(angle<8,"Face close-up missed the actor: "+angle);maxFaceAngle=Math.max(maxFaceAngle,angle);faceSamples++;
            }
        }
        if(!Boolean.getBoolean("sourceofmystery.staffDomainsCheck")||!Boolean.getBoolean("sourceofmystery.offeringMovie")||e.phase!=TickEvent.Phase.END||stage!=3||movieDone||!DomainDirector.active())return;
        try{if(movie==null)movie=new OfferingMovieCapture(600,System.getProperty("sourceofmystery.offeringFfmpeg"),"domain-execution-silent.mp4");}catch(Exception err){throw new RuntimeException(err);}
        movie.capture(DomainDirector.seconds());
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent e){
        if(!Boolean.getBoolean("sourceofmystery.staffDomainsCheck")||e.phase!=TickEvent.Phase.END)return;
        var mc=Minecraft.getInstance();if(mc.player==null||mc.level==null||mc.getSingleplayerServer()==null)return;
        if(failure!=null)throw new AssertionError("Domain server verification",failure);
        mc.mouseHandler.releaseMouse();if(warmup++<80)return;
        if(!ready){if(warmup!=81)return;mc.options.getSoundSourceOptionInstance(SoundSource.MASTER).set(0d);mc.options.pauseOnLostFocus=false;mc.setScreen(null);
            server(p->{com.sourceofmystery.config.MysteryConfig.ENERGY_REGEN_PER_SECOND.set(0d);com.sourceofmystery.config.MysteryConfig.DAILY_REFRESH.set(false);p.serverLevel().getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,p.server);p.serverLevel().setDayTime(12500);p.serverLevel().setWeatherParameters(100000,0,false,false);
                p.getInventory().clearContent();p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(ModItems.DIMENSION_BREAKER_STAFF.get()));MysteryEnergy.setEnergyAndMax(p,100000);MysteryEnergy.setEnergy(p,100000);
                StaffSpell.DOMAIN_EXPANSION.select(p.getMainHandItem());MysteryEnergy.setEnergy(p,0);Set<DomainType> choices=new HashSet<>();p.setShiftKeyDown(true);
                for(int i=0;i<5;i++){p.getMainHandItem().getItem().use(p.level(),p,InteractionHand.MAIN_HAND);choices.add(DomainType.selected(p.getMainHandItem()));}p.setShiftKeyDown(false);check(choices.size()==5,"Domain cycle skipped a choice");
                check(MysteryEnergy.getEnergy(p)==0,"Domain cycle consumed energy");check(!StaffCasting.begin(p,InteractionHand.MAIN_HAND,p.getMainHandItem()),"Insufficient energy cast a domain");MysteryEnergy.setEnergy(p,100000);report.addProperty("five_shift_right_click_choices",true);setup(p,TYPES[0]);});return;
        }
        time++;
        if(!DomainDirector.active()){mc.player.setYRot(0);mc.player.yRotO=0;mc.player.setDeltaMovement(Vec3.ZERO);}
        for(var key:new net.minecraft.client.KeyMapping[]{mc.options.keyUp,mc.options.keyDown,mc.options.keyLeft,mc.options.keyRight,mc.options.keyJump,mc.options.keyShift})key.setDown(false);
        if(stage==0&&time==1)mc.setScreen(new com.sourceofmystery.client.staff.StaffWheelScreen(InteractionHand.MAIN_HAND));
        if(stage==0&&time==3)capture("00-seven-spell-wheel.png");
        if(stage==0&&time==5){var segment=StaffWheelLayout.segments(7).get(6);mc.screen.mouseClicked(mc.screen.width/2d+Math.cos(segment.angle())*segment.radius(),mc.screen.height/2d-9+Math.sin(segment.angle())*segment.radius(),0);}
        if(time==5){mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);mc.player.setXRot(-10);mc.player.xRotO=-10;}
        if(time==10)use();
        if(time==65)server(p->{var f=StaffDomains.field(p);check(f!=null&&f.type==TYPES[stage],"Native cast did not activate domain "+stage);check(f.remaining>5900,"Default domain is not five minutes");});
        if(stage==0){
            if(time==85)capture("01-frost-ice-and-snow.png");
            if(time==110)server(p->{check(StaffDomains.active(p),"Holding use auto-dismissed domain");check(MysteryEnergy.getEnergy(p)==95000,"Domain cost not 5000");
                check(target.hasEffect(DomainEffects.EXTREME_FREEZE.get()),"Extreme Freeze missing");check(!outside.hasEffect(DomainEffects.EXTREME_FREEZE.get()),"Frost escaped radius");
                check(target.getHealth()<=994&&target.getHealth()>=988,"Frost damage is not 3 per second: "+target.getHealth());
                Vec3 pos=target.position();target.move(MoverType.SELF,new Vec3(3,0,0));check(pos.distanceToSqr(target.position())<.0001,"Frozen entity moved");
                report.addProperty("frost_root_and_3hp_per_second",true);});
            if(time==120){release();mc.options.setCameraType(CameraType.FIRST_PERSON);mc.player.setXRot(3);mc.player.xRotO=3;}
            if(time==130)capture("02-frost-caster-first-person.png");
            if(time==145)server(p->{p.teleportTo(p.serverLevel(),220.5,65,.5,0,0);outside.discard();outside=mob(p,226,5);});
            if(time==165)server(p->{check(!target.hasEffect(DomainEffects.EXTREME_FREEZE.get())&&outside.hasEffect(DomainEffects.EXTREME_FREEZE.get()),"Moving frost domain did not follow caster/release old target");report.addProperty("moving_domain_membership",true);
                int snow=0;for(int x=175;x<266;x++)for(int z=-40;z<40;z++)if(p.serverLevel().getBlockState(new BlockPos(x,65,z)).is(Blocks.SNOW))snow++;check(snow>5,"No surface snow accumulation");report.addProperty("surface_snow_blocks",snow);});
            if(time==175)click();
            if(time==185){release();server(p->{check(!StaffDomains.active(p)&&!outside.hasEffect(DomainEffects.EXTREME_FREEZE.get()),"Early right-click did not clean frost");});check(!DomainVisuals.windActive(),"Cold wind did not stop");next();}
        }else if(stage==1){
            if(time==82)server(p->{check(denied>0,"No block-break compatibility event");check(p.serverLevel().getEntitiesOfClass(FallingBlockEntity.class,new AABB(-110,60,-110,110,100,110)).isEmpty(),"Cancelled break still spawned debris");denyBreak=false;beforePull=target.getBoundingBox().getCenter();});
            if(time==102)server(p->{double distance=beforePull.distanceTo(target.getBoundingBox().getCenter());check(distance>4.5&&distance<5.6,"Black-hole pull is not five blocks/sec: "+distance);check(target.getHealth()<=980,"Black-hole damage missing: "+target.getHealth());report.addProperty("black_hole_travel_in_one_second",distance);report.addProperty("block_break_cancellation_respected",true);});
            if(time==120){mc.options.setCameraType(CameraType.FIRST_PERSON);mc.player.setXRot(-72);mc.player.xRotO=-72;}
            if(time==125){release();capture("03-darkness-black-hole.png");server(p->{var debris=p.serverLevel().getEntitiesOfClass(FallingBlockEntity.class,new AABB(-110,60,-110,110,100,110));check(!debris.isEmpty(),"No low-grade blocks absorbed");
                check(debris.get(0).getPersistentData().hasUUID(DomainEntityRecovery.TOKEN),"Debris recovery receipt missing");debrisSaved=debris.get(0).saveWithoutId(new net.minecraft.nbt.CompoundTag());});}
            if(time==155)click();if(time==170){release();server(p->{check(!StaffDomains.active(p),"Darkness did not stop");var restored=new FallingBlockEntity(EntityType.FALLING_BLOCK,p.serverLevel());restored.load(debrisSaved);restored.setUUID(UUID.randomUUID());check(!p.serverLevel().addFreshEntity(restored),"Unloaded domain debris survived after dismissal");report.addProperty("unloaded_debris_cleanup",true);});next();}
        }else if(stage==2){
            if(time==105){release();capture("04-gravity-levitation.png");server(p->{check(target.hasEffect(MobEffects.LEVITATION)&&target.hasEffect(MobEffects.GLOWING),"Gravity effects missing");check(target.getY()>65.4,"Gravity did not lift entity");check(!p.hasEffect(MobEffects.LEVITATION),"Caster levitated");report.addProperty("gravity_lifts_and_glows",true);});}
            if(time==125)click();if(time==145){release();server(p->{check(!outside.hasEffect(MobEffects.LEVITATION)&&!outside.hasEffect(MobEffects.GLOWING),"Gravity effects lingered");check(!target.hasEffect(MobEffects.LEVITATION)&&target.hasEffect(MobEffects.GLOWING)&&target.getEffect(MobEffects.GLOWING).getAmplifier()==1,"Preexisting glowing was removed");report.addProperty("gravity_cleanup",true);});next();}
        }else if(stage==3){
            if(time==72){release();capture("05-execution-hand-seal.png");server(p->{check(!p.hurt(p.damageSources().generic(),1000)&&p.getHealth()==20,"CG caster is not invulnerable");});}
            if(time==92)capture("06-execution-face.png");
            if(time==155){capture("07-execution-wide-slashes.png");check(DomainDirector.active(),"CG exited early");server(p->{float loss=1000-target.getHealth();check(loss>=20&&loss<=40&&Math.abs(loss%10)<.01&&Math.abs(2000-percentTarget.getHealth()-2*loss)<.1,"True 1% damage did not bypass armor/resistance: "+loss);report.addProperty("execution_true_damage_through_resistance",loss);});}
            if(time==265){
                if(movie!=null&&!movieDone){movie.finish();movieDone=true;}
                check(!DomainDirector.active()&&mc.getCameraEntity()==mc.player,"CG camera not returned");check(Math.abs(maxFov-130)<.1,"CG did not reach FOV130: "+maxFov);report.addProperty("cg_measured_max_fov",maxFov);check(mc.options.getCameraType()==CameraType.THIRD_PERSON_FRONT,"CG camera type not restored");check(mc.player.getMainHandItem().is(ModItems.DIMENSION_BREAKER_STAFF.get()),"CG removed staff");
                check(faceSamples>0,"Face shot was skipped");report.addProperty("cg_face_center_max_angle_degrees",maxFaceAngle);
                capture("08-execution-camera-return.png");server(p->{check(StaffDomains.active(p),"Domain ended with its ten-second CG");check(p.hurt(p.damageSources().generic(),1),"CG invulnerability leaked");previousToken=StaffDomains.field(p).token;StaffDomains.stop(p);report.addProperty("cg_10_seconds_restore_staff_and_immunity",true);});
            }
            if(time==285)next();
        }else if(stage==4){
            if(time==78){release();ModNetwork.CHANNEL.sendToServer(new DomainStopPacket(previousToken));}
            if(time==88){check(DomainDirector.active(),"Stale token cancelled new CG");click();}
            if(time==105){release();check(!DomainDirector.active()&&mc.getCameraEntity()==mc.player,"Right-click failed during CG");server(p->{check(!StaffDomains.active(p),"CG cancellation failed on server");report.addProperty("cg_early_stop_and_stale_token_guard",true);});next();}
        }else if(stage==5){
            if(time==85){release();capture("09-binding-fixed-field.png");server(p->{p.teleportTo(p.serverLevel(),100.5,65,.5,0,0);check(StaffDomains.field(p).center.distanceToSqr(new Vec3(.5,65,.5))<.1,"Fixed domain moved");});}
            if(time==115)server(p->{check(target.hasEffect(DomainEffects.DOMAIN_BINDING.get()),"Detached binding did not persist");check(!outside.hasEffect(DomainEffects.DOMAIN_BINDING.get()),"Binding escaped diameter200");report.addProperty("binding_fixed_diameter_200",true);});
            if(time==140&&Boolean.getBoolean("sourceofmystery.domainVisualOnly")){server(StaffDomains::stop);report.addProperty("real_5_minute_expiry","NOT_RETESTED_VISUAL_RUN");finish();}
            if(time>=1200&&time%1200==0)server(p->{check(StaffDomains.active(p),"Binding expired before five minutes");SourceOfMystery.LOGGER.info("DOMAIN_DURATION_PROGRESS clientTick={} remaining={}",time,StaffDomains.field(p).remaining);});
            if(time==6030)server(p->check(StaffDomains.active(p),"Domain ended before 6000 ticks"));
            if(time==6080){server(p->{check(!StaffDomains.active(p)&&!target.hasEffect(DomainEffects.DOMAIN_BINDING.get()),"Five-minute expiry failed cleanup");report.addProperty("real_5_minute_expiry",true);});}
            if(time==6100)finish();
        }
    }
    private static void next(){release();stage++;time=0;server(p->setup(p,TYPES[stage]));}
    private static void finish(){
        var mc=Minecraft.getInstance();if(failure!=null)throw new AssertionError(failure);
        report.addProperty("master_volume",mc.options.getSoundSourceVolume(SoundSource.MASTER));report.addProperty("shader_enabled",OutsiderShaderCompat.active());
        try{Files.writeString(mc.gameDirectory.toPath().resolve("domain-verification.json"),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(report));}catch(Exception error){throw new RuntimeException(error);}
        SourceOfMystery.LOGGER.info("STAFF_DOMAINS_OK");mc.stop();
    }
}
