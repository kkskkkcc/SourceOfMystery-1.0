package com.sourceofmystery.loot;

import com.sourceofmystery.SourceOfMystery;
import com.sourceofmystery.item.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.event.entity.player.FillBucketEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 下雨时用空桶打水，有 10% 概率额外获得一个水之源（雨要真的落在这片水面上：露天、且该群系下雨而不是下雪）
 */
@Mod.EventBusSubscriber(modid = SourceOfMystery.MOD_ID)
public final class WaterSourceEvents {

    private static final float WATER_SOURCE_CHANCE = 0.10f;

    private WaterSourceEvents() {
    }

    @SubscribeEvent
    public static void onFillBucket(FillBucketEvent event) {
        Level level = event.getLevel();
        Player player = event.getEntity();
        if (level.isClientSide || player == null || !event.getEmptyBucket().is(Items.BUCKET)
                || !(event.getTarget() instanceof BlockHitResult hit)) {
            return;
        }
        BlockPos pos = hit.getBlockPos();
        FluidState fluid = level.getFluidState(pos);
        if (!fluid.is(FluidTags.WATER) || !fluid.isSource()) {
            // 准星可能对着水面下的方块边缘，再看一下相邻那格
            pos = pos.relative(hit.getDirection());
            fluid = level.getFluidState(pos);
            if (!fluid.is(FluidTags.WATER) || !fluid.isSource()) {
                return;
            }
        }
        if (!level.isRainingAt(pos.above()) || level.random.nextFloat() >= WATER_SOURCE_CHANCE) {
            return;
        }
        ItemStack reward = new ItemStack(ModItems.WATER_SOURCE.get());
        if (!player.getInventory().add(reward)) {
            player.drop(reward, false);
        }
    }
}
