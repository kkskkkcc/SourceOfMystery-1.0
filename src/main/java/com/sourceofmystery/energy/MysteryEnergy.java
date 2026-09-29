package com.sourceofmystery.energy;

import com.sourceofmystery.config.MysteryConfig;
import com.sourceofmystery.network.EnergySyncPacket;
import com.sourceofmystery.network.ModNetwork;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.network.PacketDistributor;

/**
 * 神秘之能系统 - 使用玩家 persistentData 中的 NBT 存储
 * 不依赖 Capability API
 */
public class MysteryEnergy {

    private static final String ROOT_KEY = "sourceofmystery";
    private static final String ENERGY_KEY = "sourceofmystery_energy";
    private static final String ENERGY_MAX_KEY = "sourceofmystery_energy_max";
    private static final String GUI_UNLOCKED_KEY = "sourceofmystery_gui_unlocked";
    private static final String LAST_REFRESH_DAY_KEY = "sourceofmystery_last_refresh_day";

    /**
     * 获取玩家的神秘之能
     */
    public static long getEnergy(Player player) {
        return getPlayerData(player).getLong(ENERGY_KEY);
    }

    /**
     * 设置玩家的神秘之能
     */
    public static void setEnergy(Player player, long energy) {
        getPlayerData(player).putLong(ENERGY_KEY, Math.max(0, energy));
    }

    /**
     * 增加神秘之能（击杀等来源），同时同步提升能量上限
     */
    public static void addEnergy(Player player, long amount) {
        if (amount > 0) {
            setEnergy(player, getEnergy(player) + amount);
            getPlayerData(player).putLong(ENERGY_MAX_KEY, getEnergyMax(player) + amount);
            syncToClient(player);
        }
    }

    /**
     * 获取玩家的神秘之能上限
     */
    public static long getEnergyMax(Player player) {
        return getPlayerData(player).getLong(ENERGY_MAX_KEY);
    }

    /**
     * 每日恢复：跨入新的一天时把当前能量回满到上限。
     * 上次恢复的天数存在玩家 NBT 中，服务器重启、切换存档都不会丢失。
     */
    public static void tickDailyRefresh(Player player, long currentDay) {
        CompoundTag tag = getPlayerData(player);
        if (!tag.contains(LAST_REFRESH_DAY_KEY)) {
            // 首次记录：只记下当前天数，不刷新
            tag.putLong(LAST_REFRESH_DAY_KEY, currentDay);
            return;
        }
        if (tag.getLong(LAST_REFRESH_DAY_KEY) != currentDay) {
            tag.putLong(LAST_REFRESH_DAY_KEY, currentDay);
            tag.putLong(ENERGY_KEY, getEnergyMax(player));
            syncToClient(player);
        }
    }

    /**
     * 消耗神秘之能，如果足够返回 true
     */
    public static boolean consumeEnergy(Player player, long amount) {
        if (amount > 0 && getEnergy(player) >= amount) {
            setEnergy(player, getEnergy(player) - amount);
            syncToClient(player);
            return true;
        }
        return false;
    }

    /**
     * 初始化玩家神秘之能（只在首次时设置）
     */
    public static void initPlayer(Player player) {
        CompoundTag tag = getPlayerData(player);
        if (!tag.contains(ENERGY_KEY)) {
            tag.putLong(ENERGY_KEY, MysteryConfig.INITIAL_ENERGY.get());
        }
        if (!tag.contains(ENERGY_MAX_KEY)) {
            // 老存档迁移：上限至少等于当前能量，避免"回满"反而降低能量
            long existingEnergy = tag.getLong(ENERGY_KEY);
            tag.putLong(ENERGY_MAX_KEY, Math.max(MysteryConfig.INITIAL_ENERGY.get(), existingEnergy));
        }
    }

    /**
     * 获取本模组在玩家 persistentData 中的数据标签（死亡、跨维度时会整体复制）
     */
    public static CompoundTag getPlayerData(Player player) {
        CompoundTag rootTag = player.getPersistentData();
        if (!rootTag.contains(ROOT_KEY)) {
            rootTag.put(ROOT_KEY, new CompoundTag());
        }
        return rootTag.getCompound(ROOT_KEY);
    }

    /**
     * 在玩家登录时调用以初始化
     */
    public static void onPlayerJoin(Player player) {
        initPlayer(player);
        syncToClient(player);
    }

    /**
     * 玩家实体被替换时（死亡重生、从末地返回主世界）复制全部神秘之能数据。
     * Forge 不会自动复制 persistentData，从末地返回时同样会生成新的玩家实体，
     * 所以这里不能只处理死亡的情况。
     */
    public static void onPlayerClone(Player original, Player player) {
        player.getPersistentData().put(ROOT_KEY, getPlayerData(original).copy());
    }

    /**
     * 在玩家击杀时调用以增加神秘之能
     */
    public static void onPlayerKill(Player player) {
        addEnergy(player, MysteryConfig.ENERGY_PER_KILL.get());
    }

    /**
     * 检查GUI是否已解锁
     */
    public static boolean isGuiUnlocked(Player player) {
        return getPlayerData(player).getBoolean(GUI_UNLOCKED_KEY);
    }

    /**
     * 设置GUI解锁状态
     */
    public static void setGuiUnlocked(Player player, boolean unlocked) {
        if (isGuiUnlocked(player) == unlocked) {
            return;
        }
        getPlayerData(player).putBoolean(GUI_UNLOCKED_KEY, unlocked);
        syncToClient(player);
    }

    /**
     * 服务端 -> 客户端：把能量、上限、GUI状态同步给玩家
     */
    public static void syncToClient(Player player) {
        if (player instanceof ServerPlayer serverPlayer) {
            ModNetwork.CHANNEL.send(
                    PacketDistributor.PLAYER.with(() -> serverPlayer),
                    new EnergySyncPacket(getEnergy(player), getEnergyMax(player), isGuiUnlocked(player))
            );
        }
    }

    /**
     * 调试用：直接设置玩家的能量和上限（用于指令调试）
     */
    public static void setEnergyAndMax(Player player, long value) {
        CompoundTag tag = getPlayerData(player);
        tag.putLong(ENERGY_KEY, Math.max(0, value));
        tag.putLong(ENERGY_MAX_KEY, Math.max(0, value));
        syncToClient(player);
    }
}
