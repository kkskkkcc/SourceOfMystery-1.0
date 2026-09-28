package com.sourceofmystery.advancement;

import com.sourceofmystery.SourceOfMystery;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import javax.annotation.Nullable;

/**
 * 成就系统辅助类（用于 minecraft:impossible 触发器的成就，由代码直接授予）
 */
public class AdvancementHelper {

    @Nullable
    private static Advancement find(ServerPlayer player, String advancementId) {
        ResourceLocation location = new ResourceLocation(SourceOfMystery.MOD_ID, advancementId);
        Advancement advancement = player.server.getAdvancements().getAdvancement(location);
        if (advancement == null) {
            SourceOfMystery.LOGGER.warn("Advancement not found: {}", location);
        }
        return advancement;
    }

    /**
     * 检查玩家是否已完成指定成就
     */
    public static boolean hasAdvancement(ServerPlayer player, String advancementId) {
        Advancement advancement = find(player, advancementId);
        return advancement != null && player.getAdvancements().getOrStartProgress(advancement).isDone();
    }

    /**
     * 授予玩家指定成就的全部剩余条件
     */
    public static void grantAdvancement(ServerPlayer player, String advancementId) {
        Advancement advancement = find(player, advancementId);
        if (advancement == null) {
            return;
        }
        AdvancementProgress progress = player.getAdvancements().getOrStartProgress(advancement);
        for (String criterion : progress.getRemainingCriteria()) {
            player.getAdvancements().award(advancement, criterion);
        }
    }
}
