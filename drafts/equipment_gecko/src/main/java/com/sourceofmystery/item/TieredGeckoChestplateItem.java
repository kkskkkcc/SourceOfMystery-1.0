package com.sourceofmystery.item;

import com.sourceofmystery.client.equipment.TieredArmorExtensions;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;
import java.util.UUID;
import java.util.function.Consumer;
import javax.annotation.Nullable;

/** Appearance-only adapter: all armor attributes and effects stay in the original superclass. */
public final class TieredGeckoChestplateItem extends MysteryChestplateItem implements GeoItem {
    private final AnimatableInstanceCache cache=GeckoLibUtil.createInstanceCache(this);
    private final String visual;
    public TieredGeckoChestplateItem(String visual,ArmorMaterial material,Properties properties,boolean unbreakable){
        this(visual,material,properties,unbreakable,null,0);
    }
    public TieredGeckoChestplateItem(String visual,ArmorMaterial material,Properties properties,boolean unbreakable,
                                   @Nullable UUID healthId,double healthBonus){
        super(material,properties,unbreakable,healthId,healthBonus);this.visual=visual;
    }
    public String visual(){return visual;}
    @Override public void initializeClient(Consumer<IClientItemExtensions> consumer){consumer.accept(new TieredArmorExtensions(this));}
    @Override public void registerControllers(AnimatableManager.ControllerRegistrar controllers){}
    @Override public AnimatableInstanceCache getAnimatableInstanceCache(){return cache;}
}
