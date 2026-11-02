package com.sourceofmystery.mixin;

import com.sourceofmystery.staff.TimeStop;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayer.class)
public abstract class ServerPlayerTimeMixin {
    @Inject(method="doTick",at=@At("HEAD"),cancellable=true)
    private void som$freeze(CallbackInfo info) { if(TimeStop.frozen((ServerPlayer)(Object)this))info.cancel(); }
}
