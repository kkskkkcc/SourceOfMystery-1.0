package com.sourceofmystery.network;

import com.sourceofmystery.client.EasternVisuals;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkEvent;
import java.util.*;
import java.util.function.Supplier;

/** Full dimension snapshot; the client never decides collision, terrain or costs. */
public record EasternRitePacket(ResourceLocation dimension,List<View> rites) {
    public record Passenger(int id,UUID uuid,Vec3 offset){}
    public record View(UUID token,UUID owner,Vec3 origin,float yaw,float pitch,int age,double travel,double speed,int range,double[] bases,int stoppedAt,List<Passenger> carried){}
    public static void encode(EasternRitePacket p,FriendlyByteBuf b){b.writeResourceLocation(p.dimension);b.writeVarInt(p.rites.size());for(var r:p.rites){b.writeUUID(r.token);b.writeUUID(r.owner);b.writeDouble(r.origin.x);b.writeDouble(r.origin.y);b.writeDouble(r.origin.z);b.writeFloat(r.yaw);b.writeFloat(r.pitch);b.writeVarInt(r.age);b.writeDouble(r.travel);b.writeDouble(r.speed);b.writeVarInt(r.range);for(double y:r.bases)b.writeDouble(y);b.writeVarInt(r.stoppedAt);b.writeVarInt(r.carried.size());for(var c:r.carried){b.writeVarInt(c.id);b.writeUUID(c.uuid);b.writeDouble(c.offset.x);b.writeDouble(c.offset.y);b.writeDouble(c.offset.z);}}}
    public static EasternRitePacket decode(FriendlyByteBuf b){var dim=b.readResourceLocation();int size=b.readVarInt();if(size<0||size>4)throw new IllegalArgumentException("Too many eastern rites");var list=new ArrayList<View>();for(int i=0;i<size;i++){var id=b.readUUID();var owner=b.readUUID();var origin=new Vec3(b.readDouble(),b.readDouble(),b.readDouble());float yaw=b.readFloat(),pitch=b.readFloat();int age=b.readVarInt();double travel=b.readDouble(),speed=b.readDouble();int range=b.readVarInt();double[] bases=new double[5];for(int k=0;k<5;k++)bases[k]=b.readDouble();int stopped=b.readVarInt(),count=b.readVarInt();if(count<0||count>256)throw new IllegalArgumentException("Too many eastern passengers");var carried=new ArrayList<Passenger>();for(int k=0;k<count;k++)carried.add(new Passenger(b.readVarInt(),b.readUUID(),new Vec3(b.readDouble(),b.readDouble(),b.readDouble())));list.add(new View(id,owner,origin,yaw,pitch,age,travel,speed,range,bases,stopped,List.copyOf(carried)));}return new EasternRitePacket(dim,List.copyOf(list));}
    public static void handle(EasternRitePacket p,Supplier<NetworkEvent.Context> context){var c=context.get();c.enqueueWork(()->EasternVisuals.receive(p));c.setPacketHandled(true);}
}
