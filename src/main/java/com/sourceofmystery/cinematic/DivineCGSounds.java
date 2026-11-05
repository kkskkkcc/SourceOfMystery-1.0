package com.sourceofmystery.cinematic;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.*;
import java.util.*;

public final class DivineCGSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS=DeferredRegister.create(ForgeRegistries.SOUND_EVENTS,"sourceofmystery");
    private static final Map<String,RegistryObject<SoundEvent>> EVENTS=new HashMap<>();
    static {for(String name:List.of("ascent","cosmos","stairs","step","touch","bestowal","armor","throne"))
        EVENTS.put(name,SOUNDS.register("divine_cg."+name,()->SoundEvent.createVariableRangeEvent(new ResourceLocation("sourceofmystery","divine_cg."+name))));}
    public static SoundEvent event(String name){return EVENTS.get(name).get();}
    private DivineCGSounds(){}
}
