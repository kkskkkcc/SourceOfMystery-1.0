package com.sourceofmystery.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.sourceofmystery.SourceOfMystery;
import com.sourceofmystery.item.DivineBlessingChestplateItem;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoArmorRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/** War-god plate armor; all surface effects use exactly the armor's posed geometry. */
public final class DivineBlessingArmorRenderer extends GeoArmorRenderer<DivineBlessingChestplateItem> {
    private static final ResourceLocation MODEL = resource("geo/armor/divine_blessing.geo.json");
    private static final ResourceLocation TEXTURE = resource("textures/armor/divine_blessing.png");
    private static final ResourceLocation ANIMATION = resource("animations/armor/divine_blessing.animation.json");
    private static final ResourceLocation WHITE_FLOW = resource("textures/armor/divine_blessing_white_flow.png");
    private static final ResourceLocation BLACK_FLOW = resource("textures/armor/divine_blessing_black_flow.png");

    public DivineBlessingArmorRenderer() {
        super(new GeoModel<>() {
            @Override public ResourceLocation getModelResource(DivineBlessingChestplateItem item) { return MODEL; }
            @Override public ResourceLocation getTextureResource(DivineBlessingChestplateItem item) { return TEXTURE; }
            @Override public ResourceLocation getAnimationResource(DivineBlessingChestplateItem item) { return ANIMATION; }
        });
        addRenderLayer(new GloryLayer(this));
        addRenderLayer(new AutoGlowingGeoLayer<>(this));
    }

    private static ResourceLocation resource(String path) {
        return new ResourceLocation(SourceOfMystery.MOD_ID, path);
    }

    @Override
    protected void applyBoneVisibilityBySlot(EquipmentSlot slot) {
        super.applyBoneVisibilityBySlot(slot);
        if (slot == EquipmentSlot.CHEST) {
            // The chestplate's hip guards follow the wearer's legs independently.
            setBoneVisible(getRightLegBone(), true);
            setBoneVisible(getLeftLegBone(), true);
        }
    }

    private static final class GloryLayer extends GeoRenderLayer<DivineBlessingChestplateItem> {
        private final DivineBlessingArmorRenderer armor;

        GloryLayer(DivineBlessingArmorRenderer armor) {
            super(armor);
            this.armor = armor;
        }

        @Override
        public void render(PoseStack poses, DivineBlessingChestplateItem item, BakedGeoModel model,
                           RenderType baseType, MultiBufferSource buffers, VertexConsumer baseVertices,
                           float partialTick, int light, int overlay) {
            var wearer = armor.getCurrentEntity();
            if (wearer == null) return;
            // Continuous partial-tick time; different wearers do not flash in unison.
            float seconds = (wearer.tickCount + partialTick) / 20.0f;
            float seed = (wearer.getUUID().hashCode() & 1023) / 1024.0f;
            float phase = (seconds + seed * 13.6f) % 13.6f;
            float black = envelope(phase, 0, 2.7f);
            float white = envelope(phase, 0.35f, 2.65f);
            if (black <= 0 && white <= 0) return;

            // The badge uses a round alpha cutout. A scrolling map must not fill its
            // transparent square corners; keep the yin-yang face clear and readable.
            var emblem = armor.getGeoModel().getBone("taiji_emblem").orElse(null);
            boolean hidden = emblem != null && emblem.isHidden();
            if (emblem != null) emblem.setHidden(true);
            try {
                // Re-rendering the same bones keeps the flow glued to every armor pose.
                if (black > 0) draw(model, poses, buffers, item, RenderType.entityTranslucent(BLACK_FLOW),
                        phase * 0.085f, -phase * 0.12f, partialTick, overlay, black * 0.72f);
                if (white > 0) draw(model, poses, buffers, item, RenderType.eyes(WHITE_FLOW),
                        -phase * 0.065f, phase * 0.10f, partialTick, overlay, white * 0.78f);
            } finally {
                if (emblem != null) emblem.setHidden(hidden);
            }
        }

        private void draw(BakedGeoModel model, PoseStack poses, MultiBufferSource buffers,
                          DivineBlessingChestplateItem item, RenderType type, float u, float v,
                          float partialTick, int overlay, float alpha) {
            var vertices = new FlowVertices(buffers.getBuffer(type), u, v);
            armor.reRender(model, poses, buffers, item, type, vertices, partialTick,
                    LightTexture.FULL_BRIGHT, overlay, 1, 1, 1, alpha);
        }

        private static float envelope(float time, float start, float duration) {
            float t = (time - start) / duration;
            if (t <= 0 || t >= 1) return 0;
            return Mth.sin(t * Mth.PI) * Mth.sin(t * Mth.PI);
        }
    }

    /** Scroll the dedicated seamless flow maps without touching the armor atlas or GL state. */
    private record FlowVertices(VertexConsumer target, float uOffset, float vOffset) implements VertexConsumer {
        @Override public VertexConsumer vertex(double x, double y, double z) { target.vertex(x,y,z); return this; }
        @Override public VertexConsumer color(int r,int g,int b,int a) { target.color(r,g,b,a); return this; }
        @Override public VertexConsumer uv(float u,float v) { target.uv(u+uOffset,v+vOffset); return this; }
        @Override public VertexConsumer overlayCoords(int u,int v) { target.overlayCoords(u,v); return this; }
        @Override public VertexConsumer uv2(int u,int v) { target.uv2(u,v); return this; }
        @Override public VertexConsumer normal(float x,float y,float z) { target.normal(x,y,z); return this; }
        @Override public void endVertex() { target.endVertex(); }
        @Override public void defaultColor(int r,int g,int b,int a) { target.defaultColor(r,g,b,a); }
        @Override public void unsetDefaultColor() { target.unsetDefaultColor(); }
    }
}
