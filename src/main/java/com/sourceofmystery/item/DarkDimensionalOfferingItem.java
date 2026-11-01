package com.sourceofmystery.item;

import com.sourceofmystery.ritual.OfferingPortalRitual;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;

/** A single-use key for the unlit 14-obsidian offering portal. */
public final class DarkDimensionalOfferingItem extends Item {
    public DarkDimensionalOfferingItem(Properties properties) { super(properties); }

    @Override public InteractionResult useOn(UseOnContext context) {
        if (context.getLevel().isClientSide) return InteractionResult.SUCCESS;
        ItemStack stack=context.getItemInHand();
        return OfferingPortalRitual.tryStart(context.getLevel(),context.getClickedPos(),context.getPlayer(),context.getHand(),stack)
                ? InteractionResult.CONSUME : InteractionResult.PASS;
    }
}
