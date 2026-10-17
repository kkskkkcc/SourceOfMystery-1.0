package com.sourceofmystery.entity;

import com.sourceofmystery.SourceOfMystery;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.allay.Allay;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;

/** Discovers loaded villages and relays attacks on nearby players to their guardian. */
@Mod.EventBusSubscriber(modid = SourceOfMystery.MOD_ID)
public final class GuardianSpiritSpawnHandler {
    private static final int CHECK_INTERVAL = 100;
    private static final double VILLAGER_LINK_DISTANCE_SQR = 48.0 * 48.0;

    private GuardianSpiritSpawnHandler() {}

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level)
                || level.getGameTime() % CHECK_INTERVAL != 0) return;

        GuardianVillageRegistry registry = GuardianVillageRegistry.get(level);
        List<Villager> villagers = new ArrayList<>();
        List<GuardianSpirit> loadedGuardians = new ArrayList<>();
        List<Allay> companionAllays = new ArrayList<>();
        for (Entity entity : level.getEntities().getAll()) {
            if (entity instanceof Villager villager && villager.isAlive()
                    && level.isVillage(villager.blockPosition())) villagers.add(villager);
            if (entity instanceof GuardianSpirit guardian && guardian.isAlive()) loadedGuardians.add(guardian);
            if (entity instanceof Allay allay && allay.getPersistentData().hasUUID("GuardianSpiritOwner")) {
                companionAllays.add(allay);
            }
        }
        for (Allay allay : companionAllays) {
            var owner = allay.getPersistentData().getUUID("GuardianSpiritOwner");
            if (level.getEntity(owner) == null && !registry.owns(owner)) allay.discard();
        }
        if (villagers.isEmpty()) return;
        for (List<Villager> village : groupVillagers(villagers)) {
            BlockPos center = centerOf(village);
            // The reservation survives chunk unloads and prevents duplicate spawning.
            if (registry.isOccupied(center)) continue;

            GuardianSpirit existing = null;
            for (GuardianSpirit guardian : loadedGuardians) {
                BlockPos home = guardian.getHomeVillageCenter();
                if (home != null && horizontalDistanceSqr(home, center) < VILLAGER_LINK_DISTANCE_SQR) {
                    existing = guardian;
                    break;
                }
            }
            if (existing != null) {
                registry.claim(center, existing.getUUID());
                continue;
            }

            BlockPos spawn = findSpawnPosition(level, center);
            if (spawn == null) continue;
            GuardianSpirit guardian = ModEntities.GUARDIAN_SPIRIT.get().create(level);
            if (guardian == null) continue;
            guardian.setHomeVillageCenter(center);
            guardian.moveTo(spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5,
                    level.random.nextFloat() * 360.0F, 0.0F);
            if (level.addFreshEntity(guardian)) {
                registry.claim(center, guardian.getUUID());
                loadedGuardians.add(guardian);
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerHurt(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof Player player) || !(player.level() instanceof ServerLevel level)) return;
        Entity attacker = event.getSource().getEntity();
        if (!(attacker instanceof LivingEntity living) || living instanceof Player
                || living instanceof GuardianSpirit) return;
        // Also catches bosses and projectiles whose owner is not a vanilla Enemy.
        for (GuardianSpirit guardian : level.getEntitiesOfClass(GuardianSpirit.class,
                new AABB(player.blockPosition()).inflate(32.0))) {
            if (guardian.isAlive() && guardian.distanceToSqr(living) <= 32.0 * 32.0) {
                guardian.rememberPlayerAttacker(living);
            }
        }
    }

    private static List<List<Villager>> groupVillagers(List<Villager> villagers) {
        List<List<Villager>> groups = new ArrayList<>();
        for (Villager villager : villagers) {
            List<Villager> merged = new ArrayList<>();
            merged.add(villager);
            List<List<Villager>> connected = new ArrayList<>();
            for (List<Villager> group : groups) {
                if (group.stream().anyMatch(member -> member.distanceToSqr(villager) < VILLAGER_LINK_DISTANCE_SQR)) {
                    merged.addAll(group);
                    connected.add(group);
                }
            }
            groups.removeAll(connected);
            groups.add(merged);
        }
        return groups;
    }

    private static BlockPos centerOf(List<Villager> villagers) {
        double x = 0, y = 0, z = 0;
        for (Villager villager : villagers) {
            x += villager.getX();
            y += villager.getY();
            z += villager.getZ();
        }
        int count = villagers.size();
        return BlockPos.containing(x / count, y / count, z / count);
    }

    private static BlockPos findSpawnPosition(ServerLevel level, BlockPos center) {
        for (int radius = 0; radius <= 12; radius += 3) {
            for (int dx = -radius; dx <= radius; dx += 3) {
                for (int dz = -radius; dz <= radius; dz += 3) {
                    int x = center.getX() + dx;
                    int z = center.getZ() + dz;
                    BlockPos probe = new BlockPos(x, center.getY(), z);
                    if (!level.hasChunkAt(probe) || !level.isVillage(probe)) continue;
                    int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                    BlockPos result = new BlockPos(x, ground + 2, z);
                    if (level.isEmptyBlock(result) && level.isEmptyBlock(result.above())) return result;
                }
            }
        }
        return null;
    }

    private static double horizontalDistanceSqr(BlockPos a, BlockPos b) {
        double dx = a.getX() - b.getX();
        double dz = a.getZ() - b.getZ();
        return dx * dx + dz * dz;
    }
}
