package com.sourceofmystery.mixin;

import com.sourceofmystery.client.DivineCGDirector;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keep mouse deltas consumed normally, but leave the saved player view still during CG. */
@Mixin(MouseHandler.class)
public abstract class DivineMouseInputMixin {
    @Inject(method="turnPlayer",at=@At(value="INVOKE",target="Lnet/minecraft/client/player/LocalPlayer;turn(DD)V"),cancellable=true)
    private void som$divineCameraOwnsView(CallbackInfo ci){
        // In the native method the accumulated deltas and event timer have already
        // been updated before this call, so skipping it cannot build a later jump.
        if(DivineCGDirector.active())ci.cancel();
    }
}
