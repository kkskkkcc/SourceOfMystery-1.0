package com.sourceofmystery.staff;

import com.sourceofmystery.network.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.*;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.*;
import net.minecraft.world.effect.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.*;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;
import java.util.*;

/** Server-owned five-minute fields. Rendering never changes blocks, health or inventory. */
@Mod.EventBusSubscriber(modid="sourceofmystery")
public final class StaffDomains {
    public static final int CINEMATIC_TICKS=200;
    private static final int ICE=1,BIND=2,PULL=4,RISE=8,CINEMATIC=16;
    private static final TagKey<net.minecraft.world.level.block.Block> ABSORBABLE=TagKey.create(Registries.BLOCK,id("domain_absorbable"));
    private static final Map<ServerLevel,World> WORLDS=new IdentityHashMap<>();
    private StaffDomains(){}
    private static ResourceLocation id(String s){return new ResourceLocation("sourceofmystery",s);}
    public static final class Field {
        public final UUID token=UUID.randomUUID();public final UUID owner;public final DomainType type;
        public final Vec3 anchor;public Vec3 center;public float yaw;public int age,remaining;
        final Set<UUID> debris=new HashSet<>();int snowCursor;
        Field(ServerPlayer player,DomainType type){owner=player.getUUID();this.type=type;anchor=center=player.position();yaw=player.getYRot();remaining=StaffConfig.DOMAIN_DURATION.get();}
        public boolean contains(Vec3 point){
            if(point.distanceToSqr(center)>type.radius*type.radius)return false;
            if(type!=DomainType.EXECUTION)return true;
            Vec3 delta=point.subtract(center);
            return delta.x*(-Math.sin(yaw*Math.PI/180))+delta.z*Math.cos(yaw*Math.PI/180)>=-.5;
        }
        DomainSyncPacket.Field snapshot(){return new DomainSyncPacket.Field(token,owner,type.ordinal(),center,yaw,age,remaining);}
    }
    private static final class World {
        final Map<UUID,Field> fields=new LinkedHashMap<>();final Map<UUID,Affected> affected=new HashMap<>();
    }
    private static final class Affected {
        final Entity entity;Vec3 anchor;int mask;Vec3 pull,lastPull;
        boolean gravity;boolean oldGlow;MobEffectInstance oldLevitation,oldGlowing;int gravityAt;
        Affected(Entity e){entity=e;anchor=e.position();}
    }
    private record Wanted(int mask,Vec3 pull){}
    private static World world(ServerLevel level){return WORLDS.computeIfAbsent(level,l->new World());}
    public static Field field(Player player){if(!(player.level() instanceof ServerLevel level))return null;var w=WORLDS.get(level);return w==null?null:w.fields.get(player.getUUID());}
    public static boolean active(Player player){return field(player)!=null;}
    public static boolean constrained(Entity e){
        if(e.level().isClientSide)return com.sourceofmystery.client.DomainVisuals.constrained(e);
        if(!(e.level() instanceof ServerLevel level))return false;var w=WORLDS.get(level);
        var a=w==null?null:w.affected.get(e.getUUID());return a!=null&&(a.mask&(ICE|BIND|PULL|CINEMATIC))!=0;
    }
    public static void cycle(ServerPlayer p,ItemStack stack){
        if(TimeStop.frozen(p)||p.isUsingItem())return;
        var selected=DomainType.values()[(DomainType.selected(stack).ordinal()+1)%DomainType.values().length];
        stack.getOrCreateTag().putString(DomainType.KEY,selected.id);p.getInventory().setChanged();p.containerMenu.broadcastChanges();
        p.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.sourceofmystery.domain_selected",selected.title()),true);
    }
    public static boolean start(ServerPlayer p,DomainType type){
        if(active(p))return StaffCasting.fail(p,"domain_busy");
        if(p.isSpectator()||!p.isAlive()||com.sourceofmystery.cinematic.DivineCinematic.active(p)||com.sourceofmystery.ritual.OfferingPortalRitual.active(p))return StaffCasting.fail(p,"domain_busy");
        var w=world(p.serverLevel());if(w.fields.size()>=256)return StaffCasting.fail(p,"domain_busy");
        var f=new Field(p,type);w.fields.put(p.getUUID(),f);
        p.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.sourceofmystery.domain_started",type.title()),true);
        broadcast(p.serverLevel());return true;
    }
    public static void stop(ServerPlayer p,UUID token){var f=field(p);if(f!=null&&f.token.equals(token))stop(p);}
    public static void stop(ServerPlayer p){
        var w=WORLDS.get(p.serverLevel());if(w==null)return;var f=w.fields.remove(p.getUUID());if(f==null)return;
        clearDebris(p.serverLevel(),f);reconcile(p.serverLevel(),w);broadcast(p.serverLevel());
    }
    private static boolean immune(Entity e,ServerPlayer owner){return e==owner||e.hasPassenger(owner)||e.isSpectator()||e instanceof net.minecraft.world.entity.Marker;}
    private static void merge(Map<Entity,Wanted> desired,Entity e,int flag,Vec3 pull){
        var old=desired.get(e);desired.put(e,new Wanted((old==null?0:old.mask)|flag,pull!=null?pull:old==null?null:old.pull));
    }
    @SubscribeEvent public static void tick(TickEvent.LevelTickEvent event){
        if(!(event.level instanceof ServerLevel level))return;var w=WORLDS.get(level);if(w==null)return;
        if(event.phase==TickEvent.Phase.END){for(var a:w.affected.values())pin(level,a,false);return;}
        if(TimeStop.active(level))return;
        boolean changed=false;var iterator=w.fields.values().iterator();
        while(iterator.hasNext()){
            var f=iterator.next();var owner=level.getServer().getPlayerList().getPlayer(f.owner);
            if(owner==null||!owner.isAlive()||owner.level()!=level||f.remaining--<=0){clearDebris(level,f);iterator.remove();changed=true;continue;}
            if(f.type.follows)f.center=owner.position();f.yaw=owner.getYRot();f.age++;
            if(f.type==DomainType.FROST)snow(level,f);
            if(f.type==DomainType.DARKNESS&&f.age%4==0)absorbBlock(level,owner,f);
            if(f.age%20==0)damage(level,owner,f);
        }
        reconcile(level,w);
        for(var a:w.affected.values())pin(level,a,true);
        if(changed||level.getServer().getTickCount()%10==0)broadcast(level);
        if(w.fields.isEmpty()&&w.affected.isEmpty())WORLDS.remove(level);
    }
    private static void reconcile(ServerLevel level,World w){
        Map<Entity,Wanted> desired=new LinkedHashMap<>();
        for(var f:w.fields.values()){
            var owner=level.getServer().getPlayerList().getPlayer(f.owner);if(owner==null)continue;
            if(f.type==DomainType.EXECUTION&&f.age<CINEMATIC_TICKS)merge(desired,owner,CINEMATIC,null);
            int mask=switch(f.type){case FROST->ICE;case BINDING->BIND;case DARKNESS->PULL;case GRAVITY->RISE;case EXECUTION->0;};
            if(mask==0)continue;
            var area=new AABB(f.center,f.center).inflate(f.type.radius);
            for(var e:level.getEntities(owner,area,e->e.isAlive()&&!immune(e,owner)))if(f.contains(e.getBoundingBox().getCenter()))
                merge(desired,e,mask,mask==PULL?f.center.add(0,20,0):null);
        }
        var iterator=w.affected.entrySet().iterator();
        while(iterator.hasNext()){
            var a=iterator.next().getValue();
            if(!desired.containsKey(a.entity)||!a.entity.isAlive()){release(level,a);iterator.remove();}
        }
        for(var entry:desired.entrySet()){
            var e=entry.getKey();var a=w.affected.computeIfAbsent(e.getUUID(),u->new Affected(e));
            if((a.mask&(ICE|BIND|CINEMATIC))==0&&(entry.getValue().mask&(ICE|BIND|CINEMATIC))!=0)a.anchor=e.position();
            a.mask=entry.getValue().mask;a.pull=entry.getValue().pull;
            if(e instanceof LivingEntity living){
                status(living,DomainEffects.EXTREME_FREEZE.get(),(a.mask&ICE)!=0);
                status(living,DomainEffects.DOMAIN_BINDING.get(),(a.mask&BIND)!=0);
            }
            if((a.mask&RISE)!=0)gravity(level,a);else if(a.gravity)restoreGravity(level,a);
        }
    }
    private static void status(LivingEntity e,MobEffect effect,boolean present){
        var old=e.getEffect(effect);
        if(!present){if(old!=null)e.removeEffect(effect);}
        else if(old==null||old.getDuration()<25)e.addEffect(new MobEffectInstance(effect,45,0,true,false,true));
    }
    private static void gravity(ServerLevel level,Affected a){
        if(!a.gravity){
            a.gravity=true;a.gravityAt=level.getServer().getTickCount();a.oldGlow=a.entity.isCurrentlyGlowing();if(!(a.entity instanceof LivingEntity))a.entity.getPersistentData().putBoolean(DomainEntityRecovery.GLOW,a.oldGlow);
            if(a.entity instanceof LivingEntity e){a.oldLevitation=copy(e.getEffect(MobEffects.LEVITATION));a.oldGlowing=copy(e.getEffect(MobEffects.GLOWING));}
        }
        if(a.entity instanceof LivingEntity e){
            refresh(e,MobEffects.LEVITATION,0);refresh(e,MobEffects.GLOWING,0);
            // Vanilla AI movement does not travel for NoAI mobs and armor stands.
            if(e instanceof net.minecraft.world.entity.Mob mob&&mob.isNoAi()||e instanceof net.minecraft.world.entity.decoration.ArmorStand){e.setDeltaMovement(Vec3.ZERO);e.move(MoverType.SELF,new Vec3(0,.05,0));e.hasImpulse=true;}
        }else{a.entity.setGlowingTag(true);a.entity.setDeltaMovement(0,.05,0);
            if((a.mask&(ICE|BIND|PULL))==0)a.entity.move(MoverType.SELF,new Vec3(0,.05,0));a.entity.hasImpulse=true;}
    }
    private static MobEffectInstance copy(MobEffectInstance e){return e==null?null:new MobEffectInstance(e);}
    private static void refresh(LivingEntity e,MobEffect effect,int amplifier){var old=e.getEffect(effect);if(old==null||old.getDuration()<25)e.addEffect(new MobEffectInstance(effect,45,amplifier,true,false,true));}
    private static void restoreEffect(LivingEntity e,MobEffect effect,MobEffectInstance old,int elapsed){
        var current=e.getEffect(effect);
        if(current!=null&&current.getAmplifier()==0&&current.getDuration()<=45){
            e.removeEffect(effect);
            if(old!=null&&(old.isInfiniteDuration()||old.getDuration()>elapsed))e.addEffect(new MobEffectInstance(effect,old.isInfiniteDuration()?-1:old.getDuration()-elapsed,old.getAmplifier(),old.isAmbient(),old.isVisible(),old.showIcon()));
        }
    }
    private static void restoreGravity(ServerLevel level,Affected a){
        if(a.entity instanceof LivingEntity e){int elapsed=level.getServer().getTickCount()-a.gravityAt;restoreEffect(e,MobEffects.LEVITATION,a.oldLevitation,elapsed);restoreEffect(e,MobEffects.GLOWING,a.oldGlowing,elapsed);}
        else {a.entity.setGlowingTag(a.oldGlow);a.entity.getPersistentData().remove(DomainEntityRecovery.GLOW);}
        a.gravity=false;
    }
    private static void release(ServerLevel level,Affected a){
        if(a.entity instanceof LivingEntity e){e.removeEffect(DomainEffects.EXTREME_FREEZE.get());e.removeEffect(DomainEffects.DOMAIN_BINDING.get());}
        if(a.gravity)restoreGravity(level,a);a.entity.setDeltaMovement(Vec3.ZERO);a.entity.fallDistance=0;
    }
    private static void pin(ServerLevel level,Affected a,boolean advance){
        var e=a.entity;if(!e.isAlive()||e.level()!=level)return;
        if((a.mask&(ICE|BIND|PULL|CINEMATIC))==0)return;
        if(e.isPassenger())e.stopRiding();e.setDeltaMovement(Vec3.ZERO);e.fallDistance=0;
        Vec3 target=a.anchor;
        if((a.mask&(ICE|BIND|CINEMATIC))==0){
            if(!advance){if(a.lastPull!=null)e.setPos(a.lastPull);return;}
            Vec3 delta=a.pull.subtract(e.getBoundingBox().getCenter());
            target=e.position().add(delta.normalize().scale(Math.min(.25,delta.length())));
            if(delta.length()<1.25){
                if(e instanceof FallingBlockEntity || !(e instanceof LivingEntity)&&!(e instanceof Player)){e.discard();return;}
                target=a.pull.subtract(0,e.getBbHeight()*.5,0);
            }
        }
        if((a.mask&(ICE|BIND|CINEMATIC))==0)a.lastPull=target;
        if(e.position().distanceToSqr(target)>1e-8)e.setPos(target);
        if(e instanceof ServerPlayer p&&advance&&level.getServer().getTickCount()%4==0)p.connection.teleport(target.x,target.y,target.z,p.getYRot(),p.getXRot());
        if(e instanceof FallingBlockEntity fall){fall.setOnGround(false);fall.setNoGravity(true);fall.dropItem=false;}
        e.hasImpulse=true;
    }
    private static DamageSource source(ServerLevel level,ServerPlayer owner,String key){return new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(net.minecraft.resources.ResourceKey.create(Registries.DAMAGE_TYPE,id(key))),owner);}
    private static void damage(ServerLevel level,ServerPlayer owner,Field f){
        if(f.type==DomainType.GRAVITY||f.type==DomainType.BINDING||f.type==DomainType.EXECUTION&&f.age<48)return;
        String key=switch(f.type){case FROST->"domain_frost";case DARKNESS->"domain_void";default->"domain_slash";};
        float amount=f.type==DomainType.FROST?3:f.type==DomainType.DARKNESS?10:30;
        var area=new AABB(f.center,f.center).inflate(f.type.radius);
        for(var e:level.getEntitiesOfClass(LivingEntity.class,area,e->e.isAlive()&&!immune(e,owner)))if(f.contains(e.getBoundingBox().getCenter())){
            e.hurt(source(level,owner,key),amount);
            if(f.type==DomainType.EXECUTION&&e.isAlive())e.hurt(source(level,owner,"domain_execution"),e.getMaxHealth()*.01f);
        }
    }
    private static void snow(ServerLevel level,Field f){
        // An expanding low-discrepancy sweep: loaded columns only, capped at 12 per tick.
        for(int i=0;i<12;i++){
            int n=f.snowCursor++;double angle=n*2.399963229728653,r=Math.sqrt((n%8192+.5)/8192d)*f.type.radius;
            int x=(int)Math.floor(f.center.x+Math.cos(angle)*r),z=(int)Math.floor(f.center.z+Math.sin(angle)*r);
            if(!level.hasChunk(x>>4,z>>4))continue;
            var top=level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,new BlockPos(x,0,z));
            if(Math.abs(top.getY()-f.center.y)>f.type.radius||!level.getWorldBorder().isWithinBounds(top))continue;
            var state=level.getBlockState(top);var below=level.getBlockState(top.below());
            if(below.is(Blocks.SNOW)&&below.getValue(SnowLayerBlock.LAYERS)<8)level.setBlockAndUpdate(top.below(),below.cycle(SnowLayerBlock.LAYERS));
            else if(state.is(Blocks.SNOW)&&state.getValue(SnowLayerBlock.LAYERS)<8)level.setBlockAndUpdate(top,state.cycle(SnowLayerBlock.LAYERS));
            else if(state.isAir()&&!below.hasBlockEntity()&&Blocks.SNOW.defaultBlockState().canSurvive(level,top))level.setBlockAndUpdate(top,Blocks.SNOW.defaultBlockState());
        }
    }
    private static void absorbBlock(ServerLevel level,ServerPlayer owner,Field f){
        f.debris.removeIf(uuid->level.getEntity(uuid)==null);
        if(f.debris.size()>=48)return;
        double angle=level.random.nextDouble()*Math.PI*2,r=8+Math.sqrt(level.random.nextDouble())*90;
        int x=(int)Math.floor(f.center.x+Math.cos(angle)*r),z=(int)Math.floor(f.center.z+Math.sin(angle)*r);
        if(!level.hasChunk(x>>4,z>>4))return;
        BlockPos pos=level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,new BlockPos(x,0,z)).below();
        var state=level.getBlockState(pos);
        if(!f.contains(Vec3.atCenterOf(pos))||state.hasBlockEntity()||!state.is(ABSORBABLE)||state.getDestroySpeed(level,pos)<0)return;
        if(!level.getWorldBorder().isWithinBounds(pos)||MinecraftForge.EVENT_BUS.post(new BlockEvent.BreakEvent(level,pos,state,owner)))return;
        var debris=FallingBlockEntity.fall(level,pos,state);debris.dropItem=false;debris.setNoGravity(true);debris.setPos(debris.position().add(0,.15,0));f.debris.add(debris.getUUID());
        f.debris.removeIf(uuid->level.getEntity(uuid)==null);
    }
    private static void clearDebris(ServerLevel level,Field f){for(UUID uuid:f.debris){var e=level.getEntity(uuid);if(e!=null)e.discard();}f.debris.clear();}
    private static DomainSyncPacket snapshot(ServerLevel level){var w=WORLDS.get(level);return new DomainSyncPacket(w==null?List.of():w.fields.values().stream().map(Field::snapshot).toList());}
    private static void broadcast(ServerLevel level){ModNetwork.CHANNEL.send(PacketDistributor.DIMENSION.with(level::dimension),snapshot(level));}
    private static void sync(Player p){if(p instanceof ServerPlayer s)ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(()->s),snapshot(s.serverLevel()));}
    @SubscribeEvent public static void joined(PlayerEvent.PlayerLoggedInEvent e){sync(e.getEntity());}
    @SubscribeEvent public static void changed(PlayerEvent.PlayerChangedDimensionEvent e){
        for(var level:List.copyOf(WORLDS.keySet())){var w=WORLDS.get(level);var f=w.fields.remove(e.getEntity().getUUID());if(f!=null){clearDebris(level,f);reconcile(level,w);broadcast(level);}}sync(e.getEntity());
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent e){if(e.getEntity() instanceof ServerPlayer p)stop(p);}
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void protect(LivingAttackEvent e){if(e.getEntity() instanceof Player p){var f=field(p);if(f!=null&&f.type==DomainType.EXECUTION&&f.age<CINEMATIC_TICKS)e.setCanceled(true);}}
    @SubscribeEvent public static void stopped(ServerStoppedEvent e){for(var entry:WORLDS.entrySet()){for(var a:entry.getValue().affected.values())release(entry.getKey(),a);for(var f:entry.getValue().fields.values())clearDebris(entry.getKey(),f);}WORLDS.clear();}
}
