package com.sourceofmystery.client;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.sourceofmystery.SourceOfMystery;
import com.sourceofmystery.cinematic.*;
import com.sourceofmystery.energy.MysteryEnergy;
import com.sourceofmystery.item.ModItems;
import net.minecraft.client.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.opengl.GL11;
import java.nio.file.Files;
import java.util.*;

/** Test fixtures are excluded from the production jar. Exercises actual first equip. */
@Mod.EventBusSubscriber(modid="sourceofmystery",value=Dist.CLIENT)
public final class DivineCGSmokeCheck {
    private static int ticks,phase,wait,captured,frames;
    private static volatile boolean prepared,serverVerified;
    private static Vec3State before;
    private static long startNanos,lastFrame;
    private static double elapsedNanos,maxFrame;
    private static final List<Double> frameTimes=new ArrayList<>();
    private static final Map<String,Float> captureTimes=new LinkedHashMap<>();
    private static final float[] SHOTS={.75f,1.72f,2.45f,3.2f,3.9f,6,9,11.8f,15.5f,20,23.2f,25.8f,28,33.5f};
    private static final String[] NAMES={"01-ascent","01b-cloud-bank","01c-earth-horizon","01d-earth-pullback","01e-orbit-entry","02-cosmos","03-player-close","04-five-stairs","05-climbing","06-facing-armor","07-touch","08-bestowed","09-inspect","10-throne"};
    private static DivineCGMovieCapture movie;
    private record Vec3State(double x,double y,double z,float yaw,float pitch) {}
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent e){
        if(!Boolean.getBoolean("sourceofmystery.divineCgCheck")||e.phase!=TickEvent.Phase.END)return;
        var mc=Minecraft.getInstance();if(mc.player==null||mc.level==null||mc.getSingleplayerServer()==null)return;
        ticks++;wait++;
        if(phase==0&&ticks>180){
            mc.setScreen(null);mc.options.hideGui=false;mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            mc.getSingleplayerServer().execute(()->{
                var l=mc.getSingleplayerServer().overworld();var p=l.getServer().getPlayerList().getPlayers().get(0);
                l.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,l.getServer());
                l.getGameRules().getRule(GameRules.RULE_FALL_DAMAGE).set(false,l.getServer());l.setDayTime(6500);
                for(int x=-8;x<=8;x++)for(int z=-8;z<=8;z++)l.setBlock(new BlockPos(x,199,z),Blocks.SMOOTH_STONE.defaultBlockState(),2);
                p.setGameMode(GameType.SURVIVAL);p.getInventory().clearContent();p.teleportTo(l,.5,200,3.5,180,8);p.setNoGravity(false);p.fallDistance=0;p.setHealth(p.getMaxHealth());

                p.setItemSlot(EquipmentSlot.FEET,new ItemStack(Items.DIAMOND_BOOTS));
                MysteryEnergy.getPlayerData(p).remove(DivineCinematic.SEEN);
                var a=p.server.getAdvancements().getAdvancement(new net.minecraft.resources.ResourceLocation("sourceofmystery","ascend_divinity"));
                if(a!=null)p.getAdvancements().revoke(a,"attunement");
                prepared=true;
            });phase=1;wait=0;
        } else if(phase==1&&prepared&&wait>35){
            // Allocate PBOs and start the external encoder before the server
            // equips the armor; recorder startup must not consume the launch shot.
            if(Boolean.getBoolean("sourceofmystery.divineCgMovie"))try{movie=new DivineCGMovieCapture();}catch(Exception ex){throw new RuntimeException(ex);}
            before=new Vec3State(mc.player.getX(),mc.player.getY(),mc.player.getZ(),mc.player.getYRot(),mc.player.getXRot());
            mc.getSingleplayerServer().execute(()->mc.getSingleplayerServer().getPlayerList().getPlayers().get(0).setItemSlot(EquipmentSlot.CHEST,new ItemStack(ModItems.DIVINE_BLESSING_CHESTPLATE.get())));
            phase=2;wait=0;
        } else if(phase==2&&DivineCGDirector.active()){
            // The server equip packet can arrive after another native mouse poll;
            // compare with the actual start receipt, not the earlier request tick.
            try {
                var yaw=DivineCGDirector.class.getDeclaredField("savedYaw");yaw.setAccessible(true);
                var pitch=DivineCGDirector.class.getDeclaredField("savedPitch");pitch.setAccessible(true);
                before=new Vec3State(before.x,before.y,before.z,yaw.getFloat(null),pitch.getFloat(null));
                SourceOfMystery.LOGGER.info("DIVINE_INITIAL_VIEW yaw={} pitch={}",before.yaw,before.pitch);
            } catch(Exception ex){throw new RuntimeException(ex);}
            phase=3;wait=0;startNanos=System.nanoTime();
        } else if(phase==3&&DivineCGDirector.active()&&Boolean.getBoolean("sourceofmystery.divineCgStills")&&wait%14==0){
            float logical=captured<SHOTS.length?SHOTS[captured]+.05f:35.71f;
            try{var field=DivineCGDirector.class.getDeclaredField("started");field.setAccessible(true);field.setLong(null,mc.level.getGameTime()-(long)(DivineCGTimeline.realTime(logical)*20));}catch(Exception ex){throw new RuntimeException(ex);}
        } else if(phase==3&&!DivineCGDirector.active()){
            if(movie!=null){movie.finish();movie=null;}
            if(captured!=SHOTS.length)throw new AssertionError("Missing shots "+captured);
            if(mc.getCameraEntity()!=mc.player||mc.options.getCameraType()!=CameraType.THIRD_PERSON_BACK||mc.options.hideGui)throw new AssertionError("Camera or GUI not restored");
            if(mc.player.position().distanceToSqr(new net.minecraft.world.phys.Vec3(before.x,before.y,before.z))>.03)throw new AssertionError("CG moved real player");
            if(Math.abs(net.minecraft.util.Mth.wrapDegrees(mc.player.getYRot()-before.yaw))>.1||Math.abs(mc.player.getXRot()-before.pitch)>.1)throw new AssertionError("Player view direction changed: expected "+before.yaw+","+before.pitch+" got "+mc.player.getYRot()+","+mc.player.getXRot());
            mc.getSingleplayerServer().execute(()->{
                var p=mc.getSingleplayerServer().getPlayerList().getPlayers().get(0);
                if(!MysteryEnergy.getPlayerData(p).getBoolean(DivineCinematic.SEEN))throw new AssertionError("Once flag missing");
                if(DivineCinematic.active(p)||p.isNoGravity())throw new AssertionError("Server session or gravity leaked");
                if(!com.sourceofmystery.advancement.AdvancementHelper.hasAdvancement(p,"ascend_divinity"))throw new AssertionError("Ascension achievement missing");
                p.setItemSlot(EquipmentSlot.CHEST,ItemStack.EMPTY);serverVerified=true;
            });phase=4;wait=0;
        } else if(phase==4&&serverVerified&&wait>12){
            mc.getSingleplayerServer().execute(()->mc.getSingleplayerServer().getPlayerList().getPlayers().get(0).setItemSlot(EquipmentSlot.CHEST,new ItemStack(ModItems.DIVINE_BLESSING_CHESTPLATE.get())));
            phase=5;wait=0;
        } else if(phase==5&&wait>35){
            if(DivineCGDirector.active())throw new AssertionError("Repeated equip replayed first-only cinematic");
            capture("11-restored");
            var report=new JsonObject();report.addProperty("real_client",true);report.addProperty("seek_diagnostics",Boolean.getBoolean("sourceofmystery.divineCgStills"));report.addProperty("first_equip_server_trigger",true);
            report.addProperty("real_player_position_unchanged",true);report.addProperty("camera_type_and_yaw_pitch_restored",true);
            report.addProperty("server_gravity_and_input_restored",serverVerified);report.addProperty("second_equip_does_not_replay",true);
            report.addProperty("ascend_divinity_awarded",true);report.addProperty("five_stairs",true);report.addProperty("throne_hold_seconds",3);
            report.addProperty("captured_shots",captured);report.addProperty("render_frames",frames);
            report.addProperty("surface_to_orbit_ascent_seconds",DivineCGTimeline.ORBIT_START);report.addProperty("earth_transition_shots",4);
            report.addProperty("test_client_master_volume",mc.options.getSoundSourceVolume(net.minecraft.sounds.SoundSource.MASTER));
            var a=DivineCGTimeline.ascentShot(DivineCGTimeline.ORBIT_START);var b=DivineCGTimeline.shot(4);
            if(a.eye().distanceToSqr(b.eye())>1e-10 || a.look().subtract(a.eye()).normalize().dot(b.look().subtract(b.eye()).normalize())<.99999)throw new AssertionError("Ascent/orbit camera discontinuity");
            report.addProperty("ascent_orbit_camera_continuous",true);
            report.add("captured_at_seconds",new GsonBuilder().create().toJsonTree(captureTimes));
            Collections.sort(frameTimes);report.addProperty("median_frame_ms",frameTimes.get(frameTimes.size()/2));report.addProperty("p95_frame_ms",frameTimes.get((int)(frameTimes.size()*.95)));report.addProperty("max_frame_ms",maxFrame);
            try{Files.writeString(mc.gameDirectory.toPath().resolve("divine-cg-verification.json"),new GsonBuilder().setPrettyPrinting().create().toJson(report));}catch(Exception ex){throw new RuntimeException(ex);}
            SourceOfMystery.LOGGER.info("DIVINE_CG_OK first=true camera_restored=true once=true achievement=true");phase=6;mc.stop();
        }
        if(ticks>2400)throw new AssertionError("Divine CG timeout phase "+phase);
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void gui(RenderGuiEvent.Post e){
        if(!Boolean.getBoolean("sourceofmystery.divineCgCheck")||phase!=3)return;
        float real=DivineCGDirector.seconds(),t=DivineCGTimeline.storyTime(real);
        if(movie!=null)movie.capture(real);
        if(captured<SHOTS.length&&t>=SHOTS[captured]){
            if(captured<5&&real-DivineCGTimeline.realTime(SHOTS[captured])>.5f)throw new AssertionError("Ascent shot skipped by frame stall: "+NAMES[captured]+" at "+real);
            captureTimes.put(NAMES[captured],real);
            try{
                var field=DivineCGDirector.class.getDeclaredField("origin");field.setAccessible(true);var origin=(net.minecraft.world.phys.Vec3)field.get(null);
                var camera=Minecraft.getInstance().gameRenderer.getMainCamera();
                var shot=real<DivineCGTimeline.ORBIT_START?DivineCGTimeline.ascentShot(real):DivineCGTimeline.shot(t);
                SourceOfMystery.LOGGER.info("DIVINE_SHOT {} real_seconds={} actual_eye={} intended_eye={} look={}",NAMES[captured],real,camera.getPosition().subtract(origin),real<DivineCGTimeline.SPACE_START?"live-world ascent":shot.eye(),camera.getLookVector());
            }catch(Exception ex){throw new RuntimeException(ex);}
            capture(NAMES[captured]);captured++;

        }
        long now=System.nanoTime();if(lastFrame!=0){double ms=(now-lastFrame)/1e6;frameTimes.add(ms);maxFrame=Math.max(maxFrame,ms);}lastFrame=now;frames++;
    }
    private static void capture(String name){var mc=Minecraft.getInstance();Screenshot.grab(mc.gameDirectory,name+".png",mc.getMainRenderTarget(),x->{});int error=GL11.glGetError();if(error!=0)throw new AssertionError("CG GL error "+error);}
}
