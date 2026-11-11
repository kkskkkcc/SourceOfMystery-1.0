package com.sourceofmystery.staff;

import com.sourceofmystery.client.staff.StaffVisuals;
import com.sourceofmystery.energy.MysteryEnergy;
import com.sourceofmystery.mixin.ServerLevelTimeAccess;
import com.sourceofmystery.network.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.*;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.*;
import net.minecraftforge.event.level.*;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;
import java.util.*;
import java.util.function.BooleanSupplier;

/** Dimension-local simulation. The server/network clock and exempt players stay at 20 Hz. */
@Mod.EventBusSubscriber(modid="sourceofmystery")
public final class TimeStop {
    private static final Map<Level,Clock> CLOCKS=new java.util.concurrent.ConcurrentHashMap<>();
    private static final String COOLDOWN="staff_time_stop_ready";
    private TimeStop(){}
    private static Clock clock(ServerLevel level){
        return CLOCKS.computeIfAbsent(level,key->{var c=level.getDataStorage().computeIfAbsent(Clock::load,Clock::new,"sourceofmystery_time_stop");c.level=level;return c;});
    }
    public static boolean active(Level level){if(level.isClientSide)return StaffVisuals.timeStopped();var c=CLOCKS.get(level);return c!=null&&c.caster!=null&&c.mode==TimeMagicType.STOP;}
    public static boolean altered(Level level){var c=CLOCKS.get(level);return c!=null&&c.caster!=null;}
    public static boolean rateControlled(Level level){var c=CLOCKS.get(level);return c!=null&&c.caster!=null&&c.mode!=TimeMagicType.STOP;}
    public static boolean inside(ServerLevel level){var c=CLOCKS.get(level);return c!=null&&c.inner;}
    public static boolean rawTimeOnly(ServerLevel level){var c=CLOCKS.get(level);return c!=null&&c.rawTimeOnly;}
    public static boolean frozen(Entity e){if(e.level().isClientSide)return StaffVisuals.frozen(e);var c=CLOCKS.get(e.level());return c!=null&&c.caster!=null&&c.mode==TimeMagicType.STOP&&!c.caster.equals(e.getUUID());}
    public static boolean skipEntityTick(Entity e){
        if(frozen(e))return true;
        var c=CLOCKS.get(e.level());return c!=null&&c.caster!=null&&c.mode!=TimeMagicType.STOP&&e instanceof Player&&!c.tickedPlayers.add(e.getUUID());
    }
    public static long gameTime(Level level,long raw){var c=CLOCKS.get(level);return c==null?raw:c.caster!=null?c.frozenGame:raw-c.gameOffset;}
    public static long dayTime(Level level,long raw){var c=CLOCKS.get(level);return c==null?raw:c.caster!=null?c.frozenDay:raw-c.dayOffset;}
    public static boolean canCast(ServerPlayer p){return !altered(p.level())&&serverTime(p)>=MysteryEnergy.getPlayerData(p).getLong(COOLDOWN);}
    private static long serverTime(ServerPlayer p){return p.server.overworld().getLevelData().getGameTime();}
    public static void cycle(ServerPlayer p,net.minecraft.world.item.ItemStack stack){var values=TimeMagicType.values();var next=values[(TimeMagicType.selected(stack).ordinal()+1)%values.length];next.select(stack);p.inventoryMenu.broadcastChanges();p.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.sourceofmystery.staff.time_selected",next.title()),true);}
    public static boolean start(ServerPlayer p){return start(p,TimeMagicType.STOP);}
    public static boolean start(ServerPlayer p,TimeMagicType mode){
        if(!canCast(p))return StaffCasting.fail(p,"time_cooldown");
        var level=p.serverLevel();var c=clock(level);c.frozenGame=level.getGameTime();c.frozenDay=level.getDayTime();
        c.caster=p.getUUID();c.token=UUID.randomUUID();c.mode=mode;c.phase=0;c.setDirty();
        sync(c,null);return true;
    }
    public static boolean stop(ServerPlayer p){var c=CLOCKS.get(p.level());if(c==null||!p.getUUID().equals(c.caster))return false;finish(c);return true;}
    private static void finish(Clock c){
        UUID caster=c.caster;c.gameOffset=c.level.getLevelData().getGameTime()-c.frozenGame;c.dayOffset=c.level.getLevelData().getDayTime()-c.frozenDay;
        c.caster=null;c.setDirty();var p=c.level.getServer().getPlayerList().getPlayer(caster);
        if(p!=null)MysteryEnergy.getPlayerData(p).putLong(COOLDOWN,serverTime(p)+StaffConfig.TIME_STOP_COOLDOWN.get());
        send(c,new TimeMagicSyncPacket(c.level.dimension().location(),c.token,caster,c.mode.ordinal(),0,0,c.level.getGameTime(),c.level.getDayTime()),null);
    }
    private static void send(Clock c,TimeMagicSyncPacket packet,ServerPlayer player){ModNetwork.CHANNEL.send(player==null?PacketDistributor.DIMENSION.with(()->c.level.dimension()):PacketDistributor.PLAYER.with(()->player),packet);}
    private static void sync(Clock c,ServerPlayer player){if(c.caster!=null)send(c,new TimeMagicSyncPacket(c.level.dimension().location(),c.token,c.caster,c.mode.ordinal(),TimeMagicSyncPacket.INDEFINITE,c.phase,c.frozenGame,c.frozenDay),player);}

    /** Called in place of the complete dimension tick, including Forge level tick events. */
    public static void tickDimension(ServerLevel level,BooleanSupplier haveTime){
        var c=clock(level);c.tickedPlayers.clear();
        // Overworld owns the shared raw clock. Advance it ONCE, so other dimensions
        // and server bookkeeping never inherit this dimension's multiplier.
        c.rawTimeOnly=true;try{((ServerLevelTimeAccess)level).som$tickTime();}finally{c.rawTimeOnly=false;}
        c.inner=true;
        try{
            for(var player:List.copyOf(level.players()))if(!player.isRemoved()){
                if(player.isPassenger())((ServerLevelTimeAccess)level).som$tickPassenger(player.getVehicle(),player);
                else level.tickNonPassenger(player);
            }
            int steps=c.mode==TimeMagicType.HASTE?5:++c.phase==10?1:0;
            if(c.phase==10)c.phase=0;
            for(int i=0;i<steps;i++){
                net.minecraftforge.event.ForgeEventFactory.onPreLevelTick(level,haveTime);
                level.tick(haveTime);
                net.minecraftforge.event.ForgeEventFactory.onPostLevelTick(level,haveTime);
            }
            // Keep chunk IO / player visibility responsive on slow frames without
            // running random ticks, spawning, weather, machines or entity simulation.
            if(steps==0)level.getChunkSource().tick(haveTime,false);
        }finally{c.inner=false;}
    }
    public static void advanceSimulationTime(ServerLevel level){var c=clock(level);c.frozenGame++;if(level.getGameRules().getBoolean(GameRules.RULE_DAYLIGHT))c.frozenDay++;if(level.dimension()==Level.OVERWORLD)((net.minecraft.world.level.storage.ServerLevelData)level.getLevelData()).getScheduledEvents().tick(level.getServer(),c.frozenGame);c.setDirty();}
    @SubscribeEvent public static void load(LevelEvent.Load e){if(e.getLevel() instanceof ServerLevel l)clock(l);}
    @SubscribeEvent public static void unload(LevelEvent.Unload e){CLOCKS.remove(e.getLevel());}
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void tick(TickEvent.ServerTickEvent e){
        if(e.phase!=TickEvent.Phase.START)return;
        for(var c:CLOCKS.values())if(c.caster!=null){var p=c.level.getPlayerByUUID(c.caster);if(p==null||!p.isAlive())finish(c);}
    }
    @SubscribeEvent public static void joined(PlayerEvent.PlayerLoggedInEvent e){syncPlayer(e.getEntity());}
    @SubscribeEvent public static void changed(PlayerEvent.PlayerChangedDimensionEvent e){syncPlayer(e.getEntity());}
    @SubscribeEvent public static void respawn(PlayerEvent.PlayerRespawnEvent e){syncPlayer(e.getEntity());}
    private static void syncPlayer(Player p){if(p instanceof ServerPlayer s)sync(clock(s.serverLevel()),s);}
    @SubscribeEvent public static void interact(PlayerInteractEvent e){if(e.isCancelable()&&frozen(e.getEntity()))e.setCanceled(true);}
    @SubscribeEvent public static void attack(AttackEntityEvent e){if(frozen(e.getEntity()))e.setCanceled(true);}
    @SubscribeEvent public static void blockBreak(BlockEvent.BreakEvent e){if(frozen(e.getPlayer()))e.setCanceled(true);}
    @SubscribeEvent public static void stop(ServerStoppedEvent e){CLOCKS.clear();}
    private static final class Clock extends SavedData{
        long gameOffset,dayOffset,frozenGame,frozenDay;int phase;UUID caster,token=UUID.randomUUID();TimeMagicType mode=TimeMagicType.STOP;ServerLevel level;boolean inner,rawTimeOnly;final Set<UUID> tickedPlayers=new HashSet<>();
        static Clock load(CompoundTag tag){var c=new Clock();c.gameOffset=tag.getLong("GameOffset");c.dayOffset=tag.getLong("DayOffset");return c;}
        @Override public CompoundTag save(CompoundTag tag){tag.putLong("GameOffset",caster==null?gameOffset:level.getLevelData().getGameTime()-frozenGame);tag.putLong("DayOffset",caster==null?dayOffset:level.getLevelData().getDayTime()-frozenDay);return tag;}
    }
}
