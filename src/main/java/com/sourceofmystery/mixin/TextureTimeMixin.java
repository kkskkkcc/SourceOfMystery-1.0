package com.sourceofmystery.mixin;

import com.sourceofmystery.client.staff.StaffVisuals;
import net.minecraft.client.renderer.texture.TextureManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TextureManager.class)
public abstract class TextureTimeMixin {
    @Unique private boolean som$step;
    @Inject(method="tick",at=@At("HEAD"),cancellable=true)
    private void som$textures(CallbackInfo info) { if(StaffVisuals.timeStopped()){info.cancel();return;}
        if(com.sourceofmystery.client.staff.ClientTimeMagic.rateControlled()&&!som$step){som$step=true;try{for(int i=0;i<com.sourceofmystery.client.staff.ClientTimeMagic.steps();i++)((TextureManager)(Object)this).tick();}finally{som$step=false;}info.cancel();}
    }
}
