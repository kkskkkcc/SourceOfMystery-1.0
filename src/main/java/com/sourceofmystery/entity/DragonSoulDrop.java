package com.sourceofmystery.entity;

import com.sourceofmystery.SourceOfMystery;
import com.sourceofmystery.item.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 每只末影龙死亡时掉落一个龙魂。
 * 末影龙死在半空中，直接掉落很容易落进虚空，所以落在击杀者脚下；
 * 没有玩家击杀者时，放在主岛中央的传送门柱顶端。
 */
@Mod.EventBusSubscriber(modid = SourceOfMystery.MOD_ID)
public final class DragonSoulDrop {

    // 标记已掉落过，确保同一只末影龙只掉一个
    private static final String DROPPED_TAG = SourceOfMystery.MOD_ID + ".dragon_soul_dropped";

    private DragonSoulDrop() {
    }

    @SubscribeEvent
    public static void onDragonDeath(LivingDeathEvent event) {
        LivingEntity dragon = event.getEntity();
        if (dragon.getType() != EntityType.ENDER_DRAGON
                || !(dragon.level() instanceof ServerLevel level)
                || dragon.getTags().contains(DROPPED_TAG)) {
            return;
        }
        dragon.addTag(DROPPED_TAG);

        Vec3 pos;
        if (dragon.getKillCredit() instanceof Player killer && killer.level() == level) {
            pos = killer.position();
        } else {
            BlockPos pillarTop = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, BlockPos.ZERO);
            pos = Vec3.atBottomCenterOf(pillarTop).add(0, 0.5, 0);
        }

        ItemEntity soul = new ItemEntity(level, pos.x, pos.y, pos.z, new ItemStack(ModItems.DRAGON_SOUL.get()), 0, 0, 0);
        soul.setDefaultPickUpDelay();
        soul.setUnlimitedLifetime();
        level.addFreshEntity(soul);
    }
}
