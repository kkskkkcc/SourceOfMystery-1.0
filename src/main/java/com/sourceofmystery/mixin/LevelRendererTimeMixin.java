package com.sourceofmystery.mixin;

import com.sourceofmystery.client.staff.StaffVisuals;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public abstract class LevelRendererTimeMixin {
    @Unique private boolean som$step;
    @Inject(method="tick",at=@At("HEAD"),cancellable=true)
    private void som$cloudClock(CallbackInfo info) { if(StaffVisuals.timeStopped()){info.cancel();return;}
        if(com.sourceofmystery.client.staff.ClientTimeMagic.rateControlled()&&!som$step){som$step=true;try{for(int i=0;i<com.sourceofmystery.client.staff.ClientTimeMagic.steps();i++)((LevelRenderer)(Object)this).tick();}finally{som$step=false;}info.cancel();}
    }
}
