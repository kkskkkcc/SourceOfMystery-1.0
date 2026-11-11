package com.sourceofmystery.network;

import com.sourceofmystery.alchemy.AlchemyMenu;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import java.util.UUID;
import java.util.function.Supplier;

/** No client-supplied outputs/prices; the quote UUID prevents buying a different day by mistake. */
public record AlchemyTradePacket(int container,int index,UUID revision){
    public static void encode(AlchemyTradePacket p,FriendlyByteBuf b){b.writeVarInt(p.container);b.writeVarInt(p.index);b.writeUUID(p.revision);}
    public static AlchemyTradePacket decode(FriendlyByteBuf b){return new AlchemyTradePacket(b.readVarInt(),b.readVarInt(),b.readUUID());}
    public static void handle(AlchemyTradePacket p,Supplier<NetworkEvent.Context> supplier){var c=supplier.get();c.enqueueWork(()->{
        var player=c.getSender();if(player!=null&&player.containerMenu instanceof AlchemyMenu menu&&menu.containerId==p.container)menu.buy(player,p.index,p.revision);
    });c.setPacketHandled(true);}
}
