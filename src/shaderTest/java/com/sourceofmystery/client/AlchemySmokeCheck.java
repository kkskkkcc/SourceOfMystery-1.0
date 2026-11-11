package com.sourceofmystery.client;

import com.sourceofmystery.SourceOfMystery;
import com.sourceofmystery.alchemy.*;
import com.sourceofmystery.block.ModBlocks;
import com.sourceofmystery.config.MysteryConfig;
import com.sourceofmystery.energy.MysteryEnergy;
import com.sourceofmystery.item.ModItems;
import com.sourceofmystery.network.*;
import net.minecraft.client.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import java.nio.file.Files;
import java.util.*;
import java.util.function.Consumer;

/** Real client input plus authoritative trade, rollover and inventory safety checks. Always silent. */
@Mod.EventBusSubscriber(modid="sourceofmystery",value=Dist.CLIENT)
public final class AlchemySmokeCheck {
    private static final BlockPos POS=new BlockPos(0,200,0);
    private static int phase,time,ticks,index,dayChange;
    private static volatile boolean ready,done;
    private static volatile Throwable failure;
    private static UUID revision;
    private static String pendingCapture;
    private static boolean pendingTooltip;
    private static long initialDay;
    private static ItemStack reward,protectedStack;
    private static final com.google.gson.JsonObject report=new com.google.gson.JsonObject();
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    private static void server(Consumer<ServerPlayer> work){done=false;var mc=Minecraft.getInstance();mc.getSingleplayerServer().execute(()->{try{work.accept(mc.getSingleplayerServer().getPlayerList().getPlayers().get(0));}catch(Throwable t){failure=t;}});}
    private static void next(int n){phase=n;time=0;}
    private static Item relic(){return ForgeRegistries.ITEMS.getValue(new ResourceLocation("lotterycompat","unfamiliar_relic"));}
    private static List<CompoundTag> inventory(ServerPlayer p){return p.getInventory().items.stream().map(s->s.save(new CompoundTag())).toList();}
    private static void clear(ServerPlayer p,int tables){p.getInventory().clearContent();p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(ModItems.ALCHEMIST_TABLE.get(),tables));p.inventoryMenu.broadcastChanges();}
    private static void setup(ServerPlayer p){
        var l=p.serverLevel();l.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,p.server);l.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false,p.server);l.getGameRules().getRule(GameRules.RULE_FALL_DAMAGE).set(false,p.server);
        MysteryConfig.ENERGY_REGEN_FRACTION.set(0d);MysteryConfig.DAILY_REFRESH.set(false);MysteryEnergy.setEnergyAndMax(p,10000);
        for(int x=-5;x<=5;x++)for(int z=-5;z<=5;z++)l.setBlockAndUpdate(new BlockPos(x,199,z),Blocks.STONE_BRICKS.defaultBlockState());
        p.teleportTo(l,2.3,200,2.8,140,16);p.fallDistance=0;p.setHealth(p.getMaxHealth());p.setGameMode(GameType.SURVIVAL);clear(p,2);
        var grid=new TransientCraftingContainer(p.inventoryMenu,3,3);
        Item[] components={Items.GOLD_INGOT,Items.OBSIDIAN,Items.GOLD_INGOT,Items.OBSIDIAN,ModItems.DARK_DIMENSIONAL_OFFERING.get(),Items.OBSIDIAN,Items.GOLD_INGOT,Items.OBSIDIAN,Items.GOLD_INGOT};
        for(int i=0;i<9;i++)grid.setItem(i,new ItemStack(components[i]));
        var recipe=(CraftingRecipe)p.server.getRecipeManager().byKey(new ResourceLocation("sourceofmystery","alchemist_table")).orElseThrow();
        check(recipe.matches(grid,l)&&recipe.assemble(grid,l.registryAccess()).is(ModItems.ALCHEMIST_TABLE.get()),"Exact table recipe failed");
        grid.setItem(0,new ItemStack(Items.GOLD_NUGGET));check(!recipe.matches(grid,l),"Wrong recipe ingredient accepted");report.addProperty("exact_crafting_recipe",true);
        var catalog=AlchemyCatalog.get(p.server);check(catalog.size()>20,"Catalog too small");
        var gold=catalog.stream().filter(t->t.output().is(Items.GOLD_BLOCK)).findFirst().orElseThrow();
        check(gold.materials().size()==1&&gold.materials().get(0).stack().is(Items.GOLD_INGOT)&&gold.materials().get(0).maximum()==9,"Gold block lower-tier pricing incorrect");
        check(catalog.stream().anyMatch(t->t.output().is(relic())&&t.materials().stream().anyMatch(m->ForgeRegistries.ITEMS.getKey(m.stack().getItem()).getNamespace().equals("lotterycompat"))),"Foreign recipe ignored");
        report.addProperty("gold_block_1_to_9_ingots",true);report.addProperty("foreign_registry_recipe_pricing",true);report.addProperty("catalog_count",catalog.size());
        boolean found=false;
        for(int day=0;day<2048;day++){
            l.setDayTime(day*24000L+6000);var shop=AlchemyShop.get(p.server);
            for(int i=0;i<shop.trades().size();i++)if(shop.trades().get(i).output().is(relic())){index=i;found=true;break;}
            if(found)break;
        }
        check(found,"Random daily pool never offered foreign result");var shop=AlchemyShop.get(p.server);revision=shop.revision();initialDay=shop.day();reward=shop.trades().get(index).output().copy();
        check(shop.trades().size()==20&&shop.trades().stream().map(t->t.output().getItem()).distinct().count()==20,"Daily list must contain20 distinct items");
        check(AlchemyShop.get(p.server).revision().equals(revision),"Reopening rerolled day");
        var saved=shop.save(new CompoundTag());check(AlchemyShop.load(saved).save(new CompoundTag()).equals(saved),"Persisted quote roundtrip changed prices");
        AlchemyCatalog.invalidate();check(AlchemyShop.get(p.server).revision().equals(revision),"Recipe reload rerolled current day");
        report.addProperty("twenty_unique_daily_offers",true);report.addProperty("same_day_shared_and_persisted",true);ready=true;
    }
    private static void fillCosts(ServerPlayer p){var m=(AlchemyMenu)p.containerMenu;clear(p,2);int slot=9;
        for(var cost:m.materials(index)){
            var a=cost.copy();int split=Math.max(1,cost.getCount()/2);a.setCount(split);p.getInventory().setItem(slot++,a);
            if(cost.getCount()>split){var b=cost.copy();b.setCount(cost.getCount()-split);p.getInventory().setItem(slot++,b);}
        }
        protectedStack=m.materials(index).get(0).copy();protectedStack.setCount(17);protectedStack.getOrCreateTag().putString("other_mod_owner","preserve");protectedStack.setHoverName(net.minecraft.network.chat.Component.literal("Do not consume"));p.getInventory().setItem(30,protectedStack.copy());p.inventoryMenu.broadcastChanges();
    }
    private static void safetyChecks(ServerPlayer p){var m=(AlchemyMenu)p.containerMenu;var before=inventory(p);long energy=MysteryEnergy.getEnergy(p);
        m.clicked(index,0,ClickType.PICKUP,p);m.clicked(index,2,ClickType.CLONE,p);m.quickMoveStack(p,index);
        check(m.getCarried().isEmpty()&&inventory(p).equals(before),"Quote slots could be taken/cloned");
        check(!m.buy(p,-1,m.revision())&&!m.buy(p,20,m.revision())&&!m.buy(p,index,UUID.randomUUID()),"Invalid purchase accepted");
        check(inventory(p).equals(before)&&MysteryEnergy.getEnergy(p)==energy,"Invalid request changed inventory/energy");
        report.addProperty("ghost_slots_and_invalid_requests_rejected",true);
        int fullIndex=-1;
        for(int i=0;i<20;i++)if(!m.output(i).is(Items.STONE)&&!m.output(i).is(ModItems.ALCHEMIST_TABLE.get())&&m.materials(i).stream().allMatch(c->c.getCount()<64)){fullIndex=i;break;}
        check(fullIndex>=0,"No full-inventory probe trade");
        for(int i=1;i<36;i++)p.getInventory().setItem(i,new ItemStack(Items.STONE,64));int slot=9;
        for(var cost:m.materials(fullIndex)){var c=cost.copy();c.setCount(64);p.getInventory().setItem(slot++,c);}
        before=inventory(p);check(!m.buy(p,fullIndex,m.revision())&&m.feedback()==2,"Full inventory purchase accepted");check(inventory(p).equals(before),"Failed delivery consumed materials");
        report.addProperty("full_inventory_atomic_rollback",true);clear(p,2);p.inventoryMenu.broadcastChanges();done=true;
    }
    private static void click(Minecraft mc,int i){double sw=mc.getWindow().getGuiScaledWidth(),sh=mc.getWindow().getGuiScaledHeight(),fit=Math.min(1,Math.min((sw-12)/420,(sh-12)/350));
        double x=sw/2+(18+i%5*77+35-210)*fit,y=sh/2+(48+i/5*47+17-175)*fit;mc.screen.mouseClicked(x,y,0);mc.screen.mouseReleased(x,y,0);}
    private static void capture(String name,boolean tooltip){
        pendingCapture=name;pendingTooltip=tooltip;
        var mc=Minecraft.getInstance();
        if(tooltip&&mc.screen instanceof AlchemyScreen){
            double sw=mc.getWindow().getGuiScaledWidth(),sh=mc.getWindow().getGuiScaledHeight(),fit=Math.min(1,Math.min((sw-12)/420,(sh-12)/350));
            double x=sw/2+(18+index%5*77+35-210)*fit,y=sh/2+(48+index/5*47+17-175)*fit;
            org.lwjgl.glfw.GLFW.glfwSetCursorPos(mc.getWindow().getWindow(),x/sw*mc.getWindow().getScreenWidth(),y/sh*mc.getWindow().getScreenHeight());
        }
    }
    @SubscribeEvent public static void frame(TickEvent.RenderTickEvent event){
        if(!Boolean.getBoolean("sourceofmystery.alchemyCheck")||event.phase!=TickEvent.Phase.END||pendingCapture==null)return;
        var mc=Minecraft.getInstance();
        Screenshot.grab(mc.gameDirectory,pendingCapture,mc.getMainRenderTarget(),m->{});
        check(org.lwjgl.opengl.GL11.glGetError()==0,"OpenGL error at "+pendingCapture);
        if(pendingCapture.startsWith("05-"))mc.options.hideGui=false;
        pendingCapture=null;
    }
    private static void useBlock(Minecraft mc){mc.gameMode.useItemOn(mc.player,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(POS),Direction.SOUTH,POS,false));}
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event){
        if(!Boolean.getBoolean("sourceofmystery.alchemyCheck")||event.phase!=TickEvent.Phase.END)return;
        var mc=Minecraft.getInstance();if(mc.player==null||mc.level==null||mc.getSingleplayerServer()==null)return;
        if(failure!=null)throw new AssertionError("Alchemy server check",failure);if(++ticks>2400)throw new AssertionError("Alchemy timeout phase="+phase);mc.mouseHandler.releaseMouse();time++;
        if(phase==0){if(time==65){mc.setScreen(null);mc.options.pauseOnLostFocus=false;mc.options.getSoundSourceOptionInstance(SoundSource.MASTER).set(0d);server(AlchemySmokeCheck::setup);}if(ready)next(1);return;}
        if(phase==1&&time==20){mc.gameMode.useItem(mc.player,InteractionHand.MAIN_HAND);next(2);return;}
        if(phase==2&&time>20&&mc.screen instanceof AlchemyScreen){var m=(AlchemyMenu)mc.player.containerMenu;check(m.revision().equals(revision),"Quote UUID not synchronized");
            for(int i=0;i<20;i++)check(!m.output(i).isEmpty()&&!m.materials(i).isEmpty(),"Missing quote/material sync");capture("01-daily-shop-tooltip.png",true);click(mc,index);next(3);return;}
        if(phase==3&&time>15){check(((AlchemyMenu)mc.player.containerMenu).feedback()==1,"Insufficient-material feedback missing");server(p->{check(p.getInventory().countItem(reward.getItem())==0,"Missing-material click gave reward");safetyChecks(p);});next(4);return;}
        if(phase==4&&done&&time>20){server(AlchemySmokeCheck::fillCosts);next(5);return;}
        if(phase==5&&time>20){check(((AlchemyMenu)mc.player.containerMenu).affordable(index),"Exact split-slot materials not recognized");check(((AlchemyMenu)mc.player.containerMenu).feedback()==0,"Inventory change left stale error feedback");report.addProperty("inventory_change_clears_stale_feedback",true);capture("02-ready-to-exchange.png",true);click(mc,index);next(6);return;}
        if(phase==6&&time>20){server(p->{var m=(AlchemyMenu)p.containerMenu;
            check(m.feedback()==4&&p.getInventory().countItem(reward.getItem())==1,"Native click did not deliver exact reward");
            check(ItemStack.matches(p.getInventory().getItem(30),protectedStack),"Custom material NBT was consumed");
            for(var cost:m.materials(index))check(AlchemyMenu.available(p.getInventory(),cost)==0,"Split-slot materials not consumed exactly");
            check(MysteryEnergy.getEnergy(p)==10000,"Shop charged unrequested energy");report.addProperty("native_left_click_atomic_exchange",true);report.addProperty("custom_nbt_and_energy_preserved",true);done=true;
        });next(7);return;}
        if(phase==7&&done&&time>10){capture("03-purchased.png",false);server(p->{p.serverLevel().setDayTime((initialDay+1)*24000+5);dayChange++;});next(8);return;}
        if(phase==8&&time>20){var m=(AlchemyMenu)mc.player.containerMenu;check(!m.revision().equals(revision)&&m.day()==initialDay+1,"Open screen did not refresh at next game day");
            ModNetwork.CHANNEL.sendToServer(new AlchemyTradePacket(m.containerId,index,revision));next(9);return;}
        if(phase==9&&time>20){check(((AlchemyMenu)mc.player.containerMenu).feedback()==3,"Stale daily purchase not rejected");capture("04-new-day.png",true);
            server(p->{check(p.getInventory().countItem(reward.getItem())==1,"Stale day request duplicated output");report.addProperty("day_rollover_live_sync_and_stale_quote_rejected",true);});mc.player.closeContainer();next(10);return;}
        if(phase==10&&time>20){mc.player.setShiftKeyDown(true);mc.getConnection().send(new net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket(mc.player,net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket.Action.PRESS_SHIFT_KEY));
            mc.gameMode.useItemOn(mc.player,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(POS.below()).add(0,.5,0),Direction.UP,POS.below(),false));
            mc.getConnection().send(new net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket(mc.player,net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket.Action.RELEASE_SHIFT_KEY));mc.player.setShiftKeyDown(false);next(11);return;}
        if(phase==11&&time>25){server(p->{check(p.serverLevel().getBlockEntity(POS) instanceof AlchemistTableBlockEntity,"Sneak placement failed");check(p.getMainHandItem().getCount()==1,"Placement consumed wrong table count");p.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);p.teleportTo(p.serverLevel(),1.65,200,1.95,138,27);p.fallDistance=0;p.inventoryMenu.broadcastChanges();done=true;});next(12);return;}
        if(phase==12&&done&&time>20){mc.options.hideGui=true;capture("05-placed-alchemist-table.png",false);next(121);return;}
        if(phase==121&&time>10){useBlock(mc);next(13);return;}
        if(phase==13&&time>20&&mc.screen instanceof AlchemyScreen){capture("06-block-opened-shop.png",false);report.addProperty("held_and_placed_native_open",true);mc.player.closeContainer();next(14);return;}
        if(phase==14&&time>20){server(p->{check(p.gameMode.destroyBlock(POS),"Mining table failed");var drops=p.serverLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,new AABB(POS).inflate(2));
            check(drops.stream().filter(e->e.getItem().is(ModItems.ALCHEMIST_TABLE.get())).mapToInt(e->e.getItem().getCount()).sum()==1,"Mining did not drop exactly one table");report.addProperty("place_mine_drop",true);
            clear(p,1);AlchemyMenus.open(p,null,InteractionHand.MAIN_HAND);done=true;
        });next(15);return;}
        if(phase==15&&done&&time>20&&mc.screen instanceof AlchemyScreen){server(p->{p.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);p.inventoryMenu.broadcastChanges();});next(16);return;}
        if(phase==16&&time>30){check(!(mc.screen instanceof AlchemyScreen),"Portable menu stayed valid after table removed from hand");report.addProperty("portable_hand_validity",true);
            report.addProperty("master_volume",mc.options.getSoundSourceVolume(SoundSource.MASTER));report.addProperty("shader_enabled",OutsiderShaderCompat.active());report.addProperty("real_two_client_multiplayer",false);
            try{Files.writeString(mc.gameDirectory.toPath().resolve("alchemy-verification.json"),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(report));}catch(Exception e){throw new RuntimeException(e);}
            SourceOfMystery.LOGGER.info("ALCHEMY_SMOKE_OK");mc.stop();next(17);
        }
    }
}
