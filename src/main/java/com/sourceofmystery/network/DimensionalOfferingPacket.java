package com.sourceofmystery.network;

import com.sourceofmystery.client.OfferingIntroDirector;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

public record DimensionalOfferingPacket(int bossId,int x,int y,int z,boolean axis,int ticks){
    public static void encode(DimensionalOfferingPacket m,FriendlyByteBuf b){b.writeVarInt(m.bossId);b.writeInt(m.x);b.writeInt(m.y);b.writeInt(m.z);b.writeBoolean(m.axis);b.writeVarInt(m.ticks);}
    public static DimensionalOfferingPacket decode(FriendlyByteBuf b){return new DimensionalOfferingPacket(b.readVarInt(),b.readInt(),b.readInt(),b.readInt(),b.readBoolean(),b.readVarInt());}
    public static void handle(DimensionalOfferingPacket m,Supplier<NetworkEvent.Context> c){c.get().enqueueWork(()->OfferingIntroDirector.start(m));c.get().setPacketHandled(true);}
}
