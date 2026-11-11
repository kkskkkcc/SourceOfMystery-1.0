package com.sourceofmystery.client.staff;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.sourceofmystery.network.ModNetwork;
import com.sourceofmystery.network.StaffSelectionPacket;
import com.sourceofmystery.staff.*;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import org.lwjgl.glfw.GLFW;

public final class StaffWheelScreen extends Screen {
    private final InteractionHand hand;
    private int hovered = -1;
    private final List<StaffWheelLayout.Segment> segments = StaffWheelLayout.segments(StaffSpell.values().length);
    private double extent() { return segments.get(segments.size()-1).outer(); }
    private double scale() { return Math.min(1, Math.max(.25, Math.min((width-24)/(extent()*2), (height-95)/(extent()*2)))); }
    private double cy() { return height/2d-9; }
    private int hit(double x,double y) { return StaffWheelLayout.hit(segments,(x-width/2d)/scale(),(y-cy())/scale()); }
    public StaffWheelScreen(InteractionHand hand) {
        super(Component.translatable("screen.sourceofmystery.staff_spells")); this.hand = hand;
    }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partial) {
        StaffSpell[] spells = StaffSpell.values();
        hovered = hit(mouseX,mouseY);
        graphics.flush();
        RenderSystem.enableBlend(); RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();RenderSystem.disableDepthTest();
        RenderSystem.setShaderColor(1,1,1,1);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        var buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        for (var segment : segments) {
            double start=segment.start(),end=segment.end();
            int alpha = hovered == segment.index() ? 225 : 165;
            for (int step = 0; step < 24; step++) {
                double a = start + (end - start) * step / 24, b = start + (end - start) * (step + 1) / 24;
                vertex(buffer, segment.inner(), a, alpha); vertex(buffer, segment.outer(), a, alpha);
                vertex(buffer, segment.outer(), b, alpha); vertex(buffer, segment.inner(), b, alpha);
            }
        }
        BufferUploader.drawWithShader(buffer.end());
        RenderSystem.disableBlend();
        RenderSystem.enableCull();RenderSystem.enableDepthTest();
        for (var segment : segments) {
            int index=segment.index();
            int x=(int)(width/2d+Math.cos(segment.angle())*segment.radius()*scale());
            int y=(int)(cy()+Math.sin(segment.angle())*segment.radius()*scale());
            graphics.pose().pushPose();graphics.pose().translate(x,y,0);
            double available=2*segment.radius()*Math.sin((segment.end()-segment.start())/2)-8;
            float size=(float)Math.min(Math.min(1,scale()+.15),available*scale()/Math.max(1,font.width(spells[index].title())));
            graphics.pose().scale(size,size,1);
            graphics.drawCenteredString(font, spells[index].title(), 0, -8, 0xff354049);
            long cost=minecraft.player==null?spells[index].cost():spells[index].cost(minecraft.player.getItemInHand(hand));
            graphics.drawCenteredString(font, Component.translatable("screen.sourceofmystery.staff_cost_compact", cost), 0, 5, 0xff59636c);
            graphics.pose().popPose();
        }
        int top=(int)(cy()-extent()*scale()-18),bottom=(int)(cy()+extent()*scale()+8);
        graphics.drawCenteredString(font,title,width/2,top,0xfff0f1f3);
        graphics.drawCenteredString(font,Component.translatable("screen.sourceofmystery.staff_hint"),width/2,bottom,0xfff0f1f3);
        if(minecraft.player!=null) {
            var stack=minecraft.player.getItemInHand(hand);
            if((hovered>=0&&spells[hovered]==StaffSpell.TIME_STOP)||StaffSpell.selected(stack)==StaffSpell.TIME_STOP){
                graphics.drawCenteredString(font,TimeMagicType.selected(stack).title(),width/2,bottom+13,0xffc6c7ff);
                graphics.drawCenteredString(font,Component.translatable("tooltip.sourceofmystery.staff.time_hint"),width/2,bottom+26,0xffd7e1e5);
            }
            if((hovered>=0&&spells[hovered]==StaffSpell.WEATHER_CONTROL)||StaffSpell.selected(stack)==StaffSpell.WEATHER_CONTROL){
                graphics.drawCenteredString(font,WeatherType.selected(stack).title(),width/2,bottom+13,0xffa9dbe9);
                graphics.drawCenteredString(font,Component.translatable("tooltip.sourceofmystery.staff.weather_hint"),width/2,bottom+26,0xffd7e1e5);
            }
            if((hovered>=0&&spells[hovered]==StaffSpell.DOMAIN_EXPANSION)||StaffSpell.selected(stack)==StaffSpell.DOMAIN_EXPANSION){
                graphics.drawCenteredString(font,DomainType.selected(stack).title(),width/2,bottom+13,0xffefb8ec);
                graphics.drawCenteredString(font,Component.translatable("tooltip.sourceofmystery.staff.domain_hint"),width/2,bottom+26,0xffd7e1e5);
            }
            if((hovered>=0&&spells[hovered]==StaffSpell.ENVIRONMENT_SHIFT)||StaffSpell.selected(stack)==StaffSpell.ENVIRONMENT_SHIFT) {
                graphics.drawCenteredString(font,Component.translatable("tooltip.sourceofmystery.staff.environment",StaffEnvironment.title(StaffEnvironment.selected(stack))),width/2,bottom+13,0xff9cf2cb);
                graphics.drawCenteredString(font,Component.translatable("tooltip.sourceofmystery.staff.environment_hint"),width/2,bottom+26,0xffd7e1e5);
            }
        }
    }
    private void vertex(BufferBuilder buffer, double radius, double angle, int alpha) {
        buffer.vertex(width / 2d + Math.cos(angle) * radius*scale(), cy() + Math.sin(angle) * radius*scale(), 0)
                .color(224, 232, 236, alpha).endVertex();
    }

    private void choose() {
        if (hovered >= 0 && minecraft.player != null && !minecraft.player.isUsingItem() && minecraft.player.getItemInHand(hand).getItem() instanceof com.sourceofmystery.item.DimensionBreakerStaffItem) {
            StaffSpell.values()[hovered].select(minecraft.player.getItemInHand(hand));
            ModNetwork.CHANNEL.sendToServer(new StaffSelectionPacket(hand, hovered));
        }
        onClose();
    }
    @Override public boolean mouseClicked(double x, double y, int button) {
        if (button == 0) { hovered=hit(x,y); choose(); return true; }
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
