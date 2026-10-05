package com.sourceofmystery.entity;

import com.sourceofmystery.SourceOfMystery;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 原版凋灵死亡的瞬间生成泣死之主并开始出场演出（下界之星照常掉落，原版死亡动画在客户端被隐藏，
 * 由变白旋转的 WitherHusk 代替）。只认原版凋灵：神威天道召唤的巨型凋灵（GiantWither）不算。
 */
@Mod.EventBusSubscriber(modid = SourceOfMystery.MOD_ID)
public class WeepingDeathLordSpawnHandler {

    private static final double MESSAGE_RANGE = 128.0;

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onWitherDeath(LivingDeathEvent event) {
        if (event.isCanceled() || event.getEntity().getType() != EntityType.WITHER
                || !(event.getEntity() instanceof WitherBoss wither)
                || !(wither.level() instanceof ServerLevel level)) {
            return;
        }
        WeepingDeathLord lord = ModEntities.WEEPING_DEATH_LORD.get().create(level);
        if (lord == null) {
            SourceOfMystery.LOGGER.warn("Failed to spawn Weeping Death Lord");
            return;
        }
        Player focus = event.getSource().getEntity() instanceof Player p ? p
                : level.getNearestPlayer(wither, MESSAGE_RANGE);
        lord.moveTo(wither.getX(), wither.getY() + 0.5, wither.getZ(), wither.getYRot(), 0);
        lord.prepareHidden(); // 加入世界前就隐藏：凋灵爆开之前看不到她
        lord.finalizeSpawn(level, level.getCurrentDifficultyAt(BlockPos.containing(wither.position())), MobSpawnType.EVENT, null, null);
        level.addFreshEntity(lord);
        lord.beginIntro(level, wither, focus);
        SourceOfMystery.LOGGER.info("Weeping Death Lord rising at {} in {}", wither.position(), level.dimension().location());
        broadcast(level, lord, Component.translatable("message.sourceofmystery.weeping_death_lord.omen")
                .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
    }

    /** 出场结束时由 WeepingDeathLord 调用 */
    public static void announceArrival(ServerLevel level, WeepingDeathLord lord) {
        broadcast(level, lord, Component.translatable("message.sourceofmystery.weeping_death_lord.arrived")
                .withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD));
    }

    private static void broadcast(ServerLevel level, WeepingDeathLord lord, Component message) {
        Component text = Component.empty()
                .append(Component.translatable("message.sourceofmystery.weeping_death_lord.prefix")
                        .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.BOLD))
                .append(" ")
                .append(message);
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(lord) < MESSAGE_RANGE * MESSAGE_RANGE) {
                player.sendSystemMessage(text);
            }
        }
    }
}
