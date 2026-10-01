package com.sourceofmystery.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.sourceofmystery.entity.GiantWither;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.WitherBossRenderer;
import net.minecraft.world.entity.boss.wither.WitherBoss;

/**
 * 原版凋灵渲染器，整体放大 GiantWither.SCALE 倍
 */
public class GiantWitherRenderer extends WitherBossRenderer {

    public GiantWitherRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 1.0f * GiantWither.SCALE;
    }

    @Override
    protected void scale(WitherBoss entity, PoseStack poseStack, float partialTick) {
        super.scale(entity, poseStack, partialTick);
        poseStack.scale(GiantWither.SCALE, GiantWither.SCALE, GiantWither.SCALE);
    }
}
