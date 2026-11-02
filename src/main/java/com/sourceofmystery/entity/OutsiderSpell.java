package com.sourceofmystery.entity;

import com.sourceofmystery.guardian.GuardianShields;
import com.sourceofmystery.network.ModNetwork;
import com.sourceofmystery.network.ScreenEffectPacket;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.*;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.projectile.*;
import net.minecraft.world.level.*;
import net.minecraft.world.phys.*;
import net.minecraftforge.network.PacketDistributor;

/** Synced visuals and server-only hits. Large terrain changes are time-budgeted. */
public class OutsiderSpell extends Projectile {
    public static final int BOLT=0, PILLAR=1, METEOR=2, RINGS=3, AFTERMATH=4, SKY_SEAL=5;
    public static final int BLAST_RADIUS=100;
    private static final EntityDataAccessor<Integer> KIND=SynchedEntityData.defineId(OutsiderSpell.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<Long> START=SynchedEntityData.defineId(OutsiderSpell.class,EntityDataSerializers.LONG);
    private static final EntityDataAccessor<CompoundTag> TRAJECTORY=SynchedEntityData.defineId(OutsiderSpell.class,EntityDataSerializers.COMPOUND_TAG);
    private Vec3 impactPoint;
    private LivingEntity castSource;
    public OutsiderSpell(EntityType<? extends OutsiderSpell> type,Level level){super(type,level);setNoGravity(true);}
    @Override protected void defineSynchedData(){entityData.define(KIND,BOLT);entityData.define(START,0L);entityData.define(TRAJECTORY,new CompoundTag());}
    public int kind(){return entityData.get(KIND);}
    public float age(float partial){return Math.max(0,level().getGameTime()-entityData.get(START)+partial);}
    public boolean hasImpacted(){return kind()==AFTERMATH||isRemoved();}
    public void setImpactPoint(Vec3 point){impactPoint=point;}
    public void setTimedImpact(Vec3 point,int ticks){
        impactPoint=point;CompoundTag tag=entityData.get(TRAJECTORY).copy();
        tag.putInt("duration",ticks);tag.putDouble("ex",point.x);tag.putDouble("ey",point.y+OutsiderRitual.ORB_RADIUS);tag.putDouble("ez",point.z);
        entityData.set(TRAJECTORY,tag);
    }
    public static OutsiderSpell spawn(ServerLevel level,LivingEntity owner,int kind,Vec3 position,Vec3 velocity){
        OutsiderSpell spell=new OutsiderSpell(ModEntities.OUTSIDER_SPELL.get(),level);
        spell.castSource=owner;
        spell.setOwner(owner);spell.entityData.set(KIND,kind);spell.entityData.set(START,level.getGameTime());
        spell.setPos(position);spell.trajectory(position,velocity);if(!level.addFreshEntity(spell))spell.discard();return spell;
    }
    private void trajectory(Vec3 origin,Vec3 velocity){
        CompoundTag tag=new CompoundTag();tag.putDouble("x",origin.x);tag.putDouble("y",origin.y);tag.putDouble("z",origin.z);
        tag.putDouble("vx",velocity.x);tag.putDouble("vy",velocity.y);tag.putDouble("vz",velocity.z);
        entityData.set(TRAJECTORY,tag);super.setDeltaMovement(velocity);
    }
    public Vec3 trajectoryPosition(float partial){
        CompoundTag t=entityData.get(TRAJECTORY);if(t.isEmpty())return position();
        if(t.getInt("duration")>0){
            double u=Math.min(1,age(partial)/t.getInt("duration"));u*=u;
            Vec3 from=new Vec3(t.getDouble("x"),t.getDouble("y"),t.getDouble("z"));
            return from.lerp(new Vec3(t.getDouble("ex"),t.getDouble("ey"),t.getDouble("ez")),u);
        }
        double time=age(partial);return new Vec3(t.getDouble("x")+t.getDouble("vx")*time,
                t.getDouble("y")+t.getDouble("vy")*time,t.getDouble("z")+t.getDouble("vz")*time);
    }
    @Override public boolean ignoreExplosion(){return true;}
    @Override public boolean isPushable(){return false;}
    @Override public void push(double x,double y,double z){}
    @Override public void push(Entity entity){}
    @Override public void setDeltaMovement(Vec3 velocity){if(level().isClientSide)super.setDeltaMovement(velocity);}
    @Override public boolean hurt(DamageSource source,float amount){return false;}
    @Override public void tick(){
        super.tick();
        noCulling=kind()!=BOLT;
        if(level().isClientSide){setPos(trajectoryPosition(0));return;}
        // Terrain work survives the caster and this short-lived visual.
        if(kind()==AFTERMATH){if(age(0)>50)discard();return;}
        LivingEntity owner=getOwner() instanceof LivingEntity living?living:castSource;
        if(owner==null||(owner instanceof OutsiderEntity&&!owner.isAlive())){discard();return;}
        int age=(int)age(0);
        if(kind()==SKY_SEAL){if(age>=100)discard();return;}
        if(kind()==BOLT){
            // Collision and display use the same immutable launch trajectory.
            Vec3 next=trajectoryPosition(0);super.setDeltaMovement(next.subtract(position()));
            HitResult hit=ProjectileUtil.getHitResultOnMoveVector(this,this::canHitEntity);
            if(hit instanceof EntityHitResult eh){
                if(eh.getEntity() instanceof LivingEntity victim&&!GuardianShields.shieldedFromBoss(victim))victim.hurt(damage("outsider_bolt"),80);
                discard();return;
            }
            if(hit instanceof BlockHitResult bh&&hit.getType()==HitResult.Type.BLOCK){
                if(!GuardianShields.isProtected(level(),bh.getBlockPos()))level().explode(this,bh.getLocation().x,bh.getLocation().y,bh.getLocation().z,4,Level.ExplosionInteraction.MOB);
                discard();return;
            }
            setPos(next);if(age>160)discard();
        }else if(kind()==PILLAR){
            if(age>=20&&age<80&&(age-20)%20==0){
                AABB column=new AABB(getX()-.9,getY(),getZ()-.9,getX()+.9,level().getMaxBuildHeight(),getZ()+.9);
                for(LivingEntity victim:level().getEntitiesOfClass(LivingEntity.class,column,e->canAffect(owner,e))){
                    victim.hurt(damage("outsider_pillar"),100);
                }
            }
            if(age>=80)discard();
        }else if(kind()==RINGS){if(age>=(OutsiderRitual.IMPACT-OutsiderRitual.STAFF_END)*20)discard();}
        else if(kind()==METEOR){
            Vec3 next=trajectoryPosition(0);
            if(entityData.get(TRAJECTORY).getInt("duration")>0){
                if(age>=entityData.get(TRAJECTORY).getInt("duration"))impact(owner,impactPoint);
                else setPos(next);
                return;
            }
            Vec3 bottom=position().add(0,-OutsiderRitual.ORB_RADIUS,0),nextBottom=next.add(0,-OutsiderRitual.ORB_RADIUS,0);
            BlockHitResult hit=level().clip(new ClipContext(bottom,nextBottom,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,this));
            if(hit.getType()==HitResult.Type.BLOCK)impact(owner,hit.getLocation());
            else if(impactPoint!=null&&next.y-OutsiderRitual.ORB_RADIUS<=impactPoint.y)impact(owner,new Vec3(next.x,impactPoint.y,next.z));
            else {setPos(next);if(age>2400){com.sourceofmystery.SourceOfMystery.LOGGER.warn("Outsider meteor timed out at {}",position());discard();}}
        }
    }
    @Override protected boolean canHitEntity(Entity e){return super.canHitEntity(e)&&e!=getOwner()&&!(e instanceof OutsiderSpell)&&!GuardianShields.shieldedFromBoss(e);}
    private DamageSource damage(String key){return new DamageSource(level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
            .getHolderOrThrow(ResourceKey.create(Registries.DAMAGE_TYPE,new ResourceLocation("sourceofmystery",key))),this,getOwner());}
    private static boolean canAffect(LivingEntity owner,LivingEntity victim){
        return owner instanceof OutsiderEntity boss?boss.canAffect(victim)
                :!victim.getUUID().equals(owner.getUUID())&&victim.isAlive()&&!victim.isSpectator();
    }
    private void impact(LivingEntity owner,Vec3 point){
        setPos(point);entityData.set(START,level().getGameTime());trajectory(point,Vec3.ZERO);entityData.set(KIND,AFTERMATH);
        ServerLevel server=(ServerLevel)level();
        for(LivingEntity victim:server.getEntitiesOfClass(LivingEntity.class,new AABB(point,point).inflate(BLAST_RADIUS),
                e->canAffect(owner,e)&&e.position().distanceToSqr(point)<=BLAST_RADIUS*BLAST_RADIUS)){
            victim.hurt(damage("outsider_annihilation"),200);
        }
        for(var player:server.players())if(player.position().distanceToSqr(point)<160*160)
            ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(()->player),new ScreenEffectPacket(0,80,2.5f));
        server.playSound(null,point.x,point.y,point.z,SoundEvents.GENERIC_EXPLODE,SoundSource.HOSTILE,12,.55f);
        OutsiderBlastTasks.enqueue(server,owner,point);
    }
    @Override public boolean shouldRenderAtSqrDistance(double distance){return distance<(kind()==BOLT?96*96:384*384);}
    @Override protected void addAdditionalSaveData(CompoundTag tag){super.addAdditionalSaveData(tag);}
    @Override protected void readAdditionalSaveData(CompoundTag tag){super.readAdditionalSaveData(tag);discard();}
}
