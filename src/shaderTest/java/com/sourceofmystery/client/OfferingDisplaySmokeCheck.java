package com.sourceofmystery.client;

import com.sourceofmystery.SourceOfMystery;
import com.sourceofmystery.block.*;
import com.sourceofmystery.item.ModItems;
import com.sourceofmystery.entity.OutsiderEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import software.bernie.geckolib.animatable.GeoItem;

/** Real client preview: placed sculpture, held 3D item, inventory and ritual priority. */
@Mod.EventBusSubscriber(modid="sourceofmystery",value=Dist.CLIENT)
public final class OfferingDisplaySmokeCheck {
    private static int ticks;
    private static volatile boolean ready,ritualOkay;
    private static boolean completed;
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event){
        if(!Boolean.getBoolean("sourceofmystery.offeringDisplayCheck")||completed||event.phase!=TickEvent.Phase.END)return;
        var mc=Minecraft.getInstance();if(mc.player==null||mc.level==null||mc.getSingleplayerServer()==null)return;
        if(ticks++==0){mc.options.fov().set(50);mc.getSingleplayerServer().execute(()->{
            var level=mc.getSingleplayerServer().overworld();
            level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,level.getServer());
            level.setDayTime(9000);level.setWeatherParameters(100000,0,false,false);
            for(var e:com.google.common.collect.Lists.newArrayList(level.getAllEntities()))if(!(e instanceof net.minecraft.server.level.ServerPlayer))e.discard();
            for(int x=-12;x<=12;x++)for(int z=-12;z<=12;z++){
                level.setBlock(new BlockPos(x,199,z),Blocks.STONE_BRICKS.defaultBlockState(),2);
                for(int y=200;y<208;y++)level.setBlock(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState(),2);
            }
            var p=level.getServer().getPlayerList().getPlayers().get(0);p.setGameMode(GameType.CREATIVE);p.getInventory().clearContent();
            p.teleportTo(level,2.0,200,2.5,145,14);p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(ModItems.DARK_DIMENSIONAL_OFFERING.get()));
            for(int x:new int[]{0,3})level.setBlockAndUpdate(new BlockPos(x,200,0),ModBlocks.DARK_DIMENSIONAL_OFFERING.get().defaultBlockState());
            ready=true;
        });return;}
        if(!ready)return;
        if(ticks==120)capture("01-placed-and-held.png");
        if(ticks==130){mc.options.hideGui=true;mc.getSingleplayerServer().execute(()->{
            var p=mc.getSingleplayerServer().getPlayerList().getPlayers().get(0);p.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);p.teleportTo(p.serverLevel(),1.1,200,1.4,142,32);
        });}
        if(ticks==160)capture("02-model-close.png");
        if(ticks==170)mc.getSingleplayerServer().execute(()->{
            var p=mc.getSingleplayerServer().getPlayerList().getPlayers().get(0);p.teleportTo(p.serverLevel(),-1.4,200,-1.2,-49,30);
        });
        if(ticks==200)capture("03-model-back.png");
        if(ticks==210){mc.options.hideGui=false;mc.player.getInventory().setItem(0,new ItemStack(ModItems.DARK_DIMENSIONAL_OFFERING.get()));mc.setScreen(new net.minecraft.client.gui.screens.inventory.InventoryScreen(mc.player));}
        if(ticks==235)capture("04-inventory.png");
        if(ticks==240){mc.setScreen(null);mc.getSingleplayerServer().execute(()->{
            var level=mc.getSingleplayerServer().overworld();var p=level.getServer().getPlayerList().getPlayers().get(0);p.setGameMode(GameType.SURVIVAL);
            for(int u=0;u<4;u++)for(int v=0;v<5;v++)if(u==0||u==3||v==0||v==4)level.setBlockAndUpdate(new BlockPos(u-2,v+200,-6),Blocks.OBSIDIAN.defaultBlockState());
            p.teleportTo(level,0,200,0,180,0);ItemStack stack=new ItemStack(ModItems.DARK_DIMENSIONAL_OFFERING.get());p.setItemInHand(InteractionHand.MAIN_HAND,stack);
            BlockPos clicked=new BlockPos(-2,200,-6);var hit=new BlockHitResult(Vec3.atCenterOf(clicked),Direction.SOUTH,clicked,false);
            var result=stack.getItem().useOn(new UseOnContext(p,InteractionHand.MAIN_HAND,hit));
            if(!result.consumesAction()||!stack.isEmpty()||level.getEntitiesOfClass(OutsiderEntity.class,new AABB(-12,198,-16,12,212,4)).stream().noneMatch(OutsiderEntity::isOfferingIntro))throw new AssertionError("Offering did not preserve ritual priority");
            if(level.getBlockState(clicked.relative(Direction.SOUTH)).is(ModBlocks.DARK_DIMENSIONAL_OFFERING.get()))throw new AssertionError("Display placed instead of starting ritual");
            ritualOkay=true;
        });}
        if(ticks==275){
            if(!ritualOkay)throw new AssertionError("Ritual test incomplete");
            if(!(ModItems.DARK_DIMENSIONAL_OFFERING.get() instanceof GeoItem))throw new AssertionError("Item is not GeckoLib");
            completed=true;SourceOfMystery.LOGGER.info("OFFERING_DISPLAY_OK shader={} ritual_priority=true",OutsiderShaderCompat.active());mc.stop();
        }
        if(ticks>700)throw new AssertionError("Display preview timeout");
    }
    private static void capture(String name){
        var mc=Minecraft.getInstance();Screenshot.grab(mc.gameDirectory,name,mc.getMainRenderTarget(),m->{});
        int error=org.lwjgl.opengl.GL11.glGetError();if(error!=0)throw new AssertionError("Offering display GL "+error);
    }
}
