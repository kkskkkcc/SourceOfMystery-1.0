package com.sourceofmystery.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.sourceofmystery.SourceOfMystery;
import com.sourceofmystery.entity.OutsiderEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.CustomizeGuiOverlayEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Only replaces the matching network boss UUID; other bosses retain their HUD. */
@Mod.EventBusSubscriber(modid=SourceOfMystery.MOD_ID,value=Dist.CLIENT)
public final class OutsiderBossOverlay {
    private static final ResourceLocation FRAME=new ResourceLocation(SourceOfMystery.MOD_ID,"textures/gui/archmage_boss_frame.png");
    private static final ResourceLocation FILL=new ResourceLocation(SourceOfMystery.MOD_ID,"textures/gui/archmage_boss_fill.png");
    @SubscribeEvent public static void render(CustomizeGuiOverlayEvent.BossEventProgress event){
        Minecraft mc=Minecraft.getInstance();if(mc.level==null)return;
        var boss=event.getBossEvent();OutsiderEntity caster=null;
        for(var entity:mc.level.entitiesForRendering())if(entity instanceof OutsiderEntity e&&e.bossEventId().filter(boss.getId()::equals).isPresent()){
            caster=e;break;
        }
        boolean translated=boss.getName().getContents() instanceof TranslatableContents c&&c.getKey().equals("entity.sourceofmystery.outsider");
        if(caster==null&&!translated)return;
        event.setCanceled(true);
        var gui=event.getGuiGraphics();int screen=event.getWindow().getGuiScaledWidth();
        int width=Math.min(340,screen-16),height=Math.round(width*80/340f);
        int x=(screen-width)/2,y=Math.max(0,event.getY()-8);
        float scale=width/340f;
        int bx=x+Math.round(width*.203f),by=y+Math.round(height*.451f);
        int bw=Math.round(width*.631f),bh=Math.max(3,Math.round(height*.083f));
        float progress=Mth.clamp(boss.getProgress(),0,1);int filled=Math.round(bw*progress);
        RenderSystem.enableBlend();RenderSystem.defaultBlendFunc();
        gui.fill(bx,by,bx+bw,by+bh,0xEF15091F);
        if(filled>0){
            float flow=(mc.level.getGameTime()+event.getPartialTick())*2;
            gui.enableScissor(bx,by,bx+filled,by+bh);
            gui.blit(FILL,bx,by,filled,bh,flow%800,0,Math.max(1,filled*3),32,1024,32);
            gui.fillGradient(bx,by,bx+filled,by+bh,0x559E57E9,0x00400070);
            int sweep=(int)(flow%(bw+20))-20;
            gui.fill(bx+sweep,by,bx+sweep+2,by+bh,0x55DAB2FF);
            gui.disableScissor();
        }
        gui.blit(FRAME,x,y,width,height,0,0,2172,724,2172,724);
        Component name=boss.getName();int titleY=y+Math.round(18*scale);
        gui.drawCenteredString(mc.font,name,screen/2,titleY,0xE8CEFF);
        String value=caster==null?Math.round(progress*100)+"%":Math.round(progress*caster.getMaxHealth())+" / "+Math.round(caster.getMaxHealth());
        int baseline=y+Math.round(57*scale);
        gui.drawCenteredString(mc.font,value,screen/2,baseline,0xCBA9EB);
        if(caster!=null&&caster.combatState()==OutsiderEntity.METEOR)
            gui.drawCenteredString(mc.font,Component.translatable("boss.sourceofmystery.archmage.ritual"),screen/2,baseline+10,0xBE81FF);
        event.setIncrement(height+5);
        RenderSystem.disableBlend();
    }
}
