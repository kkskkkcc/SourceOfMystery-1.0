package com.sourceofmystery.client;

import com.sourceofmystery.SourceOfMystery;
import com.sourceofmystery.energy.MysteryEnergy;
import com.sourceofmystery.item.ModItems;
import com.sourceofmystery.staff.*;
import net.minecraft.client.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.nio.file.Files;

/** Short native visual regression; the independent full-size test owns terraforming acceptance. */
@Mod.EventBusSubscriber(modid="sourceofmystery",value=Dist.CLIENT)
public final class StaffEnvironmentVisualCheck {
    private static int warmup,frame,scene;
    private static OfferingMovieCapture movie;
    private static volatile boolean ready;
    private static volatile Throwable failure;
    private static final String[] THEMES={"desert","ocean","snowy_plains","jungle","nether_wastes","the_end"};
    private static void server(java.util.function.Consumer<net.minecraft.server.level.ServerPlayer> action){
        var mc=Minecraft.getInstance();mc.getSingleplayerServer().execute(()->{
            try{action.accept(mc.getSingleplayerServer().getPlayerList().getPlayers().get(0));}catch(Throwable e){failure=e;}
        });
    }
    private static void capture(String suffix){
        var mc=Minecraft.getInstance();Screenshot.grab(mc.gameDirectory,String.format("%02d-%s-%s.png",scene+1,THEMES[scene],suffix),mc.getMainRenderTarget(),m->{});
        if(suffix.startsWith("third")&&mc.gameRenderer.getMainCamera().getPosition().distanceTo(mc.player.getEyePosition())<2.5)throw new AssertionError("Review camera obstructed");
        if(Math.abs(mc.player.getY()-65)>.2)throw new AssertionError("Review actor moved off stage: "+mc.player.position());
        if(org.lwjgl.opengl.GL11.glGetError()!=0)throw new AssertionError("Spell render GL error");
    }
    @SubscribeEvent public static void render(TickEvent.RenderTickEvent event){
        if(!Boolean.getBoolean("sourceofmystery.staffEnvironmentVisualCheck")||!Boolean.getBoolean("sourceofmystery.offeringMovie")
                ||event.phase!=TickEvent.Phase.END||!ready||frame<1||scene>=THEMES.length)return;
        try{if(movie==null)movie=new OfferingMovieCapture(THEMES.length*155*3,System.getProperty("sourceofmystery.offeringFfmpeg"),"staff-environment-silent.mp4");}
        catch(java.io.IOException e){throw new RuntimeException(e);}
        movie.capture((scene*155+frame+Minecraft.getInstance().getFrameTime())/20f);
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event){
        if(!Boolean.getBoolean("sourceofmystery.staffEnvironmentVisualCheck")||event.phase!=TickEvent.Phase.END)return;
        var mc=Minecraft.getInstance();if(mc.player==null||mc.level==null||mc.getSingleplayerServer()==null)return;
        if(failure!=null)throw new AssertionError("Visual fixture server action",failure);
        if(warmup++<80)return;
        mc.mouseHandler.releaseMouse();
        if(!ready){
            if(warmup!=81)return;
            mc.options.getSoundSourceOptionInstance(SoundSource.MASTER).set(0d);mc.options.pauseOnLostFocus=false;mc.setScreen(null);
            server(p->{
                p.serverLevel().getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,p.server);
                p.serverLevel().setDayTime(12500);p.serverLevel().setWeatherParameters(100000,0,false,false);
                p.setGameMode(GameType.CREATIVE);p.getInventory().clearContent();p.teleportTo(p.serverLevel(),.5,65,.5,0,-12);
                p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(ModItems.DIMENSION_BREAKER_STAFF.get()));
                MysteryEnergy.setEnergyAndMax(p,50000);MysteryEnergy.setEnergy(p,50000);p.getAbilities().flying=true;p.onUpdateAbilities();ready=true;
            });return;
        }
        // Keep native input from moving the isolated review actor/camera between captures.
        mc.player.getAbilities().flying=true;mc.player.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
        mc.player.setYRot(0);mc.player.yRotO=0;
        for(var key:new net.minecraft.client.KeyMapping[]{mc.options.keyUp,mc.options.keyDown,mc.options.keyLeft,mc.options.keyRight,mc.options.keyJump,mc.options.keyShift})key.setDown(false);
        frame++;
        if(frame==5){
            mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);mc.player.setXRot(-12);mc.player.xRotO=-12;
            server(p->{p.teleportTo(p.serverLevel(),.5,65,.5,0,-12);p.getAbilities().flying=true;p.onUpdateAbilities();StaffSpell.ENVIRONMENT_SHIFT.select(p.getMainHandItem());p.getMainHandItem().getOrCreateTag().putString(StaffEnvironment.BIOME_KEY,"minecraft:"+THEMES[scene]);p.inventoryMenu.broadcastChanges();});
        }
        if(frame==25){mc.options.keyUse.setDown(true);mc.gameMode.useItem(mc.player,InteractionHand.MAIN_HAND);}
        if(frame==52)capture("third-open");
        if(frame==58){mc.options.setCameraType(CameraType.FIRST_PERSON);mc.player.setXRot(7);mc.player.xRotO=7;}
        if(frame==81)capture("first-channel");
        if(frame==94){mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);mc.player.setXRot(-12);mc.player.xRotO=-12;}
        if(frame==100)capture("third-gather");
        if(frame==113){mc.options.keyUse.setDown(false);mc.gameMode.releaseUsingItem(mc.player);
            // Keep the real successful release and network FX, then discard only this isolated job.
            server(p->{var data=StaffEnvironment.get(p.serverLevel());if(!data.busy())throw new AssertionError("No native spell release");
                data.releaseTickets(p.serverLevel());p.serverLevel().getDataStorage().set("sourceofmystery_staff_environment",StaffEnvironment.load(new CompoundTag()));});
        }
        if(frame==117)capture("release");
        if(frame==155){
            scene++;frame=0;
            if(scene==THEMES.length){
                var report=new com.google.gson.JsonObject();report.addProperty("native_client",true);report.addProperty("theme_count",THEMES.length);
                report.addProperty("first_and_third_person",true);report.addProperty("native_charge_and_release",true);
                report.addProperty("master_volume",mc.options.getSoundSourceVolume(SoundSource.MASTER));report.addProperty("shader_enabled",OutsiderShaderCompat.active());
                try{Files.writeString(mc.gameDirectory.toPath().resolve("environment-visual-verification.json"),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(report));}catch(Exception e){throw new RuntimeException(e);}
                if(movie!=null)movie.finish();
                SourceOfMystery.LOGGER.info("STAFF_ENVIRONMENT_VISUAL_OK");mc.stop();
            }
        }
    }
}
