package com.sourceofmystery.network;

import com.sourceofmystery.staff.StaffDomains;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import java.util.UUID;
import java.util.function.Supplier;

/** A client can stop only its own matching session; stale UI clicks cannot cancel a newer domain. */
public record DomainStopPacket(UUID token) {
    public static void encode(DomainStopPacket p,FriendlyByteBuf b){b.writeUUID(p.token);}
    public static DomainStopPacket decode(FriendlyByteBuf b){return new DomainStopPacket(b.readUUID());}
    public static void handle(DomainStopPacket p,Supplier<NetworkEvent.Context> ctx){var c=ctx.get();c.enqueueWork(()->{if(c.getSender()!=null)StaffDomains.stop(c.getSender(),p.token);});c.setPacketHandled(true);}
}
