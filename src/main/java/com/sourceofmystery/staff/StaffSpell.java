package com.sourceofmystery.staff;

import com.sourceofmystery.entity.OutsiderRitual;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/** Stable saved IDs are independent of the wheel's presentation order. */
public enum StaffSpell {
    ANNIHILATION("annihilation", 10000, (int) (OutsiderRitual.ORB_START * 20)),
    EARTH_SPLIT("earth_split", 8000, 60),
    EARTH_LIFT("earth_lift", 1000, 60),
    TIME_STOP("time_stop", 1000, 0),
    SPACE_RIFT("space_rift", 0, 20),
    ENVIRONMENT_SHIFT("environment_shift", 2000, 80),
    DOMAIN_EXPANSION("domain_expansion", 5000, 40),
    WEATHER_CONTROL("weather_control",2000,60),
    EASTERN_RITE("eastern_rite",5000,EasternRite.MAX_CHARGE);

    public static final String SELECTION_KEY = "SelectedStaffSpell";
    private final String id;
    private final long defaultCost;
    private final int defaultCastTicks;

    StaffSpell(String id, long defaultCost, int defaultCastTicks) {
        this.id = id;
        this.defaultCost = defaultCost;
        this.defaultCastTicks = defaultCastTicks;
    }

    public String id() { return id; }
    public Component title() { return Component.translatable("spell.sourceofmystery." + (this==TIME_STOP?"time_magic":id)); }
    public long defaultCost() { return defaultCost; }
    public int defaultCastTicks() { return defaultCastTicks; }
    public long cost() { return StaffConfig.SPELLS.get(this).cost().get(); }
    public long cost(ItemStack stack){return this==TIME_STOP?TimeMagicType.selected(stack).cost():this==WEATHER_CONTROL?WeatherType.selected(stack).cost():cost();}
    public int castTicks() {
        int ticks=StaffConfig.SPELLS.get(this).castTicks().get();
        // Old worlds may retain60 here: keep Eastern's complete windup under80 ticks.
        return this==EASTERN_RITE?Math.min(EasternRite.MAX_CHARGE,ticks):ticks;
    }

    public static StaffSpell fromId(String id) {
        for (StaffSpell spell : values()) if (spell.id.equals(id)) return spell;
        return ANNIHILATION;
    }

    public static StaffSpell selected(ItemStack stack) {
        return stack.hasTag() ? fromId(stack.getTag().getString(SELECTION_KEY)) : ANNIHILATION;
    }

    public void select(ItemStack stack) {
        stack.getOrCreateTag().putString(SELECTION_KEY, id);
    }
}
