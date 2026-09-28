package com.sourceofmystery.network;

import com.sourceofmystery.client.ClientEnergyCache;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 神秘之能同步包：服务端 -> 客户端，同步能量、上限、GUI解锁状态
 */
public record EnergySyncPacket(long energy, long max, boolean guiUnlocked) {

    public static void encode(EnergySyncPacket msg, FriendlyByteBuf buf) {
        buf.writeLong(msg.energy);
        buf.writeLong(msg.max);
        buf.writeBoolean(msg.guiUnlocked);
    }

    public static EnergySyncPacket decode(FriendlyByteBuf buf) {
        return new EnergySyncPacket(buf.readLong(), buf.readLong(), buf.readBoolean());
    }

    public static void handle(EnergySyncPacket msg, Supplier<NetworkEvent.Context> ctx) {
        // 只注册为 PLAY_TO_CLIENT，处理必然发生在客户端；
        // ClientEnergyCache 不引用任何客户端专属类，专用服务端加载本类也是安全的
        ctx.get().enqueueWork(() -> ClientEnergyCache.update(msg.energy, msg.max, msg.guiUnlocked));
        ctx.get().setPacketHandled(true);
    }
}
