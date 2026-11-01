package com.sourceofmystery.mixin;

import com.sourceofmystery.staff.TimeStop;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.ticks.ScheduledTick;
import net.minecraft.world.ticks.TickPriority;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(Level.class)
public abstract class LevelTimeMixin implements LevelAccessor {
    // LevelAccessor's defaults read raw LevelData time. Override the defaults on Level
    // so newly scheduled ticks and queued tick execution share the dimension clock.
    @Override public void scheduleTick(BlockPos pos,Block block,int delay,TickPriority priority) {
        getBlockTicks().schedule(new ScheduledTick<>(block,pos,((Level)(Object)this).getGameTime()+delay,priority,nextSubTickCount()));
    }
    @Override public void scheduleTick(BlockPos pos,Block block,int delay) { scheduleTick(pos,block,delay,TickPriority.NORMAL); }
    @Override public void scheduleTick(BlockPos pos,Fluid fluid,int delay,TickPriority priority) {
        getFluidTicks().schedule(new ScheduledTick<>(fluid,pos,((Level)(Object)this).getGameTime()+delay,priority,nextSubTickCount()));
    }
    @Override public void scheduleTick(BlockPos pos,Fluid fluid,int delay) { scheduleTick(pos,fluid,delay,TickPriority.NORMAL); }
    @Override public long dayTime() { return ((Level)(Object)this).getDayTime(); }
    @Inject(method="getGameTime",at=@At("RETURN"),cancellable=true)
    private void som$clock(CallbackInfoReturnable<Long> result) {
        Level level=(Level)(Object)this;
        if(!level.isClientSide)result.setReturnValue(TimeStop.gameTime(level,result.getReturnValue()));
    }
    @Inject(method="getDayTime",at=@At("RETURN"),cancellable=true)
    private void som$day(CallbackInfoReturnable<Long> result) {
        Level level=(Level)(Object)this;
        if(!level.isClientSide)result.setReturnValue(TimeStop.dayTime(level,result.getReturnValue()));
    }
    @Inject(method="tickBlockEntities",at=@At("HEAD"),cancellable=true)
    private void som$blockEntities(CallbackInfo info) { if(TimeStop.active((Level)(Object)this))info.cancel(); }
}
