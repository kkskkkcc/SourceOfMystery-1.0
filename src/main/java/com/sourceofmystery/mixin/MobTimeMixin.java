package com.sourceofmystery.mixin;

import com.sourceofmystery.staff.TimeStop;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Mob.class)
public abstract class MobTimeMixin {
    @Inject(method="checkDespawn",at=@At("HEAD"),cancellable=true)
    private void som$despawn(CallbackInfo info) { if(TimeStop.frozen((Mob)(Object)this))info.cancel(); }
}
