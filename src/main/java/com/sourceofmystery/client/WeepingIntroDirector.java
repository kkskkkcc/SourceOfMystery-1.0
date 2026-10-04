package com.sourceofmystery.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.sourceofmystery.SourceOfMystery;
import com.sourceofmystery.entity.WeepingDeathLord;
import com.sourceofmystery.sound.ModSounds;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Marker;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 泣死之主出场的脚本运镜。镜头挂在一个只存在于客户端的 Marker 实体上（Minecraft#setCameraEntity），
 * 每帧按时间表计算位置和朝向：
 * <pre>
 *   0 ~ 80    远景：从侧面看玩家与凋灵对峙（凋灵变白旋转、爆炸、她蜷缩漂浮）
 *  80 ~ 180   脸部特写：缓缓睁眼
 * 180 ~ 280   拉远到中景：张开双臂咆哮、镰刀飞回、扛起镰刀
 * 280 ~ 350   中景运镜：从玩家身前绕到玩家身后
 * 350 ~ 390   定格：玩家与她对峙
 * </pre>
 * 镜头不在玩家身上时原版不画本地玩家，所以这里在实体渲染完后手动补画。
 * 字幕栏、黑边、隐藏 HUD、锁定移动沿用 BossIntroCamera（ClientCinematicCache.scripted）。
 */
@Mod.EventBusSubscriber(modid = SourceOfMystery.MOD_ID, value = Dist.CLIENT)
public final class WeepingIntroDirector {

    private static final double CURL_FACE_HEIGHT = 2.14;
    private static final double AWAKE_FACE_HEIGHT = 2.55;
    private static final double FACE_FORWARD = 0.95;
    private static final double CHEST_HEIGHT = 2.0;

    private static Marker camera;
    private static CameraType savedCameraType;
    private static boolean running;
    private static boolean roarPlayed;
    private static float yaw;
    private static float pitch;
    private static double fov = 70;

    private WeepingIntroDirector() {
    }

    private static boolean wanted() {
        return ClientCinematicCache.active() && ClientCinematicCache.scripted;
    }

    private static void ensureStarted(Minecraft mc) {
        if (running || mc.level == null || mc.player == null) {
            return;
        }
        camera = new Marker(EntityType.MARKER, mc.level);
        camera.setPos(mc.player.getEyePosition());
        savedCameraType = mc.options.getCameraType();
        mc.options.setCameraType(CameraType.FIRST_PERSON);
        mc.setCameraEntity(camera);
        running = true;
        roarPlayed = false;
    }

    /** 结束运镜，镜头还给玩家（BossIntroCamera 收尾、退出世界时调用） */
    public static void stop() {
        if (!running) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.setCameraEntity(mc.player);
        }
        if (savedCameraType != null) {
            mc.options.setCameraType(savedCameraType);
        }
        running = false;
        camera = null;
    }

    private static float elapsed(float partialTick) {
        return ClientCinematicCache.totalTicks - ClientCinematicCache.remainingTicks + partialTick;
    }

    @SubscribeEvent
    public static void onRenderTick(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.START) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (!wanted()) {
            stop();
            return;
        }
        ensureStarted(mc);
        if (!running || mc.level == null || mc.player == null) {
            return;
        }
        if (mc.getCameraEntity() != camera) {
            mc.setCameraEntity(camera);
        }
        Shot shot = compute(mc, elapsed(event.renderTickTime));
        camera.setPos(shot.pos.x, shot.pos.y, shot.pos.z);
        camera.xo = shot.pos.x;
        camera.yo = shot.pos.y;
        camera.zo = shot.pos.z;
        camera.xOld = shot.pos.x;
        camera.yOld = shot.pos.y;
        camera.zOld = shot.pos.z;
        Vec3 d = shot.look.subtract(shot.pos);
        yaw = (float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90.0f;
        pitch = (float) -(Mth.atan2(d.y, d.horizontalDistance()) * Mth.RAD_TO_DEG);
        camera.setYRot(yaw);
        camera.setXRot(pitch);
        camera.yRotO = yaw;
        camera.xRotO = pitch;
        fov = shot.fov;
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !running) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        Entity boss = boss(mc);
        if (player == null) {
            return;
        }
        // 玩家始终面向她，最后的对峙画面里是玩家的背影对着她
        Vec3 target = boss != null ? boss.position() : anchor();
        Vec3 d = target.subtract(player.position());
        if (d.horizontalDistanceSqr() > 1.0E-4) {
            float face = (float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90.0f;
            player.setYRot(face);
            player.yRotO = face;
            player.yBodyRot = face;
            player.yHeadRot = face;
            player.setXRot(0);
        }
        if (!roarPlayed && elapsed(0) >= WeepingDeathLord.ROAR_START) {
            roarPlayed = true;
            // 凋灵咆哮：不随距离衰减，镜头在哪都很大声
            RandomSource random = RandomSource.create();
            mc.getSoundManager().play(new SimpleSoundInstance(ModSounds.WEEPING_ROAR.get().getLocation(), SoundSource.HOSTILE,
                    1.0f, 1.0f, random, false, 0, SoundInstance.Attenuation.NONE, 0, 0, 0, true));
            mc.getSoundManager().play(new SimpleSoundInstance(SoundEvents.WITHER_SPAWN.getLocation(), SoundSource.HOSTILE,
                    1.0f, 0.75f, random, false, 0, SoundInstance.Attenuation.NONE, 0, 0, 0, true));
        }
    }

    public static void applyAngles(ViewportEvent.ComputeCameraAngles event) {
        if (running) {
            event.setYaw(yaw);
            event.setPitch(pitch);
            event.setRoll(0);
        }
    }

    public static void applyFov(ViewportEvent.ComputeFov event) {
        if (running) {
            event.setFOV(fov);
        }
    }

    @SubscribeEvent
    public static void onRenderHand(RenderHandEvent event) {
        if (running) {
            event.setCanceled(true);
        }
    }

    /** 镜头不是玩家时原版不画本地玩家：手动补画，最后的对峙画面才看得到玩家 */
    @SubscribeEvent
    public static void onRenderStage(RenderLevelStageEvent event) {
        if (!running || event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || player.isSpectator()) {
            return;
        }
        float pt = event.getPartialTick();
        Vec3 cam = event.getCamera().getPosition();
        double x = Mth.lerp(pt, player.xOld, player.getX()) - cam.x;
        double y = Mth.lerp(pt, player.yOld, player.getY()) - cam.y;
        double z = Mth.lerp(pt, player.zOld, player.getZ()) - cam.z;
        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        var dispatcher = mc.getEntityRenderDispatcher();
        dispatcher.render(player, x, y, z, Mth.lerp(pt, player.yRotO, player.getYRot()), pt, poseStack, buffers,
                dispatcher.getPackedLightCoords(player, pt));
        buffers.endBatch();
    }

    // ==================== 镜头时间表 ====================

    private record Shot(Vec3 pos, Vec3 look, double fov) {
    }

    private static Vec3 anchor() {
        return new Vec3(ClientCinematicCache.anchorX, ClientCinematicCache.anchorY, ClientCinematicCache.anchorZ);
    }

    private static Entity boss(Minecraft mc) {
        return mc.level == null ? null : mc.level.getEntity(ClientCinematicCache.entityId);
    }

    private static Shot compute(Minecraft mc, float t) {
        LocalPlayer player = mc.player;
        Entity boss = boss(mc);
        float pt = mc.getFrameTime();
        Vec3 b = boss != null ? boss.getPosition(pt) : anchor();
        Vec3 p = player.getPosition(pt);
        Vec3 eye = p.add(0, player.getEyeHeight(), 0);
        Vec3 g = horizontal(b.subtract(p));
        double dist = g.length();
        g = dist < 1.0E-3 ? new Vec3(0, 0, 1) : g.scale(1 / dist);
        Vec3 side = new Vec3(-g.z, 0, g.x);
        float bossYaw = boss != null ? Mth.rotLerp(pt, boss.yRotO, boss.getYRot()) : 0;
        Vec3 fwd = new Vec3(-Mth.sin(bossYaw * Mth.DEG_TO_RAD), 0, Mth.cos(bossYaw * Mth.DEG_TO_RAD));

        if (t < WeepingDeathLord.CLOSEUP_START) {
            // 远景：侧面看玩家与凋灵对峙，缓慢推近
            double u = t / WeepingDeathLord.CLOSEUP_START;
            Vec3 mid = eye.add(b.add(0, 1.8, 0)).scale(0.5);
            double back = Math.max(10.0, dist * 0.9 + 6.0) * (1.0 - 0.12 * smooth(u));
            Vec3 pos = mid.add(side.scale(back)).add(0, 2.5 + dist * 0.08, 0);
            return new Shot(pos, mid, 70);
        }
        if (t < WeepingDeathLord.ROAR_START) {
            // 脸部特写：随她抬头缓缓上移、推近
            double u = (t - WeepingDeathLord.CLOSEUP_START) / (WeepingDeathLord.ROAR_START - WeepingDeathLord.CLOSEUP_START);
            double lift = smooth(Math.min(1.0, Math.max(0.0, (u - 0.08) / 0.84)));
            Vec3 face = b.add(0, Mth.lerp(lift, CURL_FACE_HEIGHT, AWAKE_FACE_HEIGHT), 0).add(fwd.scale(FACE_FORWARD));
            double d = Mth.lerp(smooth(u), 2.6, 1.9);
            Vec3 pos = face.add(fwd.scale(d)).add(0, 0.12, 0);
            return new Shot(pos, face, 45);
        }
        if (t < WeepingDeathLord.DOLLY_START) {
            // 咆哮：从脸部迅速拉远到中景，之后缓缓环绕
            double u = Math.min(1.0, (t - WeepingDeathLord.ROAR_START) / 30.0);
            double e = 1 - Math.pow(1 - u, 3);
            Vec3 face = b.add(0, AWAKE_FACE_HEIGHT, 0).add(fwd.scale(FACE_FORWARD));
            Vec3 chest = b.add(0, CHEST_HEIGHT, 0);
            Vec3 look = face.lerp(chest, e);
            double orbit = Math.max(0, t - WeepingDeathLord.ROAR_START - 30) / 70.0 * 12.0;
            Vec3 dir = rotateY(fwd, orbit);
            Vec3 pos = look.add(dir.scale(Mth.lerp(e, 1.9, 8.5))).add(0, Mth.lerp(e, 0.12, 0.8), 0);
            return new Shot(pos, look, Mth.lerp(e, 45, 62));
        }
        // 运镜：镜头从玩家身前（面对玩家）绕到玩家身后，最后越过玩家肩膀看向她，然后定格
        double u = Math.min(1.0, (t - WeepingDeathLord.DOLLY_START) / (WeepingDeathLord.FREEZE_START - WeepingDeathLord.DOLLY_START));
        double e = smooth(u);
        double angle = Math.PI * e;
        double radius = Mth.lerp(e, 2.4, 4.4);
        Vec3 around = g.scale(Math.cos(angle)).add(side.scale(Math.sin(angle)));
        Vec3 pos = eye.add(around.scale(radius)).add(side.scale(1.2 * e)).add(0, Mth.lerp(e, 0.05, 0.9), 0);
        Vec3 look = eye.lerp(b.add(0, CHEST_HEIGHT, 0), smooth(Math.min(1.0, u * 1.3)));
        return new Shot(pos, look, Mth.lerp(e, 60, 70));
    }

    private static double smooth(double u) {
        u = Math.max(0, Math.min(1, u));
        return u * u * (3 - 2 * u);
    }

    private static Vec3 horizontal(Vec3 v) {
        return new Vec3(v.x, 0, v.z);
    }

    private static Vec3 rotateY(Vec3 v, double angleDeg) {
        double rad = Math.toRadians(angleDeg);
        double cos = Math.cos(rad);
        double sin = Math.sin(rad);
        return new Vec3(v.x * cos + v.z * sin, v.y, -v.x * sin + v.z * cos);
    }
}
