package com.sourceofmystery.client;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.sourceofmystery.SourceOfMystery;
import com.sourceofmystery.item.ModItems;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.nio.file.Files;
import java.util.*;

/** Isolated real-client test: full player renderer, vertex transforms and screenshots. */
@Mod.EventBusSubscriber(modid="sourceofmystery", value=Dist.CLIENT)
public final class DivineHaloSmokeCheck {
    private static int ticks, step, time;
    private static volatile boolean ready;
    private static RemotePlayer actor;
    private static float yaw=180;
    private static boolean crouching;
    private static final JsonObject report=new JsonObject();

    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (!Boolean.getBoolean("sourceofmystery.divineHaloCheck") || event.phase!=TickEvent.Phase.END) return;
        var mc=Minecraft.getInstance();
        if (mc.player==null || mc.level==null || mc.getSingleplayerServer()==null) return;
        ticks++; time++;
        if (actor!=null) pose(yaw,crouching);
        if (step==0 && ticks>100) {
            mc.setScreen(null); mc.options.hideGui=true; mc.options.setCameraType(CameraType.FIRST_PERSON);
            mc.getSingleplayerServer().execute(()->{
                var level=mc.getSingleplayerServer().overworld();
                level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,level.getServer());
                level.getGameRules().getRule(GameRules.RULE_FALL_DAMAGE).set(false,level.getServer());
                level.setDayTime(7000);
                for(int x=-7;x<=7;x++) for(int z=-7;z<=7;z++) {
                    level.setBlock(new BlockPos(x,199,z),Blocks.STONE_BRICKS.defaultBlockState(),2);
                    for(int y=200;y<207;y++) level.setBlock(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState(),2);
                }
                var p=level.getServer().getPlayerList().getPlayers().get(0);
                p.setGameMode(GameType.CREATIVE); p.getInventory().clearContent();
                p.teleportTo(level,.5,200,3.1,180,10); p.fallDistance=0;
                // A real server-side armor stand also exercises the non-player attachment.
                var stand=new ArmorStand(level,3.5,200,-3.5);
                stand.setYRot(180); stand.setYBodyRot(180); stand.setYHeadRot(180);
                stand.setItemSlot(EquipmentSlot.CHEST,new ItemStack(ModItems.DIVINE_BLESSING_CHESTPLATE.get()));
                level.addFreshEntity(stand); ready=true;
            });
            step=1; time=0;
        } else if(step==1 && ready && time>40) {
            actor=new RemotePlayer(mc.level,new GameProfile(UUID.fromString("7a53bbd3-ffef-47d5-a850-5dc11820ba20"),"HaloCheck"));
            actor.setId(-7007); actor.setPos(.5,200,.5); actor.setNoGravity(true);
            actor.setItemSlot(EquipmentSlot.CHEST,new ItemStack(ModItems.DIVINE_BLESSING_CHESTPLATE.get()));
            mc.level.addPlayer(actor.getId(),actor); pose(180,false);
            geometryChecks(mc); step=2; time=0;
        } else if(step==2 && time>30) {
            capture("01-rear-upright.png"); yaw=90; step=3; time=0;
        } else if(step==3 && time>30) {
            capture("02-turned-side.png"); camera(4.5,200,.5,90,6); step=4; time=0;
        } else if(step==4 && time>30) {
            capture("03-turned-rear.png"); crouching=true; step=5; time=0;
        } else if(step==5 && time>30) {
            capture("04-crouching.png"); crouching=false;
            actor.setItemSlot(EquipmentSlot.CHEST,ItemStack.EMPTY); step=6; time=0;
        } else if(step==6 && time>30) {
            capture("05-unequipped.png");
            mc.level.removeEntity(actor.getId(),Entity.RemovalReason.DISCARDED); actor=null;
            camera(3.5,200,-.9,180,10); step=7; time=0;
        } else if(step==7 && time>30) {
            capture("06-armor-stand.png");
            report.addProperty("real_forge_client",true);
            report.addProperty("visual_screenshots",6);
            try {Files.writeString(mc.gameDirectory.toPath().resolve("divine-halo-verification.json"),new GsonBuilder().setPrettyPrinting().create().toJson(report));}
            catch(java.io.IOException e){throw new RuntimeException(e);}
            SourceOfMystery.LOGGER.info("DIVINE_HALO_OK body_yaw=true crouching=true glyph_upright=true equip_gate=true");
            step=8; mc.stop();
        }
        if(ticks>1800)throw new AssertionError("Halo smoke timeout step="+step);
    }

    private static void pose(float angle,boolean crouch) {
        actor.setPos(.5,200,.5); actor.setDeltaMovement(0,0,0);
        actor.setYRot(angle); actor.yRotO=angle;
        actor.setYBodyRot(angle); actor.yBodyRotO=angle;
        actor.setYHeadRot(angle); actor.yHeadRotO=angle;
        actor.setXRot(0); actor.xRotO=0;
        actor.setShiftKeyDown(crouch); actor.setPose(crouch?Pose.CROUCHING:Pose.STANDING);
    }

    private static void camera(double x,double y,double z,float angle,float pitch) {
        var mc=Minecraft.getInstance(); mc.getSingleplayerServer().execute(()->{
            var p=mc.getSingleplayerServer().getPlayerList().getPlayers().get(0);
            p.teleportTo(p.serverLevel(),x,y,z,angle,pitch); p.fallDistance=0;
        });
    }

    private static List<float[]> render(Minecraft mc,float angle,boolean crouch,double x,double y,double z) {
        pose(angle,crouch);
        var spy=new Spy(); var stack=new PoseStack(); stack.translate(x,y,z);
        ((PlayerRenderer)mc.getEntityRenderDispatcher().getRenderer(actor)).render(actor,angle,0,stack,spy,LightTexture.FULL_BRIGHT);
        return spy.center;
    }

    private static void geometryChecks(Minecraft mc) {
        for(var renderer:mc.getEntityRenderDispatcher().getSkinMap().values()) layerCount((LivingEntityRenderer<?,?>)renderer);
        layerCount((LivingEntityRenderer<?,?>)mc.getEntityRenderDispatcher().getRenderer(new ArmorStand(mc.level,0,0,0)));
        report.addProperty("normal_slim_armor_stand_registered_once",true);
        var zero=render(mc,0,false,0,0,0); var turn=render(mc,90,false,0,0,0);
        var a=centroid(zero); var b=centroid(turn);
        close(b[0],-a[2]); close(b[1],a[1]); close(b[2],a[0]);
        if(Math.abs(a[2])<.3 || Math.abs(b[0])<.3)throw new AssertionError("Halo does not rotate around torso");
        report.addProperty("body_yaw_rotates_attachment",true);
        var translated=centroid(render(mc,90,false,13,7,-11));
        close(translated[0],b[0]+13);close(translated[1],b[1]+7);close(translated[2],b[2]-11);
        report.addProperty("caller_transform_preserved",true);
        var rear=render(mc,180,false,0,0,0);
        if(mean(rear,4,0,1)<=mean(rear,4,1,1))throw new AssertionError("Glyph upside down: image top is below bottom");
        if(mean(rear,3,1,0)<=mean(rear,3,0,0))throw new AssertionError("Glyph mirrored from behind");
        report.addProperty("glyph_top_above_bottom",true); report.addProperty("glyph_not_mirrored_from_rear",true);
        var crouch=render(mc,180,true,0,0,0);
        if(Math.abs(mean(crouch,4,0,2)-mean(crouch,4,1,2))<.1)throw new AssertionError("Crouch does not tilt attachment with torso");
        report.addProperty("crouch_follows_torso_bone",true);
        var localChest=mc.player.getItemBySlot(EquipmentSlot.CHEST);
        try {
            mc.player.setItemSlot(EquipmentSlot.CHEST,new ItemStack(ModItems.DIVINE_BLESSING_CHESTPLATE.get()));
            var localSpy=new Spy();
            ((PlayerRenderer)mc.getEntityRenderDispatcher().getRenderer(mc.player)).render(mc.player,180,0,new PoseStack(),localSpy,LightTexture.FULL_BRIGHT);
            centroid(localSpy.center);
            if(mc.options.getCameraType()!=CameraType.FIRST_PERSON)throw new AssertionError("Expected first-person camera for local paper doll test");
        } finally {mc.player.setItemSlot(EquipmentSlot.CHEST,localChest);}
        report.addProperty("first_person_camera_does_not_hide_paper_doll_decoration",true);
        actor.setItemSlot(EquipmentSlot.CHEST,ItemStack.EMPTY);
        if(!render(mc,180,false,0,0,0).isEmpty())throw new AssertionError("Halo remains after unequipping");
        actor.setItemSlot(EquipmentSlot.CHEST,new ItemStack(Items.DIAMOND_CHESTPLATE));
        if(!render(mc,180,false,0,0,0).isEmpty())throw new AssertionError("Halo leaks to other chest armor");
        report.addProperty("unequipped_and_other_armor_hidden",true);
        actor.setItemSlot(EquipmentSlot.CHEST,new ItemStack(ModItems.DIVINE_BLESSING_CHESTPLATE.get())); pose(180,false);
    }

    private static void layerCount(LivingEntityRenderer<?,?> renderer) {
        try {
            for(var field:LivingEntityRenderer.class.getDeclaredFields()) if(List.class.isAssignableFrom(field.getType())) {
                field.setAccessible(true);
                long n=((List<?>)field.get(renderer)).stream().filter(x->x instanceof DivineBlessingHaloRenderer).count();
                if(n!=1)throw new AssertionError("Expected one halo layer, got "+n); return;
            }
            throw new AssertionError("No renderer layer list");
        } catch(ReflectiveOperationException e){throw new RuntimeException(e);}
    }
    private static float[] centroid(List<float[]> vertices) {
        if(vertices.size()!=8)throw new AssertionError("Expected translucent+glow glyph quads, got "+vertices.size());
        float[] result=new float[3];for(var v:vertices)for(int i=0;i<3;i++)result[i]+=v[i]/vertices.size();return result;
    }
    private static float mean(List<float[]> vertices,int uv,float value,int coordinate) {
        float sum=0;int n=0;for(var v:vertices)if(v[uv]==value){sum+=v[coordinate];n++;}return sum/n;
    }
    private static void close(float actual,float expected){if(Math.abs(actual-expected)>.001)throw new AssertionError("Transform mismatch: "+actual+" != "+expected);}
    private static void capture(String name) {
        var mc=Minecraft.getInstance();Screenshot.grab(mc.gameDirectory,name,mc.getMainRenderTarget(),m->{});
        int error=org.lwjgl.opengl.GL11.glGetError();if(error!=0)throw new AssertionError("Halo GL error "+error);
    }
    private static final class Spy implements MultiBufferSource,VertexConsumer {
        final List<float[]> center=new ArrayList<>(); boolean capture;float x,y,z,u,v;
        public VertexConsumer getBuffer(RenderType type){capture=type.toString().contains("divine_blessing_halo_center.png");return this;}
        public VertexConsumer vertex(double x,double y,double z){this.x=(float)x;this.y=(float)y;this.z=(float)z;return this;}
        public VertexConsumer color(int r,int g,int b,int a){return this;}
        public VertexConsumer uv(float u,float v){this.u=u;this.v=v;return this;}
        public VertexConsumer overlayCoords(int u,int v){return this;}
        public VertexConsumer uv2(int u,int v){return this;}
        public VertexConsumer normal(float x,float y,float z){return this;}
        public void endVertex(){if(capture)center.add(new float[]{x,y,z,u,v});}
        public void defaultColor(int r,int g,int b,int a){}
        public void unsetDefaultColor(){}
    }
}
