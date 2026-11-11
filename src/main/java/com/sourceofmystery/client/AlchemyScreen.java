package com.sourceofmystery.client;

import com.sourceofmystery.alchemy.*;
import com.sourceofmystery.network.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import java.util.*;

/** An obsidian-and-gold materials ledger: every daily quote stays visible. */
public final class AlchemyScreen extends AbstractContainerScreen<AlchemyMenu>{
    private float fit=1;
    public AlchemyScreen(AlchemyMenu menu,Inventory inv,Component title){super(menu,inv,title);imageWidth=420;imageHeight=350;}
    private static net.minecraft.network.chat.MutableComponent tr(String key,Object... args){return Component.translatable("alchemy.sourceofmystery."+key,args);}
    @Override protected void init(){fit=Math.min(1,Math.min((width-12)/420f,(height-12)/350f));width=Math.round(width/fit);height=Math.round(height/fit);super.init();}
    private static void frame(GuiGraphics g,int x,int y,int w,int h,int color){g.fill(x,y,x+w,y+1,color);g.fill(x,y+h-1,x+w,y+h,color);g.fill(x,y,x+1,y+h,color);g.fill(x+w-1,y,x+w,y+h,color);}
    private int cell(int mx,int my){int x=mx-leftPos-18,y=my-topPos-48;if(x<0||y<0||x>=385||y>=188||x%77>=73||y%47>=44)return -1;return x/77+y/47*5;}
    private static Component name(ItemStack stack){try{return stack.getHoverName();}catch(RuntimeException|LinkageError e){return Component.literal(String.valueOf(ForgeRegistries.ITEMS.getKey(stack.getItem())));}}
    private void text(GuiGraphics g,Component text,int x,int y,int color){g.drawString(font,text,x,y,color,false);}
    @Override protected void renderBg(GuiGraphics g,float partial,int mx,int my){
        int x=leftPos,y=topPos;g.fill(x-3,y-3,x+423,y+353,0xe009090e);g.fill(x,y,x+420,y+350,0xff17131f);frame(g,x,y,420,350,0xffa78447);
        g.fill(x+9,y+9,x+411,y+37,0xff25212c);text(g,title,x+18,y+18,0xffecdfbd);
        String day=tr("day",Long.toString(menu.day()+1)).getString();g.drawString(font,day,x+402-font.width(day),y+18,0xffc5a468,false);
        int hover=cell(mx,my);
        for(int i=0;i<20;i++){
            int xx=x+18+i%5*77,yy=y+48+i/5*47;boolean ready=menu.affordable(i);var output=menu.output(i);
            g.fill(xx,yy,xx+73,yy+44,i==hover?0xff332b37:0xff211d28);frame(g,xx,yy,73,44,i==hover?0xffead3a1:ready?0xff698978:0xff5b4c42);
            if(output.isEmpty())continue;
            g.renderItem(output,xx+5,yy+5);g.renderItemDecorations(font,output,xx+5,yy+5);
            g.drawString(font,font.plainSubstrByWidth(name(output).getString(),44),xx+24,yy+8,0xffe4d9c2,false);
            var costs=menu.materials(i);
            for(int j=0;j<Math.min(2,costs.size());j++){
                var cost=costs.get(j);int cx=xx+5+j*28;g.renderItem(cost,cx,yy+25);
                g.drawString(font,Integer.toString(cost.getCount()),cx+16,yy+30,AlchemyMenu.available(minecraft.player.getInventory(),cost)>=cost.getCount()?0xff9ed2b4:0xffcf9290,false);
            }
            if(costs.size()>2)g.drawString(font,"+"+(costs.size()-2),xx+59,yy+30,0xffc3af89,false);
        }
        g.fill(x+18,y+243,x+402,y+244,0xff68563a);text(g,playerInventoryTitle,x+18,y+247,0xffc7baa6);
        for(var slot:menu.slots)if(slot.index>=AlchemyMenu.DISPLAY){g.fill(x+slot.x-1,y+slot.y-1,x+slot.x+17,y+slot.y+17,0xff0e0d14);frame(g,x+slot.x-1,y+slot.y-1,18,18,0xff4e4449);}
        text(g,tr("daily_hint"),x+201,y+263,0xffd1b67e);text(g,tr("hover_hint"),x+201,y+279,0xffb4aca6);text(g,tr("click_hint"),x+201,y+294,0xffb4aca6);
        if(menu.feedback()!=0)text(g,tr("feedback"+menu.feedback()),x+201,y+316,menu.feedback()==4?0xff9ed2b4:0xffdfaaaa);
        else text(g,tr("world_sheet"),x+201,y+316,0xff867d84);
    }
    @Override protected void renderLabels(GuiGraphics g,int mx,int my){}
    @Override public void render(GuiGraphics g,int mx,int my,float partial){
        int actualX=mx,actualY=my;mx=(int)(mx/fit);my=(int)(my/fit);
        g.pose().pushPose();g.pose().scale(fit,fit,1);renderBackground(g);super.render(g,mx,my,partial);g.pose().popPose();
        int index=cell(mx,my);
        if(index>=0&&!menu.output(index).isEmpty()){
            var lines=new ArrayList<Component>();lines.add(name(menu.output(index)));lines.add(tr("requires").withStyle(net.minecraft.ChatFormatting.GOLD));
            for(var cost:menu.materials(index)){
                int have=AlchemyMenu.available(minecraft.player.getInventory(),cost);
                lines.add(tr("material",name(cost),cost.getCount(),have).withStyle(have>=cost.getCount()?net.minecraft.ChatFormatting.GREEN:net.minecraft.ChatFormatting.RED));
            }
            lines.add(tr(menu.affordable(index)?"click_hint":"missing_hint").withStyle(net.minecraft.ChatFormatting.GRAY));g.renderComponentTooltip(font,lines,actualX,actualY);
        }else renderTooltip(g,actualX,actualY);
    }
    @Override public boolean mouseClicked(double x,double y,int button){
        int index=cell((int)(x/fit),(int)(y/fit));
        if(button==0&&index>=0){ModNetwork.CHANNEL.sendToServer(new AlchemyTradePacket(menu.containerId,index,menu.revision()));return true;}
        return super.mouseClicked(x/fit,y/fit,button);
    }
    @Override public boolean mouseReleased(double x,double y,int button){return super.mouseReleased(x/fit,y/fit,button);}
    @Override public boolean mouseDragged(double x,double y,int button,double dx,double dy){return super.mouseDragged(x/fit,y/fit,button,dx/fit,dy/fit);}
    @Override public void mouseMoved(double x,double y){super.mouseMoved(x/fit,y/fit);}
}
