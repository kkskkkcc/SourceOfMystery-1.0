package com.sourceofmystery.enchanter;

import com.sourceofmystery.SourceOfMystery;
import com.sourceofmystery.energy.MysteryEnergy;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid=SourceOfMystery.MOD_ID)
public final class EnchanterEscrow {
    private static final String KEY="enchanter_input";
    static void store(ServerPlayer player,ItemStack stack) {
        var root=MysteryEnergy.getPlayerData(player);
        if(stack.isEmpty())root.remove(KEY);else root.put(KEY,stack.save(new CompoundTag()));
    }
    public static void recover(ServerPlayer player) {
        var root=MysteryEnergy.getPlayerData(player);var stack=ItemStack.of(root.getCompound(KEY));root.remove(KEY);
        if(stack.isEmpty())return;
        if(!player.getInventory().add(stack)) {var entity=player.drop(stack,false);if(entity!=null){entity.setTarget(player.getUUID());entity.setNoPickUpDelay();}}
        player.inventoryMenu.broadcastChanges();
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void login(PlayerEvent.PlayerLoggedInEvent e) {if(e.getEntity() instanceof ServerPlayer p)recover(p);}
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void respawn(PlayerEvent.PlayerRespawnEvent e) {if(e.getEntity() instanceof ServerPlayer p)recover(p);}
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void clone(PlayerEvent.Clone e) {MysteryEnergy.getPlayerData(e.getOriginal()).remove(KEY);}
}
