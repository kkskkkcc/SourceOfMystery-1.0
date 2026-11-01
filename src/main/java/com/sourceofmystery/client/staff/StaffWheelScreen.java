package com.sourceofmystery.client.staff;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.sourceofmystery.network.ModNetwork;
import com.sourceofmystery.network.StaffSelectionPacket;
import com.sourceofmystery.staff.StaffSpell;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import org.lwjgl.glfw.GLFW;

public final class StaffWheelScreen extends Screen {
    private final InteractionHand hand;
    private int hovered = -1;
    public StaffWheelScreen(InteractionHand hand) {
        super(Component.translatable("screen.sourceofmystery.staff_spells")); this.hand = hand;
    }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partial) {
        double dx = mouseX - width / 2d, dy = mouseY - height / 2d;
        double distance = Math.hypot(dx, dy);
        double angle = (Math.atan2(dy, dx) + Math.PI / 2 + Math.PI * 2) % (Math.PI * 2);
        StaffSpell[] spells = StaffSpell.values();
        hovered = distance >= 25 && distance <= 118 ? (int) (angle / (Math.PI * 2 / spells.length)) : -1;
        graphics.flush();
        RenderSystem.enableBlend(); RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();RenderSystem.disableDepthTest();
        RenderSystem.setShaderColor(1,1,1,1);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        var buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        for (int sector = 0; sector < spells.length; sector++) {
            double start = sector * Math.PI * 2 / spells.length - Math.PI / 2 + .025;
            double end = (sector + 1) * Math.PI * 2 / spells.length - Math.PI / 2 - .025;
            int alpha = hovered == sector ? 225 : 165;
            for (int step = 0; step < 24; step++) {
                double a = start + (end - start) * step / 24, b = start + (end - start) * (step + 1) / 24;
                vertex(buffer, 30, a, alpha); vertex(buffer, 112, a, alpha);
                vertex(buffer, 112, b, alpha); vertex(buffer, 30, b, alpha);
            }
        }
        BufferUploader.drawWithShader(buffer.end());
        RenderSystem.disableBlend();
        RenderSystem.enableCull();RenderSystem.enableDepthTest();
        for (int index = 0; index < spells.length; index++) {
            double center = (index + .5) * Math.PI * 2 / spells.length - Math.PI / 2;
            int x = width / 2 + (int) (Math.cos(center) * 74), y = height / 2 + (int) (Math.sin(center) * 74);
            graphics.drawCenteredString(font, spells[index].title(), x, y - 8, 0xff354049);
            graphics.drawCenteredString(font, Component.translatable("screen.sourceofmystery.staff_cost", spells[index].cost()), x, y + 5, 0xff59636c);
        }
        graphics.drawCenteredString(font, title, width / 2, height / 2 - 135, 0xfff0f1f3);
        graphics.drawCenteredString(font, Component.translatable("screen.sourceofmystery.staff_hint"), width / 2, height / 2 + 125, 0xfff0f1f3);
    }
    private void vertex(BufferBuilder buffer, double radius, double angle, int alpha) {
        buffer.vertex(width / 2d + Math.cos(angle) * radius, height / 2d + Math.sin(angle) * radius, 0)
                .color(224, 232, 236, alpha).endVertex();
    }
    private void choose() {
        if (hovered >= 0 && minecraft.player != null) {
            StaffSpell.values()[hovered].select(minecraft.player.getItemInHand(hand));
            ModNetwork.CHANNEL.sendToServer(new StaffSelectionPacket(hand, hovered));
        }
        onClose();
    }
    @Override public boolean mouseClicked(double x, double y, int button) {
        if (button == 0) { choose(); return true; }
        return super.mouseClicked(x, y, button);
    }
    @Override public boolean keyReleased(int key, int scan, int modifiers) {
        if (StaffClientEvents.WHEEL.matches(key, scan)) { if(hovered>=0)choose(); return true; }
        return super.keyReleased(key, scan, modifiers);
    }
    @Override public boolean keyPressed(int key,int scan,int modifiers) {
        if(StaffClientEvents.WHEEL.matches(key,scan)){choose();return true;}
        return super.keyPressed(key,scan,modifiers);
    }
}
