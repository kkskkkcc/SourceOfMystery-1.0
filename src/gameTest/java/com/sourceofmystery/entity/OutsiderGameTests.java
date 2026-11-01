package com.sourceofmystery.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.gametest.*;

/** Development-only checks; neither this class nor its world is packaged in the mod. */
@GameTestHolder("sourceofmystery")
@PrefixGameTestTemplate(false)
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid="sourceofmystery")
public class OutsiderGameTests {
    private static Vec3 fallSoundPosition;
    private static int fallSoundCount;
    private static float fallSoundVolume;
    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void fallSoundTrace(net.minecraftforge.event.PlayLevelSoundEvent.AtPosition event){
        if(event.getSound()!=null&&event.getSound().value()==com.sourceofmystery.sound.ModSounds.ARCHMAGE_METEOR_FALL.get()){
            fallSoundPosition=event.getPosition();fallSoundVolume=event.getNewVolume();fallSoundCount++;
        }
    }
    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void damageTrace(net.minecraftforge.event.entity.living.LivingDamageEvent event){
        if(event.getEntity() instanceof Cow)com.sourceofmystery.SourceOfMystery.LOGGER.info("Outsider test damage: entity={} source={} amount={} before={} position={}",
                event.getEntity().getId(),event.getSource().getMsgId(),event.getAmount(),event.getEntity().getHealth(),event.getEntity().position());
    }
    private static void state(OutsiderEntity boss,int value){
        try{var method=OutsiderEntity.class.getDeclaredMethod("state",int.class);method.setAccessible(true);method.invoke(boss,value);}
        catch(ReflectiveOperationException e){throw new RuntimeException(e);}
    }
    private static void cooldown(OutsiderEntity boss){
        try{var field=OutsiderEntity.class.getDeclaredField("cooldown");field.setAccessible(true);field.setInt(boss,10000);}
        catch(ReflectiveOperationException e){throw new RuntimeException(e);}
    }
    @SuppressWarnings("unchecked")
    private static <T> void synced(OutsiderEntity boss,String name,T value)throws ReflectiveOperationException{
        var field=OutsiderEntity.class.getDeclaredField(name);field.setAccessible(true);
        boss.getEntityData().set((net.minecraft.network.syncher.EntityDataAccessor<T>)field.get(null),value);
    }
    @SuppressWarnings("unchecked")
    private static void meteorClock(GameTestHelper h,OutsiderEntity reference){
        fallSoundPosition=null;fallSoundCount=0;
        var level=h.getLevel();var caster=new OutsiderEntity(ModEntities.OUTSIDER.get(),level);
        for(int x=3;x<=7;x++)for(int z=3;z<=7;z++){
            h.setBlock(x,0,z,Blocks.STONE);
            for(int y=1;y<30;y++)h.setBlock(x,y,z,Blocks.AIR);
        }
        Vec3 ground=Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(5,1,5)));
        caster.setPos(ground);caster.setOnGround(true);
        try {
            var prepare=OutsiderEntity.class.getDeclaredMethod("prepareMeteor");prepare.setAccessible(true);
            var tick=OutsiderEntity.class.getDeclaredMethod("tickSkill",net.minecraft.server.level.ServerLevel.class);tick.setAccessible(true);
            prepare.invoke(caster);
            h.assertTrue(caster.combatState()==OutsiderEntity.METEOR_PREP,"Ground caster skipped takeoff preparation");
            h.assertTrue(caster.animationName().equals("takeoff"),"Ground meteor did not play full-body takeoff");
            synced(caster,"START",level.getGameTime()-10);tick.invoke(caster,level);
            h.assertTrue(Math.abs(caster.getY()-ground.y)<.01,"Ground caster rose before push-off");
            for(int elapsed=12;elapsed<220&&caster.combatState()==OutsiderEntity.METEOR_PREP;elapsed++){
                synced(caster,"START",level.getGameTime()-elapsed);tick.invoke(caster,level);
            }
            h.assertTrue(caster.combatState()==OutsiderEntity.METEOR,"Caster failed to reach casting altitude");
            h.assertTrue(caster.getY()-ground.y>=10,"Ritual started below ten blocks");
            Vec3 origin=caster.position();
            // A high airborne caster enters the ritual without any extra rise.
            prepare.invoke(caster);h.assertTrue(caster.combatState()==OutsiderEntity.METEOR,"High caster repeated takeoff");
            var movingTarget=new Cow(EntityType.COW,level);movingTarget.setPos(origin.add(12,-10,-9));caster.setTarget(movingTarget);
            for(int elapsed:new int[]{0,60,140,180,220,240,300,360,400,439}){
                synced(caster,"START",level.getGameTime()-elapsed);tick.invoke(caster,level);
                h.assertTrue(caster.position().distanceTo(origin)<.01,"Overhead ritual changed casting altitude");
                h.assertTrue(caster.combatState()==OutsiderEntity.METEOR,"Ritual ended early");
            }
            var field=OutsiderEntity.class.getDeclaredField("meteor");field.setAccessible(true);
            h.assertTrue(caster.voiceIndex()==9||caster.voiceIndex()==10,"Ultimate shout was skipped by an earlier voice cooldown");
            h.assertTrue(field.get(caster)==null,"Orb spawned before sky seal finished expanding");
            Vec3 beam=caster.ritualBeamOrigin(),skyOrigin=new Vec3(beam.x,255,beam.z);
            movingTarget.setPos(origin.add(22,-10,14));Vec3 lockedTarget=movingTarget.position();
            synced(caster,"START",level.getGameTime()-440);tick.invoke(caster,level);
            var sky=level.getEntitiesOfClass(OutsiderSpell.class,new AABB(origin,origin).inflate(512)).stream()
                    .filter(e->e.getOwner()==caster&&e.kind()==OutsiderSpell.SKY_SEAL).findFirst().orElseThrow();
            h.assertTrue(sky.position().distanceTo(skyOrigin)<.001,"Sky seal is not centred on staff laser at y255");
            h.assertTrue(Math.abs(sky.getX()-lockedTarget.x)>5,"Sky seal followed target instead of laser axis");
            movingTarget.setPos(lockedTarget.add(7,0,8));
            synced(caster,"START",level.getGameTime()-480);tick.invoke(caster,level);
            var orb=(OutsiderSpell)field.get(caster);
            h.assertTrue(orb!=null&&Math.abs(orb.getY()-255)<.01,"Red orb not spawned at y255 at 24s");
            h.assertTrue(orb.position().distanceTo(skyOrigin)<.001,"Orb did not emerge from sky seal centre");
            h.assertTrue(fallSoundPosition!=null&&fallSoundPosition.distanceTo(skyOrigin)<.001,"Meteor sound is not emitted from sky spawn");
            h.assertTrue(fallSoundCount==1&&fallSoundVolume>=24,"Meteor sound is repeated or inaudible below y0");
            synced(caster,"START",level.getGameTime()-481);tick.invoke(caster,level);
            h.assertTrue(fallSoundCount==1,"Meteor start sound replays every tick");
            h.assertTrue(OutsiderRitual.ORB_RADIUS==25&&OutsiderRitual.FALL_TICKS==100,"Wrong orb size/fall time");
            h.assertTrue(orb.trajectoryPosition(100).distanceTo(lockedTarget.add(0,25,0))<.01,"Fall endpoint differs from fixed target");
            var sf=OutsiderSpell.class.getDeclaredField("START");sf.setAccessible(true);
            var start=(net.minecraft.network.syncher.EntityDataAccessor<Long>)sf.get(null);
            orb.getEntityData().set(start,level.getGameTime()-99);orb.tick();h.assertFalse(orb.hasImpacted(),"Orb hit before five seconds");
            orb.getEntityData().set(start,level.getGameTime()-100);orb.tick();h.assertTrue(orb.hasImpacted(),"Orb did not hit at five seconds");
            synced(caster,"START",level.getGameTime()-610);tick.invoke(caster,level);
            h.assertTrue(caster.combatState()==OutsiderEntity.AIR,"Caster did not recover after 30.5s");
            for(var effect:level.getEntitiesOfClass(OutsiderSpell.class,new AABB(origin,origin).inflate(512)))if(effect.getOwner()==caster)effect.discard();
        }catch(ReflectiveOperationException ex){throw new RuntimeException(ex);}
    }
    private static void speechPriority(GameTestHelper h){
        var probe=new OutsiderEntity(ModEntities.OUTSIDER.get(),h.getLevel());
        h.assertTrue(probe.speakDaily(OutsiderVoice.IDLE_FLIGHT),"Idle speech did not start");
        h.assertFalse(probe.speakDaily(OutsiderVoice.IDLE_GROUND),"Daily lines overlap");
        state(probe,OutsiderEntity.GRASP);
        h.assertTrue(probe.voiceIndex()==1||probe.voiceIndex()==2,"Daily speech suppressed a combat call");
        h.assertFalse(probe.speakDaily(OutsiderVoice.LANDING),"Daily speech interrupted combat");
        h.assertTrue(OutsiderVoice.TICKS[9]-OutsiderVoice.SPEECH_TICKS[9]==40,"Approved ultimate lost its two-second tail");
    }
    private static void attachmentTransforms(GameTestHelper h){
        var probe=new OutsiderEntity(ModEntities.OUTSIDER.get(),h.getLevel());probe.setPos(13,84,-17);
        for(float yaw:new float[]{0,45,90,179,-179,270}){
            probe.setYRot(yaw);probe.yRotO=yaw;probe.yBodyRot=yaw+120;
            h.assertTrue(Math.abs(probe.visualBodyYaw(1)-yaw)<.001,"Attachment used unsynced Mob body yaw");
            for(float second:new float[]{.8f,2,3.5f,5.4f}){
                Vec3 p=OutsiderSockets.grasp(second);
                var rendered=new org.joml.Vector3f((float)p.x,(float)p.y,(float)p.z)
                        .rotateY((float)Math.toRadians(180-yaw));
                Vec3 server=probe.modelToWorld(p).subtract(probe.position());
                h.assertTrue(server.distanceTo(new Vec3(rendered.x,rendered.y,rendered.z))<.00001,"Rendered hand and server launch socket diverged at yaw "+yaw);
            }
        }
    }
    private static void barrageDirections(GameTestHelper h){
        var random=new java.util.Random(1234567);
        for(float yaw:new float[]{0,90,179,-179,270}){
            h.assertTrue(OutsiderEntity.barrageVelocity(yaw,0,-1).y>.3,"Barrage cannot fire upward");
            h.assertTrue(OutsiderEntity.barrageVelocity(yaw,0,1).y<-.3,"Barrage cannot fire downward");
            h.assertTrue(Math.abs(OutsiderEntity.barrageVelocity(yaw,0,0).y)<.00001,"Barrage has a forced downward bias");
            int up=0,down=0;
            for(int i=0;i<128;i++){
                Vec3 v=OutsiderEntity.barrageVelocity(yaw,random.nextFloat()*2-1,random.nextFloat()*2-1);
                h.assertTrue(Math.abs(v.length()-.75)<.00001,"Barrage speed changed with scatter angle");
                if(v.y>0)up++;if(v.y<0)down++;
            }
            h.assertTrue(up>40&&down>40,"Barrage does not scatter both above and below the horizon");
        }
    }
    private static void sphereAndSky(GameTestHelper h){
        Vec3 centre=new Vec3(10.5,130.5,-10.5);
        BlockPos origin=BlockPos.containing(centre);
        for(var direction:net.minecraft.core.Direction.values()){
            h.assertTrue(OutsiderBlastTasks.containsBlock(centre,origin.relative(direction,100)),"Sphere excluded a radius-100 face: "+direction);
            h.assertFalse(OutsiderBlastTasks.containsBlock(centre,origin.relative(direction,101)),"Sphere destroyed outside a face: "+direction);
        }
        h.assertTrue(OutsiderBlastTasks.containsBlock(centre,origin.offset(60,60,50)),"Sphere lost a 3D interior point");
        h.assertFalse(OutsiderBlastTasks.containsBlock(centre,origin.offset(70,70,70)),"Blast used a cube instead of a sphere");
        h.assertFalse(OutsiderBlastTasks.containsBlock(centre,origin.offset(0,100,1)),"Blast used a cylinder instead of a sphere");
        Vec3 precise=centre.add(.49,.31,.49);
        for(int x=-101;x<=101;x+=4)for(int y=-101;y<=101;y+=4)for(int z=-101;z<=101;z+=4){
            BlockPos p=origin.offset(x,y,z);
            boolean inside=Vec3.atCenterOf(p).distanceToSqr(precise)<=10000;
            h.assertTrue(OutsiderBlastTasks.containsBlock(precise,p)==inside,"Fractional-centre sphere boundary mismatch");
        }
        double previous=353;
        for(int i=0;i<=20;i++){
            double bottom=OutsiderRitual.lightningBottom(352,64,i*.1f);
            h.assertTrue(bottom<=previous&&bottom>=64&&bottom<=352,"Sky lightning travelled upward or overshot");previous=bottom;
        }
        h.assertTrue(Math.abs(previous-64)<.0001,"Sky lightning did not reach terrain in two ticks");
        h.assertTrue(OutsiderRitual.SUPER_TIER_RADIUS==10&&OutsiderRitual.STAFF_END==3,"Super-tier size/flat band regression");
    }
    private static void meteorAirRelease(GameTestHelper h,OutsiderEntity boss){
        Vec3 ground=boss.position();
        boss.setPos(ground.x,ground.y+12,ground.z);
        h.assertTrue(boss.canCastMeteorFromAir(),"Meteor still required a landing transition above ten blocks");
        h.assertTrue(OutsiderRitual.METEOR_MIN_AIR_HEIGHT==10.0,"Meteor airborne threshold changed");
        boss.setPos(ground);
    }
    private static void ultimateCooldown(GameTestHelper h){
        var level=h.getLevel();var probe=new OutsiderEntity(ModEntities.OUTSIDER.get(),level);probe.setPos(0,200,0);
        try {
            var prepare=OutsiderEntity.class.getDeclaredMethod("prepareMeteor");prepare.setAccessible(true);prepare.invoke(probe);
            h.assertTrue(probe.meteorCooldownTicks()==2400,"Ultimate did not reserve two minutes on preparation");
            state(probe,OutsiderEntity.GRASP);long remaining=probe.meteorCooldownTicks();prepare.invoke(probe);
            h.assertTrue(probe.combatState()==OutsiderEntity.GRASP,"Ultimate bypassed its own cooldown");
            h.assertTrue(probe.meteorCooldownTicks()==remaining,"Ordinary spell reset the ultimate deadline");
            var tag=new net.minecraft.nbt.CompoundTag();probe.addAdditionalSaveData(tag);
            var restored=new OutsiderEntity(ModEntities.OUTSIDER.get(),level);restored.readAdditionalSaveData(tag);
            h.assertTrue(restored.meteorCooldownTicks()==remaining,"Save/load reset the ultimate cooldown");
            var field=OutsiderEntity.class.getDeclaredField("meteorReadyAt");field.setAccessible(true);field.setLong(probe,level.getGameTime()+1);
            h.runAfterDelay(2,()->h.assertTrue(probe.meteorCooldownTicks()==0,"Ultimate deadline did not expire without an active target"));
        }catch(ReflectiveOperationException error){throw new RuntimeException(error);}
    }
    private static void silenceRange(GameTestHelper h){
        var level=h.getLevel();Vec3 centre=Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(95,1,95)));
        for(int x=-31;x<=31;x++)for(int z=-31;z<=31;z++)level.setBlock(BlockPos.containing(centre).offset(x,-1,z),Blocks.STONE.defaultBlockState(),2);
        var caster=new OutsiderEntity(ModEntities.OUTSIDER.get(),level);caster.setPos(centre);caster.getRandom().setSeed(55882);
        var target=new Cow(EntityType.COW,level);target.setPos(centre.add(29,0,0));caster.setTarget(target);
        try {
            var spawn=OutsiderEntity.class.getDeclaredMethod("spawnPillars",net.minecraft.server.level.ServerLevel.class);spawn.setAccessible(true);spawn.invoke(caster,level);
            var pillars=level.getEntitiesOfClass(OutsiderSpell.class,new AABB(centre,centre).inflate(40),e->e.kind()==OutsiderSpell.PILLAR&&e.getOwner()==caster);
            h.assertTrue(pillars.size()>=19&&pillars.size()<=40,"Silence pillar count changed");
            double farthest=0;boolean underTarget=false;
            for(var pillar:pillars){double distance=pillar.position().subtract(centre).horizontalDistanceSqr();
                h.assertTrue(distance<=30*30+.001,"Silence placed a pillar beyond thirty blocks");farthest=Math.max(farthest,distance);
                if(pillar.position().subtract(target.position()).horizontalDistanceSqr()<.01)underTarget=true;pillar.discard();
            }
            h.assertTrue(farthest>26*26&&underTarget,"Silence does not cover the newly extended field or the distant target");
        }catch(ReflectiveOperationException error){throw new RuntimeException(error);}
    }
    private static void savedSphereBoundary(GameTestHelper h,OutsiderEntity owner){
        var level=h.getLevel();
        BlockPos centre=new BlockPos(owner.blockPosition().getX(),150,owner.blockPosition().getZ());
        for(int dy:new int[]{-101,-100,100,101})level.setBlockAndUpdate(centre.offset(0,dy,0),Blocks.STONE.defaultBlockState());
        level.getGameRules().getRule(GameRules.RULE_MOBGRIEFING).set(true,level.getServer());
        OutsiderBlastTasks.enqueue(level,owner,Vec3.atCenterOf(centre));
        var snapshot=OutsiderBlastTasks.get(level).save(new net.minecraft.nbt.CompoundTag());
        var jobs=snapshot.getList("Jobs",net.minecraft.nbt.Tag.TAG_COMPOUND);
        var top=jobs.getCompound(jobs.size()-1).copy();
        h.assertTrue(top.getDouble("CentreX")==centre.getX()+.5&&top.getDouble("CentreY")==150.5,"Blast persistence truncated its precise centre");
        // Resume two real SavedData jobs near their vertical sphere faces.
        // This exercises actual destruction without scanning four million cells.
        int[] progress=top.getIntArray("NextY");java.util.Arrays.fill(progress,Integer.MAX_VALUE);progress[0]=250;
        top.putIntArray("NextY",progress);top.putInt("Cursor",0);
        var bottom=top.copy();progress=progress.clone();progress[0]=50;bottom.putIntArray("NextY",progress);
        var restored=new net.minecraft.nbt.ListTag();restored.add(top);restored.add(bottom);snapshot.put("Jobs",restored);
        level.getDataStorage().set("sourceofmystery_outsider_blasts",OutsiderBlastTasks.load(snapshot));
        h.runAtTickTime(10,()->{
            h.assertTrue(level.getBlockState(centre.above(100)).isAir(),"Sphere did not destroy its upper face");
            h.assertTrue(level.getBlockState(centre.below(100)).isAir(),"Sphere did not destroy its lower face");
            h.assertTrue(level.getBlockState(centre.above(101)).is(Blocks.STONE),"Sphere destroyed above radius 100");
            h.assertTrue(level.getBlockState(centre.below(101)).is(Blocks.STONE),"Sphere destroyed below radius 100");
            level.getGameRules().getRule(GameRules.RULE_MOBGRIEFING).set(false,level.getServer());
        });
    }
    private static void divineHitboxes(GameTestHelper h, DivineHeavenlyDaoBoss divine){
        var level=h.getLevel();
        Vec3 origin=Vec3.atCenterOf(h.absolutePos(new BlockPos(5,90,5)));
        divine.setNoAi(true);divine.setPos(origin);
        level.addFreshEntity(divine);
        var part=divine.getParts()[0];
        h.assertTrue(level.getEntityOrPart(part.getId())==part,"Multipart ID was not registered on the server");
        h.assertTrue(Math.abs(divine.getEyeHeight()-40.8)<.01,"Tail-inclusive hitbox changed the eye/aim height");
        for(float yaw:new float[]{0,45,90,179,-179}){
            divine.setYRot(yaw);
            double angle=Math.toRadians(180-yaw),c=Math.cos(angle),s=Math.sin(angle);
            for(Vec3 local:new Vec3[]{new Vec3(0,43,0),new Vec3(11,12,0),new Vec3(-11,12,0),new Vec3(0,-5.7,0),new Vec3(15,30,7.5)}){
                Vec3 point=origin.add(local.x*c+local.z*s,local.y,local.z*c-local.x*s);
                h.assertTrue(divine.getBoundingBox().contains(point),"Model point outside hurtbox at yaw "+yaw+": "+local);
                Vec3 start=point.add(0,0,-90);
                var hit=net.minecraft.world.entity.projectile.ProjectileUtil.getEntityHitResult(level,null,start,point,
                        new net.minecraft.world.phys.AABB(start,point).inflate(.1),Entity::isPickable);
                h.assertTrue(hit!=null&&hit.getEntity()==part,"Standard entity ray missed the giant model at "+local+" yaw="+yaw);
            }
        }
        divine.setYRot(0);
        Vec3 outside=origin.add(20,43,0),start=outside.add(0,0,-90);
        h.assertTrue(net.minecraft.world.entity.projectile.ProjectileUtil.getEntityHitResult(level,null,start,outside,
                new net.minecraft.world.phys.AABB(start,outside).inflate(.1),Entity::isPickable)==null,"Ray outside model envelope hit the boss");
        var before=divine.getBoundingBox();
        Vec3 movement=new Vec3(3,1,-2);divine.noPhysics=true;divine.move(MoverType.SELF,movement);
        h.assertTrue(divine.position().distanceTo(origin.add(movement))<.0001,"Offset hurtbox moved the entity origin");
        h.assertTrue(part.getBoundingBox().getCenter().distanceTo(before.getCenter().add(movement))<.0001,"Hit part did not follow movement");
        try {
            var trigger=DivineHeavenlyDaoBoss.class.getDeclaredMethod("triggerModelAction",String.class);trigger.setAccessible(true);
            for(String animation:new String[]{"attack","thunder_strike","slam","orb_charge","orb_throw","descend"}){
                trigger.invoke(divine,animation);
                h.assertTrue(part.getBoundingBox().getXsize()>=31&&part.getBoundingBox().minY<=divine.getY()-6,"Action envelope lost the idle body: "+animation);
                h.assertTrue(part.getBoundingBox().equals(divine.modelHurtbox()),"Action envelope did not synchronize the hit part");
                h.assertTrue(divine.getBoundingBox().getSize()<part.getBoundingBox().getSize(),"Attack pose expanded terrain collision: "+animation);
            }
        }catch(ReflectiveOperationException ex){throw new RuntimeException(ex);}
        float health=divine.getHealth();
        h.assertTrue(part.hurt(level.damageSources().generic(),100)&&divine.getHealth()<health,"Part damage was not forwarded to boss health");
        var attacker=new net.minecraftforge.common.util.FakePlayer(level,new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(),"DivineHitboxTest"));
        attacker.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(100);
        divine.invulnerableTime=0;health=divine.getHealth();attacker.attack(part);
        h.assertTrue(divine.getHealth()<health,"Player melee attack did not damage the parent boss");
        divine.discard();
        h.assertFalse(level.getPartEntities().contains(part),"Removed boss left an attackable orphan part");
    }
    @GameTest(template="outsider_test",timeoutTicks=260)
    public static void runtime(GameTestHelper h){
        var level=h.getLevel();level.getGameRules().getRule(GameRules.RULE_MOBGRIEFING).set(false,level.getServer());
        level.getDataStorage().set("sourceofmystery_outsider_blasts",new OutsiderBlastTasks());
        level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,level.getServer());
        Vec3 centre=Vec3.atCenterOf(h.absolutePos(BlockPos.ZERO));
        for(LivingEntity leftover:level.getEntitiesOfClass(LivingEntity.class,new net.minecraft.world.phys.AABB(centre,centre).inflate(256)))leftover.discard();
        for(int x=-20;x<40;x++)for(int z=-20;z<40;z++)h.setBlock(x,0,z,Blocks.STONE);
        OutsiderEntity boss=h.spawn(ModEntities.OUTSIDER.get(),5,5,5);cooldown(boss);
        boss.finalizeSpawn(level,level.getCurrentDifficultyAt(boss.blockPosition()),MobSpawnType.COMMAND,null,null);
        h.assertTrue(Math.abs(boss.getMaxHealth()-7500)<.1,"Outsider health was not 7500");
        attachmentTransforms(h);
        speechPriority(h);
        barrageDirections(h);
        sphereAndSky(h);
        meteorAirRelease(h,boss);
        ultimateCooldown(h);
        silenceRange(h);
        meteorClock(h,boss);
        var divine=new DivineHeavenlyDaoBoss(ModEntities.DIVINE_HEAVENLY_DAO_BOSS.get(),level);
        h.assertTrue(Math.abs(divine.getMaxHealth()-8000)<.1,"Divine default health was not 8000");
        double configured=com.sourceofmystery.config.MysteryConfig.BOSS_MAX_HEALTH.get();
        try {
            com.sourceofmystery.config.MysteryConfig.BOSS_MAX_HEALTH.set(8000.0);
            divine.finalizeSpawn(level,level.getCurrentDifficultyAt(boss.blockPosition()),MobSpawnType.COMMAND,null,null);
            h.assertTrue(Math.abs(divine.getHealth()-8000)<.1,"Divine spawn did not apply 8000 health");
        } finally {com.sourceofmystery.config.MysteryConfig.BOSS_MAX_HEALTH.set(configured);}
        divineHitboxes(h,divine);
        savedSphereBoundary(h,boss);
        boss.setDeltaMovement(Vec3.ZERO);boss.knockback(10,1,0);boss.push(4,3,2);
        h.assertTrue(boss.getDeltaMovement().lengthSqr()==0,"Boss accepted knockback/push");
        boss.setDeltaMovement(new Vec3(5,5,5));
        h.assertTrue(boss.getDeltaMovement().lengthSqr()==0,"Boss accepted direct explosion velocity");
        Cow cow=h.spawn(EntityType.COW,6,1,5);cow.setNoAi(true);
        cow.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);cow.setHealth(1000);
        ServerPlayer player=new net.minecraftforge.common.util.FakePlayer(level,new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(),"OutsiderTest"));
        player.setGameMode(GameType.SURVIVAL);
        player.setPos(boss.position().add(8,-4,0));player.setNoGravity(true);
        level.addNewPlayer(player);
        h.runAtTickTime(20,()->{
            h.assertTrue(boss.getTarget()==player,"Closer animal took priority over player");
            player.setGameMode(GameType.CREATIVE);
        });
        h.runAtTickTime(40,()->{
            h.assertTrue(boss.getTarget()==cow,"Boss did not fall back to a living non-player");
            cow.discard();player.discard();
        });
        h.runAtTickTime(120,()->{
            double floor=h.absolutePos(new BlockPos(5,1,5)).getY();
            h.assertTrue(boss.getTarget()==null,"Unexpected hover target: "+boss.getTarget());
            h.assertTrue(boss.getY()>floor+2.5&&boss.getY()<floor+4,"Hover height drifted: "+boss.getY()+" floor="+floor+" state="+boss.combatState());
            state(boss,OutsiderEntity.METEOR);
            h.assertFalse(boss.hurt(level.damageSources().generic(),500),"Meteor cast is not invulnerable");
            state(boss,OutsiderEntity.GROUND);cooldown(boss);
            Cow armored=h.spawn(EntityType.COW,10,1,10);armored.setNoAi(true);armored.setNoGravity(true);
            armored.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);armored.getAttribute(Attributes.ARMOR).setBaseValue(20);armored.setHealth(1000);
            OutsiderSpell.spawn(level,boss,OutsiderSpell.BOLT,armored.position().add(0,.6,-1),new Vec3(0,0,.75));
            h.runAfterDelay(5,()->{
                h.assertTrue(Math.abs(armored.getHealth()-920)<.1,"Bolt failed to bypass armor: "+armored.getHealth());
                armored.invulnerableTime=0;armored.setHealth(1000);
                OutsiderSpell.spawn(level,boss,OutsiderSpell.PILLAR,armored.position().add(0,-.05,0),Vec3.ZERO);
                h.runAfterDelay(18,()->h.assertTrue(armored.getHealth()==1000,"Pillar damaged during warning"));
                h.runAfterDelay(24,()->{
                    h.assertTrue(armored.getHealth()<1000&&armored.getHealth()>900,"Pillar did not apply armor reduction: "+armored.getHealth());
                    armored.setHealth(1000);armored.invulnerableTime=0;
                    armored.setPos(Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(10,1,10))));armored.setDeltaMovement(Vec3.ZERO);
                    armored.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.DAMAGE_RESISTANCE,100,4));
                    // The GameTest structure is carved below natural terrain;
                    // distant probes need their own cleared space outside it.
                    for(int centreX:new int[]{90,112})for(int x=centreX-1;x<=centreX+1;x++)
                        for(int y=12;y<=16;y++)for(int z=9;z<=11;z++)h.setBlock(x,y,z,Blocks.AIR);
                    Cow distant=h.spawn(EntityType.COW,90,13,10);distant.setNoAi(true);distant.setNoGravity(true);
                    distant.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);distant.setHealth(1000);
                    Cow outside=h.spawn(EntityType.COW,112,13,10);outside.setNoAi(true);outside.setNoGravity(true);
                    outside.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);outside.setHealth(1000);
                    h.setBlock(10,0,10,Blocks.STONE);h.setBlock(11,0,10,Blocks.OBSIDIAN);h.setBlock(12,0,10,Blocks.BEDROCK);
                    // Make these probes a surface, not the bottom of the
                    // underground test chamber's roof/terrain column.
                    for(int x=10;x<=12;x++)for(int y=1;h.absolutePos(new BlockPos(x,y,10)).getY()<level.getMaxBuildHeight();y++)
                        h.setBlock(x,y,10,Blocks.AIR);
                    level.getGameRules().getRule(GameRules.RULE_MOBGRIEFING).set(true,level.getServer());
                    OutsiderSpell meteor=OutsiderSpell.spawn(level,boss,OutsiderSpell.METEOR,armored.position().add(0,OutsiderRitual.ORB_RADIUS+1,0),new Vec3(0,-3.5,0));
                    meteor.setImpactPoint(armored.position());
                    h.runAfterDelay(5,()->{
                        h.assertTrue(meteor.hasImpacted(),"Meteor did not impact");
                        h.assertTrue(Math.abs(armored.getHealth()-800)<.1,"Meteor was not 200 true damage: "+armored.getHealth());
                        h.assertTrue(Math.abs(distant.getHealth()-800)<.1,"Meteor damage fell off with distance: "+distant.getHealth());
                        h.assertTrue(outside.getHealth()==1000,"Meteor damaged outside its radius");
                        h.assertBlockNotPresent(Blocks.STONE,new BlockPos(10,0,10));
                        h.assertBlockPresent(Blocks.OBSIDIAN,new BlockPos(11,0,10));
                        h.assertBlockPresent(Blocks.BEDROCK,new BlockPos(12,0,10));
                        // A queued blast must survive caster/effect removal and
                        // a SavedData round trip, while retaining hard blocks.
                        BlockPos surface=new BlockPos(boss.blockPosition().getX(),250,boss.blockPosition().getZ());
                        level.setBlockAndUpdate(surface,Blocks.STONE.defaultBlockState());
                        level.setBlockAndUpdate(surface.east(),Blocks.OBSIDIAN.defaultBlockState());
                        level.getGameRules().getRule(GameRules.RULE_MOBGRIEFING).set(false,level.getServer());
                        h.assertFalse(OutsiderBlastTasks.enqueue(level,boss,surface),"Blast ignored mobGriefing=false");
                        level.getGameRules().getRule(GameRules.RULE_MOBGRIEFING).set(true,level.getServer());
                        h.assertTrue(OutsiderBlastTasks.enqueue(level,boss,surface),"Blast not queued");
                        var snapshot=OutsiderBlastTasks.get(level).save(new net.minecraft.nbt.CompoundTag());
                        level.getDataStorage().set("sourceofmystery_outsider_blasts",OutsiderBlastTasks.load(snapshot));
                        boss.discard();meteor.discard();armored.discard();distant.discard();outside.discard();
                        h.runAfterDelay(20,()->{
                            h.assertTrue(level.getBlockState(surface).isAir(),"Saved blast stopped when caster/effect disappeared");
                            h.assertTrue(level.getBlockState(surface.east()).is(Blocks.OBSIDIAN),"Saved blast destroyed obsidian");
                            h.succeed();
                        });
                    });
                });
            });
        });
    }
}
