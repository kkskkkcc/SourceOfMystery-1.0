package com.sourceofmystery.guardian;

import com.sourceofmystery.SourceOfMystery;
import net.minecraft.ChatFormatting;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.model.DefaultedBlockGeoModel;
import software.bernie.geckolib.renderer.GeoItemRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;
import software.bernie.geckolib.util.GeckoLibUtil;

import javax.annotation.Nullable;
import java.util.List;
import java.util.function.Consumer;

/**
 * 守护祭坛的物品：物品栏 / 手持时用同一个 GeckoLib 模型显示（播放待激活的循环动画）
 */
public class GuardianAltarItem extends BlockItem implements GeoItem {

    private static final RawAnimation DORMANT = RawAnimation.begin().thenLoop("guardian_altar.dormant");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public GuardianAltarItem(Block block) {
        super(block, new Properties().rarity(Rarity.EPIC));
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.sourceofmystery.guardian_altar.line1").withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable("tooltip.sourceofmystery.guardian_altar.line2",
                GuardianShields.TOP_Y).withStyle(ChatFormatting.GRAY));
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 0, state -> state.setAndContinue(DORMANT)));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(new IClientItemExtensions() {
            private Renderer renderer;

            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (renderer == null) {
                    renderer = new Renderer();
                }
                return renderer;
            }
        });
    }

    private static class Renderer extends GeoItemRenderer<GuardianAltarItem> {
        Renderer() {
            super(new DefaultedBlockGeoModel<>(new ResourceLocation(SourceOfMystery.MOD_ID, "guardian_altar")));
            addRenderLayer(new AutoGlowingGeoLayer<>(this));
        }

        @Override
        public RenderType getRenderType(GuardianAltarItem animatable, ResourceLocation texture,
                                        MultiBufferSource bufferSource, float partialTick) {
            return RenderType.entityTranslucent(texture);
        }
    }
}
