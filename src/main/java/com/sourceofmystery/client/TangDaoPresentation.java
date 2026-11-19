package com.sourceofmystery.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.PoseStack;
import com.sourceofmystery.SourceOfMystery;
import com.sourceofmystery.item.ModItems;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;

/**
 * Tang Dao presentation. First person plays the authored clips of {@link TangDaoClips} on
 * the vanilla arms through {@link TangDaoRig}; third person keeps the two-hand hold and the
 * hip scabbard.
 *
 * <p>First-person flow: sheathed, a click draws with a cut, further clicks run the combo
 * (diagonal, rising, overhead). A click after a cut's impact is kept and starts the next cut
 * at its chain tick. The combo restarts after a short pause, the inspect key plays the
 * inspection while drawn, and the knife returns to its scabbard after {@link #IDLE_TICKS}.
 */
@Mod.EventBusSubscriber(modid = SourceOfMystery.MOD_ID, value = Dist.CLIENT)
public final class TangDaoPresentation {
    private static final float GRIP_SPACING = .255f;
    private static final float ARM_TO_PALM = 7.9f / 16;
    private static final int IDLE_TICKS = 200;
    private static final int COMBO_RESET_TICKS = 16;
    private static final int TRAIL_TICKS = 4;
    private static final float TRAIL_LEAD = 1.8f;
    private static final float EQUIP_DROP = .6f;
    static final KeyMapping INSPECT = new KeyMapping("key.sourceofmystery.tang_dao_inspect",
            KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_V, "key.categories.sourceofmystery");
    private static final ResourceLocation SCABBARD = new ResourceLocation(SourceOfMystery.MOD_ID, "item/tang_dao_scabbard");

    private static AbstractClientPlayer owner;
    private static ItemStack ownerStack = ItemStack.EMPTY;
    private static int ownerSlot = -1;
    private static TangDaoClips.Clip clip;
    private static float[] clipStart;
    private static int clipTick;
    private static int comboStage;
    private static int idleTicks;
    private static boolean buffered;
    private static final float[] scratch = new float[TangDaoClips.WIDTH];
    private static EntityModelSet armModels;
    private static PlayerModel<AbstractClientPlayer> standardArms;
    private static PlayerModel<AbstractClientPlayer> slimArms;

    private TangDaoPresentation() {}

    @Mod.EventBusSubscriber(modid = SourceOfMystery.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Models {
        @SubscribeEvent
        public static void register(ModelEvent.RegisterAdditional event) {
            event.register(SCABBARD);
        }

        @SubscribeEvent
        public static void registerKeys(RegisterKeyMappingsEvent event) {
            event.register(INSPECT);
        }

        @SubscribeEvent
        public static void registerClips(RegisterClientReloadListenersEvent event) {
            event.registerReloadListener((ResourceManagerReloadListener) TangDaoClips::load);
        }
    }

    public static boolean active(AbstractClientPlayer player) {
        return player.getMainHandItem().is(ModItems.TANG_DAO.get())
                && player.getOffhandItem().isEmpty() && !player.isUsingItem()
                && !player.isSpectator() && !player.isInvisible();
    }

    public static boolean thirdPersonActive(AbstractClientPlayer player) {
        return active(player) && !player.isVisuallySwimming() && !player.isFallFlying()
                && !player.isSleeping();
    }

    // ------------------------------------------------------------------ input and timing

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void attack(InputEvent.InteractionKeyMappingTriggered event) {
        Minecraft client = Minecraft.getInstance();
        var player = client.player;
        if (!event.isAttack() || !event.shouldSwingHand() || player == null || !TangDaoClips.ready()
                || !firstPersonActive(client, player) || client.screen != null) return;
        // The vanilla attack runs at the cut's impact tick instead of on the click.
        event.setCanceled(true);
        event.setSwingHand(false);
        if (!owns(player)) {
            reset();
            owner = player;
            ownerStack = player.getMainHandItem();
            ownerSlot = player.getInventory().selected;
            start("draw", null);
            comboStage = 1 % TangDaoClips.combo().size();
        } else if (clip == null || clip.name.equals("inspect")) {
            nextCut();
        } else if (clip.name.equals("sheathe") || clip.attack() && clipTick >= clip.impact) {
            buffered = true;
        }
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft client = Minecraft.getInstance();
        boolean inspect = false;
        while (INSPECT.consumeClick()) inspect = true;
        if (owner == null) return;
        if (client.player != owner || !owns(owner) || !firstPersonActive(client, owner) || !TangDaoClips.ready()) {
            reset();
            return;
        }
        if (client.isPaused()) return;
        if (clip != null) {
            clipTick++;
            if (clip.attack() && clipTick == clip.impact) hit(client);
            if (buffered && clip.attack() && clipTick >= clip.chain) {
                nextCut();
            } else if (clipTick >= clip.duration()) {
                boolean sheathed = clip.name.equals("sheathe");
                clip = null;
                clipStart = null;
                idleTicks = 0;
                if (sheathed) {
                    boolean redraw = buffered;
                    reset();
                    if (redraw) {
                        owner = client.player;
                        ownerStack = owner.getMainHandItem();
                        ownerSlot = owner.getInventory().selected;
                        start("draw", null);
                        comboStage = 1 % TangDaoClips.combo().size();
                    }
                } else if (buffered) {
                    nextCut();
                }
            }
            return;
        }
        if (++idleTicks > COMBO_RESET_TICKS) comboStage = 0;
        if (inspect && client.screen == null && idleTicks < IDLE_TICKS) {
            start("inspect", null);
        } else if (idleTicks >= IDLE_TICKS) {
            start("sheathe", null);
        }
    }

    private static void nextCut() {
        var order = TangDaoClips.combo();
        String name = order.get(comboStage % order.size());
        comboStage = (comboStage + 1) % order.size();
        // Continue from the pose on screen so a chained or interrupted move has no jump.
        float[] from = clip != null ? clip.sample(clipTick, clipStart, new float[TangDaoClips.WIDTH]) : null;
        start(name, from);
    }

    private static void start(String name, float[] from) {
        clip = TangDaoClips.clip(name);
        clipStart = from;
        clipTick = 0;
        idleTicks = 0;
        buffered = false;
    }

    private static boolean firstPersonActive(Minecraft client, AbstractClientPlayer player) {
        return player.isAlive() && active(player) && client.getCameraEntity() == player
                && client.options.getCameraType().isFirstPerson();
    }

    private static boolean owns(AbstractClientPlayer player) {
        return owner == player && ownerStack == player.getMainHandItem()
                && ownerSlot == player.getInventory().selected;
    }

    private static void reset() {
        owner = null;
        ownerStack = ItemStack.EMPTY;
        ownerSlot = -1;
        clip = null;
        clipStart = null;
        buffered = false;
        comboStage = 0;
        idleTicks = 0;
    }

    private static void hit(Minecraft client) {
        var player = client.player;
        if (player == null || client.gameMode == null) return;
        client.gameRenderer.pick(1.0f);
        if (client.hitResult instanceof EntityHitResult target) {
            client.gameMode.attack(player, target.getEntity());
        } else {
            player.resetAttackStrengthTicker();
        }
        player.swing(InteractionHand.MAIN_HAND);
    }

    // ------------------------------------------------------------------ first-person rendering

    /** Channels on screen this frame, right-handed. */
    private static float[] currentPose(AbstractClientPlayer player, float partial, float[] out) {
        if (!owns(player)) return TangDaoClips.clip("sheathed").sample(0, null, out);
        if (clip == null) return TangDaoClips.clip("ready").sample(0, null, out);
        return clip.sample(clipTick + partial, clipStart, out);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void firstPerson(RenderHandEvent event) {
        if (event.getHand() != InteractionHand.MAIN_HAND || !event.getItemStack().is(ModItems.TANG_DAO.get())) return;
        Minecraft client = Minecraft.getInstance();
        var player = client.player;
        if (player == null || !active(player) || client.getCameraEntity() != player || !TangDaoClips.ready()) return;
        event.setCanceled(true);

        int side = player.getMainArm() == HumanoidArm.RIGHT ? 1 : -1;
        float partial = event.getPartialTick();
        var rig = TangDaoClips.rig();
        float[] v = currentPose(player, partial, scratch);
        if (side < 0) TangDaoRig.mirror(v);

        var knife = new TangDaoRig.Prop();
        TangDaoRig.knife(v, rig, knife);
        TangDaoRig.Prop scabbard = null;
        if (TangDaoRig.scabbardShown(v)) {
            TangDaoRig.Prop anchor = knife;
            boolean pinned = owns(player) && clip != null && clip.anchor >= 0;
            if (pinned) {
                float[] at = clip.sample(clip.anchor, clipStart, new float[TangDaoClips.WIDTH]);
                if (side < 0) TangDaoRig.mirror(at);
                anchor = new TangDaoRig.Prop();
                TangDaoRig.knife(at, rig, anchor);
            }
            scabbard = new TangDaoRig.Prop();
            TangDaoRig.scabbard(v, side, anchor, rig, scabbard);
        }
        boolean toBlade = owns(player) && clip != null && clip.reachBlade;
        Vector3f target = toBlade ? knife.along(v[TangDaoRig.ALONG], new Vector3f())
                : scabbard != null ? scabbard.along(v[TangDaoRig.ALONG], new Vector3f()) : null;
        var mainArm = new TangDaoRig.Arm();
        var freeArm = new TangDaoRig.Arm();
        TangDaoRig.mainArm(v, side, mainArm);
        TangDaoRig.offArm(v, -side, target, freeArm);

        PoseStack poses = event.getPoseStack();
        poses.pushPose();
        float breath = .006f * Mth.sin((player.tickCount + partial) * .11f);
        poses.translate(0, breath - EQUIP_DROP * event.getEquipProgress(), 0);
        MultiBufferSource buffers = event.getMultiBufferSource();
        int light = event.getPackedLight();
        PlayerModel<AbstractClientPlayer> model = arms(client, player);
        HumanoidArm mainSide = player.getMainArm();
        renderArm(player, model, mainSide, mainArm, poses, buffers, light);
        renderArm(player, model, mainSide.getOpposite(), freeArm, poses, buffers, light);
        if (scabbard != null) renderProp(player, event.getItemStack(), scabbard, rig.bladeScale(), poses, buffers, light, true);
        renderProp(player, event.getItemStack(), knife, rig.bladeScale(), poses, buffers, light, false);
        if (owns(player) && clip != null && clip.attack() && !OutsiderShaderCompat.shadowPass()) {
            renderTrail(clipTick + partial, side, rig, poses, buffers);
        }
        poses.popPose();
    }

    /** White arc behind the edge, from just before the impact to the current blade. */
    private static void renderTrail(float frame, int side, TangDaoClips.Rig rig, PoseStack poses, MultiBufferSource buffers) {
        float sinceHit = frame - clip.impact;
        if (sinceHit < -TRAIL_LEAD || sinceHit >= TRAIL_TICKS) return;
        float first = Math.max(clip.impact - TRAIL_LEAD, frame - 2.5f);
        float opacity = 1 - ease((sinceHit - 1.15f) / 2.85f);
        var vertices = OutsiderShaderCompat.glow(buffers, RenderType.lightning());
        Matrix4f matrix = poses.last().pose();
        float[] clear = {1, 1, 1, 0};
        float length = rig.bladeScale() * rig.tip();
        TangDaoRig.Prop from = knifeAt(first, side, rig);
        for (int i = 0; i < 9; i++) {
            float a = i / 9f, b = (i + 1) / 9f;
            TangDaoRig.Prop to = knifeAt(Mth.lerp(b, first, frame), side, rig);
            float[] bright = {1, 1, 1, opacity * (.16f + .76f * ease(b))};
            float[] fromInner = edgePoint(from, length * (.62f + .16f * (1 - a)));
            float[] toInner = edgePoint(to, length * (.62f + .16f * (1 - b)));
            float[] fromRim = edgePoint(from, length * .96f), toRim = edgePoint(to, length * .96f);
            EffectGeometry.glowQuad(vertices, matrix, fromInner, toInner, toRim, fromRim, clear, bright);
            EffectGeometry.glowQuad(vertices, matrix, fromRim, toRim, edgePoint(to, length), edgePoint(from, length), bright, clear);
            from = to;
        }
    }

    private static TangDaoRig.Prop knifeAt(float frame, int side, TangDaoClips.Rig rig) {
        float[] v = clip.sample(frame, clipStart, new float[TangDaoClips.WIDTH]);
        if (side < 0) TangDaoRig.mirror(v);
        var knife = new TangDaoRig.Prop();
        TangDaoRig.knife(v, rig, knife);
        return knife;
    }

    private static float[] edgePoint(TangDaoRig.Prop knife, float along) {
        Vector3f p = knife.along(along, new Vector3f()).add(knife.rotation.getColumn(0, new Vector3f()).mul(.02f));
        return new float[]{p.x, p.y, p.z};
    }

    private static float ease(float value) {
        float t = Mth.clamp(value, 0, 1);
        return t*t*(3-2*t);
    }

    private static PlayerModel<AbstractClientPlayer> arms(Minecraft client, AbstractClientPlayer player) {
        EntityModelSet current = client.getEntityModels();
        if (current != armModels) {
            armModels = current;
            standardArms = bakeArms(current, false);
            slimArms = bakeArms(current, true);
        }
        return "slim".equals(player.getModelName()) ? slimArms : standardArms;
    }

    private static PlayerModel<AbstractClientPlayer> bakeArms(EntityModelSet modelSet, boolean slim) {
        var model = new PlayerModel<AbstractClientPlayer>(modelSet.bakeLayer(slim ? ModelLayers.PLAYER_SLIM : ModelLayers.PLAYER), slim);
        for (ModelPart part : new ModelPart[] {model.rightArm, model.leftArm, model.rightSleeve, model.leftSleeve}) {
            part.setPos(0, 0, 0);
            part.setRotation(0, 0, 0);
        }
        return model;
    }

    private static void renderArm(AbstractClientPlayer player, PlayerModel<?> model, HumanoidArm side,
                                  TangDaoRig.Arm arm, PoseStack poses, MultiBufferSource buffers, int light) {
        boolean right = side == HumanoidArm.RIGHT;
        poses.pushPose();
        poses.translate(arm.pivot.x, arm.pivot.y, arm.pivot.z);
        poses.mulPose(new Quaternionf().setFromNormalized(arm.rotation));
        var skin = player.getSkinTextureLocation();
        (right ? model.rightArm : model.leftArm).render(poses,
                buffers.getBuffer(RenderType.entitySolid(skin)), light, OverlayTexture.NO_OVERLAY);
        if (player.isModelPartShown(right ? PlayerModelPart.RIGHT_SLEEVE : PlayerModelPart.LEFT_SLEEVE)) {
            (right ? model.rightSleeve : model.leftSleeve).render(poses,
                    buffers.getBuffer(RenderType.entityTranslucent(skin)), light, OverlayTexture.NO_OVERLAY);
        }
        poses.popPose();
    }

    private static void renderProp(AbstractClientPlayer player, ItemStack stack, TangDaoRig.Prop prop, float scale,
                                   PoseStack poses, MultiBufferSource buffers, int light, boolean sheath) {
        poses.pushPose();
        poses.translate(prop.origin.x, prop.origin.y, prop.origin.z);
        poses.mulPose(new Quaternionf().setFromNormalized(prop.rotation));
        poses.scale(scale, scale, scale);
        renderModel(player, stack, poses, buffers, light, sheath);
        poses.popPose();
    }

    // ------------------------------------------------------------------ test hooks

    /** Name of the clip playing in first person, or null when idle or sheathed. */
    static String playingClip() { return clip == null ? null : clip.name; }

    /** Ticks into the playing clip. */
    static int playingTick() { return clipTick; }

    /** The knife at time t of a clip from its own first key, mirrored for side -1. */
    static TangDaoRig.Prop knifeSample(String name, float t, int side) {
        var c = TangDaoClips.clip(name);
        float[] v = c.sample(t, null, new float[TangDaoClips.WIDTH]);
        if (side < 0) TangDaoRig.mirror(v);
        var knife = new TangDaoRig.Prop();
        TangDaoRig.knife(v, TangDaoClips.rig(), knife);
        return knife;
    }

    static int impactTick(String name) { return TangDaoClips.clip(name).impact; }
    static java.util.List<String> combo() { return TangDaoClips.combo(); }
    static float bladeLength() { return TangDaoClips.rig().bladeScale() * TangDaoClips.rig().tip(); }

    // ------------------------------------------------------------------ third person

    public static void applyThirdPerson(AbstractClientPlayer player, PlayerModel<?> model) {
        if (!thirdPersonActive(player)) return;
        Hold hold = thirdPersonHold(player, model);
        boolean right = player.getMainArm() == HumanoidArm.RIGHT;
        float rest = 1-thirdPersonSwingWeight(model);
        aimArm(model.rightArm, right ? hold.lead : hold.rear, (right ? .23f : .37f)*rest);
        aimArm(model.leftArm, right ? hold.rear : hold.lead, (right ? -.37f : -.23f)*rest);
        model.rightSleeve.copyFrom(model.rightArm);
        model.leftSleeve.copyFrom(model.leftArm);
    }

    public static void renderThirdPerson(AbstractClientPlayer player, PlayerModel<?> model, ItemStack stack,
                                         PoseStack poses, MultiBufferSource buffers, int light) {
        Hold hold = thirdPersonHold(player, model);
        Vector3f visibleGrip = new Vector3f(hold.lead).add(0,-.015f,Mth.lerp(thirdPersonSwingWeight(model),-.058f,-.085f));
        renderProp(player, stack, visibleGrip, hold.bladeAxis, .12f, poses, buffers, light, false);
    }

    public static void renderHipScabbard(AbstractClientPlayer player, PlayerModel<?> model, ItemStack stack,
                                         PoseStack poses, MultiBufferSource buffers, int light) {
        float side = player.getMainArm() == HumanoidArm.RIGHT ? 1 : -1;
        Vector3f mount = new Vector3f(side*.355f, .656f, -.265f);
        Vector3f tail = new Vector3f(side*.21f, .25f, .946f).normalize();
        poses.pushPose();
        model.body.translateAndRotate(poses);
        renderProp(player, stack, mount, tail, side*.60f, poses, buffers, light, true);
        poses.popPose();
    }

    private static Hold thirdPersonHold(AbstractClientPlayer player, PlayerModel<?> model) {
        boolean right = player.getMainArm() == HumanoidArm.RIGHT;
        float sign = right ? 1 : -1;
        float swing = Mth.sin(model.attackTime * Mth.PI);
        float attack = thirdPersonSwingWeight(model);
        Vector3f blade = new Vector3f(-sign*.89f, -.28f, -.36f)
                .lerp(new Vector3f(-sign*(.82f-.24f*swing), -.26f-.31f*swing, -.57f), attack).normalize();
        Vector3f mainShoulder = shoulder(right ? model.rightArm : model.leftArm);
        Vector3f rearShoulder = shoulder(right ? model.leftArm : model.rightArm).fma(GRIP_SPACING, blade);

        // Intersect the two arm-reach spheres after offsetting the rear shoulder by the grip spacing.
        Vector3f span = new Vector3f(rearShoulder).sub(mainShoulder);
        float spanLength = span.length();
        span.div(spanLength);
        Vector3f lead = new Vector3f(mainShoulder).lerp(rearShoulder, .5f);
        // A lower, offset guard leaves each palm visible on its own part of the handle.
        Vector3f downForward = new Vector3f(-sign*.19f, .82f, -.58f)
                .lerp(new Vector3f(0, .65f-.16f*swing, -.76f), attack);
        downForward.fma(-downForward.dot(span), span).normalize();
        float radius = Mth.sqrt(Math.max(0, ARM_TO_PALM*ARM_TO_PALM - spanLength*spanLength*.25f));
        lead.fma(radius, downForward);
        return new Hold(lead, new Vector3f(lead).fma(-GRIP_SPACING, blade), blade);
    }

    private static Vector3f shoulder(ModelPart arm) {
        return new Vector3f(arm.x/16, arm.y/16, arm.z/16);
    }

    private static float thirdPersonSwingWeight(PlayerModel<?> model) {
        return ease(Mth.sin(model.attackTime*Mth.PI)/.24f);
    }

    private static void aimArm(ModelPart arm, Vector3f palm, float roll) {
        Vector3f axis = new Vector3f(palm).sub(shoulder(arm)).normalize();
        float pitch = (float)Math.asin(Mth.clamp(axis.z, -1, 1));
        float tilt = (float)Math.atan2(-axis.x, axis.y);
        Vector3f angles = new Quaternionf().rotationZ(tilt).rotateX(pitch).rotateY(roll)
                .getEulerAnglesZYX(new Vector3f());
        arm.xRot = angles.x;
        arm.yRot = angles.y;
        arm.zRot = angles.z;
    }

    private static void renderProp(AbstractClientPlayer player, ItemStack stack, Vector3f lead, Vector3f axis,
                                   float roll, PoseStack poses, MultiBufferSource buffers, int light, boolean sheath) {
        poses.pushPose();
        poses.translate(lead.x, lead.y, lead.z);
        poses.mulPose(facingRotation(axis, roll));
        renderModel(player, stack, poses, buffers, light, sheath);
        poses.popPose();
    }

    private static Quaternionf facingRotation(Vector3f axis, float roll) {
        Vector3f across = new Vector3f(axis).cross(0, 0, 1);
        if (across.lengthSquared() < .001f) across.set(1, 0, 0);
        across.normalize();
        Vector3f face = new Vector3f(across).cross(axis).normalize();
        return new Quaternionf().setFromNormalized(
                new Matrix3f().setColumn(0, across).setColumn(1, axis).setColumn(2, face)).rotateY(roll);
    }

    private static void renderModel(AbstractClientPlayer player, ItemStack stack, PoseStack poses,
                                    MultiBufferSource buffers, int light, boolean sheath) {
        Minecraft client = Minecraft.getInstance();
        if (sheath) {
            var model = client.getModelManager().getModel(SCABBARD);
            client.getItemRenderer().render(stack, ItemDisplayContext.NONE, false, poses, buffers,
                    light, OverlayTexture.NO_OVERLAY, model);
        } else {
            client.getItemRenderer().renderStatic(player, stack, ItemDisplayContext.NONE,
                    false, poses, buffers, player.level(), light, OverlayTexture.NO_OVERLAY, player.getId());
        }
    }

    private record Hold(Vector3f lead, Vector3f rear, Vector3f bladeAxis) {}
}
