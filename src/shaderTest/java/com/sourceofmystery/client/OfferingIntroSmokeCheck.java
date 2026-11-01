package com.sourceofmystery.client;

import com.sourceofmystery.SourceOfMystery;
import com.sourceofmystery.entity.OutsiderEntity;
import com.sourceofmystery.item.ModItems;
import com.sourceofmystery.ritual.OfferingPortalRitual;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Isolated real Forge capture. Never included in the release JAR. */
@Mod.EventBusSubscriber(modid="sourceofmystery",value=Dist.CLIENT)
public final class OfferingIntroSmokeCheck {
    private static int ticks,lastFrame=-1,finished;
    private static volatile boolean ready;
    private static boolean filmed;
    private static final int CAPTURE_FPS=Integer.getInteger("sourceofmystery.offeringCaptureFps",30);
    private static long previousFrame;
    private static final java.util.List<Double> frameMilliseconds=new java.util.ArrayList<>();
    private static final java.util.List<Double> emergenceMilliseconds=new java.util.ArrayList<>();
    private static OfferingMovieCapture movie;
    private static int contactSheetFrame;
    private static final float[] SHOTS={0,7.8f,15.9f,19.5f,23,25.2f,27.2f,29.7f,31.5f,31.95f,32.05f,32.3f,33};
    private static double returnMinimumEyeHeight=Double.POSITIVE_INFINITY,returnMaximumJump;
    private static net.minecraft.world.phys.Vec3 previousReturnPosition;
    private static int returnFrames;
    private static float chestMaximum,ribcageMinimum=Float.POSITIVE_INFINITY,ribcageMaximum=Float.NEGATIVE_INFINITY;
    private static float clothMaximumAngle,clothMaximumDisplacement,eyeMaximumAngle;
    private static float maximumHairAngle,maximumHairDisplacement;
    private static final java.util.Map<String,Integer> heard=new java.util.concurrent.ConcurrentHashMap<>();
    @SubscribeEvent(priority=net.minecraftforge.eventbus.api.EventPriority.LOWEST) public static void sound(net.minecraftforge.client.event.sound.PlaySoundEvent event){
        if(!Boolean.getBoolean("sourceofmystery.offeringSmoke")||event.getSound()==null)return;
        String name=event.getSound().getLocation().getPath().replace('/','.');
        if(name.startsWith("archmage.")){
            heard.merge(name,1,Integer::sum);
            if(OfferingIntroDirector.active()&&(event.getSound().getAttenuation()!=net.minecraft.client.resources.sounds.SoundInstance.Attenuation.NONE||!event.getSound().isRelative()))throw new AssertionError("Foley still changes with camera distance: "+name);
        }
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event){
        if(!Boolean.getBoolean("sourceofmystery.offeringSmoke")||event.phase!=TickEvent.Phase.END)return;
        var mc=Minecraft.getInstance();if(mc.player==null||mc.level==null||mc.getSingleplayerServer()==null)return;
        if(ticks++==0)mc.getSingleplayerServer().execute(()->{
            try{
                var level=mc.getSingleplayerServer().overworld();
                level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,level.getServer());
                level.setDayTime(10000);level.setWeatherParameters(100000,0,false,false);
                for(var e:com.google.common.collect.Lists.newArrayList(level.getAllEntities()))if(!(e instanceof net.minecraft.server.level.ServerPlayer))e.discard();
                for(int x=-22;x<=22;x++)for(int z=-22;z<=22;z++)level.setBlock(new BlockPos(x,199,z),Blocks.STONE_BRICKS.defaultBlockState(),2);
                for(int x=-10;x<=10;x++)for(int z=-10;z<=12;z++)for(int y=200;y<212;y++)level.setBlock(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState(),2);
                boolean axis=Boolean.getBoolean("sourceofmystery.offeringAxis");
                for(int u=0;u<4;u++)for(int v=0;v<5;v++)if(u==0||u==3||v==0||v==4)level.setBlock(axis?new BlockPos(0,v+200,u-2):new BlockPos(u-2,v+200,0),Blocks.OBSIDIAN.defaultBlockState(),2);
                var p=level.getServer().getPlayerList().getPlayers().get(0);p.setGameMode(GameType.CREATIVE);p.getAbilities().flying=false;p.onUpdateAbilities();p.teleportTo(level,axis?-7:0,200,axis?0:7,axis?-90:180,60);
                ready=true;
            }catch(Throwable e){e.printStackTrace();System.exit(1);}
        });
        if(ready&&ticks==120)mc.getSingleplayerServer().execute(()->{
            var level=mc.getSingleplayerServer().overworld();var p=level.getServer().getPlayerList().getPlayers().get(0);
            if(!OfferingPortalRitual.tryStart(level,Boolean.getBoolean("sourceofmystery.offeringAxis")?new BlockPos(0,200,-2):new BlockPos(-2,200,0),p,InteractionHand.MAIN_HAND,new ItemStack(ModItems.DARK_DIMENSIONAL_OFFERING.get())))throw new AssertionError("Portal did not start");
        });
        // Reproduce mouse movement during the last shot, in addition to starting
        // with the downward aim used to click the bottom obsidian block.
        if(OfferingIntroDirector.active()&&OfferingIntroDirector.seconds()>31){mc.player.setXRot(82);mc.player.xRotO=82;}
        if(filmed&&!OfferingIntroDirector.active()&&++finished>45){
            if(movie!=null){movie.finish();movie=null;}
            if(mc.getCameraEntity()!=mc.player||mc.options.hideGui)throw new AssertionError("Camera/HUD did not restore");
            if(heard.getOrDefault("archmage.heel_step",0)!=4||heard.getOrDefault("archmage.finger_snap",0)!=1)throw new AssertionError("Missing or duplicate Foley cues: "+heard);
            for(String cue:new String[]{"void_wake","void_break","void_seal"})if(heard.getOrDefault("archmage."+cue,0)!=1)throw new AssertionError("Missing or duplicate portal cue: "+heard);
            SourceOfMystery.LOGGER.info("OFFERING_PHYSICS_OK hair_degrees={} displacement_blocks={} heard={}",maximumHairAngle,maximumHairDisplacement,heard);
            if(chestMaximum<.001f||chestMaximum>.030f)throw new AssertionError("Chest inertia missing or escaped bound: "+chestMaximum);
            if(ribcageMaximum-ribcageMinimum<.012f)throw new AssertionError("Upper-body breathing not visible in the actual rig");
            if(returnFrames<5||returnMinimumEyeHeight<1.59||returnMaximumJump>.20)throw new AssertionError("Camera dropped during handoff: frames="+returnFrames+" eye="+returnMinimumEyeHeight+" jump="+returnMaximumJump);
            try{
                java.util.Collections.sort(frameMilliseconds);java.util.Collections.sort(emergenceMilliseconds);
                var report=new java.util.LinkedHashMap<String,Object>();
                report.put("capture_fps",CAPTURE_FPS);
                report.put("frames",frameMilliseconds.size());
                report.put("mean_fps",1000/frameMilliseconds.stream().mapToDouble(Double::doubleValue).average().orElse(1));
                report.put("median_frame_ms",frameMilliseconds.get(frameMilliseconds.size()/2));
                report.put("p95_frame_ms",frameMilliseconds.get((int)(frameMilliseconds.size()*.95)));
                report.put("emergence_mean_fps",1000/emergenceMilliseconds.stream().mapToDouble(Double::doubleValue).average().orElse(1));
                report.put("hair_degrees",maximumHairAngle);
                report.put("hair_displacement_blocks",maximumHairDisplacement);
                report.put("chest_displacement_blocks",chestMaximum);
                report.put("ribcage_breathing_range_blocks",ribcageMaximum-ribcageMinimum);
                report.put("cloth_degrees",clothMaximumAngle);
                report.put("cloth_displacement_blocks",clothMaximumDisplacement);
                report.put("eye_relative_degrees",eyeMaximumAngle);
                report.put("heard_audio_cues",heard);
                report.put("return_frames",returnFrames);report.put("return_minimum_eye_height",returnMinimumEyeHeight);report.put("return_maximum_frame_jump",returnMaximumJump);
                java.nio.file.Files.writeString(mc.gameDirectory.toPath().resolve("performance.json"),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(report));
                SourceOfMystery.LOGGER.info("OFFERING_PERFORMANCE {}",report);
            }catch(java.io.IOException error){throw new RuntimeException(error);}
            SourceOfMystery.LOGGER.info("OFFERING_SMOKE_OK frames={} shader_pack_active={}",lastFrame+1,OutsiderShaderCompat.active());mc.stop();
        }
        if(ticks>1500)throw new AssertionError("Cinematic did not finish");
    }
    @SubscribeEvent public static void capture(TickEvent.RenderTickEvent event){
        if(!Boolean.getBoolean("sourceofmystery.offeringSmoke")||event.phase!=TickEvent.Phase.END)return;
        boolean active=OfferingIntroDirector.active();
        if(!active&&(!filmed||OfferingIntroDirector.seconds()>=34))return;
        var mc=Minecraft.getInstance();float seconds=OfferingIntroDirector.seconds();
        if(seconds>=31.95f&&seconds<32.5f){
            var position=mc.gameRenderer.getMainCamera().getPosition();
            if(previousReturnPosition!=null)returnMaximumJump=Math.max(returnMaximumJump,position.distanceTo(previousReturnPosition));
            previousReturnPosition=position;
            if(!active){returnFrames++;returnMinimumEyeHeight=Math.min(returnMinimumEyeHeight,position.y-mc.player.getY());
                if(mc.gameRenderer.getMainCamera().getXRot()>0)throw new AssertionError("Handoff restored downward click aim");}
        }
        long now=System.nanoTime();
        if(previousFrame>0&&seconds>3&&active){
            double milliseconds=(now-previousFrame)/1e6;frameMilliseconds.add(milliseconds);
            if(seconds>=14&&seconds<22)emergenceMilliseconds.add(milliseconds);
        }previousFrame=now;filmed=true;
        if(CAPTURE_FPS==0){lastFrame++;if(lastFrame%60==0)checkPhysics();if(seconds>29.6f&&seconds<30)checkExpression();return;}
        if(Boolean.getBoolean("sourceofmystery.offeringMovie")){
            if(movie==null)try{movie=new OfferingMovieCapture();}catch(java.io.IOException error){throw new RuntimeException(error);}
            movie.capture(seconds);lastFrame++;
            if(lastFrame%60==0)checkPhysics();
            if(seconds>29.6f&&seconds<30)checkExpression();
            if(contactSheetFrame<SHOTS.length&&seconds>=SHOTS[contactSheetFrame]){
                Screenshot.grab(mc.gameDirectory,String.format("shot-%02d.png",contactSheetFrame++),mc.getMainRenderTarget(),m->{});
            }
            int error=GLCheck();if(error!=0)throw new AssertionError("Movie OpenGL error "+error);return;
        }
        int frame=(int)(seconds*CAPTURE_FPS);
        if(frame<=lastFrame)return;lastFrame=frame;filmed=true;
        if(seconds>=14&&frame%CAPTURE_FPS==0)checkPhysics();
        if(seconds>29.6f&&seconds<30)checkExpression();
        Screenshot.grab(mc.gameDirectory,String.format("intro-%04d.png",frame),mc.getMainRenderTarget(),m->{});
        int error=org.lwjgl.opengl.GL11.glGetError();if(error!=0)throw new AssertionError("OpenGL error "+error);
    }
    private static int GLCheck(){return org.lwjgl.opengl.GL11.glGetError();}
    private static void checkExpression(){
        try{
            var mc=Minecraft.getInstance();
            for(var entity:mc.level.entitiesForRendering())if(entity instanceof OutsiderEntity boss&&boss.isOfferingIntro()){
                var renderer=mc.getEntityRenderDispatcher().getRenderer(boss);
                var field=OutsiderRenderer.class.getDeclaredField("data");field.setAccessible(true);var mesh=field.get(renderer);
                field=mesh.getClass().getDeclaredField("faceWeights");field.setAccessible(true);float[] w=(float[])field.get(mesh);
                if(w[6]<.65f||w[1]>.01f||w[3]<.5f||w[11]<.4f)throw new AssertionError("Final mouth/brow acting missing: "+java.util.Arrays.toString(w));
            }
        }catch(ReflectiveOperationException error){throw new RuntimeException(error);}
    }
    private static void checkPhysics(){
        try{
            var mc=Minecraft.getInstance();OutsiderEntity boss=null;
            for(var e:mc.level.entitiesForRendering())if(e instanceof OutsiderEntity o&&o.isOfferingIntro()){boss=o;break;}
            if(boss==null)return;
            var renderer=mc.getEntityRenderDispatcher().getRenderer(boss);
            var f=OutsiderRenderer.class.getDeclaredField("data");f.setAccessible(true);Object mesh=f.get(renderer);
            f=mesh.getClass().getDeclaredField("world");f.setAccessible(true);var world=(org.joml.Matrix4f[])f.get(mesh);
            f=mesh.getClass().getDeclaredField("authoredWorld");f.setAccessible(true);var authored=(org.joml.Matrix4f[])f.get(mesh);
            f=mesh.getClass().getDeclaredField("springJoints");f.setAccessible(true);var joints=(OutsiderDynamics.Joint[])f.get(mesh);
            f=mesh.getClass().getDeclaredField("blends");f.setAccessible(true);var state=((java.util.Map<?,?>)f.get(mesh)).get(boss);
            f=state.getClass().getDeclaredField("dynamics");f.setAccessible(true);var dynamics=(OutsiderDynamics)f.get(state);
            chestMaximum=Math.max(chestMaximum,dynamics.chestDisplacement());
            if(OfferingIntroDirector.seconds()>=22){
                float y=authored[8].m31()*OutsiderEntity.MODEL_SCALE;
                ribcageMinimum=Math.min(ribcageMinimum,y);ribcageMaximum=Math.max(ribcageMaximum,y);
            }
            if(!world[11].equals(authored[11],.0001f))throw new AssertionError("Physics distorted the head transform");
            for(int eye:new int[]{12,13}){
                var local=new org.joml.Matrix4f(authored[11]).invert().mul(authored[eye]);
                var rotation=local.getUnnormalizedRotation(new org.joml.Quaternionf()).normalize();
                float angle=(float)Math.toDegrees(2*Math.acos(Math.max(-1,Math.min(1,Math.abs(rotation.w)))));
                if(!Float.isFinite(angle)||angle>3.6f)throw new AssertionError("Iris exceeded its eye socket: "+angle);
                eyeMaximumAngle=Math.max(eyeMaximumAngle,angle);
            }
            for(var joint:joints)if(joint!=null&&joint.category()==2){
                var a=world[joint.bone()].transformDirection(joint.tail(),new org.joml.Vector3f()).normalize();
                var b=authored[joint.bone()].transformDirection(joint.tail(),new org.joml.Vector3f()).normalize();
                float angle=(float)Math.acos(Math.max(-1,Math.min(1,a.dot(b))));
                float displacement=world[joint.bone()].getTranslation(new org.joml.Vector3f()).distance(authored[joint.bone()].getTranslation(new org.joml.Vector3f()))*OutsiderEntity.MODEL_SCALE;
                if(!Float.isFinite(angle)||angle>.035f||displacement>.10f)throw new AssertionError("Clothing escaped its constraint: "+angle+", "+displacement);
                clothMaximumAngle=Math.max(clothMaximumAngle,(float)Math.toDegrees(angle));
                clothMaximumDisplacement=Math.max(clothMaximumDisplacement,displacement);
            }
            for(var joint:joints)if(joint!=null&&joint.category()==1){
                var a=world[joint.bone()].transformDirection(joint.tail(),new org.joml.Vector3f()).normalize();
                var b=authored[joint.bone()].transformDirection(joint.tail(),new org.joml.Vector3f()).normalize();
                float angle=(float)Math.acos(Math.max(-1,Math.min(1,a.dot(b))));
                if(!Float.isFinite(angle)||angle>.058f)throw new AssertionError("Hair escaped cinematic constraint: "+angle);
                maximumHairAngle=Math.max(maximumHairAngle,(float)Math.toDegrees(angle));
                float displacement=world[joint.bone()].getTranslation(new org.joml.Vector3f()).distance(authored[joint.bone()].getTranslation(new org.joml.Vector3f()))*OutsiderEntity.MODEL_SCALE;
                maximumHairDisplacement=Math.max(maximumHairDisplacement,displacement);
            }
        }catch(ReflectiveOperationException error){throw new RuntimeException(error);}
    }
}
