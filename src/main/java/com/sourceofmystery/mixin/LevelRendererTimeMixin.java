package com.sourceofmystery.mixin;

import com.sourceofmystery.client.staff.StaffVisuals;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public abstract class LevelRendererTimeMixin {
    @Inject(method="tick",at=@At("HEAD"),cancellable=true)
    private void som$cloudClock(CallbackInfo info) { if(StaffVisuals.timeStopped())info.cancel(); }
}
