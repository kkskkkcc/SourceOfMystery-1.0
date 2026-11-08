package com.sourceofmystery.staff;

import com.sourceofmystery.network.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.resources.*;
import net.minecraft.server.level.*;
import net.minecraft.tags.*;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.*;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;
import java.util.*;

/** Bounded server wind and terrain; the visible funnel and audio are client render timelines. */
@Mod.EventBusSubscriber(modid="sourceofmystery")
public final class StaffWeather {
    public static final int RADIUS=100,HEIGHT=180,FORM_TICKS=120,FADE_TICKS=40;
    public static final String DEBRIS_TOKEN="WeatherDebrisToken",DEBRIS_OWNER="WeatherDebrisOwner";
    private static final TagKey<Block> MOVABLE=TagKey.create(Registries.BLOCK,new ResourceLocation("sourceofmystery","tornado_movable"));
    private static final Map<ServerLevel,Map<UUID,Storm>> WORLDS=new IdentityHashMap<>();
    public static final class Storm {
        public final UUID token=UUID.randomUUID(),owner;public Vec3 center;public final Vec3 direction;
        public TornadoVariant variant;public int age,remaining;public boolean stopping;
        private final Map<UUID,Integer> debris=new LinkedHashMap<>();private double blockCredit;
        Storm(ServerPlayer p,Vec3 point){owner=p.getUUID();center=point;direction=new Vec3(p.getLookAngle().x,0,p.getLookAngle().z).normalize();variant=TornadoVariant.detect(p.serverLevel(),point);remaining=StaffConfig.TORNADO_DURATION.get();}
        public boolean contains(Entity e){return Math.abs(e.getY()-center.y-HEIGHT/2d)<HEIGHT/2d+16&&Math.pow(e.getX()-center.x,2)+Math.pow(e.getZ()-center.z,2)<=RADIUS*RADIUS;}
        public float strength(){return Math.min(1,age/(float)FORM_TICKS)*Math.min(1,remaining/(float)FADE_TICKS);}
        WeatherSyncPacket.Storm snapshot(){return new WeatherSyncPacket.Storm(token,owner,center,variant.ordinal(),age,remaining);}
    }
    private StaffWeather(){}
    public static Storm field(Player p){if(!(p.level() instanceof ServerLevel l))return null;var map=WORLDS.get(l);return map==null?null:map.get(p.getUUID());}
    public static boolean active(Player p){return field(p)!=null;}
    public static void cycle(ServerPlayer p,ItemStack stack){
        if(p.isUsingItem()||TimeStop.frozen(p))return;
        var next=WeatherType.values()[(WeatherType.selected(stack).ordinal()+1)%WeatherType.values().length];stack.getOrCreateTag().putString(WeatherType.KEY,next.id);
        p.getInventory().setChanged();p.containerMenu.broadcastChanges();p.displayClientMessage(Component.translatable("message.sourceofmystery.weather_selected",next.title()),true);
    }
    public static boolean start(ServerPlayer p,WeatherType type){
        var level=p.serverLevel();
        if(type!=WeatherType.TORNADO){
            if(!level.dimensionType().hasSkyLight()||level.dimensionType().hasCeiling())return StaffCasting.fail(p,"weather_dimension");
            stop(p);int ticks=StaffConfig.WEATHER_DURATION.get();level.setWeatherParameters(type==WeatherType.CLEAR?ticks:0,ticks,type!=WeatherType.CLEAR,type==WeatherType.THUNDER);
            p.displayClientMessage(Component.translatable("message.sourceofmystery.weather_changed",type.title()),true);return true;
        }
        if(active(p)||!p.isAlive()||p.isSpectator())return StaffCasting.fail(p,"weather_busy");
        var map=WORLDS.computeIfAbsent(level,l->new LinkedHashMap<>());if(map.size()>=8)return StaffCasting.fail(p,"weather_busy");
        Vec3 hit=StaffCasting.target(p);BlockPos column=BlockPos.containing(hit);
        if(!level.hasChunk(column.getX()>>4,column.getZ()>>4)||!level.getWorldBorder().isWithinBounds(column))return StaffCasting.fail(p,"weather_unloaded");
        Vec3 base=new Vec3(hit.x,surface(level,column.getX(),column.getZ(),level.dimensionType().hasCeiling()?p.getY():hit.y,false),hit.z);
        var storm=new Storm(p,base);map.put(p.getUUID(),storm);
        // The tornado has its own mesocyclone and variant precipitation. A global vanilla
        // rain curtain hides the funnel and is especially wrong for sand/fire vortices.
        if(level.dimensionType().hasSkyLight()&&!level.dimensionType().hasCeiling())level.setWeatherParameters(storm.remaining,0,false,false);
        p.displayClientMessage(Component.translatable("message.sourceofmystery.tornado_started",storm.variant.title()),true);broadcast(level);return true;
    }
    public static void stop(ServerPlayer p,UUID token){var s=field(p);if(s!=null&&s.token.equals(token))stop(p);}
    public static void stop(ServerPlayer p){var s=field(p);if(s!=null&&!s.stopping){s.stopping=true;s.remaining=Math.min(s.remaining,FADE_TICKS);clearDebris(p.serverLevel(),s);broadcast(p.serverLevel());}}
    private static boolean immune(Entity e,ServerPlayer owner){return e==owner||e.hasPassenger(owner)||e instanceof Marker||e instanceof com.sourceofmystery.entity.OutsiderSpell||e.isSpectator()||e instanceof Projectile projectile&&projectile.getOwner()==owner;}
    @SubscribeEvent public static void tick(TickEvent.LevelTickEvent event){
        if(event.phase!=TickEvent.Phase.START||!(event.level instanceof ServerLevel level))return;var map=WORLDS.get(level);if(map==null||TimeStop.active(level))return;
        long deadline=System.nanoTime()+2_000_000;boolean changed=false;
        var iterator=map.values().iterator();
        while(iterator.hasNext()){
            var s=iterator.next();var owner=level.getServer().getPlayerList().getPlayer(s.owner);
            if(owner==null||!owner.isAlive()||owner.level()!=level||--s.remaining<=0){clearDebris(level,s);iterator.remove();changed=true;continue;}
            s.age++;
            if(s.remaining<=FADE_TICKS)s.stopping=true;
            if(!s.stopping){
                drift(level,s);
                if(s.age%20==0)s.variant=TornadoVariant.detect(level,s.center);
                wind(level,owner,s);
                if(System.nanoTime()<deadline)terrain(level,owner,s,deadline);
            }
            debris(level,s);
        }
        if(changed||level.getServer().getTickCount()%5==0)broadcast(level);
        if(map.isEmpty())WORLDS.remove(level);
    }
    private static void drift(ServerLevel level,Storm s){
        if(s.age<FORM_TICKS)return;
        double bend=Math.sin(s.age/380d)*.3;Vec3 step=s.direction.scale(.025).add(-s.direction.z*bend*.025,0,s.direction.x*bend*.025);
        Vec3 next=s.center.add(step);BlockPos p=BlockPos.containing(next);
        if(!level.hasChunk(p.getX()>>4,p.getZ()>>4)||!level.getWorldBorder().isWithinBounds(p))return;
        int surface=surface(level,p.getX(),p.getZ(),s.center.y,false);
        s.center=new Vec3(next.x,s.center.y+Math.max(-.3,Math.min(.3,surface-s.center.y)),next.z);
    }
    /** A ceiling dimension's heightmap is its roof, not the terrain the caster sees. */
    public static int surface(ServerLevel level,int x,int z,double reference,boolean leaves){
        if(!level.dimensionType().hasCeiling())return level.getHeight(leaves?Heightmap.Types.MOTION_BLOCKING:Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,x,z);
        int y=Math.max(level.getMinBuildHeight(),Math.min(level.getMaxBuildHeight()-1,(int)Math.floor(reference)-1));
        for(int bottom=Math.max(level.getMinBuildHeight(),y-96);y>=bottom;y--){
            var pos=new BlockPos(x,y,z);var state=level.getBlockState(pos);
            if(!state.getFluidState().isEmpty()||(!state.getCollisionShape(level,pos).isEmpty()&&(leaves||!state.is(BlockTags.LEAVES))))return y+1;
        }
        return (int)Math.floor(reference);
    }
    private static void wind(ServerLevel level,ServerPlayer owner,Storm s){
        var area=new AABB(s.center.add(-RADIUS,-16,-RADIUS),s.center.add(RADIUS,HEIGHT,RADIUS));
        for(var e:level.getEntities(owner,area,e->e.isAlive()&&!immune(e,owner)))if(s.contains(e)){
            if(StaffDomains.constrained(e)||TimeStop.frozen(e))continue;
            Vec3 radial=new Vec3(e.getX()-s.center.x,0,e.getZ()-s.center.z);double radius=radial.length();
            Vec3 n=radius<.01?new Vec3(1,0,0):radial.scale(1/radius),tangent=new Vec3(-n.z,0,n.x);
            double core=1-radius/RADIUS,high=(e.getY()-s.center.y)/HEIGHT;
            Vec3 force=n.scale(high>.65?.13:-.025-.08*core).add(tangent.scale(.025+.085*core)).add(0,.035+.10*core,0).scale(s.strength());
            Vec3 velocity=e.getDeltaMovement().scale(.88).add(force);double speed=velocity.horizontalDistance();if(speed>1.15)velocity=new Vec3(velocity.x*1.15/speed,velocity.y,velocity.z*1.15/speed);
            velocity=new Vec3(velocity.x,Math.min(.8,velocity.y),velocity.z);e.setDeltaMovement(velocity);e.hasImpulse=true;e.fallDistance=0;
            if(e instanceof Mob m&&m.isNoAi()||e instanceof net.minecraft.world.entity.decoration.ArmorStand)e.move(MoverType.SELF,velocity);
            if(e instanceof ServerPlayer p&&s.age%4==0)p.connection.send(new ClientboundSetEntityMotionPacket(p));
            if(s.age>=60&&s.age%20==0&&e instanceof LivingEntity living){
                var key=ResourceKey.create(Registries.DAMAGE_TYPE,new ResourceLocation("sourceofmystery","tornado_wind"));
                living.hurt(new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(key),owner),5);
                switch(s.variant){
                    case NORMAL->living.addEffect(new MobEffectInstance(MobEffects.WEAKNESS,45,0,true,false,true));
                    case SAND->{living.addEffect(new MobEffectInstance(MobEffects.BLINDNESS,45,0,true,false,true));living.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,45,0,true,false,true));}
                    case WATER->living.clearFire();
                    case FIRE->{living.addEffect(new MobEffectInstance(MobEffects.WEAKNESS,45,1,true,false,true));living.addEffect(new MobEffectInstance(MobEffects.GLOWING,45,0,true,false,true));}
                }
            }
            if(s.variant==TornadoVariant.WATER)e.clearFire();
        }
    }
    private static void terrain(ServerLevel level,ServerPlayer owner,Storm s,long deadline){
        if(s.age<60||!StaffConfig.TORNADO_TERRAIN.get()||!level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING))return;
        s.blockCredit=Math.min(5,s.blockCredit+StaffConfig.TORNADO_BLOCKS_PER_SECOND.get()/20d);
        while(s.blockCredit>=1&&System.nanoTime()<deadline){
            s.blockCredit--;double angle=level.random.nextDouble()*Math.PI*2,r=Math.sqrt(level.random.nextDouble())*RADIUS*.85;
            int x=(int)Math.floor(s.center.x+Math.cos(angle)*r),z=(int)Math.floor(s.center.z+Math.sin(angle)*r);
            if(!level.hasChunk(x>>4,z>>4))continue;
            var pos=new BlockPos(x,surface(level,x,z,s.center.y,true)-1,z);
            var state=level.getBlockState(pos);if(Math.abs(pos.getY()-s.center.y)>32||!level.getWorldBorder().isWithinBounds(pos)||!level.mayInteract(owner,pos))continue;
            if(s.variant==TornadoVariant.WATER&&level.getBlockState(pos.above()).is(BlockTags.FIRE)){if(!MinecraftForge.EVENT_BUS.post(new BlockEvent.BreakEvent(level,pos.above(),level.getBlockState(pos.above()),owner)))level.removeBlock(pos.above(),false);continue;}
            if(state.hasBlockEntity()||!state.is(MOVABLE)||state.getDestroySpeed(level,pos)<0||state.getDestroySpeed(level,pos)>6)continue;
            if(MinecraftForge.EVENT_BUS.post(new BlockEvent.BreakEvent(level,pos,state,owner)))continue;
            if(s.variant==TornadoVariant.FIRE&&(state.is(BlockTags.LOGS)||state.is(BlockTags.PLANKS)||state.is(Blocks.GRASS_BLOCK))){level.setBlockAndUpdate(pos,(state.is(Blocks.GRASS_BLOCK)?Blocks.COARSE_DIRT:Blocks.BASALT).defaultBlockState());continue;}
            s.debris.entrySet().removeIf(e->level.getEntity(e.getKey())==null);
            if(s.debris.size()>=96)continue;
            var piece=FallingBlockEntity.fall(level,pos,state);piece.dropItem=false;piece.setNoGravity(true);piece.setPos(piece.position().add(0,.3,0));
            piece.getPersistentData().putUUID(DEBRIS_TOKEN,s.token);piece.getPersistentData().putUUID(DEBRIS_OWNER,s.owner);s.debris.put(piece.getUUID(),s.age);
        }
    }
    private static void debris(ServerLevel level,Storm s){
        var iterator=s.debris.entrySet().iterator();
        while(iterator.hasNext()){
            var receipt=iterator.next();var e=level.getEntity(receipt.getKey());if(e==null){iterator.remove();continue;}
            if(s.stopping||s.age-receipt.getValue()>120||e.getY()>s.center.y+HEIGHT*.8){e.discard();iterator.remove();continue;}
            Vec3 radial=e.position().subtract(s.center);double radius=Math.max(1,radial.horizontalDistance());Vec3 n=new Vec3(radial.x/radius,0,radial.z/radius);
            e.setDeltaMovement(n.scale(-.25).add(-n.z*.95,.65,n.x*.95));e.hasImpulse=true;
            if(e instanceof FallingBlockEntity f){f.dropItem=false;f.setNoGravity(true);}
        }
    }
    private static void clearDebris(ServerLevel level,Storm s){for(var id:s.debris.keySet()){var e=level.getEntity(id);if(e!=null)e.discard();}s.debris.clear();}
    @SubscribeEvent public static void recovered(EntityJoinLevelEvent event){
        if(!(event.getLevel() instanceof ServerLevel l)||!(event.getEntity() instanceof FallingBlockEntity piece))return;var tag=piece.getPersistentData();
        if(!tag.hasUUID(DEBRIS_OWNER)||!tag.hasUUID(DEBRIS_TOKEN))return;
        var map=WORLDS.get(l);var s=map==null?null:map.get(tag.getUUID(DEBRIS_OWNER));
        if(s==null||s.stopping||!s.token.equals(tag.getUUID(DEBRIS_TOKEN))||s.debris.size()>=96){event.setCanceled(true);piece.discard();return;}
        s.debris.put(piece.getUUID(),s.age);piece.dropItem=false;piece.setNoGravity(true);
    }
    private static WeatherSyncPacket snapshot(ServerLevel level){var map=WORLDS.get(level);return new WeatherSyncPacket(map==null?List.of():map.values().stream().map(Storm::snapshot).toList());}
    private static void broadcast(ServerLevel level){ModNetwork.CHANNEL.send(PacketDistributor.DIMENSION.with(level::dimension),snapshot(level));}
    private static void sync(Player p){if(p instanceof ServerPlayer s)ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(()->s),snapshot(s.serverLevel()));}
    private static void remove(Player p){for(var entry:WORLDS.entrySet()){var s=entry.getValue().remove(p.getUUID());if(s!=null){clearDebris(entry.getKey(),s);broadcast(entry.getKey());}}}
    @SubscribeEvent public static void joined(PlayerEvent.PlayerLoggedInEvent event){sync(event.getEntity());}
    @SubscribeEvent public static void changed(PlayerEvent.PlayerChangedDimensionEvent event){remove(event.getEntity());sync(event.getEntity());}
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event){remove(event.getEntity());}
    @SubscribeEvent public static void stopped(ServerStoppedEvent event){for(var entry:WORLDS.entrySet())for(var s:entry.getValue().values())clearDebris(entry.getKey(),s);WORLDS.clear();}
}
