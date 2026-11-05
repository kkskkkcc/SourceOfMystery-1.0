package com.sourceofmystery.client;

import com.sourceofmystery.SourceOfMystery;
import com.sourceofmystery.block.ModBlocks;
import com.sourceofmystery.config.MysteryConfig;
import com.sourceofmystery.energy.MysteryEnergy;
import com.sourceofmystery.enchanter.*;
import com.sourceofmystery.item.ModItems;
import com.sourceofmystery.offering.OfferingMenu;
import net.minecraft.client.*;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.core.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.*;
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
import java.nio.file.Files;

@Mod.EventBusSubscriber(modid="sourceofmystery",value=Dist.CLIENT)
public final class EnchanterSmokeCheck {
    private static int ticks,step,time;
    private static volatile boolean ready,applied,removed,done;
    private static volatile long applyCost,removeCost;
    private static final BlockPos POS=new BlockPos(0,200,0),OFFERING=new BlockPos(3,200,0);
    private static net.minecraft.world.item.enchantment.Enchantment enchantment(){return ForgeRegistries.ENCHANTMENTS.getValue(new ResourceLocation("enchantcompat","arcane_resonance"));}
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent e){
        if(!Boolean.getBoolean("sourceofmystery.enchanterCheck")||e.phase!=TickEvent.Phase.END)return;
        var mc=Minecraft.getInstance();if(mc.player==null||mc.level==null||mc.getSingleplayerServer()==null)return;ticks++;time++;
        if(step==0&&ticks>80){mc.setScreen(null);mc.getSingleplayerServer().execute(()->{
            var level=mc.getSingleplayerServer().overworld();var p=level.getServer().getPlayerList().getPlayers().get(0);
            MysteryConfig.ENERGY_REGEN_PER_SECOND.set(0d);MysteryConfig.DAILY_REFRESH.set(false);level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,level.getServer());level.getGameRules().getRule(GameRules.RULE_FALL_DAMAGE).set(false,level.getServer());level.setDayTime(8500);
            for(int x=-8;x<=8;x++)for(int z=-8;z<=8;z++){level.setBlock(new BlockPos(x,199,z),Blocks.STONE_BRICKS.defaultBlockState(),2);for(int y=200;y<207;y++)level.setBlock(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState(),2);}
            p.setGameMode(GameType.SURVIVAL);p.getInventory().clearContent();p.teleportTo(level,2.2,200,2.7,140,17);p.fallDistance=0;p.setHealth(p.getMaxHealth());p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(ModItems.MYSTERY_ENCHANTER.get()));MysteryEnergy.setEnergyAndMax(p,200000);
            level.setBlockAndUpdate(POS,ModBlocks.MYSTERY_ENCHANTER.get().defaultBlockState());level.setBlockAndUpdate(OFFERING,ModBlocks.DARK_DIMENSIONAL_OFFERING.get().defaultBlockState());ready=true;
        });step=1;time=0;}
        else if(step==1&&ready&&time>50){capture("01-table-and-held.png");mc.options.hideGui=true;mc.getSingleplayerServer().execute(()->{
            var p=mc.getSingleplayerServer().getPlayerList().getPlayers().get(0);p.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);p.teleportTo(p.serverLevel(),1.5,200,1.9,142,24);
        });step=2;time=0;}
        else if(step==2&&time>25){capture("02-table-close.png");mc.options.hideGui=false;mc.getSingleplayerServer().execute(()->{
            var p=mc.getSingleplayerServer().getPlayerList().getPlayers().get(0);var sword=new ItemStack(Items.DIAMOND_SWORD);sword.setDamageValue(75);sword.getOrCreateTag().putString("othermod_data","keep");p.getInventory().setItem(9,sword);p.inventoryMenu.broadcastChanges();
        });step=3;time=0;}
        else if(step==3&&time>15){use(mc,POS);step=4;time=0;}
        else if(step==4&&mc.screen instanceof MysteryEnchanterScreen&&time>15){
            mc.gameMode.handleInventoryMouseClick(mc.player.containerMenu.containerId,1,0,ClickType.QUICK_MOVE,mc.player);
            var box=(EditBox)mc.screen.children().stream().filter(c->c instanceof EditBox).findFirst().orElseThrow();box.setValue("enchantcompat");step=5;time=0;
        }else if(step==5&&time>15){click(mc,230,61,360,318);step=6;time=0;}
        else if(step==6&&time>15){click(mc,121,152,360,318);step=7;time=0;}
        else if(step==7&&time>15){
            var m=(EnchanterMenu)mc.player.containerMenu;if(m.selected()!=enchantment()||m.targetLevel()!=3)throw new AssertionError("Search/list/level native mouse actions failed");
            applyCost=m.enchantCost();capture("03-enchantment-menu.png");click(mc,74,178,360,318);step=8;time=0;
        }else if(step==8&&time>20){mc.getSingleplayerServer().execute(()->{
            var p=mc.getSingleplayerServer().getPlayerList().getPlayers().get(0);var m=(EnchanterMenu)p.containerMenu;
            if(EnchanterRules.level(m.getSlot(0).getItem(),enchantment())!=3||MysteryEnergy.getEnergy(p)!=200000-applyCost)throw new AssertionError("Foreign enchant/charge failed");applied=true;
        });step=9;time=0;
        }else if(step==9&&applied&&time>10){capture("04-enchanted.png");removeCost=((EnchanterMenu)mc.player.containerMenu).removeCost();click(mc,74,199,360,318);step=10;time=0;}
        else if(step==10&&time>20){mc.getSingleplayerServer().execute(()->{
            var p=mc.getSingleplayerServer().getPlayerList().getPlayers().get(0);var m=(EnchanterMenu)p.containerMenu;var item=m.getSlot(0).getItem();
            if(EnchanterRules.level(item,enchantment())!=0||item.getDamageValue()!=75||!item.getTag().getString("othermod_data").equals("keep")||MysteryEnergy.getEnergy(p)!=200000-applyCost-removeCost)throw new AssertionError("Foreign removal/NBT/charge failed");removed=true;
        });step=11;time=0;
        }else if(step==11&&removed&&time>10){mc.player.closeContainer();mc.getSingleplayerServer().execute(()->{
            var p=mc.getSingleplayerServer().getPlayerList().getPlayers().get(0);p.getInventory().setItem(9,new ItemStack(Items.DIRT));p.addEffect(new MobEffectInstance(MobEffects.LUCK,1200,0));MysteryEnergy.setEnergyAndMax(p,5000);p.inventoryMenu.broadcastChanges();
        });step=12;time=0;}
        else if(step==12&&time>20){use(mc,OFFERING);step=13;time=0;}
        else if(step==13&&mc.screen instanceof OfferingLotteryScreen&&time>15){mc.gameMode.handleInventoryMouseClick(mc.player.containerMenu.containerId,1,0,ClickType.QUICK_MOVE,mc.player);step=14;time=0;}
        else if(step==14&&time>15){var m=(OfferingMenu)mc.player.containerMenu;if(Math.abs(m.luckMultiplier()-1.1)>.001||Math.abs(m.goodMultiplier()-1.1)>.001)throw new AssertionError("Luck/global boost not synced");capture("05-lucky-offering.png");click(mc,160,184,320,314);step=15;time=0;}
        else if(step==15&&time>140){capture("06-lucky-result.png");mc.getSingleplayerServer().execute(()->{
            var p=mc.getSingleplayerServer().getPlayerList().getPlayers().get(0);var m=(OfferingMenu)p.containerMenu;if(m.state()!=2||MysteryEnergy.getEnergy(p)!=4000||!m.getSlot(0).getItem().isEmpty())throw new AssertionError("Lucky roll failed");done=true;
        });step=16;time=0;}
        else if(step==16&&done&&time>10){var report=new com.google.gson.JsonObject();report.addProperty("enchantment_registry_count",ForgeRegistries.ENCHANTMENTS.getValues().size());report.addProperty("foreign_treasure_enchantment_applied_and_removed",true);report.addProperty("native_mouse_search_level_buttons",true);report.addProperty("item_nbt_preserved",true);report.addProperty("vanilla_luck_affects_roll",true);report.addProperty("apply_energy",applyCost);report.addProperty("remove_energy",removeCost);
            try{Files.writeString(mc.gameDirectory.toPath().resolve("enchanter-verification.json"),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(report));}catch(java.io.IOException x){throw new RuntimeException(x);}
            SourceOfMystery.LOGGER.info("ENCHANTER_SMOKE_OK enchantments={} foreign_enchantment=true luck=true",ForgeRegistries.ENCHANTMENTS.getValues().size());mc.stop();
        }
        if(ticks>2200)throw new AssertionError("Enchanter smoke timeout step="+step);
    }
    private static void use(Minecraft mc,BlockPos pos){mc.gameMode.useItemOn(mc.player,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(pos),Direction.SOUTH,pos,false));}
    private static void click(Minecraft mc,double x,double y,int w,int h){double sw=mc.getWindow().getGuiScaledWidth(),sh=mc.getWindow().getGuiScaledHeight(),fit=Math.min(1,Math.min((sw-12)/w,(sh-12)/h));double xx=sw/2+(x-w/2d)*fit,yy=sh/2+(y-h/2d)*fit;mc.screen.mouseClicked(xx,yy,0);mc.screen.mouseReleased(xx,yy,0);}
    private static void capture(String name){var mc=Minecraft.getInstance();Screenshot.grab(mc.gameDirectory,name,mc.getMainRenderTarget(),m->{});int err=org.lwjgl.opengl.GL11.glGetError();if(err!=0)throw new AssertionError("Enchanter GL error "+err);}
}
