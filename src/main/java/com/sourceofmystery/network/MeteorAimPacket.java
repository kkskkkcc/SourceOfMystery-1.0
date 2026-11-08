package com.sourceofmystery.network;

import com.sourceofmystery.client.MeteorAimClient;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkEvent;
import java.util.UUID;
import java.util.function.Supplier;

/** -2: no hit, -1: area, otherwise the selected entity's ID. Sent using PLAYER, never DIMENSION. */
public record MeteorAimPacket(int entityId,UUID entity,Vec3 point,int dwell){
    public static void encode(MeteorAimPacket p,FriendlyByteBuf b){
        b.writeInt(p.entityId);b.writeBoolean(p.entity!=null);if(p.entity!=null)b.writeUUID(p.entity);
        b.writeDouble(p.point.x);b.writeDouble(p.point.y);b.writeDouble(p.point.z);b.writeVarInt(p.dwell);
    }
    public static MeteorAimPacket decode(FriendlyByteBuf b){return new MeteorAimPacket(b.readInt(),b.readBoolean()?b.readUUID():null,new Vec3(b.readDouble(),b.readDouble(),b.readDouble()),b.readVarInt());}
    public static void handle(MeteorAimPacket p,Supplier<NetworkEvent.Context> supplier){var c=supplier.get();c.enqueueWork(()->MeteorAimClient.receive(p));c.setPacketHandled(true);}
}
