package com.sourceofmystery.client;

/**
 * 客户端缓存：自己的神威天佑外环卫星哪些正在出击（第 i 位为 1 表示第 i 颗不在身边）。
 * 由服务端 SatelliteSyncPacket 更新；不引用客户端专属类，专用服务端加载也安全。
 */
public class ClientSatelliteCache {

    public static int busyMask = 0;
}
