package com.sourceofmystery.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.api.distmarker.Dist;
import java.util.UUID;
import java.util.function.Supplier;

public record DivineCinematicPacket(UUID token,boolean start,net.minecraft.world.item.ItemStack armor) {
    public static void encode(DivineCinematicPacket m,FriendlyByteBuf b){b.writeUUID(m.token);b.writeBoolean(m.start);if(m.start)b.writeItem(m.armor);}
    public static DivineCinematicPacket decode(FriendlyByteBuf b){UUID token=b.readUUID();boolean start=b.readBoolean();return new DivineCinematicPacket(token,start,start?b.readItem():net.minecraft.world.item.ItemStack.EMPTY);}
    public static void handle(DivineCinematicPacket m,Supplier<NetworkEvent.Context> ctx){
        ctx.get().enqueueWork(()->DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->com.sourceofmystery.client.DivineCGDirector.receive(m)));
        ctx.get().setPacketHandled(true);
    }
}
