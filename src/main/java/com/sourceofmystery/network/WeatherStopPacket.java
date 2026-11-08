package com.sourceofmystery.network;

import com.sourceofmystery.staff.StaffWeather;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import java.util.UUID;
import java.util.function.Supplier;

public record WeatherStopPacket(UUID token){
    public static void encode(WeatherStopPacket p,FriendlyByteBuf b){b.writeUUID(p.token);}
    public static WeatherStopPacket decode(FriendlyByteBuf b){return new WeatherStopPacket(b.readUUID());}
    public static void handle(WeatherStopPacket p,Supplier<NetworkEvent.Context> ctx){var c=ctx.get();c.enqueueWork(()->{if(c.getSender()!=null)StaffWeather.stop(c.getSender(),p.token);});c.setPacketHandled(true);}
}
