package com.sourceofmystery.client;

import com.sourceofmystery.SourceOfMystery;
import com.sourceofmystery.entity.ModEntities;
import com.sourceofmystery.entity.OutsiderEntity;
import net.minecraft.client.*;
import net.minecraft.client.gui.screens.AccessibilityOnboardingScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.*;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.player.ChatVisiblity;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.nio.file.Files;
import java.util.*;

/** Isolated, muted client run that drives the Outsider through blink, flinch, phase two and its skills. */
@Mod.EventBusSubscriber(modid="sourceofmystery",value=Dist.CLIENT)
public final class OutsiderClientCheck {
    private static final String[] NAMES={"AIR","DESCEND","LAND","GROUND","TAKEOFF","GRASP","SILENCE","METEOR","METEOR_PREP"};
    private static final int HUG_START=240, HUG_END=420, HIT=440, PHASE=520, END=2600;
    private static boolean opened,started;
    private static volatile boolean ready;
    private static volatile OutsiderEntity boss;
    private static int ticks,warm,frames,lastState=-1;
    private static float maxFlinch;
    private static boolean sawPhase,sawBlink;
    private static final List<String> events=new ArrayList<>();
    private static double lastX,lastZ;

    private static void log(String line){events.add(ticks+" "+line);SourceOfMystery.LOGGER.info("OUTSIDER_CHECK {} {}",ticks,line);}
    private static void capture(String name){
        var mc=Minecraft.getInstance();frames++;mc.getToasts().clear();
        Screenshot.grab(mc.gameDirectory,name,mc.getMainRenderTarget(),m->{});
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event){
        if(!Boolean.getBoolean("sourceofmystery.outsiderCheck")||event.phase!=TickEvent.Phase.END)return;
        var mc=Minecraft.getInstance();
        try {
            if(mc.screen instanceof AccessibilityOnboardingScreen){mc.options.onboardAccessibility=false;mc.options.save();mc.setScreen(new TitleScreen(true));return;}
            if(!opened&&mc.screen instanceof TitleScreen){
                opened=true;mc.options.pauseOnLostFocus=false;mc.options.renderDistance().set(10);mc.options.guiScale().set(3);
                mc.options.getSoundSourceOptionInstance(SoundSource.MASTER).set(0.0);
                mc.options.chatVisibility().set(ChatVisiblity.HIDDEN);mc.options.tutorialStep=net.minecraft.client.tutorial.TutorialSteps.NONE;
                String name="outsider-check-"+System.currentTimeMillis();
                mc.createWorldOpenFlows().createFreshLevel(name,new LevelSettings(name,GameType.SURVIVAL,false,Difficulty.NORMAL,true,new GameRules(),WorldDataConfiguration.DEFAULT),
                        new WorldOptions(4096,false,false),r->r.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions());
                return;
            }
            if(mc.player==null||mc.level==null||mc.getSingleplayerServer()==null)return;
            var server=mc.getSingleplayerServer();
            if(!started){
                started=true;server.execute(()->{
                    var p=server.getPlayerList().getPlayers().get(0);ServerLevel level=p.serverLevel();
                    level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,server);
                    level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false,server);
                    level.setDayTime(6000);level.setWeatherParameters(100000,0,false,false);
                    for(int x=-40;x<=40;x++)for(int z=-40;z<=40;z++)level.setBlock(new BlockPos(x,199,z),Blocks.SMOOTH_STONE.defaultBlockState(),18);
                    p.getAbilities().invulnerable=true;p.getAbilities().mayfly=true;p.getAbilities().flying=true;p.onUpdateAbilities();
                    p.teleportTo(level,.5,201,.5,0,10);
                    var o=ModEntities.OUTSIDER.get().create(level);
                    o.moveTo(.5,204,14.5,180,0);
                    o.finalizeSpawn(level,level.getCurrentDifficultyAt(o.blockPosition()),MobSpawnType.COMMAND,null,null);
                    level.addFreshEntity(o);boss=o;ready=true;
                });return;
            }
            if(!ready||warm++<300)return;ticks++;
            OutsiderEntity o=boss;
            if(o==null||!o.isAlive()){log("boss gone");finish(mc);return;}
            int state=o.combatState();
            if(state!=lastState){log("state "+(lastState<0?"-":NAMES[lastState])+" -> "+NAMES[state]+" hp="+(int)o.getHealth());lastState=state;}
            double jump=Math.hypot(o.getX()-lastX,o.getZ()-lastZ);
            if(ticks>1&&jump>4){sawBlink=true;log(String.format("blink %.1f blocks",jump));}
            lastX=o.getX();lastZ=o.getZ();
            maxFlinch=Math.max(maxFlinch,o.flinchAmount(0));
            if(!sawPhase&&o.secondPhase()){sawPhase=true;log("phase two hp="+(int)o.getHealth());}
            // Camera follows the boss from the player's position.
            mc.options.setCameraType(CameraType.FIRST_PERSON);
            if(ticks>=HUG_START&&ticks<HUG_END)server.execute(()->{var p=server.getPlayerList().getPlayers().get(0);
                p.teleportTo(p.serverLevel(),o.getX()+2,o.getY(),o.getZ(),p.getYRot(),p.getXRot());});
            else if(ticks==HUG_END)server.execute(()->{var p=server.getPlayerList().getPlayers().get(0);p.teleportTo(p.serverLevel(),.5,201,.5,0,0);});
            if(ticks==HIT)server.execute(()->{var p=server.getPlayerList().getPlayers().get(0);
                log("hit accepted="+o.hurt(p.damageSources().playerAttack(p),200));});
            if(ticks==HIT+3)log(String.format("flinch amount %.2f",o.flinchAmount(0)));
            if(ticks==PHASE)server.execute(()->o.setHealth(o.getMaxHealth()*.39f));
            var pl=mc.player;var d=o.position().add(0,1.4,0).subtract(pl.getEyePosition());
            pl.setYRot((float)(Math.atan2(d.z,d.x)*180/Math.PI)-90);pl.setXRot((float)(-Math.atan2(d.y,Math.hypot(d.x,d.z))*180/Math.PI));
            if(ticks==HIT+2)capture("o-flinch.png");
            if(ticks==PHASE+40)capture("o-phase-two.png");
            if(ticks%200==0)capture(String.format("o-%04d-%s.png",ticks,NAMES[state]));
            if(ticks>=END)finish(mc);
        }catch(Throwable failure){failure.printStackTrace();System.exit(1);}
    }
    private static void finish(Minecraft mc){
        try{
            events.add(String.format("summary blink=%s maxFlinch=%.2f phaseTwo=%s frames=%d",sawBlink,maxFlinch,sawPhase,frames));
            Files.write(mc.gameDirectory.toPath().resolve("outsider-check.txt"),events);
            SourceOfMystery.LOGGER.info("OUTSIDER_CHECK_DONE");
        }catch(Exception e){e.printStackTrace();}
        mc.stop();
    }
}
