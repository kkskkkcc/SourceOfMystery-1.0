package com.sourceofmystery.mixin;

import com.sourceofmystery.client.MeteorAimClient;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class MeteorAimColorMixin {
    @Inject(method="getTeamColor",at=@At("RETURN"),cancellable=true)
    private void som$meteorColor(CallbackInfoReturnable<Integer> result){var entity=(Entity)(Object)this;int color=MeteorAimClient.outlineColor(entity);if(color>=0)result.setReturnValue(color);}
}
