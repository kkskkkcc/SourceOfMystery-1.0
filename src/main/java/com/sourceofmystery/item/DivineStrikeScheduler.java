package com.sourceofmystery.item;

import com.sourceofmystery.SourceOfMystery;
import com.sourceofmystery.config.MysteryConfig;
import com.sourceofmystery.energy.MysteryEnergy;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * 神威天罚的延迟天雷：命中后目标脚下先出现法阵，1 秒后落下一道闪电并造成无视护甲的真实伤害。
 * 同一个目标同时只会有一道天雷在酝酿，避免连续攻击把能量一次扣光。
 * 天雷落下前目标已经死亡或离开（例如被燃烧、凋零打死），退还消耗的神秘之能。
 */
@Mod.EventBusSubscriber(modid = SourceOfMystery.MOD_ID)
public final class DivineStrikeScheduler {

    private static final int DELAY_TICKS = 20; // 1 秒
    private static final int CIRCLE_POINTS = 20;
    private static final double CIRCLE_PADDING = 0.6; // 法阵半径 = 目标半宽 + 该值
    private static final DustParticleOptions CIRCLE_OUTER = new DustParticleOptions(new Vector3f(1.0f, 0.8f, 0.2f), 1.5f);
    private static final DustParticleOptions CIRCLE_INNER = new DustParticleOptions(new Vector3f(0.75f, 0.45f, 1.0f), 1.2f);

    private static final List<Strike> PENDING = new ArrayList<>();

    private DivineStrikeScheduler() {
    }

    private static final class Strike {
        final ServerPlayer attacker;
        final LivingEntity target;
        final long energyCost;
        int ticksLeft = DELAY_TICKS;

        Strike(ServerPlayer attacker, LivingEntity target, long energyCost) {
            this.attacker = attacker;
            this.target = target;
            this.energyCost = energyCost;
        }
    }

    public static boolean isPending(LivingEntity target) {
        for (Strike strike : PENDING) {
            if (strike.target == target) {
                return true;
            }
        }
        return false;
    }

    /**
     * 登记一道天雷；调用前应已扣除 energyCost
     */
    public static void schedule(ServerPlayer attacker, LivingEntity target, long energyCost) {
        PENDING.add(new Strike(attacker, target, energyCost));
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || PENDING.isEmpty()) {
            return;
        }
        Iterator<Strike> it = PENDING.iterator();
        while (it.hasNext()) {
            Strike strike = it.next();
            LivingEntity target = strike.target;
            if (!target.isAlive() || target.isRemoved() || !(target.level() instanceof ServerLevel level)) {
                it.remove();
                refund(strike);
                continue;
            }

            drawCircle(level, target, strike.ticksLeft);
            if (--strike.ticksLeft <= 0) {
                it.remove();
                strike(level, strike.attacker, target);
            }
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        PENDING.clear();
    }

    /**
     * 退还能量（不提升上限）；攻击者已下线或已重生为新实体时不退还
     */
    private static void refund(Strike strike) {
        ServerPlayer attacker = strike.attacker;
        if (strike.energyCost > 0 && !attacker.isRemoved()) {
            MysteryEnergy.setEnergy(attacker, MysteryEnergy.getEnergy(attacker) + strike.energyCost);
            MysteryEnergy.syncToClient(attacker);
        }
    }

    private static void strike(ServerLevel level, ServerPlayer attacker, LivingEntity target) {
        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
        if (bolt != null) {
            bolt.moveTo(target.getX(), target.getY(), target.getZ());
            bolt.setVisualOnly(true); // 只要画面和雷声，伤害由下面的真实伤害负责
            level.addFreshEntity(bolt);
        }

        // 清掉受击无敌时间，否则刚挨过一刀的目标会直接忽略这次伤害
        target.invulnerableTime = 0;
        // indirectMagic：magic 类型无视护甲，且归属玩家（触发击杀 +10 神秘之能、击杀 Boss 成就）
        float damage = MysteryConfig.DIVINE_PUNISHMENT_STRIKE_DAMAGE.get().floatValue();
        target.hurt(level.damageSources().indirectMagic(attacker, attacker), damage);

        spawnParticleCloud(level, ParticleTypes.ENCHANT, target, 30, 3.0, 0.05);
        spawnParticleCloud(level, ParticleTypes.FIREWORK, target, 20, 2.0, 0.02);
        spawnParticleCloud(level, ParticleTypes.WITCH, target, 15, 2.0, 0.02);
    }

    /**
     * 目标脚下的法阵：外圈金色圆环，内圈紫色五芒星，随时间旋转并逐渐收紧
     */
    private static void drawCircle(ServerLevel level, LivingEntity target, int ticksLeft) {
        double radius = target.getBbWidth() * 0.5 + CIRCLE_PADDING;
        double y = target.getY() + 0.05;
        double spin = ticksLeft * 0.15;
        for (int i = 0; i < CIRCLE_POINTS; i++) {
            double angle = spin + i * 2 * Math.PI / CIRCLE_POINTS;
            send(level, CIRCLE_OUTER, target.getX() + radius * Math.cos(angle), y, target.getZ() + radius * Math.sin(angle));
        }
        // 五芒星：依次连接圆上每隔一个的五个顶点，每条边取 3 个点
        double inner = radius * 0.85;
        for (int i = 0; i < 5; i++) {
            double a0 = -spin + i * 4 * Math.PI / 5;
            double a1 = -spin + (i + 1) * 4 * Math.PI / 5;
            for (int s = 0; s < 3; s++) {
                double t = s / 3.0;
                double x = inner * ((1 - t) * Math.cos(a0) + t * Math.cos(a1));
                double z = inner * ((1 - t) * Math.sin(a0) + t * Math.sin(a1));
                send(level, CIRCLE_INNER, target.getX() + x, y, target.getZ() + z);
            }
        }
    }

    private static void send(ServerLevel level, ParticleOptions particle, double x, double y, double z) {
        level.sendParticles(particle, x, y, z, 1, 0, 0, 0, 0);
    }

    private static void spawnParticleCloud(ServerLevel level, ParticleOptions particle, LivingEntity target,
                                           int count, double size, double speed) {
        for (int i = 0; i < count; i++) {
            level.sendParticles(particle,
                    target.getX() + (level.random.nextDouble() - 0.5) * size,
                    target.getY() + level.random.nextDouble() * size,
                    target.getZ() + (level.random.nextDouble() - 0.5) * size,
                    1, 0, 0, 0, speed);
        }
    }
}
