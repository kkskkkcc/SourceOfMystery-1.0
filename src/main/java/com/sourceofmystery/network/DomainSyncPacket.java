package com.sourceofmystery.network;

import com.sourceofmystery.client.DomainVisuals;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkEvent;
import java.util.*;
import java.util.function.Supplier;

/** Full dimension snapshot also removes expired/stopped fields and supports late joiners. */
public record DomainSyncPacket(List<Field> fields) {
    public record Field(UUID token,UUID owner,int type,Vec3 center,float yaw,int age,int remaining){}
    public static void encode(DomainSyncPacket p,FriendlyByteBuf b){
        b.writeVarInt(p.fields.size());
        for(var f:p.fields){b.writeUUID(f.token);b.writeUUID(f.owner);b.writeVarInt(f.type);b.writeDouble(f.center.x);b.writeDouble(f.center.y);b.writeDouble(f.center.z);b.writeFloat(f.yaw);b.writeVarInt(f.age);b.writeVarInt(f.remaining);}
    }
    public static DomainSyncPacket decode(FriendlyByteBuf b){
        int count=b.readVarInt();if(count<0||count>256)throw new IllegalArgumentException("Domain snapshot too large");
        List<Field> fields=new ArrayList<>(count);
        for(int i=0;i<count;i++)fields.add(new Field(b.readUUID(),b.readUUID(),b.readVarInt(),new Vec3(b.readDouble(),b.readDouble(),b.readDouble()),b.readFloat(),b.readVarInt(),b.readVarInt()));
        return new DomainSyncPacket(List.copyOf(fields));
    }
    public static void handle(DomainSyncPacket p,Supplier<NetworkEvent.Context> ctx){ctx.get().enqueueWork(()->DomainVisuals.receive(p));ctx.get().setPacketHandled(true);}
}
