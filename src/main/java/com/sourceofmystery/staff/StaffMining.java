package com.sourceofmystery.staff;

import com.sourceofmystery.item.DimensionBreakerStaffItem;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.*;

@Mod.EventBusSubscriber(modid="sourceofmystery")
public final class StaffMining {
    private record Mining(BlockPos pos,int start,net.minecraft.server.level.ServerLevel level) {}
    private static final Map<UUID,Mining> ACTIVE=new HashMap<>();
    private StaffMining() {}
    public static void request(ServerPlayer player,BlockPos pos,boolean start) {
        stop(player);
        if(!start||!valid(player,pos))return;
        var state=player.level().getBlockState(pos);
        if(state.getDestroySpeed(player.level(),pos)>=0||state.is(Blocks.BEDROCK))return;
        ACTIVE.put(player.getUUID(),new Mining(pos.immutable(),player.server.getTickCount(),player.serverLevel()));
    }
    private static boolean valid(ServerPlayer player,BlockPos pos) {
        if(TimeStop.frozen(player)||player.isSpectator()||!player.mayBuild()||!(player.getMainHandItem().getItem() instanceof DimensionBreakerStaffItem))return false;
        double reach=player.getAttributeValue(ForgeMod.BLOCK_REACH.get());
        var hit=player.level().clip(new ClipContext(player.getEyePosition(),player.getEyePosition().add(player.getLookAngle().scale(reach)),ClipContext.Block.OUTLINE,ClipContext.Fluid.NONE,player));
        return hit.getType()==HitResult.Type.BLOCK&&hit.getBlockPos().equals(pos);
    }
    private static void stop(ServerPlayer player){Mining m=ACTIVE.remove(player.getUUID());if(m!=null)m.level.destroyBlockProgress(player.getId(),m.pos,-1);}
    @SubscribeEvent public static void tick(TickEvent.PlayerTickEvent event) {
        if(event.phase!=TickEvent.Phase.END||!(event.player instanceof ServerPlayer player))return;
        Mining m=ACTIVE.get(player.getUUID());if(m==null)return;
        if(player.level()!=m.level||!valid(player,m.pos)){stop(player);return;}
        int elapsed=player.server.getTickCount()-m.start;
        int duration=(int)Math.ceil(StaffConfig.BREAK_SECONDS.get()*20);
        m.level.destroyBlockProgress(player.getId(),m.pos,Math.min(9,elapsed*10/duration));
        if(elapsed<duration)return;
        stop(player);var state=m.level.getBlockState(m.pos);
        if(state.is(Blocks.BEDROCK)||state.isAir())return;
        int experience=ForgeHooks.onBlockBreakEvent(m.level,player.gameMode.getGameModeForPlayer(),player,m.pos);
        if(experience<0)return;
        var tile=m.level.getBlockEntity(m.pos);state.getBlock().playerWillDestroy(m.level,m.pos,state,player);
        if(m.level.removeBlock(m.pos,false)){
            state.getBlock().destroy(m.level,m.pos,state);
            state.getBlock().playerDestroy(m.level,player,m.pos,state,tile,player.getMainHandItem());
            if(experience>0)state.getBlock().popExperience(m.level,m.pos,experience);
        }
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event){if(event.getEntity() instanceof ServerPlayer player)stop(player);}
}
