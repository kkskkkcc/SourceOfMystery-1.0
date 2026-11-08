package com.sourceofmystery.staff;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.*;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.*;
import java.util.*;

public final class StaffSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS=DeferredRegister.create(ForgeRegistries.SOUND_EVENTS,"sourceofmystery");
    private static final Map<String,RegistryObject<SoundEvent>> EVENTS=new HashMap<>();
    private static final Map<TornadoVariant,RegistryObject<SoundEvent>> WINDS=new EnumMap<>(TornadoVariant.class);
    static {
        for(StaffSpell spell:StaffSpell.values())for(String phase:List.of("charge","release")) {
            String name="staff."+spell.id()+"."+phase;
            EVENTS.put(name,SOUNDS.register(name,()->SoundEvent.createVariableRangeEvent(new ResourceLocation("sourceofmystery",name))));
        }
        for(var variant:TornadoVariant.values()){String name="weather.tornado."+variant.id;WINDS.put(variant,SOUNDS.register(name,()->SoundEvent.createVariableRangeEvent(new ResourceLocation("sourceofmystery",name))));}
    }
    private StaffSounds() {}
    public static SoundEvent event(StaffSpell spell,String phase){return EVENTS.get("staff."+spell.id()+"."+phase).get();}
    public static SoundEvent wind(TornadoVariant variant){return WINDS.get(variant).get();}
    public static void play(ServerLevel level,Vec3 point,StaffSpell spell,String phase){
        level.playSound(null,point.x,point.y,point.z,event(spell,phase),SoundSource.PLAYERS,1.25f,1);
    }
}
