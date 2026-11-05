package com.sourceofmystery.client;

import com.sourceofmystery.SourceOfMystery;
import com.sourceofmystery.enchanter.MysteryEnchanterBlockEntity;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.DefaultedBlockGeoModel;
import software.bernie.geckolib.renderer.GeoBlockRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

public final class MysteryEnchanterRenderer extends GeoBlockRenderer<MysteryEnchanterBlockEntity> {
    public MysteryEnchanterRenderer(BlockEntityRendererProvider.Context c){super(new DefaultedBlockGeoModel<>(new ResourceLocation(SourceOfMystery.MOD_ID,"mystery_enchanter")));addRenderLayer(new AutoGlowingGeoLayer<>(this));}
}
