package com.sourceofmystery.alchemy;

import com.sourceofmystery.block.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

/** The shop belongs to the world; this sculpture needs no inventory or server ticker. */
public final class AlchemistTableBlockEntity extends BlockEntity implements GeoBlockEntity {
    private final AnimatableInstanceCache cache=GeckoLibUtil.createInstanceCache(this);
    public AlchemistTableBlockEntity(BlockPos pos,BlockState state){super(ModBlockEntities.ALCHEMIST_TABLE.get(),pos,state);}
    @Override public void registerControllers(AnimatableManager.ControllerRegistrar controllers){}
    @Override public AnimatableInstanceCache getAnimatableInstanceCache(){return cache;}
}
