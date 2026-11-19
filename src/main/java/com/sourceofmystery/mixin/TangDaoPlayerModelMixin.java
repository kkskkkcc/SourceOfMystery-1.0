package com.sourceofmystery.mixin;

import com.sourceofmystery.client.TangDaoPresentation;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerModel.class)
public abstract class TangDaoPlayerModelMixin {
    @Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
    private void som$tangDaoPose(LivingEntity entity, float walk, float speed, float age,
                                 float yaw, float pitch, CallbackInfo ci) {
        if (entity instanceof AbstractClientPlayer player) {
            TangDaoPresentation.applyThirdPerson(player, (PlayerModel<?>) (Object) this);
        }
    }
}
