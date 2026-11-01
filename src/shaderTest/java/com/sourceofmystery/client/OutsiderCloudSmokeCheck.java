package com.sourceofmystery.client;

import com.sourceofmystery.SourceOfMystery;
import com.sourceofmystery.entity.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Separate empty-world regression: real player viewpoints below and above clouds. */
@Mod.EventBusSubscriber(modid="sourceofmystery",value=Dist.CLIENT)
public final class OutsiderCloudSmokeCheck {
    private static int ticks;private static volatile boolean ready;
    private static OutsiderEntity owner;private static OutsiderSpell sky;
    private static void server(Runnable action){Minecraft.getInstance().getSingleplayerServer().execute(action);}
    private static void camera(double y,double z,float pitch){
        var s=Minecraft.getInstance().getSingleplayerServer();var p=s.getPlayerList().getPlayers().get(0);
        p.teleportTo(s.overworld(),0,y,z,180,pitch);
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event){
        if(!Boolean.getBoolean("sourceofmystery.cloudSmoke")||event.phase!=TickEvent.Phase.END)return;
        var mc=Minecraft.getInstance();if(mc.level==null||mc.player==null||mc.getSingleplayerServer()==null)return;
        try{
            if(ticks==0){ticks=1;server(()->{try{
                var l=mc.getSingleplayerServer().overworld();l.setDayTime(6000);l.setWeatherParameters(100000,0,false,false);
                l.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false,l.getServer());
                l.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,l.getServer());
                l.getGameRules().getRule(GameRules.RULE_MOBGRIEFING).set(false,l.getServer());
                for(var e:com.google.common.collect.Lists.newArrayList(l.getAllEntities()))if(!(e instanceof net.minecraft.server.level.ServerPlayer))e.discard();
                var p=l.getServer().getPlayerList().getPlayers().get(0);p.setGameMode(GameType.CREATIVE);p.getAbilities().flying=true;p.onUpdateAbilities();camera(64,100,-60);
                owner=new OutsiderEntity(ModEntities.OUTSIDER.get(),l);owner.setPos(0,80,0);l.addFreshEntity(owner);
                state(OutsiderEntity.GROUND,0);
                for(String name:new String[]{"cooldown","groundTime"}){var f=OutsiderEntity.class.getDeclaredField(name);f.setAccessible(true);f.setInt(owner,100000);}
                sky=OutsiderSpell.spawn(l,owner,OutsiderSpell.SKY_SEAL,new Vec3(0,255,0),Vec3.ZERO);ready=true;
            }catch(Throwable error){error.printStackTrace();System.exit(1);}});return;}
            if(!ready)return;ticks++;
            // Hold the same fully opened seal through all views, independent
            // of loading time and screenshots. Only the isolated test is frozen.
            server(()->{try{setAge(sky,40);}catch(Exception error){throw new RuntimeException(error);}});
            if(ticks==100){if(!OutsiderShaderCompat.active())throw new AssertionError("Shader pack is disabled");capture("01-sky-ground.png");}
            if(ticks==110)server(()->camera(-60,40,-82));
            if(ticks==140)capture("02-sky-negative-y.png");
            if(ticks==150){mc.options.renderDistance().set(8);mc.levelRenderer.allChanged();}
            if(ticks==190)capture("03-sky-distance8.png");
            if(ticks==200){mc.options.renderDistance().set(18);mc.levelRenderer.allChanged();server(()->camera(430,220,38));}
            if(ticks==240)capture("04-sky-above.png");
            if(ticks==250)server(()->{try{sky.discard();owner.setPos(0,100,0);state(OutsiderEntity.METEOR,240);camera(72,36,-45);}catch(Exception error){throw new RuntimeException(error);}});
            if(ticks>=250)server(()->{try{setAge(owner,240);}catch(Exception error){throw new RuntimeException(error);}});
            if(ticks==290)capture("05-near-below-clouds.png");
            if(ticks==300)server(()->{for(int x=-6;x<=6;x++)for(int y=92;y<=120;y++)Minecraft.getInstance().getSingleplayerServer().overworld().setBlock(new BlockPos(x,y,12),Blocks.STONE.defaultBlockState(),2);});
            if(ticks==330)capture("06-terrain-occlusion.png");
            if(ticks==350){SourceOfMystery.LOGGER.info("CLOUD_SMOKE_OK shader_pack_active=true sky_y=255 views=ground,negative_y,distance8,above,near,wall");mc.stop();}
        }catch(Throwable error){error.printStackTrace();System.exit(1);}
    }
    private static void state(int kind,int age)throws ReflectiveOperationException{
        var m=OutsiderEntity.class.getDeclaredMethod("state",int.class);m.setAccessible(true);m.invoke(owner,kind);setAge(owner,age);
    }
    @SuppressWarnings("unchecked") private static void setAge(net.minecraft.world.entity.Entity e,int age)throws ReflectiveOperationException{
        var f=e.getClass().getDeclaredField("START");f.setAccessible(true);
        e.getEntityData().set((net.minecraft.network.syncher.EntityDataAccessor<Long>)f.get(null),e.level().getGameTime()-age);
    }
    private static void capture(String name){
        var m=Minecraft.getInstance();var tracked=sky==null?null:m.level.getEntity(sky.getId());
        SourceOfMystery.LOGGER.info("CLOUD_VIEW {} camera={} sky_client={} sky_position={} sky_age={}",name,m.gameRenderer.getMainCamera().getPosition(),tracked!=null,
                tracked==null?null:tracked.position(),tracked instanceof OutsiderSpell s?s.age(0):-1);
        Screenshot.grab(m.gameDirectory,name,m.getMainRenderTarget(),text->SourceOfMystery.LOGGER.info("Cloud screenshot {}",text.getString()));
        if(name.equals("03-sky-distance8.png"))checkCloudPrecision();
        int error=org.lwjgl.opengl.GL11.glGetError();if(error!=0)throw new AssertionError("OpenGL error "+error);
    }
    private static void checkCloudPrecision(){
        try{
            var method=Class.forName("com.sourceofmystery.client.OutsiderRitualComposite$Targets").getDeclaredMethod("texture");method.setAccessible(true);
            int texture=(int)method.invoke(null),previous=org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL11.GL_TEXTURE_BINDING_2D);
            com.mojang.blaze3d.systems.RenderSystem.bindTexture(texture);
            try{
                int format=org.lwjgl.opengl.GL11.glGetTexLevelParameteri(org.lwjgl.opengl.GL11.GL_TEXTURE_2D,0,org.lwjgl.opengl.GL11.GL_TEXTURE_INTERNAL_FORMAT);
                if(format!=org.lwjgl.opengl.GL30.GL_RGBA16F)throw new AssertionError("Cloud depth was clamped to normalized color storage");
                int w=org.lwjgl.opengl.GL11.glGetTexLevelParameteri(org.lwjgl.opengl.GL11.GL_TEXTURE_2D,0,org.lwjgl.opengl.GL11.GL_TEXTURE_WIDTH);
                int h=org.lwjgl.opengl.GL11.glGetTexLevelParameteri(org.lwjgl.opengl.GL11.GL_TEXTURE_2D,0,org.lwjgl.opengl.GL11.GL_TEXTURE_HEIGHT);
                var data=org.lwjgl.system.MemoryUtil.memAllocFloat(w*h*4);
                try{
                    org.lwjgl.opengl.GL11.glGetTexImage(org.lwjgl.opengl.GL11.GL_TEXTURE_2D,0,org.lwjgl.opengl.GL11.GL_RGBA,org.lwjgl.opengl.GL11.GL_FLOAT,data);
                    float maximum=0;for(int i=3;i<data.limit();i+=4)maximum=Math.max(maximum,data.get(i));
                    if(maximum<1.1f)throw new AssertionError("Distant cloud distance lost at 8 chunks");
                    SourceOfMystery.LOGGER.info("RITUAL_CLOUD_DEPTH_OK format=RGBA16F maximum={}",maximum);
                }finally{org.lwjgl.system.MemoryUtil.memFree(data);}
            }finally{com.mojang.blaze3d.systems.RenderSystem.bindTexture(previous);}
        }catch(ReflectiveOperationException error){throw new RuntimeException(error);}
    }
}
