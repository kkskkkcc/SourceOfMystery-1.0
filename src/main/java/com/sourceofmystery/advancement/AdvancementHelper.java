package com.sourceofmystery.advancement;

import com.sourceofmystery.SourceOfMystery;
import net.minecraft.advancements.Advancement;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import javax.annotation.Nullable;

/**
 * 成就系统辅助类
 */
public class AdvancementHelper {

    private static final String MOD_ID = SourceOfMystery.MOD_ID;

    /**
     * 检查玩家是否已完成指定成就
     */
    public static boolean hasAdvancement(ServerPlayer player, String advancementId) {
        ResourceLocation location = new ResourceLocation(MOD_ID, advancementId);
        Advancement advancement = player.getServer().getAdvancements().getAdvancement(location);

        if (advancement == null) {
            SourceOfMystery.LOGGER.warn("Advancement not found: {}", location);
            return false;
        }

        // 纯检查：只判断是否已完成，不授予
        return player.getAdvancements().getOrStartProgress(advancement).isDone();
    }

    /**
     * 授予玩家指定成就
     */
    public static void grantAdvancement(ServerPlayer player, String advancementId) {
        ResourceLocation location = new ResourceLocation(MOD_ID, advancementId);
        Advancement advancement = player.getServer().getAdvancements().getAdvancement(location);

        if (advancement == null) {
            SourceOfMystery.LOGGER.warn("Advancement not found: {}", location);
            return;
        }

        // 遍历所有剩余 criteria 逐个授予（用正确的 criteria 名，而非硬编码 "trigger"）
        net.minecraft.advancements.AdvancementProgress progress = player.getAdvancements().getOrStartProgress(advancement);
        for (String criteriaName : progress.getRemainingCriteria()) {
            player.getAdvancements().award(advancement, criteriaName);
        }
        SourceOfMystery.LOGGER.info("Awarded advancement {} to player {}", advancementId, player.getName().getString());
    }

    /**
     * 撤销玩家指定成就
     */
    public static void revokeAdvancement(ServerPlayer player, String advancementId) {
        ResourceLocation location = new ResourceLocation(MOD_ID, advancementId);
        Advancement advancement = player.getServer().getAdvancements().getAdvancement(location);

        if (advancement == null) {
            SourceOfMystery.LOGGER.warn("Advancement not found: {}", location);
            return;
        }

        player.getAdvancements().revoke(advancement, "trigger");
    }
}
