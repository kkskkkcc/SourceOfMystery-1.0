package com.sourceofmystery.client;

import com.sourceofmystery.network.*;
import net.minecraft.client.*;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.*;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.*;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.UUID;

/** Only the caster's camera is replaced. Pose, hand visibility, FOV and view are restored on every exit. */
@Mod.EventBusSubscriber(modid="sourceofmystery",value=Dist.CLIENT)
public final class DomainDirector {
    private static boolean running,gui;private static UUID token;private static ClientLevel world;
    private static Marker camera;private static Entity oldCamera;private static CameraType oldType;
    private static Vec3 origin,initialEye,initialLook,forward,side;private static float eyeHeight,yaw,pitch;
    private static long started;private static float offset;
    private DomainDirector(){}
    public static boolean active(){return running;}
    public static float seconds(){return running&&world!=null?(world.getGameTime()-started+Minecraft.getInstance().getFrameTime()+offset)/20f:0;}
    public static void start(DomainVisuals.View field){
        var mc=Minecraft.getInstance();if(running&&field.data().token().equals(token))return;
        if(mc.player==null||mc.level==null||DivineCGDirector.active()||OfferingIntroDirector.active())return;
        if(running)stop();world=mc.level;token=field.data().token();origin=field.center(1);yaw=field.yaw();pitch=mc.player.getXRot();
        initialEye=mc.player.getEyePosition();initialLook=initialEye.add(mc.player.getLookAngle().scale(5));
        forward=new Vec3(-Math.sin(yaw*Mth.DEG_TO_RAD),0,Math.cos(yaw*Mth.DEG_TO_RAD));side=new Vec3(forward.z,0,-forward.x);
        oldCamera=mc.getCameraEntity();oldType=mc.options.getCameraType();gui=mc.options.hideGui;eyeHeight=mc.player.getEyeHeight();
        started=world.getGameTime();offset=field.age(0);
        camera=new Marker(EntityType.MARKER,world){@Override protected float getEyeHeight(Pose pose,EntityDimensions d){return eyeHeight;}};
        position(initialEye,initialLook);mc.options.setCameraType(CameraType.FIRST_PERSON);mc.options.hideGui=false;mc.setCameraEntity(camera);running=true;
    }
    public static void stop(){
        if(!running)return;var mc=Minecraft.getInstance();
        mc.setCameraEntity(oldCamera!=null&&oldCamera.level()==mc.level&&!oldCamera.isRemoved()?oldCamera:mc.player);
        mc.options.setCameraType(oldType);mc.options.hideGui=gui;
        if(mc.player!=null){mc.player.setYRot(yaw);mc.player.yRotO=yaw;mc.player.setXRot(pitch);mc.player.xRotO=pitch;}
        mc.mouseHandler.setIgnoreFirstMove();running=false;camera=null;world=null;
    }
    private static Vec3 at(double x,double y,double z){return origin.add(side.scale(x)).add(0,y,0).add(forward.scale(z));}
    private static float ease(float x){x=Mth.clamp(x,0,1);return x*x*(3-2*x);}
    private static void position(Vec3 p,Vec3 target){
        Vec3 feet=p.subtract(0,eyeHeight,0),d=target.subtract(p);camera.setPos(feet);camera.xo=camera.xOld=feet.x;camera.yo=camera.yOld=feet.y;camera.zo=camera.zOld=feet.z;
        float a=(float)(Mth.atan2(d.z,d.x)*Mth.RAD_TO_DEG)-90,b=(float)(-Mth.atan2(d.y,d.horizontalDistance())*Mth.RAD_TO_DEG);
        camera.setYRot(a);camera.yRotO=a;camera.setXRot(b);camera.xRotO=b;
    }
    @SubscribeEvent public static void frame(TickEvent.RenderTickEvent event){
        if(event.phase!=TickEvent.Phase.START||!running)return;var mc=Minecraft.getInstance();float t=seconds();
        if(mc.level!=world||mc.player==null||!mc.player.isAlive()||t>=10){stop();return;}
        // A late authoritative position correction must move the shot together with its actor.
        // Keeping the first packet's feet position made close-ups drift above a falling player.
        origin=mc.player.getPosition(mc.getFrameTime());
        Vec3 eye,look;
        if(t<.7f){float p=ease(t/.7f);eye=initialEye.lerp(at(.5,1.05,1.6),p);look=initialLook.lerp(at(0,1.2,.3),p);}
        else if(t<1.7f){float p=ease((t-.7f));eye=at(.5,1.05,1.6).lerp(at(.22,1.03,1.2),p);look=at(0,1.03,.5);}
        else if(t<2.5f){eye=at(.18,1.73,1.15);look=at(0,1.73,0);}
        else if(t<3.7f){float p=ease((t-2.5f)/1.2f);eye=at(.18,1.73,1.15).lerp(at(1.8,12,-2.8),p);look=at(0,1.73,0).lerp(at(0,1,1),p);}
        else {float p=ease((t-3.7f)/5.7f);eye=at(1.8,12,-2.8).lerp(at(2.2,12.6,-3.2),p);look=at(0,1,1);}
        if(t>9.45f){float p=ease((t-9.45f)/.55f);eye=eye.lerp(mc.player.getEyePosition(mc.getFrameTime()),p);look=look.lerp(initialLook,p);}
        position(eye,look);if(mc.getCameraEntity()!=camera)mc.setCameraEntity(camera);
    }
    @SubscribeEvent public static void overlay(RenderGuiOverlayEvent.Pre e){if(running)e.setCanceled(true);}
    @SubscribeEvent public static void screen(ScreenEvent.Opening e){if(running&&e.getNewScreen() instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?>)e.setCanceled(true);}
    @SubscribeEvent public static void hand(RenderHandEvent e){if(running)e.setCanceled(true);}
    @SubscribeEvent public static void fov(ViewportEvent.ComputeFov e){
        if(!running)return;float t=seconds(),f=t<1.7?48:t<2.5?40:Mth.lerp(ease((t-2.5f)/1.2f),40,130);
        if(t>9.45)f=Mth.lerp(ease((t-9.45f)/.55f),f,(float)e.getFOV());e.setFOV(f);
    }
    @SubscribeEvent public static void input(InputEvent.InteractionKeyMappingTriggered e){
        if(!running)return;if(e.isUseItem())ModNetwork.CHANNEL.sendToServer(new DomainStopPacket(token));e.setCanceled(true);e.setSwingHand(false);
    }
    @SubscribeEvent public static void scroll(InputEvent.MouseScrollingEvent e){if(running)e.setCanceled(true);}
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut e){stop();}
    @SubscribeEvent public static void angles(ViewportEvent.ComputeCameraAngles e){
        if(!running)return;float t=seconds(),pulse=(float)Math.exp(-Math.max(0,t-2.5)*4)*(t>=2.5?1:0);
        e.setRoll(e.getRoll()+Mth.sin(t*41)*pulse*.8f);e.setPitch(e.getPitch()+Mth.sin(t*33)*pulse*.3f);
    }
    @SubscribeEvent public static void gui(RenderGuiEvent.Post e){
        if(!running)return;var mc=Minecraft.getInstance();int w=mc.getWindow().getGuiScaledWidth(),h=mc.getWindow().getGuiScaledHeight();float t=seconds();
        float fade=ease(t/.4f)*(1-ease((t-9.45f)/.55f));int bar=(int)(h*.07*fade);
        e.getGuiGraphics().fill(0,0,w,bar,0xff000000);e.getGuiGraphics().fill(0,h-bar,w,h,0xff000000);
        float flash=(float)Math.exp(-Math.abs(t-2.55)*14);
        if(flash>.03)e.getGuiGraphics().fill(0,0,w,h,((int)(flash*115)<<24)|0xa50820);
    }
}
