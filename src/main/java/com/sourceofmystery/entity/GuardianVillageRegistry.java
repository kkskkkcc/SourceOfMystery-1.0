package com.sourceofmystery.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import javax.annotation.Nullable;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** One persistent guardian reservation for each detected village area in a dimension. */
public final class GuardianVillageRegistry extends SavedData {
    private static final String DATA_NAME = "sourceofmystery_guardian_villages";
    private static final double SAME_VILLAGE_DISTANCE_SQR = 48.0 * 48.0;
    private final Map<BlockPos, UUID> guardians = new LinkedHashMap<>();

    private GuardianVillageRegistry() {}

    public static GuardianVillageRegistry get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(GuardianVillageRegistry::load,
                GuardianVillageRegistry::new, DATA_NAME);
    }

    @Nullable
    public BlockPos findArea(BlockPos candidate) {
        for (BlockPos anchor : guardians.keySet()) {
            if (horizontalDistanceSqr(anchor, candidate) < SAME_VILLAGE_DISTANCE_SQR) return anchor;
        }
        return null;
    }

    public boolean isOccupied(BlockPos candidate) {
        return findArea(candidate) != null;
    }

    public boolean owns(UUID guardian) {
        return guardians.containsValue(guardian);
    }

    /** Claim after addFreshEntity succeeds. A village can never have two reservations. */
    public boolean claim(BlockPos candidate, UUID guardian) {
        BlockPos existing = findArea(candidate);
        if (existing != null) return guardian.equals(guardians.get(existing));
        guardians.put(candidate.immutable(), guardian);
        setDirty();
        return true;
    }

    /** Transfer the reservation when a displaced guardian adopts a nearer unguarded village. */
    public boolean move(UUID guardian, BlockPos destination) {
        BlockPos existing = findArea(destination);
        if (existing != null && !guardian.equals(guardians.get(existing))) return false;
        guardians.entrySet().removeIf(entry -> guardian.equals(entry.getValue()));
        guardians.put(destination.immutable(), guardian);
        setDirty();
        return true;
    }

    public void release(UUID guardian) {
        if (guardians.entrySet().removeIf(entry -> guardian.equals(entry.getValue()))) setDirty();
    }

    private static double horizontalDistanceSqr(BlockPos a, BlockPos b) {
        long dx = (long) a.getX() - b.getX();
        long dz = (long) a.getZ() - b.getZ();
        return (double) dx * dx + (double) dz * dz;
    }

    private static GuardianVillageRegistry load(CompoundTag tag) {
        GuardianVillageRegistry registry = new GuardianVillageRegistry();
        for (Tag entry : tag.getList("Villages", Tag.TAG_COMPOUND)) {
            CompoundTag item = (CompoundTag) entry;
            if (item.hasUUID("Guardian")) {
                registry.guardians.put(BlockPos.of(item.getLong("Anchor")), item.getUUID("Guardian"));
            }
        }
        return registry;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag villages = new ListTag();
        guardians.forEach((anchor, guardian) -> {
            CompoundTag item = new CompoundTag();
            item.putLong("Anchor", anchor.asLong());
            item.putUUID("Guardian", guardian);
            villages.add(item);
        });
        tag.put("Villages", villages);
        return tag;
    }
}
