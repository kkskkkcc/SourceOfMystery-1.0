package com.sourceofmystery.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.sourceofmystery.client.TangDaoPresentation;
import com.sourceofmystery.item.ModItems;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemInHandLayer.class)
public abstract class TangDaoHeldLayerMixin {
    @Inject(method = "renderArmWithItem", at = @At("HEAD"), cancellable = true)
    private void som$tangDaoGrip(LivingEntity entity, ItemStack stack, ItemDisplayContext context,
                                 HumanoidArm arm, PoseStack poses, MultiBufferSource buffers, int light,
                                 CallbackInfo ci) {
        if (!(entity instanceof AbstractClientPlayer player) || arm != player.getMainArm()
                || !stack.is(ModItems.TANG_DAO.get()) || player.isInvisible() || player.isSleeping()) return;
        if (((ItemInHandLayer<?, ?>) (Object) this).getParentModel() instanceof PlayerModel<?> model) {
            TangDaoPresentation.renderHipScabbard(player, model, stack, poses, buffers, light);
            if (TangDaoPresentation.thirdPersonActive(player)) {
                TangDaoPresentation.renderThirdPerson(player, model, stack, poses, buffers, light);
                ci.cancel();
            }
        }
    }
}
