package com.sourceofmystery.hud;

import com.sourceofmystery.client.ClientEnergyCache;
import com.sourceofmystery.config.MysteryConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

/**
 * 神秘之能 HUD："当前 / 上限"。
 * 位置、边距和低能量提醒阈值见客户端配置 sourceofmystery-client.toml；
 * 能量低于阈值时当前值显示为红色。
 * 作为独立 overlay 注册（见 ClientModEvents），每帧只绘制一次，并会随 F1 隐藏。
 */
public class MysteryEnergyOverlay implements IGuiOverlay {

    public static final String ID = "mystery_energy";

    @Override
    public void render(ForgeGui gui, GuiGraphics guiGraphics, float partialTick, int screenWidth, int screenHeight) {
        Minecraft minecraft = gui.getMinecraft();
        if (minecraft.player == null || minecraft.options.hideGui || !ClientEnergyCache.guiUnlocked) {
            return;
        }

        boolean low = ClientEnergyCache.energy < MysteryConfig.HUD_LOW_ENERGY_THRESHOLD.get();
        Component text = Component.translatable("hud.sourceofmystery.energy",
                        Component.literal(String.valueOf(ClientEnergyCache.energy))
                                .withStyle(low ? ChatFormatting.RED : ChatFormatting.GREEN),
                        Component.literal(String.valueOf(ClientEnergyCache.max)).withStyle(ChatFormatting.YELLOW))
                .withStyle(ChatFormatting.GOLD);

        int width = minecraft.font.width(text);
        int height = minecraft.font.lineHeight;
        int offsetX = MysteryConfig.HUD_OFFSET_X.get();
        int offsetY = MysteryConfig.HUD_OFFSET_Y.get();
        MysteryConfig.HudAnchor anchor = MysteryConfig.HUD_ANCHOR.get();

        int x = switch (anchor) {
            case TOP_LEFT, BOTTOM_LEFT -> offsetX;
            case TOP_RIGHT, BOTTOM_RIGHT -> screenWidth - width - offsetX;
        };
        int y = switch (anchor) {
            case TOP_LEFT, TOP_RIGHT -> offsetY;
            case BOTTOM_LEFT, BOTTOM_RIGHT -> screenHeight - height - offsetY;
        };
        guiGraphics.drawString(minecraft.font, text, x, y, 0xFFFFFF);
    }
}
