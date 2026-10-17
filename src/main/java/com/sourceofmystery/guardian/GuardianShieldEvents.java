package com.sourceofmystery.guardian;

import com.sourceofmystery.SourceOfMystery;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDestroyBlockEvent;
import net.minecraftforge.event.entity.living.MobSpawnEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.EnumSet;
import java.util.Set;

/**
 * 守护结界的事件：挡住 Boss 对结界内的一切攻击。
 * <ul>
 *     <li>伤害：攻击者或伤害来源实体是 Boss / Boss 的召唤物 / Boss 的弹射物和法术实体，目标在结界里 -> 取消</li>
 *     <li>爆炸：Boss 引起的爆炸不影响结界里的方块和实体</li>
 *     <li>破坏方块：Boss（激光、凋灵、末影龙撞墙）不能破坏结界里的方块</li>
 *     <li>刷怪：结界里不自然生成敌对生物（刷怪蛋、刷怪笼、指令不受影响）</li>
 * </ul>
 * 拉拽、抓取、击飞这类不走伤害的招式，在各 Boss 选目标时就排除了结界里的目标（{@link GuardianShields#shieldedFromBoss}）。
 */
@Mod.EventBusSubscriber(modid = SourceOfMystery.MOD_ID)
public class GuardianShieldEvents {

    private static final Set<MobSpawnType> NATURAL_SPAWNS = EnumSet.of(MobSpawnType.NATURAL,
            MobSpawnType.CHUNK_GENERATION, MobSpawnType.PATROL, MobSpawnType.REINFORCEMENT, MobSpawnType.JOCKEY);

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingAttack(LivingAttackEvent event) {
        if (event.getEntity().level().isClientSide || GuardianShields.isBossRelated(event.getEntity())) {
            return;
        }
        DamageSource source = event.getSource();
        if ((GuardianShields.isBossRelated(source.getEntity()) || GuardianShields.isBossRelated(source.getDirectEntity()))
                && GuardianShields.isProtected(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onExplosion(ExplosionEvent.Detonate event) {
        if (event.getLevel().isClientSide()) {
            return;
        }
        var explosion = event.getExplosion();
        if (!GuardianShields.isBossRelated(explosion.getExploder())
                && !GuardianShields.isBossRelated(explosion.getIndirectSourceEntity())) {
            return;
        }
        event.getAffectedBlocks().removeIf(pos -> GuardianShields.isProtected(event.getLevel(), pos));
        event.getAffectedEntities().removeIf(e -> !GuardianShields.isBossRelated(e) && GuardianShields.isProtected(e));
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onLivingDestroyBlock(LivingDestroyBlockEvent event) {
        if (!event.getEntity().level().isClientSide && GuardianShields.isBossRelated(event.getEntity())
                && GuardianShields.isProtected(event.getEntity().level(), event.getPos())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onMobSpawn(MobSpawnEvent.FinalizeSpawn event) {
        if (event.getEntity() instanceof Enemy && NATURAL_SPAWNS.contains(event.getSpawnType())
                && GuardianShields.isProtected(event.getEntity().level(), event.getEntity().position())) {
            event.setSpawnCancelled(true);
        }
    }

    /** 维度加载时清理过时的登记（方块被指令替换、存档被编辑等） */
    @SubscribeEvent
    public static void onLevelLoad(LevelEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel level) {
            level.getServer().execute(() -> GuardianShields.validate(level));
        }
    }
}
