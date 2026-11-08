package com.sourceofmystery.client;

import com.sourceofmystery.SourceOfMystery;
import com.sourceofmystery.client.staff.StaffWheelScreen;
import com.sourceofmystery.config.MysteryConfig;
import com.sourceofmystery.energy.MysteryEnergy;
import com.sourceofmystery.item.ModItems;
import com.sourceofmystery.staff.*;
import net.minecraft.client.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.nio.file.Files;
import java.util.*;
import java.util.function.Consumer;

/** Native input, real server terrain/biomes and actual GPU screenshots in an isolated silent world. */
@Mod.EventBusSubscriber(modid="sourceofmystery",value=Dist.CLIENT)
public final class StaffEnvironmentSmokeCheck {
    private static int ticks,time,step,review;
    private static volatile boolean ready,finished,checkpoint;
    private static volatile Throwable failure;
    private static final com.google.gson.JsonObject report=new com.google.gson.JsonObject();
    private static final BlockPos INSIDE=new BlockPos(18,64,0),OUTSIDE=new BlockPos(502,64,0),CHEST=new BlockPos(8,65,8);
    private static final String[] REVIEWS={"snowy_plains","ocean","jungle","nether_wastes","the_end","cherry_grove"};
    private static void check(boolean pass,String message){if(!pass)throw new AssertionError(message);}
    private static void server(Consumer<ServerPlayer> action) {
        var mc=Minecraft.getInstance();mc.getSingleplayerServer().execute(()->{
            try{action.accept(mc.getSingleplayerServer().getPlayerList().getPlayers().get(0));}catch(Throwable e){failure=e;}
        });
    }
    private static void target(ServerPlayer p,String biome) {
        StaffSpell.ENVIRONMENT_SHIFT.select(p.getMainHandItem());
        p.getMainHandItem().getOrCreateTag().putString(StaffEnvironment.BIOME_KEY,"minecraft:"+biome);
        p.inventoryMenu.broadcastChanges();
    }
    private static void recoverSmall(ServerPlayer p) {
        var data=StaffEnvironment.get(p.serverLevel());var tag=data.save(new CompoundTag());
        var row=tag.getList("Jobs",10).getCompound(0);row.putInt("Radius",50);row.putInt("Stage",0);row.putInt("Cursor",0);row.putInt("ColumnCursor",0);
        data.releaseTickets(p.serverLevel());p.serverLevel().getDataStorage().set("sourceofmystery_staff_environment",StaffEnvironment.load(tag));
        report.addProperty("ocean_jungle_worker_radius_under_test",50);
    }
    private static void lookOverview() {
        var p=Minecraft.getInstance().player;
        p.setYRot(180);p.yRotO=180;p.setXRot(27);p.xRotO=27;
    }
    private static void use() {var mc=Minecraft.getInstance();mc.options.keyUse.setDown(true);mc.gameMode.useItem(mc.player,InteractionHand.MAIN_HAND);}
    private static void release() {var mc=Minecraft.getInstance();mc.options.keyUse.setDown(false);mc.gameMode.releaseUsingItem(mc.player);}
    private static void capture(String name) {
        var mc=Minecraft.getInstance();Screenshot.grab(mc.gameDirectory,name,mc.getMainRenderTarget(),m->{});
        check(org.lwjgl.opengl.GL11.glGetError()==0,"OpenGL error at "+name);
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent e) {
        if(!Boolean.getBoolean("sourceofmystery.staffEnvironmentCheck")||e.phase!=TickEvent.Phase.END)return;
        var mc=Minecraft.getInstance();if(mc.player==null||mc.level==null||mc.getSingleplayerServer()==null)return;
        ticks++;time++;
        if(step==3||step==5||step==7)lookOverview();
        if(failure!=null)throw new AssertionError("Environment server check",failure);
        if(step==0&&time>80) {
            mc.options.getSoundSourceOptionInstance(SoundSource.MASTER).set(0d);mc.options.pauseOnLostFocus=false;mc.setScreen(null);
            server(p->{
                var level=p.serverLevel();MysteryConfig.ENERGY_REGEN_PER_SECOND.set(0d);MysteryConfig.DAILY_REFRESH.set(false);
                level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,p.server);
                level.setDayTime(6000);level.setWeatherParameters(100000,0,false,false);
                p.setGameMode(GameType.CREATIVE);p.getInventory().clearContent();p.teleportTo(level,.5,65,.5,0,4);
                p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(ModItems.DIMENSION_BREAKER_STAFF.get()));target(p,"plains");
                MysteryEnergy.setEnergyAndMax(p,50000);MysteryEnergy.setEnergy(p,0);
                var ids=StaffEnvironment.choices(p.serverLevel().registryAccess());check(ids.size()>=60,"Missing vanilla biomes");
                Set<ResourceLocation> cycled=new HashSet<>();p.setShiftKeyDown(true);
                for(int i=0;i<ids.size();i++){p.getMainHandItem().getItem().use(level,p,InteractionHand.MAIN_HAND);cycled.add(StaffEnvironment.selected(p.getMainHandItem()));}
                p.setShiftKeyDown(false);check(cycled.size()==ids.size(),"Cycle skipped biomes");
                check(MysteryEnergy.getEnergy(p)==0&&!StaffEnvironment.get(level).busy(),"Selection spent energy or cast a spell");
                check(!StaffCasting.begin(p,InteractionHand.MAIN_HAND,p.getMainHandItem()),"Insufficient energy accepted");
                for(int count:new int[]{6,9,24,25,40}) {
                    var layout=StaffWheelLayout.segments(count);
                    for(var segment:layout)check(StaffWheelLayout.hit(layout,Math.cos(segment.angle())*segment.radius(),Math.sin(segment.angle())*segment.radius())==segment.index(),"Wheel ring hit mismatch");
                    check(StaffWheelLayout.hit(layout,0,0)==-1,"Wheel dead zone selects a spell");
                }
                report.addProperty("vanilla_biomes_cycled",ids.size());report.addProperty("multi_ring_hit_test",true);
                MysteryEnergy.setEnergy(p,50000);target(p,"desert");
                level.setBlockAndUpdate(CHEST,Blocks.CHEST.defaultBlockState());((ChestBlockEntity)level.getBlockEntity(CHEST)).setItem(0,new ItemStack(Items.DIAMOND,7));
                for(int y=64;y<=68;y++)level.setBlockAndUpdate(new BlockPos(10,y,8),Blocks.STONE_BRICKS.defaultBlockState());
                ready=true;
            });step=1;time=0;
        } else if(step==1&&ready) {
            if(time==30){mc.gui.setOverlayMessage(net.minecraft.network.chat.Component.empty(),false);mc.setScreen(new StaffWheelScreen(InteractionHand.MAIN_HAND));}
            if(time==40){capture("01-six-spell-wheel.png");double angle=5.5*Math.PI/3-Math.PI/2;mc.screen.mouseClicked(mc.screen.width/2d+Math.cos(angle)*71,mc.screen.height/2d-9+Math.sin(angle)*71,0);}
            if(time==50){mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);use();}
            if(time==85)capture("02-desert-charge.png");
            if(time==95)release();
            if(time==110){server(p->{check(MysteryEnergy.getEnergy(p)==50000&&!StaffEnvironment.get(p.serverLevel()).busy(),"Cancelled cast changed the world or energy");});step=2;time=0;}
        } else if(step==2) {
            if(time==10)use();
            if(time==48)capture("03-desert-player-gesture.png");
            if(time==86)capture("04-desert-release-gesture.png");
            if(time==100){release();server(p->{check(StaffEnvironment.get(p.serverLevel()).busy(),"Native hold did not start conversion");check(MysteryEnergy.getEnergy(p)==48000,"Wrong cast charge");});}
            if(time==130) {
                server(p->{var data=StaffEnvironment.get(p.serverLevel());CompoundTag saved=data.save(new CompoundTag());check(!saved.getList("Jobs",10).isEmpty(),"No checkpoint");check(saved.getList("Jobs",10).getCompound(0).getInt("Radius")==500,"Saved radius is not 500");
                    data.releaseTickets(p.serverLevel());p.serverLevel().getDataStorage().set("sourceofmystery_staff_environment",StaffEnvironment.load(saved));checkpoint=true;
                    // Overlapping casts now replace older work; the dedicated overwrite
                    // fixture checks admission and persistence without changing this desert run.
                    p.teleportTo(p.serverLevel(),.5,120,110,180,27);p.getAbilities().flying=true;p.onUpdateAbilities();
                });
            }
            if(time>140&&time%20==0)server(p->{
                if(time%1200<20){var rows=StaffEnvironment.get(p.serverLevel()).save(new CompoundTag()).getList("Jobs",10);SourceOfMystery.LOGGER.info("ENVIRONMENT_PROGRESS step={} jobs={}",step,rows);}
                if(!StaffEnvironment.get(p.serverLevel()).busy()) {
                check(p.serverLevel().getBlockState(INSIDE).is(Blocks.SAND),"Desert ground not converted");
                check(p.serverLevel().getBlockState(OUTSIDE).is(Blocks.GRASS_BLOCK),"Conversion escaped radius");
                int[][] rim={{499,0},{-499,0},{0,499},{0,-499},{353,353},{-353,353},{353,-353},{-353,-353}};
                for(int[] v:rim)check(p.serverLevel().getBlockState(new BlockPos(v[0],64,v[1])).is(Blocks.SAND),"500-block rim not converted: "+Arrays.toString(v));
                check(p.serverLevel().getNoiseBiome(124,16,0).is(net.minecraft.world.level.biome.Biomes.DESERT),"Far biome not converted");
                check(p.serverLevel().getNoiseBiome(127,16,0).is(net.minecraft.world.level.biome.Biomes.PLAINS),"Biome escaped range");
                report.addProperty("diameter_1000_full_desert_conversion",true);
                check(((ChestBlockEntity)p.serverLevel().getBlockEntity(CHEST)).getItem(0).getCount()==7,"Chest contents damaged");
                check(p.serverLevel().getBlockState(new BlockPos(10,68,8)).is(Blocks.STONE_BRICKS),"Structure damaged");
                check(p.serverLevel().getNoiseBiome(4,16,0).is(net.minecraft.world.level.biome.Biomes.DESERT),"Server biome not updated");finished=true;
            }});
            if(finished) {mc.options.setCameraType(CameraType.FIRST_PERSON);step=3;time=0;}
        } else if(step==3&&time>30) {
            check(mc.level.hasChunk(0,0),"Centre not loaded at client probe");
            check(mc.level.getNoiseBiome(4,16,0).is(net.minecraft.world.level.biome.Biomes.DESERT),"Client biome packet missing");
            lookOverview();capture("05-desert-transformed.png");report.addProperty("desert_server_and_client",true);report.addProperty("checkpoint_resume",checkpoint);
            finished=false;server(p->{p.teleportTo(p.serverLevel(),.5,65,.5,0,5);target(p,"ocean");});step=4;time=0;
        } else if(step==4) {
            if(time==25){mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);use();}
            if(time==65)capture("06-ocean-charge.png");
            if(time==120){release();server(p->{check(StaffEnvironment.get(p.serverLevel()).busy(),"Ocean job missing");recoverSmall(p);p.teleportTo(p.serverLevel(),.5,120,110,180,27);p.getAbilities().flying=true;p.onUpdateAbilities();});}
            if(time>140&&time%20==0)server(p->{
                if(time%1200<20){var rows=StaffEnvironment.get(p.serverLevel()).save(new CompoundTag()).getList("Jobs",10);SourceOfMystery.LOGGER.info("ENVIRONMENT_PROGRESS step={} jobs={}",step,rows);}
                if(!StaffEnvironment.get(p.serverLevel()).busy()) {
                check(p.serverLevel().getBlockState(INSIDE).is(Blocks.WATER),"Ocean did not create water");
                check(p.serverLevel().getBlockState(INSIDE.below(6)).is(Blocks.SAND),"Ocean floor missing");
                check(p.serverLevel().getBlockState(OUTSIDE).is(Blocks.GRASS_BLOCK),"Ocean escaped radius");
                check(p.serverLevel().getNoiseBiome(4,16,0).is(net.minecraft.world.level.biome.Biomes.OCEAN),"Ocean biome missing");
                check(MysteryEnergy.getEnergy(p)==46000,"Ocean cost incorrect");finished=true;
            }});
            if(finished){mc.options.setCameraType(CameraType.FIRST_PERSON);step=5;time=0;}
        } else if(step==5&&time>30) {
            check(mc.level.getNoiseBiome(4,16,0).is(net.minecraft.world.level.biome.Biomes.OCEAN),"Ocean client biome missing");
            lookOverview();capture("07-ocean-transformed.png");report.addProperty("ocean_terrain_and_biome",true);finished=false;
            server(p->{p.teleportTo(p.serverLevel(),.5,65,.5,0,5);target(p,"jungle");});step=6;time=0;
        } else if(step==6) {
            if(time==25){mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);use();}
            if(time==65)capture("08-jungle-charge.png");
            if(time==120){release();server(p->{check(StaffEnvironment.get(p.serverLevel()).busy(),"Jungle job missing");recoverSmall(p);p.teleportTo(p.serverLevel(),.5,120,110,180,27);p.getAbilities().flying=true;p.onUpdateAbilities();});}
            if(time>140&&time%20==0)server(p->{
                if(time%1200<20){var rows=StaffEnvironment.get(p.serverLevel()).save(new CompoundTag()).getList("Jobs",10);SourceOfMystery.LOGGER.info("ENVIRONMENT_PROGRESS step={} jobs={}",step,rows);}
                if(!StaffEnvironment.get(p.serverLevel()).busy()) {
                int logs=0;for(int x=-40;x<=40;x++)for(int z=-40;z<=40;z++)for(int y=65;y<74;y++)if(p.serverLevel().getBlockState(new BlockPos(x,y,z)).is(Blocks.JUNGLE_LOG))logs++;
                check(logs>50,"No jungle vegetation: "+logs);check(p.serverLevel().getBlockState(INSIDE).is(Blocks.GRASS_BLOCK),"Ocean not reclaimed");
                check(MysteryEnergy.getEnergy(p)==44000,"Jungle cost incorrect");report.addProperty("jungle_log_blocks",logs);finished=true;
            }});
            if(finished){mc.options.setCameraType(CameraType.FIRST_PERSON);step=7;time=0;}
        } else if(step==7&&time>30) {
            lookOverview();capture("09-jungle-transformed.png");server(p->{p.teleportTo(p.serverLevel(),.5,65,.5,0,5);target(p,REVIEWS[0]);});step=8;time=0;
        } else if(step==8) {
            if(time==20){mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);use();}
            if(time==65)capture("10-theme-"+REVIEWS[review]+".png");
            if(time==68)release();
            if(time==85) {
                review++;
                if(review<REVIEWS.length){server(p->target(p,REVIEWS[review]));time=0;}
                else {step=9;time=0;}
            }
        } else if(step==9&&time>25) {
            report.addProperty("native_charge_and_cancel",true);report.addProperty("radius_500_boundary",true);report.addProperty("buildings_and_container_preserved",true);
            report.addProperty("master_volume",mc.options.getSoundSourceVolume(SoundSource.MASTER));report.addProperty("runtime_ticks",ticks);
            try{Files.writeString(mc.gameDirectory.toPath().resolve("environment-verification.json"),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(report));}
            catch(Exception error){throw new RuntimeException(error);}
            SourceOfMystery.LOGGER.info("STAFF_ENVIRONMENT_OK");mc.stop();
        }
        if(ticks>36000)throw new AssertionError("Environment check timed out, step="+step+", time="+time);
    }
}
