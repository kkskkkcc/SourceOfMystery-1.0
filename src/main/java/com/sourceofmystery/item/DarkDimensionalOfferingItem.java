package com.sourceofmystery.item;

import com.sourceofmystery.ritual.OfferingPortalRitual;
import net.minecraft.world.InteractionResult;
import com.sourceofmystery.SourceOfMystery;
import com.sourceofmystery.block.ModBlocks;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.model.DefaultedBlockGeoModel;
import software.bernie.geckolib.renderer.GeoItemRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;
import software.bernie.geckolib.util.GeckoLibUtil;
import java.util.List;
import java.util.function.Consumer;
import javax.annotation.Nullable;

/** A single-use key for the unlit 14-obsidian offering portal. */
public final class DarkDimensionalOfferingItem extends BlockItem implements GeoItem {
    private final AnimatableInstanceCache cache=GeckoLibUtil.createInstanceCache(this);
    public DarkDimensionalOfferingItem(Properties properties) { super(ModBlocks.DARK_DIMENSIONAL_OFFERING.get(),properties); }

    @Override public net.minecraft.world.InteractionResultHolder<ItemStack> use(Level level,net.minecraft.world.entity.player.Player player,net.minecraft.world.InteractionHand hand) {
        if(player instanceof net.minecraft.server.level.ServerPlayer p)com.sourceofmystery.offering.OfferingMenus.open(p,null);
        return net.minecraft.world.InteractionResultHolder.sidedSuccess(player.getItemInHand(hand),level.isClientSide);
    }

    @Override public InteractionResult useOn(UseOnContext context) {
        // The server owns summoning. Suppress speculative placement against
        // obsidian while it decides whether this is a valid ritual frame.
        if (context.getLevel().isClientSide && context.getLevel().getBlockState(context.getClickedPos()).is(Blocks.OBSIDIAN)) return InteractionResult.SUCCESS;
        ItemStack stack=context.getItemInHand();
        if (!context.getLevel().isClientSide && OfferingPortalRitual.tryStart(context.getLevel(),context.getClickedPos(),context.getPlayer(),context.getHand(),stack))return InteractionResult.CONSUME;
        return super.useOn(context);
    }
    @Override public void registerControllers(AnimatableManager.ControllerRegistrar controllers){}
    @Override public AnimatableInstanceCache getAnimatableInstanceCache(){return cache;}
    @Override public void appendHoverText(ItemStack stack,@Nullable Level level,List<Component> tooltip,TooltipFlag flag){
        tooltip.add(Component.translatable("tooltip.sourceofmystery.offering.place").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.sourceofmystery.offering.ritual").withStyle(ChatFormatting.DARK_PURPLE));
        tooltip.add(Component.translatable("offering.sourceofmystery.tooltip").withStyle(ChatFormatting.AQUA));
    }
    @Override public void initializeClient(Consumer<IClientItemExtensions> consumer){
        consumer.accept(new IClientItemExtensions(){
            private Renderer renderer;
            @Override public BlockEntityWithoutLevelRenderer getCustomRenderer(){if(renderer==null)renderer=new Renderer();return renderer;}
        });
    }
    private static final class Renderer extends GeoItemRenderer<DarkDimensionalOfferingItem>{
        Renderer(){
            super(new DefaultedBlockGeoModel<>(new ResourceLocation(SourceOfMystery.MOD_ID,"dark_dimensional_offering")));
            addRenderLayer(new AutoGlowingGeoLayer<>(this));
        }
    }
}
