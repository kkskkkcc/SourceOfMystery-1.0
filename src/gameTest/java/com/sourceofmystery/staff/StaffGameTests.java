package com.sourceofmystery.staff;

import com.mojang.authlib.GameProfile;
import com.sourceofmystery.energy.MysteryEnergy;
import com.sourceofmystery.entity.*;
import com.sourceofmystery.item.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.storage.loot.*;
import net.minecraft.world.level.storage.loot.parameters.*;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.*;
import java.util.UUID;

/** Real Forge runtime checks. The test source set is excluded from release jars. */
@GameTestHolder("sourceofmystery")
@PrefixGameTestTemplate(false)
public final class StaffGameTests {
    private static FakePlayer player(ServerLevel level,Vec3 position) {
        // Forge's FakePlayer is invulnerable by default; this probe must behave like a survival player.
        FakePlayer player=new FakePlayer(level,new GameProfile(UUID.randomUUID(),"StaffTest")) {
            @Override public boolean isInvulnerableTo(net.minecraft.world.damagesource.DamageSource damage){return false;}
            @Override public boolean canHarmPlayer(net.minecraft.world.entity.player.Player other){return true;}
        };
        try {var field=ServerPlayer.class.getDeclaredField("spawnInvulnerableTime");field.setAccessible(true);field.setInt(player,0);}
        catch(ReflectiveOperationException error){throw new RuntimeException(error);}
        player.setGameMode(GameType.SURVIVAL);player.setPos(position);player.setNoGravity(true);
        player.setYRot(0);player.setXRot(0);player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(ModItems.DIMENSION_BREAKER_STAFF.get()));
        level.getChunkAt(player.blockPosition());level.addNewPlayer(player);return player;
    }
    private static Cow cow(ServerLevel level,Vec3 position) {
        Cow cow=new Cow(EntityType.COW,level);cow.setPos(position);cow.setNoAi(true);cow.setNoGravity(true);
        cow.getAttribute(Attributes.MAX_HEALTH).setBaseValue(2000);cow.setHealth(2000);
        ChunkPos chunk=new ChunkPos(cow.blockPosition());level.setChunkForced(chunk.x,chunk.z,true);
        level.getChunkAt(cow.blockPosition());level.addFreshEntity(cow);return cow;
    }
    private static void basic(GameTestHelper h,FakePlayer player) {
        var staff=player.getMainHandItem();var level=player.serverLevel();
        MysteryEnergy.setEnergyAndMax(player,25);MysteryEnergy.setEnergy(player,0);
        for(int i=0;i<4;i++)MysteryEnergy.regenerate(player);
        h.assertTrue(MysteryEnergy.getEnergy(player)==1,"Fractional regeneration was discarded");
        h.assertTrue(MysteryEnergy.getEnergyMax(player)==25,"Regeneration raised the energy cap");
        MysteryEnergy.setEnergyAndMax(player,20000);MysteryEnergy.setEnergy(player,19899);MysteryEnergy.regenerate(player);
        h.assertTrue(MysteryEnergy.getEnergy(player)==20000,"Regeneration exceeded the cap");
        var modifiers=staff.getAttributeModifiers(EquipmentSlot.MAINHAND).get(Attributes.ATTACK_DAMAGE);
        h.assertTrue(modifiers.size()==1&&modifiers.iterator().next().getAmount()==198,"Staff attack is not 199 including player base damage");
        for(Block block:new Block[]{Blocks.DIRT,Blocks.OBSIDIAN,Blocks.COMMAND_BLOCK,Blocks.END_PORTAL_FRAME})
            h.assertTrue(Math.abs(block.defaultBlockState().getDestroyProgress(player,level,player.blockPosition())-1f/18)<.0001,"Mining duration differs for "+block);
        h.assertTrue(Blocks.BEDROCK.defaultBlockState().getDestroyProgress(player,level,player.blockPosition())==0,"Staff mines bedrock");
        h.assertFalse(staff.getItem().canAttackBlock(Blocks.BEDROCK.defaultBlockState(),level,player.blockPosition(),player),"Creative staff can remove bedrock");
        var boss=new OutsiderEntity(ModEntities.OUTSIDER.get(),level);boss.setPos(player.position());
        var loot=level.getServer().getLootData().getLootTable(ModEntities.OUTSIDER.get().getDefaultLootTable());
        var params=new LootParams.Builder(level).withParameter(LootContextParams.THIS_ENTITY,boss)
                .withParameter(LootContextParams.ORIGIN,boss.position()).withParameter(LootContextParams.DAMAGE_SOURCE,level.damageSources().generic())
                .create(LootContextParamSets.ENTITY);
        for(int i=0;i<20;i++) {
            var drops=loot.getRandomItems(params);
            h.assertTrue(drops.size()==1&&drops.get(0).is(staff.getItem())&&drops.get(0).getCount()==1,"Boss staff drop is not guaranteed");
        }
        StaffSpell.ANNIHILATION.select(staff);
        h.assertTrue(StaffCasting.begin(player,InteractionHand.MAIN_HAND,staff),"Annihilation cannot begin");
        h.assertTrue(MysteryEnergy.getEnergy(player)==20000,"Charge deducted energy early");
        h.assertTrue(player.hurt(level.damageSources().generic(),1),"Charging granted invulnerability");
        StaffCasting.cancel(player);
        h.assertTrue(MysteryEnergy.getEnergy(player)==20000,"Cancelled charge deducted energy");
        player.setHealth(player.getMaxHealth());
        h.assertTrue(StaffCasting.begin(player,InteractionHand.MAIN_HAND,staff)&&StaffCasting.finish(player,staff),"Completed annihilation failed");
        h.assertTrue(MysteryEnergy.getEnergy(player)==10000,"Completed annihilation did not cost exactly 10000");
        for(Entity entity:level.getAllEntities())if(entity instanceof OutsiderSpell spell&&spell.getOwner()==player)spell.discard();
        Cow victim=cow(level,player.position().add(2,0,0));
        var meteor=OutsiderSpell.spawn(level,player,OutsiderSpell.METEOR,player.position(),Vec3.ZERO);
        meteor.setTimedImpact(player.position(),0); // Direct collision on a prepared surface exercises the shared damage path.
        try {
            var impact=OutsiderSpell.class.getDeclaredMethod("impact",LivingEntity.class,Vec3.class);impact.setAccessible(true);
            impact.invoke(meteor,player,player.position());
        }catch(ReflectiveOperationException error){throw new RuntimeException(error);}
        h.assertTrue(player.getHealth()==player.getMaxHealth(),"Own meteor damaged its caster");
        h.assertTrue(victim.getHealth()==1800,"Player meteor differs from boss meteor damage");
        victim.discard();meteor.discard();level.getDataStorage().set("sourceofmystery_outsider_blasts",new OutsiderBlastTasks());
    }
    @GameTest(template="outsider_test",batch="staff",timeoutTicks=2400)
    public static void runtime(GameTestHelper h) {
        ServerLevel level=h.getLevel(),nether=level.getServer().getLevel(Level.NETHER);
        level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,level.getServer());
        Vec3 origin=new Vec3(1000.5,150,1000.5);FakePlayer player=player(nether,origin);
        basic(h,player);
        BlockPos unbreakable=player.blockPosition().offset(0,1,3);
        nether.setBlockAndUpdate(unbreakable,Blocks.COMMAND_BLOCK.defaultBlockState());
        StaffMining.request(player,unbreakable,true);
        h.runAtTickTime(17,()->h.assertTrue(nether.getBlockState(unbreakable).is(Blocks.COMMAND_BLOCK),"Unbreakable block broke before 0.9 seconds"));
        h.runAtTickTime(18,()->{
            StaffMining.tick(new net.minecraftforge.event.TickEvent.PlayerTickEvent(net.minecraftforge.event.TickEvent.Phase.END,player));
            h.assertTrue(nether.getBlockState(unbreakable).isAir(),"Manual unbreakable-block mining did not complete");
        });
        Vec3 direction=new Vec3(0,0,1);
        h.assertTrue(StaffTerrain.withinSplit(origin.add(14.9,0,50),origin,direction,100,30),"Fissure middle is narrower than 30");
        h.assertFalse(StaffTerrain.withinSplit(origin.add(15.1,0,50),origin,direction,100,30),"Fissure exceeds width 30");
        h.assertFalse(StaffTerrain.withinSplit(origin.add(1,0,0),origin,direction,100,30),"Fissure start does not taper");
        h.assertFalse(StaffTerrain.withinSplit(origin.add(0,0,101),origin,direction,100,30),"Fissure exceeds length 100");
        // Build a shelf above the Nether ceiling, with a chest inside the selected chunk.
        ChunkPos chunk=new ChunkPos(BlockPos.containing(origin.add(0,0,16)));
        nether.getDataStorage().set("sourceofmystery_staff_terrain",new StaffTerrain());
        for(int x=chunk.getMinBlockX();x<=chunk.getMaxBlockX();x++)for(int z=chunk.getMinBlockZ();z<=chunk.getMaxBlockZ();z++)
            for(int y=100;y<nether.getMaxBuildHeight();y++) {
                BlockPos pos=new BlockPos(x,y,z);nether.removeBlockEntity(pos);
                nether.setBlock(pos,(y<=149?Blocks.STONE:Blocks.AIR).defaultBlockState(),18);
            }
        BlockPos chest=new BlockPos(chunk.getMinBlockX()+4,150,chunk.getMinBlockZ()+4);
        nether.setBlockAndUpdate(chest,Blocks.CHEST.defaultBlockState());
        ((ChestBlockEntity)nether.getBlockEntity(chest)).setItem(0,new ItemStack(Items.DIAMOND,7));
        h.assertTrue(StaffTerrain.lift(player),"Valid terrain lift refused");
        StaffTerrain saved=nether.getDataStorage().get(StaffTerrain::load,"sourceofmystery_staff_terrain");
        nether.getDataStorage().set("sourceofmystery_staff_terrain",StaffTerrain.load(saved.save(new CompoundTag())));
        h.runAtTickTime(180,()->{
            h.assertTrue(nether.getBlockState(chest).isAir(),"Lift left the original chest in place");
            h.assertTrue(nether.getBlockState(chest.above(100)).is(Blocks.CHEST),"Lift did not move the chest 100 blocks");
            var moved=(ChestBlockEntity)nether.getBlockEntity(chest.above(100));
            h.assertTrue(moved.getItem(0).is(Items.DIAMOND)&&moved.getItem(0).getCount()==7,"Lift lost or duplicated chest contents");
            testTime(h,player);
        });
    }
    private static void testTime(GameTestHelper h,FakePlayer player) {
        var level=player.serverLevel();Cow frozen=cow(level,player.position().add(5,0,0));
        FakePlayer observer=player(level,player.position().add(8,0,0));
        Cow normal=cow(h.getLevel(),new Vec3(1600,200,1600));
        var item=new net.minecraft.world.entity.item.ItemEntity(level,player.getX()+2,player.getY(),player.getZ(),new ItemStack(Items.STONE));
        level.addFreshEntity(item);item.setDeltaMovement(1,0,0);
        long startTime=level.getGameTime(),otherTime=h.getLevel().getGameTime();
        BlockPos lamp=player.blockPosition().offset(4,-1,0);
        level.getBlockTicks().clearArea(new net.minecraft.world.level.levelgen.structure.BoundingBox(lamp));
        level.setBlock(lamp,Blocks.REDSTONE_LAMP.defaultBlockState().setValue(RedstoneLampBlock.LIT,true),18);
        level.scheduleTick(lamp,Blocks.REDSTONE_LAMP,5);
        h.assertTrue(TimeStop.start(player),"Time stop did not begin");
        h.assertTrue(TimeStop.frozen(frozen)&&TimeStop.frozen(item)&&!TimeStop.frozen(player),"Time-stop caster exemption is incorrect");
        h.assertFalse(TimeStop.frozen(normal),"Time stop affected another dimension");
        Vec3 observerPosition=observer.position();observer.move(MoverType.SELF,new Vec3(1,0,0));
        h.assertTrue(observer.position().equals(observerPosition),"Another player moved during time stop");
        Vec3 casterPosition=player.position();player.move(MoverType.SELF,new Vec3(1,0,0));
        h.assertTrue(player.position().distanceTo(casterPosition)>.5,"Caster could not move during time stop");
        Vec3 position=item.position();level.tickNonPassenger(item);
        h.assertTrue(item.position().equals(position),"Frozen projectile/item moved");
        h.runAfterDelay(30,()->{
            h.assertTrue(TimeStop.active(level)&&level.getGameTime()==startTime,"Stopped dimension clock advanced");
            h.assertTrue(h.getLevel().getGameTime()>=otherTime+30,"Other dimension clock was paused");
            h.assertFalse(TimeStop.canCast(player),"Time stop can overlap itself");
            h.assertTrue(level.getBlockState(lamp).getValue(RedstoneLampBlock.LIT),"Scheduled redstone ran during time stop");
        });
        h.runAfterDelay(242,()->{
            h.assertFalse(TimeStop.active(level),"Time stop did not end after 12 seconds");
            h.assertFalse(TimeStop.canCast(player),"Post-stop cooldown was skipped");
            h.assertTrue(level.getGameTime()<startTime+10,"Frozen world caught up skipped simulation ticks");
            h.assertTrue(level.getBlockState(lamp).getValue(RedstoneLampBlock.LIT),"Scheduled redstone caught up immediately after time stop");
        });
        h.runAfterDelay(248,()->h.assertFalse(level.getBlockState(lamp).getValue(RedstoneLampBlock.LIT),
                "Scheduled redstone did not resume: time="+(level.getGameTime()-startTime)
                        +", queued="+level.getBlockTicks().hasScheduledTick(lamp,Blocks.REDSTONE_LAMP)
                        +", ticking="+level.shouldTickBlocksAt(new ChunkPos(lamp).toLong())
                        +", loaded="+level.getChunkAt(lamp).getFullStatus()));
        h.runAfterDelay(303,()->{
            h.assertTrue(TimeStop.canCast(player),"Three-second post-stop cooldown did not finish");
            frozen.discard();normal.discard();observer.discard();item.discard();testPortalsAndSplit(h,player);
        });
    }
    private static void testPortalsAndSplit(GameTestHelper h,FakePlayer player) {
        ServerLevel source=player.serverLevel();var staff=player.getMainHandItem();
        player.setPos(1100.5,150,1100.5);player.setYRot(0);player.setXRot(0);
        BlockPos target=new BlockPos(1100,151,1103);source.setBlockAndUpdate(target,Blocks.END_STONE.defaultBlockState());
        MysteryEnergy.setEnergyAndMax(player,0);StaffSpell.SPACE_RIFT.select(staff);
        h.assertTrue(StaffCasting.begin(player,InteractionHand.MAIN_HAND,staff)&&StaffCasting.finish(player,staff),"Free End rift required energy");
        h.assertTrue(MysteryEnergy.getEnergy(player)==0,"Free rift changed energy");
        Cow traveler=cow(source,new Vec3(1100.5,152,1102.95));UUID travelerId=traveler.getUUID();
        h.startSequence().thenWaitUntil(()->{
            var arrived=source.getServer().getLevel(Level.END).getEntity(travelerId);
            h.assertTrue(arrived!=null&&arrived.position().distanceTo(Vec3.atBottomCenterOf(ServerLevel.END_SPAWN_POINT))<2,
                    "Rift did not transport an entity to the End island: arrived="+arrived+", source="+source.getEntity(travelerId));
            arrived.discard();
            player.setRespawnPosition(Level.OVERWORLD,null,0,false,false);
            Vec3 fallback=StaffPortals.arrival(player,source.getServer().overworld());
            h.assertTrue(Math.abs(fallback.x-source.getServer().overworld().getSharedSpawnPos().getX())<10,"Missing respawn did not use dimension spawn");
            player.setPos(1200.5,150,1200.5);player.setYRot(0);player.setXRot(0);
            BlockPos probe=new BlockPos(1200,180,1253),bedrock=probe.below();
            source.setBlockAndUpdate(probe,Blocks.COMMAND_BLOCK.defaultBlockState());source.setBlockAndUpdate(bedrock,Blocks.BEDROCK.defaultBlockState());
            Cow victim=cow(source,new Vec3(1200.5,185,1253.5));
            victim.getAttribute(Attributes.ARMOR).setBaseValue(30);
            victim.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE,400,4));
            h.assertTrue(StaffTerrain.split(player),"Fissure did not start");
            player.setPos(victim.position().add(2,0,0));
            h.runAfterDelay(150,()->h.succeedWhen(()->{
                h.assertTrue(source.getBlockState(probe).isAir(),"Fissure did not remove an unbreakable block");
                h.assertTrue(source.getBlockState(bedrock).is(Blocks.BEDROCK),"Fissure removed bedrock");
                h.assertTrue(victim.getHealth()==1000,"Fissure failed to deal 1000 true damage: "+victim.getHealth()+", removed="+victim.isRemoved()+", tracked="+source.getEntity(victim.getUUID())+", pos="+victim.position());
                h.assertTrue(player.isAlive(),"Fissure hurt its caster");
                victim.discard();player.discard();
            }));
        });
    }
}
