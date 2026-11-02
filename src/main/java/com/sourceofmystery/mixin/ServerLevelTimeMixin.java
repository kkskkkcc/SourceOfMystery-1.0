package com.sourceofmystery.mixin;

import com.sourceofmystery.staff.TimeStop;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.ticks.LevelTicks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.function.BiConsumer;

@Mixin(ServerLevel.class)
public abstract class ServerLevelTimeMixin {
    @Inject(method="tickNonPassenger",at=@At("HEAD"),cancellable=true)
    private void som$entity(Entity entity,CallbackInfo info) { if(TimeStop.frozen(entity))info.cancel(); }
    @Inject(method="tickPassenger",at=@At("HEAD"),cancellable=true)
    private void som$passenger(Entity vehicle,Entity passenger,CallbackInfo info) { if(TimeStop.frozen(passenger))info.cancel(); }
    @Inject(method="tickChunk",at=@At("HEAD"),cancellable=true)
    private void som$randomTicks(LevelChunk chunk,int randomSpeed,CallbackInfo info) { if(TimeStop.active((ServerLevel)(Object)this))info.cancel(); }
    @Inject(method={"advanceWeatherCycle","runBlockEvents"},at=@At("HEAD"),cancellable=true)
    private void som$world(CallbackInfo info) { if(TimeStop.active((ServerLevel)(Object)this))info.cancel(); }
    @Inject(method="tickCustomSpawners",at=@At("HEAD"),cancellable=true)
    private void som$spawners(boolean enemies,boolean friendly,CallbackInfo info) { if(TimeStop.active((ServerLevel)(Object)this))info.cancel(); }
    @Redirect(method="tick",at=@At(value="INVOKE",target="Lnet/minecraft/world/ticks/LevelTicks;tick(JILjava/util/function/BiConsumer;)V"))
    private <T> void som$scheduled(LevelTicks<T> ticks,long time,int maximum,BiConsumer<BlockPos,T> action) {
        if(!TimeStop.active((ServerLevel)(Object)this))ticks.tick(time,maximum,action);
    }
    @Redirect(method="tick",at=@At(value="INVOKE",target="Lnet/minecraft/world/entity/raid/Raids;tick()V"))
    private void som$raids(net.minecraft.world.entity.raid.Raids raids) {
        if(!TimeStop.active((ServerLevel)(Object)this))raids.tick();
    }
    @Redirect(method="tick",at=@At(value="INVOKE",target="Lnet/minecraft/world/level/dimension/end/EndDragonFight;tick()V"))
    private void som$dragonFight(net.minecraft.world.level.dimension.end.EndDragonFight fight) {
        if(!TimeStop.active((ServerLevel)(Object)this))fight.tick();
    }
}
