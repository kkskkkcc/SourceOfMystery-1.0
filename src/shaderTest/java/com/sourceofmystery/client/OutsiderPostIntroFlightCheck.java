package com.sourceofmystery.client;

import com.sourceofmystery.SourceOfMystery;
import com.sourceofmystery.entity.OutsiderEntity;
import com.sourceofmystery.item.ModItems;
import com.sourceofmystery.ritual.OfferingPortalRitual;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.*;
import java.lang.Math;

/** Observe the same actual AI-controlled boss for 34 seconds AFTER its full summon. */
@Mod.EventBusSubscriber(modid="sourceofmystery",value=Dist.CLIENT)
public final class OutsiderPostIntroFlightCheck {
    private static int ticks,frames,airFrames,shots;
    private static volatile boolean ready;
    private static boolean sawIntro,finished;
    private static float start=-1,previousTime=-1,lastYaw;
    private static Vec3 lastPosition;
    private static int bossId=-1;
    private static OfferingMovieCapture movie;
    private static Vector3f[] previous;
    private static final java.util.List<Double> rates=new java.util.ArrayList<>();
    private static final java.util.Map<Integer,Integer> states=new java.util.TreeMap<>();
    private static double hairAngle,clothAngle,hairShift,clothShift,maximumRate,travel,turn;
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event){
        if(!Boolean.getBoolean("sourceofmystery.postIntroFlightCheck")||finished||event.phase!=TickEvent.Phase.END)return;
        var mc=Minecraft.getInstance();if(mc.player==null||mc.level==null||mc.getSingleplayerServer()==null)return;
        if(ticks++==0)mc.getSingleplayerServer().execute(()->{
            var level=mc.getSingleplayerServer().overworld();
            level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,level.getServer());
            level.setDayTime(10000);level.setWeatherParameters(100000,0,false,false);
            for(var e:com.google.common.collect.Lists.newArrayList(level.getAllEntities()))if(!(e instanceof net.minecraft.server.level.ServerPlayer))e.discard();
            for(int x=-30;x<=30;x++)for(int z=-30;z<=30;z++)level.setBlock(new BlockPos(x,199,z),Blocks.STONE_BRICKS.defaultBlockState(),2);
            for(int x=-10;x<=10;x++)for(int z=-10;z<=12;z++)for(int y=200;y<212;y++)level.setBlock(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState(),2);
            for(int u=0;u<4;u++)for(int v=0;v<5;v++)if(u==0||u==3||v==0||v==4)level.setBlock(new BlockPos(u-2,v+200,0),Blocks.OBSIDIAN.defaultBlockState(),2);
            var p=level.getServer().getPlayerList().getPlayers().get(0);p.setGameMode(GameType.CREATIVE);p.teleportTo(level,0,200,7,180,60);ready=true;
        });
        if(ready&&ticks==120)mc.getSingleplayerServer().execute(()->{
            var level=mc.getSingleplayerServer().overworld();var p=level.getServer().getPlayerList().getPlayers().get(0);
            if(!OfferingPortalRitual.tryStart(level,new BlockPos(-2,200,0),p,InteractionHand.MAIN_HAND,new ItemStack(ModItems.DARK_DIMENSIONAL_OFFERING.get())))throw new AssertionError("Summon failed");
            for(var e:level.getAllEntities())if(e instanceof OutsiderEntity boss)boss.getRandom().setSeed(8125);
        });
        if(ticks>2500)throw new AssertionError("Post-intro flight timeout");
    }
    @SubscribeEvent public static void capture(TickEvent.RenderTickEvent event){
        if(!Boolean.getBoolean("sourceofmystery.postIntroFlightCheck")||finished||event.phase!=TickEvent.Phase.END)return;
        var mc=Minecraft.getInstance();if(mc.level==null||mc.player==null)return;
        if(OfferingIntroDirector.active()){
            sawIntro=true;
            for(var e:mc.level.entitiesForRendering())if(e instanceof OutsiderEntity boss)bossId=boss.getId();
            return;
        }
        if(!sawIntro||!(mc.level.getEntity(bossId) instanceof OutsiderEntity boss)||boss.isOfferingIntro())return;
        float age=boss.tickCount+mc.getFrameTime();
        if(start<0){
            start=age;lastPosition=boss.getPosition(mc.getFrameTime());lastYaw=boss.visualBodyYaw(mc.getFrameTime());
            mc.options.fov().set(50);mc.options.hideGui=true;
            mc.getSingleplayerServer().execute(()->mc.getSingleplayerServer().getPlayerList().getPlayers().get(0).setGameMode(GameType.SPECTATOR));
        }
        float time=(age-start)/20;
        if(time>=34){
            finished=true;if(movie!=null)movie.finish();
            if(airFrames<120||travel<2||turn<10)throw new AssertionError("Did not observe genuine roaming: "+airFrames+", "+travel+", "+turn);
            try{
                java.util.Collections.sort(rates);var report=new java.util.LinkedHashMap<String,Object>();
                report.put("actual_intro_completed",sawIntro);report.put("same_boss_entity",bossId);report.put("post_intro_seconds",34);
                report.put("frames",frames);report.put("air_frames",airFrames);report.put("natural_travel_blocks",travel);report.put("natural_turn_degrees",turn);report.put("states",states);
                report.put("hair_degrees",hairAngle);report.put("cloth_degrees",clothAngle);report.put("hair_displacement_blocks",hairShift);report.put("cloth_displacement_blocks",clothShift);
                report.put("maximum_secondary_degrees_per_second",maximumRate);report.put("p95_secondary_degrees_per_second",rates.get((int)(rates.size()*.95)));report.put("shader_enabled",OutsiderShaderCompat.active());
                java.nio.file.Files.writeString(mc.gameDirectory.toPath().resolve("post-flight-physics.json"),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(report));
                SourceOfMystery.LOGGER.info("POST_INTRO_FLIGHT_OK {}",report);
            }catch(java.io.IOException ex){throw new RuntimeException(ex);}
            mc.stop();return;
        }
        Vec3 body=boss.getPosition(mc.getFrameTime()),p=body.add(3.4,2.05,4.7),look=body.add(0,1.7,0).subtract(p);
        mc.player.setPos(p);mc.player.xo=mc.player.xOld=p.x;mc.player.yo=mc.player.yOld=p.y;mc.player.zo=mc.player.zOld=p.z;
        float yaw=(float)Math.toDegrees(Math.atan2(look.z,look.x))-90,pitch=(float)-Math.toDegrees(Math.atan2(look.y,look.horizontalDistance()));
        mc.player.setYRot(yaw);mc.player.yRotO=yaw;mc.player.setXRot(pitch);mc.player.xRotO=pitch;
        travel+=body.distanceTo(lastPosition);lastPosition=body;float bodyYaw=boss.visualBodyYaw(mc.getFrameTime());turn+=Math.abs(Mth.wrapDegrees(bodyYaw-lastYaw));lastYaw=bodyYaw;
        states.merge(boss.combatState(),1,Integer::sum);
        if(Boolean.getBoolean("sourceofmystery.offeringMovie")){
            if(movie==null)try{movie=new OfferingMovieCapture();}catch(java.io.IOException ex){throw new RuntimeException(ex);}
            movie.capture(time);
        }
        if(time>=shots*5){Screenshot.grab(mc.gameDirectory,String.format("post-flight-%02d.png",shots++),mc.getMainRenderTarget(),m->{});}
        if(boss.combatState()==OutsiderEntity.AIR){measure(boss,time);airFrames++;}else previousTime=-1;
        frames++;int error=org.lwjgl.opengl.GL11.glGetError();if(error!=0)throw new AssertionError("Post-flight GL "+error);
    }
    private static void measure(OutsiderEntity boss,float time){
        try{
            var renderer=Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(boss);
            var f=OutsiderRenderer.class.getDeclaredField("data");f.setAccessible(true);var mesh=f.get(renderer);
            f=mesh.getClass().getDeclaredField("world");f.setAccessible(true);var world=(Matrix4f[])f.get(mesh);
            f=mesh.getClass().getDeclaredField("authoredWorld");f.setAccessible(true);var authored=(Matrix4f[])f.get(mesh);
            f=mesh.getClass().getDeclaredField("springJoints");f.setAccessible(true);var joints=(OutsiderDynamics.Joint[])f.get(mesh);
            if(previous==null){previous=new Vector3f[world.length];for(int i=0;i<world.length;i++)previous[i]=new Vector3f();}
            double dt=time-previousTime;
            for(var joint:joints)if(joint!=null&&joint.category()>0){
                Vector3f a=world[joint.bone()].transformDirection(joint.tail(),new Vector3f()).normalize(),b=authored[joint.bone()].transformDirection(joint.tail(),new Vector3f()).normalize();
                double angle=Math.toDegrees(Math.acos(Math.max(-1,Math.min(1,a.dot(b)))));
                double shift=world[joint.bone()].getTranslation(new Vector3f()).distance(authored[joint.bone()].getTranslation(new Vector3f()))*OutsiderEntity.MODEL_SCALE;
                if(!Double.isFinite(angle)||shift>2)throw new AssertionError("Escaped normal-flight rig");
                if(joint.category()==1){hairAngle=Math.max(hairAngle,angle);hairShift=Math.max(hairShift,shift);}else{clothAngle=Math.max(clothAngle,angle);clothShift=Math.max(clothShift,shift);}
                Vector3f relative=authored[joint.bone()].getUnnormalizedRotation(new Quaternionf()).conjugate().transform(a).normalize();
                if(previousTime>0&&dt>.001&&dt<.2&&time>1){
                    double rate=Math.toDegrees(Math.atan2(new Vector3f(relative).cross(previous[joint.bone()]).length(),relative.dot(previous[joint.bone()])))/dt;
                    rates.add(rate);maximumRate=Math.max(maximumRate,rate);
                }previous[joint.bone()].set(relative);
            }previousTime=time;
        }catch(ReflectiveOperationException ex){throw new RuntimeException(ex);}
    }
}
