package com.sourceofmystery.network;

import com.sourceofmystery.client.staff.StaffVisuals;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkEvent;
import java.util.UUID;
import java.util.function.Supplier;

/** Visual timelines only; hits, costs, terrain and teleportation remain on the server. */
public record StaffEffectPacket(int kind, UUID caster, Vec3 origin, Vec3 target, int duration, int variant, int[] blocks) {
    public static final int CHARGE=0, CANCEL=1, SPLIT=2, LIFT=3, STOP=4, RIFT=5;
    public StaffEffectPacket(int kind, UUID caster, Vec3 origin, Vec3 target, int duration, int variant) {
        this(kind, caster, origin, target, duration, variant, new int[0]);
    }
    public static void encode(StaffEffectPacket message, FriendlyByteBuf b) {
        b.writeVarInt(message.kind); b.writeUUID(message.caster);
        vector(b,message.origin); vector(b,message.target);
        b.writeVarInt(message.duration); b.writeVarInt(message.variant); b.writeVarIntArray(message.blocks);
    }
    private static void vector(FriendlyByteBuf b, Vec3 v) { b.writeDouble(v.x); b.writeDouble(v.y); b.writeDouble(v.z); }
    private static Vec3 vector(FriendlyByteBuf b) { return new Vec3(b.readDouble(),b.readDouble(),b.readDouble()); }
    public static StaffEffectPacket decode(FriendlyByteBuf b) {
        return new StaffEffectPacket(b.readVarInt(),b.readUUID(),vector(b),vector(b),b.readVarInt(),b.readVarInt(),b.readVarIntArray(131072));
    }
    public static void handle(StaffEffectPacket message, Supplier<NetworkEvent.Context> supplier) {
        var context=supplier.get(); context.enqueueWork(()->StaffVisuals.accept(message)); context.setPacketHandled(true);
    }
}
