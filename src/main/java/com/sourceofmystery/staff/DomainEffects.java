package com.sourceofmystery.staff;

import net.minecraft.world.effect.*;
import net.minecraftforge.registries.*;

public final class DomainEffects {
    public static final DeferredRegister<MobEffect> EFFECTS=DeferredRegister.create(ForgeRegistries.MOB_EFFECTS,"sourceofmystery");
    public static final RegistryObject<MobEffect> EXTREME_FREEZE=EFFECTS.register("extreme_freeze",()->new Status(0x86d7ff));
    public static final RegistryObject<MobEffect> DOMAIN_BINDING=EFFECTS.register("domain_binding",()->new Status(0xe2be70));
    private DomainEffects(){}
    private static final class Status extends MobEffect {Status(int color){super(MobEffectCategory.HARMFUL,color);}}
}
