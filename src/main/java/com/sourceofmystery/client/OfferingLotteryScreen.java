package com.sourceofmystery.client;

import com.sourceofmystery.network.OfferingRollPacket;
import com.sourceofmystery.offering.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.*;
import net.minecraftforge.registries.ForgeRegistries;
import java.util.*;

/** Native inventory UI; reel motion uses render time, not the 20 Hz server tick. */
public final class OfferingLotteryScreen extends AbstractContainerScreen<OfferingMenu> {
    private static final int[] COLORS={0xff8794ac,0xff56b5ca,0xff7e86fa,0xffc877f5,0xffefc276};
    private static final Set<Long> HEARD=new LinkedHashSet<>();
    private final List<OfferingRollPacket.Cell> preview=new ArrayList<>();
    private OfferingRollPacket roll;
    private boolean finished,sounded;
    private long startNanos;
    private int lastCell=-1;
    private float fitScale=1;
    private Button draw;
    public OfferingLotteryScreen(OfferingMenu menu,Inventory inventory,Component title) {
        super(menu,inventory,title);imageWidth=320;imageHeight=314;
        for(Item item:ForgeRegistries.ITEMS)if(item!=Items.AIR)preview.add(new OfferingRollPacket.Cell(Item.getId(item),0));
        Collections.shuffle(preview,new Random(90210));
    }
    @Override protected void init() {
        // Keep the full inventory usable at Minecraft's minimum 240-pixel GUI height.
        fitScale=Math.min(1,Math.min((width-12)/320f,(height-12)/314f));
        width=Math.round(width/fitScale);height=Math.round(height/fitScale);
        super.init();
        draw=addRenderableWidget(Button.builder(tr("draw"),button->{
            if(minecraft.gameMode!=null)minecraft.gameMode.handleInventoryButtonClick(menu.containerId,0);
        }).bounds(leftPos+101,topPos+174,118,20).build());
    }
    private static Component tr(String key,Object... values) {return Component.translatable("offering.sourceofmystery."+key,values);}
    public static void receive(OfferingRollPacket packet) {
        var mc=Minecraft.getInstance();
        if(mc.screen instanceof OfferingLotteryScreen screen && screen.menu.containerId==packet.containerId()) {
            if(!packet.finished()) {
                screen.roll=packet;screen.startNanos=System.nanoTime();screen.finished=false;screen.sounded=false;screen.lastCell=-1;
            } else if(screen.roll!=null && screen.roll.token()==packet.token()) screen.finished=true;
            else feedback(packet);
        } else if(packet.finished()) feedback(packet);
    }
    private static void feedback(OfferingRollPacket packet) {
        if(!HEARD.add(packet.token()))return;
        if(HEARD.size()>64)HEARD.remove(HEARD.iterator().next());
        var sound=Minecraft.getInstance().getSoundManager();
        if(packet.prizeValue()>packet.paidValue()) {
            sound.play(SimpleSoundInstance.forUI(SoundEvents.PLAYER_LEVELUP,1.15f,.7f));
            sound.play(SimpleSoundInstance.forUI(SoundEvents.AMETHYST_BLOCK_CHIME,1.5f,.75f));
        } else if(packet.prizeValue()<packet.paidValue()) {
            sound.play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_BASS.value(),.55f,.9f));
            sound.play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_DIDGERIDOO.value(),.65f,.6f));
        } else sound.play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_CHIME.value(),1,.5f));
    }
    private double progress() {return roll==null?0:Math.min(1,(System.nanoTime()-startNanos)/5_500_000_000d);}
    @Override public void onClose() {
        // Closing early settles on the server; a finish already received still deserves its feedback.
        if(finished && roll!=null && !sounded)feedback(roll);
        super.onClose();
    }
    @Override public void render(GuiGraphics g,int mouseX,int mouseY,float partial) {
        int actualX=mouseX,actualY=mouseY;
        mouseX=(int)(mouseX/fitScale);mouseY=(int)(mouseY/fitScale);
        g.pose().pushPose();g.pose().scale(fitScale,fitScale,1);
        renderBackground(g);
        if(finished && roll!=null && menu.state()==0 && !menu.getSlot(0).getItem().isEmpty())roll=null;
        draw.active=!menu.rolling() && !menu.getSlot(0).getItem().isEmpty() && ClientEnergyCache.energy>=OfferingMenu.ENERGY_COST;
        draw.setMessage(menu.rolling()?tr("rolling"):tr("draw"));
        if(finished && progress()>=1 && !sounded) {feedback(roll);sounded=true;}
        super.render(g,mouseX,mouseY,partial);
        g.pose().popPose();renderTooltip(g,actualX,actualY);
    }
    @Override public boolean mouseClicked(double x,double y,int button) {return super.mouseClicked(x/fitScale,y/fitScale,button);}
    @Override public boolean mouseReleased(double x,double y,int button) {return super.mouseReleased(x/fitScale,y/fitScale,button);}
    @Override public boolean mouseDragged(double x,double y,int button,double dx,double dy) {return super.mouseDragged(x/fitScale,y/fitScale,button,dx/fitScale,dy/fitScale);}
    @Override public boolean mouseScrolled(double x,double y,double amount) {return super.mouseScrolled(x/fitScale,y/fitScale,amount);}
    @Override public void mouseMoved(double x,double y) {super.mouseMoved(x/fitScale,y/fitScale);}
    private static void frame(GuiGraphics g,int x,int y,int w,int h,int color) {
        g.fill(x,y,x+w,y+1,color);g.fill(x,y+h-1,x+w,y+h,color);g.fill(x,y,x+1,y+h,color);g.fill(x+w-1,y,x+w,y+h,color);
    }
    private void centered(GuiGraphics g,Component text,int x,int y,int color) {g.drawCenteredString(font,text,x,y,color);}
    @Override protected void renderBg(GuiGraphics g,float partial,int mx,int my) {
        int x=leftPos,y=topPos;
        g.fill(x-3,y-3,x+323,y+317,0xc5080a13);
        g.fillGradient(x,y,x+320,y+314,0xff19182b,0xff0c111d);frame(g,x,y,320,314,0xff665276);
        g.fill(x+12,y+39,x+308,y+40,0xff49415e);
        centered(g,tr("title"),x+160,y+12,0xffe8d5ff);
        centered(g,tr("subtitle"),x+160,y+27,0xff8d9bae);
        g.fill(x+12,y+46,x+308,y+109,0xff080c16);frame(g,x+12,y+46,296,63,0xff4c546b);
        renderReel(g,x+14,y+49);
        // Fixed gold pointer and central frame are above the moving reel.
        frame(g,x+138,y+47,44,61,0xffe7bf79);
        for(int i=0;i<5;i++)g.fill(x+155+i,y+43+i,x+165-i,y+44+i,0xffffdfa0);
        g.fill(x+158,y+107,x+162,y+111,0xffedc785);
        Component status=tr("ready");int color=0xff9caabe;
        if(roll!=null && progress()>=1 && finished) {
            int compare=Long.compare(roll.prizeValue(),roll.paidValue());
            color=compare>0?0xffedc276:compare<0?0xffa49bb3:0xff7dced8;
            status=tr(compare>0?"win":compare<0?"loss":"equal",roll.prize().getHoverName(),format(roll.prizeValue()));
        } else if(menu.rolling())status=tr("rolling");
        if(menu.error()!=0) {status=tr("error"+menu.error());color=0xffff8f93;}
        centered(g,status,x+160,y+114,color);
        g.drawString(font,tr("input_value"),x+20,y+133,0xff8d9bae,false);
        g.drawString(font,format(menu.quotedValue()),x+20,y+145,0xffd3cee6,false);
        g.drawString(font,tr("energy"),x+212,y+133,0xff8d9bae,false);
        g.drawString(font,format(ClientEnergyCache.energy),x+212,y+145,ClientEnergyCache.energy>=1000?0xff8edbdc:0xffed8a96,false);
        g.fill(x+149,y+130,x+171,y+152,0xff080b16);frame(g,x+149,y+130,22,22,0xffa87ac8);
        centered(g,tr("cost"),x+160,y+158,0xffb1a8c6);
        g.drawString(font,tr("luck",String.format(Locale.ROOT,"%+.0f%%",(menu.luckMultiplier()-1)*100)),x+20,y+164,0xff86cdb9,false);
        g.drawString(font,tr("boost",String.format(Locale.ROOT,"%+.0f%%",(menu.goodMultiplier()-1)*100)),x+228,y+164,0xffd5bc86,false);
        g.fill(x+12,y+200,x+308,y+201,0xff38374d);
        g.drawString(font,playerInventoryTitle,x+79,y+203,0xff8d9bae,false);
        for(var slot:menu.slots)if(slot.index!=0) {
            g.fill(x+slot.x-1,y+slot.y-1,x+slot.x+17,y+slot.y+17,0xff0a0f1b);
            frame(g,x+slot.x-1,y+slot.y-1,18,18,0xff32394c);
        }
        centered(g,tr("pool",menu.poolSize()),x+160,y+298,0xff8d9bae);
    }
    private void renderReel(GuiGraphics g,int x,int y) {
        var cells=roll==null?preview:roll.reel();if(cells.isEmpty())return;
        double p=progress(),ease=1-Math.pow(1-p,4);
        double center=roll==null?4:(2+(OfferingMenu.STOP_INDEX-2)*ease);
        int current=(int)Math.floor(center+.5);
        if(roll!=null && p<1 && current!=lastCell) {
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(),1.55f,.12f));lastCell=current;
        }
        g.enableScissor(Math.round(x*fitScale),Math.round(y*fitScale),Math.round((x+292)*fitScale),Math.round((y+56)*fitScale));
        for(int i=Math.max(0,current-5);i<Math.min(cells.size(),current+6);i++) {
            var cell=cells.get(i);var stack=new ItemStack(Item.byId(cell.itemId()));int left=x+146+(int)Math.round((i-center)*44)-21;
            int color=COLORS[Math.min(4,cell.tier())];
            g.fillGradient(left,y+2,left+42,y+54,0xff22283c,0xff121726);g.fill(left+2,y+51,left+40,y+54,color);
            g.renderItem(stack,left+13,y+10);
            var name=font.plainSubstrByWidth(stack.getHoverName().getString(),36);
            g.drawCenteredString(font,name,left+21,y+36,0xffc2c7db);
        }
        g.disableScissor();
    }
    @Override protected void renderLabels(GuiGraphics g,int x,int y) {}
    public static String format(long value) {return String.format(Locale.ROOT,"%,d",value);}
}
