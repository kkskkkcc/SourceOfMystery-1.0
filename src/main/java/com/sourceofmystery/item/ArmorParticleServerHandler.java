package com.sourceofmystery.item;

import com.sourceofmystery.SourceOfMystery;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3f;

import java.util.List;

/**
 * 护甲环绕粒子 - 服务端广播（让其他玩家也能看到穿戴者的粒子特效）
 * - 向"除穿戴者自己以外的其他玩家"定向发送环绕粒子
 * - 穿戴者自己的粒子由客户端 ArmorParticleEffect 渲染（第三人称显示，第一人称隐藏）
 */
@Mod.EventBusSubscriber(modid = SourceOfMystery.MOD_ID)
public class ArmorParticleServerHandler {

    private static final Vector3f PURPLE = new Vector3f(0.62f, 0.25f, 0.85f); // 紫色
    private static final Vector3f ORANGE = new Vector3f(1.0f, 0.55f, 0.1f);   // 橙色
    private static final Vector3f WHITE = new Vector3f(1.0f, 1.0f, 1.0f);     // 白色
    private static final Vector3f BLACK = new Vector3f(0.05f, 0.05f, 0.05f);  // 黑色
    private static final Vector3f GREEN = new Vector3f(0.2f, 0.9f, 0.2f);     // 绿色
    private static final Vector3f YELLOW = new Vector3f(1.0f, 0.85f, 0.1f);   // 黄色
    private static final Vector3f BLUE = new Vector3f(0.2f, 0.4f, 1.0f);      // 蓝色
    private static final Vector3f RED = new Vector3f(1.0f, 0.1f, 0.1f);       // 红色

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        MinecraftServer server = event.getServer();
        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        if (players.isEmpty()) {
            return;
        }

        // 遍历所有玩家，找到穿戴护甲的玩家
        for (ServerPlayer wearer : players) {
            ItemStack chest = wearer.getItemBySlot(EquipmentSlot.CHEST);
            if (!(chest.getItem() instanceof ArmorItem armor)) {
                continue;
            }

            Vector3f color = null;
            int satelliteCount = 0;
            boolean divine = false;

            if (armor.getMaterial() == MysteryArmorMaterial.DARK_SOURCE) {
                color = PURPLE;
                satelliteCount = 1;
            } else if (armor.getMaterial() == MysteryArmorMaterial.ORIGIN_DRAGON) {
                color = ORANGE;
                satelliteCount = 2;
            } else if (armor.getMaterial() == MysteryArmorMaterial.DIVINE_BLESSING) {
                divine = true;
            } else {
                continue;
            }

            // 向其他玩家定向发送粒子（穿戴者自己由客户端渲染，不重复发送）
            for (ServerPlayer other : players) {
                if (other == wearer) {
                    continue;
                }
                sendParticlesTo(other, wearer, color, satelliteCount, divine);
            }
        }
    }

    private static void sendParticlesTo(ServerPlayer target, ServerPlayer wearer, Vector3f color,
                                        int satelliteCount, boolean divine) {
        ServerLevel level = target.serverLevel();
        if (level == null) {
            return;
        }

        double y = wearer.getY() + 1.2;
        long gameTime = level.getGameTime();

        if (divine) {
            // 神威天佑：内层 2 卫星环绕（白+黑），外层 5 个静止卫星（绿黄蓝红橙）
            spawnDivineBlessing(target, level, wearer, y, gameTime);
        } else {
            spawnSatellite(target, level, color, wearer, y, gameTime, satelliteCount, 1.2, 1.0f);
        }
    }

    private static void spawnSatellite(ServerPlayer target, ServerLevel level, Vector3f color,
                                       ServerPlayer wearer, double y, long gameTime,
                                       int count, double radius, float scale) {
        DustParticleOptions dust = new DustParticleOptions(color, scale);
        double baseAngle = (gameTime * 0.15) % (2 * Math.PI);
        for (int i = 0; i < count; i++) {
            double angle = baseAngle + (i * 2 * Math.PI / count);
            double x = wearer.getX() + radius * Math.cos(angle);
            double z = wearer.getZ() + radius * Math.sin(angle);
            level.sendParticles(target, dust, false, x, y, z, 1, 0, 0, 0, 0);
        }
    }

    /**
     * 神威天佑特效（服务端广播）：内层 2 个卫星环绕（白+黑），外层 5 个静止卫星（绿黄蓝红橙）
     */
    private static void spawnDivineBlessing(ServerPlayer target, ServerLevel level, ServerPlayer wearer, double y, long gameTime) {
        // 内层：白色 + 黑色卫星环绕
        double innerRadius = 1.0;
        double baseAngle = (gameTime * 0.15) % (2 * Math.PI);
        DustParticleOptions white = new DustParticleOptions(WHITE, 1.0f);
        DustParticleOptions black = new DustParticleOptions(BLACK, 1.0f);
        for (int i = 0; i < 2; i++) {
            double angle = baseAngle + (i * Math.PI); // 相隔 180 度
            double x = wearer.getX() + innerRadius * Math.cos(angle);
            double z = wearer.getZ() + innerRadius * Math.sin(angle);
            level.sendParticles(target, i == 0 ? white : black, false, x, y, z, 1, 0, 0, 0, 0);
        }

        // 外层：5 个静止卫星（绿、黄、蓝、红、橙）
        double outerRadius = 1.7;
        Vector3f[] outerColors = {GREEN, YELLOW, BLUE, RED, ORANGE};
        for (int i = 0; i < 5; i++) {
            double angle = i * 2 * Math.PI / 5; // 固定角度，不旋转
            double x = wearer.getX() + outerRadius * Math.cos(angle);
            double z = wearer.getZ() + outerRadius * Math.sin(angle);
            DustParticleOptions dust = new DustParticleOptions(outerColors[i], 1.0f);
            level.sendParticles(target, dust, false, x, y, z, 1, 0, 0, 0, 0);
        }
    }
}
