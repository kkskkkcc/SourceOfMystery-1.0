package com.sourceofmystery.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.sourceofmystery.SourceOfMystery;
import com.sourceofmystery.entity.WeepingDeathLord;
import com.sourceofmystery.sound.ModSounds;
import net.minecraft.client.multiplayer.ClientLevel;
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
 * 每帧按现实时间（不是 tick）计算位置和朝向，和服务端的出场时间表、wl_intro 动画一一对应：
 * <pre>
 *   0 ~ 0.8     从玩家视角平滑拉到远景
 *   0.8 ~ 3.3   远景：从侧面看玩家与凋灵对峙（凋灵变白旋转、2 秒爆炸、她蜷缩漂浮），缓慢推近
 *   3.3 ~ 4.5   镜头飞向她的脸
 *   4.5 ~ 9     脸部特写：整整 5 秒缓缓睁眼（镜头随她抬头慢慢上移、推近）
 *   9 ~ 9.7     咆哮：从脸部猛地拉远到中景，之后缓缓环绕（镰刀飞回、扛起镰刀）
 *   14.5 ~ 15.5 镜头滑到玩家身前
 *   15.5 ~ 19   中景运镜：从玩家身前绕到玩家身后
 *   19 ~ 21     定格：玩家与她对峙
 *   21 ~ 21.8   镜头回到玩家眼睛，交还控制
 * </pre>
 * 相邻两段之间都是平滑的过渡（位置、看向的点、视野一起插值），没有硬切。
 * 出场期间天空日夜交替越来越快（只改客户端显示的时间，结束时还原）。
 * 镜头不在玩家身上时原版不画本地玩家，所以这里在实体渲染完后手动补画。
 * 字幕栏、黑边、隐藏 HUD、锁定移动沿用 BossIntroCamera（ClientCinematicCache.scripted）。
 */
@Mod.EventBusSubscriber(modid = SourceOfMystery.MOD_ID, value = Dist.CLIENT)
public final class WeepingIntroDirector {

    private static final double CURL_FACE_HEIGHT = 2.14;
    private static final double AWAKE_FACE_HEIGHT = 2.55;
    private static final double FACE_FORWARD = 0.95;
    private static final double CHEST_HEIGHT = 2.0;

    private static final float EYE_TO_WIDE_END = 0.8f;
    private static final float WIDE_TO_FACE_START = 3.3f;
    private static final float WIDE_TO_FACE_END = 4.5f;
    private static final float PULL_BACK = 0.7f;
    private static final float TO_DOLLY_END = WeepingDeathLord.INTRO_SETTLE + 1.0f;

    private static Marker camera;
    private static CameraType savedCameraType;
    private static boolean running;
    private static boolean roarPlayed;
    private static long dayStart = -1;
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
        dayStart = mc.level.dimensionType().hasFixedTime() ? -1 : mc.level.getDayTime();
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
        if (dayStart >= 0 && mc.level != null) {
            // 还原成服务端的时间（下一次服务端同步时间时也会自动校正）
            mc.level.setDayTime(dayStart + (long) ClientCinematicCache.scriptedElapsedTicks());
        }
        dayStart = -1;
        running = false;
        camera = null;
    }

    /** 出场开始后经过的秒数（现实时间） */
    private static float seconds() {
        return ClientCinematicCache.scriptedElapsedTicks() / 20.0f;
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
        float t = seconds();
        spinSky(mc.level, t);
        Shot shot = compute(mc, t);
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

    /**
     * 日夜交替：她一出现（凋灵爆炸）天空就飞快地转起来，越转越慢，咆哮时正好停下。
     * 总共转过整数天，所以停下后时刻和原来一样。
     */
    private static void spinSky(ClientLevel level, float t) {
        if (dayStart < 0) {
            return;
        }
        double u = Mth.clamp((t - WeepingDeathLord.INTRO_EXPLODE) / (WeepingDeathLord.INTRO_ROAR - WeepingDeathLord.INTRO_EXPLODE), 0.0, 1.0);
        double eased = 1.0 - (1.0 - u) * (1.0 - u);
        long spin = Math.round(eased * WeepingDeathLord.DAY_SPIN_DAYS * 24000L);
        level.setDayTime(dayStart + (long) (t * 20) + spin);
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
        // 玩家始终面向她，最后的对峙画面里是玩家的背影对着她；镜头回到玩家时视角正好看着她
        Vec3 target = boss != null ? boss.position().add(0, CHEST_HEIGHT, 0) : anchor().add(0, CHEST_HEIGHT, 0);
        Vec3 d = target.subtract(player.getEyePosition());
        if (d.horizontalDistanceSqr() > 1.0E-4) {
            float face = (float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90.0f;
            float look = (float) -(Mth.atan2(d.y, d.horizontalDistance()) * Mth.RAD_TO_DEG);
            player.setYRot(face);
            player.yRotO = face;
            player.yBodyRot = face;
            player.yHeadRot = face;
            player.setXRot(look);
            player.xRotO = look;
        }
        if (!roarPlayed && seconds() >= WeepingDeathLord.INTRO_ROAR) {
            roarPlayed = true;
            // 凋灵咆哮：几层叠在一起，不随距离衰减，镜头在哪都很大声
            RandomSource random = RandomSource.create();
            playLoud(mc, ModSounds.WEEPING_ROAR.get().getLocation(), 1.0f, random);
            playLoud(mc, ModSounds.WEEPING_ROAR.get().getLocation(), 0.9f, random);
            playLoud(mc, ModSounds.WEEPING_ROAR_BEAST.get().getLocation(), 1.0f, random);
            playLoud(mc, SoundEvents.ENDER_DRAGON_GROWL.getLocation(), 0.6f, random);
        }
    }

    private static void playLoud(Minecraft mc, net.minecraft.resources.ResourceLocation sound, float pitch, RandomSource random) {
        mc.getSoundManager().play(new SimpleSoundInstance(sound, SoundSource.HOSTILE, 1.0f, pitch, random, false, 0,
                SoundInstance.Attenuation.NONE, 0, 0, 0, true));
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
        // 镜头几乎贴在玩家眼睛上时（开头和结尾的过渡）不画，免得挡住画面
        if (event.getCamera().getPosition().distanceToSqr(player.getEyePosition(event.getPartialTick())) < 0.6 * 0.6) {
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

        Shot blend(Shot o, double u, double arc) {
            double e = smooth(u);
            Vec3 p = pos.lerp(o.pos, e).add(0, Math.sin(Math.PI * e) * arc, 0);
            return new Shot(p, look.lerp(o.look, e), Mth.lerp(e, fov, o.fov));
        }
    }

    /** 每帧都会用到的几何量 */
    private record Scene(Vec3 boss, Vec3 eye, Vec3 toBoss, Vec3 side, Vec3 bossFwd, double dist) {
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
        // 她一直面向玩家：用「她指向玩家的方向」当作她的正前方，比用她的朝向更稳
        Scene s = new Scene(b, eye, g, side, g.scale(-1), dist);

        if (t < EYE_TO_WIDE_END) {
            return eyeShot(s).blend(wideShot(s, t), t / EYE_TO_WIDE_END, 0.6);
        }
        if (t < WIDE_TO_FACE_START) {
            return wideShot(s, t);
        }
        if (t < WIDE_TO_FACE_END) {
            double u = (t - WIDE_TO_FACE_START) / (WIDE_TO_FACE_END - WIDE_TO_FACE_START);
            return wideShot(s, t).blend(faceShot(s, t), u, 1.5);
        }
        if (t < WeepingDeathLord.INTRO_ROAR) {
            return faceShot(s, t);
        }
        if (t < WeepingDeathLord.INTRO_SETTLE) {
            return roarShot(s, t);
        }
        if (t < TO_DOLLY_END) {
            double u = (t - WeepingDeathLord.INTRO_SETTLE) / (TO_DOLLY_END - WeepingDeathLord.INTRO_SETTLE);
            return roarShot(s, t).blend(dollyShot(s, 0), u, 0.8);
        }
        // 推到玩家身后定格，定格结束出场就结束，镜头直接还给玩家
        double u = (t - TO_DOLLY_END) / (WeepingDeathLord.INTRO_DOLLY_END - TO_DOLLY_END);
        return dollyShot(s, Math.min(1.0, u));
    }

    /** 玩家自己的视角，看着她 */
    private static Shot eyeShot(Scene s) {
        return new Shot(s.eye, s.boss.add(0, CHEST_HEIGHT, 0), 70);
    }

    /** 远景：侧面看玩家与凋灵对峙，缓慢推近 */
    private static Shot wideShot(Scene s, float t) {
        double u = Mth.clamp(t / WIDE_TO_FACE_END, 0.0, 1.0);
        Vec3 mid = s.eye.add(s.boss.add(0, 1.8, 0)).scale(0.5);
        double back = Math.max(10.0, s.dist * 0.9 + 6.0) * (1.0 - 0.15 * smooth(u));
        Vec3 pos = mid.add(s.side.scale(back)).add(0, 2.5 + s.dist * 0.08, 0);
        return new Shot(pos, mid, 70);
    }

    /** 她的脸（随抬头从蜷缩时的高度移到睁眼后的高度） */
    private static Vec3 face(Scene s, float t) {
        double lift = smooth((t - WeepingDeathLord.INTRO_WAKE - 0.3) / (WeepingDeathLord.INTRO_ROAR - WeepingDeathLord.INTRO_WAKE - 0.4));
        return s.boss.add(0, Mth.lerp(lift, CURL_FACE_HEIGHT, AWAKE_FACE_HEIGHT), 0).add(s.bossFwd.scale(FACE_FORWARD));
    }

    /** 脸部特写：慢慢推近 */
    private static Shot faceShot(Scene s, float t) {
        double u = Mth.clamp((t - WeepingDeathLord.INTRO_WAKE) / (WeepingDeathLord.INTRO_ROAR - WeepingDeathLord.INTRO_WAKE), 0.0, 1.0);
        Vec3 f = face(s, t);
        double d = Mth.lerp(smooth(u), 2.6, 1.9);
        return new Shot(f.add(s.bossFwd.scale(d)).add(0, 0.12, 0), f, 45);
    }

    /** 咆哮：从脸部猛地拉远到中景，之后缓缓环绕（9 秒时和脸部特写完全重合） */
    private static Shot roarShot(Scene s, float t) {
        float since = t - WeepingDeathLord.INTRO_ROAR;
        double u = Math.min(1.0, since / PULL_BACK);
        double e = 1 - Math.pow(1 - u, 3);
        Vec3 f = face(s, WeepingDeathLord.INTRO_ROAR);
        Vec3 chest = s.boss.add(0, CHEST_HEIGHT, 0);
        Vec3 look = f.lerp(chest, e);
        double orbit = Math.max(0, since - PULL_BACK) * 4.0;  // 度
        Vec3 dir = rotateY(s.bossFwd, orbit);
        Vec3 pos = look.add(dir.scale(Mth.lerp(e, 1.9, 8.5))).add(0, Mth.lerp(e, 0.12, 0.8), 0);
        return new Shot(pos, look, Mth.lerp(e, 45, 62));
    }

    /** 运镜：u = 0 时镜头在玩家身前看着玩家，u = 1 时越过玩家肩膀看向她 */
    private static Shot dollyShot(Scene s, double u) {
        double e = smooth(u);
        double angle = Math.PI * e;
        double radius = Mth.lerp(e, 2.4, 4.4);
        Vec3 around = s.toBoss.scale(Math.cos(angle)).add(s.side.scale(Math.sin(angle)));
        Vec3 pos = s.eye.add(around.scale(radius)).add(s.side.scale(1.2 * e)).add(0, Mth.lerp(e, 0.05, 0.9), 0);
        Vec3 look = s.eye.lerp(s.boss.add(0, CHEST_HEIGHT, 0), smooth(Math.min(1.0, u * 1.3)));
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
