package com.sourceofmystery.mixin;

import com.sourceofmystery.client.staff.StaffVisuals;
import net.minecraft.client.particle.ParticleEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ParticleEngine.class)
public abstract class ParticleTimeMixin {
    @Unique private boolean som$step;
    @Inject(method="tick",at=@At("HEAD"),cancellable=true)
    private void som$particles(CallbackInfo info) {
        if(StaffVisuals.timeStopped()){info.cancel();return;}
        if(com.sourceofmystery.client.staff.ClientTimeMagic.rateControlled()&&!som$step){som$step=true;try{for(int i=0;i<com.sourceofmystery.client.staff.ClientTimeMagic.steps();i++)((ParticleEngine)(Object)this).tick();}finally{som$step=false;}info.cancel();}
    }
    @ModifyVariable(method="render",at=@At("HEAD"),argsOnly=true,ordinal=0)
    private float som$partial(float partial) { return com.sourceofmystery.client.staff.ClientTimeMagic.partial(partial); }
}
