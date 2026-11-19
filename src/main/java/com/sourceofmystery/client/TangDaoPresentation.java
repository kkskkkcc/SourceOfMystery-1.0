package com.sourceofmystery.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.systems.RenderSystem;
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

/** Renders the test knife and the player's skin with a shared pair of grip anchors. */
@Mod.EventBusSubscriber(modid = SourceOfMystery.MOD_ID, value = Dist.CLIENT)
public final class TangDaoPresentation {
    private static final float GRIP_SPACING = .255f;
    private static final float ARM_TO_PALM = 7.9f / 16;
    private static final float SCABBARD_MOUTH = .120f;
    private static final float DRAW_TRAVEL = .79f;
    private static final float SCABBARD_TRAVEL = 1.10f;
    private static final int IDLE_TICKS = 200;
    private static final int TAIL_BEAT_TICKS = 20;
    private static final int DRAW_TICKS = 4;
    private static final int SHEATHE_ALIGN_TICKS = 3;
    private static final int SHEATHE_TICKS = 8;
    private static final int INSPECT_TICKS = 54;
    // Distance from the lead-hand origin to the tip of the exported OBJ.
    private static final float BLADE_TIP = 1.825f;
    private static final float EDGE_OFFSET = .034f;
    private static final float CONTACT_DISTANCE = .76f;
    private static final float DRAW_TURN = 30 * Mth.DEG_TO_RAD;
    private static final float RISING_TURN = 50 * Mth.DEG_TO_RAD;
    static final KeyMapping INSPECT = new KeyMapping("key.sourceofmystery.tang_dao_inspect",
            KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_V, "key.categories.sourceofmystery");
    private static final ResourceLocation SCABBARD = new ResourceLocation(SourceOfMystery.MOD_ID, "item/tang_dao_scabbard");
    // The two guards use different parts of the viewport. In the sheathed guard,
    // the blade axis runs down-left and the pommel rises toward the center.
    private static final CameraPose SHEATHED = new CameraPose(-.16f, -.375f, -1.05f, -.57f, -.72f, -.39f, .13f);
    private static final CameraPose DRAW_CLEAR = SHEATHED.shiftAlong(-DRAW_TRAVEL);
    private static final CameraPose DRAWN_READY = new CameraPose(.55f, -.36f, -1.17f, .16f, .94f, -.31f, .19f+Mth.HALF_PI);
    private static final CameraPose RECOVERY_CLEAR = new CameraPose(.36f, -.53f, -1.46f, -.12f, .52f, -.85f, -.09f);
    private static final CameraPose DRAW_WINDUP = new CameraPose(.87f, .36f, -1.10f, -.80f, .42f, -.43f, .34f);
    private static final CameraPose DRAW_FOLLOW = new CameraPose(-.33f, -.49f, -1.10f, -.71f, -.37f, -.60f, -.39f);
    private static final CameraPose INSPECT_FACE = new CameraPose(.52f, -.14f, -1.80f, -.93f, .26f, -.29f, -.04f);
    private static final CameraPose INSPECT_REVERSE = new CameraPose(.40f, .02f, -1.77f, -.94f, -.13f, -.32f, 2.89f);
    private static final Gesture SHEATHED_ARMS = new Gesture(.78f,-.99f,-.49f,.18f, -.44f,-.65f,-.88f,-.18f);
    private static final Gesture READY_ARMS = new Gesture(1.26f,-.98f,-.66f,.71f, -.49f,-.65f,-.96f,-.37f);
    private static final Gesture INSPECT_ARMS = new Gesture(1.27f,-.76f,-.69f,.24f, -.23f,-.34f,-1.58f,-.42f,1);
    private static final Gesture INSPECT_REVERSE_ARMS = new Gesture(1.19f,-.68f,-.66f,1.31f, -.25f,-.30f,-1.60f,.08f,1);
    private static final ArmBeats CROSS_ARMS = new ArmBeats(
            new Gesture(1.48f,-.02f,-.62f,.48f, -.54f,-.62f,-.96f,-.45f,.85f),
            new Gesture(1.06f,-.54f,-.54f,.08f, -.66f,-.59f,-.93f,-.63f,1),
            new Gesture(.38f,-1.13f,-.54f,-.56f, -.47f,-.62f,-.91f,-.26f,1));
    private static final ArmBeats RETURN_ARMS = new ArmBeats(
            new Gesture(.27f,-1.18f,-.46f,-.64f, -.81f,-.68f,-.96f,.26f,.85f),
            new Gesture(.54f,-1.17f,-.43f,-.26f, -.79f,-.71f,-.94f,.07f,1),
            new Gesture(1.02f,-.34f,-.65f,.40f, -.65f,-.54f,-.96f,-.36f,1));
    private static final ArmBeats FINISH_ARMS = new ArmBeats(
            new Gesture(.87f,.88f,-.51f,-.34f, -.44f,-.48f,-1.08f,.29f,1),
            new Gesture(.86f,-.77f,-.43f,.04f, -.53f,-.49f,-.97f,.18f,1),
            new Gesture(.71f,-1.12f,-.53f,.31f, -.48f,-.58f,-.96f,-.21f,1));
    private static final ArmBeats SWEEP_ARMS = new ArmBeats(
            new Gesture(.00f,-.42f,-.49f,-.52f, -.75f,-.77f,-.89f,-.24f,.85f),
            new Gesture(.95f,-.01f,-.51f,-.11f, -.82f,-.60f,-.88f,.03f,1),
            new Gesture(1.48f,-.73f,-.58f,.48f, -.51f,-.69f,-.92f,.24f,1));

    private static AbstractClientPlayer attackOwner;
    private static ItemStack attackStack = ItemStack.EMPTY;
    private static int attackSlot = -1;
    private static int motionTick;
    private static int tailBeatTicks;
    private static int idleTicks;
    private static int inspectionTick = -1;
    private static boolean bufferedAttack;
    private static boolean sheathing;
    private static Cut cut;
    private static Cut nextCut = Cut.RETURN;
    private static CameraPose startPose = SHEATHED;
    private static Gesture startGesture = SHEATHED_ARMS;
    private static float startGrip;
    private static float startOverheadFit;
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

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void attack(InputEvent.InteractionKeyMappingTriggered event) {
        Minecraft client = Minecraft.getInstance();
        var player = client.player;
        if (!event.isAttack() || !event.shouldSwingHand() || player == null
                || !firstPersonActive(client, player) || client.screen != null) return;
        // Route the original attack through the impact frame instead of the click frame.
        event.setCanceled(true);
        event.setSwingHand(false);
        if (!ownsMotion(player)) {
            resetMotion();
            attackOwner = player;
            attackStack = player.getMainHandItem();
            attackSlot = player.getInventory().selected;
            begin(Cut.DRAW, SHEATHED, SHEATHED_ARMS, 0);
        } else if (cut == null && !sheathing) {
            if (inspectionTick >= 0) {
                begin(nextCut, inspectionPose(inspectionTick), inspectionArms(inspectionTick), inspectionGrip(inspectionTick));
            } else {
                begin(nextCut, DRAWN_READY, READY_ARMS, 1);
            }
        } else if (sheathing || motionTick >= cut.impactTick) {
            // One buffered click can carry through the current follow-through.
            bufferedAttack = true;
        }
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft client = Minecraft.getInstance();
        boolean inspect = INSPECT.consumeClick();
        while (INSPECT.consumeClick()) inspect = true;
        if (attackOwner == null) return;
        if (client.player != attackOwner || !ownsMotion(attackOwner) || !firstPersonActive(client, attackOwner)) {
            resetMotion();
            return;
        }
        if (client.isPaused()) return;
        if (cut != null) {
            if (motionTick == cut.chainTick && !bufferedAttack && tailBeatTicks < TAIL_BEAT_TICKS) {
                tailBeatTicks++;
                return;
            }
            if (!bufferedAttack || motionTick < cut.chainTick) {
                motionTick++;
                if (motionTick == cut.impactTick) hit(client);
            }
            if (bufferedAttack && motionTick >= cut.chainTick) {
                CameraPose from = cutPose(motionTick);
                Vector3f edge = attackRotation(motionTick,1).transform(new Vector3f(1,0,0));
                float wristTurn = twistToEdge(from.rotation(1), edge);
                float overheadFit = overheadFit(motionTick);
                begin(nextCut, from.withRoll(from.roll+wristTurn), armsForCut(motionTick), attackGrip(motionTick));
                startOverheadFit = overheadFit;
            } else if (motionTick >= cut.duration()) {
                cut = null;
                idleTicks = 0;
            }
        } else if (sheathing) {
            if (++motionTick >= SHEATHE_TICKS) {
                if (bufferedAttack) begin(Cut.DRAW, SHEATHED, SHEATHED_ARMS, 0);
                else resetMotion();
            }
        } else {
            idleTicks++;
            if (inspect && client.screen == null && inspectionTick < 0 && idleTicks < IDLE_TICKS) {
                inspectionTick = 0;
            } else if (inspectionTick >= 0 && ++inspectionTick >= INSPECT_TICKS) {
                inspectionTick = -1;
            }
            if (inspectionTick < 0 && idleTicks >= IDLE_TICKS) {
                motionTick = 0;
                sheathing = true;
            }
        }
    }

    private static boolean firstPersonActive(Minecraft client, AbstractClientPlayer player) {
        return player.isAlive() && active(player) && client.getCameraEntity() == player
                && client.options.getCameraType().isFirstPerson();
    }

    private static boolean ownsMotion(AbstractClientPlayer player) {
        return attackOwner == player && attackStack == player.getMainHandItem()
                && attackSlot == player.getInventory().selected;
    }

    private static void begin(Cut next, CameraPose from, Gesture fromArms, float grip) {
        cut = next;
        nextCut = switch (next) {
            case DRAW, CROSS -> Cut.RETURN;
            case RETURN -> Cut.FINISH;
            case FINISH -> Cut.CROSS;
        };
        startPose = from;
        startGesture = fromArms;
        startGrip = grip;
        startOverheadFit = 0;
        motionTick = 0;
        tailBeatTicks = 0;
        idleTicks = 0;
        inspectionTick = -1;
        bufferedAttack = false;
        sheathing = false;
    }

    private static void resetMotion() {
        attackOwner = null;
        attackStack = ItemStack.EMPTY;
        attackSlot = -1;
        bufferedAttack = false;
        sheathing = false;
        cut = null;
        inspectionTick = -1;
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

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void firstPerson(RenderHandEvent event) {
        if (event.getHand() != InteractionHand.MAIN_HAND || !event.getItemStack().is(ModItems.TANG_DAO.get())) return;
        Minecraft client = Minecraft.getInstance();
        var player = client.player;
        if (player == null || !active(player) || client.getCameraEntity() != player) return;
        event.setCanceled(true);

        HumanoidArm mainArm = player.getMainArm();
        int side = mainArm == HumanoidArm.RIGHT ? 1 : -1;
        boolean drawn = ownsMotion(player);
        float frame = motionTick + event.getPartialTick();
        float effectFrame = frame+tailBeatTicks;
        if (drawn && cut != null && motionTick == cut.chainTick && !bufferedAttack && tailBeatTicks < TAIL_BEAT_TICKS) {
            frame = motionTick;
        }
        boolean inspecting = drawn && inspectionTick >= 0;
        float inspectFrame = inspectionTick + event.getPartialTick();
        CameraPose key = !drawn ? SHEATHED : cut != null ? cutPose(frame)
                : sheathing ? sheathePose(frame) : inspecting ? inspectionPose(inspectFrame) : DRAWN_READY;
        float retraction = 0;
        float rearGrip = 0;
        Gesture gesture = SHEATHED_ARMS;
        if (drawn) {
            retraction = cut == Cut.DRAW ? ease(frame/DRAW_TICKS)
                    : sheathing ? 1-ease((frame-SHEATHE_ALIGN_TICKS)/(SHEATHE_TICKS-SHEATHE_ALIGN_TICKS)) : 1;
            rearGrip = cut != null ? attackGrip(frame)
                    : sheathing ? 1-ease(frame/SHEATHE_ALIGN_TICKS) : inspecting ? inspectionGrip(inspectFrame) : 1;
            gesture = cut != null ? armsForCut(frame)
                    : sheathing ? READY_ARMS.blend(SHEATHED_ARMS, ease(frame/SHEATHE_ALIGN_TICKS))
                    : inspecting ? inspectionArms(inspectFrame) : READY_ARMS;
        }
        float breath = .006f * Mth.sin((player.tickCount + event.getPartialTick()) * .11f);
        float lift = breath - .47f*event.getEquipProgress();
        PoseStack poses = event.getPoseStack();
        boolean attacking = drawn && cut != null;
        Vector3f aim = attacking && frame >= cut.impactTick-2 && effectFrame < cut.impactTick+4 ? aimOffset(
                new Matrix4f(RenderSystem.getProjectionMatrix()).mul(poses.last().pose()), side, lift) : new Vector3f();
        BladeSample blade = attacking ? bladeSample(frame, side, lift, aim)
                : new BladeSample(key.origin(side,lift), key.rotation(side));
        Vector3f lead = blade.origin;
        Vector3f direction = key.axis(side);
        Vector3f sheathAxis = SHEATHED.axis(side);
        float lowerSheath = ease((retraction-.92f)/.08f);
        Vector3f sheath = SHEATHED.origin(side, lift).fma(SCABBARD_MOUTH + SCABBARD_TRAVEL*retraction, sheathAxis)
                .add(0, -.52f*lowerSheath, -.048f*lowerSheath);
        Vector3f supportFree = new Vector3f(sheath).fma(.21f, sheathAxis)
                .lerp(gesture.supportPalm(side,lift), ease((retraction-.67f)/.33f));
        float gripArc = Mth.sin(rearGrip*Mth.PI);
        float handFit = gesture.handFit*ease((retraction-.70f)/.30f);
        Vector3f support = supportFree.lerp(new Vector3f(lead).fma(-GRIP_SPACING+.023f*handFit, direction), rearGrip)
                .add(0, -.22f*gripArc, .24f*gripArc);

        PlayerModel<AbstractClientPlayer> model = arms(client, player);
        MultiBufferSource buffers = event.getMultiBufferSource();
        int light = event.getPackedLight();
        Vector3f supportShoulder = new Vector3f(side*Mth.lerp(rearGrip,-.80f,.14f), -1.02f, -.36f);
        float twoHanded = attacking ? overheadFit(frame) : 0;
        float raised = attacking && cut == Cut.FINISH ? ease((frame-2)/4)*(1-ease((frame-7)/2)) : 0;
        supportShoulder.lerp(new Vector3f(-side*.81f, Mth.lerp(raised,-.97f,.93f), -.47f), twoHanded);
        if ((attacking || inspecting) && retraction > .98f && rearGrip < .98f) {
            clearSupportFromBlade(support, supportShoulder, lead, direction, rearGrip);
        }
        renderCameraArm(player, model, mainArm.getOpposite(), support, supportShoulder,
                side*gesture.supportWrist, rearGrip*handFit, poses, buffers, light);
        Vector3f mainShoulder = gesture.mainShoulder(side);
        if ((attacking || inspecting) && retraction >= .99f) {
            // Keep the forearm behind its hand instead of swinging through the blade or the crossguard.
            float towardBlade = new Vector3f(mainShoulder).sub(lead).dot(direction);
            mainShoulder.fma(-Math.max(0,towardBlade+.035f),direction);
        }
        // Center the block-shaped palm on the wrapped portion behind the guard during motion.
        Vector3f mainGrip = new Vector3f(lead).fma(-.055f*handFit, direction);
        renderCameraArm(player, model, mainArm, mainGrip, mainShoulder,
                side*gesture.mainWrist, handFit, poses, buffers, light);
        if (retraction < .998f) {
            renderProp(player, event.getItemStack(), sheath, SHEATHED.rotation(side), poses, buffers, light, true);
        }
        renderProp(player, event.getItemStack(), lead, blade.rotation, poses, buffers, light, false);
        if (attacking && motionTick >= cut.impactTick && !OutsiderShaderCompat.shadowPass()) {
            renderWhiteTrail(frame, effectFrame, side, lift, aim, blade, poses, buffers);
        }
    }

    private static void clearSupportFromBlade(Vector3f palm, Vector3f shoulder, Vector3f grip,
                                               Vector3f blade, float rearGrip) {
        Vector3f alongArm = new Vector3f(shoulder).sub(palm).normalize();
        float distanceSq = Float.POSITIVE_INFINITY;
        for (int i = 0; i <= 5; i++) {
            Vector3f armPoint = new Vector3f(palm).fma(i*.13f, alongArm).sub(grip);
            float alongBlade = Mth.clamp(armPoint.dot(blade), .13f, BLADE_TIP);
            distanceSq = Math.min(distanceSq, armPoint.fma(-alongBlade,blade).lengthSquared());
        }
        float clearance = ease((.18f-Mth.sqrt(distanceSq))/.13f)*(1-ease((rearGrip-.80f)/.18f));
        palm.add(0,-.17f*clearance,.24f*clearance);
    }

    private static float ease(float value) {
        float t = Mth.clamp(value, 0, 1);
        return t*t*(3-2*t);
    }

    static float attackGrip(float frame) {
        if (cut == Cut.FINISH) return Mth.lerp(ease(frame/2.8f),startGrip,1);
        float release = startGrip * (1 - ease(frame/2));
        float recover = ease((frame-cut.chainTick)/(cut.duration()-cut.chainTick));
        return Math.max(release, recover);
    }

    private static float overheadFit(float frame) {
        if (cut == Cut.FINISH) return ease(frame/4)*(1-ease((frame-cut.impactTick)/(cut.duration()-cut.impactTick)));
        return startOverheadFit*(1-ease(frame/3));
    }

    private static Gesture armsForCut(float frame) {
        float windup = cut.impactTick-2;
        ArmBeats arms = cut.arms;
        if (frame < windup) return startGesture.blend(arms.windup, ease(frame/windup));
        if (frame < cut.impactTick) return arms.windup.blend(arms.impact, ease((frame-windup)/2));
        if (frame < cut.chainTick) {
            return arms.impact.blend(arms.follow, ease((frame-cut.impactTick)/(cut.chainTick-cut.impactTick)));
        }
        return arms.follow.blend(READY_ARMS, ease((frame-cut.chainTick)/(cut.duration()-cut.chainTick)));
    }

    private static CameraPose cutPose(float frame) {
        Key previous = new Key(0, startPose);
        for (int i = 0; i < cut.keys.length; i++) {
            Key key = cut.keys[i];
            if (frame < key.tick) {
                float span = key.tick-previous.tick;
                float progress = (frame-previous.tick)/span;
                if (cut == Cut.DRAW && i == 0) {
                    return CameraPose.between(previous.pose, key.pose, ease(progress));
                }
                Key before = i >= 2 ? cut.keys[i-2] : new Key(0, startPose);
                Key after = i+1 < cut.keys.length ? cut.keys[i+1] : key;
                float enter = i == 0 || cut == Cut.DRAW && i == 1 ? 0 : span/(key.tick-before.tick);
                float leave = after == key ? 0 : span/(after.tick-previous.tick);
                return CameraPose.curve(before.pose, previous.pose, key.pose, after.pose, progress, enter, leave);
            }
            previous = key;
        }
        return DRAWN_READY;
    }

    private static CameraPose sheathePose(float frame) {
        if (frame < SHEATHE_ALIGN_TICKS) {
            return CameraPose.between(DRAWN_READY, DRAW_CLEAR, ease(frame/SHEATHE_ALIGN_TICKS));
        }
        return CameraPose.between(DRAW_CLEAR, SHEATHED, ease((frame-SHEATHE_ALIGN_TICKS)/(SHEATHE_TICKS-SHEATHE_ALIGN_TICKS)));
    }

    private static CameraPose inspectionPose(float frame) {
        if (frame < 17) return CameraPose.between(DRAWN_READY, INSPECT_FACE, ease(frame/11));
        if (frame < 38) return CameraPose.between(INSPECT_FACE, INSPECT_REVERSE, ease((frame-17)/13));
        return CameraPose.between(INSPECT_REVERSE, DRAWN_READY, ease((frame-38)/(INSPECT_TICKS-38)));
    }

    private static Gesture inspectionArms(float frame) {
        if (frame < 17) return READY_ARMS.blend(INSPECT_ARMS, ease(frame/11));
        if (frame < 38) return INSPECT_ARMS.blend(INSPECT_REVERSE_ARMS, ease((frame-17)/13));
        return INSPECT_REVERSE_ARMS.blend(READY_ARMS, ease((frame-38)/(INSPECT_TICKS-38)));
    }

    private static float inspectionGrip(float frame) {
        return Math.max(1-ease(frame/8), ease((frame-44)/(INSPECT_TICKS-44)));
    }

    private static float aimWeight(float frame) {
        float sinceImpact = frame-cut.impactTick;
        return ease((sinceImpact+1.65f)/1.65f)*(1-ease(sinceImpact/1.95f));
    }

    static Vector3f aimOffset(Matrix4f handToClip, int side, float lift) {
        CameraPose impact = cutPose(cut.impactTick);
        Vector3f contact = impact.origin(side, lift).fma(CONTACT_DISTANCE, impact.axis(side));
        // The hand pass has its own FOV and can be rotated by view bob or damage.
        // Reproject at the blade's original depth so the contact follows the actual HUD center.
        Vector3f screen = handToClip.transformProject(new Vector3f(contact));
        return new Matrix4f(handToClip).invert().transformProject(screen.set(0,0,screen.z)).sub(contact);
    }

    private static void renderWhiteTrail(float poseFrame, float effectFrame, int side, float lift, Vector3f aim,
                                         BladeSample current,
                                         PoseStack poses, MultiBufferSource buffers) {
        float sinceHit = effectFrame-cut.impactTick;
        if (sinceHit >= 4) return;
        float first = Math.max(cut.impactTick-1.85f, poseFrame-2.5f);
        float opacity = 1-ease((sinceHit-1.15f)/2.85f);
        var vertices = OutsiderShaderCompat.glow(buffers, RenderType.lightning());
        var matrix = poses.last().pose();
        float[] clear = {1, 1, 1, 0};
        BladeSample from = bladeSample(first, side, lift, aim);
        for (int i = 0; i < 9; i++) {
            float a = i/9f;
            float b = (i+1)/9f;
            BladeSample to = i == 8 ? current : bladeSample(Mth.lerp(b,first,poseFrame),side,lift,aim);
            float alpha = opacity * (.16f+.76f*ease(b));
            float[] bright = {1, 1, 1, alpha};
            float[] fromInner = from.point(.63f+.16f*(1-a));
            float[] toInner = to.point(.63f+.16f*(1-b));
            float[] fromRim = from.point(.96f);
            float[] toRim = to.point(.96f);
            EffectGeometry.glowQuad(vertices, matrix, fromInner, toInner, toRim, fromRim, clear, bright);
            EffectGeometry.glowQuad(vertices, matrix, fromRim, toRim, to.point(1), from.point(1), bright, clear);
            from = to;
        }
    }

    static BladeSample bladeSample(float frame, int side, float lift, Vector3f aim) {
        CameraPose pose = cutPose(frame);
        return new BladeSample(pose.origin(side, lift).fma(aimWeight(frame),aim), attackRotation(frame, side));
    }

    static Quaternionf attackRotation(float frame, int side) {
        Quaternionf facing = cutPose(frame).rotation(side);
        float sinceHit = frame-cut.impactTick;
        float edgeWeight = ease((sinceHit+1.95f)/1.25f) * (1-ease((sinceHit-2.15f)/2.0f));
        if (edgeWeight <= 0) return facing;
        float impactTwist = cuttingTwist(cut.impactTick, side);
        float trackFrame = Mth.clamp(frame, cut.impactTick-1.65f, cut.impactTick+1.10f);
        float twist = cuttingTwist(trackFrame, side);
        // Stay on the same side of the half turn as the impact. Let the wrist relax once the
        // blade starts to lose speed, when its instantaneous travel no longer defines the strike.
        double delta = Math.toRadians(Mth.wrapDegrees(Math.toDegrees(twist-impactTwist)));
        return facing.rotateY((impactTwist+(float)delta)*edgeWeight);
    }

    private static float cuttingTwist(float frame, int side) {
        return twistToEdge(cutPose(frame).rotation(side), cuttingDirection(frame, side));
    }

    private static float twistToEdge(Quaternionf facing, Vector3f edge) {
        // Exported OBJ local +X is the sharp side. Rotate only around local +Y (the blade axis).
        Vector3f across = facing.transform(new Vector3f(1,0,0));
        Vector3f face = facing.transform(new Vector3f(0,0,1));
        return (float)Math.atan2(-face.dot(edge), across.dot(edge));
    }

    static Quaternionf readyRotation(int side) {
        return DRAWN_READY.rotation(side);
    }

    static Vector3f cuttingDirection(float frame, int side) {
        CameraPose previous = cutPose(frame-.07f);
        CameraPose next = cutPose(frame+.07f);
        Vector3f axis = cutPose(frame).axis(side);
        Vector3f motion = next.origin(side,0).fma(.72f*BLADE_TIP, next.axis(side))
                .sub(previous.origin(side,0).fma(.72f*BLADE_TIP, previous.axis(side)));
        motion.fma(-motion.dot(axis), axis);
        if (motion.lengthSquared() < 1.0e-7f) return facingRotation(axis,0).transform(new Vector3f(1,0,0));
        return motion.normalize();
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
        }
        return model;
    }

    private static void renderCameraArm(AbstractClientPlayer player, PlayerModel<?> model, HumanoidArm side,
                                        Vector3f palm, Vector3f shoulder, float wrist, float gripFit, PoseStack poses,
                                        MultiBufferSource buffers, int light) {
        boolean right = side == HumanoidArm.RIGHT;
        float sign = right ? 1 : -1;
        float shift = 1-gripFit;
        palm = new Vector3f(palm).add(sign*.049f*shift, -.023f*shift, -.104f*shift);
        Vector3f axis = new Vector3f(palm).sub(shoulder).normalize();
        poses.pushPose();
        poses.translate(palm.x, palm.y, palm.z);
        poses.mulPose(new Quaternionf().rotationTo(0, 1, 0, axis.x, axis.y, axis.z).rotateY(wrist));
        poses.translate(0, -.53f, 0);
        var skin = player.getSkinTextureLocation();
        (right ? model.rightArm : model.leftArm).render(poses,
                buffers.getBuffer(RenderType.entitySolid(skin)), light, OverlayTexture.NO_OVERLAY);
        if (player.isModelPartShown(right ? PlayerModelPart.RIGHT_SLEEVE : PlayerModelPart.LEFT_SLEEVE)) {
            (right ? model.rightSleeve : model.leftSleeve).render(poses,
                    buffers.getBuffer(RenderType.entityTranslucent(skin)), light, OverlayTexture.NO_OVERLAY);
        }
        poses.popPose();
    }

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
        renderProp(player, stack, lead, facingRotation(axis, roll), poses, buffers, light, sheath);
    }

    private static Quaternionf facingRotation(Vector3f axis, float roll) {
        Vector3f across = new Vector3f(axis).cross(0, 0, 1);
        if (across.lengthSquared() < .001f) across.set(1, 0, 0);
        across.normalize();
        Vector3f face = new Vector3f(across).cross(axis).normalize();
        return new Quaternionf().setFromNormalized(
                new Matrix3f().setColumn(0, across).setColumn(1, axis).setColumn(2, face)).rotateY(roll);
    }

    private static void renderProp(AbstractClientPlayer player, ItemStack stack, Vector3f lead, Quaternionf rotation,
                                   PoseStack poses, MultiBufferSource buffers, int light, boolean sheath) {
        poses.pushPose();
        poses.translate(lead.x, lead.y, lead.z);
        poses.mulPose(rotation);
        Minecraft client = Minecraft.getInstance();
        if (sheath) {
            var model = client.getModelManager().getModel(SCABBARD);
            client.getItemRenderer().render(stack, ItemDisplayContext.NONE, false, poses, buffers,
                    light, OverlayTexture.NO_OVERLAY, model);
        } else {
            client.getItemRenderer().renderStatic(player, stack, ItemDisplayContext.NONE,
                    false, poses, buffers, player.level(), light, OverlayTexture.NO_OVERLAY, player.getId());
        }
        poses.popPose();
    }

    private record Hold(Vector3f lead, Vector3f rear, Vector3f bladeAxis) {}

    record BladeSample(Vector3f origin, Quaternionf rotation) {
        Vector3f contact() {
            return rotation.transform(new Vector3f(0,CONTACT_DISTANCE,0)).add(origin);
        }

        private float[] point(float along) {
            float edge = Mth.lerp(ease((along-.947f)/.053f),EDGE_OFFSET,-.018f);
            Vector3f p = rotation.transform(new Vector3f(edge,BLADE_TIP*along,0)).add(origin);
            return new float[]{p.x,p.y,p.z+.004f};
        }
    }

    private record Key(int tick, CameraPose pose) {}

    private record ArmBeats(Gesture windup, Gesture impact, Gesture follow) {}

    private record Gesture(float elbowX, float elbowY, float elbowZ, float mainWrist,
                           float freeX, float freeY, float freeZ, float supportWrist, float handFit) {
        private Gesture(float elbowX, float elbowY, float elbowZ, float mainWrist,
                        float freeX, float freeY, float freeZ, float supportWrist) {
            this(elbowX, elbowY, elbowZ, mainWrist, freeX, freeY, freeZ, supportWrist, 0);
        }

        private Vector3f mainShoulder(int side) { return new Vector3f(side*elbowX,elbowY,elbowZ); }
        private Vector3f supportPalm(int side, float lift) { return new Vector3f(side*freeX,freeY+lift,freeZ); }
        private Gesture blend(Gesture next, float t) {
            return new Gesture(Mth.lerp(t,elbowX,next.elbowX), Mth.lerp(t,elbowY,next.elbowY),
                    Mth.lerp(t,elbowZ,next.elbowZ), Mth.lerp(t,mainWrist,next.mainWrist),
                    Mth.lerp(t,freeX,next.freeX), Mth.lerp(t,freeY,next.freeY),
                    Mth.lerp(t,freeZ,next.freeZ), Mth.lerp(t,supportWrist,next.supportWrist),
                    Mth.lerp(t,handFit,next.handFit));
        }
    }

    private enum Cut {
        DRAW(7, 9, CROSS_ARMS, new Key(DRAW_TICKS, DRAW_CLEAR), new Key(5, DRAW_WINDUP.turnInView(DRAW_TURN)),
                new Key(7, CameraPose.onAim(-1.49f, -.87f, .26f, -.42f, .05f).turnInView(DRAW_TURN)),
                new Key(9, DRAW_FOLLOW.turnInView(DRAW_TURN)), new Key(12, RECOVERY_CLEAR), new Key(15, DRAWN_READY)),
        RETURN(4, 6, RETURN_ARMS,
                new Key(2, new CameraPose(-.71f, -.59f, -1.16f, .51f, .09f, -.86f, -.38f).turnInView(RISING_TURN)),
                new Key(4, CameraPose.onAim(-1.49f, .80f, .43f, -.42f, .06f).turnInView(RISING_TURN)),
                new Key(6, new CameraPose(.40f, .31f, -1.17f, .62f, .66f, -.44f, .42f).turnInView(RISING_TURN)),
                new Key(9, RECOVERY_CLEAR), new Key(13, DRAWN_READY)),
        FINISH(9, 11, FINISH_ARMS,
                new Key(3, new CameraPose(.20f, .25f, -1.36f, .43f, .54f, -.72f, .66f)),
                new Key(6, new CameraPose(-.21f, .73f, -1.35f, .39f, .20f, -.90f, -.09f)),
                new Key(7, new CameraPose(-.24f, .71f, -1.35f, .39f, .13f, -.91f, -.19f)),
                new Key(9, CameraPose.onAim(-1.96f, .36f, -.13f, -.92f, -.35f)),
                new Key(11, new CameraPose(-.25f, -.46f, -1.34f, .33f, -.24f, -.91f, -.41f)),
                new Key(15, RECOVERY_CLEAR), new Key(19, DRAWN_READY)),
        CROSS(5, 7, SWEEP_ARMS,
                new Key(3, new CameraPose(-.76f, .50f, -1.31f, -.21f, -.82f, -.53f, -.58f)),
                new Key(5, CameraPose.onAim(-1.47f, -.94f, .04f, -.34f, .10f).turnInView(Mth.HALF_PI)),
                new Key(7, new CameraPose(.72f, .42f, -1.32f, .25f, -.82f, -.51f, .59f)),
                new Key(10, new CameraPose(.48f, -.42f, -1.43f, .62f, .20f, -.76f, .63f)),
                new Key(13, DRAWN_READY));

        private final int impactTick;
        private final int chainTick;
        private final ArmBeats arms;
        private final Key[] keys;

        Cut(int impactTick, int chainTick, ArmBeats arms, Key... keys) {
            this.impactTick = impactTick;
            this.chainTick = chainTick;
            this.arms = arms;
            this.keys = keys;
        }

        private int duration() { return keys[keys.length-1].tick; }
    }

    private record CameraPose(float x, float y, float z, float dx, float dy, float dz, float roll) {
        private Vector3f origin(int side, float lift) { return new Vector3f(side*x, y+lift, z); }
        private Vector3f axis(int side) { return new Vector3f(side*dx, dy, dz).normalize(); }

        private Quaternionf rotation(int side) {
            // The edge is on local +X. Mirror the edge vector as well as the length axis.
            return facingRotation(axis(side), side > 0 ? roll : Mth.PI-roll);
        }

        private CameraPose withRoll(float angle) {
            return new CameraPose(x,y,z,dx,dy,dz,angle);
        }

        private CameraPose turnInView(float radians) {
            float c = Mth.cos(radians), s = Mth.sin(radians);
            return new CameraPose(c*x-s*y, s*x+c*y, z, c*dx-s*dy, s*dx+c*dy, dz, roll);
        }

        private static CameraPose onAim(float depth, float dx, float dy, float dz, float roll) {
            Vector3f offset = new Vector3f(dx,dy,dz).normalize().mul(-CONTACT_DISTANCE);
            return new CameraPose(offset.x, offset.y, depth+offset.z, dx, dy, dz, roll);
        }

        private CameraPose shiftAlong(float distance) {
            Vector3f point = origin(1, 0).fma(distance, axis(1));
            return new CameraPose(point.x, point.y, point.z, dx, dy, dz, roll);
        }

        private static CameraPose between(CameraPose a, CameraPose b, float t) {
            return new CameraPose(Mth.lerp(t,a.x,b.x), Mth.lerp(t,a.y,b.y), Mth.lerp(t,a.z,b.z),
                    Mth.lerp(t,a.dx,b.dx), Mth.lerp(t,a.dy,b.dy), Mth.lerp(t,a.dz,b.dz),
                    Mth.lerp(t,a.roll,b.roll));
        }

        private static CameraPose curve(CameraPose before, CameraPose from, CameraPose to, CameraPose after,
                                        float progress, float enter, float leave) {
            float t = Mth.clamp(progress,0,1), s = 1-t;
            float end = t*t*(3-2*t), start = 1-end;
            float tangentIn = t*s*s*enter, tangentOut = -t*t*s*leave;
            return new CameraPose(
                    start*from.x+end*to.x+tangentIn*(to.x-before.x)+tangentOut*(after.x-from.x),
                    start*from.y+end*to.y+tangentIn*(to.y-before.y)+tangentOut*(after.y-from.y),
                    start*from.z+end*to.z+tangentIn*(to.z-before.z)+tangentOut*(after.z-from.z),
                    start*from.dx+end*to.dx+tangentIn*(to.dx-before.dx)+tangentOut*(after.dx-from.dx),
                    start*from.dy+end*to.dy+tangentIn*(to.dy-before.dy)+tangentOut*(after.dy-from.dy),
                    start*from.dz+end*to.dz+tangentIn*(to.dz-before.dz)+tangentOut*(after.dz-from.dz),
                    start*from.roll+end*to.roll+tangentIn*(to.roll-before.roll)+tangentOut*(after.roll-from.roll));
        }
    }
}
