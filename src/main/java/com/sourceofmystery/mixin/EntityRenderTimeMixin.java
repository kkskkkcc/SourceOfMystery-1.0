package com.sourceofmystery.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.sourceofmystery.client.staff.StaffVisuals;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;

@Mixin(EntityRenderDispatcher.class)
public abstract class EntityRenderTimeMixin {
    @ModifyVariable(method="render",at=@At("HEAD"),argsOnly=true,ordinal=1)
    private float som$partial(float partial,Entity entity,double x,double y,double z,float yaw,float original,
                              PoseStack poses,MultiBufferSource buffers,int light) {
        return StaffVisuals.frozen(entity)?0:partial;
    }
}
