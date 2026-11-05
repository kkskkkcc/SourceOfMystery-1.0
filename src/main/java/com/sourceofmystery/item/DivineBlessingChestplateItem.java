package com.sourceofmystery.item;

import com.sourceofmystery.client.DivineBlessingArmorRenderer;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.UUID;
import java.util.function.Consumer;

/** The existing divine chestplate, with a wearer-driven GeckoLib armor model. */
public final class DivineBlessingChestplateItem extends MysteryChestplateItem implements GeoItem {
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public DivineBlessingChestplateItem(Properties properties) {
        super(MysteryArmorMaterial.DIVINE_BLESSING, properties, true,
                UUID.fromString("4d22fa12-dc4f-45ea-a2dd-2c94cb7defdb"), 200.0);
    }

    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(new IClientItemExtensions() {
            private DivineBlessingArmorRenderer renderer;

            @Override
            public HumanoidModel<?> getHumanoidArmorModel(LivingEntity wearer, ItemStack stack,
                                                         EquipmentSlot slot, HumanoidModel<?> original) {
                if (renderer == null) renderer = new DivineBlessingArmorRenderer();
                renderer.prepForRender(wearer, stack, slot, original);
                return renderer;
            }
        });
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        // Rigid plates follow the vanilla wearer bones rather than a competing idle animation.
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
