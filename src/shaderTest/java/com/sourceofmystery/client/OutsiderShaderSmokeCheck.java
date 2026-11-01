package com.sourceofmystery.client;

import com.sourceofmystery.entity.*;
import com.sourceofmystery.SourceOfMystery;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Packaged only in the separate shader-check JAR, never in the release mod. */
@Mod.EventBusSubscriber(modid="sourceofmystery",value=Dist.CLIENT)
public final class OutsiderShaderSmokeCheck {
    private static int ticks;
    private static OutsiderEntity boss;
    private static volatile boolean ready;
    private static void state(int value,int age)throws Exception{
        var method=OutsiderEntity.class.getDeclaredMethod("state",int.class);method.setAccessible(true);method.invoke(boss,value);
        var field=OutsiderEntity.class.getDeclaredField("START");field.setAccessible(true);
        @SuppressWarnings("unchecked") var key=(net.minecraft.network.syncher.EntityDataAccessor<Long>)field.get(null);
        boss.getEntityData().set(key,boss.level().getGameTime()-age);
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event){
        if(!Boolean.getBoolean("sourceofmystery.shaderSmoke")||event.phase!=TickEvent.Phase.END)return;
        var mc=Minecraft.getInstance();if(mc.player==null||mc.level==null||mc.getSingleplayerServer()==null)return;
        try {
            if(ticks==0){
                ticks=1;mc.getSingleplayerServer().execute(()->{
                    try {
                        var level=mc.getSingleplayerServer().overworld();
                        level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,level.getServer());
                        level.getGameRules().getRule(GameRules.RULE_MOBGRIEFING).set(false,level.getServer());
                        level.setDayTime(6000);
                        for(var entity:com.google.common.collect.Lists.newArrayList(level.getAllEntities()))
                            if(entity!=null&&!(entity instanceof net.minecraft.server.level.ServerPlayer))entity.discard();
                        for(int x=-16;x<=16;x++)for(int z=-16;z<=16;z++)level.setBlock(new BlockPos(x,199,z),Blocks.STONE.defaultBlockState(),2);
                        for(int x=-5;x<=5;x++)for(int z=-5;z<=5;z++)for(int y=200;y<=230;y++)level.setBlock(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState(),2);
                        var player=level.getServer().getPlayerList().getPlayers().get(0);player.setGameMode(GameType.CREATIVE);
                        player.getAbilities().flying=true;player.onUpdateAbilities();player.teleportTo(level,0,201.6,5,180,10);
                        boss=new OutsiderEntity(ModEntities.OUTSIDER.get(),level);boss.setPos(0,200,0);boss.setYRot(0);
                        level.addFreshEntity(boss);state(OutsiderEntity.GROUND,0);
                        for(String name:new String[]{"cooldown","groundTime"}){var f=OutsiderEntity.class.getDeclaredField(name);f.setAccessible(true);f.setInt(boss,100000);}
                        ready=true;
                    }catch(Throwable error){error.printStackTrace();System.exit(1);}
                });return;
            }
            if(!ready)return;ticks++;
            if(ticks==120){
                if(!OutsiderShaderCompat.active())throw new AssertionError("Configured Oculus shader pack is not active");
                capture("01-model.png");
            }
            if(ticks==130)mc.getSingleplayerServer().execute(()->{try{
                mc.getSingleplayerServer().getPlayerList().getPlayers().get(0).teleportTo(mc.getSingleplayerServer().overworld(),0,205,32,180,-4);
                state(OutsiderEntity.METEOR,240);
            }catch(Exception error){throw new RuntimeException(error);}});
            if(ticks==160)capture("02-ritual.png");
            if(ticks==180)mc.getSingleplayerServer().execute(()->{
                try{
                    state(OutsiderEntity.GROUND,0);var level=mc.getSingleplayerServer().overworld();
                    var player=level.getServer().getPlayerList().getPlayers().get(0);
                    player.teleportTo(level,0,430,220,180,38);
                    var sky=OutsiderSpell.spawn(level,boss,OutsiderSpell.SKY_SEAL,new Vec3(0,255,0),Vec3.ZERO);
                    var field=OutsiderSpell.class.getDeclaredField("START");field.setAccessible(true);
                    @SuppressWarnings("unchecked") var key=(net.minecraft.network.syncher.EntityDataAccessor<Long>)field.get(null);
                    sky.getEntityData().set(key,level.getGameTime()-40);
                }catch(Exception error){throw new RuntimeException(error);}
            });
            if(ticks==215)capture("03-sky-seal.png");
            if(ticks==230)mc.getSingleplayerServer().execute(()->{
                var level=mc.getSingleplayerServer().overworld();var player=level.getServer().getPlayerList().getPlayers().get(0);
                player.teleportTo(level,0,260,100,180,3);
                var meteor=OutsiderSpell.spawn(level,boss,OutsiderSpell.METEOR,new Vec3(0,255,0),Vec3.ZERO);
                meteor.setTimedImpact(new Vec3(0,230,0),100);
            });
            if(ticks==260)capture("04-meteor.png");
            if(ticks==270)mc.getSingleplayerServer().execute(()->{
                try{
                    var level=mc.getSingleplayerServer().overworld();
                    level.getServer().getPlayerList().getPlayers().get(0).teleportTo(level,0,204,18,180,10);
                    state(OutsiderEntity.GRASP,40);
                    OutsiderSpell.spawn(level,boss,OutsiderSpell.BOLT,new Vec3(3,202,0),Vec3.ZERO);
                    OutsiderSpell.spawn(level,boss,OutsiderSpell.PILLAR,new Vec3(-4,200,0),Vec3.ZERO);
                }catch(Exception error){throw new RuntimeException(error);}
            });
            if(ticks==310)capture("05-barrage-pillar.png");
            if(ticks==320)mc.getSingleplayerServer().execute(()->{try{
                var level=mc.getSingleplayerServer().overworld();
                for(var entity:com.google.common.collect.Lists.newArrayList(level.getAllEntities()))
                    if(entity instanceof OutsiderSpell)entity.discard();
                boss.setPos(0,210,0);
                mc.getSingleplayerServer().getPlayerList().getPlayers().get(0).teleportTo(level,0,213,18,180,4);
                state(OutsiderEntity.METEOR,170);
            }catch(Exception error){throw new RuntimeException(error);}});
            if(ticks==360)capture("06-charge-wind.png");
            if(ticks==380)mc.getSingleplayerServer().execute(()->{try{
                state(OutsiderEntity.GROUND,0);boss.setPos(0,200,0);boss.setDeltaMovement(Vec3.ZERO);
                mc.getSingleplayerServer().getPlayerList().getPlayers().get(0).teleportTo(mc.getSingleplayerServer().overworld(),0,201.05,1.45,180,0);
                var method=OutsiderEntity.class.getDeclaredMethod("speak",int.class,boolean.class);method.setAccessible(true);method.invoke(boss,0,true);
            }catch(Exception error){throw new RuntimeException(error);}});
            if(ticks==394){capture("07-speech-confident.png");checkSpeech(0);}
            if(ticks==400)mc.getSingleplayerServer().execute(()->{try{
                var method=OutsiderEntity.class.getDeclaredMethod("speak",int.class,boolean.class);method.setAccessible(true);method.invoke(boss,9,true);
            }catch(Exception error){throw new RuntimeException(error);}});
            if(ticks==430){capture("08-speech-declaration.png");checkSpeech(9);}
            if(ticks==480)capture("09-speech-shout.png");
            if(ticks==510){capture("10-speech-tail.png");checkSpeech(9);}
            if(ticks==550){SourceOfMystery.LOGGER.info("OCULUS_SMOKE_OK shader_pack_active=true model/ritual/sky/meteor/barrage/pillar/charge_wind/speech_faces screenshots saved voice_playback=true");mc.stop();}
        }catch(Throwable error){error.printStackTrace();System.exit(1);}
    }
    private static void capture(String name){
        var mc=Minecraft.getInstance();Screenshot.grab(mc.gameDirectory,name,mc.getMainRenderTarget(),message->SourceOfMystery.LOGGER.info("Shader smoke screenshot: {}",message.getString()));
        int error=org.lwjgl.opengl.GL11.glGetError();if(error!=0)throw new AssertionError("OpenGL error "+error);
    }
    private static void checkSpeech(int voice)throws ReflectiveOperationException{
        var mc=Minecraft.getInstance();var clientBoss=(OutsiderEntity)mc.level.getEntity(boss.getId());
        var tracks=OutsiderCombatAudio.class.getDeclaredField("TRACKS");tracks.setAccessible(true);
        var track=((java.util.Map<?,?>)tracks.get(null)).get(clientBoss);
        if(track==null||clientBoss.voiceIndex()!=voice)throw new AssertionError("Client did not receive speech cue");
        var speech=track.getClass().getDeclaredField("speech");speech.setAccessible(true);
        var sound=(net.minecraft.client.resources.sounds.SoundInstance)speech.get(track);
        if(sound==null||!mc.getSoundManager().isActive(sound))throw new AssertionError("Client speech is not playing: "+voice);
        SourceOfMystery.LOGGER.info("VOICE_PLAYBACK_OK voice={} age={}",voice,clientBoss.voiceAge(0));
    }
}
