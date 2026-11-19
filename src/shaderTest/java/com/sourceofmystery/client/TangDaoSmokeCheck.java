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
        SHEATHED("01-first-sheathed"), DRAW_PULL("02-draw-pull", 1, 2, "draw"), DRAW_IMPACT("03-draw-cut", 1, 4, "draw"),
        DRAW_FOLLOW("03b-draw-follow-through", 1, 6, "draw"),
        RISING_IMPACT("05-rising-cut", 2, 4, "rising"), OVERHEAD_RAISED("06a-overhead-raised", 3, 3, "overhead"),
        OVERHEAD_IMPACT("06-overhead-cut", 3, 7, "overhead"), OVERHEAD_FOLLOW("06b-overhead-follow-through", 3, 8, "overhead"),
        DIAGONAL_IMPACT("07-diagonal-cut", 4, 5, "diagonal"), READY("04-drawn-ready", 4, 22),
        INSPECT_FACE("08a-inspect-face", 4, 36), INSPECT_WIPE("08b-inspect-wipe", 4, 46),
        INSPECT_EDGE("08c-inspect-edge", 4, 64), INSPECT_READY("08d-inspect-return", 4, 84),
        HOLD("09-hold", 4, 270), SHEATHING("10-auto-sheathe", 4, 285), RESHEATHED("11-first-resheathed", 4, 296),
        THIRD_FRONT("12-third-front"), THIRD_BACK("13-third-back"), FIRST_LEFT("14-first-left"),
        THIRD_LEFT("15-third-left"), OFFHAND("16-busy-offhand"), INVENTORY("17-inventory");

        final String file;
        final int requiredInputs;
        final int captureTick;
        /** When set, captureTick counts ticks into this clip instead of ticks since the last click. */
        final String clip;
        Shot(String file) { this(file, 0, 0, null); }
        Shot(String file, int requiredInputs, int captureTick) { this(file, requiredInputs, captureTick, null); }
        Shot(String file, int requiredInputs, int captureTick, String clip) {
            this.file = file;
            this.requiredInputs = requiredInputs;
            this.captureTick = captureTick;
            this.clip = clip;
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
    private static final List<String> IMPACT_CLIPS = new ArrayList<>();
    private static final List<Float> EDGE_LEAD = new ArrayList<>();
    private static final List<Float> CENTRE_MISS = new ArrayList<>();
    private static int checkedImpacts;
    private static final AtomicInteger SERVER_HITS = new AtomicInteger();
    private static final AtomicInteger SERVER_ATTACKS = new AtomicInteger();
    private static volatile boolean worldReady;
    private static boolean worldRequested;
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
            String clip = TangDaoPresentation.playingClip();
            if (clip == null) throw new AssertionError("Impact outside a cut");
            IMPACT_CLIPS.add(clip);
        }
    }

    /** Projects the knife around its impact tick: the blade must sweep through the reticle, edge first. */
    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void renderedAim(RenderHandEvent event) {
        if (!enabled() || event.getHand() != InteractionHand.MAIN_HAND || checkedImpacts == IMPACT_CLIPS.size()) return;
        String clip = IMPACT_CLIPS.get(checkedImpacts++);
        Matrix4f handToClip = new Matrix4f(RenderSystem.getProjectionMatrix()).mul(event.getPoseStack().last().pose());
        float aspect = handToClip.m11() / handToClip.m00();
        int impact = TangDaoPresentation.impactTick(clip);
        float length = TangDaoPresentation.bladeLength();
        for (int side : new int[]{1, -1}) {
            float miss = Float.MAX_VALUE;
            for (float t = impact - 1; t <= impact + 1.001f; t += .05f) {
                var knife = TangDaoPresentation.knifeSample(clip, t, side);
                for (int i = 0; i <= 30; i++) {
                    Vector3f p = handToClip.transformProject(knife.along(length * (.15f + .85f * i / 30), new Vector3f()));
                    miss = Math.min(miss, (float) Math.hypot(p.x * aspect, p.y));
                }
            }
            var before = TangDaoPresentation.knifeSample(clip, impact - .05f, side);
            var after = TangDaoPresentation.knifeSample(clip, impact + .05f, side);
            var now = TangDaoPresentation.knifeSample(clip, impact, side);
            Vector3f motion = after.along(length * .6f, new Vector3f()).sub(before.along(length * .6f, new Vector3f()));
            Vector3f blade = now.rotation.getColumn(1, new Vector3f());
            motion.fma(-motion.dot(blade), blade).normalize();
            float lead = now.rotation.getColumn(0, new Vector3f()).dot(motion);
            if (!(miss < .06f && lead > .8f)) {
                throw new AssertionError(clip + " side " + side + ": blade misses the reticle by " + miss + " or edge lead " + lead);
            }
            CENTRE_MISS.add(miss);
            EDGE_LEAD.add(lead);
        }
    }

    /** Every cut reaches the server as a vanilla attack; damage itself follows vanilla hurt cooldowns. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void serverAttack(AttackEntityEvent event) {
        if (enabled() && !event.getEntity().level().isClientSide && event.getTarget().getId() == targetId) {
            SERVER_ATTACKS.incrementAndGet();
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
        if (client.player == null && openWorld(client)) return;
        if (client.player == null || client.getSingleplayerServer() == null) return;
        if (failure != null) throw new AssertionError("Tang Dao fixture setup failed", failure);
        if (++ticks > 1100) throw new AssertionError("Tang Dao check timed out at " + stage);
        if (ticks == 50) {
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
                List<String> expected = List.of("draw", "rising", "overhead", "diagonal");
                if (attackInputs != 4 || SERVER_ATTACKS.get() != 4 || SERVER_HITS.get() < 2 || !requestedInspect || !IMPACT_CLIPS.equals(expected)
                        || CENTRE_MISS.size() != 8) {
                    throw new AssertionError("Tang Dao inputs=" + attackInputs + " impacts=" + IMPACT_CLIPS
                            + " centre misses=" + CENTRE_MISS + " server attacks=" + SERVER_ATTACKS.get() + " damage=" + SERVER_HITS.get());
                }
                SourceOfMystery.LOGGER.info("TANG_DAO_OK frames={} attack_keys={} impacts={} server_attacks={} damage_events={} edge_lead={} reticle_miss={} inspection={} shaders={}",
                        SHOTS.length, attackInputs, IMPACT_CLIPS, SERVER_ATTACKS.get(), SERVER_HITS.get(), EDGE_LEAD,
                        CENTRE_MISS, requestedInspect, OutsiderShaderCompat.active());
                client.stop();
                return;
            }
            configureStage(client);
            stageTime = Util.getMillis();
        }
        Shot shot = SHOTS[stage];
        long settleTime = shot == Shot.SHEATHED ? 1800 : 700;
        frameReady = shot.captureTick != 0 || Util.getMillis() - stageTime >= settleTime;
        // Draw, then the rising cut while the combo is live, then two clicks kept during the
        // follow-through of each cut so the overhead and diagonal cuts chain.
        int since = ticks - attackInputTick;
        if (requestedInputs == 1 && attackInputs == 1 && since == 18) tap(client);
        if (requestedInputs == 2 && attackInputs == 2 && since == 5) tap(client);
        if (requestedInputs == 3 && attackInputs == 3 && since == 9) tap(client);
        if (shot == Shot.INSPECT_FACE && !requestedInspect && attackInputs == 4 && since == 25) {
            requestedInspect = true;
            KeyMapping.click(InputConstants.getKey(TangDaoPresentation.INSPECT.saveString()));
        }
        player.yBodyRot = player.yBodyRotO = 180;
        player.yHeadRot = player.yHeadRotO = 180;
    }

    /** In a development client (no quick-play world) create a flat test world from the title screen. */
    private static boolean openWorld(Minecraft client) {
        if (client.screen instanceof net.minecraft.client.gui.screens.AccessibilityOnboardingScreen) {
            client.options.onboardAccessibility = false;
            client.options.save();
            client.setScreen(new net.minecraft.client.gui.screens.TitleScreen(true));
            return true;
        }
        if (worldRequested || !(client.screen instanceof net.minecraft.client.gui.screens.TitleScreen)) return false;
        worldRequested = true;
        client.options.tutorialStep = net.minecraft.client.tutorial.TutorialSteps.NONE;
        client.options.guiScale().set(2);
        String name = "tang-dao-check-" + System.currentTimeMillis();
        client.createWorldOpenFlows().createFreshLevel(name, new net.minecraft.world.level.LevelSettings(name, GameType.CREATIVE,
                        false, net.minecraft.world.Difficulty.NORMAL, true, new GameRules(),
                        net.minecraft.world.level.WorldDataConfiguration.DEFAULT),
                new net.minecraft.world.level.levelgen.WorldOptions(2048, false, false),
                r -> r.registryOrThrow(net.minecraft.core.registries.Registries.WORLD_PRESET)
                        .getHolderOrThrow(net.minecraft.world.level.levelgen.presets.WorldPresets.FLAT).value().createWorldDimensions());
        return true;
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
        if (shot == Shot.DRAW_PULL) tap(client);
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
        if (attackInputs < shot.requiredInputs) return;
        if (shot.clip != null ? !shot.clip.equals(TangDaoPresentation.playingClip())
                || TangDaoPresentation.playingTick() != shot.captureTick : ticks-attackInputTick < shot.captureTick) return;
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
