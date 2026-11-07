package com.sourceofmystery.client;

import com.sourceofmystery.SourceOfMystery;
import com.sourceofmystery.item.ModItems;
import net.minecraft.client.*;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.*;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.RegistryObject;
import java.nio.file.Files;
import java.util.List;
import java.util.function.Consumer;

/** Isolated, muted client run: gear in GUI, hands, frames and worn, then the full ascension CG. */
@Mod.EventBusSubscriber(modid="sourceofmystery",value=Dist.CLIENT)
public final class GearClientCheck {
    private static final List<RegistryObject<Item>> CHESTS=List.of(ModItems.MYSTERY_CHESTPLATE,ModItems.SPIRIT_SOURCE_CHESTPLATE,
            ModItems.DARK_SOURCE_CHESTPLATE,ModItems.ORIGIN_DRAGON_CHESTPLATE,ModItems.DIVINE_BLESSING_CHESTPLATE);
    private static final List<RegistryObject<Item>> SWORDS=List.of(ModItems.MYSTERY_SWORD,ModItems.SPIRIT_SOURCE_SWORD,
            ModItems.DARK_SOURCE_SWORD,ModItems.ORIGIN_DRAGON_SWORD,ModItems.DIVINE_PUNISHMENT_SWORD);
    /** Real CG seconds at which frames are captured. */
    private static final float[] CG={2,6,10,14,17,21,25,29,33,37,41,44,47.5f,49.5f,52,55,58,61,63.5f,65.5f,67.5f};
    private static boolean opened,started;
    private static volatile boolean ready;
    private static int ticks,warm,frames,cgStart=-1,cgNext;

    private static void server(Consumer<ServerPlayer> work){
        var mc=Minecraft.getInstance();mc.getSingleplayerServer().execute(()->work.accept(mc.getSingleplayerServer().getPlayerList().getPlayers().get(0)));
    }
    private static void capture(String name){
        var mc=Minecraft.getInstance();frames++;mc.getToasts().clear();
        Screenshot.grab(mc.gameDirectory,name,mc.getMainRenderTarget(),m->SourceOfMystery.LOGGER.info("GEAR_FRAME {} {}",name,m.getString()));
    }
    private static void wear(int set){
        server(p->{p.setItemSlot(EquipmentSlot.CHEST,new ItemStack(CHESTS.get(set).get()));
            p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(SWORDS.get(set).get()));
            p.setItemInHand(InteractionHand.OFF_HAND,ItemStack.EMPTY);
            p.teleportTo(p.serverLevel(),.5,200,.5,180,0);});
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event){
        if(!Boolean.getBoolean("sourceofmystery.gearCheck")||event.phase!=TickEvent.Phase.END)return;
        var mc=Minecraft.getInstance();
        try {
            if(mc.screen instanceof net.minecraft.client.gui.screens.AccessibilityOnboardingScreen){
                mc.options.onboardAccessibility=false;mc.options.save();mc.setScreen(new TitleScreen(true));return;
            }
            if(!opened&&mc.screen instanceof TitleScreen){
                opened=true;mc.options.pauseOnLostFocus=false;mc.options.renderDistance().set(8);mc.options.guiScale().set(3);
                mc.options.getSoundSourceOptionInstance(SoundSource.MASTER).set(0.0);
                mc.options.chatVisibility().set(net.minecraft.world.entity.player.ChatVisiblity.HIDDEN);
                mc.options.tutorialStep=net.minecraft.client.tutorial.TutorialSteps.NONE;
                String name="gear-check-"+System.currentTimeMillis();
                mc.createWorldOpenFlows().createFreshLevel(name,new LevelSettings(name,GameType.CREATIVE,false,Difficulty.PEACEFUL,true,new GameRules(),WorldDataConfiguration.DEFAULT),
                        new WorldOptions(2048,false,false),r->r.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions());
                return;
            }
            if(mc.player==null||mc.level==null||mc.getSingleplayerServer()==null)return;
            if(!started){
                started=true;server(p->{
                    var level=p.serverLevel();level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,p.server);
                    level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false,p.server);
                    level.setDayTime(6000);level.setWeatherParameters(100000,0,false,false);
                    for(int x=-24;x<=24;x++)for(int z=-24;z<=24;z++)level.setBlock(new BlockPos(x,199,z),((x+z)%2==0?Blocks.SMOOTH_STONE:Blocks.POLISHED_ANDESITE).defaultBlockState(),18);
                    // A wall of item frames behind the stand-in position, plus dropped items.
                    for(int x=-5;x<=5;x++)for(int y=200;y<=202;y++)level.setBlock(new BlockPos(x,y,6),Blocks.QUARTZ_BLOCK.defaultBlockState(),18);
                    for(int i=0;i<5;i++){
                        for(int row=0;row<2;row++){
                            var frame=new ItemFrame(level,new BlockPos(-4+i*2,201-row,5),Direction.NORTH);
                            frame.setItem(new ItemStack((row==0?SWORDS:CHESTS).get(i).get()),false);level.addFreshEntity(frame);
                        }
                        var drop=new ItemEntity(level,-4+i*2+.5,200,3.5,new ItemStack(SWORDS.get(i).get()));drop.setNeverPickUp();drop.setUnlimitedLifetime();level.addFreshEntity(drop);
                    }
                    var inv=p.getInventory();
                    for(int i=0;i<9;i++)inv.setItem(i,new ItemStack((i<5?CHESTS.get(i):SWORDS.get(i-5)).get()));
                    p.setItemInHand(InteractionHand.OFF_HAND,new ItemStack(SWORDS.get(4).get()));
                    p.teleportTo(level,.5,200,.5,180,0);ready=true;
                });return;
            }
            if(!ready||warm++<500)return;ticks++;
            if(ticks==60)capture("01-hotbar-first-person.png");
            if(ticks==70){mc.options.setCameraType(CameraType.FIRST_PERSON);server(p->p.teleportTo(p.serverLevel(),.5,200,-1.5,0,8));}
            if(ticks==100)capture("02-item-frames-and-drops.png");
            for(int set=0;set<4;set++){
                int base=120+set*80;
                if(ticks==base){wear(set);mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);}
                if(ticks==base+25)capture(String.format("%02d-worn-front-%d.png",3+set*2,set));
                if(ticks==base+30)mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
                if(ticks==base+50)capture(String.format("%02d-worn-back-%d.png",4+set*2,set));
            }
            // Side view of the grip: armor stands share the humanoid hand transform.
            if(ticks==415){mc.options.setCameraType(CameraType.FIRST_PERSON);server(p->{
                var level=p.serverLevel();p.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);
                float[] raise={0,-40,-90};
                for(int i=0;i<3;i++){
                    var stand=new ArmorStand(level,-1+i*1.5,200,-6.5);stand.setShowArms(true);stand.setNoBasePlate(true);
                    stand.setYRot(270);stand.setYBodyRot(270);stand.setYHeadRot(270);
                    stand.setRightArmPose(new Rotations(raise[i],0,0));stand.addTag("grip_check");
                    stand.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(SWORDS.get(4).get()));level.addFreshEntity(stand);
                }
                p.teleportTo(level,.5,200,-3.4,180,18);});}
            if(ticks==435)capture("14-grip-side.png");
            if(ticks==437)server(p->p.teleportTo(p.serverLevel(),.95,200,-5.3,180,32));
            if(ticks==446)capture("15-grip-close.png");
            if(ticks==448)server(p->p.serverLevel().getEntities(net.minecraft.world.entity.EntityType.ARMOR_STAND,e->e.getTags().contains("grip_check")).forEach(e->e.discard()));
            if(ticks==450){mc.options.setCameraType(CameraType.FIRST_PERSON);server(p->{p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(SWORDS.get(4).get()));
                p.setItemInHand(InteractionHand.OFF_HAND,new ItemStack(SWORDS.get(4).get()));});}
            if(ticks==475)capture("11-first-person-divine-sword.png");
            if(ticks==480){mc.options.setCameraType(CameraType.FIRST_PERSON);
                server(p->{p.setItemSlot(EquipmentSlot.CHEST,new ItemStack(ModItems.DIVINE_BLESSING_CHESTPLATE.get()));p.setItemInHand(InteractionHand.OFF_HAND,ItemStack.EMPTY);p.teleportTo(p.serverLevel(),.5,200,.5,180,0);});}
            if(cgStart<0&&ticks>480&&DivineCGDirector.active())cgStart=ticks;
            if(cgStart>0){
                float real=DivineCGDirector.seconds();
                if(cgNext<CG.length&&real>=CG[cgNext]){capture(String.format("cg-%02d-%04.1fs.png",cgNext,CG[cgNext]));cgNext++;}
            }
            if(cgStart>0&&!DivineCGDirector.active()&&ticks>cgStart+100&&ticks<cgStart+5000){
                cgStart=-(ticks);mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
            }
            if(cgStart<-1&&ticks==-cgStart+40)capture("12-worn-front-divine.png");
            if(cgStart<-1&&ticks==-cgStart+45)mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            if(cgStart<-1&&ticks==-cgStart+70)capture("13-worn-back-divine.png");
            if(cgStart<-1&&ticks==-cgStart+90){
                Files.writeString(mc.gameDirectory.toPath().resolve("gear-client-ok.txt"),"GEAR_CLIENT_OK frames="+frames+"\n");
                SourceOfMystery.LOGGER.info("GEAR_CLIENT_OK frames={}",frames);mc.stop();
            }
            if(ticks>480+20*120)throw new AssertionError("Ascension CG did not start or finish");
        }catch(Throwable failure){failure.printStackTrace();System.exit(1);}
    }
}
