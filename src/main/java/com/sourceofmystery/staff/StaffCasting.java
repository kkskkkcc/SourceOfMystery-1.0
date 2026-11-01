package com.sourceofmystery.staff;

import com.sourceofmystery.energy.MysteryEnergy;
import com.sourceofmystery.entity.*;
import com.sourceofmystery.item.DimensionBreakerStaffItem;
import com.sourceofmystery.network.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.*;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;
import software.bernie.geckolib.animatable.GeoItem;
import java.util.*;

@Mod.EventBusSubscriber(modid="sourceofmystery")
public final class StaffCasting {
    private static final class Cast {
        final ServerLevel level;final InteractionHand hand;final ItemStack stack;final StaffSpell spell;
        OutsiderSpell sky;Vec3 impact;
        Cast(ServerLevel level,InteractionHand hand,ItemStack stack,StaffSpell spell) {
            this.level=level;this.hand=hand;this.stack=stack;this.spell=spell;
        }
    }
    private static final Map<UUID, Cast> CASTS = new HashMap<>();
    private StaffCasting() {}

    public static boolean begin(Player player, InteractionHand hand, ItemStack stack) {
        if (!(player instanceof ServerPlayer server) || TimeStop.frozen(player)) return false;
        StaffSpell spell = StaffSpell.selected(stack);
        if (spell == StaffSpell.TIME_STOP && !TimeStop.canCast(server)) return fail(player,"time_cooldown");
        if (MysteryEnergy.getEnergy(player) < spell.cost()) return fail(player,"energy");
        CASTS.put(player.getUUID(),new Cast(server.serverLevel(),hand,stack,spell));
        var staff=(DimensionBreakerStaffItem)stack.getItem();
        staff.triggerAnim(player,GeoItem.getOrAssignId(stack,server.serverLevel()),"staff",animation(spell));
        if (spell.castTicks() == 0) return finish(player,stack);
        send(server.serverLevel(),new StaffEffectPacket(StaffEffectPacket.CHARGE,player.getUUID(),player.position(),
                target(server),spell.castTicks(),spell.ordinal()));
        return true;
    }

    private static String animation(StaffSpell spell) {
        return switch(spell) {
            case ANNIHILATION->"charge"; case EARTH_SPLIT->"cleave"; case EARTH_LIFT->"uplift";
            case SPACE_RIFT->"rift"; case TIME_STOP->"time_stop";
        };
    }

    public static void tick(Player player, ItemStack stack, int remaining) {
        Cast cast=CASTS.get(player.getUUID());
        if (cast==null || cast.stack!=stack || !player.isAlive() || TimeStop.frozen(player)) {player.stopUsingItem();return;}
        int skyLead=(int)((OutsiderRitual.ORB_START-OutsiderRitual.SKY_START)*20);
        if(cast.spell==StaffSpell.ANNIHILATION&&cast.sky==null&&remaining<=skyLead) {
            cast.impact=target((ServerPlayer)player);
            cast.sky=OutsiderSpell.spawn(cast.level,player,OutsiderSpell.SKY_SEAL,skyOrigin(player),Vec3.ZERO);
        }
    }

    public static boolean finish(Player player, ItemStack stack) {
        Cast cast=CASTS.remove(player.getUUID());
        if (cast==null || cast.stack!=stack || player.level()!=cast.level || !player.isAlive()) return false;
        stopVisual(player,cast.level);
        if(cast.spell==StaffSpell.ANNIHILATION)stopAnimation(player,cast);
        if (MysteryEnergy.getEnergy(player)<cast.spell.cost()) {discardSky(cast);return fail(player,"energy");}
        var server=(ServerPlayer)player;
        boolean success=switch(cast.spell) {
            case ANNIHILATION->meteor(server,cast);
            case EARTH_SPLIT->StaffTerrain.split(server);
            case EARTH_LIFT->StaffTerrain.lift(server);
            case TIME_STOP->TimeStop.start(server);
            case SPACE_RIFT->StaffPortals.open(server);
        };
        if (!success) {discardSky(cast);return false;}
        if (cast.spell.cost()>0) MysteryEnergy.consumeEnergy(player,cast.spell.cost());
        StaffSounds.play(cast.level,player.position(),cast.spell,"release");
        return true;
    }

    private static Vec3 skyOrigin(Player player) {
        return new Vec3(player.getX(),Math.max(OutsiderRitual.SKY_HEIGHT,player.getY()+60),player.getZ());
    }
    private static boolean meteor(ServerPlayer player,Cast cast) {
        ServerLevel level=player.serverLevel(); Vec3 impact=cast.impact==null?target(player):cast.impact;
        Vec3 sky=cast.sky==null?skyOrigin(player):cast.sky.position();
        OutsiderSpell meteor=OutsiderSpell.spawn(level,player,OutsiderSpell.METEOR,sky,Vec3.ZERO);
        if (meteor.isRemoved()) return false;
        meteor.setTimedImpact(impact,OutsiderRitual.FALL_TICKS);
        if(cast.sky==null)OutsiderSpell.spawn(level,player,OutsiderSpell.SKY_SEAL,sky,Vec3.ZERO);
        return true;
    }

    public static Vec3 target(ServerPlayer player) {
        Vec3 start=player.getEyePosition(),end=start.add(player.getLookAngle().scale(128));
        BlockHitResult hit=player.serverLevel().clip(new ClipContext(start,end,ClipContext.Block.COLLIDER,ClipContext.Fluid.ANY,player));
        if (hit.getType()==HitResult.Type.BLOCK) return hit.getLocation();
        Vec3 flat=new Vec3(player.getLookAngle().x,0,player.getLookAngle().z).normalize();
        if (flat.lengthSqr()<.1) flat=new Vec3(0,0,1);
        Vec3 point=player.position().add(flat.scale(80));
        return new Vec3(point.x,player.serverLevel().getHeight(Heightmap.Types.WORLD_SURFACE,(int)Math.floor(point.x),(int)Math.floor(point.z)),point.z);
    }

    public static void cancel(Player player) {
        Cast cast=CASTS.remove(player.getUUID());
        if (cast!=null) {stopVisual(player,cast.level);stopAnimation(player,cast);discardSky(cast);}
    }
    private static void discardSky(Cast cast) {if(cast.sky!=null)cast.sky.discard();}
    private static void stopAnimation(Player player,Cast cast) {
        ((DimensionBreakerStaffItem)cast.stack.getItem()).stopTriggeredAnim(player,GeoItem.getId(cast.stack),"staff",animation(cast.spell));
    }
    private static void stopVisual(Player player,ServerLevel level) {
        send(level,new StaffEffectPacket(StaffEffectPacket.CANCEL,player.getUUID(),player.position(),Vec3.ZERO,0,0));
    }
    public static void send(ServerLevel level,StaffEffectPacket packet) {
        ModNetwork.CHANNEL.send(PacketDistributor.DIMENSION.with(level::dimension),packet);
    }
    public static boolean fail(Player player,String reason) {
        player.displayClientMessage(Component.translatable("message.sourceofmystery.staff."+reason),true); return false;
    }
    @SubscribeEvent public static void playerTick(TickEvent.PlayerTickEvent event) {
        if(event.phase!=TickEvent.Phase.END||event.player.level().isClientSide)return;
        Cast cast=CASTS.get(event.player.getUUID());
        if(cast!=null&&(!event.player.isAlive()||event.player.level()!=cast.level||event.player.getItemInHand(cast.hand)!=cast.stack))cancel(event.player);
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) { cancel(event.getEntity()); }
    @SubscribeEvent public static void stop(ServerStoppedEvent event) { CASTS.clear(); }
}
