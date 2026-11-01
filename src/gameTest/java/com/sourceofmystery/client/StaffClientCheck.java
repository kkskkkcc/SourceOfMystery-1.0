package com.sourceofmystery.client;

import com.sourceofmystery.SourceOfMystery;
import com.sourceofmystery.client.staff.*;
import com.sourceofmystery.energy.MysteryEnergy;
import com.sourceofmystery.item.ModItems;
import com.sourceofmystery.network.*;
import com.sourceofmystery.staff.*;
import net.minecraft.client.*;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.nio.file.Files;
import java.util.function.Consumer;

/** Isolated client smoke run: creates its own world and captures actual rendered frames. */
@Mod.EventBusSubscriber(modid="sourceofmystery",value=Dist.CLIENT)
public final class StaffClientCheck {
    private static boolean opened,started;
    private static int ticks;
    private static volatile boolean ready;
    private static void server(Consumer<ServerPlayer> work) {
        var mc=Minecraft.getInstance();mc.getSingleplayerServer().execute(()->work.accept(mc.getSingleplayerServer().getPlayerList().getPlayers().get(0)));
    }
    private static void select(StaffSpell spell) {
        var mc=Minecraft.getInstance();spell.select(mc.player.getMainHandItem());
        ModNetwork.CHANNEL.sendToServer(new StaffSelectionPacket(InteractionHand.MAIN_HAND,spell.ordinal()));
    }
    private static void use() {var mc=Minecraft.getInstance();mc.options.keyUse.setDown(true);mc.gameMode.useItem(mc.player,InteractionHand.MAIN_HAND);}
    private static void release() {var mc=Minecraft.getInstance();mc.options.keyUse.setDown(false);mc.gameMode.releaseUsingItem(mc.player);}
    private static void capture(String name) {
        var mc=Minecraft.getInstance();Screenshot.grab(mc.gameDirectory,name,mc.getMainRenderTarget(),message->SourceOfMystery.LOGGER.info("STAFF_FRAME {} {}",name,message.getString()));
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if(!Boolean.getBoolean("sourceofmystery.staffCheck")||event.phase!=TickEvent.Phase.END)return;
        var mc=Minecraft.getInstance();
        try {
            if(!opened&&mc.screen instanceof TitleScreen) {
                opened=true;mc.options.pauseOnLostFocus=false;mc.options.renderDistance().set(8);mc.options.guiScale().set(2);
                String name="staff-check-"+System.currentTimeMillis();
                mc.createWorldOpenFlows().createFreshLevel(name,new LevelSettings(name,GameType.CREATIVE,false,Difficulty.NORMAL,true,new GameRules(),WorldDataConfiguration.DEFAULT),
                        new WorldOptions(1024,false,false),registries->registries.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions());
                return;
            }
            if(mc.player==null||mc.level==null||mc.getSingleplayerServer()==null)return;
            if(!started) {
                started=true;server(player->{
                    var level=player.serverLevel();level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,player.server);
                    level.setDayTime(6000);level.setWeatherParameters(100000,0,false,false);
                    for(int x=-32;x<=32;x++)for(int z=-32;z<=32;z++)level.setBlock(new BlockPos(x,199,z),((x+z)%3==0?Blocks.GRASS_BLOCK:Blocks.STONE_BRICKS).defaultBlockState(),18);
                    for(int x=-5;x<=5;x++)level.setBlock(new BlockPos(x,200,12),(x%2==0?Blocks.RED_WOOL:Blocks.LIME_WOOL).defaultBlockState(),18);
                    player.teleportTo(level,0,200,0,0,0);player.setNoGravity(true);player.getAbilities().flying=true;player.onUpdateAbilities();
                    player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(ModItems.DIMENSION_BREAKER_STAFF.get()));
                    player.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.NIGHT_VISION,2400,0,false,false));
                    MysteryEnergy.setEnergyAndMax(player,50000);ready=true;
                });return;
            }
            if(!ready)return;ticks++;
            if(ticks==60)capture("01-staff-first-person.png");
            if(ticks==70)mc.setScreen(new StaffWheelScreen(InteractionHand.MAIN_HAND));
            if(ticks==80)capture("02-spell-wheel.png");
            if(ticks==90){mc.setScreen(null);select(StaffSpell.ANNIHILATION);}
            if(ticks==95){use();mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);}
            if(ticks==130)capture("03-charge-third-person.png");
            if(ticks==150)mc.options.setCameraType(CameraType.FIRST_PERSON);
            if(ticks==170)capture("04-charge-first-person.png");
            if(ticks==330)capture("05-annihilation-ritual.png");
            if(ticks==340){release();select(StaffSpell.SPACE_RIFT);server(p->p.serverLevel().setBlockAndUpdate(new BlockPos(0,201,4),Blocks.END_STONE.defaultBlockState()));}
            if(ticks==345){use();mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);}
            if(ticks==357)capture("06-rift-cast-pose.png");
            if(ticks==370)mc.options.setCameraType(CameraType.FIRST_PERSON);
            if(ticks==372)release();
            if(ticks==390)capture("07-spatial-rift.png");
            if(ticks==400){select(StaffSpell.EARTH_SPLIT);server(p->{var cow=EntityType.COW.create(p.serverLevel());cow.setPos(0,210,50);cow.setNoGravity(true);p.serverLevel().addFreshEntity(cow);});}
            if(ticks==405){use();mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);}
            if(ticks==440)capture("08-earth-split-pose.png");
            if(ticks==470){release();mc.options.setCameraType(CameraType.FIRST_PERSON);server(p->p.teleportTo(p.serverLevel(),0,235,-90,0,-13));}
            if(ticks==570)capture("09-earth-split-sword.png");
            if(ticks==610){select(StaffSpell.EARTH_LIFT);server(p->p.teleportTo(p.serverLevel(),-24,200,0,0,0));}
            if(ticks==615){use();mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);}
            if(ticks==655)capture("10-earth-lift-pose.png");
            if(ticks==680){release();mc.options.setCameraType(CameraType.FIRST_PERSON);server(p->p.teleportTo(p.serverLevel(),-24,220,-15,0,-20));}
            if(ticks==730)capture("11-earth-lift.png");
            if(ticks==800){select(StaffSpell.TIME_STOP);server(p->p.teleportTo(p.serverLevel(),-24,200,-12,0,0));}
            if(ticks==805)use();
            if(ticks==806)release();
            if(ticks==815)capture("12-time-distortion.png");
            if(ticks==850){if(!StaffVisuals.timeStopped())throw new AssertionError("Client did not receive time stop");capture("13-time-grayscale.png");}
            if(ticks==1060){if(StaffVisuals.timeStopped())throw new AssertionError("Client did not resume time");capture("14-time-restored.png");}
            if(ticks==1100){Files.writeString(mc.gameDirectory.toPath().resolve("staff-client-ok.txt"),"STAFF_CLIENT_OK frames=14\n");SourceOfMystery.LOGGER.info("STAFF_CLIENT_OK frames=14");mc.stop();}
        }catch(Throwable failure){failure.printStackTrace();System.exit(1);}
    }
}
