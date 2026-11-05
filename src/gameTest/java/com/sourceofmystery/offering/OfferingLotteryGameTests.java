package com.sourceofmystery.offering;

import com.mojang.authlib.GameProfile;
import com.sourceofmystery.energy.MysteryEnergy;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.item.*;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.*;
import net.minecraftforge.registries.ForgeRegistries;
import java.util.UUID;

@GameTestHolder("sourceofmystery")
@PrefixGameTestTemplate(false)
public final class OfferingLotteryGameTests {
    private static OfferingMenu menu(FakePlayer p,int id) {var m=new OfferingMenu(id,p.getInventory(),(net.minecraft.core.BlockPos)null);p.containerMenu=m;return m;}
    private static int count(FakePlayer p) {int n=0;for(int i=0;i<p.getInventory().getContainerSize();i++)n+=p.getInventory().getItem(i).getCount();return n;}
    @GameTest(template="outsider_test",timeoutTicks=40)
    public static void recoveryAndIsolation(GameTestHelper h) {
        var p=new FakePlayer(h.getLevel(),new GameProfile(UUID.randomUUID(),"LotteryRecover"));
        var other=new FakePlayer(h.getLevel(),new GameProfile(UUID.randomUUID(),"LotteryOther"));
        var m=menu(p,20);var second=menu(other,21);m.getSlot(0).set(new ItemStack(Items.DIAMOND,7));
        h.assertTrue(second.getSlot(0).getItem().isEmpty(),"Altar viewers share an input slot");
        // Simulate a saved player logging back in with the canonical escrow, without a live menu.
        OfferingEscrow.recover(p);h.assertTrue(count(p)==7,"Saved input recovery failed");
        // Clear the stale in-memory slot exactly as a disconnected menu would be discarded.
        p.containerMenu=p.inventoryMenu;
        OfferingEscrow.input(p,ItemStack.EMPTY);OfferingEscrow.commit(p,new ItemStack(Items.EMERALD));
        var clone=new FakePlayer(h.getLevel(),new GameProfile(UUID.randomUUID(),"LotteryClone"));
        MysteryEnergy.onPlayerClone(p,clone);OfferingEscrow.clone(new net.minecraftforge.event.entity.player.PlayerEvent.Clone(clone,p,true));
        OfferingEscrow.recover(p);OfferingEscrow.recover(clone);OfferingEscrow.recover(clone);
        h.assertTrue(count(p)==7 && count(clone)==1,"Cloned reward lost or duplicated");
        for(int i=0;i<36;i++)other.getInventory().setItem(i,new ItemStack(Items.STONE,64));
        var pos=h.absolutePos(new net.minecraft.core.BlockPos(3,3,3));other.setPos(net.minecraft.world.phys.Vec3.atCenterOf(pos));
        OfferingEscrow.commit(other,new ItemStack(Items.DIAMOND));OfferingEscrow.recover(other);
        var drops=h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,new net.minecraft.world.phys.AABB(pos).inflate(3));
        h.assertTrue(drops.stream().filter(e->e.getItem().is(Items.DIAMOND)).mapToInt(e->e.getItem().getCount()).sum()==1,"Full inventory deleted/duplicated reward");
        drops.forEach(net.minecraft.world.entity.item.ItemEntity::discard);second.removed(other);
        var anchor=h.absolutePos(new net.minecraft.core.BlockPos(5,3,3));h.getLevel().setBlockAndUpdate(anchor,com.sourceofmystery.block.ModBlocks.DARK_DIMENSIONAL_OFFERING.get().defaultBlockState());
        var anchored=new OfferingMenu(22,p.getInventory(),anchor);p.setPos(net.minecraft.world.phys.Vec3.atCenterOf(anchor));
        h.assertTrue(anchored.stillValid(p),"Nearby altar invalid");h.getLevel().removeBlock(anchor,false);h.assertFalse(anchored.stillValid(p),"Broken altar keeps menu valid");
        h.succeed();
    }
    @GameTest(template="outsider_test",timeoutTicks=180)
    public static void lotteryTransactionsAndProbability(GameTestHelper h) {
        var p=new FakePlayer(h.getLevel(),new GameProfile(UUID.randomUUID(),"LotteryTest"));
        p.setPos(h.absoluteVec(new net.minecraft.world.phys.Vec3(2,3,2)));p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        var values=OfferingValues.get(h.getLevel().getServer());
        long registryCount=ForgeRegistries.ITEMS.getValues().stream().filter(i->i!=Items.AIR && i.isEnabled(h.getLevel().enabledFeatures())).count();
        h.assertTrue(values.entries().size()==registryCount,"Pool omitted registered enabled item types");
        var cheap=values.distribution(1);var expensive=values.distribution(65536);
        for(int i=0;i<values.entries().size();i++)h.assertTrue(cheap.probability(i)>0 && expensive.probability(i)>0,"Zero probability for "+values.entries().get(i).item());
        h.assertTrue(expensive.expectedValue()>cheap.expectedValue(),"Higher input did not improve expected reward");
        h.assertTrue(values.stack(new ItemStack(Items.DIAMOND,64))==64*values.unit(Items.DIAMOND),"Count ignored");
        h.assertTrue(values.unit(Items.DIAMOND)>values.unit(Items.DIRT),"Material anchors broken");
        var m=menu(p,10);MysteryEnergy.setEnergyAndMax(p,5000);
        h.assertFalse(m.clickMenuButton(p,0),"Empty input charged");h.assertTrue(MysteryEnergy.getEnergy(p)==5000,"Empty input consumed energy");
        m.getSlot(0).set(new ItemStack(Items.DIAMOND,3));MysteryEnergy.setEnergy(p,999);
        h.assertFalse(m.clickMenuButton(p,0),"Insufficient energy accepted");h.assertTrue(m.getSlot(0).getItem().getCount()==3 && MysteryEnergy.getEnergy(p)==999,"Failed draw consumed payment");
        m.removed(p);h.assertTrue(count(p)==3,"Unpaid input not returned");m.removed(p);h.assertTrue(count(p)==3,"Input returned twice");p.getInventory().clearContent();
        var roll=menu(p,11);MysteryEnergy.setEnergy(p,5000);roll.getSlot(0).set(new ItemStack(Items.DIRT,32));
        h.assertTrue(roll.clickMenuButton(p,0),"Valid roll rejected");
        h.assertTrue(MysteryEnergy.getEnergy(p)==4000 && roll.getSlot(0).getItem().isEmpty(),"Wrong charge or partial stack consumption");
        h.assertFalse(roll.clickMenuButton(p,0),"Repeated roll accepted");
        h.assertTrue(MysteryEnergy.getEnergy(p)==4000 && count(p)==0,"Repeat spent energy or early payout");
        roll.removed(p);h.assertTrue(count(p)==1,"Closing early did not deliver one committed reward");roll.removed(p);OfferingEscrow.recover(p);h.assertTrue(count(p)==1,"Closing/recovery duplicated reward");
        p.getInventory().clearContent();var timed=menu(p,12);timed.getSlot(0).set(new ItemStack(Items.STICK));
        h.assertTrue(timed.clickMenuButton(p,0),"Second draw rejected");
        h.runAfterDelay(OfferingMenu.ROLL_TICKS+2,()->{
            timed.broadcastChanges();h.assertTrue(timed.state()==2 && count(p)==1,"Timed completion failed");
            timed.broadcastChanges();timed.removed(p);h.assertTrue(count(p)==1 && MysteryEnergy.getEnergy(p)==3000,"Timed reward duplicated or extra charge");
            p.containerMenu=p.inventoryMenu;h.succeed();
        });
    }
}
