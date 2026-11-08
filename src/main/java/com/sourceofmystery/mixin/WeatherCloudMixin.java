package com.sourceofmystery.mixin;

import com.sourceofmystery.client.WeatherVisuals;
import net.minecraft.client.Options;
import net.minecraft.client.CloudStatus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Nearby windstorms supply their own tall cloud shelf instead of intersecting vanilla cloud slabs. */
@Mixin(Options.class)
public abstract class WeatherCloudMixin {
    // Iris can replace the whole cloud renderer. Querying the effective cloud type
    // suppresses those same vanilla cloud slabs without changing the saved option.
    @Inject(method="getCloudsType",at=@At("HEAD"),cancellable=true)
    private void som$stormShelf(CallbackInfoReturnable<CloudStatus> info){if(WeatherVisuals.localCloudShelf())info.setReturnValue(CloudStatus.OFF);}
}
