package com.sourceofmystery.hud;

import com.sourceofmystery.client.ClientEnergyCache;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

/**
 * 神秘之能 HUD：左下角显示 "当前 / 上限"。
 * 作为独立 overlay 注册（见 ClientModEvents），每帧只绘制一次，并会随 F1 隐藏。
 */
public class MysteryEnergyOverlay implements IGuiOverlay {

    public static final String ID = "mystery_energy";
    private static final int MARGIN = 10;

    @Override
    public void render(ForgeGui gui, GuiGraphics guiGraphics, float partialTick, int screenWidth, int screenHeight) {
        Minecraft minecraft = gui.getMinecraft();
        if (minecraft.player == null || minecraft.options.hideGui || !ClientEnergyCache.guiUnlocked) {
            return;
        }

        Component text = Component.translatable("hud.sourceofmystery.energy",
                        Component.literal(String.valueOf(ClientEnergyCache.energy)).withStyle(ChatFormatting.GREEN),
                        Component.literal(String.valueOf(ClientEnergyCache.max)).withStyle(ChatFormatting.YELLOW))
                .withStyle(ChatFormatting.GOLD);
        int y = screenHeight - minecraft.font.lineHeight - MARGIN;
        guiGraphics.drawString(minecraft.font, text, MARGIN, y, 0xFFFFFF);
    }
}
