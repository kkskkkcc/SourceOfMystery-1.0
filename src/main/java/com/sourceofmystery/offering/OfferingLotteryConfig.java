package com.sourceofmystery.offering;

import net.minecraftforge.common.ForgeConfigSpec;
import java.util.List;

public final class OfferingLotteryConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> VALUES;
    public static final ForgeConfigSpec.DoubleValue GOOD_CHANCE_MULTIPLIER,LUCK_BONUS;
    static {
        var b = new ForgeConfigSpec.Builder();
        GOOD_CHANCE_MULTIPLIER=b.comment("Relative multiplier for the total chance of a reward worth more than the entire input stack.","1.10 means a 10% relative increase. Other rewards always retain nonzero probability.")
                .defineInRange("goodRewardChanceMultiplier",1.10,1d,10d);
        LUCK_BONUS=b.comment("Additional relative good-reward chance per point of the player's Luck attribute.","Includes vanilla Luck/Bad Luck effects and modded equipment. Negative luck reduces the chance.")
                .defineInRange("luckBonusPerPoint",.10,0d,1d);
        VALUES = b.comment("Optional unit-value overrides, e.g. othermod:rare_crystal=2500. Positive integers only.",
                "Unlisted items are valued automatically. Overrides never exclude items from the lottery.")
                .defineListAllowEmpty("valueOverrides", List.of(), v -> {
                    if (!(v instanceof String s)) return false;
                    try { var p=s.split("=",2); return p.length==2 && net.minecraft.resources.ResourceLocation.tryParse(p[0])!=null && Long.parseLong(p[1])>0 && Long.parseLong(p[1])<=1_000_000_000L; }
                    catch (RuntimeException e) { return false; }
                });
        SPEC=b.build();
    }
    private OfferingLotteryConfig() {}
}
