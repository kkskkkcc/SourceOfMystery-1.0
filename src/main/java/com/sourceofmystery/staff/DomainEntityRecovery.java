package com.sourceofmystery.staff;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Reconcile short-lived domain visuals when an entity is saved outside ticking chunks. */
@Mod.EventBusSubscriber(modid="sourceofmystery")
public final class DomainEntityRecovery {
    public static final String OWNER="DomainDebrisOwner",TOKEN="DomainDebrisToken",GLOW="DomainPreviousGlow";
    private DomainEntityRecovery(){}
    @SubscribeEvent public static void join(EntityJoinLevelEvent event){
        if(!(event.getLevel() instanceof ServerLevel level))return;
        var entity=event.getEntity();var tag=entity.getPersistentData();
        if(tag.contains(GLOW)){entity.setGlowingTag(tag.getBoolean(GLOW));tag.remove(GLOW);}
        if(!(entity instanceof FallingBlockEntity))return;
        if(tag.hasUUID(OWNER)&&tag.hasUUID(TOKEN)){
            var player=level.getServer().getPlayerList().getPlayer(tag.getUUID(OWNER));var field=player==null?null:StaffDomains.field(player);
            if(field==null||field.type!=DomainType.DARKNESS||!field.token.equals(tag.getUUID(TOKEN))||!field.contains(entity.position())){event.setCanceled(true);entity.discard();}
            return;
        }
        for(var player:level.players()){
            var field=StaffDomains.field(player);
            if(field!=null&&field.type==DomainType.DARKNESS&&field.contains(entity.position())){
                tag.putUUID(OWNER,field.owner);tag.putUUID(TOKEN,field.token);break;
            }
        }
    }
}
