package com.sourceofmystery.staff;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/** Subtypes share the legacy time_stop spell slot, saved ID and casting animation. */
public enum TimeMagicType {
    STOP("stop", 0), SLOW("slow", .1), HASTE("haste", 5);
    public static final String KEY="SelectedTimeMagic";
    public final String id;
    public final double speed;
    TimeMagicType(String id,double speed){this.id=id;this.speed=speed;}
    public Component title(){return Component.translatable("time_magic.sourceofmystery."+id);}
    public long cost(){return switch(this){case STOP->StaffSpell.TIME_STOP.cost();case SLOW->StaffConfig.TIME_SLOW_COST.get();case HASTE->StaffConfig.TIME_HASTE_COST.get();};}
    public static TimeMagicType selected(ItemStack stack){String id=stack.hasTag()?stack.getTag().getString(KEY):"";for(var type:values())if(type.id.equals(id))return type;return STOP;}
    public void select(ItemStack stack){stack.getOrCreateTag().putString(KEY,id);}
}
