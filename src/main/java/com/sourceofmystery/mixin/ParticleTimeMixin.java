package com.sourceofmystery.mixin;

import com.sourceofmystery.client.staff.StaffVisuals;
import net.minecraft.client.particle.ParticleEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ParticleEngine.class)
public abstract class ParticleTimeMixin {
    @Inject(method="tick",at=@At("HEAD"),cancellable=true)
    private void som$particles(CallbackInfo info) { if(StaffVisuals.timeStopped())info.cancel(); }
    @ModifyVariable(method="render",at=@At("HEAD"),argsOnly=true,ordinal=0)
    private float som$partial(float partial) { return StaffVisuals.timeStopped()?0:partial; }
}
