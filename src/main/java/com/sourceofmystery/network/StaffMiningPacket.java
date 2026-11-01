package com.sourceofmystery.network;

import com.sourceofmystery.staff.StaffMining;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

public record StaffMiningPacket(BlockPos position,boolean start) {
    public static void encode(StaffMiningPacket p,FriendlyByteBuf b){b.writeBlockPos(p.position);b.writeBoolean(p.start);}
    public static StaffMiningPacket decode(FriendlyByteBuf b){return new StaffMiningPacket(b.readBlockPos(),b.readBoolean());}
    public static void handle(StaffMiningPacket p,Supplier<NetworkEvent.Context> supplier){
        var c=supplier.get();c.enqueueWork(()->{if(c.getSender()!=null)StaffMining.request(c.getSender(),p.position,p.start);});c.setPacketHandled(true);
    }
}
