package com.sourceofmystery.mixin;

import com.sourceofmystery.staff.TimeStop;
import com.sourceofmystery.client.staff.ClientTimeMagic;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientLevel.class)
public abstract class ClientLevelTimeMixin {
    @Unique private boolean som$worldStep,som$entityStep,som$ambientStep;
    @Inject(method="tick",at=@At("HEAD"),cancellable=true)
    private void som$world(java.util.function.BooleanSupplier time,CallbackInfo info){
        if(ClientTimeMagic.rateControlled()&&!som$worldStep){som$worldStep=true;try{for(int i=0;i<ClientTimeMagic.steps();i++)((ClientLevel)(Object)this).tick(time);}finally{som$worldStep=false;}info.cancel();}
    }
    @Inject(method="tickEntities",at=@At("HEAD"),cancellable=true)
    private void som$entities(CallbackInfo info){
        if(ClientTimeMagic.rateControlled()&&!som$entityStep){
            som$entityStep=true;try{
                // Player entities must also animate normally on the nine skipped frames.
                for(var p:((ClientLevel)(Object)this).players())if(!p.isPassenger())((ClientLevel)(Object)this).tickNonPassenger(p);
                for(int i=0;i<ClientTimeMagic.steps();i++)((ClientLevel)(Object)this).tickEntities();
            }finally{som$entityStep=false;}info.cancel();
        }
    }
    @Inject(method="tickNonPassenger",at=@At("HEAD"),cancellable=true)
    private void som$entity(Entity entity,CallbackInfo info) { if(TimeStop.frozen(entity)||ClientTimeMagic.skipPlayerTick(entity))info.cancel(); }
    @Inject(method="tickPassenger",at=@At("HEAD"),cancellable=true)
    private void som$passenger(Entity vehicle,Entity passenger,CallbackInfo info) { if(TimeStop.frozen(passenger)||ClientTimeMagic.skipPlayerTick(passenger))info.cancel(); }
    @Inject(method="tickTime",at=@At("HEAD"),cancellable=true)
    private void som$time(CallbackInfo info) { if(TimeStop.active((ClientLevel)(Object)this))info.cancel(); }
    @Inject(method="animateTick",at=@At("HEAD"),cancellable=true)
    private void som$ambient(int x,int y,int z,CallbackInfo info) {
        if(TimeStop.active((ClientLevel)(Object)this)){info.cancel();return;}
        if(ClientTimeMagic.rateControlled()&&!som$ambientStep){som$ambientStep=true;try{for(int i=0;i<ClientTimeMagic.steps();i++)((ClientLevel)(Object)this).animateTick(x,y,z);}finally{som$ambientStep=false;}info.cancel();}
    }
}
