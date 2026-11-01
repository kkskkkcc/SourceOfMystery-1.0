package com.sourceofmystery.mixin;
import com.sourceofmystery.item.DimensionBreakerStaffItem;
import com.sourceofmystery.staff.StaffConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class StaffBlockBreakingMixin {
    @Inject(method="getDestroyProgress",at=@At("HEAD"),cancellable=true)
    private void som$staffSpeed(Player player,BlockGetter level,BlockPos pos,CallbackInfoReturnable<Float> result) {
        if(player.getMainHandItem().getItem() instanceof DimensionBreakerStaffItem)
            result.setReturnValue(((BlockState)(Object)this).is(Blocks.BEDROCK)?0f:(float)(1/(StaffConfig.BREAK_SECONDS.get()*20)));
    }
}
