package com.sourceofmystery.item;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import javax.annotation.Nullable;

/**
 * 护甲环绕粒子的几何形状，客户端（穿戴者自己）和服务端（广播给其他玩家）共用：
 * - 暗源之甲：紫色，1 个卫星环绕
 * - 始源龙甲：橙色，2 个卫星环绕（相隔 180 度）
 * - 神威天佑：内层 2 卫星环绕（白+黑），外层 5 个静止卫星（绿黄蓝红橙）
 * 会攻击的卫星（{@link #attackRing}）出击时不在玩家身边绘制，由 SatelliteAttackHandler 在服务端绘制飞行/环绕轨迹。
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

    private static final double HEIGHT = 1.2; // 身体中部高度
    private static final double SATELLITE_RADIUS = 1.2;
    private static final double DIVINE_INNER_RADIUS = 1.0;
    private static final double DIVINE_OUTER_RADIUS = 1.7;
    private static final double ANGLE_PER_TICK = 0.15; // 约 2 秒转一圈
    private static final float BOLD_SCALE = 2.5f; // 攻击时卫星粒子的大小（普通为 1.0）

    private static final SatelliteRing DARK_SOURCE_RING = new SatelliteRing(SATELLITE_RADIUS, true, PURPLE);
    private static final SatelliteRing ORIGIN_DRAGON_RING = new SatelliteRing(SATELLITE_RADIUS, true, ORANGE, ORANGE);
    private static final SatelliteRing DIVINE_OUTER_RING = new SatelliteRing(DIVINE_OUTER_RADIUS, false, DIVINE_OUTER);

    @FunctionalInterface
    public interface Emitter {
        void emit(ParticleOptions particle, double x, double y, double z);
    }

    /**
     * 一圈会出击的卫星：半径、待命时是否旋转、每颗的颜色（普通 / 加粗）
     */
    public static final class SatelliteRing {
        private final double radius;
        private final boolean rotating;
        private final DustParticleOptions[] particles;
        private final DustParticleOptions[] boldParticles;

        private SatelliteRing(double radius, boolean rotating, DustParticleOptions... particles) {
            this.radius = radius;
            this.rotating = rotating;
            this.particles = particles;
            this.boldParticles = new DustParticleOptions[particles.length];
            for (int i = 0; i < particles.length; i++) {
                boldParticles[i] = new DustParticleOptions(particles[i].getColor(), BOLD_SCALE);
            }
        }

        public int size() {
            return particles.length;
        }

        public DustParticleOptions particle(int index) {
            return particles[index];
        }

        public DustParticleOptions boldParticle(int index) {
            return boldParticles[index];
        }

        /**
         * 第 index 颗卫星待命时的位置
         */
        public Vec3 idlePos(Player wearer, long gameTime, int index) {
            double angle = (rotating ? baseAngle(gameTime) : 0) + index * 2 * Math.PI / particles.length;
            return new Vec3(wearer.getX() + radius * Math.cos(angle),
                    wearer.getY() + HEIGHT,
                    wearer.getZ() + radius * Math.sin(angle));
        }
    }

    private ArmorParticlePattern() {
    }

    private static DustParticleOptions dust(float r, float g, float b) {
        return new DustParticleOptions(new Vector3f(r, g, b), 1.0f);
    }

    private static double baseAngle(long gameTime) {
        return (gameTime * ANGLE_PER_TICK) % (2 * Math.PI);
    }

    /**
     * 该胸甲会出击的卫星；没有攻击卫星的胸甲返回 null
     */
    @Nullable
    public static SatelliteRing attackRing(@Nullable ArmorMaterial material) {
        if (material == MysteryArmorMaterial.DARK_SOURCE) {
            return DARK_SOURCE_RING;
        } else if (material == MysteryArmorMaterial.ORIGIN_DRAGON) {
            return ORIGIN_DRAGON_RING;
        } else if (material == MysteryArmorMaterial.DIVINE_BLESSING) {
            return DIVINE_OUTER_RING;
        }
        return null;
    }

    /**
     * 按穿戴者当前胸甲生成一帧粒子；不是带特效的胸甲时什么都不做
     *
     * @param busyMask 正在出击的卫星（第 i 位为 1 表示第 i 颗不在玩家身边）
     */
    public static void emit(Player wearer, long gameTime, int busyMask, Emitter emitter) {
        ArmorMaterial material = ChestplateEffectHandler.getChestplateMaterial(wearer);
        if (material == MysteryArmorMaterial.DIVINE_BLESSING) {
            // 内环黑白两颗只做装饰，不参与攻击
            double angle = baseAngle(gameTime);
            double y = wearer.getY() + HEIGHT;
            for (int i = 0; i < 2; i++) {
                double a = angle + i * Math.PI;
                emitter.emit(i == 0 ? WHITE : BLACK,
                        wearer.getX() + DIVINE_INNER_RADIUS * Math.cos(a), y, wearer.getZ() + DIVINE_INNER_RADIUS * Math.sin(a));
            }
        }

        SatelliteRing ring = attackRing(material);
        if (ring == null) {
            return;
        }
        for (int i = 0; i < ring.size(); i++) {
            if ((busyMask & (1 << i)) == 0) {
                Vec3 pos = ring.idlePos(wearer, gameTime, i);
                emitter.emit(ring.particle(i), pos.x, pos.y, pos.z);
            }
        }
    }
}
