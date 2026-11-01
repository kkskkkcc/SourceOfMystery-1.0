package com.sourceofmystery.mixin;

import com.sourceofmystery.staff.TimeStop;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.network.protocol.game.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class PlayerConnectionTimeMixin {
    @Shadow public ServerPlayer player;
    @Inject(method="handleMovePlayer",at=@At("HEAD"),cancellable=true)
    private void som$move(ServerboundMovePlayerPacket packet,CallbackInfo info) { if(TimeStop.frozen(player))info.cancel(); }
    @Inject(method="handleMoveVehicle",at=@At("HEAD"),cancellable=true)
    private void som$vehicle(ServerboundMoveVehiclePacket packet,CallbackInfo info) { if(TimeStop.frozen(player))info.cancel(); }
    @Inject(method="handleUseItem",at=@At("HEAD"),cancellable=true)
    private void som$use(ServerboundUseItemPacket packet,CallbackInfo info) { if(TimeStop.frozen(player))info.cancel(); }
    @Inject(method="handleUseItemOn",at=@At("HEAD"),cancellable=true)
    private void som$useOn(ServerboundUseItemOnPacket packet,CallbackInfo info) { if(TimeStop.frozen(player))info.cancel(); }
    @Inject(method="handlePlayerAction",at=@At("HEAD"),cancellable=true)
    private void som$action(ServerboundPlayerActionPacket packet,CallbackInfo info) { if(TimeStop.frozen(player))info.cancel(); }
    @Inject(method="handleInteract",at=@At("HEAD"),cancellable=true)
    private void som$interact(ServerboundInteractPacket packet,CallbackInfo info) { if(TimeStop.frozen(player))info.cancel(); }
}
