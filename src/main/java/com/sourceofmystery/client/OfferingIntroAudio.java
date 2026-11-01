package com.sourceofmystery.client;

import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.util.RandomSource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.sound.PlaySoundEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Consistent cinematic Foley level for the viewer as the lens moves between shots. */
@Mod.EventBusSubscriber(modid="sourceofmystery",value=Dist.CLIENT)
public final class OfferingIntroAudio {
    private OfferingIntroAudio(){}
    @SubscribeEvent public static void sound(PlaySoundEvent event){
        if(!OfferingIntroDirector.active()||event.getSound()==null)return;
        SoundInstance sound=event.getSound();
        if(!sound.getLocation().getNamespace().equals("sourceofmystery"))return;
        float gain=switch(sound.getLocation().getPath()){
            case "archmage.heel_step" -> .90f;
            case "archmage.finger_snap" -> .95f;
            case "archmage.void_wake" -> .55f;
            case "archmage.void_break" -> .90f;
            case "archmage.void_seal" -> .70f;
            default -> -1;
        };
        if(gain<0)return;
        // PlaySoundEvent runs before SoundInstance.resolve; getPitch() would
        // dereference an unresolved Sound. Ritual cues are authored at pitch 1.
        event.setSound(new SimpleSoundInstance(sound.getLocation(),sound.getSource(),gain,1f,
                RandomSource.create(),false,0,SoundInstance.Attenuation.NONE,0,0,0,true));
    }
}
