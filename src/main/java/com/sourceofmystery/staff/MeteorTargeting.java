package com.sourceofmystery.staff;

import com.sourceofmystery.entity.OutsiderSpell;
import com.sourceofmystery.item.DimensionBreakerStaffItem;
import com.sourceofmystery.network.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;
import java.util.*;

/** A server-confirmed crosshair ray, not a nearest-mob search. Preview packets go only to their caster. */
@Mod.EventBusSubscriber(modid="sourceofmystery")
public final class MeteorTargeting {
    public static final int LOCK_TICKS=40;
    public static final double RANGE=128;
    public record Pick(Entity entity,Vec3 point) { public boolean valid(){return point!=null;} }
    public record Selection(Pick pick,int dwell) {public boolean locked(){return pick.entity()!=null&&dwell>=LOCK_TICKS;} }
    private record State(net.minecraft.world.level.Level level,Selection selection){}
    private static final Map<UUID,State> AIM=new HashMap<>();
    private MeteorTargeting(){}
    public static boolean enabled(Player player){
        if(!player.isAlive()||player.isSpectator()||TimeStop.frozen(player))return false;
        if(player.isUsingItem())return selected(player.getUseItem());
        return selected(player.getMainHandItem())||selected(player.getOffhandItem());
    }
    private static boolean selected(net.minecraft.world.item.ItemStack stack){
        return stack.getItem() instanceof DimensionBreakerStaffItem&&StaffSpell.selected(stack)==StaffSpell.ANNIHILATION;
    }
    public static Pick pick(Player player,float partial){
        Vec3 from=player.getEyePosition(partial),end=from.add(player.getViewVector(partial).scale(RANGE));
        var block=player.level().clip(new ClipContext(from,end,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,player));
        double distance=block.getType()==HitResult.Type.BLOCK?from.distanceToSqr(block.getLocation()):RANGE*RANGE;
        var entity=ProjectileUtil.getEntityHitResult(player,from,end,player.getBoundingBox().expandTowards(end.subtract(from)).inflate(1),
                e->e.isAlive()&&e.isPickable()&&!e.isSpectator()&&!(e instanceof OutsiderSpell)&&!(e instanceof Marker)&&!e.hasPassenger(player),distance);
        if(entity!=null&&from.distanceToSqr(entity.getLocation())<distance)return new Pick(entity.getEntity(),entity.getEntity().position());
        return block.getType()==HitResult.Type.BLOCK?new Pick(null,block.getLocation()):new Pick(null,null);
    }
    private static boolean same(Pick a,Pick b){return a.entity()!=null&&b.entity()!=null&&a.entity().getUUID().equals(b.entity().getUUID());}
    private static Selection update(ServerPlayer player,boolean advance){
        Pick current=pick(player,1);var old=AIM.get(player.getUUID());
        int dwell=current.entity()==null?0:old!=null&&old.level==player.level()&&same(old.selection.pick,current)
                ?Math.min(LOCK_TICKS,old.selection.dwell+(advance?1:0)):advance?1:0;
        Selection selection=new Selection(current,dwell);AIM.put(player.getUUID(),new State(player.level(),selection));
        return selection;
    }
    /** Refresh at release so an aim change on the final use tick cannot use a stale lock. */
    public static Selection forCast(ServerPlayer player){return update(player,false);}
    public static Selection current(ServerPlayer player){var s=AIM.get(player.getUUID());return s==null?null:s.selection;}
    private static void send(ServerPlayer player,Selection s){
        var p=s.pick;var e=p.entity;
        ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(()->player),new MeteorAimPacket(
                p.point==null?-2:e==null?-1:e.getId(),e==null?null:e.getUUID(),p.point==null?Vec3.ZERO:p.point,s.dwell));
    }
    @SubscribeEvent public static void tick(TickEvent.PlayerTickEvent event){
        if(event.phase!=TickEvent.Phase.START||!(event.player instanceof ServerPlayer p))return;
        if(!enabled(p)){
            if(AIM.remove(p.getUUID())!=null)send(p,new Selection(new Pick(null,null),0));return;
        }
        var before=current(p);var now=update(p,true);
        if(p.tickCount%3==0||before==null||!Objects.equals(before.pick.entity,now.pick.entity)||before.locked()!=now.locked())send(p,now);
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event){AIM.remove(event.getEntity().getUUID());}
    @SubscribeEvent public static void changed(PlayerEvent.PlayerChangedDimensionEvent event){AIM.remove(event.getEntity().getUUID());}
    @SubscribeEvent public static void stopped(ServerStoppedEvent event){AIM.clear();}
}
