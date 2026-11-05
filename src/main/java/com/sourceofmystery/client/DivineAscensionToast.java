package com.sourceofmystery.client;

import com.sourceofmystery.item.ModItems;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastComponent;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

/** The achievement has a real persisted advancement and an animated prismatic toast. */
final class DivineAscensionToast implements Toast {
    @Override public Visibility render(GuiGraphics g,ToastComponent toasts,long time){
        g.fill(0,0,160,32,0xf00c101b);
        for(int i=0;i<158;i++)g.fill(1+i,0,2+i,1,0xff000000|Mth.hsvToRgb((i/160f+time/7000f)%1,.55f,1));
        g.renderItem(new ItemStack(ModItems.DIVINE_BLESSING_CHESTPLATE.get()),8,8);
        var font=net.minecraft.client.Minecraft.getInstance().font;
        g.drawString(font,"挑战达成",31,6,0xffc8b684,false);
        rainbow(g,"登神",31,18,time/1000f);
        return time>=6000?Visibility.HIDE:Visibility.SHOW;
    }
    static void rainbow(GuiGraphics g,String text,int x,int y,float seconds){
        var font=net.minecraft.client.Minecraft.getInstance().font;
        for(int i=0;i<text.length();i++){
            String c=text.substring(i,i+1);int color=0xff000000|Mth.hsvToRgb((seconds*.16f+i*.18f)%1,.46f,1);
            g.drawString(font,c,x,y,color,true);x+=font.width(c);
        }
    }
}
