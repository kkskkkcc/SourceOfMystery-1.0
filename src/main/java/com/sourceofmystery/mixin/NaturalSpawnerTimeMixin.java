package com.sourceofmystery.mixin;

import com.sourceofmystery.staff.TimeStop;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(NaturalSpawner.class)
public abstract class NaturalSpawnerTimeMixin {
    @Inject(method="spawnForChunk",at=@At("HEAD"),cancellable=true)
    private static void som$spawning(ServerLevel level,LevelChunk chunk,NaturalSpawner.SpawnState state,
                                     boolean friendly,boolean enemies,boolean persistent,CallbackInfo info) {
        if(TimeStop.active(level))info.cancel();
    }
}
