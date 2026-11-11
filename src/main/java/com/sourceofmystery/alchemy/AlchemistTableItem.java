package com.sourceofmystery.alchemy;

import com.sourceofmystery.block.ModBlocks;
import net.minecraft.ChatFormatting;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
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

public final class AlchemistTableItem extends BlockItem implements GeoItem {
    private final AnimatableInstanceCache cache=GeckoLibUtil.createInstanceCache(this);
    public AlchemistTableItem(){super(ModBlocks.ALCHEMIST_TABLE.get(),new Properties().rarity(Rarity.EPIC).fireResistant());}
    @Override public InteractionResultHolder<ItemStack> use(Level level,Player player,InteractionHand hand){
        if(player instanceof net.minecraft.server.level.ServerPlayer p)AlchemyMenus.open(p,null,hand);
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand),level.isClientSide);
    }
    @Override public InteractionResult useOn(UseOnContext context){
        if(context.getPlayer()!=null&&!context.getPlayer().isShiftKeyDown()){
            if(context.getPlayer() instanceof net.minecraft.server.level.ServerPlayer p)AlchemyMenus.open(p,null,context.getHand());
            return InteractionResult.sidedSuccess(context.getLevel().isClientSide);
        }
        return super.useOn(context);
    }
    @Override public void appendHoverText(ItemStack stack,Level level,List<Component> lines,TooltipFlag flag){
        lines.add(Component.translatable("alchemy.sourceofmystery.item_hint").withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("alchemy.sourceofmystery.daily_hint").withStyle(ChatFormatting.GOLD));
    }
    @Override public void registerControllers(AnimatableManager.ControllerRegistrar controllers){}
    @Override public AnimatableInstanceCache getAnimatableInstanceCache(){return cache;}
    @Override public void initializeClient(Consumer<IClientItemExtensions> consumer){consumer.accept(new IClientItemExtensions(){
        private Renderer renderer;
        @Override public BlockEntityWithoutLevelRenderer getCustomRenderer(){if(renderer==null)renderer=new Renderer();return renderer;}
    });}
    private static final class Renderer extends GeoItemRenderer<AlchemistTableItem>{
        Renderer(){super(new DefaultedBlockGeoModel<>(new ResourceLocation("sourceofmystery","alchemist_table")));addRenderLayer(new AutoGlowingGeoLayer<>(this));}
    }
}
