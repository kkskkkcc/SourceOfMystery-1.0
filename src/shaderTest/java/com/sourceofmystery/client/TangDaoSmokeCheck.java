package com.sourceofmystery.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.systems.RenderSystem;
import com.sourceofmystery.SourceOfMystery;
import com.sourceofmystery.item.ModItems;
import net.minecraft.Util;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.opengl.GL11;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/** Isolated production client fixture for both grips and the normal attack-key path. */
@Mod.EventBusSubscriber(modid = SourceOfMystery.MOD_ID, value = Dist.CLIENT)
public final class TangDaoSmokeCheck {
    private enum Shot {
        SHEATHED("01-first-sheathed"), DRAW("02-draw", 1, 3),
        FIRST_IMPACT("03-white-draw-cut", 1, 7), CROSS_TRAVEL("03b-cross-body-follow-through", 1, 9),
        TAIL_BEAT("03c-tail-pause-0-95s", 1, 28), REGRIP("03d-regrip-clearance", 1, 33),
        READY("04-drawn-ready", 1, 38),
        SECOND_IMPACT("05-white-return-cut", 2, 4), OVERHEAD("06a-overhead-grip", 3, 6),
        THIRD_IMPACT("06-white-finish", 3, 9), OVERHEAD_FOLLOW("06a-overhead-follow-through", 3, 11),
        FOURTH_IMPACT("06b-buffered-sweep", 4, 6),
        INSPECT_FACE("06c-inspect-face", 4, 54), INSPECT_REVERSE("06d-inspect-reverse", 4, 76),
        INSPECT_READY("06e-inspect-return", 4, 99),
        HOLD("07-hold-9-95s", 4, 233), SHEATHING("08-auto-sheathe", 4, 238),
        RESHEATHED("09-first-resheathed", 4, 244), THIRD_FRONT("10-third-front"),
        THIRD_BACK("11-third-back"), FIRST_LEFT("12-first-left"), THIRD_LEFT("13-third-left"),
        OFFHAND("14-busy-offhand"), INVENTORY("15-inventory");

        final String file;
        final int requiredInputs;
        final int captureTick;
        Shot(String file) { this(file, 0, 0); }
        Shot(String file, int requiredInputs, int captureTick) {
            this.file = file;
            this.requiredInputs = requiredInputs;
            this.captureTick = captureTick;
        }
        boolean leftHanded() { return this == FIRST_LEFT || this == THIRD_LEFT; }
    }
    private static final Shot[] SHOTS = Shot.values();
    private static int ticks;
    private static int stage;
    private static boolean frameReady;
    private static long stageTime;
    private static int attackInputTick;
    private static int attackInputs;
    private static int requestedInputs;
    private static boolean requestedInspect;
    private static volatile int targetId;
    private static final List<Integer> IMPACT_FRAMES = new ArrayList<>();
    private static final List<Integer> CLICK_DELAYS = new ArrayList<>();
    private static final List<Float> EDGE_ALIGNMENT = new ArrayList<>();
    private static final List<Float> AIM_ERRORS = new ArrayList<>();
    private static final AtomicInteger SERVER_HITS = new AtomicInteger();
    private static volatile boolean worldReady;
    private static volatile Throwable failure;

    private TangDaoSmokeCheck() {}

    private static boolean enabled() {
        return Boolean.getBoolean("sourceofmystery.tangDaoCheck");
    }

    private static void setup(ServerPlayer player) {
        var level = player.serverLevel();
        level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, player.server);
        level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, player.server);
        level.setDayTime(3000);
        for (int x = -10; x <= 10; x++) {
            for (int z = -10; z <= 10; z++) {
                level.setBlock(new BlockPos(x,199,z), Blocks.SMOOTH_STONE.defaultBlockState(), 2);
            }
        }
        player.setGameMode(GameType.CREATIVE);
        player.setNoGravity(true);
        player.getInventory().clearContent();
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.TANG_DAO.get()));
        player.getInventory().selected = 0;
        player.teleportTo(level,.5,200,.5,180,3);
        var target = EntityType.HUSK.create(level);
        if (target == null) throw new AssertionError("Could not create the timing-test target");
        target.setNoAi(true);
        target.setNoGravity(true);
        target.setPersistenceRequired();
        target.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);
        target.moveTo(.5, 200, -1.7, 0, 0);
        level.addFreshEntity(target);
        targetId = target.getId();
        worldReady = true;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void attack(InputEvent.InteractionKeyMappingTriggered event) {
        if (!enabled() || !event.isAttack()) return;
        if (attackInputs == requestedInputs) {
            // Keep physical input reaching an isolated test window out of the scripted timing sample.
            event.setCanceled(true);
            event.setSwingHand(false);
            return;
        }
        attackInputs++;
        attackInputTick = ticks;
    }

    @SubscribeEvent
    public static void clientImpact(AttackEntityEvent event) {
        if (enabled() && event.getEntity().level().isClientSide && event.getTarget().getId() == targetId) {
            int delay = ticks-attackInputTick+1;
            int frame = delay-(IMPACT_FRAMES.size() == 3 ? 1 : 0);
            CLICK_DELAYS.add(delay);
            IMPACT_FRAMES.add(frame);
            int strike = IMPACT_FRAMES.size();
            if (strike == 3 && (TangDaoPresentation.attackGrip(frame-3) < .999f
                    || TangDaoPresentation.attackGrip(frame) < .999f
                    || TangDaoPresentation.attackGrip(frame+2) < .999f)) {
                throw new AssertionError("Overhead cut must stay in a two-hand grip through follow-through");
            }
            for (int side : new int[]{1,-1}) {
                var rotation = TangDaoPresentation.attackRotation(frame, side);
                Vector3f edge = rotation.transform(new Vector3f(1,0,0));
                Vector3f blade = rotation.transform(new Vector3f(0,1,0));
                float alignment = edge.dot(TangDaoPresentation.cuttingDirection(frame, side));
                if (!(alignment >= .999f)) throw new AssertionError("Knife edge points away from its path: " + alignment);
                switch (strike) {
                    case 1 -> checkTurn(blade, side, -.87f, .26f, 30);
                    case 2 -> checkTurn(blade, side, .80f, .43f, 50);
                    case 3 -> {
                        var raised = TangDaoPresentation.bladeSample(frame-2, side, 0, new Vector3f());
                        var lowered = TangDaoPresentation.bladeSample(frame+2, side, 0, new Vector3f());
                        if (edge.y >= -.90f || Math.abs(blade.y) > .25f || raised.origin().y < .65f
                                || lowered.origin().y > -.35f || raised.contact().y - lowered.contact().y < 1.1f
                                || raised.rotation().transform(new Vector3f(0,1,0))
                                        .dot(lowered.rotation().transform(new Vector3f(0,1,0))) < .90f) {
                            throw new AssertionError("Overhead cut must lower the grip and blade together from above the view");
                        }
                    }
                    case 4 -> checkTurn(blade, side, -.94f, .04f, 90);
                }
                EDGE_ALIGNMENT.add(alignment);
                Vector3f previous = TangDaoPresentation.attackRotation(frame-2, side).transform(new Vector3f(1,0,0));
                for (int sample = 1; sample <= 120; sample++) {
                    Vector3f current = TangDaoPresentation.attackRotation(frame-2+sample*.05f, side)
                            .transform(new Vector3f(1,0,0));
                    if (previous.dot(current) < .85f) {
                        throw new AssertionError("Knife flips near impact " + frame + " at sample " + sample);
                    }
                    previous = current;
                }
            }
        }
    }

    private static void checkTurn(Vector3f blade, int side, float oldX, float oldY, float expected) {
        double offset = Math.toDegrees(Math.atan2(blade.y, blade.x) - Math.atan2(oldY, side*oldX));
        if (Math.abs(Mth.wrapDegrees(offset)-side*expected) > .03) {
            throw new AssertionError("Unexpected blade angle at impact: " + offset + " instead of " + side*expected);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void renderedAim(RenderHandEvent event) {
        if (!enabled() || event.getHand() != InteractionHand.MAIN_HAND || IMPACT_FRAMES.isEmpty()
                || AIM_ERRORS.size() == 2*IMPACT_FRAMES.size()) return;
        float frame = IMPACT_FRAMES.get(IMPACT_FRAMES.size()-1);
        Matrix4f handToClip = new Matrix4f(RenderSystem.getProjectionMatrix()).mul(event.getPoseStack().last().pose());
        for (int side : new int[]{1, -1}) {
            // Include an equip offset, plus a tilted view to exercise the projection correction on both hands.
            float lift = -.047f;
            Matrix4f view = new Matrix4f(handToClip).rotateZ(side*.035f);
            Vector3f aim = TangDaoPresentation.aimOffset(view, side, lift);
            var blade = TangDaoPresentation.bladeSample(frame, side, lift, aim);
            Vector3f center = view.transformProject(blade.contact());
            float error = (float)Math.hypot(center.x, center.y);
            Vector3f before = view.transformProject(TangDaoPresentation.bladeSample(frame-.45f, side, lift, aim).contact()).setComponent(2,0);
            Vector3f after = view.transformProject(TangDaoPresentation.bladeSample(frame+.45f, side, lift, aim).contact()).setComponent(2,0);
            if (IMPACT_FRAMES.size() == 4 && !(side*before.x < -.06f && side*after.x > .06f)) {
                throw new AssertionError("Last swipe must cross from left to right for a right-handed player");
            }
            if (!(error < .0001f && before.lengthSquared() > .0009f && after.lengthSquared() > .0009f
                    && before.normalize().dot(after.normalize()) < -.85f)) {
                throw new AssertionError("Blade missed or failed to cross the reticle: " + center + " " + before + " " + after);
            }
            AIM_ERRORS.add(error);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void serverImpact(LivingDamageEvent event) {
        if (enabled() && !event.getEntity().level().isClientSide && event.getEntity().getId() == targetId
                && event.getSource().getEntity() instanceof ServerPlayer && event.getAmount() > 0) {
            SERVER_HITS.incrementAndGet();
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void tick(TickEvent.ClientTickEvent event) {
        if (!enabled() || event.phase != TickEvent.Phase.END) return;
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.getSingleplayerServer() == null) return;
        if (failure != null) throw new AssertionError("Tang Dao fixture setup failed", failure);
        if (++ticks > 940) throw new AssertionError("Tang Dao check timed out at " + stage);
        if (ticks == 50) {
            Vector3f readyRight = TangDaoPresentation.readyRotation(1).transform(new Vector3f(1,0,0));
            Vector3f readyLeft = TangDaoPresentation.readyRotation(-1).transform(new Vector3f(1,0,0));
            if (readyRight.z > -.90f || readyLeft.z > -.90f
                    || readyRight.distance(new Vector3f(-readyLeft.x,readyLeft.y,readyLeft.z)) > .0001f) {
                throw new AssertionError("Drawn guard edge must point forward with either hand: " + readyRight + " / " + readyLeft);
            }
            client.setScreen(null);
            client.options.pauseOnLostFocus = false;
            client.options.fov().set(70);
            client.options.bobView().set(false);
            client.options.getSoundSourceOptionInstance(SoundSource.MASTER).set(0d);
            client.getSingleplayerServer().execute(() -> {
                try {
                    setup(client.getSingleplayerServer().getPlayerList().getPlayers().get(0));
                } catch (Throwable error) {
                    failure = error;
                }
            });
        }
        var player = client.player;
        if (!worldReady || !player.getMainHandItem().is(ModItems.TANG_DAO.get())) return;
        if (stageTime == 0) {
            if (stage == SHOTS.length) {
                if (attackInputs != 4 || SERVER_HITS.get() != 4 || !requestedInspect || AIM_ERRORS.size() != 8
                        || !IMPACT_FRAMES.equals(List.of(7,4,9,5)) || !CLICK_DELAYS.equals(List.of(7,4,9,6))) {
                    throw new AssertionError("Tang Dao timing inputs=" + attackInputs + " impacts=" + IMPACT_FRAMES
                            + " click delays=" + CLICK_DELAYS + " aim errors=" + AIM_ERRORS + " server hits=" + SERVER_HITS.get());
                }
                SourceOfMystery.LOGGER.info("TANG_DAO_OK frames={} attack_keys={} impact_ticks={} click_delays={} server_hits={} edge_alignment={} reticle_error={} inspection={} shaders={}",
                        SHOTS.length, attackInputs, IMPACT_FRAMES, CLICK_DELAYS, SERVER_HITS.get(), EDGE_ALIGNMENT,
                        AIM_ERRORS, requestedInspect, OutsiderShaderCompat.active());
                client.stop();
                return;
            }
            configureStage(client);
            stageTime = Util.getMillis();
        }
        Shot shot = SHOTS[stage];
        long settleTime = shot == Shot.SHEATHED ? 1800 : 700;
        frameReady = shot.captureTick != 0 || Util.getMillis() - stageTime >= settleTime;
        if ((shot == Shot.SECOND_IMPACT || shot == Shot.OVERHEAD) && requestedInputs < shot.requiredInputs
                && ticks-attackInputTick >= 70) {
            tap(client);
        }
        if (shot == Shot.FOURTH_IMPACT && requestedInputs < shot.requiredInputs && ticks-attackInputTick >= 20) {
            tap(client);
        }
        if (shot == Shot.INSPECT_FACE && !requestedInspect && ticks-attackInputTick == 42) {
            requestedInspect = true;
            KeyMapping.click(InputConstants.getKey(TangDaoPresentation.INSPECT.saveString()));
        }
        player.yBodyRot = player.yBodyRotO = 180;
        player.yHeadRot = player.yHeadRotO = 180;
    }

    private static void configureStage(Minecraft client) {
        Shot shot = SHOTS[stage];
        var camera = switch (shot) {
            case THIRD_FRONT, THIRD_LEFT -> CameraType.THIRD_PERSON_FRONT;
            case THIRD_BACK -> CameraType.THIRD_PERSON_BACK;
            default -> CameraType.FIRST_PERSON;
        };
        client.options.setCameraType(camera);
        client.player.setYRot(shot == Shot.THIRD_BACK ? 151 : shot == Shot.THIRD_FRONT ? 205 : 180);
        client.player.yRotO = client.player.getYRot();
        client.getToasts().clear();
        client.gui.getChat().clearMessages(true);
        if (shot == Shot.INVENTORY) client.setScreen(new InventoryScreen(client.player));
        if (shot == Shot.DRAW) tap(client);
        if (stage >= Shot.THIRD_FRONT.ordinal()) {
            HumanoidArm arm = shot.leftHanded() ? HumanoidArm.LEFT : HumanoidArm.RIGHT;
            ItemStack offhand = shot == Shot.OFFHAND ? new ItemStack(Items.TORCH) : ItemStack.EMPTY;
            client.options.mainHand().set(arm);
            client.player.setMainArm(arm);
            client.player.setItemInHand(InteractionHand.OFF_HAND, offhand.copy());
            client.getSingleplayerServer().execute(() -> {
                ServerPlayer player = client.getSingleplayerServer().getPlayerList().getPlayers().get(0);
                player.setMainArm(arm);
                player.setItemInHand(InteractionHand.OFF_HAND, offhand);
                if (shot == Shot.THIRD_FRONT) {
                    var target = player.serverLevel().getEntity(targetId);
                    if (target != null) target.discard();
                }
            });
        }
    }

    private static void tap(Minecraft client) {
        requestedInputs++;
        KeyMapping.click(InputConstants.getKey(client.options.keyAttack.saveString()));
    }

    @SubscribeEvent
    public static void frame(TickEvent.RenderTickEvent event) {
        if (!enabled() || event.phase != TickEvent.Phase.END || !frameReady || stage >= SHOTS.length) return;
        var client = Minecraft.getInstance();
        if (client.player == null || !client.player.getMainHandItem().is(ModItems.TANG_DAO.get())) return;
        Shot shot = SHOTS[stage];
        if (client.player.getMainArm() != (shot.leftHanded() ? HumanoidArm.LEFT : HumanoidArm.RIGHT)) return;
        if (shot == Shot.OFFHAND && !client.player.getOffhandItem().is(Items.TORCH)) return;
        if (attackInputs < shot.requiredInputs || ticks-attackInputTick < shot.captureTick) return;
        var model = client.getItemRenderer().getModel(client.player.getMainHandItem(), client.level, client.player, 0);
        if (model == client.getModelManager().getMissingModel()) throw new AssertionError("Tang Dao loaded a missing model");
        var scabbard = client.getModelManager().getModel(new ResourceLocation(SourceOfMystery.MOD_ID, "item/tang_dao_scabbard"));
        if (scabbard == client.getModelManager().getMissingModel()) throw new AssertionError("Tang Dao scabbard is missing");
        Screenshot.grab(client.gameDirectory, shot.file + ".png", client.getMainRenderTarget(), message -> {});
        if (GL11.glGetError() != 0) throw new AssertionError("Tang Dao OpenGL error");
        stage++;
        stageTime = 0;
        frameReady = false;
    }
}
