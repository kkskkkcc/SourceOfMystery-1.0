package com.sourceofmystery.staff;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/** Stable IDs live on each staff; all five fields use the latest user-specified diameter 200. */
public enum DomainType {
    FROST("frost",100,true,0x91dcff),DARKNESS("darkness",100,true,0x8658f5),
    GRAVITY("gravity",100,true,0xc2a6ff),BINDING("binding",100,false,0xe9bd6b),
    EXECUTION("execution",100,true,0xe12046);
    public static final String KEY="StaffDomain";
    public final String id;public final int radius,color;public final boolean follows;
    DomainType(String id,int radius,boolean follows,int color){this.id=id;this.radius=radius;this.follows=follows;this.color=color;}
    public Component title(){return Component.translatable("domain.sourceofmystery."+id);}
    public static DomainType selected(ItemStack stack){
        String id=stack.hasTag()?stack.getTag().getString(KEY):"";
        for(var type:values())if(type.id.equals(id))return type;
        return FROST;
    }
    public static DomainType index(int index){return index>=0&&index<values().length?values()[index]:FROST;}
}
