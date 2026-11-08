package com.sourceofmystery.network;

import com.sourceofmystery.client.WeatherVisuals;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkEvent;
import java.util.*;
import java.util.function.Supplier;

public record WeatherSyncPacket(List<Storm> storms){
    public record Storm(UUID token,UUID owner,Vec3 center,int variant,int age,int remaining){}
    public static void encode(WeatherSyncPacket p,FriendlyByteBuf b){b.writeVarInt(p.storms.size());for(var s:p.storms){b.writeUUID(s.token);b.writeUUID(s.owner);b.writeDouble(s.center.x);b.writeDouble(s.center.y);b.writeDouble(s.center.z);b.writeVarInt(s.variant);b.writeVarInt(s.age);b.writeVarInt(s.remaining);}}
    public static WeatherSyncPacket decode(FriendlyByteBuf b){int n=b.readVarInt();if(n<0||n>32)throw new IllegalArgumentException("Storm limit");List<Storm> result=new ArrayList<>(n);for(int i=0;i<n;i++)result.add(new Storm(b.readUUID(),b.readUUID(),new Vec3(b.readDouble(),b.readDouble(),b.readDouble()),b.readVarInt(),b.readVarInt(),b.readVarInt()));return new WeatherSyncPacket(List.copyOf(result));}
    public static void handle(WeatherSyncPacket p,Supplier<NetworkEvent.Context> ctx){var c=ctx.get();c.enqueueWork(()->WeatherVisuals.receive(p));c.setPacketHandled(true);}
}
