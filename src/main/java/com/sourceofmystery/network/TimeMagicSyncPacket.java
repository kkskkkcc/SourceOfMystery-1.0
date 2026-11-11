package com.sourceofmystery.network;

import com.sourceofmystery.client.staff.ClientTimeMagic;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkEvent;
import java.util.UUID;
import java.util.function.Supplier;

/** Dimension and session identity prevent an old finish packet from clearing a newer clock. */
public record TimeMagicSyncPacket(ResourceLocation dimension,UUID token,UUID caster,int mode,int remaining,int phase,long gameTime,long dayTime) {
    public static final int INDEFINITE=-1;
    public static void encode(TimeMagicSyncPacket p,FriendlyByteBuf b){b.writeResourceLocation(p.dimension);b.writeUUID(p.token);b.writeUUID(p.caster);b.writeVarInt(p.mode);b.writeVarInt(p.remaining);b.writeVarInt(p.phase);b.writeLong(p.gameTime);b.writeLong(p.dayTime);}
    public static TimeMagicSyncPacket decode(FriendlyByteBuf b){return new TimeMagicSyncPacket(b.readResourceLocation(),b.readUUID(),b.readUUID(),b.readVarInt(),b.readVarInt(),b.readVarInt(),b.readLong(),b.readLong());}
    public static void handle(TimeMagicSyncPacket p,Supplier<NetworkEvent.Context> supplier){var c=supplier.get();c.enqueueWork(()->ClientTimeMagic.accept(p));c.setPacketHandled(true);}
}
