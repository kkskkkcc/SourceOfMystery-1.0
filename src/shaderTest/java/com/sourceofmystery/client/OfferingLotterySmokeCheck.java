package com.sourceofmystery.client;

import com.sourceofmystery.SourceOfMystery;
import com.sourceofmystery.block.ModBlocks;
import com.sourceofmystery.config.MysteryConfig;
import com.sourceofmystery.energy.MysteryEnergy;
import com.sourceofmystery.offering.*;
import net.minecraft.client.*;
import net.minecraft.core.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.sound.PlaySoundEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

/** Real production-protocol client flow: block use, shift-click, button, reel, payout and sound. */
@Mod.EventBusSubscriber(modid="sourceofmystery",value=Dist.CLIENT)
public final class OfferingLotterySmokeCheck {
    private static int ticks,step,elapsed;
    private static volatile boolean ready,checked;
    private static long winSeed;
    private static int firstMenu=-1;
    private static boolean winSound,lossSound;
    private static final BlockPos ALTAR=new BlockPos(0,200,0);
    @SubscribeEvent public static void seed(TickEvent.PlayerTickEvent event) {
        if(Boolean.getBoolean("sourceofmystery.offeringLotteryCheck") && event.phase==TickEvent.Phase.END && !event.player.level().isClientSide && event.player.containerMenu instanceof OfferingMenu m && !m.rolling()) {
            if(firstMenu<0)firstMenu=m.containerId;
            // Distinct seeds preserve the production invariant that successive roll tokens differ.
            event.player.getRandom().setSeed(winSeed+(m.containerId==firstMenu?0:1));
        }
    }
    @SubscribeEvent public static void sound(PlaySoundEvent event) {
        if(!Boolean.getBoolean("sourceofmystery.offeringLotteryCheck") || event.getSound()==null)return;
        var path=event.getSound().getLocation().getPath();
        if(path.equals("entity.player.levelup"))winSound=true;
        if(path.equals("block.note_block.bass"))lossSound=true;
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if(!Boolean.getBoolean("sourceofmystery.offeringLotteryCheck") || event.phase!=TickEvent.Phase.END)return;
        var mc=Minecraft.getInstance();if(mc.player==null || mc.level==null || mc.getSingleplayerServer()==null)return;
        if(ticks++==0) {
            mc.getSingleplayerServer().execute(()->{
                var level=mc.getSingleplayerServer().overworld();var p=level.getServer().getPlayerList().getPlayers().get(0);
                MysteryConfig.ENERGY_REGEN_PER_SECOND.set(0d);MysteryConfig.DAILY_REFRESH.set(false);
                level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,level.getServer());level.setDayTime(9000);
                for(int x=-5;x<=5;x++)for(int z=-5;z<=5;z++) {
                    level.setBlock(new BlockPos(x,199,z),Blocks.STONE_BRICKS.defaultBlockState(),2);
                    for(int y=200;y<206;y++)level.setBlock(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState(),2);
                }
                p.setGameMode(GameType.SURVIVAL);p.getInventory().clearContent();p.teleportTo(level,.5,200,3,180,15);
                level.setBlockAndUpdate(ALTAR,ModBlocks.DARK_DIMENSIONAL_OFFERING.get().defaultBlockState());
                p.getInventory().setItem(9,new ItemStack(Items.DIRT,1));MysteryEnergy.setEnergyAndMax(p,3000);
                var values=OfferingValues.get(p.server);
                var token=ForgeRegistries.ITEMS.getValue(new ResourceLocation("lotterycompat","unfamiliar_token"));
                var relic=ForgeRegistries.ITEMS.getValue(new ResourceLocation("lotterycompat","unfamiliar_relic"));
                if(token==Items.AIR || relic==Items.AIR)throw new AssertionError("Foreign fixture not registered");
                var dist=values.distribution(1);
                for(Item item:new Item[]{token,relic}) {
                    int index=-1;for(int i=0;i<values.entries().size();i++)if(values.entries().get(i).item()==item)index=i;
                    if(index<0 || dist.probability(index)<=0)throw new AssertionError("Foreign item omitted/locked");
                }
                // Pick a winning seed in this isolated test only. Production always uses server RNG.
                for(long seed=0;seed<10000;seed++) {
                    if(values.stack(OfferingValues.prize(values.distribution(values.stack(new ItemStack(Items.DIRT))).sample(net.minecraft.util.RandomSource.create(seed))))>values.stack(new ItemStack(Items.DIRT))) {
                        winSeed=seed;p.getRandom().setSeed(seed);break;
                    }
                }
                ready=true;
            });return;
        }
        if(!ready)return;
        elapsed++;
        if(step==0 && elapsed>70) {
            mc.gameMode.useItemOn(mc.player,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(ALTAR),Direction.SOUTH,ALTAR,false));step=1;elapsed=0;
        } else if(step==1 && mc.screen instanceof OfferingLotteryScreen && elapsed>10) {
            // Menu slot 1 maps to the first main-inventory cell (index 9).
            mc.gameMode.handleInventoryMouseClick(mc.player.containerMenu.containerId,1,0,ClickType.QUICK_MOVE,mc.player);step=2;elapsed=0;
        } else if(step==2 && elapsed==12)capture("01-ready.png");
        else if(step==2 && elapsed>18) {
            clickDraw(mc);step=3;elapsed=0;
        } else if(step==3 && elapsed==35)capture("02-rolling.png");
        else if(step==3 && elapsed>135) {
            if(!winSound)throw new AssertionError("Win feedback not played");capture("03-win.png");
            mc.player.closeContainer();mc.getSingleplayerServer().execute(()->{
                var p=mc.getSingleplayerServer().getPlayerList().getPlayers().get(0);
                if(MysteryEnergy.getEnergy(p)!=2000)throw new AssertionError("First charge differs from 1000");
                int count=0;for(var s:p.getInventory().items)count+=s.getCount();if(count!=1)throw new AssertionError("First payout count "+count);
                p.getInventory().clearContent();p.getInventory().setItem(9,new ItemStack(Items.NETHERITE_BLOCK,64));p.inventoryMenu.broadcastChanges();
            });step=4;elapsed=0;
        } else if(step==4 && elapsed>25) {
            mc.gameMode.useItemOn(mc.player,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(ALTAR),Direction.SOUTH,ALTAR,false));step=5;elapsed=0;
        } else if(step==5 && mc.screen instanceof OfferingLotteryScreen && elapsed>10) {
            mc.gameMode.handleInventoryMouseClick(mc.player.containerMenu.containerId,1,0,ClickType.QUICK_MOVE,mc.player);step=6;elapsed=0;
        } else if(step==6 && elapsed>15) {
            mc.gameMode.handleInventoryButtonClick(mc.player.containerMenu.containerId,0);step=7;elapsed=0;
        } else if(step==7 && elapsed>135) {
            capture("04-loss.png");if(!lossSound)throw new AssertionError("Loss feedback not played state="+((OfferingMenu)mc.player.containerMenu).state()+" quote="+((OfferingMenu)mc.player.containerMenu).quotedValue());
            mc.getSingleplayerServer().execute(()->{
                var p=mc.getSingleplayerServer().getPlayerList().getPlayers().get(0);
                if(MysteryEnergy.getEnergy(p)!=1000)throw new AssertionError("Second charge differs from 1000");
                int count=0;for(var s:p.getInventory().items)count+=s.getCount();if(count!=1)throw new AssertionError("Second payout count "+count);
                checked=true;
            });step=8;elapsed=0;
        } else if(step==8 && checked && elapsed>10) {
            mc.player.closeContainer();checked=false;
            mc.options.guiScale().set(3);mc.resizeDisplay();
            mc.getSingleplayerServer().execute(()->{
                var p=mc.getSingleplayerServer().getPlayerList().getPlayers().get(0);p.getInventory().clearContent();
                p.getInventory().setItem(0,new ItemStack(com.sourceofmystery.item.ModItems.DARK_DIMENSIONAL_OFFERING.get()));
                p.getInventory().setItem(9,new ItemStack(Items.COBBLESTONE));MysteryEnergy.setEnergy(p,999);MysteryEnergy.syncToClient(p);p.inventoryMenu.broadcastChanges();
            });step=9;elapsed=0;
        } else if(step==9 && elapsed>20) {
            mc.gameMode.useItem(mc.player,InteractionHand.MAIN_HAND);step=10;elapsed=0;
        } else if(step==10 && mc.screen instanceof OfferingLotteryScreen && elapsed>10) {
            mc.gameMode.handleInventoryMouseClick(mc.player.containerMenu.containerId,1,0,ClickType.QUICK_MOVE,mc.player);step=11;elapsed=0;
        } else if(step==11 && elapsed>15) {
            mc.gameMode.handleInventoryButtonClick(mc.player.containerMenu.containerId,0);step=12;elapsed=0;
        } else if(step==12 && elapsed>20) {
            capture("05-insufficient.png");
            mc.getSingleplayerServer().execute(()->{
                var p=mc.getSingleplayerServer().getPlayerList().getPlayers().get(0);var m=(OfferingMenu)p.containerMenu;
                if(MysteryEnergy.getEnergy(p)!=999 || !m.getSlot(0).getItem().is(Items.COBBLESTONE) || m.error()!=2)throw new AssertionError("Native insufficient-energy guard failed");
                checked=true;
            });step=13;elapsed=0;
        } else if(step==13 && checked && elapsed>10) {
            SourceOfMystery.LOGGER.info("OFFERING_LOTTERY_OK native_menu=true charge=1000 win_sound=true loss_sound=true foreign_namespace=true portable=true insufficient_guard=true shader={}",OutsiderShaderCompat.active());mc.stop();
        }
        if(ticks>1500)throw new AssertionError("Lottery smoke timeout step="+step);
    }
    private static void capture(String name) {
        var mc=Minecraft.getInstance();Screenshot.grab(mc.gameDirectory,name,mc.getMainRenderTarget(),m->{});
        int error=org.lwjgl.opengl.GL11.glGetError();if(error!=0)throw new AssertionError("Lottery GL error "+error);
    }
    private static void clickDraw(Minecraft mc) {
        double w=mc.getWindow().getGuiScaledWidth(),h=mc.getWindow().getGuiScaledHeight();
        double scale=Math.min(1,Math.min((w-12)/320,(h-12)/314));
        if(!mc.screen.mouseClicked(w/2,h/2+27*scale,0))throw new AssertionError("Draw button mouse hit test failed");
        mc.screen.mouseReleased(w/2,h/2+27*scale,0);
    }
}
