package com.sourceofmystery.client;

import com.sourceofmystery.SourceOfMystery;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.Input;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Boss 出场镜头：播放期间锁定移动、隐藏 HUD、上下加电影黑边，视角平滑跟随 Boss，
 * Boss 离得远时拉近焦距。结束后视角停在 Boss 身上，玩家直接接管。
 */
@Mod.EventBusSubscriber(modid = SourceOfMystery.MOD_ID, value = Dist.CLIENT)
public final class BossIntroCamera {

    private static final int BAR_FADE_TICKS = 20;
    private static final float TURN_SPEED = 0.08f;   // 每帧向目标角度靠近的比例

    private static float camYaw;
    private static float camPitch;
    private static boolean tracking;
    private static double fovScale = 1.0;

    private BossIntroCamera() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (ClientCinematicCache.flashRemaining > 0) {
            ClientCinematicCache.flashRemaining--;
        }
        if (ClientCinematicCache.shakeRemaining > 0) {
            ClientCinematicCache.shakeRemaining--;
        }
        if (ClientCinematicCache.rootTicks > 0) {
            ClientCinematicCache.rootTicks--;
        }
        if (!ClientCinematicCache.active()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.level.getEntity(ClientCinematicCache.entityId) == null) {
            // 实体还没同步过来时先等一会儿；出场结束或 Boss 消失就收尾
            if (ClientCinematicCache.totalTicks - ClientCinematicCache.remainingTicks > 40) {
                stop();
                return;
            }
        }
        if (--ClientCinematicCache.remainingTicks <= 0) {
            stop();
        }
    }

    private static void stop() {
        WeepingIntroDirector.stop();
        ClientCinematicCache.reset();
        tracking = false;
        fovScale = 1.0;
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        stop();
        ClientCinematicCache.resetScreenEffects();
    }

    @SubscribeEvent
    public static void onMovementInput(MovementInputUpdateEvent event) {
        if (!ClientCinematicCache.active() && ClientCinematicCache.rootTicks <= 0) {
            return;
        }
        Input input = event.getInput();
        input.forwardImpulse = 0;
        input.leftImpulse = 0;
        input.up = false;
        input.down = false;
        input.left = false;
        input.right = false;
        input.jumping = false;
        input.shiftKeyDown = false;
    }

    @SubscribeEvent
    public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return;
        }
        if (!ClientCinematicCache.active()) {
            applyShake(event);
            return;
        }
        if (ClientCinematicCache.scripted) {
            WeepingIntroDirector.applyAngles(event);
            applyShake(event);
            return;
        }
        Entity boss = mc.level.getEntity(ClientCinematicCache.entityId);
        if (boss == null) {
            return;
        }
        float partial = (float) event.getPartialTick();
        Vec3 eye = event.getCamera().getPosition();
        Vec3 focus = boss.getPosition(partial).add(0, boss.getBbHeight() * 0.6, 0);
        Vec3 d = focus.subtract(eye);
        float targetYaw = (float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90.0f;
        float targetPitch = (float) -(Mth.atan2(d.y, d.horizontalDistance()) * Mth.RAD_TO_DEG);
        if (!tracking) {
            camYaw = event.getYaw();
            camPitch = event.getPitch();
            tracking = true;
        }
        camYaw = camYaw + Mth.wrapDegrees(targetYaw - camYaw) * TURN_SPEED;
        camPitch = camPitch + (targetPitch - camPitch) * TURN_SPEED;
        event.setYaw(camYaw);
        event.setPitch(camPitch);
        applyShake(event);
        // 让玩家本身也朝向镜头方向，镜头结束时不会突然跳回原来的视角
        mc.player.setYRot(camYaw);
        mc.player.setXRot(camPitch);
        mc.player.yRotO = camYaw;
        mc.player.xRotO = camPitch;

        // 远处的 Boss 拉近一些（最多放大到 2 倍），靠近时恢复正常
        double distance = d.length();
        double wanted = Mth.clamp(distance / 40.0, 0.5, 1.0);
        fovScale += (wanted - fovScale) * 0.05;
    }

    /**
     * 震屏：爆炸等事件期间镜头随机抖动，强度随剩余时间衰减
     */
    static void applyShake(ViewportEvent.ComputeCameraAngles event) {
        if (ClientCinematicCache.shakeRemaining <= 0) {
            return;
        }
        float t = (float) (Minecraft.getInstance().level.getGameTime() + event.getPartialTick());
        float strength = ClientCinematicCache.shakeStrength * Math.min(1.0f, ClientCinematicCache.shakeRemaining / 20.0f);
        event.setYaw(event.getYaw() + Mth.sin(t * 2.3f) * strength);
        event.setPitch(event.getPitch() + Mth.sin(t * 3.1f + 1.3f) * strength * 0.7f);
        event.setRoll(event.getRoll() + Mth.sin(t * 1.7f + 0.5f) * strength * 0.5f);
    }

    @SubscribeEvent
    public static void onFov(ViewportEvent.ComputeFov event) {
        if (ClientCinematicCache.active() && ClientCinematicCache.scripted) {
            WeepingIntroDirector.applyFov(event);
        } else if (ClientCinematicCache.active() && event.usedConfiguredFov()) {
            event.setFOV(event.getFOV() * fovScale);
        }
    }

    @SubscribeEvent
    public static void onOverlay(RenderGuiOverlayEvent.Pre event) {
        if (!ClientCinematicCache.active()) {
            return;
        }
        var id = event.getOverlay().id();
        // 保留聊天栏、字幕和动作栏（Boss 台词的中文字幕显示在动作栏）
        if (!id.equals(VanillaGuiOverlay.CHAT_PANEL.id()) && !id.equals(VanillaGuiOverlay.SUBTITLES.id())
                && !id.equals(VanillaGuiOverlay.RECORD_OVERLAY.id())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        renderFlash(event);
        if (!ClientCinematicCache.active()) {
            return;
        }
        int elapsed = ClientCinematicCache.totalTicks - ClientCinematicCache.remainingTicks;
        float in = Math.min(1.0f, (elapsed + event.getPartialTick()) / BAR_FADE_TICKS);
        float out = Math.min(1.0f, ClientCinematicCache.remainingTicks / (float) BAR_FADE_TICKS);
        float amount = Math.min(in, out);
        int width = event.getWindow().getGuiScaledWidth();
        int height = event.getWindow().getGuiScaledHeight();
        int bar = (int) (height * 0.12f * amount);
        if (bar > 0) {
            event.getGuiGraphics().fill(0, 0, width, bar, 0xFF000000);
            event.getGuiGraphics().fill(0, height - bar, width, height, 0xFF000000);
        }
    }

    /**
     * 闪白：从纯白慢慢淡出
     */
    private static void renderFlash(RenderGuiEvent.Post event) {
        if (ClientCinematicCache.flashRemaining <= 0 || ClientCinematicCache.flashTotal <= 0) {
            return;
        }
        float u = (ClientCinematicCache.flashRemaining - event.getPartialTick()) / ClientCinematicCache.flashTotal;
        int alpha = (int) (255 * Mth.clamp(u * u, 0.0f, 1.0f));
        if (alpha > 0) {
            int width = event.getWindow().getGuiScaledWidth();
            int height = event.getWindow().getGuiScaledHeight();
            event.getGuiGraphics().fill(0, 0, width, height, (alpha << 24) | 0xFFFFFF);
        }
    }
}
