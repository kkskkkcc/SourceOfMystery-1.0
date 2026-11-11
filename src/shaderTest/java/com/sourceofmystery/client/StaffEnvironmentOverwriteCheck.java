package com.sourceofmystery.client;

import com.sourceofmystery.SourceOfMystery;
import com.sourceofmystery.config.MysteryConfig;
import com.sourceofmystery.energy.MysteryEnergy;
import com.sourceofmystery.item.ModItems;
import com.sourceofmystery.staff.*;
import net.minecraft.client.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.nio.file.Files;
import java.util.*;
import java.util.function.Consumer;

/** Real native recasting while a 1000-block job is unfinished; bounded workers test last-cast ownership. */
@Mod.EventBusSubscriber(modid="sourceofmystery",value=Dist.CLIENT)
public final class StaffEnvironmentOverwriteCheck {
    private static final String DATA="sourceofmystery_staff_environment";
    private static final BlockPos OLD=new BlockPos(472,64,0),OVERLAP=new BlockPos(532,64,0),NEW=new BlockPos(584,64,0),OUT=new BlockPos(616,64,0),CHEST=new BlockPos(522,65,8),WALL=new BlockPos(524,68,8);
    private static int phase,time,ticks,syncWait;private static volatile Throwable failure;private static volatile boolean ready,complete;
    private static UUID firstToken;private static CompoundTag older;private static boolean checkpoint;
    private static final com.google.gson.JsonObject report=new com.google.gson.JsonObject();
    private static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    private static void server(Consumer<ServerPlayer> action){var mc=Minecraft.getInstance();mc.getSingleplayerServer().execute(()->{try{action.accept(mc.getSingleplayerServer().getPlayerList().getPlayers().get(0));}catch(Throwable error){failure=error;}});}
    private static ListTag rows(ServerPlayer p){return StaffEnvironment.get(p.serverLevel()).save(new CompoundTag()).getList("Jobs",Tag.TAG_COMPOUND);}
    private static void replace(ServerPlayer p,ListTag rows){var data=StaffEnvironment.get(p.serverLevel());data.releaseTickets(p.serverLevel());var tag=new CompoundTag();tag.put("Jobs",rows);p.serverLevel().getDataStorage().set(DATA,StaffEnvironment.load(tag));}
    private static ListTag list(CompoundTag row){var rows=new ListTag();rows.add(row);return rows;}
    private static void target(ServerPlayer p,String id){StaffSpell.ENVIRONMENT_SHIFT.select(p.getMainHandItem());p.getMainHandItem().getOrCreateTag().putString(StaffEnvironment.BIOME_KEY,"minecraft:"+id);p.inventoryMenu.broadcastChanges();}
    private static void position(ServerPlayer p,double x){p.teleportTo(p.serverLevel(),x,65,.5,0,0);p.getAbilities().flying=true;p.onUpdateAbilities();}
    private static boolean cast(ServerPlayer p,String biome){target(p,biome);return StaffCasting.begin(p,InteractionHand.MAIN_HAND,p.getMainHandItem())&&StaffCasting.finish(p,p.getMainHandItem());}
    private static void small(CompoundTag row){row.putInt("Radius",48);row.putInt("Stage",0);row.putInt("Cursor",0);row.putInt("ColumnCursor",0);for(Tag value:row.getList("ExcludedAreas",Tag.TAG_COMPOUND))((CompoundTag)value).putInt("Radius",48);}
    private static void use(){var mc=Minecraft.getInstance();mc.options.keyUse.setDown(true);mc.gameMode.useItem(mc.player,InteractionHand.MAIN_HAND);}
    private static void release(){var mc=Minecraft.getInstance();mc.options.keyUse.setDown(false);mc.gameMode.releaseUsingItem(mc.player);}
    private static void next(int stage){phase=stage;time=0;}
    private static void capture(String name){var mc=Minecraft.getInstance();Screenshot.grab(mc.gameDirectory,name,mc.getMainRenderTarget(),m->{});int error=org.lwjgl.opengl.GL11.glGetError();check(error==0,"OpenGL error "+error+" in "+name);}
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event){
        if(!Boolean.getBoolean("sourceofmystery.staffEnvironmentOverwriteCheck")||event.phase!=TickEvent.Phase.END)return;
        var mc=Minecraft.getInstance();if(mc.level==null||mc.player==null||mc.getSingleplayerServer()==null)return;
        if(failure!=null)throw new AssertionError("Environment overwrite server regression",failure);
        if(++ticks>12000)throw new AssertionError("Overwrite regression timeout phase="+phase);
        mc.mouseHandler.releaseMouse();time++;
        if(phase==0){
            if(time==80){mc.setScreen(null);mc.options.pauseOnLostFocus=false;mc.options.getSoundSourceOptionInstance(SoundSource.MASTER).set(0d);server(p->{
                var level=p.serverLevel();level.setDayTime(6000);level.setWeatherParameters(100000,0,false,false);level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,p.server);
                MysteryConfig.ENERGY_REGEN_FRACTION.set(0d);MysteryConfig.DAILY_REFRESH.set(false);p.setGameMode(GameType.CREATIVE);p.getInventory().clearContent();p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(ModItems.DIMENSION_BREAKER_STAFF.get()));MysteryEnergy.setEnergyAndMax(p,100000);position(p,.5);target(p,"desert");ready=true;
            });}
            if(ready)next(1);return;
        }
        if(phase==1){
            if(time==10)use();
            if(time==120){release();server(p->{var jobs=rows(p);check(jobs.size()==1&&jobs.getCompound(0).getInt("Radius")==500,"Initial full-size conversion missing");check(MysteryEnergy.getEnergy(p)==98000,"Initial cast fee incorrect");firstToken=jobs.getCompound(0).getUUID("Token");position(p,.5);target(p,"snowy_plains");});}
            if(time==140)next(2);return;
        }
        if(phase==2){
            if(time==10)use();
            if(time==120){release();server(p->{var jobs=rows(p);check(jobs.size()==1,"Co-located recast was rejected or retained its covered predecessor");var row=jobs.getCompound(0);check(row.getInt("Radius")==500&&row.getString("Biome").equals("minecraft:snowy_plains")&&!row.getUUID("Token").equals(firstToken),"New full-size spell did not replace old task");check(MysteryEnergy.getEnergy(p)==96000,"Recast did not spend exactly 2000");report.addProperty("native_1000_diameter_recast_while_busy",true);small(row);replace(p,list(row));});}
            if(time==140)next(3);return;
        }
        if(phase==3){
            if(time%20==0&&!complete)server(p->{if(!StaffEnvironment.get(p.serverLevel()).busy()){check(p.serverLevel().getBlockState(new BlockPos(10,64,0)).is(Blocks.SNOW_BLOCK),"Recast did not change the real surface");check(p.serverLevel().getNoiseBiome(2,16,0).is(Biomes.SNOWY_PLAINS),"Recast did not change the server biome");complete=true;}});
            if(complete&&++syncWait>=10){check(mc.level.getNoiseBiome(2,16,0).is(Biomes.SNOWY_PLAINS),"Client did not receive the replacement biome");capture("01-same-centre-overwrite.png");report.addProperty("same_centre_surface_server_and_client_biome",true);complete=false;server(p->{position(p,512.5);});next(4);}return;
        }
        if(phase==4){
            if(time==40)server(p->{
                var level=p.serverLevel();level.setBlockAndUpdate(CHEST,Blocks.CHEST.defaultBlockState());((ChestBlockEntity)level.getBlockEntity(CHEST)).setItem(0,new ItemStack(Items.DIAMOND,7));level.setBlockAndUpdate(WALL,Blocks.STONE_BRICKS.defaultBlockState());
                position(p,512.5);check(cast(p,"desert"),"Partial-overlap predecessor failed");position(p,544.5);check(cast(p,"snowy_plains"),"Partial overlap was still rejected");
                var jobs=rows(p);check(jobs.size()==2,"Partial overlap discarded the old non-overlapping area");older=jobs.getCompound(1).copy();var newer=jobs.getCompound(0).copy();
                check(older.getInt("Radius")==500&&newer.getInt("Radius")==500,"Admission was not tested at production radius");check(older.getList("ExcludedAreas",Tag.TAG_COMPOUND).size()==1,"Older job did not relinquish overlapping area");check(!older.getUUID("Token").equals(newer.getUUID("Token")),"Overlapping jobs share a chunk ticket identity");
                small(older);small(newer);replace(p,list(newer));report.addProperty("partial_overlap_admitted_at_radius_500",true);report.addProperty("independent_chunk_ticket_identities",true);report.addProperty("worker_radius_under_test",48);check(MysteryEnergy.getEnergy(p)==92000,"Partial-overlap fees incorrect");
            });
            if(time==60)next(5);return;
        }
        if(phase==5){
            if(time%20==0&&!complete)server(p->{if(!StaffEnvironment.get(p.serverLevel()).busy()){
                var level=p.serverLevel();check(level.getBlockState(OVERLAP).is(Blocks.SNOW_BLOCK)&&level.getBlockState(NEW).is(Blocks.SNOW_BLOCK),"Newer worker did not finish its whole area");check(level.getBlockState(OLD).is(Blocks.GRASS_BLOCK),"Newer worker escaped its radius");
                // Resume the old saved job only after its successor completed.
                // It must finish the remaining land without reclaiming the overlap.
                replace(p,list(older.copy()));complete=true;
            }});
            if(complete){complete=false;next(6);}return;
        }
        if(phase==6){
            if(time==3)server(p->{var tag=StaffEnvironment.get(p.serverLevel()).save(new CompoundTag());var before=tag.getList("Jobs",Tag.TAG_COMPOUND).getCompound(0);check(before.getList("ExcludedAreas",Tag.TAG_COMPOUND).size()==1,"Overwrite ownership lost before checkpoint");replace(p,tag.getList("Jobs",Tag.TAG_COMPOUND));var after=rows(p).getCompound(0);check(before.equals(after),"Checkpoint altered cursors, ownership or task identity");checkpoint=true;});
            if(time%20==0&&!complete)server(p->{if(!StaffEnvironment.get(p.serverLevel()).busy()){
                var level=p.serverLevel();check(level.getBlockState(OLD).is(Blocks.SAND),"Old non-overlapping terrain was abandoned");check(level.getBlockState(OVERLAP).is(Blocks.SNOW_BLOCK)&&level.getBlockState(NEW).is(Blocks.SNOW_BLOCK),"Old saved job reverted a newer cast");check(level.getBlockState(OUT).is(Blocks.GRASS_BLOCK),"Terrain conversion escaped both disks");
                check(level.getNoiseBiome(118,16,0).is(Biomes.DESERT),"Old non-overlapping biome did not finish");check(level.getNoiseBiome(133,16,0).is(Biomes.SNOWY_PLAINS)&&level.getNoiseBiome(146,16,0).is(Biomes.SNOWY_PLAINS),"Old biome stage reclaimed newer biome cells");check(level.getNoiseBiome(154,16,0).is(Biomes.PLAINS),"Biome conversion escaped the disks");
                check(level.getBlockState(WALL).is(Blocks.STONE_BRICKS)&&((ChestBlockEntity)level.getBlockEntity(CHEST)).getItem(0).getCount()==7,"Overlap conversion damaged protected structures or inventory");report.addProperty("old_job_resumed_after_new_job_completed",true);report.addProperty("non_overlap_preserved_and_overlap_not_reverted",true);report.addProperty("protected_structures_and_inventory",true);complete=true;
            }});
            if(complete){complete=false;next(7);}return;
        }
        if(phase==7){
            if(time==30){check(mc.level.getNoiseBiome(118,16,0).is(Biomes.DESERT)&&mc.level.getNoiseBiome(133,16,0).is(Biomes.SNOWY_PLAINS),"Client biomes disagree with saved overwrite ownership");capture("02-partial-overlap-after-old-resume.png");report.addProperty("partial_overlap_client_biomes",true);next(8);}return;
        }
        if(phase==8){
            if(time==1)server(p->{
                ListTag full=new ListTag();for(int i=0;i<4;i++){var row=older.copy();row.putDouble("X",3000.5+i*1500);row.putInt("Version",2);row.remove("Token");row.remove("ExcludedAreas");row.putInt("Cursor",1);row.putInt("ColumnCursor",7);full.add(row);}
                replace(p,full);var migrated=rows(p);check(migrated.size()==4&&migrated.getCompound(0).getInt("Cursor")==1&&migrated.getCompound(0).getInt("ColumnCursor")==7,"Version2 task checkpoint migration failed");report.addProperty("version2_saved_jobs_load_without_resetting_cursors",true);
                position(p,3000.5);check(cast(p,"jungle"),"Covered replacement was refused at four-job capacity");check(rows(p).size()==4,"Covered replacement did not retire its predecessor");var before=rows(p).copy();long energy=MysteryEnergy.getEnergy(p);position(p,10000.5);check(cast(p,"ocean"),"Independent fifth job was refused");check(rows(p).size()==5&&MysteryEnergy.getEnergy(p)==energy-2000,"Fifth queued cast did not consume exactly one fee");for(int i=0;i<4;i++)check(rows(p).getCompound(i+1).equals(before.getCompound(i)),"Queuing a disjoint cast mutated an older task");
                energy=MysteryEnergy.getEnergy(p);position(p,10000.75);check(cast(p,"snowy_plains"),"Sixth partially overlapping cast was refused");check(rows(p).size()==6&&MysteryEnergy.getEnergy(p)==energy-2000,"Sixth queued cast accounting failed");check(rows(p).getCompound(1).getList("ExcludedAreas",Tag.TAG_COMPOUND).size()==1,"Queue lost newer-cast ownership");var snapshot=rows(p).copy();replace(p,snapshot);check(rows(p).equals(snapshot),"Queue order or cursors changed after reload");report.addProperty("fifth_and_sixth_casts_admitted_and_paid",true);report.addProperty("saved_queue_keeps_order_ownership_and_cursors",true);
                // Small real jobs exercise promotion and completion, rather than
                // claiming six complete 1000-block conversions were repeated.
                ListTag resume=new ListTag();for(int i=0;i<6;i++){var row=older.copy();row.putUUID("Token",UUID.randomUUID());row.putDouble("X",900.5+i*32);row.putDouble("Z",.5);row.putInt("Radius",4);row.putInt("Stage",0);row.putInt("Cursor",0);row.putInt("ColumnCursor",0);row.putInt("Version",3);row.put("ExcludedAreas",new ListTag());row.putString("Biome",i==5?"minecraft:snowy_plains":"minecraft:desert");p.serverLevel().getChunkAt(new BlockPos(900+i*32,64,0));resume.add(row);}replace(p,resume);report.addProperty("queue_completion_worker_radius",4);complete=true;
            });
            if(complete){complete=false;next(9);}return;
        }
        if(phase==9){
            if(time%10==0&&!complete)server(p->{try{var field=StaffEnvironment.class.getDeclaredField("jobs");field.setAccessible(true);var jobs=(List<?>)field.get(StaffEnvironment.get(p.serverLevel()));int active=0;for(var job:jobs){var ticket=job.getClass().getDeclaredField("ticket");ticket.setAccessible(true);if(ticket.get(job)!=null)active++;}check(active<=4,"Queued tasks held more than four chunk tickets");}catch(ReflectiveOperationException error){throw new RuntimeException(error);}if(!StaffEnvironment.get(p.serverLevel()).busy()){check(p.serverLevel().getBlockState(new BlockPos(900,64,0)).is(Blocks.SAND),"First active job did not finish");check(p.serverLevel().getBlockState(new BlockPos(1060,64,0)).is(Blocks.SNOW_BLOCK),"Queued sixth job was never promoted or completed");report.addProperty("bounded_tickets_and_queued_job_eventually_completes",true);complete=true;}});
            if(complete){report.addProperty("checkpoint_keeps_exclusions_and_cursors",checkpoint);report.addProperty("master_volume",mc.options.getSoundSourceVolume(SoundSource.MASTER));report.addProperty("production_radius",StaffEnvironment.RADIUS);try{Files.writeString(mc.gameDirectory.toPath().resolve("environment-overwrite-verification.json"),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(report));}catch(Exception error){throw new RuntimeException(error);}SourceOfMystery.LOGGER.info("STAFF_ENVIRONMENT_OVERWRITE_OK");mc.stop();}
        }
    }
}
