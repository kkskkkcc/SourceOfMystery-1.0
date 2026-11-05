package com.sourceofmystery.network;

import com.sourceofmystery.client.OfferingLotteryScreen;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;
import java.util.*;
import java.util.function.Supplier;

/** The client receives the chosen reel; it never submits an outcome, value or random seed. */
public record OfferingRollPacket(int containerId,long token,boolean finished,long paidValue,long prizeValue,
                                ItemStack prize,List<Cell> reel) {
    public record Cell(int itemId,int tier) {}
    public static void encode(OfferingRollPacket p,FriendlyByteBuf b) {
        b.writeVarInt(p.containerId);b.writeLong(p.token);b.writeBoolean(p.finished);
        b.writeLong(p.paidValue);b.writeLong(p.prizeValue);b.writeItem(p.prize);
        b.writeVarInt(p.reel.size());for(var c:p.reel){b.writeVarInt(c.itemId);b.writeByte(c.tier);}
    }
    public static OfferingRollPacket decode(FriendlyByteBuf b) {
        int id=b.readVarInt();long token=b.readLong();boolean done=b.readBoolean();long paid=b.readLong(),value=b.readLong();ItemStack prize=b.readItem();
        int size=b.readVarInt();if(size<0||size>48)throw new IllegalArgumentException("Offering reel length");
        var cells=new ArrayList<Cell>(size);for(int i=0;i<size;i++)cells.add(new Cell(b.readVarInt(),b.readUnsignedByte()));
        return new OfferingRollPacket(id,token,done,paid,value,prize,List.copyOf(cells));
    }
    public static void handle(OfferingRollPacket p,Supplier<NetworkEvent.Context> context) {
        context.get().enqueueWork(()->OfferingLotteryScreen.receive(p));context.get().setPacketHandled(true);
    }
}
