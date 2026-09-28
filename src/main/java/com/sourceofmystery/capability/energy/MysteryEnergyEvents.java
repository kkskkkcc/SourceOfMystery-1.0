package com.sourceofmystery.capability.energy;

import com.sourceofmystery.SourceOfMystery;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = SourceOfMystery.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class MysteryEnergyEvents {

    private static final int TICKS_PER_DAY = 24000; // Minecraft 每天 24000 tick
    private static final int DAILY_CHECK_INTERVAL = 20; // 每秒检查一次是否跨天

    @SubscribeEvent
    public static void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        // 玩家登录时初始化神秘之能（确保老存档/新玩家都有初始值）
        MysteryEnergyCapability.onPlayerJoin(event.getEntity());
    }

    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
        // 死亡重生和从末地返回都会替换玩家实体，两种情况都要复制数据
        MysteryEnergyCapability.onPlayerClone(event.getOriginal(), event.getEntity());
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        MysteryEnergyCapability.syncToClient(event.getEntity());
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide) {
            return;
        }
        Player player = event.player;
        if (player.tickCount % DAILY_CHECK_INTERVAL != 0 || player.getServer() == null) {
            return;
        }

        // ==================== 每日回满神秘之能 ====================
        ServerLevel overworld = player.getServer().getLevel(Level.OVERWORLD);
        if (overworld != null) {
            MysteryEnergyCapability.tickDailyRefresh(player, overworld.getGameTime() / TICKS_PER_DAY);
        }
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (event.getEntity().level().isClientSide) {
            return;
        }
        if (event.getEntity().getKillCredit() instanceof Player player) {
            MysteryEnergyCapability.onPlayerKill(player);
        }
    }
}
