package com.sourceofmystery.network;

import com.sourceofmystery.client.ClientSatelliteCache;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 神威天佑卫星状态同步包：服务端 -> 穿戴者客户端，告诉客户端哪些外环卫星正在出击，
 * 客户端据此不在玩家身边绘制这些卫星
 */
public record SatelliteSyncPacket(int busyMask) {

    public static void encode(SatelliteSyncPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.busyMask);
    }

    public static SatelliteSyncPacket decode(FriendlyByteBuf buf) {
        return new SatelliteSyncPacket(buf.readVarInt());
    }

    public static void handle(SatelliteSyncPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> ClientSatelliteCache.busyMask = msg.busyMask);
        ctx.get().setPacketHandled(true);
    }
}
