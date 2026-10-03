package com.sourceofmystery.network;

import com.sourceofmystery.client.ClientCinematicCache;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Boss 出场动画开始：服务端 -> 附近玩家，客户端在 ticks 时长内把镜头对准该实体（见 client.BossIntroCamera）
 */
public record BossIntroPacket(int entityId, int ticks) {

    public static void encode(BossIntroPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.entityId);
        buf.writeVarInt(msg.ticks);
    }

    public static BossIntroPacket decode(FriendlyByteBuf buf) {
        return new BossIntroPacket(buf.readVarInt(), buf.readVarInt());
    }

    public static void handle(BossIntroPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> ClientCinematicCache.start(msg.entityId, msg.ticks));
        ctx.get().setPacketHandled(true);
    }
}
