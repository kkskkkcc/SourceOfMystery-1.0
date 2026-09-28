package com.sourceofmystery.capability.energy;

import com.sourceofmystery.SourceOfMystery;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = SourceOfMystery.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class MysteryEnergyEvents {

    @SubscribeEvent
    public static void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        // 玩家登录时初始化神秘之能（确保老存档/新玩家都有初始值）
        MysteryEnergyCapability.onPlayerJoin(event.getEntity());
    }

    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
        if (event.isWasDeath()) {
            Player original = event.getOriginal();
            Player player = event.getEntity();
            MysteryEnergyCapability.onPlayerClone(original, player, true);
        }
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        Entity entity = event.getEntity();
        if (entity instanceof LivingEntity) {
            LivingEntity livingEntity = (LivingEntity) entity;
            Entity killCredit = livingEntity.getKillCredit();
            if (killCredit instanceof Player) {
                Player player = (Player) killCredit;
                MysteryEnergyCapability.onPlayerKill(player);
                SourceOfMystery.LOGGER.info("Player {} gained 10 Mystery Energy. Total: {}",
                        player.getName().getString(), MysteryEnergyCapability.getEnergy(player));
            }
        }
    }

    // 注意：不再需要 AttachCapabilitiesEvent，因为我们使用 NBT 直接存储
    // 玩家神秘之能会在首次获取时自动初始化
}
