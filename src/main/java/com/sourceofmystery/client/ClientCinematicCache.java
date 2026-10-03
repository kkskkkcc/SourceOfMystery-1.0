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

    private ClientCinematicCache() {
    }

    public static void start(int id, int ticks) {
        entityId = id;
        totalTicks = ticks;
        remainingTicks = ticks;
    }

    public static void reset() {
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
        flashTotal = 0;
        flashRemaining = 0;
        shakeRemaining = 0;
        shakeStrength = 0;
    }

    public static boolean active() {
        return remainingTicks > 0 && entityId >= 0;
    }
}
