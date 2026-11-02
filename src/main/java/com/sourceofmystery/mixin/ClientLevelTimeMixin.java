package com.sourceofmystery.mixin;

import com.sourceofmystery.staff.TimeStop;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientLevel.class)
public abstract class ClientLevelTimeMixin {
    @Inject(method="tickNonPassenger",at=@At("HEAD"),cancellable=true)
    private void som$entity(Entity entity,CallbackInfo info) { if(TimeStop.frozen(entity))info.cancel(); }
    @Inject(method="tickPassenger",at=@At("HEAD"),cancellable=true)
    private void som$passenger(Entity vehicle,Entity passenger,CallbackInfo info) { if(TimeStop.frozen(passenger))info.cancel(); }
    @Inject(method="tickTime",at=@At("HEAD"),cancellable=true)
    private void som$time(CallbackInfo info) { if(TimeStop.active((ClientLevel)(Object)this))info.cancel(); }
    @Inject(method="animateTick",at=@At("HEAD"),cancellable=true)
    private void som$ambient(int x,int y,int z,CallbackInfo info) { if(TimeStop.active((ClientLevel)(Object)this))info.cancel(); }
}
