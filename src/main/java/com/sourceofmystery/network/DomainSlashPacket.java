package com.sourceofmystery.network;

import com.sourceofmystery.client.DomainVisuals;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkEvent;
import java.util.*;
import java.util.function.Supplier;

/** Only actual server damage creates a short-lived slash; snapshots never replay it. */
public record DomainSlashPacket(ResourceLocation dimension,UUID field,int pulse,List<Target> targets) {
    public static final int MAX_TARGETS=256;
    public record Target(int id,UUID uuid){}
    public static void encode(DomainSlashPacket p,FriendlyByteBuf b){
        b.writeResourceLocation(p.dimension);b.writeUUID(p.field);b.writeVarInt(p.pulse);b.writeVarInt(p.targets.size());
        for(var target:p.targets){b.writeVarInt(target.id);b.writeUUID(target.uuid);}
    }
    public static DomainSlashPacket decode(FriendlyByteBuf b){
        var dimension=b.readResourceLocation();var field=b.readUUID();int pulse=b.readVarInt(),count=b.readVarInt();
        if(count<0||count>MAX_TARGETS||pulse<0)throw new IllegalArgumentException("Invalid domain slash pulse");
        var targets=new ArrayList<Target>(count);
        for(int i=0;i<count;i++)targets.add(new Target(b.readVarInt(),b.readUUID()));
        return new DomainSlashPacket(dimension,field,pulse,List.copyOf(targets));
    }
    public static void handle(DomainSlashPacket p,Supplier<NetworkEvent.Context> supplier){
        var context=supplier.get();context.enqueueWork(()->DomainVisuals.receiveSlash(p));context.setPacketHandled(true);
    }
}
