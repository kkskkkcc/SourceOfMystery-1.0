package com.sourceofmystery.entity;

import com.sourceofmystery.guardian.GuardianShields;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.*;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.*;
import net.minecraft.server.level.*;
import net.minecraft.util.Mth;
import net.minecraft.world.*;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.*;
import net.minecraft.world.phys.*;
import javax.annotation.Nullable;
import java.util.Comparator;
import java.util.ArrayList;
import java.util.List;
import org.joml.Vector3f;
import static com.sourceofmystery.entity.OutsiderTactics.*;

/** Manually spawned aerial boss. All motion and skill events use one server clock. */
public class OutsiderEntity extends PathfinderMob {
    public static final double OUTSIDER_MAX_HEALTH = 7500.0;
    public static final float MODEL_SCALE = .147F;
    public static final int AIR=0, DESCEND=1, LAND=2, GROUND=3, TAKEOFF=4,
            GRASP=5, SILENCE=6, METEOR=7, METEOR_PREP=8;
    private static final float FLINCH_DAMAGE=120, FLINCH_RISE=2.5f;
    private static final int FLINCH_COOLDOWN=40, FLINCH_TICKS=10;
    private static final double FLINCH_PUSH=.32, FLINCH_LIFT=.06;
    public static final float FLINCH_PIVOT=.55f, FLINCH_LEAN=14;
    private static final double ORBIT_MIN=9, ORBIT_MAX=13, ORBIT_SPEED=.012, CLOSE_RANGE=6,
            CLOSE_HOVER=5.5, HOVER_HEIGHT=3.2;
    private static final int ORBIT_CHANGE_MIN=100, ORBIT_CHANGE_RANDOM=100;
    private static final double BLINK_RANGE=4, BLINK_HEIGHT=4, BLINK_MIN=8, BLINK_MAX=12,
            BLINK_SPREAD_XZ=.6, BLINK_SPREAD_Y=1.2, BLINK_PARTICLE_SPEED=.05;
    private static final int BLINK_TRIGGER=40, BLINK_DECAY=2, BLINK_ATTEMPTS=12, BLINK_COOLDOWN=160,
            BLINK_RETRY=40, BLINK_SKILL_DELAY=20, BLINK_PARTICLES=60;
    private static final float BLINK_VOLUME=1.6f, BLINK_PITCH=.6f;
    private static final double GRASP_FAR_RANGE=18, GRASP_FAR_WEIGHT=3, GRASP_WEIGHT=1.5,
            SILENCE_OPTIMAL_MIN=8, SILENCE_OPTIMAL_MAX=22, SILENCE_OPTIMAL_WEIGHT=3,
            SILENCE_WEIGHT=1, METEOR_WEIGHT=1.2;
    private static final float METEOR_HEALTH_GATE=.70f, PHASE_TWO_HEALTH=.40f, PHASE_TWO_COOLDOWN=.7f;
    private static final int PHASE_ONE=1, PHASE_TWO=2, PHASE_TWO_METEOR_COOLDOWN=90*20,
            SKILL_COOLDOWN_MIN=60, SKILL_COOLDOWN_RANDOM=60, PHASE_PARTICLE_INTERVAL=10,
            PHASE_BURST_PARTICLES=160, PHASE_IDLE_PARTICLES=6;
    private static final double PHASE_BURST_XZ=1.2, PHASE_BURST_Y=1.6, PHASE_BURST_SPEED=.2,
            PHASE_IDLE_XZ=.5, PHASE_IDLE_Y=1, PHASE_IDLE_SPEED=.02, BODY_CENTRE=.5;
    private static final float PHASE_VOLUME=3, PHASE_PITCH=.7f;
    private static final int FAN=0, SPIRAL=1, WAVES=2, GRASP_PATTERNS=3, BARRAGE_INTERVAL=3,
            BARRAGE_BOLTS=3, WAVE_BOLTS=15;
    private static final float BARRAGE_START=2, BARRAGE_END=5.5f,
            FAN_SPACING=4, FAN_PITCH=12, SPIRAL_TURNS=3, SPIRAL_PITCH=.8f,
            WAVE_FIRST=2.2f, WAVE_SECOND=3.4f, WAVE_THIRD=4.6f,
            WAVE_PITCH_LOW=-8, WAVE_PITCH_HIGH=10, WAVE_PITCH_JITTER=3;
    private static final float PILLAR_WARNING=1.5f, PILLAR_RELEASE=2, PILLAR_MARK_Y=.1f,
            PILLAR_DUST_SCALE=1.2f, PILLAR_DUST_RED=.55f, PILLAR_DUST_GREEN=.2f, PILLAR_DUST_BLUE=.9f;
    private static final int PILLAR_RINGS_MIN=2, PILLAR_RINGS_RANDOM=2, PILLAR_RING_DENSITY=6,
            PILLAR_MARK_INTERVAL=2, PILLAR_MARK_PARTICLES=8, PILLAR_MARK_POINTS=12;
    private static final double PILLAR_RING_SPACING=4, PILLAR_ANGLE_JITTER=.3, PILLAR_RADIUS_JITTER=.5,
            PILLAR_FLOOR_HEIGHT=8, PILLAR_SURFACE_OFFSET=.025, PILLAR_MARK_RADIUS=.8,
            PILLAR_MARK_SPREAD=.45, PILLAR_MARK_SPREAD_Y=.02;
    private static final EntityDataAccessor<Integer> STATE=SynchedEntityData.defineId(OutsiderEntity.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<Long> START=SynchedEntityData.defineId(OutsiderEntity.class,EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Integer> WINDUP=SynchedEntityData.defineId(OutsiderEntity.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> RECOVERY=SynchedEntityData.defineId(OutsiderEntity.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> VOICE=SynchedEntityData.defineId(OutsiderEntity.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<Long> VOICE_START=SynchedEntityData.defineId(OutsiderEntity.class,EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Integer> OFFERING_INTRO=SynchedEntityData.defineId(OutsiderEntity.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<Long> OFFERING_START=SynchedEntityData.defineId(OutsiderEntity.class,EntityDataSerializers.LONG);
    private static final EntityDataAccessor<java.util.Optional<java.util.UUID>> BOSS_ID=SynchedEntityData.defineId(OutsiderEntity.class,EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<Long> FLINCH_START=SynchedEntityData.defineId(OutsiderEntity.class,EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Integer> PHASE=SynchedEntityData.defineId(OutsiderEntity.class,EntityDataSerializers.INT);
    private final ServerBossEvent bossEvent;
    private final OutsiderLocomotion locomotion=new OutsiderLocomotion();
    private OutsiderFace face;
    private int cooldown=80, groundTime=100, nextRoam;
    private long meteorReadyAt;
    private long voiceReadyAt,nextEngageAt;
    private long nextAmbientAt,nextMovementVoiceAt;
    private boolean meteorSpawned;
    private int pendingGroundSkill=-1;
    private Vec3 ritualOrigin;
    private Vec3 ritualAim;
    private Vec3 ritualSkyOrigin;
    private double ritualAltitude;
    private boolean skySpawned;
    private Vec3 waypoint, landingPoint;
    private OutsiderSpell meteor;
    private float previousSkillSeconds=-1;
    private float barrageYaw;
    private long flinchReadyAt, nextOrbitChange, blinkReadyAt;
    private Vec3 flinchPush=Vec3.ZERO;
    private double orbitAngle, orbitRadius=ORBIT_MIN;
    private int orbitDir=1, closeTicks, lastSkill=-1, graspPattern, pendingAirSkill=-1;
    private LivingEntity orbitTarget;
    private final List<Vec3> pillarSpots=new ArrayList<>();

    public OutsiderEntity(EntityType<? extends PathfinderMob> type,Level level) {
        super(type,level);setPersistenceRequired();setNoGravity(true);xpReward=10000;
        bossEvent=new ServerBossEvent(getDisplayName(),BossEvent.BossBarColor.PURPLE,BossEvent.BossBarOverlay.PROGRESS);
        if(!level.isClientSide)entityData.set(BOSS_ID,java.util.Optional.of(bossEvent.getId()));
    }
    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes().add(Attributes.MAX_HEALTH,OUTSIDER_MAX_HEALTH)
                .add(Attributes.MOVEMENT_SPEED,.32).add(Attributes.FOLLOW_RANGE,128)
                .add(Attributes.ARMOR,100).add(Attributes.ARMOR_TOUGHNESS,30)
                .add(Attributes.KNOCKBACK_RESISTANCE,1).add(Attributes.ATTACK_DAMAGE,100);
    }
    @Override public SpawnGroupData finalizeSpawn(ServerLevelAccessor world,DifficultyInstance difficulty,MobSpawnType reason,@Nullable SpawnGroupData data,@Nullable CompoundTag tag) {
        SpawnGroupData result=super.finalizeSpawn(world,difficulty,reason,data,tag);
        // The Outsider has its own test-boss health budget.  The shared
        // 神威天道 setting is intentionally not reused here.
        getAttribute(Attributes.MAX_HEALTH).setBaseValue(OUTSIDER_MAX_HEALTH);setHealth(getMaxHealth());state(AIR);return result;
    }
    @Override protected void registerGoals() { /* One controller owns movement and targeting. */ }
    @Override protected void defineSynchedData() {
        super.defineSynchedData();entityData.define(STATE,AIR);entityData.define(START,0L);
        entityData.define(FLINCH_START,0L);entityData.define(PHASE,PHASE_ONE);
        entityData.define(WINDUP,0);entityData.define(RECOVERY,0);entityData.define(BOSS_ID,java.util.Optional.empty());
        entityData.define(VOICE,-1);entityData.define(VOICE_START,0L);
        entityData.define(OFFERING_INTRO,0);
        entityData.define(OFFERING_START,0L);
    }
    public boolean isOfferingIntro(){return entityData.get(OFFERING_INTRO)>0;}
    public int offeringIntroTicks(){return isOfferingIntro()?(int)Math.max(0,level().getGameTime()-entityData.get(OFFERING_START)):0;}
    public void setOfferingIntro(boolean active){
        if(active&&!isOfferingIntro()){
            entityData.set(OFFERING_START,level().getGameTime());
            entityData.set(OFFERING_INTRO,1);
        }else if(!active)entityData.set(OFFERING_INTRO,0);
    }
    public int combatState(){return entityData.get(STATE);}
    public float stateAge(float partial){return Math.max(0,level().getGameTime()-entityData.get(START)+partial);}
    public boolean attacking(){return combatState()>=GRASP;}
    public boolean secondPhase(){return entityData.get(PHASE)==PHASE_TWO;}
    public float flinchAmount(float partial){
        long start=entityData.get(FLINCH_START);if(start==0)return 0;
        float u=(level().getGameTime()-start+partial)/FLINCH_TICKS;
        return u>=0&&u<1?(float)Math.sin(Math.PI*Math.min(1,u*FLINCH_RISE))*(1-u):0;
    }
    @Override public boolean hurt(DamageSource source,float amount){
        boolean hit=super.hurt(source,amount);long now=level().getGameTime();
        if(!level().isClientSide&&hit&&amount>=FLINCH_DAMAGE&&combatState()!=METEOR&&combatState()!=METEOR_PREP
                &&!isOfferingIntro()&&now>=flinchReadyAt){
            entityData.set(FLINCH_START,now);flinchReadyAt=now+FLINCH_COOLDOWN;flinchPush=Vec3.ZERO;
            if(source.getEntity() instanceof LivingEntity attacker&&validTarget(attacker)){
                setTarget(attacker);
                if(!attacking())flinchPush=position().subtract(attacker.position()).multiply(1,0,1).normalize()
                        .scale(FLINCH_PUSH).add(0,FLINCH_LIFT,0);
            }
        }
        return hit;
    }
    public boolean isFlyingPose(){return combatState()==AIR||combatState()==DESCEND||combatState()==TAKEOFF||combatState()==GRASP||combatState()==METEOR||combatState()==METEOR_PREP;}
    public String animationName(){
        if(isOfferingIntro())return "offering_intro";
        return switch(combatState()){
        case DESCEND->"descend";case LAND->"landing";case GROUND->"grounded_idle";
        case TAKEOFF->"takeoff";case GRASP->"dark_grasp";case SILENCE->"dark_silence";case METEOR->"annihilation";
        case METEOR_PREP->entityData.get(WINDUP)==1&&stateAge(0)<32?"takeoff":"flight_idle";
        default->getDeltaMovement().horizontalDistanceSqr()>.002?"flight_move":"flight_idle";};}
    public boolean animationLoops(){
        if(isOfferingIntro())return false;
        return combatState()==AIR||combatState()==DESCEND||combatState()==GROUND||(combatState()==METEOR_PREP&&animationName().equals("flight_idle"));
    }
    public float animationSeconds(float partial){
        if(isOfferingIntro()){
            float intro=(offeringIntroTicks()+partial)/20f;
            float emerge=com.sourceofmystery.ritual.OfferingPortalRitual.EMERGE_START_TICK/20f;
            return intro<emerge?0: intro-emerge;
        }
        if(!attacking())return combatState()==GROUND&&walkBlend(partial)>.05?walkSeconds(partial):stateAge(partial)/20;
        float time=stateAge(partial)/20,wind=entityData.get(WINDUP)/20f;
        if(combatState()==METEOR)return Mth.clamp(time,0,OutsiderRitual.END);
        if(combatState()==METEOR_PREP)return entityData.get(WINDUP)==1?Math.max(0,time-(time>=1.6f?1.6f:0)):time;
        float stop=combatState()==GRASP?1.4f:combatState()==SILENCE?1.4f:1.4f;
        // Stretch a real preparation interval; never freeze or skip the warning/charge.
        if(time>stop-.5f){if(time<stop+wind)time=stop-.5f+(time-(stop-.5f))*.5f/(.5f+wind);else time-=wind;}
        float end=combatState()==GRASP?7:7.4f;
        float recovery=entityData.get(RECOVERY)/20f;
        if(time>end-1)time=end-1+(time-(end-1))/(1+recovery);
        return Mth.clamp(time,0,end);
    }
    private void state(int next){
        entityData.set(STATE,next);entityData.set(START,level().getGameTime());previousSkillSeconds=-1;
        getNavigation().stop();waypoint=null;setControlledMotion(Vec3.ZERO);
        if(next>=GRASP){entityData.set(WINDUP,random.nextFloat()<.35f?18:0);entityData.set(RECOVERY,random.nextFloat()<.4f?24:0);}
        else {entityData.set(WINDUP,0);entityData.set(RECOVERY,0);}
        meteorSpawned=false;meteor=null;barrageYaw=getYRot();
        if(next==METEOR){ritualOrigin=position();ritualAim=null;ritualSkyOrigin=null;skySpawned=false;}
        if(!level().isClientSide){
            pillarSpots.clear();
            if(next==GRASP){graspPattern=random.nextInt(GRASP_PATTERNS);speak(random.nextBoolean()?1:2);}
            else if(next==SILENCE)speak(random.nextBoolean()?3:4);
            else if(getTarget()==null&&level().getGameTime()>=nextMovementVoiceAt){
                int line=next==TAKEOFF?OutsiderVoice.TAKEOFF:next==DESCEND?OutsiderVoice.LANDING:-1;
                if(line>=0&&random.nextFloat()<.35f&&speakDaily(line))nextMovementVoiceAt=level().getGameTime()+240+random.nextInt(161);
            }
        }
    }
    /** Preparation never descends to the ground and never resets a high caster. */
    private void prepareMeteor(){
        if(meteorCooldownTicks()>0)return;
        // Reserve the ultimate when its preparation starts. Other spells keep
        // their ordinary cooldown, and unloading cannot reset this deadline.
        meteorReadyAt=level().getGameTime()+(secondPhase()?PHASE_TWO_METEOR_COOLDOWN:OutsiderRitual.METEOR_COOLDOWN_TICKS);
        double gap=heightAboveGround();
        if(gap>=10){state(METEOR);return;}
        boolean grounded=onGround()||gap<.4;
        ritualAltitude=getY()+Math.max(0,10.1-gap);
        state(METEOR_PREP);entityData.set(WINDUP,grounded?1:0);
    }
    @Override public void tick(){
        double x=getX(),z=getZ();super.tick();
        if(level().isClientSide){
            locomotion.tick((float)Math.hypot(getX()-x,getZ()-z),combatState()==GROUND);
            if(face==null)face=new OutsiderFace(getId());face.tick(attacking()||walkBlend(1)>.05);return;
        }
        if(!isAlive())return;
        if(isOfferingIntro()){
            if(!level().isClientSide){
                // One synchronized start time feeds fractional-frame sampling;
                // sending a changing counter each tick caused pose-clock jitter.
                setControlledMotion(Vec3.ZERO);setNoGravity(true);
            }
            return;
        }
        bossEvent.setName(getDisplayName());bossEvent.setProgress(Mth.clamp(getHealth()/getMaxHealth(),0,1));
        tickPhase((ServerLevel)level());
        if(tickCount%10==0)chooseTarget();
        if(cooldown>0)cooldown--;
        LivingEntity target=getTarget();
        if(target!=null&&!validTarget(target))setTarget(null);
        if(getTarget()==null){pendingAirSkill=-1;orbitTarget=null;}
        tickAmbientVoice();
        if(attacking()){tickSkill((ServerLevel)level());return;}
        if(tickBlink((ServerLevel)level()))return;
        if(getTarget()!=null)faceTarget(getTarget());
        tickMovement();
        if(pendingAirSkill>=0&&combatState()==AIR&&getTarget()!=null){
            lastSkill=GRASP;pendingAirSkill=-1;state(GRASP);return;
        }
        if(cooldown==0&&pendingGroundSkill<0&&getTarget()!=null&&distanceToSqr(getTarget())<48*48&&(combatState()==AIR||combatState()==GROUND)){
            int choice=chooseSkill();lastSkill=choice;
            if(choice==METEOR)prepareMeteor();
            else if(choice==SILENCE&&combatState()!=GROUND){pendingGroundSkill=SILENCE;beginLanding();}
            else state(choice);
        }
    }
    private void tickPhase(ServerLevel server){
        long now=level().getGameTime();
        if(!secondPhase()&&getHealth()<=getMaxHealth()*PHASE_TWO_HEALTH){
            entityData.set(PHASE,PHASE_TWO);speak(0,true);
            meteorReadyAt=Math.min(meteorReadyAt,now+PHASE_TWO_METEOR_COOLDOWN);
            server.sendParticles(ParticleTypes.REVERSE_PORTAL,getX(),getY()+getBbHeight()*BODY_CENTRE,getZ(),
                    PHASE_BURST_PARTICLES,PHASE_BURST_XZ,PHASE_BURST_Y,PHASE_BURST_XZ,PHASE_BURST_SPEED);
            server.playSound(null,getX(),getY(),getZ(),com.sourceofmystery.sound.ModSounds.ARCHMAGE_RELEASE.get(),
                    SoundSource.HOSTILE,PHASE_VOLUME,PHASE_PITCH);
        }
        if(secondPhase()&&tickCount%PHASE_PARTICLE_INTERVAL==0)
            server.sendParticles(ParticleTypes.REVERSE_PORTAL,getX(),getY()+getBbHeight()*BODY_CENTRE,getZ(),
                    PHASE_IDLE_PARTICLES,PHASE_IDLE_XZ,PHASE_IDLE_Y,PHASE_IDLE_XZ,PHASE_IDLE_SPEED);
    }
    private int chooseSkill(){
        double r=getTarget().position().subtract(position()).horizontalDistance();
        double grasp=lastSkill==GRASP?0:r>GRASP_FAR_RANGE?GRASP_FAR_WEIGHT:GRASP_WEIGHT;
        // Silence needs ground to land on; skip it rather than count a cancelled landing.
        double silence=lastSkill==SILENCE||r>OutsiderRitual.SILENCE_RADIUS||floor(position())==null?0:
                r>=SILENCE_OPTIMAL_MIN&&r<=SILENCE_OPTIMAL_MAX?SILENCE_OPTIMAL_WEIGHT:SILENCE_WEIGHT;
        double ultimate=lastSkill!=METEOR&&meteorCooldownTicks()==0&&getHealth()<getMaxHealth()*METEOR_HEALTH_GATE?METEOR_WEIGHT:0;
        double total=grasp+silence+ultimate;if(total==0)return GRASP;
        double pick=random.nextDouble()*total;
        return pick<grasp?GRASP:pick<grasp+silence?SILENCE:METEOR;
    }
    private boolean tickBlink(ServerLevel server){
        LivingEntity target=getTarget();int s=combatState();
        if((s!=AIR&&s!=GROUND)||target==null){closeTicks=0;return false;}
        Vec3 gap=target.position().subtract(position());
        if(gap.horizontalDistanceSqr()<BLINK_RANGE*BLINK_RANGE&&Math.abs(gap.y)<BLINK_HEIGHT)closeTicks++;
        else closeTicks=Math.max(0,closeTicks-BLINK_DECAY);
        long now=level().getGameTime();if(closeTicks<BLINK_TRIGGER||now<blinkReadyAt)return false;
        for(int attempt=0;attempt<BLINK_ATTEMPTS;attempt++){
            double angle=random.nextDouble()*Math.PI*2,r=BLINK_MIN+random.nextDouble()*(BLINK_MAX-BLINK_MIN);
            Vec3 spot=target.position().add(Math.cos(angle)*r,0,Math.sin(angle)*r),surface=floor(spot);
            if(surface==null)continue;
            Vec3 dest=new Vec3(spot.x,surface.y+HOVER_HEIGHT,spot.z);
            if(GuardianShields.isProtected(server,BlockPos.containing(dest))
                    ||!level().noCollision(this,getBoundingBox().move(dest.subtract(position()))))continue;
            Vec3 old=position();
            server.sendParticles(ParticleTypes.REVERSE_PORTAL,old.x,old.y,old.z,BLINK_PARTICLES,
                    BLINK_SPREAD_XZ,BLINK_SPREAD_Y,BLINK_SPREAD_XZ,BLINK_PARTICLE_SPEED);
            server.sendParticles(ParticleTypes.PORTAL,dest.x,dest.y,dest.z,BLINK_PARTICLES,
                    BLINK_SPREAD_XZ,BLINK_SPREAD_Y,BLINK_SPREAD_XZ,BLINK_PARTICLE_SPEED);
            server.playSound(null,old.x,old.y,old.z,SoundEvents.ENDERMAN_TELEPORT,SoundSource.HOSTILE,BLINK_VOLUME,BLINK_PITCH);
            server.playSound(null,dest.x,dest.y,dest.z,SoundEvents.ENDERMAN_TELEPORT,SoundSource.HOSTILE,BLINK_VOLUME,BLINK_PITCH);
            teleportTo(dest.x,dest.y,dest.z);state(AIR);
            Vec3 facing=target.position().subtract(position());
            float yaw=(float)(Mth.atan2(facing.z,facing.x)*180/Math.PI)-90;
            setYRot(yaw);yBodyRot=yaw;yHeadRot=yaw;yRotO=yaw;
            resetOrbit(target);closeTicks=0;blinkReadyAt=now+BLINK_COOLDOWN;
            cooldown=Math.max(cooldown,BLINK_SKILL_DELAY);return true;
        }
        blinkReadyAt=now+BLINK_RETRY;
        return false;
    }
    private void resetOrbit(LivingEntity target){
        Vec3 bearing=position().subtract(target.position());orbitAngle=Math.atan2(bearing.z,bearing.x);orbitTarget=target;
    }
    public boolean validTarget(LivingEntity victim){return canAffect(victim)&&distanceToSqr(victim)<=128*128;}
    public boolean canAffect(LivingEntity victim){return victim!=this&&victim.isAlive()&&victim.isAttackable()
            &&!(victim instanceof net.minecraft.world.entity.decoration.ArmorStand)
            &&!(victim instanceof Player p&&(p.isCreative()||p.isSpectator()))
            &&!GuardianShields.shieldedFromBoss(victim);}
    private void chooseTarget(){
        // Re-evaluate players even while a non-player target is alive.
        LivingEntity best=level().players().stream().filter(this::validTarget).min(Comparator.comparingDouble(this::distanceToSqr)).orElse(null);
        if(best==null){best=getTarget();if(best==null||!validTarget(best))best=level().getEntitiesOfClass(LivingEntity.class,getBoundingBox().inflate(96),this::validTarget)
                .stream().min(Comparator.comparingDouble(this::distanceToSqr)).orElse(null);}
        if(best!=null&&getTarget()==null&&level().getGameTime()>=nextEngageAt){speak(0);cooldown=Math.max(cooldown,OutsiderVoice.TICKS[0]+12);nextEngageAt=level().getGameTime()+400;}
        setTarget(best);
    }
    @Nullable private Vec3 floor(Vec3 from){
        BlockHitResult hit=level().clip(new ClipContext(from.add(0,1,0),from.add(0,-48,0),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,this));
        return hit.getType()==HitResult.Type.BLOCK?hit.getLocation():null;
    }
    private void beginLanding(){landingPoint=floor(position());if(landingPoint==null){pendingGroundSkill=-1;cooldown=40;return;}state(DESCEND);}
    private double heightAboveGround(){
        Vec3 ground=floor(position());
        return ground==null?Double.POSITIVE_INFINITY:position().y-ground.y;
    }
    boolean canCastMeteorFromAir(){return heightAboveGround()>OutsiderRitual.METEOR_MIN_AIR_HEIGHT;}
    private void tickMovement(){
        int s=combatState();float age=stateAge(0);Vec3 velocity=Vec3.ZERO;
        if(s==DESCEND){
            if(landingPoint==null){state(AIR);return;}
            double gap=getY()-landingPoint.y;
            velocity=new Vec3(0,-Mth.clamp(gap*.14,.035,.3),0);
            if(gap<.06||onGround()){state(LAND);velocity=Vec3.ZERO;}
            else if(age>160){pendingGroundSkill=-1;state(AIR);return;}
        }else if(s==LAND){velocity=new Vec3(0,-.08,0);if(age>=24){groundTime=80+random.nextInt(100);state(GROUND);}}
        else if(s==GROUND){
            if(pendingGroundSkill>=GRASP){int next=pendingGroundSkill;pendingGroundSkill=-1;state(next);return;}
            velocity=new Vec3(0,-.08,0);
            if(age>groundTime){state(TAKEOFF);return;}
        }else if(s==TAKEOFF){
            // Smooth crouch, push-off and easing into hover. Avoid a constant
            // vertical step that makes the authored takeoff look mechanical.
            double lift=Mth.clamp((age-10)/22,0,1);
            double speed=.34*(lift*lift*(3-2*lift));
            // Hold contact while the animation compresses, then kick upward
            // with a visibly accelerating push instead of rising immediately.
            velocity=new Vec3(0,age<10?0:speed,0);
            if(age>=32){state(AIR);return;}
        }else{
            if(age>240&&tickCount%80==0&&random.nextFloat()<.28){beginLanding();return;}
            Vec3 ground=floor(position());double hoverY=ground==null?getY():ground.y+HOVER_HEIGHT;
            LivingEntity target=getTarget();
            if(target!=null){
                if(orbitTarget!=target)resetOrbit(target);
                long now=level().getGameTime();
                if(now>=nextOrbitChange){
                    orbitDir=random.nextBoolean()?1:-1;orbitRadius=ORBIT_MIN+random.nextDouble()*(ORBIT_MAX-ORBIT_MIN);
                    nextOrbitChange=now+ORBIT_CHANGE_MIN+random.nextInt(ORBIT_CHANGE_RANDOM);
                }
                orbitAngle+=orbitDir*ORBIT_SPEED;
                waypoint=target.position().add(Math.cos(orbitAngle)*orbitRadius,0,Math.sin(orbitAngle)*orbitRadius);
                boolean close=target.position().subtract(position()).horizontalDistanceSqr()<CLOSE_RANGE*CLOSE_RANGE;
                hoverY=Math.max(hoverY,target.getY()+(close?CLOSE_HOVER:HOVER_HEIGHT));
            }else if(waypoint==null||--nextRoam<=0){waypoint=position().add(random.nextDouble()*12-6,0,random.nextDouble()*12-6);nextRoam=100;}
            Vec3 flat=waypoint.subtract(position()).multiply(1,0,1);if(flat.length()>1)flat=flat.normalize();
            Vec3 desired=new Vec3(flat.x*.17,Mth.clamp((hoverY-getY())*.10,-.2,.2),flat.z*.17);
            velocity=getDeltaMovement().scale(.78).add(desired.scale(.22));
        }
        long flinchStart=entityData.get(FLINCH_START),flinchAge=level().getGameTime()-flinchStart;
        boolean flinching=flinchStart!=0&&flinchAge>=0&&flinchAge<FLINCH_TICKS;
        if(!flinching)flinchPush=Vec3.ZERO;
        else if(s==AIR||s==GROUND||s==DESCEND){
            Vec3 push=flinchPush.scale(1-(double)flinchAge/FLINCH_TICKS);
            velocity=velocity.add(s==GROUND?push.multiply(1,0,1):push);
        }
        if(flinching&&!attacking()&&getTarget()!=null)faceTarget(getTarget());
        else if((s==AIR||s==DESCEND)&&velocity.horizontalDistanceSqr()>.0004){
            float desiredYaw=(float)(Mth.atan2(velocity.z,velocity.x)*180/Math.PI)-90;
            float yaw=Mth.rotLerp(.16f,getYRot(),desiredYaw);
            setYRot(yaw);yBodyRot=yaw;yHeadRot=yaw;
        }
        setControlledMotion(velocity);move(MoverType.SELF,velocity);
    }
    private void tickSkill(ServerLevel server){
        setControlledMotion(Vec3.ZERO);
        if(combatState()==METEOR_PREP){
            float elapsed=stateAge(0);
            if((int)elapsed%30==0)server.playSound(null,getX(),getY()+12,getZ(),com.sourceofmystery.sound.ModSounds.ARCHMAGE_THUNDER.get(),
                    net.minecraft.sounds.SoundSource.HOSTILE,4,.68f+random.nextFloat()*.15f);
            double gap=ritualAltitude-getY();
            boolean fromGround=entityData.get(WINDUP)==1;
            if(gap<=.04&&(!fromGround||elapsed>=32)){state(METEOR);return;}
            double speed=fromGround&&elapsed<12?0:Math.min(.48,Math.max(.025,gap*.13));
            if(fromGround&&elapsed<18)speed*=Mth.clamp((elapsed-12)/6,0,1);
            Vec3 movement=new Vec3(0,Math.min(gap,speed),0);
            setControlledMotion(movement);move(MoverType.SELF,movement);
            // A blocked ceiling must not start the spell below its required altitude.
            if(elapsed>240&&heightAboveGround()<10){cooldown=60;state(AIR);}
            return;
        }
        float seconds=animationSeconds(0);LivingEntity target=getTarget();
        if(target!=null&&seconds<1.4)faceTarget(target);
        int skill=combatState();
        if(skill==GRASP){
            if(crossed(seconds,1.4f))barrageYaw=getYRot();
            if(seconds>1.2f&&seconds<5.7f&&target!=null){
                // Circle during the barrage; a broad windup and recovery bracket
                // the authored rib-cage/shoulder pulses instead of a frozen pose.
                Vec3 aim=Vec3.directionFromRotation(0,barrageYaw);
                double envelope=Math.min(1,(seconds-1.2)/.7)*Math.min(1,(5.7-seconds)/.5);
                Vec3 velocity=new Vec3(aim.z*.065,Math.sin(seconds*2)*.015,-aim.x*.065).scale(envelope);
                setControlledMotion(velocity);move(MoverType.SELF,velocity);
            }
            if(seconds>=BARRAGE_START&&seconds<BARRAGE_END){
                Vec3 origin=graspOrigin();
                float p=(seconds-BARRAGE_START)/(BARRAGE_END-BARRAGE_START);
                // Every pattern uses only the frozen cast direction.
                if(graspPattern==WAVES){
                    if(crossed(seconds,WAVE_FIRST)||crossed(seconds,WAVE_SECOND)||crossed(seconds,WAVE_THIRD))
                        for(int k=0;k<WAVE_BOLTS;k++){
                            float offset=-BARRAGE_YAW+2*BARRAGE_YAW*k/(WAVE_BOLTS-1);
                            float pitch=(k%2==0?WAVE_PITCH_LOW:WAVE_PITCH_HIGH)+(random.nextFloat()*2-1)*WAVE_PITCH_JITTER;
                            OutsiderSpell.spawn(server,this,OutsiderSpell.BOLT,origin,OutsiderTactics.barrageVelocity(barrageYaw,offset,pitch));
                        }
                }else if(tickCount%BARRAGE_INTERVAL==0){
                    for(int i=0;i<BARRAGE_BOLTS;i++){
                        float offset,pitch;
                        if(graspPattern==FAN){
                            offset=-BARRAGE_YAW+2*BARRAGE_YAW*p+(i-1)*FAN_SPACING;
                            pitch=(random.nextFloat()*2-1)*FAN_PITCH;
                        }else{
                            double phase=p*Math.PI*2*SPIRAL_TURNS+i*Math.PI*2/BARRAGE_BOLTS;
                            offset=(float)Math.cos(phase)*BARRAGE_YAW;pitch=(float)Math.sin(phase)*BARRAGE_PITCH*SPIRAL_PITCH;
                        }
                        OutsiderSpell.spawn(server,this,OutsiderSpell.BOLT,origin,OutsiderTactics.barrageVelocity(barrageYaw,offset,pitch));
                    }
                }
            }
        }else if(skill==SILENCE){
            if(crossed(seconds,PILLAR_WARNING)){
                // If the target escaped the field during the windup, approach
                // again instead of placing a pillar outside the stated radius.
                if(target!=null&&target.position().subtract(position()).horizontalDistanceSqr()>OutsiderRitual.SILENCE_RADIUS*OutsiderRitual.SILENCE_RADIUS){
                    pendingAirSkill=GRASP;cooldown=0;state(TAKEOFF);return;
                }
                planPillars(server);markPillars(server);
            }
            else if(seconds>=PILLAR_WARNING&&seconds<PILLAR_RELEASE&&tickCount%PILLAR_MARK_INTERVAL==0)markPillars(server);
            if(crossed(seconds,PILLAR_RELEASE))spawnPillars(server);
        }else if(skill==METEOR){
            ritualAudio(server,seconds);
            if(ritualOrigin==null)ritualOrigin=position();
            // Height preparation has its own state. The 30.5s ritual begins only
            // once airborne; high casters do not gain another ten blocks.
            if(crossed(seconds,OutsiderRitual.CHANT_END))ritualSkyOrigin=skyPositionAtBeam();
            if(crossed(seconds,OutsiderRitual.SKY_START)&&!skySpawned){
                skySpawned=true;ritualAim=target==null?ritualOrigin:target.position();
                if(ritualSkyOrigin==null)ritualSkyOrigin=skyPositionAtBeam();
                OutsiderSpell.spawn(server,this,OutsiderSpell.SKY_SEAL,ritualSkyOrigin,Vec3.ZERO);
            }
            if(crossed(seconds,OutsiderRitual.ORB_START)&&!meteorSpawned){
                meteorSpawned=true;
                Vec3 aim=ritualAim!=null?ritualAim:target==null?ritualOrigin:target.position();
                Vec3 spawn=ritualSkyOrigin!=null?ritualSkyOrigin:skyPositionAtBeam();
                meteor=OutsiderSpell.spawn(server,this,OutsiderSpell.METEOR,spawn,Vec3.ZERO);
                meteor.setTimedImpact(aim,OutsiderRitual.FALL_TICKS);
                // Reach listeners below y=0 while keeping the sound genuinely
                // at the y=255 source. A normal-volume sky sound is inaudible.
                server.playSound(null,spawn.x,spawn.y,spawn.z,com.sourceofmystery.sound.ModSounds.ARCHMAGE_METEOR_FALL.get(),
                        net.minecraft.sounds.SoundSource.HOSTILE,32,1);
                for(var player:server.players())if(player.position().subtract(aim).horizontalDistanceSqr()<256*256)
                    com.sourceofmystery.network.ModNetwork.CHANNEL.send(net.minecraftforge.network.PacketDistributor.PLAYER.with(()->player),
                            new com.sourceofmystery.network.ScreenEffectPacket(0,OutsiderRitual.FALL_TICKS,1.25f));
            }
        }
        previousSkillSeconds=seconds;
        float stateEnd=skill==GRASP?7:skill==SILENCE?7.4f:OutsiderRitual.END;
        // Recovery follows the held animation clock, never wall time: slow
        // falling meteors must not end the chant/invulnerability at 9 seconds.
        if(seconds>=stateEnd-.001&&(skill!=METEOR||meteor==null||meteor.hasImpacted())){
            cooldown=(int)((SKILL_COOLDOWN_MIN+random.nextInt(SKILL_COOLDOWN_RANDOM))*(secondPhase()?PHASE_TWO_COOLDOWN:1));
            state(skill==SILENCE?GROUND:AIR);
        }
    }
    private boolean crossed(float now,float event){return previousSkillSeconds<event&&now>=event;}
    static Vec3 barrageVelocity(float yaw,float horizontalSample,float verticalSample){
        // Preserve normalized samples for existing callers; patterns use degrees.
        return OutsiderTactics.barrageVelocity(yaw,Mth.clamp(horizontalSample,-1,1)*BARRAGE_YAW,
                Mth.clamp(verticalSample,-1,1)*BARRAGE_PITCH);
    }
    private void ritualAudio(ServerLevel server,float seconds){
        var source=net.minecraft.sounds.SoundSource.HOSTILE;
        if(crossed(seconds,0))speak(random.nextBoolean()?5:6);
        if(crossed(seconds,8))speak(random.nextBoolean()?7:8);
        if(crossed(seconds,OutsiderRitual.SHOUT_START))speak(random.nextBoolean()?9:10,true);
        if(crossed(seconds,0)||crossed(seconds,OutsiderRitual.STAFF_END))
            server.playSound(null,getX(),getY(),getZ(),com.sourceofmystery.sound.ModSounds.ARCHMAGE_CHARGE.get(),source,voiceSpeaking()?1:2.4f,.85f);
        int elapsed=(int)stateAge(0);
        if(seconds>=OutsiderRitual.STAFF_END&&seconds<OutsiderRitual.CHANT_END&&elapsed%50==0)
            server.playSound(null,getX(),getY()+3,getZ(),com.sourceofmystery.sound.ModSounds.ARCHMAGE_CHANT.get(),source,voiceSpeaking()?.6f:2,.9f);
        if(seconds<OutsiderRitual.END&&elapsed%30==0){
            double angle=elapsed*.81;
            server.playSound(null,getX()+Math.cos(angle)*12,getY()+8,getZ()+Math.sin(angle)*12,
                    com.sourceofmystery.sound.ModSounds.ARCHMAGE_THUNDER.get(),source,voiceSpeaking()?1.05f:3,.8f+random.nextFloat()*.12f);
        }
        if(crossed(seconds,OutsiderRitual.CHANT_END)||crossed(seconds,OutsiderRitual.ORB_START))
            server.playSound(null,getX(),getY()+4,getZ(),com.sourceofmystery.sound.ModSounds.ARCHMAGE_RELEASE.get(),source,voiceSpeaking()?1.35f:3.5f,.8f);
    }
    private void planPillars(ServerLevel server){
        pillarSpots.clear();LivingEntity target=getTarget();Vec3 centre=target==null?position():target.position();
        addPillarSpot(server,centre);
        int rings=PILLAR_RINGS_MIN+random.nextInt(PILLAR_RINGS_RANDOM);
        for(int k=1;k<=rings;k++){
            int count=PILLAR_RING_DENSITY*k;double phase=random.nextDouble()*Math.PI*2;
            for(int i=0;i<count;i++){
                double angle=phase+i*Math.PI*2/count+(random.nextDouble()*2-1)*Math.PI/count*PILLAR_ANGLE_JITTER;
                double radius=PILLAR_RING_SPACING*k+(random.nextDouble()*2-1)*PILLAR_RADIUS_JITTER;
                addPillarSpot(server,centre.add(Math.cos(angle)*radius,0,Math.sin(angle)*radius));
            }
        }
    }
    private void addPillarSpot(ServerLevel server,Vec3 spot){
        if(spot.subtract(position()).horizontalDistanceSqr()>OutsiderRitual.SILENCE_RADIUS*OutsiderRitual.SILENCE_RADIUS)return;
        Vec3 surface=floor(spot.add(0,PILLAR_FLOOR_HEIGHT,0));
        if(surface!=null&&!GuardianShields.isProtected(server,surface))pillarSpots.add(surface);
    }
    private void markPillars(ServerLevel server){
        DustParticleOptions dust=new DustParticleOptions(new Vector3f(PILLAR_DUST_RED,PILLAR_DUST_GREEN,PILLAR_DUST_BLUE),PILLAR_DUST_SCALE);
        for(Vec3 spot:pillarSpots){
            server.sendParticles(ParticleTypes.WITCH,spot.x,spot.y+PILLAR_MARK_Y,spot.z,PILLAR_MARK_PARTICLES,
                    PILLAR_MARK_SPREAD,PILLAR_MARK_SPREAD_Y,PILLAR_MARK_SPREAD,0);
            for(int i=0;i<PILLAR_MARK_POINTS;i++){
                double angle=i*Math.PI*2/PILLAR_MARK_POINTS;
                server.sendParticles(dust,spot.x+Math.cos(angle)*PILLAR_MARK_RADIUS,spot.y+PILLAR_MARK_Y,
                        spot.z+Math.sin(angle)*PILLAR_MARK_RADIUS,1,0,0,0,0);
            }
        }
    }
    private void spawnPillars(ServerLevel server){
        for(Vec3 surface:pillarSpots)
            OutsiderSpell.spawn(server,this,OutsiderSpell.PILLAR,surface.add(0,PILLAR_SURFACE_OFFSET,0),Vec3.ZERO);
        pillarSpots.clear();
    }
    public java.util.Optional<java.util.UUID> bossEventId(){return entityData.get(BOSS_ID);}
    /** Render and server sockets use networked YRot, not Mob's unsynced body controller. */
    public float visualBodyYaw(float partial){return Mth.rotLerp(partial,yRotO,getYRot());}
    public Vec3 modelToWorld(Vec3 scaledPoint){
        // Match the renderer's continuous rotation, including wrap-around;
        // Vec3.yRot uses the coarser vanilla trigonometric lookup table.
        float angle=(180-getYRot())*Mth.DEG_TO_RAD;
        double c=Math.cos(angle),s=Math.sin(angle);
        return position().add(scaledPoint.x*c+scaledPoint.z*s,scaledPoint.y,scaledPoint.z*c-scaledPoint.x*s);
    }
    public Vec3 graspOrigin(){return modelToWorld(OutsiderSockets.grasp(animationSeconds(0)));}
    public Vec3 ritualBeamOrigin(){return modelToWorld(OutsiderSockets.ritual(OutsiderRitual.CHANT_END));}
    private Vec3 skyPositionAtBeam(){
        Vec3 core=ritualBeamOrigin();
        return new Vec3(core.x,OutsiderRitual.SKY_HEIGHT,core.z);
    }
    private void faceTarget(LivingEntity target){Vec3 d=target.position().subtract(position());float yaw=(float)(Mth.atan2(d.z,d.x)*180/Math.PI)-90;
        setYRot(Mth.rotLerp(.14f,getYRot(),yaw));yBodyRot=getYRot();yHeadRot=getYRot();}
    @Override public void travel(Vec3 ignored){} // Prevent vanilla gravity/navigation fighting flight.
    @Override public boolean causeFallDamage(float d,float m,DamageSource s){return false;}
    @Override public boolean isPushable(){return false;}
    @Override public AABB getBoundingBoxForCulling(){return getBoundingBox().inflate(1.5,.8,1.5);}
    @Override public void knockback(double strength,double x,double z){}
    @Override public void push(double x,double y,double z){}
    @Override public void push(Entity entity){}
    // Explosions can write velocity directly instead of calling knockback().
    // Only this controller may change server velocity; clients still accept sync.
    @Override public void setDeltaMovement(Vec3 velocity){if(level().isClientSide)super.setDeltaMovement(velocity);}
    private void setControlledMotion(Vec3 velocity){super.setDeltaMovement(velocity);}
    @Override public boolean isInvulnerableTo(DamageSource s){return combatState()==METEOR||combatState()==METEOR_PREP||s.getEntity()==this||super.isInvulnerableTo(s);}
    @Override public void startSeenByPlayer(ServerPlayer p){super.startSeenByPlayer(p);bossEvent.addPlayer(p);}
    @Override public void stopSeenByPlayer(ServerPlayer p){super.stopSeenByPlayer(p);bossEvent.removePlayer(p);}
    @Override public boolean killedEntity(ServerLevel server,LivingEntity victim){speak(11);return super.killedEntity(server,victim);}
    @Override public void die(DamageSource s){speak(12,true);bossEvent.removeAllPlayers();super.die(s);}
    @Override public void remove(RemovalReason reason){bossEvent.removeAllPlayers();super.remove(reason);}
    public long meteorCooldownTicks(){return Math.max(0,meteorReadyAt-level().getGameTime());}
    private void speak(int index){
        speak(index,false);
    }
    private void speak(int index,boolean priority){
        boolean interruptsDaily=index<OutsiderVoice.FIRST_DAILY&&voiceIndex()>=OutsiderVoice.FIRST_DAILY;
        if(level().isClientSide||(!priority&&!interruptsDaily&&level().getGameTime()<voiceReadyAt))return;
        voiceReadyAt=level().getGameTime()+OutsiderVoice.TICKS[index]+8;
        entityData.set(VOICE,index);entityData.set(VOICE_START,level().getGameTime());
        // Clients play one attached sound per boss from this synced cue, so a
        // priority shout can interrupt an older line without stopping another boss.
    }
    public int voiceIndex(){return entityData.get(VOICE);}
    public long voiceStartTick(){return entityData.get(VOICE_START);}
    public float voiceAge(float partial){return level().getGameTime()-entityData.get(VOICE_START)+partial;}
    private boolean voiceSpeaking(){int index=voiceIndex();return index>=0&&voiceAge(0)<OutsiderVoice.TICKS[index];}
    /** Quiet non-combat lines never reserve a combat cooldown or interrupt speech. */
    boolean speakDaily(int index){
        if(index<OutsiderVoice.FIRST_DAILY||index>=OutsiderVoice.IDS.length||level().isClientSide||!isAlive()
                ||attacking()||getTarget()!=null||level().getGameTime()<voiceReadyAt)return false;
        speak(index);nextAmbientAt=level().getGameTime()+480+random.nextInt(321);return true;
    }
    private void tickAmbientVoice(){
        if(nextAmbientAt==0)nextAmbientAt=level().getGameTime()+480+random.nextInt(321);
        if(tickCount%20!=0||level().getGameTime()<nextAmbientAt||attacking()||getTarget()!=null)return;
        if(combatState()!=GROUND&&combatState()!=AIR)return;
        if(level().getNearestPlayer(this,24)==null)return;
        speakDaily(combatState()==GROUND?OutsiderVoice.IDLE_GROUND:OutsiderVoice.IDLE_FLIGHT);
    }
    @Override public void addAdditionalSaveData(CompoundTag tag){super.addAdditionalSaveData(tag);tag.putLong("MeteorReadyAt",meteorReadyAt);
        tag.putInt("LastSkill",lastSkill);tag.putInt("Phase",entityData.get(PHASE));}
    @Override public void readAdditionalSaveData(CompoundTag tag){super.readAdditionalSaveData(tag);state(AIR);cooldown=80;
        entityData.set(PHASE,tag.getInt("Phase")==PHASE_TWO?PHASE_TWO:PHASE_ONE);
        lastSkill=tag.contains("LastSkill")?tag.getInt("LastSkill"):-1;
        meteorReadyAt=Math.min(tag.getLong("MeteorReadyAt"),level().getGameTime()
                +(secondPhase()?PHASE_TWO_METEOR_COOLDOWN:OutsiderRitual.METEOR_COOLDOWN_TICKS));}
    public float walkBlend(float t){return locomotion.blend(t);}
    public float walkSeconds(float t){return locomotion.seconds(t);}
    public float blinkAmount(float t){return face==null?0:face.blink(t);}
    public float smileAmount(float t){return face==null||attacking()?0:face.smile(t);}
}
