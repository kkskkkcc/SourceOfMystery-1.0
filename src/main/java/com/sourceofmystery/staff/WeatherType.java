package com.sourceofmystery.staff;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

public enum WeatherType {
    CLEAR("clear"),RAIN("rain"),THUNDER("thunder"),TORNADO("tornado");
    public static final String KEY="StaffWeather";
    public final String id;
    WeatherType(String id){this.id=id;}
    public Component title(){return Component.translatable("weather.sourceofmystery."+id);}
    public static WeatherType selected(ItemStack stack){String key=stack.hasTag()?stack.getTag().getString(KEY):"";for(var t:values())if(t.id.equals(key))return t;return CLEAR;}
    public long cost(){return this==TORNADO?StaffConfig.TORNADO_COST.get():StaffSpell.WEATHER_CONTROL.cost();}
}
