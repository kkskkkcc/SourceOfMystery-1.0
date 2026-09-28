package com.sourceofmystery.network;

import com.sourceofmystery.client.ClientEnergyCache;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 神秘之能同步包：服务端 -> 客户端，同步能量、上限、GUI解锁状态
 */
public class EnergySyncPacket {

    private final long energy;
    private final long max;
    private final boolean guiUnlocked;

    public EnergySyncPacket(long energy, long max, boolean guiUnlocked) {
        this.energy = energy;
        this.max = max;
        this.guiUnlocked = guiUnlocked;
    }

    public static void encode(EnergySyncPacket msg, FriendlyByteBuf buf) {
        buf.writeLong(msg.energy);
        buf.writeLong(msg.max);
        buf.writeBoolean(msg.guiUnlocked);
    }

    public static EnergySyncPacket decode(FriendlyByteBuf buf) {
        return new EnergySyncPacket(buf.readLong(), buf.readLong(), buf.readBoolean());
    }

    public static void handle(EnergySyncPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> handleClient(msg));
        });
        ctx.get().setPacketHandled(true);
    }

    private static void handleClient(EnergySyncPacket msg) {
        // 更新客户端缓存（不依赖 Minecraft.getInstance().player，避免登录/重生瞬间 player 为 null 导致同步丢失）
        ClientEnergyCache.update(msg.energy, msg.max, msg.guiUnlocked);
    }
}
