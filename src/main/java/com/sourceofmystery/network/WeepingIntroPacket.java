package com.sourceofmystery.network;

import com.sourceofmystery.client.ClientCinematicCache;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 泣死之主出场开始：服务端 -> 附近玩家。客户端按固定时间表播放运镜（client.WeepingIntroDirector），
 * (x, y, z) 为原版凋灵死亡的位置。
 */
public record WeepingIntroPacket(int entityId, double x, double y, double z, int ticks) {

    public static void encode(WeepingIntroPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.entityId);
        buf.writeDouble(msg.x);
        buf.writeDouble(msg.y);
        buf.writeDouble(msg.z);
        buf.writeVarInt(msg.ticks);
    }

    public static WeepingIntroPacket decode(FriendlyByteBuf buf) {
        return new WeepingIntroPacket(buf.readVarInt(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readVarInt());
    }

    public static void handle(WeepingIntroPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> ClientCinematicCache.startScripted(msg.entityId, msg.x, msg.y, msg.z, msg.ticks));
        ctx.get().setPacketHandled(true);
    }
}
