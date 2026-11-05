package com.sourceofmystery.client.equipment;

import com.sourceofmystery.item.MysterySwordItem;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import net.minecraftforge.registries.ForgeRegistries;
import software.bernie.geckolib.model.DefaultedItemGeoModel;
import software.bernie.geckolib.renderer.GeoItemRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

public final class EquipmentSwordExtensions implements IClientItemExtensions {
    private final MysterySwordItem item;
    private GeoItemRenderer<MysterySwordItem> renderer;
    public EquipmentSwordExtensions(MysterySwordItem item){this.item=item;}
    @Override public BlockEntityWithoutLevelRenderer getCustomRenderer(){
        if(renderer==null){
            ResourceLocation id=ForgeRegistries.ITEMS.getKey(item);
            if(id==null)throw new IllegalStateException("Unregistered mystery sword");
            renderer=new GeoItemRenderer<>(new DefaultedItemGeoModel<>(id));
            renderer.addRenderLayer(new AutoGlowingGeoLayer<>(renderer));
        }
        return renderer;
    }
}
