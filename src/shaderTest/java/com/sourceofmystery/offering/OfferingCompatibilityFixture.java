package com.sourceofmystery.offering;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.*;

/** Foreign namespace fixture, excluded from the production jar. No lottery integration. */
@Mod.EventBusSubscriber(modid="sourceofmystery",bus=Mod.EventBusSubscriber.Bus.MOD)
public final class OfferingCompatibilityFixture {
    @SubscribeEvent public static void register(RegisterEvent event) {
        event.register(ForgeRegistries.Keys.ITEMS,h->{
            h.register(new ResourceLocation("lotterycompat","unfamiliar_token"),new Item(new Item.Properties()));
            h.register(new ResourceLocation("lotterycompat","unfamiliar_relic"),new Item(new Item.Properties().rarity(Rarity.EPIC)));
        });
    }
}
