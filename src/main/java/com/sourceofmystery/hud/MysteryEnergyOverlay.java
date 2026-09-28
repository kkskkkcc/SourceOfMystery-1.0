package com.sourceofmystery.hud;

import com.sourceofmystery.SourceOfMystery;
import com.sourceofmystery.client.ClientEnergyCache;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = SourceOfMystery.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class MysteryEnergyOverlay {

    private static final int ENERGY_DISPLAY_THRESHOLD = 1; // 显示阈值

    @SubscribeEvent
    public static void onRenderGuiOverlay(RenderGuiOverlayEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();

        if (minecraft.player == null || minecraft.level == null) {
            return;
        }

        // 检查是否已解锁神秘之能GUI（从客户端缓存读取）
        if (!ClientEnergyCache.guiUnlocked) {
            return;
        }

        // 检查是否有神秘之能
        long energy = ClientEnergyCache.energy;
        if (energy < ENERGY_DISPLAY_THRESHOLD) {
            return;
        }

        GuiGraphics guiGraphics = event.getGuiGraphics();
        Font fontRenderer = minecraft.font;

        long max = ClientEnergyCache.max;
        String energyText = "§6⚡ 神秘之能: §a" + energy + " §7/ §e" + max;

        int x = 10; // 左下角，左边距10像素
        int screenHeight = event.getWindow().getGuiScaledHeight();
        int y = screenHeight - fontRenderer.lineHeight - 10; // 底部边距10像素

        // 纯文字，不绘制背景
        guiGraphics.drawString(fontRenderer, energyText, x, y, 0xFFFFFF);
    }
}
