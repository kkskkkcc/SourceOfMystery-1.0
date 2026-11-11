package com.sourceofmystery.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ServerLevel.class)
public interface ServerLevelTimeAccess {
    @Invoker("tickTime") void som$tickTime();
    @Invoker("tickPassenger") void som$tickPassenger(Entity vehicle,Entity passenger);
}
