package com.sourceofmystery.client;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.sourceofmystery.entity.OutsiderSpell;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Large sky effects cannot depend on their empty Y=255 section being compiled. */
@Mod.EventBusSubscriber(modid="sourceofmystery",value=Dist.CLIENT)
public final class OutsiderHighSpellEffects {
    private static MultiBufferSource.BufferSource buffers;
    private OutsiderHighSpellEffects(){}

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event){
        if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_ENTITIES)return;
        var level=Minecraft.getInstance().level;if(level==null)return;
        if(buffers==null)buffers=MultiBufferSource.immediate(new BufferBuilder(65536));
        try{
            renderTracked(level.entitiesForRendering(),event.getCamera().getPosition(),event.getPartialTick(),event.getPoseStack(),buffers);
        }finally{buffers.endBatch();}
    }

    static void renderTracked(Iterable<? extends Entity> entities,Vec3 camera,float partial,PoseStack poses,MultiBufferSource target){
        for(Entity entity:entities){
            if(!(entity instanceof OutsiderSpell spell)||spell.isRemoved()||!OutsiderSpellRenderer.usesWorldPass(spell.kind()))continue;
            Vec3 exact=spell.trajectoryPosition(partial);
            double dx=exact.x-camera.x,dz=exact.z-camera.z;
            // Tracking already limits horizontal reach. Height alone must not
            // hide a Y=255 seal from a player in a negative-Y flat world.
            if(dx*dx+dz*dz>384*384)continue;
            poses.pushPose();
            try{
                poses.translate(dx,exact.y-camera.y,dz);
                OutsiderSpellRenderer.renderHighEffect(spell,partial,poses,target);
            }finally{poses.popPose();}
        }
    }
}
