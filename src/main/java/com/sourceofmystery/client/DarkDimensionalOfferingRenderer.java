package com.sourceofmystery.client;

import com.sourceofmystery.SourceOfMystery;
import com.sourceofmystery.block.DarkDimensionalOfferingBlockEntity;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.DefaultedBlockGeoModel;
import software.bernie.geckolib.renderer.GeoBlockRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

public final class DarkDimensionalOfferingRenderer extends GeoBlockRenderer<DarkDimensionalOfferingBlockEntity> {
    public DarkDimensionalOfferingRenderer(BlockEntityRendererProvider.Context context){
        super(new DefaultedBlockGeoModel<>(new ResourceLocation(SourceOfMystery.MOD_ID,"dark_dimensional_offering")));
        addRenderLayer(new AutoGlowingGeoLayer<>(this));
    }
}
