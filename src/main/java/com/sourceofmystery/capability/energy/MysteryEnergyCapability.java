package com.sourceofmystery.capability.energy;

import com.sourceofmystery.network.EnergySyncPacket;
import com.sourceofmystery.network.ModNetwork;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.network.PacketDistributor;

/**
 * 神秘之能系统 - 使用简单的 NBT 存储
 * 不再依赖复杂的 Capability API
 */
public class MysteryEnergyCapability {

    private static final String ENERGY_KEY = "sourceofmystery_energy";
    private static final String ENERGY_MAX_KEY = "sourceofmystery_energy_max";
    private static final String GUI_UNLOCKED_KEY = "sourceofmystery_gui_unlocked";
    private static final long INITIAL_ENERGY = 100L;

    /**
     * 获取玩家的神秘之能
     */
    public static long getEnergy(Player player) {
        CompoundTag tag = getPlayerData(player);
        return tag.getLong(ENERGY_KEY);
    }

    /**
     * 设置玩家的神秘之能
     */
    public static void setEnergy(Player player, long energy) {
        CompoundTag tag = getPlayerData(player);
        tag.putLong(ENERGY_KEY, Math.max(0, energy));
    }

    /**
     * 增加神秘之能（击杀等来源），同时同步提升能量上限
     */
    public static void addEnergy(Player player, long amount) {
        if (amount > 0) {
            setEnergy(player, getEnergy(player) + amount);
            CompoundTag tag = getPlayerData(player);
            tag.putLong(ENERGY_MAX_KEY, getEnergyMax(player) + amount);
            syncToClient(player);
        }
    }

    /**
     * 获取玩家的神秘之能上限
     */
    public static long getEnergyMax(Player player) {
        CompoundTag tag = getPlayerData(player);
        return tag.getLong(ENERGY_MAX_KEY);
    }

    /**
     * 每日恢复：把当前能量回满到上限
     */
    public static void dailyRefresh(Player player) {
        CompoundTag tag = getPlayerData(player);
        tag.putLong(ENERGY_KEY, getEnergyMax(player));
        syncToClient(player);
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
            tag.putLong(ENERGY_KEY, INITIAL_ENERGY);
        }
        if (!tag.contains(ENERGY_MAX_KEY)) {
            // 老存档迁移：上限至少等于当前能量，避免"回满"反而降低能量
            long existingEnergy = tag.getLong(ENERGY_KEY);
            tag.putLong(ENERGY_MAX_KEY, Math.max(INITIAL_ENERGY, existingEnergy));
        }
    }

    /**
     * 获取玩家的持久化数据标签
     */
    private static CompoundTag getPlayerData(Player player) {
        CompoundTag rootTag = player.getPersistentData();
        if (!rootTag.contains("sourceofmystery")) {
            rootTag.put("sourceofmystery", new CompoundTag());
        }
        return rootTag.getCompound("sourceofmystery");
    }

    /**
     * 在玩家登录时调用以初始化
     */
    public static void onPlayerJoin(Player player) {
        initPlayer(player);
        syncToClient(player);
    }

    /**
     * 在玩家死亡时调用以保留神秘之能
     */
    public static void onPlayerClone(Player original, Player player, boolean wasDeath) {
        if (wasDeath) {
            long originalEnergy = getEnergy(original);
            setEnergy(player, originalEnergy);
            // 复制能量上限
            CompoundTag tag = getPlayerData(player);
            tag.putLong(ENERGY_MAX_KEY, getEnergyMax(original));
            // 复制GUI解锁状态
            boolean guiUnlocked = isGuiUnlocked(original);
            setGuiUnlocked(player, guiUnlocked);
        }
    }

    /**
     * 在玩家击杀时调用以增加神秘之能
     */
    public static void onPlayerKill(Player player) {
        addEnergy(player, 10);
    }

    /**
     * 检查GUI是否已解锁
     */
    public static boolean isGuiUnlocked(Player player) {
        CompoundTag tag = getPlayerData(player);
        return tag.getBoolean(GUI_UNLOCKED_KEY);
    }

    /**
     * 设置GUI解锁状态
     */
    public static void setGuiUnlocked(Player player, boolean unlocked) {
        CompoundTag tag = getPlayerData(player);
        tag.putBoolean(GUI_UNLOCKED_KEY, unlocked);
        syncToClient(player);
    }

    /**
     * 服务端 -> 客户端：把能量、上限、GUI状态同步给玩家
     */
    private static void syncToClient(Player player) {
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
