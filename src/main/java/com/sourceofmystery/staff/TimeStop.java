package com.sourceofmystery.staff;

import com.sourceofmystery.client.staff.StaffVisuals;
import com.sourceofmystery.network.StaffEffectPacket;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.*;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.*;

/** A dimension-local simulation clock. The server's own tick counter still advances. */
@Mod.EventBusSubscriber(modid="sourceofmystery")
public final class TimeStop {
    private static final Map<Level, Clock> CLOCKS = new java.util.concurrent.ConcurrentHashMap<>();
    private static final String COOLDOWN="staff_time_stop_ready";
    private TimeStop() {}

    private static Clock clock(ServerLevel level) {
        Clock clock=CLOCKS.get(level);
        if(clock==null) {
            clock=level.getDataStorage().computeIfAbsent(Clock::load,Clock::new,"sourceofmystery_time_stop");
            clock.level=level; CLOCKS.put(level,clock);
        }
        return clock;
    }
    public static boolean active(Level level) {
        if(level.isClientSide) return StaffVisuals.timeStopped();
        Clock clock=CLOCKS.get(level); return clock!=null&&clock.caster!=null;
    }
    public static boolean frozen(Entity entity) {
        if(entity.level().isClientSide) return StaffVisuals.frozen(entity);
        Clock clock=CLOCKS.get(entity.level());
        return clock!=null&&clock.caster!=null&&!clock.caster.equals(entity.getUUID());
    }
    public static long gameTime(Level level,long raw) {
        Clock clock=CLOCKS.get(level);
        return clock==null?raw:clock.caster!=null?clock.frozenGame:raw-clock.gameOffset;
    }
    public static long dayTime(Level level,long raw) {
        Clock clock=CLOCKS.get(level);
        return clock==null?raw:clock.caster!=null?clock.frozenDay:raw-clock.dayOffset;
    }
    public static boolean canCast(ServerPlayer player) {
        return !active(player.level())&&serverTime(player)>=com.sourceofmystery.energy.MysteryEnergy.getPlayerData(player).getLong(COOLDOWN);
    }
    private static long serverTime(ServerPlayer player) { return player.server.overworld().getLevelData().getGameTime(); }
    public static boolean start(ServerPlayer player) {
        if(!canCast(player))return StaffCasting.fail(player,"time_cooldown");
        ServerLevel level=player.serverLevel(); Clock clock=clock(level);
        clock.frozenGame=level.getGameTime(); clock.frozenDay=level.getDayTime();
        clock.rawGame=level.getLevelData().getGameTime(); clock.rawDay=level.getLevelData().getDayTime();
        clock.caster=player.getUUID(); clock.until=level.getServer().getTickCount()+StaffConfig.TIME_STOP_TICKS.get();
        com.sourceofmystery.energy.MysteryEnergy.getPlayerData(player).putLong(COOLDOWN,
                serverTime(player)+StaffConfig.TIME_STOP_TICKS.get()+StaffConfig.TIME_STOP_COOLDOWN.get());
        clock.setDirty();
        StaffCasting.send(level,new StaffEffectPacket(StaffEffectPacket.STOP,clock.caster,player.position(),Vec3.ZERO,StaffConfig.TIME_STOP_TICKS.get(),0));
        return true;
    }
    private static void finish(Clock clock) {
        UUID caster=clock.caster;
        clock.gameOffset+=clock.level.getLevelData().getGameTime()-clock.rawGame;
        clock.dayOffset+=clock.level.getLevelData().getDayTime()-clock.rawDay;
        clock.caster=null; clock.setDirty();
        ServerPlayer player=clock.level.getServer().getPlayerList().getPlayer(caster);
        if(player!=null)com.sourceofmystery.energy.MysteryEnergy.getPlayerData(player).putLong(COOLDOWN,serverTime(player)+StaffConfig.TIME_STOP_COOLDOWN.get());
        StaffCasting.send(clock.level,new StaffEffectPacket(StaffEffectPacket.STOP,caster,Vec3.ZERO,Vec3.ZERO,0,0));
    }
    @SubscribeEvent public static void load(LevelEvent.Load event) {
        if(event.getLevel() instanceof ServerLevel level)clock(level);
    }
    @SubscribeEvent public static void tick(TickEvent.LevelTickEvent event) {
        if(event.phase!=TickEvent.Phase.START||!(event.level instanceof ServerLevel level))return;
        Clock clock=clock(level); if(clock.caster==null)return;
        net.minecraft.world.entity.player.Player caster=level.getPlayerByUUID(clock.caster);
        if(level.getServer().getTickCount()>=clock.until||caster==null||!caster.isAlive()||caster.level()!=level)finish(clock);
    }
    @SubscribeEvent public static void joined(PlayerEvent.PlayerLoggedInEvent event) { sync(event.getEntity()); }
    @SubscribeEvent public static void changed(PlayerEvent.PlayerChangedDimensionEvent event) { sync(event.getEntity()); }
    private static void sync(net.minecraft.world.entity.player.Player player) {
        if(!(player instanceof ServerPlayer server))return;
        Clock clock=clock(server.serverLevel());
        if(clock.caster!=null)com.sourceofmystery.network.ModNetwork.CHANNEL.send(
                net.minecraftforge.network.PacketDistributor.PLAYER.with(()->server),
                new StaffEffectPacket(StaffEffectPacket.STOP,clock.caster,server.position(),Vec3.ZERO,
                        Math.max(0,clock.until-server.server.getTickCount()),0));
    }
    @SubscribeEvent public static void interact(PlayerInteractEvent event) {
        if(event.isCancelable()&&frozen(event.getEntity()))event.setCanceled(true);
    }
    @SubscribeEvent public static void attack(AttackEntityEvent event) { if(frozen(event.getEntity()))event.setCanceled(true); }
    @SubscribeEvent public static void blockBreak(BlockEvent.BreakEvent event) { if(frozen(event.getPlayer()))event.setCanceled(true); }
    @SubscribeEvent public static void stop(ServerStoppedEvent event) { CLOCKS.clear(); }

    private static final class Clock extends SavedData {
        long gameOffset,dayOffset,frozenGame,frozenDay,rawGame,rawDay;
        int until; volatile UUID caster; ServerLevel level;
        static Clock load(CompoundTag tag) {
            Clock clock=new Clock();clock.gameOffset=tag.getLong("GameOffset");clock.dayOffset=tag.getLong("DayOffset");return clock;
        }
        @Override public CompoundTag save(CompoundTag tag) {
            tag.putLong("GameOffset",gameOffset+(caster==null?0:level.getLevelData().getGameTime()-rawGame));
            tag.putLong("DayOffset",dayOffset+(caster==null?0:level.getLevelData().getDayTime()-rawDay));return tag;
        }
    }
}
