package com.sourceofmystery.enchanter;

import net.minecraftforge.common.ForgeConfigSpec;

public final class EnchanterConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.LongValue ENCHANT_BASE,REMOVE_BASE;
    static {
        var b=new ForgeConfigSpec.Builder();
        ENCHANT_BASE=b.comment("Energy = base * rarity weight factor * (target level squared - current level squared).")
                .defineInRange("enchantBaseCost",1000L,1L,1_000_000_000L);
        REMOVE_BASE=b.comment("Energy per removed level, multiplied by the enchantment rarity weight factor.","Curses can also be removed; no XP or lapis is required.")
                .defineInRange("disenchantBaseCost",100L,1L,1_000_000_000L);
        SPEC=b.build();
    }
    private EnchanterConfig() {}
}
