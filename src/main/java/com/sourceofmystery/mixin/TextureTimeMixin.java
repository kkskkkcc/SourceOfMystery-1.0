package com.sourceofmystery.mixin;

import com.sourceofmystery.client.staff.StaffVisuals;
import net.minecraft.client.renderer.texture.TextureManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TextureManager.class)
public abstract class TextureTimeMixin {
    @Inject(method="tick",at=@At("HEAD"),cancellable=true)
    private void som$textures(CallbackInfo info) { if(StaffVisuals.timeStopped())info.cancel(); }
}
