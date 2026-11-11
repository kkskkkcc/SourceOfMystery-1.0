package com.sourceofmystery.client;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.sourceofmystery.network.EasternRitePacket;
import com.sourceofmystery.staff.EasternRite;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.*;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.*;

@Mod.EventBusSubscriber(modid="sourceofmystery",value=Dist.CLIENT)
public final class EasternVisuals {
    public record View(EasternRitePacket.View data,long stamp){
        public float age(float partial){return data.age()+elapsed()+partial;}
        public long elapsed(){return world==null?0:Math.max(0,world.getGameTime()-stamp);}
        public Vec3 forward(){return Vec3.directionFromRotation(data.pitch(),data.yaw()).normalize();}
        public Vec3 up(){return forward().cross(new Vec3(Math.cos(Math.toRadians(data.yaw())),0,Math.sin(Math.toRadians(data.yaw())))).normalize();}
        public double travel(float partial){return data.stoppedAt()>=0?data.travel():Math.min(data.range(),data.travel()+Math.max(0,age(partial)-Math.max(EasternRite.LAUNCH,data.age()))*data.speed());}
        public int end(){return data.stoppedAt()<0?EasternRite.LAUNCH+(int)Math.ceil(data.range()/data.speed())+30:data.stoppedAt()+30;}
    }
    private static ClientLevel world;
    private static final Map<UUID,View> RITES=new LinkedHashMap<>();
    private static final Set<UUID> CARRIED=new HashSet<>();
    private static MultiBufferSource.BufferSource buffers;
    private EasternVisuals(){}
    public static View local(){var p=Minecraft.getInstance().player;return p==null?null:RITES.values().stream().filter(v->v.data.owner().equals(p.getUUID())).findFirst().orElse(null);}
    public static Collection<View> views(){return List.copyOf(RITES.values());}
    /** Match the actual rendered GeckoLib item, rather than a world-space player offset. */
    public static float staffAge(net.minecraft.world.item.ItemStack stack,float partial){
        long id=software.bernie.geckolib.animatable.GeoItem.getId(stack);
        if(id<0||world==null)return -1;
        for(var view:RITES.values()){
            float age=view.age(partial);if(age>EasternRite.LAUNCH+20)continue;
            var owner=world.getPlayerByUUID(view.data.owner());if(owner==null)continue;
            for(var hand:net.minecraft.world.InteractionHand.values()){
                var held=owner.getItemInHand(hand);
                if(held.getItem() instanceof com.sourceofmystery.item.DimensionBreakerStaffItem
                        &&com.sourceofmystery.staff.StaffSpell.selected(held)==com.sourceofmystery.staff.StaffSpell.EASTERN_RITE
                        &&software.bernie.geckolib.animatable.GeoItem.getId(held)==id)return age;
            }
        }
        return -1;
    }
    public static boolean constrained(Entity entity){return CARRIED.contains(entity.getUUID());}
    public static void receive(EasternRitePacket p){var mc=Minecraft.getInstance();if(mc.level==null||!mc.level.dimension().location().equals(p.dimension()))return;
        if(world!=mc.level){reset();world=mc.level;}RITES.clear();CARRIED.clear();for(var data:p.rites()){RITES.put(data.token(),new View(data,world.getGameTime()));for(var c:data.carried())CARRIED.add(c.uuid());}
    }
    public static void reset(){RITES.clear();CARRIED.clear();world=null;}
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent e){if(e.phase!=TickEvent.Phase.END)return;var mc=Minecraft.getInstance();if(world!=mc.level){reset();world=mc.level;}if(world==null||mc.player==null||mc.isPaused())return;
        RITES.values().removeIf(v->v.age(0)>v.end());CARRIED.clear();for(var view:RITES.values())for(var c:view.data.carried()){if(view.data.stoppedAt()>=0)continue;CARRIED.add(c.uuid());var entity=world.getEntity(c.id());if(entity==null||!entity.getUUID().equals(c.uuid()))continue;var ball=view.data.origin().add(view.forward().scale(16+view.travel(0))).add(view.up().scale(8));entity.setPos(ball.add(c.offset()));entity.setDeltaMovement(Vec3.ZERO);entity.fallDistance=0;}

    }
    @SubscribeEvent public static void movement(MovementInputUpdateEvent e){if(!constrained(e.getEntity()))return;var i=e.getInput();i.forwardImpulse=i.leftImpulse=0;i.up=i.down=i.left=i.right=i.jumping=i.shiftKeyDown=false;}
    @SubscribeEvent public static void render(RenderLevelStageEvent e){
        if(e.getStage()!=RenderLevelStageEvent.Stage.AFTER_ENTITIES||world==null||OutsiderShaderCompat.shadowPass())return;var mc=Minecraft.getInstance();if(mc.level!=world)return;
        if(buffers==null)buffers=MultiBufferSource.immediate(new BufferBuilder(1<<23));var p=e.getPoseStack();var camera=e.getCamera().getPosition();float partial=e.getPartialTick();
        for(var view:RITES.values()){
            Vec3 origin=view.data.origin();if(camera.distanceToSqr(origin)>Math.pow(view.data.range()+180,2))continue;
            p.pushPose();p.translate(origin.x-camera.x,origin.y-camera.y,origin.z-camera.z);EasternGeometry.render(p,buffers,view,partial,camera);p.popPose();
        }buffers.endBatch();
    }
}
