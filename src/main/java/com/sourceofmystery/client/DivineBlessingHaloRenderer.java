package com.sourceofmystery.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.sourceofmystery.SourceOfMystery;
import com.sourceofmystery.item.MysteryArmorMaterial;
import com.sourceofmystery.item.MysteryChestplateItem;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;

/** Chest armor decoration, rendered inside the wearer's animated model transform. */
public final class DivineBlessingHaloRenderer<T extends LivingEntity, M extends EntityModel<T>>
        extends RenderLayer<T, M> {
    private static final ResourceLocation[] RING_TEXTURES = {
            texture("ring_0"), texture("ring_1"), texture("ring_2"), texture("ring_3")
    };
    private static final ResourceLocation CENTER_TEXTURE = texture("center");
    private static final float[] RING_RADII = {0.78f, 0.66f, 0.54f, 0.42f};
    private static final float[] RING_SPEEDS = {1.45f, -1.10f, 0.82f, -0.60f};
    private static final float CHEST_HEIGHT = 0.35f;
    private static final float BACK_OFFSET = 0.45f;

    public DivineBlessingHaloRenderer(RenderLayerParent<T, M> parent) {
        super(parent);
    }

    private static ResourceLocation texture(String name) {
        return new ResourceLocation(SourceOfMystery.MOD_ID,
                "textures/entity/divine_blessing_halo_" + name + ".png");
    }

    @Override
    public void render(PoseStack poses, MultiBufferSource buffers, int packedLight, T wearer,
                       float limbSwing, float limbAmount, float partialTick, float age,
                       float headYaw, float headPitch) {
        if (!(wearer.getItemBySlot(EquipmentSlot.CHEST).getItem() instanceof MysteryChestplateItem armor)
                || armor.getMaterial() != MysteryArmorMaterial.DIVINE_BLESSING) return;
        if (!(getParentModel() instanceof HumanoidModel<?> model)) return;

        poses.pushPose();
        try {
            // Render layers retain body yaw, model scale, swimming and flight transforms.
            // Post-render events have already lost that parent transform.
            model.body.translateAndRotate(poses);
            // Humanoid model Y points down; the body origin is at the neck.
            poses.translate(0.0f, CHEST_HEIGHT, BACK_OFFSET);
            float time = wearer.tickCount + partialTick;
            for (int i = 0; i < RING_TEXTURES.length; i++) {
                poses.pushPose();
                try {
                    poses.translate(0.0f, 0.0f, 0.015f * i);
                    poses.mulPose(Axis.ZP.rotationDegrees(time * RING_SPEEDS[i]));
                    float pulse = 0.92f + 0.08f * Mth.sin(time * 0.08f + i * 0.7f);
                    drawQuad(buffers.getBuffer(RenderType.entityTranslucent(RING_TEXTURES[i])),
                            poses, RING_RADII[i], pulse);
                } finally {
                    poses.popPose();
                }
            }
            poses.translate(0.0f, 0.0f, 0.08f);
            float radius = 0.25f * (0.95f + 0.05f * Mth.sin(time * 0.12f));
            drawQuad(buffers.getBuffer(RenderType.entityTranslucent(CENTER_TEXTURE)), poses, radius, 1.0f);
            drawQuad(buffers.getBuffer(RenderType.eyes(CENTER_TEXTURE)), poses, radius, 1.0f);
        } finally {
            poses.popPose();
        }
    }

    private static void drawQuad(VertexConsumer vertices, PoseStack poses, float radius, float brightness) {
        PoseStack.Pose pose = poses.last();
        // Image top (V=0) is model -Y. Reverse U so the glyph reads from behind the armor.
        vertex(vertices, pose, -radius, -radius, 1, 0, brightness);
        vertex(vertices, pose, -radius, radius, 1, 1, brightness);
        vertex(vertices, pose, radius, radius, 0, 1, brightness);
        vertex(vertices, pose, radius, -radius, 0, 0, brightness);
    }

    private static void vertex(VertexConsumer vertices, PoseStack.Pose pose, float x, float y,
                               float u, float v, float brightness) {
        vertices.vertex(pose.pose(), x, y, 0).color(brightness, brightness, brightness, 1.0f)
                .uv(u, v).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT)
                .normal(pose.normal(), 0, 0, 1).endVertex();
    }
}
