package com.sourceofmystery.client;

import com.sourceofmystery.SourceOfMystery;
import com.sourceofmystery.cinematic.*;
import com.sourceofmystery.network.*;
import net.minecraft.client.*;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.*;
import net.minecraft.util.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.*;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.resources.ResourceLocation;
import java.util.*;

@Mod.EventBusSubscriber(modid="sourceofmystery",value=Dist.CLIENT)
public final class DivineCGDirector {
    private static ClientLevel world;
    private static Entity savedCamera;
    private static Marker camera;
    private static CameraType savedType;
    private static boolean savedGui,running,returned,achievementShown;
    private static long started;
    private static UUID token;
    private static Vec3 eye,look,origin;
    private static float eyeHeight,savedYaw,savedPitch;
    private static ItemStack armor=ItemStack.EMPTY;
    private static final DivineCGScene SCENE=new DivineCGScene();
    private static final Set<String> cues=new HashSet<>();
    private static final List<SoundInstance> sounds=new ArrayList<>();
    private static float tail;
    private static final ResourceLocation CLOUDS=new ResourceLocation("sourceofmystery","textures/cinematic/divine/ascent_clouds.png");
    private DivineCGDirector(){}
    public static boolean active(){return running;}
    public static float seconds(){var mc=Minecraft.getInstance();return running&&world!=null?(world.getGameTime()-started+mc.getFrameTime())/20f:0;}
    public static void receive(DivineCinematicPacket packet){
        var mc=Minecraft.getInstance();
        if(!packet.start()){if(running&&packet.token().equals(token))stop(false);return;}
        if(running || mc.level==null || mc.player==null || OfferingIntroDirector.active()){
            ModNetwork.CHANNEL.sendToServer(new DivineCinematicDonePacket(packet.token()));return;
        }
        SCENE.prepare();
        world=mc.level;token=packet.token();started=world.getGameTime();eye=mc.player.getEyePosition();look=mc.player.getLookAngle();
        origin=new Vec3(eye.x,eye.y+8192,eye.z);eyeHeight=mc.player.getEyeHeight();savedYaw=mc.player.getYRot();savedPitch=mc.player.getXRot();
        savedCamera=mc.getCameraEntity();savedType=mc.options.getCameraType();savedGui=mc.options.hideGui;
        armor=packet.armor().copy();cues.clear();sounds.clear();returned=false;achievementShown=false;tail=0;
        camera=new Marker(EntityType.MARKER,world){@Override protected float getEyeHeight(Pose pose,EntityDimensions size){return eyeHeight;}};
        position(eye,eye.add(look));mc.player.closeContainer();mc.setScreen(null);
        mc.options.hideGui=false;mc.options.setCameraType(CameraType.FIRST_PERSON);mc.setCameraEntity(camera);running=true;
        cue("ascent",1);SourceOfMystery.LOGGER.info("DIVINE_CG_START local camera, vanilla body, 68.7 seconds with surface-to-orbit ascent");
    }
    private static void returnCamera(){
        var mc=Minecraft.getInstance();
        if(!returned){
            returned=true;mc.setCameraEntity(savedCamera!=null&&savedCamera.level()==mc.level&&!savedCamera.isRemoved()?savedCamera:mc.player);
            mc.options.setCameraType(savedType);mc.options.hideGui=savedGui;
            mc.mouseHandler.setIgnoreFirstMove();
        }
        if(mc.player!=null){mc.player.setYRot(savedYaw);mc.player.yRotO=savedYaw;mc.player.setXRot(savedPitch);mc.player.xRotO=savedPitch;}
    }
    public static void stop(boolean notify){
        if(!running)return;returnCamera();var mc=Minecraft.getInstance();
        if(mc.player!=null&&mc.level==world)achievement();
        for(var sound:sounds)mc.getSoundManager().stop(sound);sounds.clear();
        UUID receipt=token;running=false;world=null;camera=null;armor=ItemStack.EMPTY;SCENE.clear();tail=1;
        if(notify&&mc.getConnection()!=null)ModNetwork.CHANNEL.sendToServer(new DivineCinematicDonePacket(receipt));
        SourceOfMystery.LOGGER.info("DIVINE_CG_END restored camera and input");
    }
    private static void position(Vec3 p,Vec3 target){
        Vec3 feet=p.subtract(0,eyeHeight,0),d=target.subtract(p);camera.setPos(feet);
        camera.xo=camera.xOld=feet.x;camera.yo=camera.yOld=feet.y;camera.zo=camera.zOld=feet.z;
        float yaw=(float)(Mth.atan2(d.z,d.x)*Mth.RAD_TO_DEG)-90,pitch=(float)(-Mth.atan2(d.y,d.horizontalDistance())*Mth.RAD_TO_DEG);
        camera.setYRot(yaw);camera.yRotO=yaw;camera.setXRot(pitch);camera.xRotO=pitch;
    }
    @SubscribeEvent public static void frame(TickEvent.RenderTickEvent event){
        if(event.phase!=TickEvent.Phase.START)return;
        var mc=Minecraft.getInstance();if(!running)return;
        if(mc.player==null || mc.level!=world || !mc.player.isAlive()){stop(true);return;}
        float real=seconds(),t=DivineCGTimeline.storyTime(real);
        if(real>=DivineCGTimeline.END){stop(true);return;}
        if(t>=35.35f){returnCamera();return;}
        if(real<DivineCGTimeline.SPACE_START){
            float u=real/DivineCGTimeline.SPACE_START;
            Vec3 horizontal=new Vec3(look.x,0,look.z).normalize();
            if(horizontal.lengthSqr()<.001)horizontal=new Vec3(-Math.sin(savedYaw*Mth.DEG_TO_RAD),0,Math.cos(savedYaw*Mth.DEG_TO_RAD));
            Vec3 pos=eye.add(horizontal.scale(real*.6)).add(0,u*u*u*u*900,0);
            float pitch=DivineCGTimeline.mix(savedPitch,72,DivineCGTimeline.ramp(real,.35f,2.8f))*Mth.DEG_TO_RAD;
            Vec3 direction=horizontal.scale(Mth.cos(pitch)).add(0,-Mth.sin(pitch),0);
            position(pos,pos.add(direction));
        } else {
            var sample=real>=DivineCGTimeline.ORBIT_START?DivineCGTrack.camera(t):null;
            if(sample!=null){
                var v=sample.values;position(origin.add(v[1],v[2],v[3]),origin.add(v[4],v[5],v[6]));
            } else {
                var shot=real<DivineCGTimeline.ORBIT_START?DivineCGTimeline.ascentShot(real):DivineCGTimeline.shot(t);
                position(origin.add(shot.eye()),origin.add(shot.look()));
            }
        }
        if(mc.getCameraEntity()!=camera)mc.setCameraEntity(camera);
        if(t>=4)cue("cosmos",.58f);if(t>=10)cue("stairs",.7f);
        for(int i=0;i<5;i++)if(t>=12.76f+i*1.0833333f)cue("step"+i,"step",.74f);
        if(t>=17.9f)cue("step5","step",.65f);
        if(t>=22.05f)cue("touch",.65f);if(t>=24.15f)cue("bestowal",1);
        if(t>=25.0f)cue("armor",.65f);if(t>=29.5f)cue("throne",.8f);
        if(t>=DivineCGTimeline.EQUIP)achievement();
    }
    private static void achievement(){if(!achievementShown){achievementShown=true;Minecraft.getInstance().getToasts().addToast(new DivineAscensionToast());}}
    private static void cue(String name,float volume){cue(name,name,volume);}
    private static void cue(String key,String name,float volume){
        if(!cues.add(key))return;
        var s=new SimpleSoundInstance(DivineCGSounds.event(name).getLocation(),net.minecraft.sounds.SoundSource.PLAYERS,volume,1,RandomSource.create(),false,0,SoundInstance.Attenuation.NONE,0,0,0,true);
        sounds.add(s);Minecraft.getInstance().getSoundManager().play(s);
    }
    @SubscribeEvent public static void render(RenderLevelStageEvent e){
        // AFTER_ENTITIES carries the world-view pose; AFTER_LEVEL in this Forge
        // version carries a projection stack and would project the scene twice.
        if(!running || returned || seconds()<DivineCGTimeline.SPACE_START || e.getStage()!=RenderLevelStageEvent.Stage.AFTER_ENTITIES || OutsiderShaderCompat.shadowPass())return;
        SCENE.render(e,origin,DivineCGTimeline.storyTime(seconds()),armor);
    }
    @SubscribeEvent public static void input(MovementInputUpdateEvent e){if(running){var i=e.getInput();i.forwardImpulse=i.leftImpulse=0;i.up=i.down=i.left=i.right=i.jumping=i.shiftKeyDown=false;}}
    @SubscribeEvent public static void hand(RenderHandEvent e){if(running)e.setCanceled(true);}
    @SubscribeEvent public static void scroll(InputEvent.MouseScrollingEvent e){if(running)e.setCanceled(true);}
    @SubscribeEvent public static void screen(ScreenEvent.Opening e){if(running&&e.getNewScreen() instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?>)e.setCanceled(true);}
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut e){if(running)stop(false);tail=0;}
    @SubscribeEvent public static void interact(InputEvent.InteractionKeyMappingTriggered e){if(running){e.setCanceled(true);e.setSwingHand(false);}}
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void key(InputEvent.Key e){if(running&&e.getKey()==GLFW.GLFW_KEY_ESCAPE&&e.getAction()==GLFW.GLFW_PRESS)stop(true);}
    @SubscribeEvent public static void overlay(RenderGuiOverlayEvent.Pre e){if(running)e.setCanceled(true);}
    @SubscribeEvent public static void name(RenderNameTagEvent e){if(running)e.setResult(Event.Result.DENY);}
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void player(RenderPlayerEvent.Pre e){if(running&&e.getEntity()==Minecraft.getInstance().player)e.setCanceled(true);}
    @SubscribeEvent public static void fov(ViewportEvent.ComputeFov e){if(running&&!returned){float real=seconds(),t=DivineCGTimeline.storyTime(real);var sample=real>=DivineCGTimeline.ORBIT_START?DivineCGTrack.camera(t):null;e.setFOV(real<DivineCGTimeline.SPACE_START?DivineCGTimeline.mix((float)e.getFOV(),80,DivineCGTimeline.ramp(real,0,DivineCGTimeline.SPACE_START)):real<DivineCGTimeline.ORBIT_START?DivineCGTimeline.ascentShot(real).fov():sample!=null?sample.values[7]:DivineCGTimeline.shot(t).fov());}}
    @SubscribeEvent public static void fog(ViewportEvent.ComputeFogColor e){if(running&&seconds()>=DivineCGTimeline.SPACE_START&&!returned){e.setRed(0);e.setGreen(0);e.setBlue(0);}}
    @SubscribeEvent public static void fogRange(ViewportEvent.RenderFog e){if(running&&seconds()>=DivineCGTimeline.SPACE_START&&!returned){e.setNearPlaneDistance(500);e.setFarPlaneDistance(4096);e.setCanceled(true);}}
    @SubscribeEvent public static void gui(RenderGuiEvent.Post e){
        var mc=Minecraft.getInstance();int w=mc.getWindow().getGuiScaledWidth(),h=mc.getWindow().getGuiScaledHeight();
        if(!running){if(tail>0){tail=Math.max(0,tail-.035f);e.getGuiGraphics().fill(0,0,w,h,((int)(tail*255)<<24));}return;}
        float real=seconds(),t=DivineCGTimeline.storyTime(real);ascentClouds(e.getGuiGraphics(),w,h,real);
        int bar=(int)(h*.095f*DivineCGTimeline.ramp(real,0,.6f));
        e.getGuiGraphics().fill(0,0,w,bar,0xff000000);e.getGuiGraphics().fill(0,h-bar,w,h,0xff000000);
        float flash=(1-DivineCGTimeline.ramp(t,24.65f,25.55f))*DivineCGTimeline.ramp(t,24.05f,24.5f);
        if(flash>.002)e.getGuiGraphics().fill(0,0,w,h,((int)(flash*255)<<24)|0xfff7df);
        float end=DivineCGTimeline.ramp(t,35,35.35f);
        float black=end;
        if(black>.002)e.getGuiGraphics().fill(0,0,w,h,((int)(black*255)<<24));
        if(real>.8&&real<2.4)e.getGuiGraphics().drawCenteredString(mc.font,"神威 · 天佑",w/2,h-bar-19,0xffe3cf99);
    }
    private static void ascentClouds(net.minecraft.client.gui.GuiGraphics g,int w,int h,float real){
        if(real>7)return;
        // Atmospheric light thins continuously as the planet's limb comes into view.
        if(real>=DivineCGTimeline.SPACE_START){
            int haze=(int)(110*(1-DivineCGTimeline.ramp(real,4.6f,6.8f)));
            if(haze>0)g.fillGradient(0,0,w,h,(haze<<24)|0x648ebc,(haze<<24)|0xa3c6e5);
        }
        float cloud=DivineCGTimeline.ramp(real,2.35f,3.4f)*(1-DivineCGTimeline.ramp(real,3.95f,5.35f));
        if(cloud<.002)return;
        // Opaque inside the cloud bank across the coordinate handoff, never a black cut.
        int alpha=(int)(cloud*255);
        g.fillGradient(0,0,w,h,(alpha<<24)|0xcad9e6,(alpha<<24)|0xf2f6fa);
        g.flush();RenderSystem.enableBlend();RenderSystem.defaultBlendFunc();
        try {
            RenderSystem.setShaderColor(1,1,1,cloud*.83f);
            g.blit(CLOUDS,0,0,w,h,real*28,-real*96,512,512,512,512);
            RenderSystem.setShaderColor(1,1,1,cloud*.34f);
            g.blit(CLOUDS,0,0,w,h,-real*39+150,real*125,384,384,512,512);
            g.flush();
        } finally {RenderSystem.setShaderColor(1,1,1,1);RenderSystem.disableBlend();}
    }
}
