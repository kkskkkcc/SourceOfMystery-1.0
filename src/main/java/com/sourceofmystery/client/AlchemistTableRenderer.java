package com.sourceofmystery.client;

import com.sourceofmystery.alchemy.AlchemistTableBlockEntity;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.DefaultedBlockGeoModel;
import software.bernie.geckolib.renderer.GeoBlockRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

public final class AlchemistTableRenderer extends GeoBlockRenderer<AlchemistTableBlockEntity>{
    public AlchemistTableRenderer(BlockEntityRendererProvider.Context context){
        super(new DefaultedBlockGeoModel<>(new ResourceLocation("sourceofmystery","alchemist_table")));addRenderLayer(new AutoGlowingGeoLayer<>(this));
    }
}
