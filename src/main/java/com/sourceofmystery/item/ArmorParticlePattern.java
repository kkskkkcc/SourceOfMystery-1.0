package com.sourceofmystery.item;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * 护甲环绕粒子的几何形状，客户端（穿戴者自己）和服务端（广播给其他玩家）共用：
 * - 暗源之甲：紫色，1 个卫星环绕
 * - 始源龙甲：橙色，2 个卫星环绕（相隔 180 度）
 * - 神威天佑：内层 2 卫星环绕（白+黑），外层 5 个静止卫星（绿黄蓝红橙）；
 *   外层卫星出击时不在玩家身边绘制，由 DivineSatelliteHandler 在服务端绘制飞行/环绕轨迹
 */
public final class ArmorParticlePattern {

    private static final DustParticleOptions PURPLE = dust(0.62f, 0.25f, 0.85f);
    private static final DustParticleOptions ORANGE = dust(1.0f, 0.55f, 0.1f);
    private static final DustParticleOptions WHITE = dust(1.0f, 1.0f, 1.0f);
    private static final DustParticleOptions BLACK = dust(0.05f, 0.05f, 0.05f);
    private static final DustParticleOptions[] DIVINE_OUTER = {
            dust(0.2f, 0.9f, 0.2f),  // 绿
            dust(1.0f, 0.85f, 0.1f), // 黄
            dust(0.2f, 0.4f, 1.0f),  // 蓝
            dust(1.0f, 0.1f, 0.1f),  // 红
            ORANGE                   // 橙
    };

    /** 外层卫星数量 */
    public static final int DIVINE_OUTER_COUNT = DIVINE_OUTER.length;

    private static final double HEIGHT = 1.2; // 身体中部高度
    private static final double SATELLITE_RADIUS = 1.2;
    private static final double DIVINE_INNER_RADIUS = 1.0;
    private static final double DIVINE_OUTER_RADIUS = 1.7;
    private static final double ANGLE_PER_TICK = 0.15; // 约 2 秒转一圈

    @FunctionalInterface
    public interface Emitter {
        void emit(ParticleOptions particle, double x, double y, double z);
    }

    private ArmorParticlePattern() {
    }

    private static DustParticleOptions dust(float r, float g, float b) {
        return new DustParticleOptions(new Vector3f(r, g, b), 1.0f);
    }

    public static DustParticleOptions divineOuterParticle(int index) {
        return DIVINE_OUTER[index];
    }

    /**
     * 外层第 index 颗卫星待命时的位置（固定角度，不旋转）
     */
    public static Vec3 divineOuterPos(Player wearer, int index) {
        double angle = index * 2 * Math.PI / DIVINE_OUTER_COUNT;
        return new Vec3(wearer.getX() + DIVINE_OUTER_RADIUS * Math.cos(angle),
                wearer.getY() + HEIGHT,
                wearer.getZ() + DIVINE_OUTER_RADIUS * Math.sin(angle));
    }

    /**
     * 按穿戴者当前胸甲生成一帧粒子；不是带特效的胸甲时什么都不做
     *
     * @param busyMask 正在出击的外层卫星（第 i 位为 1 表示第 i 颗不在玩家身边）
     */
    public static void emit(Player wearer, long gameTime, int busyMask, Emitter emitter) {
        ArmorMaterial material = ChestplateEffectHandler.getChestplateMaterial(wearer);
        double baseAngle = (gameTime * ANGLE_PER_TICK) % (2 * Math.PI);
        double y = wearer.getY() + HEIGHT;

        if (material == MysteryArmorMaterial.DARK_SOURCE) {
            ring(wearer, y, baseAngle, SATELLITE_RADIUS, emitter, PURPLE);
        } else if (material == MysteryArmorMaterial.ORIGIN_DRAGON) {
            ring(wearer, y, baseAngle, SATELLITE_RADIUS, emitter, ORANGE, ORANGE);
        } else if (material == MysteryArmorMaterial.DIVINE_BLESSING) {
            ring(wearer, y, baseAngle, DIVINE_INNER_RADIUS, emitter, WHITE, BLACK);
            for (int i = 0; i < DIVINE_OUTER_COUNT; i++) {
                if ((busyMask & (1 << i)) == 0) {
                    Vec3 pos = divineOuterPos(wearer, i);
                    emitter.emit(DIVINE_OUTER[i], pos.x, pos.y, pos.z);
                }
            }
        }
    }

    /**
     * 在穿戴者周围的圆周上均匀放置若干粒子
     */
    private static void ring(Player wearer, double y, double baseAngle, double radius, Emitter emitter,
                             DustParticleOptions... particles) {
        for (int i = 0; i < particles.length; i++) {
            double angle = baseAngle + i * 2 * Math.PI / particles.length;
            emitter.emit(particles[i], wearer.getX() + radius * Math.cos(angle), y, wearer.getZ() + radius * Math.sin(angle));
        }
    }
}
