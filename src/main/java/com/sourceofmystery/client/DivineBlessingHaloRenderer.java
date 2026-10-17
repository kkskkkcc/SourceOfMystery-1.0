package com.sourceofmystery.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.sourceofmystery.SourceOfMystery;
import com.sourceofmystery.item.MysteryArmorMaterial;
import com.sourceofmystery.item.MysteryChestplateItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * 神威天佑胸甲背后的光环特效。
 *
 * 实现细节（参照 WeepingDeathLordRenderer.renderHalo 的写法）：
 *   - 四张带纹路的黑白神环贴图，分别缩放并反向交替旋转
 *   - 中心使用独立的白色“神”字贴图，使用实体贴图而不是粒子
 *   - RenderType.entityTranslucent（基底）+ RenderType.eyes（发光层）
 *   - 位置：玩家上半身背后、胸口高度
 *   - 脱下胸甲后不再进入渲染分支，神环立即消失
 *
 * 使用 Forge 的玩家专用 RenderPlayerEvent，避免 RenderLivingEvent 泛型事件在运行时擦除后无法匹配。
 * 客户端模组事件中仍通过 MinecraftForge.EVENT_BUS 显式注册本类。
 */
public class DivineBlessingHaloRenderer {

    static {
        SourceOfMystery.LOGGER.info("[Halo] CLASS LOADED at tick={}", System.currentTimeMillis());
    }

    private static final ResourceLocation[] RING_TEXTURES = {
            texture("divine_blessing_halo_ring_0.png"),
            texture("divine_blessing_halo_ring_1.png"),
            texture("divine_blessing_halo_ring_2.png"),
            texture("divine_blessing_halo_ring_3.png")
    };
    private static final ResourceLocation CENTER_TEXTURE = texture("divine_blessing_halo_center.png");
    private static final float[] RING_RADII = {0.78f, 0.66f, 0.54f, 0.42f};
    private static final float[] RING_SPEEDS = {1.45f, -1.10f, 0.82f, -0.60f};
    private static final float BACK_OFFSET = 0.65f;

    private static ResourceLocation texture(String fileName) {
        return new ResourceLocation(SourceOfMystery.MOD_ID, "textures/entity/" + fileName);
    }

    @SubscribeEvent
    public static void onRenderPlayer(RenderPlayerEvent.Post event) {
        try {
            Player player = event.getEntity();

            ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST);
            if (!(chest.getItem() instanceof MysteryChestplateItem armor)) return;
            if (armor.getMaterial() != MysteryArmorMaterial.DIVINE_BLESSING) return;

            Minecraft mc = Minecraft.getInstance();
            if (mc.options.getCameraType().isFirstPerson() && mc.player == player) return;

            PoseStack poseStack = event.getPoseStack();
            MultiBufferSource buffers = event.getMultiBufferSource();

            float time = player.tickCount + event.getPartialTick();
            PlayerModel<?> model = event.getRenderer().getModel();

            poseStack.pushPose();
            // RenderPlayerEvent 的矩阵已经包含玩家位置和身体朝向，只需要挂到身体骨骼背面。
            model.body.translateAndRotate(poseStack);
            // 身体骨骼原点在腰部附近；正 Y 在玩家模型的倒置坐标中向上，把神环放到背部胸口。
            poseStack.translate(0.0f, 1.0f, BACK_OFFSET);

            for (int i = 0; i < RING_TEXTURES.length; i++) {
                poseStack.pushPose();
                poseStack.translate(0.0f, 0.0f, 0.015f * i);
                poseStack.mulPose(Axis.ZP.rotationDegrees(time * RING_SPEEDS[i]));
                float pulse = 0.92f + 0.08f * Mth.sin(time * 0.08f + i * 0.7f);
                drawHaloQuad(buffers.getBuffer(RenderType.entityTranslucent(RING_TEXTURES[i])),
                        poseStack, RING_RADII[i], pulse, pulse, pulse, 1.0f);
                poseStack.popPose();
            }

            poseStack.pushPose();
            poseStack.translate(0.0f, 0.0f, 0.08f);
            float centerPulse = 0.95f + 0.05f * Mth.sin(time * 0.12f);
            drawHaloQuad(buffers.getBuffer(RenderType.entityTranslucent(CENTER_TEXTURE)),
                    poseStack, 0.25f * centerPulse, 1f, 1f, 1f, 1f);
            drawHaloQuad(buffers.getBuffer(RenderType.eyes(CENTER_TEXTURE)),
                    poseStack, 0.25f * centerPulse, 1f, 1f, 1f, 1f);
            poseStack.popPose();

            poseStack.popPose();
        } catch (Throwable t) {
            SourceOfMystery.LOGGER.error("[Halo] EXCEPTION", t);
        }
    }

    /**
     * 画一个以 poseStack 当前原点为中心、Z=0 平面上的方形 quad。
     * 顶点顺序：(-r,-r)→(-r,+r)→(+r,+r)→(+r,-r)，CCW（从 +Z 看），normal (0,0,1)。
     * UV：bottom-left (0,0)、top-left (0,1)、top-right (1,1)、bottom-right (1,0)。
     */
    private static void drawHaloQuad(VertexConsumer vc, PoseStack poseStack,
                                     float r, float red, float green, float blue, float alpha) {
        Matrix4f m = poseStack.last().pose();
        Matrix3f n = poseStack.last().normal();
        vc.vertex(m, -r, -r, 0f).color(red, green, blue, alpha).uv(0f, 0f)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT)
                .normal(n, 0f, 0f, 1f).endVertex();
        vc.vertex(m, -r,  r, 0f).color(red, green, blue, alpha).uv(0f, 1f)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT)
                .normal(n, 0f, 0f, 1f).endVertex();
        vc.vertex(m,  r,  r, 0f).color(red, green, blue, alpha).uv(1f, 1f)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT)
                .normal(n, 0f, 0f, 1f).endVertex();
        vc.vertex(m,  r, -r, 0f).color(red, green, blue, alpha).uv(1f, 0f)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT)
                .normal(n, 0f, 0f, 1f).endVertex();
    }
}
