package com.sourceofmystery.client;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.mojang.authlib.GameProfile;
import com.sourceofmystery.SourceOfMystery;
import com.sourceofmystery.item.*;
import net.minecraft.client.*;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderNameTagEvent;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod;
import software.bernie.geckolib.renderer.GeoArmorRenderer;
import software.bernie.geckolib.renderer.GeoItemRenderer;
import java.nio.file.Files;
import java.util.*;

/** Production renderers, native first/third person, inventory, dropped items and posed armor. */
@Mod.EventBusSubscriber(modid="sourceofmystery",value=Dist.CLIENT)
public final class EquipmentVisualSmokeCheck {
    private static int ticks,phase,wait,index;
    private static volatile boolean ready;
    private static final List<RemotePlayer> actors=new ArrayList<>();
    private static Item[] armor,swords;
    private static final JsonObject report=new JsonObject();
    @SubscribeEvent public static void names(RenderNameTagEvent e){if(Boolean.getBoolean("sourceofmystery.equipmentCheck"))e.setResult(Event.Result.DENY);}
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event){
        if(!Boolean.getBoolean("sourceofmystery.equipmentCheck")||event.phase!=TickEvent.Phase.END)return;
        var mc=Minecraft.getInstance();if(mc.player==null||mc.level==null||mc.getSingleplayerServer()==null)return;
        ticks++;wait++;
        if(phase==0&&ticks>100){
            armor=new Item[]{ModItems.MYSTERY_CHESTPLATE.get(),ModItems.SPIRIT_SOURCE_CHESTPLATE.get(),ModItems.DARK_SOURCE_CHESTPLATE.get(),ModItems.ORIGIN_DRAGON_CHESTPLATE.get()};
            swords=new Item[]{ModItems.MYSTERY_SWORD.get(),ModItems.SPIRIT_SOURCE_SWORD.get(),ModItems.DARK_SOURCE_SWORD.get(),ModItems.ORIGIN_DRAGON_SWORD.get(),ModItems.DIVINE_PUNISHMENT_SWORD.get()};
            mc.setScreen(null);mc.options.hideGui=true;mc.options.setCameraType(CameraType.FIRST_PERSON);mc.options.fov().set(55);
            mc.getSingleplayerServer().execute(()->{
                var l=mc.getSingleplayerServer().overworld();l.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,l.getServer());l.setDayTime(6500);
                for(int x=-12;x<=12;x++)for(int z=-7;z<=12;z++)l.setBlock(new BlockPos(x,199,z),Blocks.SMOOTH_QUARTZ.defaultBlockState(),2);
                var p=l.getServer().getPlayerList().getPlayers().get(0);p.setGameMode(GameType.CREATIVE);p.getInventory().clearContent();p.teleportTo(l,.5,200,9.5,180,7);p.setNoGravity(true);ready=true;
            });phase=1;wait=0;
        }else if(phase==1&&ready&&wait>40){
            for(int i=0;i<5;i++){
                var a=new RemotePlayer(mc.level,new GameProfile(UUID.nameUUIDFromBytes(("Equipment"+i).getBytes()),"Tier"+i));a.setId(-8100-i);a.setPos((i-2)*3.0,200,0);
                a.setNoGravity(true);a.setItemSlot(EquipmentSlot.CHEST,new ItemStack(armor[Math.min(i,3)]));a.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(swords[i]));
                mc.level.addPlayer(a.getId(),a);actors.add(a);
            }
            checks(mc);camera(new Vec3(8.2,203.0,12),new Vec3(0,201,0));phase=2;wait=0;
        }else if(phase==2&&wait>25){capture("01-front-lineup");camera(new Vec3(7,203.6,-10),new Vec3(0,201,0));phase=3;wait=0;
        }else if(phase==3&&wait>25){capture("02-back-lineup");camera(new Vec3(2.8,203.5,3.3),new Vec3(0,201,0));phase=4;wait=0;
        }else if(phase==4&&wait>25){
            capture("03-crouched-shoulder");mc.setCameraEntity(mc.player);mc.options.hideGui=false;index=0;hand(0);phase=5;wait=0;
        }else if(phase==5&&wait>22){
            capture("04-first-"+index);index++;wait=0;
            if(index<5)hand(index);else{mc.player.setMainArm(HumanoidArm.LEFT);phase=6;}
        }else if(phase==6&&wait>22){
            capture("05-left-hand");mc.player.setMainArm(HumanoidArm.RIGHT);
            mc.getSingleplayerServer().execute(()->{
                var p=mc.getSingleplayerServer().getPlayerList().getPlayers().get(0);
                for(int i=0;i<5;i++)p.getInventory().setItem(i,new ItemStack(swords[i]));
                for(int i=0;i<4;i++)p.getInventory().setItem(i+5,new ItemStack(armor[i]));
                p.getInventory().selected=8;p.setItemSlot(EquipmentSlot.CHEST,new ItemStack(armor[0]));
            });phase=7;wait=0;
        }else if(phase==7&&wait>24){
            mc.setScreen(new InventoryScreen(mc.player));phase=8;wait=0;
        }else if(phase==8&&wait>24){
            capture("06-inventory-3d");mc.setScreen(null);mc.options.hideGui=true;
            mc.getSingleplayerServer().execute(()->{
                var l=mc.getSingleplayerServer().overworld();
                List<Item> all=new ArrayList<>(List.of(swords));all.addAll(List.of(armor));
                for(int i=0;i<all.size();i++){var drop=new ItemEntity(l,(i-4)*.85,200.55,-4,new ItemStack(all.get(i)));drop.setNoGravity(true);drop.setDeltaMovement(Vec3.ZERO);l.addFreshEntity(drop);}
            });camera(new Vec3(3.4,202.8,1.5),new Vec3(0,200.6,-4));phase=9;wait=0;
        }else if(phase==9&&wait>30){
            capture("07-dropped-items");mc.getSingleplayerServer().execute(()->mc.getSingleplayerServer().overworld().setDayTime(18000));camera(new Vec3(8.2,202.6,12),new Vec3(0,201,0));phase=10;wait=0;
        }else if(phase==10&&wait>35){
            capture("08-night-inlays");report.addProperty("real_forge_client",true);report.addProperty("armor_models",4);report.addProperty("sword_models",5);
            report.addProperty("native_first_third_inventory_ground_rendered",true);report.addProperty("left_and_right_hands_rendered",true);
            report.addProperty("test_master_volume",mc.options.getSoundSourceVolume(net.minecraft.sounds.SoundSource.MASTER));
            if(mc.options.getSoundSourceVolume(net.minecraft.sounds.SoundSource.MASTER)!=0)throw new AssertionError("Test must be silent");
            try{Files.writeString(mc.gameDirectory.toPath().resolve("equipment-verification.json"),new GsonBuilder().setPrettyPrinting().create().toJson(report));}catch(Exception e){throw new RuntimeException(e);}
            SourceOfMystery.LOGGER.info("EQUIPMENT_GECKO_OK armor=4 swords=5 silent=true");phase=11;mc.stop();
        }
        for(var a:actors){
            a.setDeltaMovement(Vec3.ZERO);a.setYRot(0);a.yRotO=0;a.setYBodyRot(0);a.yBodyRotO=0;a.setYHeadRot(0);a.yHeadRotO=0;a.setXRot(0);a.xRotO=0;
            boolean crouch=phase==4&&a==actors.get(2);a.setShiftKeyDown(crouch);a.setPose(crouch?Pose.CROUCHING:Pose.STANDING);
            a.walkAnimation.update(phase==4?.65f:0,.4f);
        }
        if(ticks>1500)throw new AssertionError("Equipment fixture timeout "+phase);
    }
    private static void hand(int i){var mc=Minecraft.getInstance();mc.getSingleplayerServer().execute(()->{
        var p=mc.getSingleplayerServer().getPlayerList().getPlayers().get(0);p.getInventory().selected=0;p.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(swords[i]));
    });}
    private static void camera(Vec3 eye,Vec3 target){
        var mc=Minecraft.getInstance();var camera=new Marker(EntityType.MARKER,mc.level);camera.setPos(eye);camera.xo=camera.xOld=eye.x;camera.yo=camera.yOld=eye.y;camera.zo=camera.zOld=eye.z;
        Vec3 d=target.subtract(eye);float yaw=(float)(Mth.atan2(d.z,d.x)*Mth.RAD_TO_DEG)-90,pitch=(float)(-Mth.atan2(d.y,d.horizontalDistance())*Mth.RAD_TO_DEG);
        camera.setYRot(yaw);camera.yRotO=yaw;camera.setXRot(pitch);camera.xRotO=pitch;mc.setCameraEntity(camera);
    }
    @SuppressWarnings("unchecked") private static void checks(Minecraft mc){
        List<Item> all=new ArrayList<>(List.of(swords));all.addAll(List.of(armor));
        for(var item:all)if(!mc.getItemRenderer().getModel(new ItemStack(item),mc.level,mc.player,0).isCustomRenderer())throw new AssertionError("No custom item model: "+item);
        report.addProperty("all_nine_item_models_use_custom_renderer",true);
        for(var item:armor){
            var geo=(TieredGeckoChestplateItem)item;var ext=IClientItemExtensions.of(item);
            var worn=(GeoArmorRenderer<TieredGeckoChestplateItem>)ext.getHumanoidArmorModel(actors.get(0),new ItemStack(item),EquipmentSlot.CHEST,new HumanoidModel<>(mc.getEntityModels().bakeLayer(ModelLayers.PLAYER)));
            var held=(GeoItemRenderer<TieredGeckoChestplateItem>)ext.getCustomRenderer();
            var w=worn.getGeoModel().getBakedModel(worn.getGeoModel().getModelResource(geo));var h=held.getGeoModel().getBakedModel(held.getGeoModel().getModelResource(geo));
            if(w==h)throw new AssertionError("Worn and displayed models share mutable bone cache");
        }
        report.addProperty("worn_and_displayed_bone_caches_isolated",true);
    }
    private static void capture(String name){var mc=Minecraft.getInstance();Screenshot.grab(mc.gameDirectory,name+".png",mc.getMainRenderTarget(),x->{});}
}
