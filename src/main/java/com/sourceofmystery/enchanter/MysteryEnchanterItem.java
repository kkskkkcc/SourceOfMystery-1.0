package com.sourceofmystery.enchanter;

import com.sourceofmystery.SourceOfMystery;
import com.sourceofmystery.block.ModBlocks;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.model.DefaultedBlockGeoModel;
import software.bernie.geckolib.renderer.GeoItemRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;
import software.bernie.geckolib.util.GeckoLibUtil;
import java.util.function.Consumer;

public final class MysteryEnchanterItem extends BlockItem implements GeoItem {
    private final AnimatableInstanceCache cache=GeckoLibUtil.createInstanceCache(this);
    public MysteryEnchanterItem(){super(ModBlocks.MYSTERY_ENCHANTER.get(),new Item.Properties().rarity(Rarity.EPIC).fireResistant());}
    @Override public void registerControllers(AnimatableManager.ControllerRegistrar controllers){}
    @Override public AnimatableInstanceCache getAnimatableInstanceCache(){return cache;}
    @Override public void initializeClient(Consumer<IClientItemExtensions> consumer){consumer.accept(new IClientItemExtensions(){
        private Renderer renderer;
        @Override public BlockEntityWithoutLevelRenderer getCustomRenderer(){if(renderer==null)renderer=new Renderer();return renderer;}
    });}
    private static final class Renderer extends GeoItemRenderer<MysteryEnchanterItem> {
        Renderer(){super(new DefaultedBlockGeoModel<>(new ResourceLocation(SourceOfMystery.MOD_ID,"mystery_enchanter")));addRenderLayer(new AutoGlowingGeoLayer<>(this));}
    }
}
