package com.sourceofmystery.mixin;

import com.sourceofmystery.staff.TimeStop;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public abstract class EntityTimeMixin {
    @Inject(method="move",at=@At("HEAD"),cancellable=true)
    private void som$motion(MoverType type,Vec3 movement,CallbackInfo info) { if(TimeStop.frozen((Entity)(Object)this)||com.sourceofmystery.staff.StaffDomains.constrained((Entity)(Object)this))info.cancel(); }
    @Inject(method="turn",at=@At("HEAD"),cancellable=true)
    private void som$look(double x,double y,CallbackInfo info) { if(TimeStop.frozen((Entity)(Object)this))info.cancel(); }
}
