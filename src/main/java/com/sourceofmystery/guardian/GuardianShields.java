package com.sourceofmystery.guardian;

import com.sourceofmystery.SourceOfMystery;
import com.sourceofmystery.block.ModBlocks;
import com.sourceofmystery.entity.DivineHeavenlyDaoBoss;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.TraceableEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 守护结界：每座已激活的守护祭坛罩住以它所在区块为中心的 3x3 区块（对齐区块网格，48x48 格），
 * 高度从世界底部到 y = 255。
 * <p>
 * 已激活的祭坛按维度记录在 SavedData 里（位置 + 激活时刻），用来：
 * <ul>
 *     <li>激活前检测冲突：两座祭坛的结界不能重叠（区块中心在 x、z 上都相距 2 个区块以内即为重叠）</li>
 *     <li>伤害 / 刷怪 / 爆炸事件里快速判断某个位置是否受保护（见 {@link GuardianShieldEvents}）</li>
 * </ul>
 * 结界在激活动画放出光柱的那一刻（激活后 {@link #DEPLOY_TICKS} tick）才开始生效。
 */
public final class GuardianShields {

    /** 结界顶部（含） */
    public static final int TOP_Y = 255;
    /** 结界半径（区块）：1 = 3x3 区块 */
    public static final int CHUNK_RADIUS = 1;
    /** 激活动画放出光柱的时刻（7.4 秒），结界从这一刻起生效 */
    public static final int DEPLOY_TICKS = 148;
    /** 激活动画总长（9 秒），之后进入循环动画 */
    public static final int ACTIVATE_TICKS = 180;

    /** 不会被结界秒杀、而是被弹出结界外的 Boss（数据包可以增删） */
    public static final TagKey<EntityType<?>> SHIELD_BOSSES =
            TagKey.create(Registries.ENTITY_TYPE, new ResourceLocation(SourceOfMystery.MOD_ID, "guardian_shield_bosses"));

    private GuardianShields() {
    }

    // ------------------------------------------------------------------ 范围

    /** 祭坛在 pos 时结界的范围（方块坐标的包围盒，max 为开区间） */
    public static AABB area(Level level, BlockPos altar) {
        int cx = altar.getX() >> 4;
        int cz = altar.getZ() >> 4;
        return new AABB((cx - CHUNK_RADIUS) * 16, level.getMinBuildHeight(), (cz - CHUNK_RADIUS) * 16,
                (cx + CHUNK_RADIUS + 1) * 16, TOP_Y + 1, (cz + CHUNK_RADIUS + 1) * 16);
    }

    /** 两座祭坛的结界是否重叠 */
    public static boolean overlaps(BlockPos a, BlockPos b) {
        int dx = Math.abs((a.getX() >> 4) - (b.getX() >> 4));
        int dz = Math.abs((a.getZ() >> 4) - (b.getZ() >> 4));
        return dx <= 2 * CHUNK_RADIUS && dz <= 2 * CHUNK_RADIUS;
    }

    // ------------------------------------------------------------------ 登记

    /** 激活前检测：返回会与之重叠的已激活祭坛（自己除外） */
    public static Optional<BlockPos> findConflict(ServerLevel level, BlockPos altar) {
        for (BlockPos other : Data.get(level).shields.keySet()) {
            if (!other.equals(altar) && overlaps(altar, other)) {
                return Optional.of(other);
            }
        }
        return Optional.empty();
    }

    public static void register(ServerLevel level, BlockPos altar, long activatedAt) {
        Data data = Data.get(level);
        Long old = data.shields.put(altar.immutable(), activatedAt);
        if (old == null || old != activatedAt) {
            data.setDirty();
        }
    }

    public static void unregister(ServerLevel level, BlockPos altar) {
        Data data = Data.get(level);
        if (data.shields.remove(altar) != null) {
            data.setDirty();
            SourceOfMystery.LOGGER.info("Guardian shield at {} in {} removed", altar, level.dimension().location());
        }
    }

    /** 已加载的区块里，登记过但方块已经不是激活的守护祭坛（被指令替换、存档编辑等）的记录清理掉 */
    static void validate(ServerLevel level) {
        Data data = Data.get(level);
        List<BlockPos> stale = new ArrayList<>();
        for (BlockPos pos : data.shields.keySet()) {
            if (level.isLoaded(pos) && !(level.getBlockState(pos).is(ModBlocks.GUARDIAN_ALTAR.get())
                    && level.getBlockState(pos).getValue(GuardianAltarBlock.ACTIVE))) {
                stale.add(pos);
            }
        }
        stale.forEach(pos -> unregister(level, pos));
    }

    public static Map<BlockPos, Long> all(ServerLevel level) {
        return Collections.unmodifiableMap(Data.get(level).shields);
    }

    // ------------------------------------------------------------------ 查询

    /** 已经展开（过了 DEPLOY_TICKS）、并且包含这个点的结界的祭坛位置 */
    @Nullable
    public static BlockPos shieldAt(Level level, Vec3 point) {
        if (!(level instanceof ServerLevel server)) {
            return null;
        }
        long now = server.getGameTime();
        for (Map.Entry<BlockPos, Long> e : Data.get(server).shields.entrySet()) {
            if (now - e.getValue() >= DEPLOY_TICKS && area(server, e.getKey()).contains(point)) {
                return e.getKey();
            }
        }
        return null;
    }

    public static boolean isProtected(Level level, Vec3 point) {
        return shieldAt(level, point) != null;
    }

    public static boolean isProtected(Level level, BlockPos pos) {
        return isProtected(level, Vec3.atCenterOf(pos));
    }

    /** 实体（按包围盒中心）是否在某个已展开的结界里 */
    public static boolean isProtected(Entity entity) {
        return isProtected(entity.level(), entity.getBoundingBox().getCenter());
    }

    /**
     * 给 Boss 的招式用：目标在结界里时，Boss 不能对它出手（拉拽、抓取、击飞等不走伤害的效果也一样）
     */
    public static boolean shieldedFromBoss(Entity target) {
        return isProtected(target);
    }

    /**
     * Boss 本身、Boss 的召唤物（神威天道召唤的凋灵），以及 Boss 发出的弹射物 / 法术实体
     */
    public static boolean isBossRelated(@Nullable Entity entity) {
        return isBossRelated(entity, 0);
    }

    private static boolean isBossRelated(@Nullable Entity entity, int depth) {
        if (entity == null || depth > 3) {
            return false;
        }
        if (isBoss(entity) || entity.getTags().contains(DivineHeavenlyDaoBoss.MINION_TAG)) {
            return true;
        }
        if (entity instanceof Projectile projectile) {
            return isBossRelated(projectile.getOwner(), depth + 1);
        }
        if (entity instanceof TraceableEntity traceable) {
            return isBossRelated(traceable.getOwner(), depth + 1);
        }
        return false;
    }

    public static boolean isBoss(Entity entity) {
        return entity.getType().is(SHIELD_BOSSES);
    }

    // ------------------------------------------------------------------ 持久化

    private static class Data extends SavedData {
        private static final String DATA_NAME = SourceOfMystery.MOD_ID + "_guardian_shields";

        /** 祭坛位置 -> 激活时的 gameTime */
        private final Map<BlockPos, Long> shields = new LinkedHashMap<>();

        static Data get(ServerLevel level) {
            return level.getDataStorage().computeIfAbsent(Data::load, Data::new, DATA_NAME);
        }

        static Data load(CompoundTag tag) {
            Data data = new Data();
            ListTag list = tag.getList("Shields", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag e = list.getCompound(i);
                data.shields.put(BlockPos.of(e.getLong("Pos")), e.getLong("ActivatedAt"));
            }
            return data;
        }

        @Override
        public CompoundTag save(CompoundTag tag) {
            ListTag list = new ListTag();
            shields.forEach((pos, at) -> {
                CompoundTag e = new CompoundTag();
                e.putLong("Pos", pos.asLong());
                e.putLong("ActivatedAt", at);
                list.add(e);
            });
            tag.put("Shields", list);
            return tag;
        }
    }
}
