package com.sourceofmystery.client;

/**
 * 客户端缓存：当前正在播放的 Boss 出场镜头。由服务端 BossIntroPacket 设置；
 * 不引用客户端专属类，专用服务端加载也安全。
 */
public final class ClientCinematicCache {

    public static int entityId = -1;
    public static int totalTicks = 0;
    public static int remainingTicks = 0;

    // 屏幕闪白 / 镜头震动（与出场镜头无关，单独计时）
    public static int flashTotal = 0;
    public static int flashRemaining = 0;
    public static int shakeRemaining = 0;
    public static float shakeStrength = 0;

    // 脚本运镜（泣死之主出场）：镜头位置由 WeepingIntroDirector 按时间表计算，anchor 为凋灵死亡的位置。
    // 脚本运镜按现实时间计时（scriptedStartMillis），不受客户端 tick 波动影响
    public static boolean scripted = false;
    public static long scriptedStartMillis;
    public static double anchorX;
    public static double anchorY;
    public static double anchorZ;

    // 定身：剩余 tick，期间移动 / 跳跃输入无效
    public static int rootTicks = 0;

    private ClientCinematicCache() {
    }

    public static void start(int id, int ticks) {
        entityId = id;
        totalTicks = ticks;
        remainingTicks = ticks;
    }

    public static void startScripted(int id, double x, double y, double z, int ticks) {
        start(id, ticks);
        scripted = true;
        scriptedStartMillis = net.minecraft.Util.getMillis();
        anchorX = x;
        anchorY = y;
        anchorZ = z;
    }

    /** 脚本运镜开始后经过的时间（tick，带小数，按现实时间） */
    public static float scriptedElapsedTicks() {
        return (net.minecraft.Util.getMillis() - scriptedStartMillis) / 50.0f;
    }

    public static void reset() {
        scripted = false;
        entityId = -1;
        totalTicks = 0;
        remainingTicks = 0;
    }

    public static void screenEffect(int flash, int shake, float strength) {
        if (flash >= flashRemaining) {
            flashTotal = flash;
            flashRemaining = flash;
        }
        if (shake >= shakeRemaining) {
            shakeRemaining = shake;
            shakeStrength = strength;
        }
    }

    public static void resetScreenEffects() {
        rootTicks = 0;
        flashTotal = 0;
        flashRemaining = 0;
        shakeRemaining = 0;
        shakeStrength = 0;
    }

    public static boolean active() {
        return remainingTicks > 0 && entityId >= 0;
    }
}
