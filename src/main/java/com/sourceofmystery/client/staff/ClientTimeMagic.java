package com.sourceofmystery.client.staff;

import com.sourceofmystery.network.*;
import com.sourceofmystery.staff.TimeMagicType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod;
import java.util.*;

@Mod.EventBusSubscriber(modid="sourceofmystery",value=Dist.CLIENT)
public final class ClientTimeMagic {
    private static ClientLevel world;
    private static UUID token,caster;
    private static TimeMagicType mode;
    private static int left,phase,steps=1;
    private static final Set<UUID> players=new HashSet<>();
    private ClientTimeMagic(){}
    public static boolean altered(){return mode!=null&&left!=0&&world==Minecraft.getInstance().level;}
    public static boolean rateControlled(){return altered()&&mode!=TimeMagicType.STOP;}
    public static boolean localActive(){return altered()&&Minecraft.getInstance().player!=null&&Minecraft.getInstance().player.getUUID().equals(caster);}
    public static int steps(){return rateControlled()?steps:1;}
    public static boolean skipPlayerTick(Entity e){return rateControlled()&&e instanceof Player&&!players.add(e.getUUID());}
    public static float partial(float partial){return StaffVisuals.timeStopped()?0:rateControlled()&&mode==TimeMagicType.SLOW?(phase+partial)/10f:partial;}
    public static float partial(Entity e,float partial){return e instanceof Player?StaffVisuals.frozen(e)?0:partial:partial(partial);}
    public static void accept(TimeMagicSyncPacket p){
        var mc=Minecraft.getInstance();if(mc.level==null||!mc.level.dimension().location().equals(p.dimension()))return;
        if(p.remaining()==0){if(!p.token().equals(token))return;clear();mc.level.setGameTime(p.gameTime());mc.level.setDayTime(p.dayTime());return;}
        if(p.mode()<0||p.mode()>=TimeMagicType.values().length||p.remaining()<TimeMagicSyncPacket.INDEFINITE)return;
        world=mc.level;token=p.token();caster=p.caster();mode=TimeMagicType.values()[p.mode()];left=p.remaining();phase=Math.floorMod(p.phase(),10);steps=mode==TimeMagicType.HASTE?5:0;players.clear();
        world.setGameTime(p.gameTime());world.setDayTime(p.dayTime());
        StaffVisuals.accept(new StaffEffectPacket(StaffEffectPacket.STOP,caster,Vec3.ZERO,Vec3.ZERO,mode==TimeMagicType.STOP?left:0,0));
        for(Entity e:world.entitiesForRendering())if(!(e instanceof Player))e.setOldPosAndRot();
    }
    private static void clear(){
        if(caster!=null)StaffVisuals.accept(new StaffEffectPacket(StaffEffectPacket.STOP,caster,Vec3.ZERO,Vec3.ZERO,0,0));
        world=null;token=null;caster=null;mode=null;left=phase=0;steps=1;players.clear();
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void tick(TickEvent.ClientTickEvent e){
        if(e.phase!=TickEvent.Phase.START)return;
        var mc=Minecraft.getInstance();if(world!=mc.level){clear();return;}if(!altered()||mc.isPaused())return;
        players.clear();steps=mode==TimeMagicType.HASTE?5:mode==TimeMagicType.STOP?0:++phase==10?1:0;if(phase==10)phase=0;
        if(left>0&&--left==0)clear();
    }
    @SubscribeEvent public static void hud(RenderGuiEvent.Post e){if(!altered())return;var mc=Minecraft.getInstance();e.getGuiGraphics().drawCenteredString(mc.font,mode.title(),mc.getWindow().getGuiScaledWidth()/2,mc.getWindow().getGuiScaledHeight()-65,0xffc6c7ff);}
}
