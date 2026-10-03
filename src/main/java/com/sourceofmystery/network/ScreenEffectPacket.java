package com.sourceofmystery.network;

import com.sourceofmystery.client.ClientCinematicCache;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 服务端 -> 客户端：屏幕闪白 flashTicks、镜头震动 shakeTicks（强度 shakeStrength 度）。用于大爆炸、Boss 落地等
 */
public record ScreenEffectPacket(int flashTicks, int shakeTicks, float shakeStrength) {

    public static void encode(ScreenEffectPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.flashTicks);
        buf.writeVarInt(msg.shakeTicks);
        buf.writeFloat(msg.shakeStrength);
    }

    public static ScreenEffectPacket decode(FriendlyByteBuf buf) {
        return new ScreenEffectPacket(buf.readVarInt(), buf.readVarInt(), buf.readFloat());
    }

    public static void handle(ScreenEffectPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> ClientCinematicCache.screenEffect(msg.flashTicks, msg.shakeTicks, msg.shakeStrength));
        ctx.get().setPacketHandled(true);
    }
}
