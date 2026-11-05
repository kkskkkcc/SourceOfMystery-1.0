package com.sourceofmystery.client.equipment;

import com.sourceofmystery.item.TieredGeckoChestplateItem;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoArmorRenderer;
import software.bernie.geckolib.renderer.GeoItemRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/** Separate worn and inventory model resources prevent cached armor poses leaking into held items. */
public final class TieredArmorExtensions implements IClientItemExtensions {
    private final TieredGeckoChestplateItem item;
    private Worn renderer;
    private GeoItemRenderer<TieredGeckoChestplateItem> displayed;
    public TieredArmorExtensions(TieredGeckoChestplateItem item){this.item=item;}
    static ResourceLocation resource(String path){return new ResourceLocation("sourceofmystery",path);}
    private static GeoModel<TieredGeckoChestplateItem> model(String visual,boolean worn){return new GeoModel<>(){
        @Override public ResourceLocation getModelResource(TieredGeckoChestplateItem item){return resource("geo/"+(worn?"armor/":"item/")+visual+(worn?"":"_chestplate")+".geo.json");}
        @Override public ResourceLocation getTextureResource(TieredGeckoChestplateItem item){return resource("textures/armor/"+visual+".png");}
        @Override public ResourceLocation getAnimationResource(TieredGeckoChestplateItem item){return resource("animations/armor/"+visual+".animation.json");}
    };}
    @Override public HumanoidModel<?> getHumanoidArmorModel(LivingEntity entity,ItemStack stack,EquipmentSlot slot,HumanoidModel<?> original){
        if(renderer==null)renderer=new Worn(item.visual());renderer.prepForRender(entity,stack,slot,original);return renderer;
    }
    @Override public BlockEntityWithoutLevelRenderer getCustomRenderer(){
        if(displayed==null){displayed=new GeoItemRenderer<>(model(item.visual(),false));displayed.addRenderLayer(new AutoGlowingGeoLayer<>(displayed));}
        return displayed;
    }
    private static final class Worn extends GeoArmorRenderer<TieredGeckoChestplateItem>{
        Worn(String visual){super(model(visual,true));addRenderLayer(new AutoGlowingGeoLayer<>(this));}
        @Override protected void applyBoneVisibilityBySlot(EquipmentSlot slot){
            super.applyBoneVisibilityBySlot(slot);
            if(slot==EquipmentSlot.CHEST){setBoneVisible(getRightLegBone(),true);setBoneVisible(getLeftLegBone(),true);}
        }
    }
}
