package com.sourceofmystery.network;

import com.sourceofmystery.client.ClientCinematicCache;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 服务端 -> 客户端：定身 ticks 个 tick（期间移动、跳跃输入全部无效）；ticks = 0 立即解除。
 * 用于泣死之主的掷镰刀定身和吸附抓取。
 */
public record RootPacket(int ticks) {

    public static void encode(RootPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.ticks);
    }

    public static RootPacket decode(FriendlyByteBuf buf) {
        return new RootPacket(buf.readVarInt());
    }

    public static void handle(RootPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> ClientCinematicCache.rootTicks = msg.ticks);
        ctx.get().setPacketHandled(true);
    }
}
