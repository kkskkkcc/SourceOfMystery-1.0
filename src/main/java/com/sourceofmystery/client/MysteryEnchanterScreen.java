package com.sourceofmystery.client;

import com.sourceofmystery.enchanter.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraftforge.registries.ForgeRegistries;
import java.util.*;

public final class MysteryEnchanterScreen extends AbstractContainerScreen<EnchanterMenu> {
    private EditBox search;
    private List<Enchantment> filtered;
    private int scroll;
    private float fit=1;
    private Button apply,remove,clear,less,more,max;
    public MysteryEnchanterScreen(EnchanterMenu menu,Inventory inv,Component title){super(menu,inv,title);imageWidth=360;imageHeight=318;filtered=menu.enchantments;}
    public static Component tr(String key,Object... args){return Component.translatable("enchanter.sourceofmystery."+key,args);}
    private void send(int id){if(minecraft.gameMode!=null)minecraft.gameMode.handleInventoryButtonClick(menu.containerId,id);}
    private Button button(String key,int x,int y,int w,int h,int action){return addRenderableWidget(Button.builder(tr(key),b->send(action)).bounds(leftPos+x,topPos+y,w,h).build());}
    @Override protected void init(){
        fit=Math.min(1,Math.min((width-12)/360f,(height-12)/318f));width=Math.round(width/fit);height=Math.round(height/fit);super.init();
        String previous=search==null?"":search.getValue();
        search=addRenderableWidget(new EditBox(font,leftPos+148,topPos+31,198,18,tr("search")));search.setMaxLength(100);search.setHint(tr("search"));
        search.setResponder(s->{String q=s.toLowerCase(Locale.ROOT).strip();filtered=menu.enchantments.stream().filter(e->e.getFullname(1).getString().toLowerCase(Locale.ROOT).contains(q)||ForgeRegistries.ENCHANTMENTS.getKey(e).toString().contains(q)).toList();scroll=0;});search.setValue(previous);
        less=button("less",14,144,22,18,EnchanterMenu.LESS);more=button("more",83,144,22,18,EnchanterMenu.MORE);max=button("max",108,144,27,18,EnchanterMenu.MAXIMUM);
        apply=button("apply",14,169,121,18,EnchanterMenu.APPLY);remove=button("remove",14,190,121,18,EnchanterMenu.REMOVE);clear=button("clear",148,202,198,16,EnchanterMenu.CLEAR);
    }
    private static String number(long value){return String.format(Locale.ROOT,"%,d",value);}
    private static void frame(GuiGraphics g,int x,int y,int w,int h,int c){g.fill(x,y,x+w,y+1,c);g.fill(x,y+h-1,x+w,y+h,c);g.fill(x,y,x+1,y+h,c);g.fill(x+w-1,y,x+w,y+h,c);}
    private void text(GuiGraphics g,Component c,int x,int y,int color){g.drawString(font,c,x,y,color,false);}
    private String trim(String text,int w){return font.plainSubstrByWidth(text,w);}
    @Override public void render(GuiGraphics g,int mx,int my,float partial){
        int actualX=mx,actualY=my;mx=(int)(mx/fit);my=(int)(my/fit);
        long energy=ClientEnergyCache.energy;boolean selected=menu.selected()!=null;
        apply.active=menu.status()==EnchanterRules.READY&&menu.enchantCost()>0&&energy>=menu.enchantCost();
        remove.active=menu.removeCost()>0&&energy>=menu.removeCost();clear.active=menu.clearCost()>0&&energy>=menu.clearCost();
        less.active=selected&&menu.targetLevel()>EnchanterRules.minimum(menu.selected());more.active=selected&&menu.targetLevel()<EnchanterRules.maximum(menu.selected());max.active=more.active;
        apply.setMessage(tr("apply_cost",number(menu.enchantCost())));remove.setMessage(tr("remove_cost",number(menu.removeCost())));clear.setMessage(tr("clear_cost",number(menu.clearCost())));
        g.pose().pushPose();g.pose().scale(fit,fit,1);renderBackground(g);super.render(g,mx,my,partial);g.pose().popPose();
        renderTooltip(g,actualX,actualY);
        int row=(my-topPos-54)/20;
        if(mx>=leftPos+148&&mx<leftPos+343&&my>=topPos+54&&my<topPos+194&&row+scroll<filtered.size()) {
            var e=filtered.get(row+scroll);int target=Math.min(EnchanterRules.maximum(e),Math.max(EnchanterRules.minimum(e),menu.targetLevel()));
            g.renderComponentTooltip(font,List.of(e.getFullname(target),Component.literal(ForgeRegistries.ENCHANTMENTS.getKey(e).toString()),tr("status"+EnchanterRules.canApply(menu.getSlot(0).getItem(),e,target))),actualX,actualY);
        }
    }
    @Override protected void renderBg(GuiGraphics g,float p,int mx,int my){
        int x=leftPos,y=topPos;g.fill(x-3,y-3,x+363,y+321,0xd0070b13);g.fillGradient(x,y,x+360,y+318,0xff152638,0xff0d121e);frame(g,x,y,360,318,0xff4f9eb3);
        g.drawCenteredString(font,title,x+180,y+12,0xffd2f4ff);text(g,tr("input"),x+18,y+49,0xffa8bbc9);
        g.fill(x+51,y+65,x+73,y+87,0xff080f1b);frame(g,x+51,y+65,22,22,0xff73c4cf);
        text(g,tr("energy"),x+18,y+98,0xff9fb4c6);text(g,Component.literal(number(ClientEnergyCache.energy)),x+18,y+110,0xff7ed4e3);
        var e=menu.selected();text(g,Component.literal(e==null?"—":trim(e.getFullname(menu.targetLevel()).getString(),118)),x+16,y+128,0xffe4d3a8);
        g.drawCenteredString(font,Integer.toString(menu.targetLevel()),x+59,y+149,0xffedf4ff);
        g.fill(x+148,y+53,x+346,y+196,0xff09121e);frame(g,x+148,y+53,198,143,0xff38556b);
        for(int row=0;row<7&&scroll+row<filtered.size();row++){
            var entry=filtered.get(scroll+row);int yy=y+54+row*20;
            if(entry==e)g.fill(x+149,yy,x+343,yy+20,0xff294f68);else if((row&1)==0)g.fill(x+149,yy,x+343,yy+20,0xff111e2c);
            int level=EnchanterRules.level(menu.getSlot(0).getItem(),entry);
            String name=entry.getFullname(Math.max(1,level)).getString();
            g.drawString(font,trim(name,180),x+154,yy+2,entry.isCurse()?0xffed8f9d:level>0?0xff87d8ad:0xffd4e1ee,false);
            g.drawString(font,trim(ForgeRegistries.ENCHANTMENTS.getKey(entry).toString(),179),x+154,yy+12,0xff748da3,false);
        }
        if(filtered.size()>7){int h=Math.max(8,140*7/filtered.size()),top=y+54+(140-h)*scroll/Math.max(1,filtered.size()-7);g.fill(x+343,top,x+345,top+h,0xff8cd2dd);}
        if(filtered.isEmpty())g.drawCenteredString(font,tr("no_results"),x+247,y+114,0xff889bad);
        text(g,playerInventoryTitle,x+18,y+209,0xffa8bbc9);
        for(var slot:menu.slots)if(slot.index!=0){g.fill(x+slot.x-1,y+slot.y-1,x+slot.x+17,y+slot.y+17,0xff080e18);frame(g,x+slot.x-1,y+slot.y-1,18,18,0xff344c60);}
        text(g,tr("current",menu.currentLevel()),x+198,y+231,0xffa6b9c9);
        text(g,tr("status"+menu.status()),x+198,y+247,menu.status()==0?0xff81cbb2:0xffc4b4a9);
        if(menu.feedback()!=0)text(g,tr("feedback"+menu.feedback()),x+198,y+265,menu.feedback()<=3?0xff8bd5b7:0xffe5a098);
        text(g,tr("hint"),x+198,y+283,0xff738b9f);
        g.drawCenteredString(font,tr("count",filtered.size(),menu.enchantments.size()),x+180,y+305,0xff9bb3c6);
    }
    @Override protected void renderLabels(GuiGraphics g,int x,int y){}
    @Override public boolean mouseClicked(double x,double y,int b){x/=fit;y/=fit;
        if(b==0&&x>=leftPos+148&&x<leftPos+343&&y>=topPos+54&&y<topPos+194){int index=scroll+(int)(y-topPos-54)/20;if(index<filtered.size()){search.setFocused(false);send(EnchanterMenu.SELECT_BASE+net.minecraft.core.registries.BuiltInRegistries.ENCHANTMENT.getId(filtered.get(index)));return true;}}
        return super.mouseClicked(x,y,b);
    }
    @Override public boolean mouseScrolled(double x,double y,double amount){x/=fit;y/=fit;if(x>=leftPos+148&&x<leftPos+346&&y>=topPos+53&&y<topPos+196){scroll=Math.max(0,Math.min(Math.max(0,filtered.size()-7),scroll-(int)Math.signum(amount)*3));return true;}return super.mouseScrolled(x,y,amount);}
    @Override public boolean mouseReleased(double x,double y,int b){return super.mouseReleased(x/fit,y/fit,b);}
    @Override public boolean mouseDragged(double x,double y,int b,double dx,double dy){return super.mouseDragged(x/fit,y/fit,b,dx/fit,dy/fit);}
    @Override public void mouseMoved(double x,double y){super.mouseMoved(x/fit,y/fit);}
    @Override public boolean keyPressed(int key,int scan,int mods){if(key!=256&&search.isFocused()){search.keyPressed(key,scan,mods);return true;}return super.keyPressed(key,scan,mods);}
}
