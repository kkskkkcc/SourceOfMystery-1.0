package com.sourceofmystery.cinematic;

import com.sourceofmystery.energy.MysteryEnergy;
import com.sourceofmystery.item.ModItems;
import com.sourceofmystery.network.DivineCinematicPacket;
import com.sourceofmystery.network.ModNetwork;
import com.sourceofmystery.ritual.OfferingPortalRitual;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;
import java.util.*;

/** The server owns the once-per-player receipt and a bounded, stationary session. */
@Mod.EventBusSubscriber(modid="sourceofmystery")
public final class DivineCinematic {
    public static final String SEEN="divine_blessing_first_equip_v1";
    private static final String PENDING="divine_cg_restore_pending",GRAVITY="divine_cg_previous_no_gravity";
    private static final Map<UUID,Session> ACTIVE=new HashMap<>();
    private record Session(UUID token,ServerPlayer player,Vec3 anchor,boolean gravity,net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension,long start) {}
    private DivineCinematic() {}
    public static boolean active(net.minecraft.world.entity.player.Player p){return ACTIVE.containsKey(p.getUUID());}

    @SubscribeEvent public static void tick(TickEvent.PlayerTickEvent event){
        if(event.phase!=TickEvent.Phase.END || !(event.player instanceof ServerPlayer p))return;
        Session s=ACTIVE.get(p.getUUID());
        if(s!=null){
            if(!p.isAlive() || !p.level().dimension().equals(s.dimension) || p.server.getTickCount()-s.start>(DivineCGTimeline.END+10)*20){finish(p,s.token);return;}
            p.setNoGravity(true);p.setDeltaMovement(Vec3.ZERO);p.fallDistance=0;
            if(p.server.getTickCount()-s.start>=Math.ceil(DivineCGTimeline.realTime(DivineCGTimeline.EQUIP)*20))grant(p);
            if(p.position().distanceToSqr(s.anchor)>.01)p.teleportTo(s.anchor.x,s.anchor.y,s.anchor.z);
            return;
        }
        if(!p.isAlive() || p.isSpectator() || p.isPassenger() || p.isSleeping()
                || OfferingPortalRitual.active(p)
                || !p.getItemBySlot(EquipmentSlot.CHEST).is(ModItems.DIVINE_BLESSING_CHESTPLATE.get())
                || MysteryEnergy.getPlayerData(p).getBoolean(SEEN))return;
        start(p);
    }
    private static void start(ServerPlayer p){
        UUID token=UUID.randomUUID();
        var s=new Session(token,p,p.position(),p.isNoGravity(),p.level().dimension(),p.server.getTickCount());
        ACTIVE.put(p.getUUID(),s);MysteryEnergy.getPlayerData(p).putBoolean(SEEN,true);
        var data=MysteryEnergy.getPlayerData(p);data.putBoolean(PENDING,true);data.putBoolean(GRAVITY,s.gravity);
        p.closeContainer();p.setNoGravity(true);p.setDeltaMovement(Vec3.ZERO);p.fallDistance=0;
        ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(()->p),new DivineCinematicPacket(token,true,p.getItemBySlot(EquipmentSlot.CHEST).copy()));
    }
    public static void finish(ServerPlayer p,UUID token){
        Session s=ACTIVE.get(p.getUUID());if(s==null || !s.token.equals(token))return;
        ACTIVE.remove(p.getUUID());p.setNoGravity(s.gravity);p.setDeltaMovement(Vec3.ZERO);p.fallDistance=0;
        clearReceipt(p);
        grant(p);
        if(p.connection!=null)ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(()->p),new DivineCinematicPacket(token,false,net.minecraft.world.item.ItemStack.EMPTY));
    }
    private static void clearReceipt(ServerPlayer p){var data=MysteryEnergy.getPlayerData(p);data.remove(PENDING);data.remove(GRAVITY);}
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent e){
        if(e.getEntity() instanceof ServerPlayer p){var data=MysteryEnergy.getPlayerData(p);if(data.getBoolean(PENDING)){p.setNoGravity(data.getBoolean(GRAVITY));p.fallDistance=0;clearReceipt(p);grant(p);}}
    }
    @SubscribeEvent public static void commands(net.minecraftforge.event.RegisterCommandsEvent e){
        e.getDispatcher().register(net.minecraft.commands.Commands.literal("somdivinecg").requires(s->s.hasPermission(2)).executes(ctx->{
            var p=ctx.getSource().getPlayerOrException();
            if(active(p)||OfferingPortalRitual.active(p)||!p.isAlive()||p.isPassenger()
                    ||!p.getItemBySlot(EquipmentSlot.CHEST).is(ModItems.DIVINE_BLESSING_CHESTPLATE.get())){
                ctx.getSource().sendFailure(net.minecraft.network.chat.Component.literal("请先穿戴神威天佑，并等待当前动画结束。"));return 0;
            }
            start(p);return 1;
        }));
    }
    private static void grant(ServerPlayer p){
        if(!com.sourceofmystery.advancement.AdvancementHelper.hasAdvancement(p,"ascend_divinity"))
            com.sourceofmystery.advancement.AdvancementHelper.grantAdvancement(p,"ascend_divinity");
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent e){if(e.getEntity() instanceof ServerPlayer p){var s=ACTIVE.get(p.getUUID());if(s!=null)finish(p,s.token);}}
    @SubscribeEvent public static void stop(ServerStoppedEvent e){for(var s:List.copyOf(ACTIVE.values())){s.player.setNoGravity(s.gravity);clearReceipt(s.player);}ACTIVE.clear();}
    @SubscribeEvent public static void attack(LivingAttackEvent e){
        if(e.getEntity() instanceof ServerPlayer p && active(p)
                || e.getSource().getEntity() instanceof ServerPlayer attacker && active(attacker))e.setCanceled(true);
    }
    @SubscribeEvent public static void interact(PlayerInteractEvent e){if(!e.getLevel().isClientSide&&active(e.getEntity())&&e.isCancelable())e.setCanceled(true);}
    @SubscribeEvent public static void breaking(BlockEvent.BreakEvent e){if(active(e.getPlayer()))e.setCanceled(true);}
}
