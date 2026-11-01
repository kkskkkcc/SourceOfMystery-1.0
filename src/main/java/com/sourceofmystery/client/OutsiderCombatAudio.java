package com.sourceofmystery.client;

import com.sourceofmystery.entity.*;
import com.sourceofmystery.sound.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.lang.ref.WeakReference;
import java.util.WeakHashMap;

/** Per-boss speech and ducked spatial gale. Uses existing synchronized voice cues. */
@Mod.EventBusSubscriber(modid="sourceofmystery",value=Dist.CLIENT)
public final class OutsiderCombatAudio {
    private static final WeakHashMap<OutsiderEntity,Track> TRACKS=new WeakHashMap<>();
    private static final class Track {long start=Long.MIN_VALUE;int voice=-1;Speech speech;Gale gale;}
    @SubscribeEvent public static void tick(LivingEvent.LivingTickEvent event){
        if(!(event.getEntity() instanceof OutsiderEntity boss)||!boss.level().isClientSide)return;
        Track track=TRACKS.computeIfAbsent(boss,key->new Track());
        if(track.start!=boss.voiceStartTick()||track.voice!=boss.voiceIndex()){
            track.start=boss.voiceStartTick();track.voice=boss.voiceIndex();
            if(track.speech!=null)Minecraft.getInstance().getSoundManager().stop(track.speech);
            if(track.voice>=0&&boss.voiceAge(0)<6){
                track.speech=new Speech(boss,track.voice,track.start);
                Minecraft.getInstance().getSoundManager().play(track.speech);
            }
        }
        float seconds=boss.animationSeconds(0);
        boolean charge=boss.isAlive()&&boss.combatState()==OutsiderEntity.METEOR&&seconds>=3&&seconds<22;
        if(charge&&(track.gale==null||track.gale.isStopped())){
            track.gale=new Gale(boss);Minecraft.getInstance().getSoundManager().play(track.gale);
        }
    }
    private static final class Speech extends AbstractTickableSoundInstance {
        private final WeakReference<OutsiderEntity> owner;private final long start;private final int voice;
        private int playedTicks;
        Speech(OutsiderEntity boss,int voice,long start){
            super(ModSounds.ARCHMAGE_VOICES.get(OutsiderVoice.IDS[voice]).get(),SoundSource.HOSTILE,RandomSource.create());
            owner=new WeakReference<>(boss);this.start=start;this.voice=voice;
            volume=voice==9||voice==10?1.65f:1.35f;pitch=1;x=boss.getX();y=boss.getY()+1.8;z=boss.getZ();
        }
        @Override public void tick(){
            OutsiderEntity boss=owner.get();
            if(++playedTicks>OutsiderVoice.TICKS[voice]+2){stop();return;}
            // Finish the death recording at the last position even after the
            // corpse's ordinary 20-tick lifetime ends.
            if(boss==null||boss.isRemoved()){if(voice!=12)stop();return;}
            if(boss.voiceStartTick()!=start||boss.voiceIndex()!=voice){stop();return;}
            x=boss.getX();y=boss.getY()+1.8;z=boss.getZ();
        }
    }
    private static final class Gale extends AbstractTickableSoundInstance {
        private final WeakReference<OutsiderEntity> owner;
        Gale(OutsiderEntity boss){
            super(ModSounds.ARCHMAGE_RITUAL_WIND.get(),SoundSource.HOSTILE,RandomSource.create());
            owner=new WeakReference<>(boss);looping=true;delay=0;volume=.01f;
            x=boss.getX();y=boss.getY()+2;z=boss.getZ();
        }
        @Override public boolean canStartSilent(){return true;}
        @Override public void tick(){
            OutsiderEntity boss=owner.get();
            if(boss==null||boss.isRemoved()||!boss.isAlive()||boss.combatState()!=OutsiderEntity.METEOR||boss.animationSeconds(0)>=22){stop();return;}
            float strength=OutsiderRitual.chargeEnvelope(boss.animationSeconds(0));
            int voice=boss.voiceIndex();boolean speaking=voice>=0&&boss.voiceAge(0)<OutsiderVoice.TICKS[voice];
            volume=(.12f+strength*1.35f)*(speaking?.32f:1);pitch=.86f+strength*.13f;
            x=boss.getX();y=boss.getY()+2;z=boss.getZ();
        }
    }
}
