package com.sourceofmystery.client;

import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Axis;
import com.sourceofmystery.ritual.OfferingPortalRitual;
import net.minecraft.client.*;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.*;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

/** One continuous world-clock shot sequence; paused games also pause the film. */
@Mod.EventBusSubscriber(modid="sourceofmystery",value=Dist.CLIENT)
public final class OfferingIntroDirector {
    private static boolean running,axis,savedGui;
    private static long started;
    private static int bossId,x0,y0,z0,total;
    private static Marker camera;
    private static float cameraEyeHeight=1.62f;
    private static Camera returnView;
    private static Vec3 returnTarget;
    private static CameraType savedType;
    private static Vec3 playerEye,normal,side,initialLook;
    private static net.minecraft.client.multiplayer.ClientLevel scene;
    private static final int[][] OUTLINE={{0,0},{1,0},{2,0},{3,0},{3,1},{3,2},{3,3},{3,4},{2,4},{1,4},{0,4},{0,3},{0,2},{0,1}};

    public static void start(com.sourceofmystery.network.DimensionalOfferingPacket packet){
        var mc=Minecraft.getInstance();if(mc.level==null||mc.player==null)return;
        if(running)stop(false);scene=mc.level;bossId=packet.bossId();x0=packet.x();y0=packet.y();z0=packet.z();axis=packet.axis();total=packet.ticks();
        started=mc.level.getGameTime();playerEye=mc.player.getEyePosition();initialLook=playerEye.add(mc.player.getLookAngle().scale(8));
        normal=axis?new Vec3(playerEye.x<center().x?-1:1,0,0):new Vec3(0,0,playerEye.z<center().z?-1:1);
        side=new Vec3(normal.z,0,-normal.x);
        savedType=mc.options.getCameraType();savedGui=mc.options.hideGui;mc.options.hideGui=false;
        cameraEyeHeight=mc.player.getEyeHeight();returnTarget=null;
        camera=new CinematicCamera(mc.level);camera.setPos(playerEye.subtract(0,cameraEyeHeight,0));
        returnView=new Camera();returnView.setup(mc.level,mc.player,false,false,1);
        for(int i=0;i<32;i++)returnView.tick();
        mc.options.setCameraType(CameraType.FIRST_PERSON);mc.setCameraEntity(camera);running=true;
    }
    public static boolean active(){return running;}
    public static float seconds(){var mc=Minecraft.getInstance();return mc.level==null?0:(mc.level.getGameTime()-started+mc.getFrameTime())/20f;}
    private static Vec3 center(){return axis?new Vec3(x0+.5,y0+3.45,z0+2):new Vec3(x0+2,y0+3.45,z0+.5);}
    private static void stop(boolean completed){
        if(!running)return;var mc=Minecraft.getInstance();
        if(completed&&returnTarget!=null&&mc.player!=null)aimPlayer(returnTarget);
        mc.setCameraEntity(mc.player);mc.options.setCameraType(savedType);mc.options.hideGui=savedGui;
        running=false;camera=null;scene=null;returnView=null;returnTarget=null;
    }
    /** Vanilla Camera smooths entity eye height even across entity switches.
     * A zero-eye-height Marker makes the first player frame originate at the feet. */
    private static final class CinematicCamera extends Marker {
        CinematicCamera(net.minecraft.client.multiplayer.ClientLevel level){super(EntityType.MARKER,level);}
        @Override protected float getEyeHeight(Pose pose,EntityDimensions dimensions){return cameraEyeHeight;}
    }
    private static void aimPlayer(Vec3 target){
        var mc=Minecraft.getInstance();Vec3 d=target.subtract(mc.player.getEyePosition(mc.getFrameTime()));
        float yaw=(float)(Mth.atan2(d.z,d.x)*Mth.RAD_TO_DEG)-90;
        float pitch=(float)(-Mth.atan2(d.y,d.horizontalDistance())*Mth.RAD_TO_DEG);
        mc.player.setYRot(yaw);mc.player.yRotO=yaw;mc.player.setXRot(pitch);mc.player.xRotO=pitch;
    }
    @SubscribeEvent public static void tick(TickEvent.RenderTickEvent event){
        if(event.phase!=TickEvent.Phase.START||!running)return;var mc=Minecraft.getInstance();float t=seconds();
        if(mc.level!=scene||mc.player==null||!mc.player.isAlive()){stop(false);return;}
        if(t>=total/20f){stop(true);return;}
        if(mc.getCameraEntity()!=camera)mc.setCameraEntity(camera);
        if(Math.abs(cameraEyeHeight-mc.player.getEyeHeight())>.001f){cameraEyeHeight=mc.player.getEyeHeight();camera.refreshDimensions();}
        Shot shot=shot(t);Vec3 p=shot.pos.subtract(0,cameraEyeHeight,0);
        camera.setPos(p);camera.xo=camera.xOld=p.x;camera.yo=camera.yOld=p.y;camera.zo=camera.zOld=p.z;
        Vec3 look=shot.look.subtract(shot.pos);float yaw=(float)(Mth.atan2(look.z,look.x)*Mth.RAD_TO_DEG)-90;
        float pitch=(float)(-Mth.atan2(look.y,look.horizontalDistance())*Mth.RAD_TO_DEG);
        camera.setYRot(yaw);camera.setXRot(pitch);camera.yRotO=yaw;camera.xRotO=pitch;
    }
    @SubscribeEvent public static void input(MovementInputUpdateEvent event){if(!running)return;var i=event.getInput();i.forwardImpulse=i.leftImpulse=0;i.up=i.down=i.left=i.right=i.jumping=i.shiftKeyDown=false;}
    @SubscribeEvent public static void hand(RenderHandEvent event){if(running)event.setCanceled(true);}
    @SubscribeEvent public static void interact(InputEvent.InteractionKeyMappingTriggered event){if(running){event.setCanceled(true);event.setSwingHand(false);}}
    @SubscribeEvent public static void overlay(RenderGuiOverlayEvent.Pre event){if(running)event.setCanceled(true);}
    @SubscribeEvent public static void name(RenderNameTagEvent event){if(running)event.setResult(net.minecraftforge.eventbus.api.Event.Result.DENY);}
    @SubscribeEvent public static void player(RenderPlayerEvent.Pre event){
        if(running&&event.getEntity()==Minecraft.getInstance().player&&(seconds()>=10||camera.getEyePosition().distanceToSqr(playerEye)<.36))event.setCanceled(true);
    }
    @SubscribeEvent public static void fov(ViewportEvent.ComputeFov event){
        if(!running)return;float t=seconds(),natural=(float)event.getFOV(),lens;
        if(t<3)lens=Mth.lerp(smooth(t/3),natural,52);
        else if(t<8)lens=Mth.lerp(smooth((t-3)/5),52,46);
        else if(t<10)lens=43;
        else if(t<14)lens=Mth.lerp(smooth((t-10)/3),48,44);
        else if(t<22)lens=Mth.lerp(smooth((t-14)/8),40,43);
        else if(t<24)lens=48;
        else lens=Mth.lerp(smooth((t-24)/6.5f),42,35);
        event.setFOV(Mth.lerp(smooth((t-30.5f)/1.5f),lens,natural));
    }
    @SubscribeEvent public static void angles(ViewportEvent.ComputeCameraAngles event){
        if(!running)return;float t=seconds();float quake=t>=3&&t<8?.18f:0;
        if(t>=13&&t<14)quake=1.8f*(float)Math.exp(-(t-13)*5);
        float cinematicRoll=t>=16&&t<20.5f?.65f*Mth.sin((t-16)*Mth.PI/4.5f):0;
        if(t>=24.9f&&t<25.25f)cinematicRoll+=Mth.sin((t-24.9f)*42f)*.8f*pulse(t,24.9f,.35f);
        event.setRoll(Mth.sin(t*43)*quake+cinematicRoll);
        event.setPitch(event.getPitch()+Mth.sin(t*61)*quake*.3f);
    }
    @SubscribeEvent public static void bars(RenderGuiEvent.Post event){
        if(!running)return;var mc=Minecraft.getInstance();int w=mc.getWindow().getGuiScaledWidth(),h=mc.getWindow().getGuiScaledHeight();
        float now=seconds(),pressure=smooth((now-10)/3)*(1-smooth((now-30)/2));
        event.getGuiGraphics().fill(0,0,w,h,((int)(30*pressure)<<24)|0x010513);
        for(int i=0;i<32;i++){
            int alpha=(int)(pressure*90*(1-i/32f)*(1-i/32f));int dx=Math.max(1,w/130);
            event.getGuiGraphics().fill(i*dx,0,(i+1)*dx,h,(alpha<<24)|0x01030a);
            event.getGuiGraphics().fill(w-(i+1)*dx,0,w-i*dx,h,(alpha<<24)|0x01030a);
        }
        // Short accents at the two actual events; never a persistent blue veil.
        float burst=Math.max(pulse(now,13,.30f)*.65f,pulse(now,24.9f,.14f)*.36f);
        if(burst>.001f)event.getGuiGraphics().fill(0,0,w,h,((int)(burst*100)<<24)|0x397db1);
        int b=(int)(h*.10f*Math.min(smooth(now),1-smooth((now-30.5f)/1.5f)));
        event.getGuiGraphics().fill(0,0,w,b,0xff000000);event.getGuiGraphics().fill(0,h-b,w,h,0xff000000);
    }
    @SubscribeEvent public static void render(RenderLevelStageEvent event){
        if(!running||event.getStage()!=RenderLevelStageEvent.Stage.AFTER_ENTITIES)return;
        var mc=Minecraft.getInstance();if(mc.level!=scene)return;
        float t=seconds();var poses=event.getPoseStack();var buffers=mc.renderBuffers().bufferSource();Vec3 cam=event.getCamera().getPosition();
        if(t>=3&&t<26)frame(poses,buffers,cam,t);
        if(t>=8&&t<14)trails(poses,buffers,cam,t);
        if(t>=10&&t<26)rift(poses,buffers,cam,t);
        if(t>=15.1f&&t<25.5f)airStepSigils(poses,buffers,cam,t);
        buffers.endBatch();
    }
    private static void airStepSigils(PoseStack p,MultiBufferSource buffers,Vec3 cam,float t){
        float[] contacts={15.9f,18.2f,20.2f,22};
        float[] steps={.65f,1.30f,1.95f,2.20f},release={18,20.15f,24.9f,24.9f};
        for(int i=0;i<contacts.length;i++){
            float age=t-contacts[i];if(age<-.12f||t>release[i]+.55f)continue;
            Vec3 c=center().add(normal.scale(-.45f+steps[i])).add(side.scale(i%2==0?-.125:.125));
            p.pushPose();p.translate(c.x-cam.x,y0+1.048-cam.y,c.z-cam.z);
            VertexConsumer glow=OutsiderShaderCompat.glow(buffers,RenderType.lightning());
            float open=smooth((age+.12f)/.18f),alpha=.68f*open*(1-smooth((t-release[i])/.55f));
            float radius=.17f*open;
            for(int k=0;k<48;k++){
                double a=k*Math.PI/24+t*.18,b=(k+1)*Math.PI/24+t*.18;float w=.009f;
                EffectGeometry.glowQuad(glow,p.last().pose(),
                        new float[]{(float)Math.cos(a)*radius,0,(float)Math.sin(a)*radius},
                        new float[]{(float)Math.cos(b)*radius,0,(float)Math.sin(b)*radius},
                        new float[]{(float)Math.cos(b)*(radius+w),0,(float)Math.sin(b)*(radius+w)},
                        new float[]{(float)Math.cos(a)*(radius+w),0,(float)Math.sin(a)*(radius+w)},
                        new float[]{.63f,.16f,.98f,alpha},new float[]{.26f,.025f,.55f,alpha*.35f});
                float inner=radius*.70f;
                EffectGeometry.glowQuad(glow,p.last().pose(),
                        new float[]{(float)Math.cos(a)*inner,0,(float)Math.sin(a)*inner},
                        new float[]{(float)Math.cos(b)*inner,0,(float)Math.sin(b)*inner},
                        new float[]{(float)Math.cos(b)*(inner+.005f),0,(float)Math.sin(b)*(inner+.005f)},
                        new float[]{(float)Math.cos(a)*(inner+.005f),0,(float)Math.sin(a)*(inner+.005f)},
                        new float[]{.68f,.22f,1,alpha*.7f},new float[]{.36f,.04f,.68f,alpha*.25f});
                if(k%8==0){
                    double d=a+Math.PI*2/3;float r=radius*.58f;
                    EffectGeometry.glowQuad(glow,p.last().pose(),
                            new float[]{(float)Math.cos(a)*r,0,(float)Math.sin(a)*r},
                            new float[]{(float)Math.cos(d)*r,0,(float)Math.sin(d)*r},
                            new float[]{(float)Math.cos(d)*r,.005f,(float)Math.sin(d)*r+.005f},
                            new float[]{(float)Math.cos(a)*r,.005f,(float)Math.sin(a)*r+.005f},
                            new float[]{.58f,.12f,.95f,alpha*.8f},new float[]{.36f,.03f,.72f,alpha*.4f});
                }
            }p.popPose();
        }
    }
    private static void plane(PoseStack p,Vec3 cam){Vec3 c=center();p.translate(c.x-cam.x,c.y-cam.y,c.z-cam.z);p.mulPose(Axis.YP.rotationDegrees(axis?(normal.x>0?90:-90):(normal.z>0?0:180)));}
    private static void frame(PoseStack p,MultiBufferSource buffers,Vec3 cam,float t){
        for(int i=0;i<14;i++){
            int u=OUTLINE[i][0],v=OUTLINE[i][1];
            Vec3 source=axis?new Vec3(x0+.5,y0+v+.5,z0+u+.5):new Vec3(x0+u+.5,y0+v+.5,z0+.5);
            // Ordered floating arch, with a clear floor-level passage for the feet.
            // The bottom-left block keeps its winding instead of crossing its neighbors.
            double a=Math.toRadians(i==0?220:-40+(i-1)*20);
            float e=smooth((t-3-i*.055f)/3.9f),vanish=1-smooth((t-OfferingPortalRitual.SEAL_TICK/20f-i*.015f)/.55f);
            double rx=Math.cos(a)*3.85,ry=Math.sin(a)*3.85;
            Vec3 target=center().add(axis?new Vec3(.10*Math.sin(t*.65+i),ry,rx):new Vec3(rx,ry,.10*Math.sin(t*.65+i)));
            Vec3 pos=source.lerp(target,e).add(0,.045*Math.sin(t*1.2+i)*e,0);
            p.pushPose();p.translate(pos.x-cam.x,pos.y-cam.y,pos.z-cam.z);
            p.mulPose(Axis.YP.rotationDegrees(e*(6*Mth.sin(t*.35f+i)+i%3*3)));
            p.mulPose(Axis.ZP.rotationDegrees(e*7*Mth.sin(t*.29f+i*2)));
            p.scale(vanish,vanish,vanish);p.translate(-.5,-.5,-.5);
            Minecraft.getInstance().getBlockRenderer().renderSingleBlock(Blocks.OBSIDIAN.defaultBlockState(),p,buffers,15728880,OverlayTexture.NO_OVERLAY);p.popPose();
        }
    }
    private static void trails(PoseStack p,MultiBufferSource buffers,Vec3 cam,float t){
        p.pushPose();plane(p,cam);Matrix4f m=p.last().pose();VertexConsumer glow=OutsiderShaderCompat.glow(buffers,RenderType.lightning());
        float fade=smooth(t-8)*(1-smooth(t-13));
        for(int i=0;i<22;i++){
            float phase=(t*.38f+i*.618f)%1;double angle=i*2.39996+t*.12;float r=6*(1-phase)+.2f;
            for(int j=0;j<9;j++){
                double a=angle+j*.018;float r0=r+j*.14f,r1=r0+.14f;
                ribbon(glow,m,a,a+.018,r0,r1,.012f+.028f*(1-j/9f),.10f,.38f,.9f,fade*(1-j/9f)*.65f);
            }
        }p.popPose();
    }
    private static void rift(PoseStack p,MultiBufferSource buffers,Vec3 cam,float t){
        p.pushPose();plane(p,cam);Matrix4f m=p.last().pose();VertexConsumer glow=OutsiderShaderCompat.glow(buffers,RenderType.lightning());
        if(t<13){float e=smooth((t-10)/3);
            for(int i=0;i<11;i++){double a=i*Math.PI*2/11;float r=e*(2.1f+EffectGeometry.hash(71,i));
                EffectGeometry.crack(glow,m,new float[]{0,0,.025f},new float[]{(float)Math.cos(a),(float)Math.sin(a),0},new float[]{-(float)Math.sin(a),(float)Math.cos(a),0},6,r/6,.022f,i*43L,new float[]{.18f,.48f,.92f,.85f});}
        }else{
            float collapse=1-smooth((t-OfferingPortalRitual.SEAL_TICK/20f)/.72f),r=3.4f*smooth((t-13)/.22f)*collapse;
            OfferingVoidSurface.render(p,buffers,t,r,collapse);
            glow=OutsiderShaderCompat.glow(buffers,RenderType.lightning());
            float impact=1-smooth((t-13)/.7f);
            if(impact>0)for(int i=0;i<64;i++){double a=i*Math.PI*2/64,b=(i+1)*Math.PI*2/64;float rr=3.2f+(t-13)*7;
                EffectGeometry.glowQuad(glow,m,point(a,rr,.05f),point(b,rr,.05f),point(b,rr+.045f,.05f),point(a,rr+.045f,.05f),new float[]{.4f,.72f,1,impact*.6f},new float[]{.08f,.3f,.85f,0});}
        }p.popPose();
    }
    private static float[] point(double a,float r,float z){return new float[]{(float)Math.cos(a)*r,(float)Math.sin(a)*r,z};}
    private static void ribbon(VertexConsumer v,Matrix4f m,double a,double b,float r,float s,float w,float red,float green,float blue,float alpha){
        EffectGeometry.glowQuad(v,m,point(a,r-w,.04f),point(a,r+w,.04f),point(b,s+w,.04f),point(b,s-w,.04f),new float[]{red,green,blue,alpha},new float[]{red,green,blue,alpha*.25f});
    }
    private record Shot(Vec3 pos,Vec3 look){}
    private static Shot shot(float t){
        Vec3 c=center(),wide=playerEye.add(normal.scale(5)).add(side.scale(4)).add(0,3,0);
        Vec3 gate=c.add(normal.scale(13)).add(side.scale(2));
        Vec3 face=playerEye.subtract(normal.scale(1.8)).add(side.scale(.45));
        float distance=OfferingPortalRitual.entranceBodyDistance(t);
        Vec3 feet=new Vec3(c.x,y0+1.04,c.z).add(normal.scale(distance));
        Vec3 head=feet.add(0,2.57,0),body=feet.add(0,1.65,0);
        Vec3 portrait=head.add(normal.scale(1.70)).add(side.scale(.12));
        if(t<3)return new Shot(playerEye.lerp(wide,smooth(t/3)),initialLook.lerp(c.add(0,-1,0),smooth(t/3)));
        if(t<8)return new Shot(wide.lerp(gate,smooth((t-3)/5)),c.add(0,-.5,0));
        if(t<10)return new Shot(face,playerEye.add(0,-.08,0));
        if(t<13)return new Shot(gate.lerp(c.add(normal.scale(8.5)),smooth((t-10)/3)),c);
        if(t<14)return new Shot(c.add(normal.scale(8.5+.35*Math.sin((t-13)*Mth.PI))),c);
        // Same screen direction throughout the entrance: foot, profile, face, full figure.
        // One continuous eight-second crane/dolly from heels to face.
        if(t<22){
            float u=smooth((t-14)/8);
            Vec3 lens=feet.add(side.scale(Mth.lerp(u,1.65f,1.05f)))
                    .add(normal.scale(2.65f+.38f*Math.sin(u*Math.PI)))
                    .add(0,Mth.lerp(u,1.05f,2.55f),0);
            return new Shot(lens,feet.add(0,Mth.lerp(u,.23f,2.57f),0));
        }
        Vec3 hero=feet.add(normal.scale(5.6)).add(side.scale(2.5)).add(0,1.65,0);
        if(t<24)return new Shot(hero,body);
        float push=smooth((t-24)/6.5f);
        Vec3 closePortrait=head.add(normal.scale(Mth.lerp(push,1.85f,1.70f))).add(side.scale(Mth.lerp(push,.32f,.12f)));
        if(t<30.5f)return new Shot(closePortrait,head);
        // Return to the live player view while looking at the boss, not the lower
        // obsidian block that happened to be clicked when the ritual began.
        returnTarget=head;aimPlayer(head);returnView.tick();
        var mc=Minecraft.getInstance();
        returnView.setup(mc.level,mc.player,!savedType.isFirstPerson(),savedType.isMirrored(),mc.getFrameTime());
        Vec3 end=returnView.getPosition(),endLook=end.add(Vec3.directionFromRotation(returnView.getXRot(),returnView.getYRot()).scale(8));
        float blend=smooth((t-30.5f)/1.5f);
        return new Shot(portrait.lerp(end,blend),head.lerp(endLook,blend));
    }
    private static float pulse(float time,float start,float duration){return time<start||time>=start+duration?0:1-smooth((time-start)/duration);}
    private static float smooth(float v){float u=Mth.clamp(v,0,1);return u*u*u*(10+u*(-15+6*u));}
}
