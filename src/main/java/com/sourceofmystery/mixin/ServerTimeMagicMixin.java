package com.sourceofmystery.mixin;

import com.sourceofmystery.staff.TimeStop;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.ForgeEventFactory;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import java.util.*;
import java.util.function.BooleanSupplier;

/** Wrap exactly the level tick and its Forge hooks, never the whole server tick. */
@Mixin(MinecraftServer.class)
public abstract class ServerTimeMagicMixin {
    @Unique private final Set<Level> som$scaled=new HashSet<>();
    @Redirect(method="tickChildren",at=@At(value="INVOKE",target="Lnet/minecraftforge/event/ForgeEventFactory;onPreLevelTick(Lnet/minecraft/world/level/Level;Ljava/util/function/BooleanSupplier;)V",remap=false))
    private void som$pre(Level level,BooleanSupplier time){if(TimeStop.rateControlled(level))som$scaled.add(level);else ForgeEventFactory.onPreLevelTick(level,time);}
    @Redirect(method="tickChildren",at=@At(value="INVOKE",target="Lnet/minecraft/server/level/ServerLevel;tick(Ljava/util/function/BooleanSupplier;)V"))
    private void som$dimension(ServerLevel level,BooleanSupplier time){if(som$scaled.contains(level))TimeStop.tickDimension(level,time);else level.tick(time);}
    @Redirect(method="tickChildren",at=@At(value="INVOKE",target="Lnet/minecraftforge/event/ForgeEventFactory;onPostLevelTick(Lnet/minecraft/world/level/Level;Ljava/util/function/BooleanSupplier;)V",remap=false))
    private void som$post(Level level,BooleanSupplier time){if(!som$scaled.remove(level))ForgeEventFactory.onPostLevelTick(level,time);}
}
