package com.sourceofmystery.item;

import com.sourceofmystery.SourceOfMystery;
import com.sourceofmystery.config.MysteryConfig;
import com.sourceofmystery.energy.MysteryEnergy;
import com.sourceofmystery.network.ModNetwork;
import com.sourceofmystery.network.SatelliteSyncPacket;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

import javax.annotation.Nullable;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 神威天佑外环 5 颗卫星（绿黄蓝红橙）的自动攻击。每颗卫星独立运作：
 * <ol>
 *     <li>待命：停在玩家身边的固定位置；冷却结束后，索敌范围内有目标且神秘之能足够时出击；</li>
 *     <li>出击：消耗神秘之能，穿墙飞向目标；</li>
 *     <li>攻击：环绕目标，每秒造成一次伤害，持续数秒；</li>
 *     <li>返回：飞回玩家身边，开始计算冷却。</li>
 * </ol>
 * 目标为范围内的敌对生物，以及最近攻击过穿戴者的实体（包括可以 PvP 的玩家）。
 * 优先分散攻击不同目标，目标不够时多颗卫星可以攻击同一个目标。
 * 内环黑白两颗卫星不参与攻击。状态只保存在服务端内存中，重新登录后重置。
 */
@Mod.EventBusSubscriber(modid = SourceOfMystery.MOD_ID)
public final class DivineSatelliteHandler {

    private static final int SATELLITE_COUNT = ArmorParticlePattern.DIVINE_OUTER_COUNT;
    private static final int SEARCH_INTERVAL = 5; // 每 5 tick 索敌一次
    private static final int HIT_INTERVAL = 20; // 每秒一次伤害
    private static final double OUTBOUND_SPEED = 1.2; // 格/tick
    private static final double RETURN_SPEED = 1.5;
    private static final int MAX_OUTBOUND_TICKS = 100; // 5 秒追不上目标就放弃
    private static final double ORBIT_ANGLE_PER_TICK = 0.35;
    private static final double ORBIT_PADDING = 0.8; // 环绕半径 = 目标半宽 + 该值
    private static final int RETALIATE_TICKS = 200; // 攻击过穿戴者的实体在 10 秒内算作目标
    private static final int TRAIL_POINTS = 3; // 飞行时每 tick 画的拖尾粒子数

    private static final Map<UUID, Satellites> STATES = new HashMap<>();

    private DivineSatelliteHandler() {
    }

    private enum Phase {
        IDLE, OUTBOUND, ORBIT, RETURN
    }

    private static final class Satellite {
        Phase phase = Phase.IDLE;
        int cooldown;
        int phaseTicks;
        Vec3 pos = Vec3.ZERO;
        @Nullable
        LivingEntity target;

        boolean busy() {
            return phase != Phase.IDLE;
        }

        void startReturn() {
            phase = Phase.RETURN;
            phaseTicks = 0;
            target = null;
        }
    }

    private static final class Satellites {
        final Satellite[] satellites = new Satellite[SATELLITE_COUNT];
        /** 最近攻击过穿戴者的实体 -> 失效的游戏时间 */
        final Map<LivingEntity, Long> attackers = new HashMap<>();
        @Nullable
        ServerLevel level;
        int syncedMask;

        Satellites() {
            for (int i = 0; i < SATELLITE_COUNT; i++) {
                satellites[i] = new Satellite();
            }
        }

        int busyMask() {
            int mask = 0;
            for (int i = 0; i < SATELLITE_COUNT; i++) {
                if (satellites[i].busy()) {
                    mask |= 1 << i;
                }
            }
            return mask;
        }

        /**
         * 所有出击中的卫星立即回到身边并进入冷却（脱下胸甲、死亡、切换维度时）
         */
        void recallAll() {
            for (Satellite satellite : satellites) {
                if (satellite.busy()) {
                    satellite.phase = Phase.IDLE;
                    satellite.target = null;
                    satellite.cooldown = cooldownTicks();
                }
            }
        }
    }

    /**
     * 正在出击的外层卫星掩码，供服务端广播环绕粒子时跳过这些卫星
     */
    public static int busyMask(Player player) {
        Satellites state = STATES.get(player.getUUID());
        return state == null ? 0 : state.busyMask();
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)
                || !(player.level() instanceof ServerLevel level)) {
            return;
        }

        boolean active = player.isAlive() && !player.isSpectator()
                && ChestplateEffectHandler.getChestplateMaterial(player) == MysteryArmorMaterial.DIVINE_BLESSING;
        Satellites state = active ? STATES.computeIfAbsent(player.getUUID(), id -> new Satellites()) : STATES.get(player.getUUID());
        if (state == null) {
            return;
        }

        if (!active || state.level != level) {
            state.recallAll();
            state.level = level;
        }

        long gameTime = level.getGameTime();
        state.attackers.entrySet().removeIf(e -> e.getValue() < gameTime || e.getKey().isRemoved());

        for (int i = 0; i < SATELLITE_COUNT; i++) {
            tickSatellite(player, level, state.satellites[i], i);
        }
        if (active && gameTime % SEARCH_INTERVAL == 0) {
            launchReady(player, level, state);
        }
        syncMask(player, state);
    }

    /**
     * 记录攻击穿戴者的实体，卫星会反击它们（即使这次伤害被胸甲免疫或抵挡）
     */
    @SubscribeEvent(receiveCanceled = true)
    public static void onWearerAttacked(LivingAttackEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || ChestplateEffectHandler.getChestplateMaterial(player) != MysteryArmorMaterial.DIVINE_BLESSING
                || !(event.getSource().getEntity() instanceof LivingEntity attacker)
                || attacker == player) {
            return;
        }
        STATES.computeIfAbsent(player.getUUID(), id -> new Satellites())
                .attackers.put(attacker, player.level().getGameTime() + RETALIATE_TICKS);
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        STATES.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        STATES.clear();
    }

    private static void tickSatellite(ServerPlayer player, ServerLevel level, Satellite satellite, int index) {
        Vec3 previous = satellite.pos;
        LivingEntity target = satellite.target;

        switch (satellite.phase) {
            case IDLE -> {
                if (satellite.cooldown > 0) {
                    satellite.cooldown--;
                }
                return; // 待命的卫星由环绕粒子绘制
            }
            case OUTBOUND -> {
                if (!isAttackable(target, level)) {
                    satellite.startReturn();
                } else if (moveTowards(satellite, orbitCenter(target), OUTBOUND_SPEED)) {
                    satellite.phase = Phase.ORBIT;
                    satellite.phaseTicks = 0;
                } else if (++satellite.phaseTicks > MAX_OUTBOUND_TICKS) {
                    satellite.startReturn();
                }
            }
            case ORBIT -> {
                if (!isAttackable(target, level)) {
                    satellite.startReturn();
                    break;
                }
                if (satellite.phaseTicks % HIT_INTERVAL == 0) {
                    hit(player, level, target);
                }
                satellite.phaseTicks++;
                double radius = target.getBbWidth() * 0.5 + ORBIT_PADDING;
                double angle = satellite.phaseTicks * ORBIT_ANGLE_PER_TICK + index * 2 * Math.PI / SATELLITE_COUNT;
                satellite.pos = orbitCenter(target).add(radius * Math.cos(angle), 0, radius * Math.sin(angle));
                if (satellite.phaseTicks >= MysteryConfig.SATELLITE_ATTACK_SECONDS.get() * 20) {
                    satellite.startReturn();
                }
            }
            case RETURN -> {
                if (moveTowards(satellite, ArmorParticlePattern.divineOuterPos(player, index), RETURN_SPEED)) {
                    satellite.phase = Phase.IDLE;
                    satellite.cooldown = cooldownTicks();
                    return;
                }
            }
        }
        drawTrail(level, index, previous, satellite.pos);
    }

    /**
     * 给冷却完毕的卫星分配目标并出击：优先攻击还没有卫星在打的目标，其次按距离由近到远
     */
    private static void launchReady(ServerPlayer player, ServerLevel level, Satellites state) {
        List<LivingEntity> candidates = null;
        Map<LivingEntity, Integer> assigned = new HashMap<>();
        for (Satellite satellite : state.satellites) {
            if (satellite.busy() && satellite.target != null) {
                assigned.merge(satellite.target, 1, Integer::sum);
            }
        }

        long cost = MysteryConfig.SATELLITE_ENERGY_COST.get();
        for (int i = 0; i < SATELLITE_COUNT; i++) {
            Satellite satellite = state.satellites[i];
            if (satellite.busy() || satellite.cooldown > 0) {
                continue;
            }
            if (candidates == null) {
                candidates = findTargets(player, level, state);
            }
            if (candidates.isEmpty()) {
                return;
            }

            LivingEntity target = candidates.get(0);
            int fewest = Integer.MAX_VALUE;
            for (LivingEntity candidate : candidates) {
                int count = assigned.getOrDefault(candidate, 0);
                if (count < fewest) {
                    fewest = count;
                    target = candidate;
                }
            }

            if (cost > 0 && !MysteryEnergy.consumeEnergy(player, cost)) {
                return; // 能量不足，剩下的卫星也不出击
            }
            satellite.phase = Phase.OUTBOUND;
            satellite.phaseTicks = 0;
            satellite.target = target;
            satellite.pos = ArmorParticlePattern.divineOuterPos(player, i);
            assigned.merge(target, 1, Integer::sum);
        }
    }

    /**
     * 索敌范围内的目标，按距离由近到远排序
     */
    private static List<LivingEntity> findTargets(ServerPlayer player, ServerLevel level, Satellites state) {
        double range = MysteryConfig.SATELLITE_RANGE.get();
        double rangeSq = range * range;
        return level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(range),
                        entity -> entity != player && isAttackable(entity, level)
                                && player.distanceToSqr(entity) <= rangeSq
                                && isHostile(player, state, entity))
                .stream()
                .sorted(Comparator.comparingDouble(player::distanceToSqr))
                .toList();
    }

    private static boolean isHostile(ServerPlayer player, Satellites state, LivingEntity entity) {
        boolean attackedWearer = state.attackers.containsKey(entity);
        if (entity instanceof Player other) {
            // 只反击攻击过自己的玩家，并遵守服务器 PvP 设置与队伍友伤设置
            return attackedWearer && EntitySelector.NO_CREATIVE_OR_SPECTATOR.test(other) && player.canHarmPlayer(other);
        }
        if (player.isAlliedTo(entity) || entity.isAlliedTo(player)) {
            return false; // 队友、自己驯服的生物
        }
        return entity instanceof Enemy || attackedWearer;
    }

    private static boolean isAttackable(@Nullable LivingEntity target, ServerLevel level) {
        return target != null && target.isAlive() && !target.isRemoved() && target.level() == level;
    }

    private static void hit(ServerPlayer player, ServerLevel level, LivingEntity target) {
        // 每颗卫星独立计算伤害，不受其他卫星或近战攻击的无敌帧影响
        target.invulnerableTime = 0;
        target.hurt(level.damageSources().indirectMagic(player, player), MysteryConfig.SATELLITE_DAMAGE.get().floatValue());
        Vec3 center = orbitCenter(target);
        level.sendParticles(ParticleTypes.ENCHANTED_HIT, center.x, center.y, center.z, 8,
                target.getBbWidth() * 0.4, target.getBbHeight() * 0.3, target.getBbWidth() * 0.4, 0.1);
    }

    private static Vec3 orbitCenter(LivingEntity target) {
        return target.position().add(0, target.getBbHeight() * 0.5, 0);
    }

    /**
     * 直线飞向目的地（不做碰撞，穿墙），到达时返回 true
     */
    private static boolean moveTowards(Satellite satellite, Vec3 destination, double speed) {
        Vec3 delta = destination.subtract(satellite.pos);
        double distance = delta.length();
        if (distance <= speed) {
            satellite.pos = destination;
            return true;
        }
        satellite.pos = satellite.pos.add(delta.scale(speed / distance));
        return false;
    }

    /**
     * 从上一帧位置到当前位置画几颗粒子，飞行时形成拖尾；sendParticles 会发给附近所有玩家
     */
    private static void drawTrail(ServerLevel level, int index, Vec3 from, Vec3 to) {
        for (int i = 1; i <= TRAIL_POINTS; i++) {
            Vec3 point = from.lerp(to, (double) i / TRAIL_POINTS);
            level.sendParticles(ArmorParticlePattern.divineOuterParticle(index), point.x, point.y, point.z, 1, 0, 0, 0, 0);
        }
    }

    private static void syncMask(ServerPlayer player, Satellites state) {
        int mask = state.busyMask();
        if (mask != state.syncedMask) {
            state.syncedMask = mask;
            ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new SatelliteSyncPacket(mask));
        }
    }

    private static int cooldownTicks() {
        return MysteryConfig.SATELLITE_COOLDOWN_SECONDS.get() * 20;
    }
}
