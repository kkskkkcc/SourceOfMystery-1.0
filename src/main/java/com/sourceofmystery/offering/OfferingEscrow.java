package com.sourceofmystery.offering;

import com.sourceofmystery.SourceOfMystery;
import com.sourceofmystery.energy.MysteryEnergy;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.OnDatapackSyncEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod;

/** Player-persisted input and committed reward survive menu close, disconnect and cloning. */
@Mod.EventBusSubscriber(modid=SourceOfMystery.MOD_ID)
public final class OfferingEscrow {
    private static final String KEY="offering_lottery";
    static CompoundTag data(ServerPlayer player) {
        var root=MysteryEnergy.getPlayerData(player);
        if(!root.contains(KEY)) root.put(KEY,new CompoundTag());
        return root.getCompound(KEY);
    }
    static void input(ServerPlayer player,ItemStack stack) {
        var tag=data(player);if(stack.isEmpty())tag.remove("input");else tag.put("input",stack.save(new CompoundTag()));
    }
    static void commit(ServerPlayer player,ItemStack reward) { data(player).put("reward",reward.save(new CompoundTag())); }
    public static void recover(ServerPlayer player) {
        var tag=data(player);
        var input=ItemStack.of(tag.getCompound("input"));var reward=ItemStack.of(tag.getCompound("reward"));
        tag.remove("input");tag.remove("reward");
        give(player,input);give(player,reward);
    }
    static void give(ServerPlayer player,ItemStack stack) {
        if(stack.isEmpty()) return;
        if(!player.getInventory().add(stack)) {
            var entity=player.drop(stack,false);if(entity!=null){entity.setTarget(player.getUUID());entity.setNoPickUpDelay();}
        }
        player.inventoryMenu.broadcastChanges();
    }
    @SubscribeEvent public static void reload(OnDatapackSyncEvent event) { if(event.getPlayer()==null)OfferingValues.invalidate(); }
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void login(PlayerEvent.PlayerLoggedInEvent event) {if(event.getEntity() instanceof ServerPlayer p)recover(p);}
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void respawn(PlayerEvent.PlayerRespawnEvent event) {if(event.getEntity() instanceof ServerPlayer p)recover(p);}
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void clone(PlayerEvent.Clone event) {
        // MysteryEnergy's normal-priority clone already copied this entire root to the new entity.
        MysteryEnergy.getPlayerData(event.getOriginal()).remove(KEY);
    }
}
