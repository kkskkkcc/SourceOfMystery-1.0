package com.sourceofmystery.network;

import com.sourceofmystery.item.DimensionBreakerStaffItem;
import com.sourceofmystery.staff.StaffSpell;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.InteractionHand;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

public record StaffSelectionPacket(InteractionHand hand, int slot) {
    public static void encode(StaffSelectionPacket message, FriendlyByteBuf buffer) {
        buffer.writeEnum(message.hand); buffer.writeVarInt(message.slot);
    }
    public static StaffSelectionPacket decode(FriendlyByteBuf buffer) {
        return new StaffSelectionPacket(buffer.readEnum(InteractionHand.class), buffer.readVarInt());
    }
    public static void handle(StaffSelectionPacket message, Supplier<NetworkEvent.Context> supplier) {
        var context = supplier.get();
        context.enqueueWork(() -> {
            var player = context.getSender();
            if (player == null || player.isUsingItem() || message.slot < 0 || message.slot >= StaffSpell.values().length) return;
            var stack = player.getItemInHand(message.hand);
            if (stack.getItem() instanceof DimensionBreakerStaffItem) {
                StaffSpell.values()[message.slot].select(stack);
                player.getInventory().setChanged();
                player.containerMenu.broadcastChanges();
            }
        });
        context.setPacketHandled(true);
    }
}
