package com.sourceofmystery.offering;

import net.minecraft.ChatFormatting;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.gametest.*;
import net.minecraftforge.registries.*;

/** Reproduces the Integrated MC crash: Forge can append Rarity enum constants. */
@GameTestHolder("sourceofmystery")
@PrefixGameTestTemplate(false)
@Mod.EventBusSubscriber(modid="sourceofmystery",bus=Mod.EventBusSubscriber.Bus.MOD)
public final class OfferingRarityCompatibilityGameTests {
    private static final ResourceLocation EXTENDED=new ResourceLocation("lotterycompat","extended_rarity");
    private static final ResourceLocation BROKEN=new ResourceLocation("lotterycompat","broken_rarity_hook");
    @SubscribeEvent public static void register(RegisterEvent event) {
        event.register(ForgeRegistries.Keys.ITEMS,h->{
            var rarity=Rarity.create("SOURCEOFMYSTERY_COMPAT_MYTHIC",ChatFormatting.GOLD);
            h.register(EXTENDED,new Item(new Item.Properties().rarity(rarity)));
            h.register(BROKEN,new Item(new Item.Properties()) {
                @Override public Rarity getRarity(ItemStack stack) {throw new IncompatibleClassChangeError("Deliberate third-party hook fixture");}
                @Override public ItemStack getDefaultInstance() {throw new NoSuchMethodError("Deliberate third-party default-instance fixture");}
            });
        });
    }
    @GameTest(template="outsider_test",timeoutTicks=40)
    public static void extendedRarityAndBrokenHooksRemainInPool(GameTestHelper h) {
        OfferingValues.invalidate();var values=OfferingValues.get(h.getLevel().getServer());
        var extended=ForgeRegistries.ITEMS.getValue(EXTENDED);var broken=ForgeRegistries.ITEMS.getValue(BROKEN);
        h.assertTrue(extended.getRarity(new ItemStack(extended)).ordinal()>=4,"Rarity extension was not installed");
        h.assertTrue(values.unit(extended)>0 && values.unit(broken)>0,"Fallback has no value");
        var distribution=values.distribution(1);
        for(Item item:new Item[]{extended,broken}) {
            int index=-1;for(int i=0;i<values.entries().size();i++)if(values.entries().get(i).item()==item)index=i;
            h.assertTrue(index>=0 && distribution.probability(index)>0,"Compatibility fallback excluded an item");
            h.assertTrue(!OfferingValues.prize(values.entries().get(index)).isEmpty(),"Default-instance fallback failed");
        }
        h.assertTrue(OfferingValues.rarityValue(Rarity.COMMON)==4 && OfferingValues.rarityValue(Rarity.EPIC)==2048,"Vanilla values changed");
        h.succeed();
    }
}
