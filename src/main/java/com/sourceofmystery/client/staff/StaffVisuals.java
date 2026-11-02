package com.sourceofmystery.client.staff;

import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Axis;
import com.sourceofmystery.SourceOfMystery;
import com.sourceofmystery.client.StaffMagicGeometry;
import com.sourceofmystery.mixin.PostChainAccess;
import com.sourceofmystery.network.StaffEffectPacket;
import com.sourceofmystery.staff.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.*;
import net.minecraft.client.resources.sounds.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.*;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.*;

@Mod.EventBusSubscriber(modid="sourceofmystery",value=Dist.CLIENT)
public final class StaffVisuals {
    private static final List<Effect> EFFECTS=new ArrayList<>();
    private static final Map<UUID,SoundInstance> CHANTS=new HashMap<>();
    private static ClientLevel world;
    private static UUID timeCaster;
    private static int timeLeft,timeAge;
    private static PostChain timeShader;
    private static int shaderWidth,shaderHeight;
    private static boolean shaderFailed;
    private static MultiBufferSource.BufferSource buffers;
    private static final class Effect {final StaffEffectPacket data;int age;Effect(StaffEffectPacket data){this.data=data;}}
    private StaffVisuals() {}
    public static boolean timeStopped(){return timeLeft>0;}
    public static boolean frozen(Entity entity){return timeStopped()&&!entity.getUUID().equals(timeCaster);}
    public static float chargeAge(net.minecraft.world.item.ItemStack stack,StaffSpell spell,float partial) {
        long itemId=software.bernie.geckolib.animatable.GeoItem.getId(stack);
        if(itemId<0||world==null)return -1;
        for(Effect effect:EFFECTS)if(effect.data.kind()==StaffEffectPacket.CHARGE&&effect.data.variant()==spell.ordinal()) {
            var caster=world.getPlayerByUUID(effect.data.caster());if(caster==null)continue;
            for(var hand:net.minecraft.world.InteractionHand.values())
                if(software.bernie.geckolib.animatable.GeoItem.getId(caster.getItemInHand(hand))==itemId)
                    return effect.age+(paused(effect)?0:partial);
        }
        return -1;
    }
    public static void accept(StaffEffectPacket packet) {
        var client=Minecraft.getInstance();if(client.level==null)return;
        if(world!=client.level){reset();world=client.level;}
        if(packet.kind()==StaffEffectPacket.STOP) {
            timeCaster=packet.caster();timeLeft=packet.duration();timeAge=0;
            shaderFailed=false;
            if(timeLeft>0)for(Entity entity:client.level.entitiesForRendering())if(frozen(entity)) {
                entity.xOld=entity.xo=entity.getX();entity.yOld=entity.yo=entity.getY();entity.zOld=entity.zo=entity.getZ();
                entity.xRotO=entity.getXRot();entity.yRotO=entity.getYRot();
            }
            return;
        }
        if(packet.kind()==StaffEffectPacket.CANCEL) {
            EFFECTS.removeIf(e->e.data.kind()==StaffEffectPacket.CHARGE&&e.data.caster().equals(packet.caster()));
            SoundInstance sound=CHANTS.remove(packet.caster());if(sound!=null)client.getSoundManager().stop(sound);return;
        }
        if(packet.kind()==StaffEffectPacket.LIFT&&packet.duration()==0) {
            EFFECTS.removeIf(e->e.data.kind()==packet.kind()&&e.data.origin().equals(packet.origin()));return;
        }
        EFFECTS.add(new Effect(packet));
        if(packet.kind()==StaffEffectPacket.CHARGE) {
            StaffSpell spell=StaffSpell.values()[packet.variant()];
            SoundInstance sound=new SimpleSoundInstance(StaffSounds.event(spell,"charge"),SoundSource.PLAYERS,1,1,
                    RandomSource.create(),packet.origin().x,packet.origin().y,packet.origin().z);
            SoundInstance old=CHANTS.put(packet.caster(),sound);if(old!=null)client.getSoundManager().stop(old);
            client.getSoundManager().play(sound);
        }
    }
    public static void tick() {
        var client=Minecraft.getInstance();
        if(world!=client.level){reset();world=client.level;}
        if(client.isPaused())return;
        for(Effect effect:EFFECTS)if(!paused(effect))effect.age++;
        EFFECTS.removeIf(e->e.age>e.data.duration()&&e.data.kind()!=StaffEffectPacket.LIFT);
        if(timeLeft>0){timeLeft=Math.max(1,timeLeft-1);timeAge++;}
        if(timeLeft==0&&timeShader!=null){timeShader.close();timeShader=null;shaderWidth=shaderHeight=0;}
    }
    private static boolean paused(Effect effect) {
        return timeStopped()&&!(effect.data.kind()==StaffEffectPacket.CHARGE&&effect.data.caster().equals(timeCaster));
    }
    private static void reset() {
        for(SoundInstance sound:CHANTS.values())Minecraft.getInstance().getSoundManager().stop(sound);
        CHANTS.clear();EFFECTS.clear();timeLeft=0;timeCaster=null;
        if(timeShader!=null){timeShader.close();timeShader=null;}
        shaderWidth=shaderHeight=0;shaderFailed=false;
    }
    @SubscribeEvent public static void input(InputEvent.InteractionKeyMappingTriggered event) {
        var player=Minecraft.getInstance().player;
        if(player!=null&&frozen(player)){event.setCanceled(true);event.setSwingHand(false);}
    }
    @SubscribeEvent public static void render(RenderLevelStageEvent event) {
        if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_ENTITIES)return;
        var client=Minecraft.getInstance();if(client.level==null)return;
        if(buffers==null)buffers=MultiBufferSource.immediate(new BufferBuilder(1<<20));
        Vec3 camera=event.getCamera().getPosition();PoseStack poses=event.getPoseStack();
        for(Effect effect:EFFECTS) {
            StaffEffectPacket data=effect.data;float age=effect.age+(paused(effect)?0:event.getPartialTick());
            Vec3 origin=data.origin();
            if(data.kind()==StaffEffectPacket.CHARGE) {
                var caster=client.level.getPlayerByUUID(data.caster());if(caster==null)continue;
                origin=caster.getPosition(event.getPartialTick()).add(0,2.2,0);
            }
            poses.pushPose();poses.translate(origin.x-camera.x,origin.y-camera.y,origin.z-camera.z);
            if(data.kind()==StaffEffectPacket.CHARGE) {
                if(data.variant()==StaffSpell.ANNIHILATION.ordinal())StaffMagicGeometry.ritual(poses,buffers,age/20f,(float)(255-origin.y));
            } else if(data.kind()==StaffEffectPacket.SPLIT&&data.variant()==1) {
                Vec3 midpoint=data.target().subtract(data.origin()).scale(.5);poses.translate(midpoint.x,0,midpoint.z);
                poses.pushPose();poses.translate(0,110,0);StaffMagicGeometry.portal(poses,buffers,age,140,0,100,100,true);poses.popPose();
                StaffMagicGeometry.blackSword(poses,buffers,age);
            } else if(data.kind()==StaffEffectPacket.RIFT) {
                poses.mulPose(Axis.YP.rotation((float)Math.atan2(data.target().x,data.target().z)));
                poses.translate(0,2,0);StaffMagicGeometry.portal(poses,buffers,age,data.duration(),data.variant(),3.1f,4.1f,false);
            } else if(data.kind()==StaffEffectPacket.LIFT) {
                double progress=com.sourceofmystery.entity.OutsiderRitual.smooth(age/100.0);
                poses.translate(0,(data.target().y-data.origin().y)*progress,0);
                for(int i=0;i+1<data.blocks().length;i+=2) {
                    int packed=data.blocks()[i];poses.pushPose();poses.translate(packed&15,packed>>>8,(packed>>>4)&15);
                    client.getBlockRenderer().renderSingleBlock(Block.stateById(data.blocks()[i+1]),poses,buffers,15728880,net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY);
                    poses.popPose();
                }
            }
            poses.popPose();
        }
        buffers.endBatch();
    }
    @SubscribeEvent public static void post(RenderGuiEvent.Pre event) {
        if(!timeStopped()||shaderFailed)return;
        var client=Minecraft.getInstance();
        try {
            if(timeShader==null)timeShader=new PostChain(client.getTextureManager(),client.getResourceManager(),client.getMainRenderTarget(),
                    new ResourceLocation("sourceofmystery","shaders/post/time_stop.json"));
            int width=client.getWindow().getWidth(),height=client.getWindow().getHeight();
            if(shaderWidth!=width||shaderHeight!=height){timeShader.resize(width,height);shaderWidth=width;shaderHeight=height;}
            ((PostChainAccess)(Object)timeShader).som$passes().get(0).getEffect().safeGetUniform("Elapsed").set((timeAge+event.getPartialTick())/20f);
            timeShader.process(event.getPartialTick());client.getMainRenderTarget().bindWrite(false);
        } catch(Exception exception) {
            SourceOfMystery.LOGGER.error("Cannot render staff time-stop shader",exception);
            if(timeShader!=null)timeShader.close();timeShader=null;
            shaderFailed=true;
        }
    }
}
