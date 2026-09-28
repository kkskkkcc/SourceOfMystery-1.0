package com.sourceofmystery.client;

import com.sourceofmystery.SourceOfMystery;
import com.sourceofmystery.item.MysteryArmorMaterial;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3f;

/**
 * 护甲环绕粒子特效（纯客户端）：
 * - 第一人称视角不显示（避免挡视野）
 * - 暗源之甲：紫色，1 个卫星环绕
 * - 始源龙甲：橙色，2 个卫星环绕（相隔 180 度）
 * - 神威天佑：内层 2 卫星环绕（白+黑），外层 5 个静止卫星（绿黄蓝红橙）
 * 卸甲后自动停止（不穿戴就不生成粒子）。
 */
@Mod.EventBusSubscriber(modid = SourceOfMystery.MOD_ID, value = Dist.CLIENT)
public class ArmorParticleEffect {

    // 粒子颜色（RGB）
    private static final Vector3f PURPLE = new Vector3f(0.62f, 0.25f, 0.85f); // 紫色
    private static final Vector3f ORANGE = new Vector3f(1.0f, 0.55f, 0.1f);   // 橙色
    private static final Vector3f WHITE = new Vector3f(1.0f, 1.0f, 1.0f);     // 白色
    private static final Vector3f BLACK = new Vector3f(0.05f, 0.05f, 0.05f);  // 黑色
    private static final Vector3f GREEN = new Vector3f(0.2f, 0.9f, 0.2f);     // 绿色
    private static final Vector3f YELLOW = new Vector3f(1.0f, 0.85f, 0.1f);   // 黄色
    private static final Vector3f BLUE = new Vector3f(0.2f, 0.4f, 1.0f);      // 蓝色
    private static final Vector3f RED = new Vector3f(1.0f, 0.1f, 0.1f);       // 红色

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        ClientLevel level = mc.level;
        if (player == null || level == null) {
            return;
        }

        // 第一人称视角不显示粒子（避免挡视野）
        if (mc.options.getCameraType().isFirstPerson()) {
            return;
        }

        // 检测穿戴的护甲
        ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST);
        if (!(chest.getItem() instanceof ArmorItem armor)) {
            return;
        }

        if (armor.getMaterial() == MysteryArmorMaterial.DARK_SOURCE) {
            spawnSatellite(player, level, PURPLE, 1);
        } else if (armor.getMaterial() == MysteryArmorMaterial.ORIGIN_DRAGON) {
            spawnSatellite(player, level, ORANGE, 2);
        } else if (armor.getMaterial() == MysteryArmorMaterial.DIVINE_BLESSING) {
            // 神威天佑：内层 2 卫星环绕（白+黑），外层 5 个静止卫星（绿黄蓝红橙）
            spawnDivineBlessingEffect(player, level);
        }
    }

    /**
     * 卫星环绕：count 个粒子在玩家周围圆周上环绕
     */
    private static void spawnSatellite(Player player, ClientLevel level, Vector3f color, int count) {
        double radius = 1.2; // 环绕半径
        double y = player.getY() + 1.2; // 身体中部高度
        // 环绕角度随时间递增（每 tick 0.15 弧度，约 2 秒转一圈）
        double baseAngle = (level.getGameTime() * 0.15) % (2 * Math.PI);

        DustParticleOptions dust = new DustParticleOptions(color, 1.0f);

        for (int i = 0; i < count; i++) {
            double angle = baseAngle + (i * 2 * Math.PI / count); // 多个卫星均匀分布
            double x = player.getX() + radius * Math.cos(angle);
            double z = player.getZ() + radius * Math.sin(angle);
            level.addParticle(dust, x, y, z, 0, 0, 0);
        }
    }

    /**
     * 神威天佑特效：内层 2 个卫星环绕（白+黑，相隔 180 度旋转），外层 5 个静止卫星（绿黄蓝红橙，等分 72 度不旋转）
     */
    private static void spawnDivineBlessingEffect(Player player, ClientLevel level) {
        double y = player.getY() + 1.2;
        long gameTime = level.getGameTime();

        // 内层：白色 + 黑色卫星环绕
        double innerRadius = 1.0;
        double baseAngle = (gameTime * 0.15) % (2 * Math.PI);
        DustParticleOptions white = new DustParticleOptions(WHITE, 1.0f);
        DustParticleOptions black = new DustParticleOptions(BLACK, 1.0f);
        for (int i = 0; i < 2; i++) {
            double angle = baseAngle + (i * Math.PI); // 相隔 180 度
            double x = player.getX() + innerRadius * Math.cos(angle);
            double z = player.getZ() + innerRadius * Math.sin(angle);
            level.addParticle(i == 0 ? white : black, x, y, z, 0, 0, 0);
        }

        // 外层：5 个静止卫星（绿、黄、蓝、红、橙）
        double outerRadius = 1.7;
        Vector3f[] outerColors = {GREEN, YELLOW, BLUE, RED, ORANGE};
        for (int i = 0; i < 5; i++) {
            double angle = i * 2 * Math.PI / 5; // 固定角度，不旋转
            double x = player.getX() + outerRadius * Math.cos(angle);
            double z = player.getZ() + outerRadius * Math.sin(angle);
            DustParticleOptions dust = new DustParticleOptions(outerColors[i], 1.0f);
            level.addParticle(dust, x, y, z, 0, 0, 0);
        }
    }
}
