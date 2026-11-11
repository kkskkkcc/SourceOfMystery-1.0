package com.sourceofmystery.staff;

import com.sourceofmystery.network.*;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.*;
import net.minecraft.world.Clearable;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.*;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.player.*;
import net.minecraftforge.event.level.*;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;
import java.util.*;

/** Server-owned swept sphere. Torii are visual particles and never placed blocks/entities. */
@Mod.EventBusSubscriber(modid="sourceofmystery")
public final class EasternRite {
    public static final int GATES=5,GATE_START=2,GATE_INTERVAL=8,GATE_RISE=18,MAX_CHARGE=20,ORB_CHARGE_START=34,ORB_FORM_START=40,LAUNCH=58;
    public static final double RADIUS=10,DAMAGE=1000;
    public static final int RANGE=255;
    private static final String CARRY_GRAVITY="sourceofmystery_eastern_original_gravity";
    private static final Map<ServerLevel,Map<UUID,Rite>> WORLDS=new HashMap<>();
    private static final Map<Entity,Carried> CAPTURED=new IdentityHashMap<>();
    public static final class Rite {
        public final UUID token=UUID.randomUUID(),owner;
        public final Vec3 origin,forward,up;public final float yaw,pitch;public final double[] bases=new double[GATES];
        public final double speed;public final int range;public int age,stoppedAt=-1;public double travel;private boolean swept;
        final Map<UUID,Carried> carried=new LinkedHashMap<>();
        final Set<UUID> hit=new HashSet<>();final LongArrayFIFOQueue pending=new LongArrayFIFOQueue();
        Rite(ServerPlayer p){owner=p.getUUID();origin=p.position();yaw=p.getYRot();pitch=p.getXRot();forward=Vec3.directionFromRotation(pitch,yaw).normalize();up=forward.cross(new Vec3(Math.cos(Math.toRadians(yaw)),0,Math.sin(Math.toRadians(yaw)))).normalize();speed=StaffConfig.EASTERN_SPEED.get();range=RANGE;
            for(int i=0;i<GATES;i++){var at=BlockPos.containing(origin.add(forward.scale(gateDistance(i))));bases[i]=p.serverLevel().hasChunkAt(at)?p.serverLevel().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,at.getX(),at.getZ()):origin.y;}}
        public Vec3 ball(){return origin.add(forward.scale(16+travel)).add(up.scale(8));}
        public int end(){return stoppedAt<0?LAUNCH+(int)Math.ceil(range/speed)+30:stoppedAt+30;}
        EasternRitePacket.View snapshot(){return new EasternRitePacket.View(token,owner,origin,yaw,pitch,age,travel,speed,range,bases.clone(),stoppedAt,carried.values().stream().map(c->new EasternRitePacket.Passenger(c.entity.getId(),c.entity.getUUID(),c.offset)).toList());}
    }
    private static final class Carried {
        final Entity entity;final Vec3 offset;final boolean gravity;
        Carried(Entity entity,Vec3 ball){this.entity=entity;offset=entity.position().subtract(ball);gravity=entity.isNoGravity();entity.getPersistentData().putBoolean(CARRY_GRAVITY,gravity);entity.setNoGravity(true);if(entity.isPassenger())entity.stopRiding();}
    }
    private EasternRite(){}
    public static double gateDistance(int index){return 34+index*48;}
    public static Rite rite(Player p){if(!(p.level() instanceof ServerLevel l))return null;var w=WORLDS.get(l);return w==null?null:w.get(p.getUUID());}
    public static boolean constrained(Entity e){return e.level().isClientSide?com.sourceofmystery.client.EasternVisuals.constrained(e):CAPTURED.containsKey(e);}
    public static boolean mayCast(ServerPlayer p){return rite(p)==null&&!StaffDomains.active(p)&&!com.sourceofmystery.cinematic.DivineCinematic.active(p)&&!com.sourceofmystery.ritual.OfferingPortalRitual.active(p)&&!p.isSpectator()&&p.isAlive()&&WORLDS.getOrDefault(p.serverLevel(),Map.of()).size()<4;}
    public static boolean start(ServerPlayer p){if(!mayCast(p))return StaffCasting.fail(p,"eastern_busy");var r=new Rite(p);WORLDS.computeIfAbsent(p.serverLevel(),l->new LinkedHashMap<>()).put(p.getUUID(),r);broadcast(p.serverLevel());return true;}
    private static void broadcast(ServerLevel l){var views=WORLDS.getOrDefault(l,Map.of()).values().stream().map(Rite::snapshot).toList();ModNetwork.CHANNEL.send(PacketDistributor.DIMENSION.with(l::dimension),new EasternRitePacket(l.dimension().location(),views));}
    @SubscribeEvent public static void tick(TickEvent.LevelTickEvent e){
        if(!(e.level instanceof ServerLevel l))return;var world=WORLDS.get(l);if(world==null)return;
        if(e.phase==TickEvent.Phase.END){for(var r:world.values())pinCarried(l,r);return;}
        if(TimeStop.active(l))return;
        boolean changed=false;long deadline=System.nanoTime()+3_000_000L;int budget=StaffConfig.EASTERN_BLOCKS_PER_TICK.get();
        var it=world.values().iterator();while(it.hasNext()){
            var r=it.next();var p=l.getServer().getPlayerList().getPlayer(r.owner);
            if(p==null||p.level()!=l||!p.isAlive()){releaseAll(r);it.remove();changed=true;continue;}r.age++;
            for(int i=0;i<GATES;i++)if(r.age==GATE_START+i*GATE_INTERVAL)StaffSounds.eastern(l,r.origin.add(r.forward.scale(gateDistance(i))),"gate_rise",1.8f,.82f+i*.055f);
            if(r.age==ORB_CHARGE_START)StaffSounds.eastern(l,p.position(),"orb_charge",1.2f,1);
            if(r.age==LAUNCH)StaffSounds.eastern(l,p.position(),"orb_launch",1.8f,1);
            if(r.age>=LAUNCH&&r.travel<r.range&&r.stoppedAt<0){
                Vec3 before=r.ball();double step=Math.min(r.speed,r.range-r.travel);Vec3 candidate=before.add(r.forward.scale(step));
                double fraction=blockingFraction(l,before,candidate);r.travel+=step*(fraction<1?Math.max(0,fraction-1e-6):1);Vec3 after=r.ball();
                damage(l,p,r,before,after);
                if(StaffConfig.EASTERN_TERRAIN.get())enqueue(l,r,before,after,!r.swept);r.swept=true;
                pinCarried(l,r);
                if(fraction<1||r.travel>=r.range-1e-5){r.travel=Math.min(r.range,r.travel);stopBall(r);changed=true;}
            }
            while(!r.pending.isEmpty()&&budget>0&&System.nanoTime()<deadline){if(erase(l,p,BlockPos.of(r.pending.dequeueLong()))&&r.stoppedAt<0){stopBall(r);r.pending.clear();changed=true;}budget--;}
            if(r.age>=r.end()&&r.pending.isEmpty()){releaseAll(r);it.remove();changed=true;}
        }
        if(changed||l.getGameTime()%5==0)broadcast(l);
        if(world.isEmpty())WORLDS.remove(l);
    }
    private static void damage(ServerLevel l,ServerPlayer p,Rite r,Vec3 a,Vec3 b){
        for(var entity:l.getEntities(p,new AABB(a,b).inflate(RADIUS+1),x->x.isAlive()&&!x.isSpectator()&&!(x instanceof net.minecraft.world.entity.Marker)&&!r.hit.contains(x.getUUID()))){
            if(entity instanceof Player other&&!p.canHarmPlayer(other)||entity.hasPassenger(p))continue;
            if(!sweptHits(entity.getBoundingBox(),a,b,RADIUS))continue;r.hit.add(entity.getUUID());entity.hurt(l.damageSources().indirectMagic(p,p),(float)DAMAGE);
            if(!entity.isRemoved()&&!CAPTURED.containsKey(entity)&&r.carried.size()<256){var carry=new Carried(entity,b);r.carried.put(entity.getUUID(),carry);CAPTURED.put(entity,carry);}
        }
    }
    private static void pinCarried(ServerLevel level,Rite r){
        var it=r.carried.values().iterator();while(it.hasNext()){var c=it.next();var e=c.entity;if(e.isRemoved()||e.level()!=level){release(c);it.remove();continue;}var pos=r.ball().add(c.offset);e.setDeltaMovement(Vec3.ZERO);e.fallDistance=0;e.setPos(pos);}
    }
    private static void release(Carried c){var e=c.entity;CAPTURED.remove(e);e.setNoGravity(c.gravity);e.getPersistentData().remove(CARRY_GRAVITY);e.setDeltaMovement(Vec3.ZERO);e.fallDistance=0;if(e instanceof ServerPlayer p&&!p.isRemoved())p.connection.teleport(p.getX(),p.getY(),p.getZ(),p.getYRot(),p.getXRot());}
    private static void releaseAll(Rite r){for(var c:r.carried.values())release(c);r.carried.clear();}
    private static void stopBall(Rite r){if(r.stoppedAt>=0)return;r.stoppedAt=r.age;releaseAll(r);}
    private static double blockingFraction(ServerLevel level,Vec3 a,Vec3 b){
        double fraction=1;int minX=(int)Math.floor(Math.min(a.x,b.x)-RADIUS),maxX=(int)Math.floor(Math.max(a.x,b.x)+RADIUS),minZ=(int)Math.floor(Math.min(a.z,b.z)-RADIUS),maxZ=(int)Math.floor(Math.max(a.z,b.z)+RADIUS);
        for(int x=minX;x<=maxX;x++)for(int z=minZ;z<=maxZ;z++)for(int y=Math.max(level.getMinBuildHeight(),(int)Math.floor(Math.min(a.y,b.y)-RADIUS));y<=Math.min(level.getMaxBuildHeight()-1,(int)Math.floor(Math.max(a.y,b.y)+RADIUS));y++){
            var pos=new BlockPos(x,y,z);if(!level.hasChunkAt(pos))continue;var state=level.getBlockState(pos);if(state.isAir()||state.getDestroySpeed(level,pos)>=0)continue;var box=new AABB(pos);if(!sweptHits(box,a,b,RADIUS))continue;
            double lo=0,hi=fraction;for(int i=0;i<14;i++){double mid=(lo+hi)/2;if(sweptHits(box,a,a.lerp(b,mid),RADIUS))hi=mid;else lo=mid;}fraction=Math.min(fraction,lo);
        }return fraction;
    }
    public static double distanceSquared(Vec3 point,Vec3 a,Vec3 b){Vec3 d=b.subtract(a);double n=d.lengthSqr(),t=n<1e-12?0:Math.max(0,Math.min(1,point.subtract(a).dot(d)/n));return point.distanceToSqr(a.add(d.scale(t)));}
    /** Exact piecewise-quadratic segment/AABB distance, including large and thin targets. */
    public static boolean sweptHits(AABB box,Vec3 a,Vec3 b,double radius){
        double[] start={a.x,a.y,a.z},d={b.x-a.x,b.y-a.y,b.z-a.z},lo={box.minX,box.minY,box.minZ},hi={box.maxX,box.maxY,box.maxZ};
        var cuts=new ArrayList<Double>();cuts.add(0d);cuts.add(1d);for(int k=0;k<3;k++)if(Math.abs(d[k])>1e-12)for(double edge:new double[]{lo[k],hi[k]}){double t=(edge-start[k])/d[k];if(t>0&&t<1)cuts.add(t);}cuts.sort(Double::compare);
        for(int i=0;i<cuts.size()-1;i++){double left=cuts.get(i),right=cuts.get(i+1),mid=(left+right)/2,A=0,B=0;
            for(int k=0;k<3;k++){double q=start[k]+mid*d[k],edge=q<lo[k]?lo[k]:q>hi[k]?hi[k]:Double.NaN;if(!Double.isNaN(edge)){A+=d[k]*d[k];B+=d[k]*(start[k]-edge);}}
            double t=A<1e-12?mid:Math.max(left,Math.min(right,-B/A)),sum=0;for(int k=0;k<3;k++){double q=start[k]+t*d[k],v=q<lo[k]?q-lo[k]:q>hi[k]?q-hi[k]:0;sum+=v*v;}if(sum<=radius*radius)return true;
        }return false;
    }
    private static void enqueue(ServerLevel l,Rite r,Vec3 a,Vec3 b,boolean initial){
        // Only the new spherical cap is queued after the first sphere: no repeated
        // erosion of the same volume, no block drops, and no synchronous chunk loads.
        int minX=(int)Math.floor(Math.min(a.x,b.x)-RADIUS),maxX=(int)Math.floor(Math.max(a.x,b.x)+RADIUS),minZ=(int)Math.floor(Math.min(a.z,b.z)-RADIUS),maxZ=(int)Math.floor(Math.max(a.z,b.z)+RADIUS);
        int low=Math.max(l.getMinBuildHeight(),(int)Math.floor(Math.min(a.y,b.y)-RADIUS)),high=Math.min(l.getMaxBuildHeight()-1,(int)Math.floor(Math.max(a.y,b.y)+RADIUS));
        for(int x=minX;x<=maxX;x++)for(int z=minZ;z<=maxZ;z++)for(int y=low;y<=high;y++){
            var point=new Vec3(x+.5,y+.5,z+.5);if(!initial&&point.distanceToSqr(a)<=RADIUS*RADIUS||distanceSquared(point,a,b)>RADIUS*RADIUS)continue;
            var pos=new BlockPos(x,y,z);if(l.hasChunkAt(pos)&&!l.getBlockState(pos).isAir())r.pending.enqueue(pos.asLong());
        }
    }
    private static boolean erase(ServerLevel l,ServerPlayer p,BlockPos pos){
        if(!l.hasChunkAt(pos))return false;if(!l.getWorldBorder().isWithinBounds(pos)||!l.mayInteract(p,pos))return true;var state=l.getBlockState(pos);
        if(state.isAir())return false;if(state.getDestroySpeed(l,pos)<0)return true;
        var event=new BlockEvent.BreakEvent(l,pos,state,p);boolean canceled=MinecraftForge.EVENT_BUS.post(event);
        if(canceled)return true;if(!l.getBlockState(pos).equals(state))return false;
        Clearable.tryClear(l.getBlockEntity(pos));l.setBlock(pos,net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),18);return false;
    }
    @SubscribeEvent public static void attack(LivingAttackEvent e){if(CAPTURED.containsKey(e.getEntity())&&e.getSource().is(net.minecraft.world.damagesource.DamageTypes.IN_WALL))e.setCanceled(true);}
    @SubscribeEvent public static void joined(PlayerEvent.PlayerLoggedInEvent e){sync(e.getEntity());}
    @SubscribeEvent public static void changed(PlayerEvent.PlayerChangedDimensionEvent e){sync(e.getEntity());}
    private static void sync(Player p){if(p instanceof ServerPlayer s)ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(()->s),new EasternRitePacket(s.level().dimension().location(),WORLDS.getOrDefault(s.serverLevel(),Map.of()).values().stream().map(Rite::snapshot).toList()));}
    @SubscribeEvent public static void recover(net.minecraftforge.event.entity.EntityJoinLevelEvent e){if(e.getLevel().isClientSide)return;var entity=e.getEntity();var data=entity.getPersistentData();if(data.contains(CARRY_GRAVITY)&&!CAPTURED.containsKey(entity)){entity.setNoGravity(data.getBoolean(CARRY_GRAVITY));data.remove(CARRY_GRAVITY);entity.setDeltaMovement(Vec3.ZERO);entity.fallDistance=0;}}
    @SubscribeEvent public static void unload(LevelEvent.Unload e){var w=WORLDS.remove(e.getLevel());if(w!=null)for(var r:w.values())releaseAll(r);}
    @SubscribeEvent public static void stop(ServerStoppedEvent e){for(var w:WORLDS.values())for(var r:w.values())releaseAll(r);WORLDS.clear();CAPTURED.clear();}
}
