package com.sourceofmystery.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.sourceofmystery.entity.WitherHusk;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.WitherBossModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.boss.wither.WitherBoss;

/**
 * 凋灵躯壳：原版凋灵模型，2 秒内全身变白（用苦力怕闪白同款的白色覆盖层）、逐渐变亮，同时越转越快
 */
public class WitherHuskRenderer extends EntityRenderer<WitherHusk> {

    private static final ResourceLocation TEXTURE = new ResourceLocation("textures/entity/wither/wither.png");
    private final WitherBossModel<WitherBoss> model;
    private WitherBoss dummy;

    public WitherHuskRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.model = new WitherBossModel<>(context.bakeLayer(ModelLayers.WITHER));
    }

    @Override
    public void render(WitherHusk husk, float yaw, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light) {
        float white = husk.whiteness(partialTick);
        float age = husk.tickCount + partialTick;
        if (dummy == null || dummy.level() != husk.level()) {
            dummy = new WitherBoss(EntityType.WITHER, Minecraft.getInstance().level);
        }
        model.setupAnim(dummy, 0, 0, age, 0, 0);

        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0f - husk.getYRot() - husk.spin(partialTick)));
        poseStack.scale(-1.0f, -1.0f, 1.0f);
        poseStack.scale(2.0f, 2.0f, 2.0f);
        poseStack.translate(0.0f, -1.501f, 0.0f);
        int overlay = OverlayTexture.pack(OverlayTexture.u(white), OverlayTexture.v(false));
        int lit = white > 0.5f ? LightTexture.FULL_BRIGHT : light;
        VertexConsumer consumer = buffers.getBuffer(model.renderType(TEXTURE));
        model.renderToBuffer(poseStack, consumer, lit, overlay, 1.0f, 1.0f, 1.0f, 1.0f);
        poseStack.popPose();
        super.render(husk, yaw, partialTick, poseStack, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(WitherHusk husk) {
        return TEXTURE;
    }
}
