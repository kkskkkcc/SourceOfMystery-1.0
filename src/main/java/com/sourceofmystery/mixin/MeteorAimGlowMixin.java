package com.sourceofmystery.mixin;

import com.sourceofmystery.client.MeteorAimClient;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
public abstract class MeteorAimGlowMixin {
    @Inject(method="shouldEntityAppearGlowing",at=@At("RETURN"),cancellable=true)
    private void som$meteorPreview(Entity entity,CallbackInfoReturnable<Boolean> result){if(MeteorAimClient.outlineColor(entity)>=0)result.setReturnValue(true);}
}
