package com.sourceofmystery.client;

import com.sourceofmystery.SourceOfMystery;
import com.sourceofmystery.block.ModBlocks;
import com.sourceofmystery.config.MysteryConfig;
import com.sourceofmystery.energy.MysteryEnergy;
import com.sourceofmystery.offering.*;
import net.minecraft.client.*;
import net.minecraft.core.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import java.nio.file.*;

/** Full installed modpack, isolated world/config: real extended-rarity item and native menu packets. */
@Mod.EventBusSubscriber(modid="sourceofmystery",value=Dist.CLIENT)
public final class OfferingPackCompatibilitySmokeCheck {
    private static int ticks,step,elapsed,pool,rarities,extendedItems;
    private static volatile boolean ready,verified;
    private static long paid;
    private static double minimumProbability;
    private static final BlockPos POS=new BlockPos(0,200,0);
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if(!Boolean.getBoolean("sourceofmystery.offeringPackCheck") || event.phase!=TickEvent.Phase.END)return;
        var mc=Minecraft.getInstance();if(mc.player==null || mc.level==null || mc.getSingleplayerServer()==null)return;
        ticks++;elapsed++;
        if(step==0 && ticks>100) {
            mc.setScreen(null);mc.getSingleplayerServer().execute(()->{
                var level=mc.getSingleplayerServer().overworld();var p=level.getServer().getPlayerList().getPlayers().get(0);
                MysteryConfig.ENERGY_REGEN_PER_SECOND.set(0d);MysteryConfig.DAILY_REFRESH.set(false);
                level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,level.getServer());
                for(int x=-4;x<=4;x++)for(int z=-4;z<=4;z++) {
                    level.setBlock(new BlockPos(x,199,z),Blocks.STONE_BRICKS.defaultBlockState(),2);
                    for(int y=200;y<206;y++)level.setBlock(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState(),2);
                }
                p.setGameMode(GameType.SURVIVAL);p.teleportTo(level,.5,200,3,180,15);p.getInventory().clearContent();
                level.setBlockAndUpdate(POS,ModBlocks.DARK_DIMENSIONAL_OFFERING.get().defaultBlockState());MysteryEnergy.setEnergyAndMax(p,3000);
                var values=OfferingValues.get(p.server);pool=values.entries().size();rarities=Rarity.values().length;
                ItemStack chosen=ItemStack.EMPTY;
                for(var e:values.entries())try {
                    var stack=OfferingValues.prize(e);
                    if(stack.getRarity().ordinal()>=4) {extendedItems++;if(chosen.isEmpty())chosen=stack;}
                }catch(RuntimeException | LinkageError ignored) {}
                if(extendedItems==0 || rarities<=4)throw new AssertionError("Full pack did not supply extended item rarity");
                var distribution=values.distribution(1);minimumProbability=1;
                for(int i=0;i<pool;i++)minimumProbability=Math.min(minimumProbability,distribution.probability(i));
                if(minimumProbability<=0)throw new AssertionError("Modpack item omitted by probability");
                paid=values.stack(chosen);p.getInventory().setItem(9,chosen);p.inventoryMenu.broadcastChanges();ready=true;
                SourceOfMystery.LOGGER.info("OFFERING_PACK_READY pool={} rarity_types={} extended_items={} input_value={}",pool,rarities,extendedItems,paid);
            });step=1;elapsed=0;
        } else if(step==1 && ready && elapsed>30) {
            mc.setScreen(null);mc.gameMode.useItemOn(mc.player,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(POS),Direction.SOUTH,POS,false));step=2;elapsed=0;
        } else if(step==2 && mc.screen instanceof OfferingLotteryScreen && elapsed>20) {
            mc.gameMode.handleInventoryMouseClick(mc.player.containerMenu.containerId,1,0,ClickType.QUICK_MOVE,mc.player);step=3;elapsed=0;
        } else if(step==3 && elapsed>20) {
            capture("01-full-pack-open.png");mc.gameMode.handleInventoryButtonClick(mc.player.containerMenu.containerId,0);step=4;elapsed=0;
        } else if(step==4 && elapsed>150) {
            capture("02-full-pack-result.png");mc.getSingleplayerServer().execute(()->{
                var p=mc.getSingleplayerServer().getPlayerList().getPlayers().get(0);var menu=(OfferingMenu)p.containerMenu;
                if(menu.state()!=2 || MysteryEnergy.getEnergy(p)!=2000 || !menu.getSlot(0).getItem().isEmpty())throw new AssertionError("Full-pack payment/payout failed");
                if(p.getInventory().items.stream().allMatch(ItemStack::isEmpty))throw new AssertionError("No reward in full pack");verified=true;
            });step=5;elapsed=0;
        } else if(step==5 && verified && elapsed>10) {
            var report=new com.google.gson.JsonObject();report.addProperty("pool_count",pool);report.addProperty("rarity_types",rarities);
            report.addProperty("extended_rarity_items",extendedItems);report.addProperty("minimum_probability",minimumProbability);
            report.addProperty("native_open_and_roll",true);report.addProperty("energy_charge",1000);
            try {Files.writeString(mc.gameDirectory.toPath().resolve("compatibility.json"),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(report));}
            catch(java.io.IOException e){throw new RuntimeException(e);}
            SourceOfMystery.LOGGER.info("OFFERING_PACK_COMPAT_OK pool={} rarity_types={} extended_items={}",pool,rarities,extendedItems);mc.stop();
        }
        if(ticks>2000)throw new AssertionError("Full-pack smoke timeout step="+step);
    }
    private static void capture(String name) {var mc=Minecraft.getInstance();Screenshot.grab(mc.gameDirectory,name,mc.getMainRenderTarget(),m->{});}
}
