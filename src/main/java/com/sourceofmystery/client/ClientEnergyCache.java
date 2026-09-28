package com.sourceofmystery.client;

/**
 * 客户端神秘之能缓存：HUD 从这里读取，而不是从玩家 persistentData 读取。
 * 服务端通过同步包更新这个缓存，避免登录/重生瞬间客户端玩家为 null 导致同步丢失。
 */
public class ClientEnergyCache {

    public static long energy = 0;
    public static long max = 0;
    public static boolean guiUnlocked = false;

    public static void update(long energy, long max, boolean guiUnlocked) {
        ClientEnergyCache.energy = energy;
        ClientEnergyCache.max = max;
        ClientEnergyCache.guiUnlocked = guiUnlocked;
    }
}
