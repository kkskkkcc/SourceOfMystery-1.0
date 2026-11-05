package com.sourceofmystery.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import java.util.UUID;
import java.util.function.Supplier;

public record DivineCinematicDonePacket(UUID token) {
    public static void encode(DivineCinematicDonePacket m,FriendlyByteBuf b){b.writeUUID(m.token);}
    public static DivineCinematicDonePacket decode(FriendlyByteBuf b){return new DivineCinematicDonePacket(b.readUUID());}
    public static void handle(DivineCinematicDonePacket m,Supplier<NetworkEvent.Context> ctx){
        ctx.get().enqueueWork(()->{var p=ctx.get().getSender();if(p!=null)com.sourceofmystery.cinematic.DivineCinematic.finish(p,m.token);});
        ctx.get().setPacketHandled(true);
    }
}
