package com.sourceofmystery.staff;

import com.sourceofmystery.network.StaffEffectPacket;
import net.minecraft.core.*;
import net.minecraft.resources.*;
import net.minecraft.server.level.*;
import net.minecraft.tags.TagKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.phys.*;
import net.minecraftforge.common.util.ITeleporter;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.*;
import java.util.function.Function;

@Mod.EventBusSubscriber(modid="sourceofmystery")
public final class StaffPortals {
    private static final TagKey<Block> OVERWORLD=TagKey.create(Registries.BLOCK,new ResourceLocation("sourceofmystery","staff_overworld_rift"));
    private record Portal(UUID owner,ServerLevel source,ServerLevel destination,Vec3 center,Vec3 normal,Vec3 arrival,long until,int color) {}
    private static final List<Portal> PORTALS=new ArrayList<>();
    private static final Map<UUID,Integer> COOLDOWNS=new HashMap<>();
    private StaffPortals() {}

    public static boolean open(ServerPlayer player) {
        ServerLevel source=player.serverLevel();Vec3 eye=player.getEyePosition();
        BlockHitResult hit=source.clip(new ClipContext(eye,eye.add(player.getLookAngle().scale(8)),ClipContext.Block.OUTLINE,ClipContext.Fluid.NONE,player));
        if(hit.getType()!=HitResult.Type.BLOCK)return StaffCasting.fail(player,"rift_block");
        var block=source.getBlockState(hit.getBlockPos());ResourceKey<Level> dimension;int color;
        if(block.is(Blocks.END_STONE)){dimension=Level.END;color=0;}
        else if(block.is(Blocks.OBSIDIAN)){dimension=Level.NETHER;color=1;}
        else if(block.is(OVERWORLD)){dimension=Level.OVERWORLD;color=2;}
        else return StaffCasting.fail(player,"rift_block");
        ServerLevel destination=source.getServer().getLevel(dimension);
        if(destination==null)return StaffCasting.fail(player,"rift_destination");
        Vec3 arrival=arrival(player,destination);
        Vec3 normal=StaffTerrain.forward(player);
        Vec3 center=Vec3.atBottomCenterOf(hit.getBlockPos().above()).add(normal.scale(-.55));
        Portal portal=new Portal(player.getUUID(),source,destination,center,normal,arrival,
                source.getGameTime()+StaffConfig.PORTAL_TICKS.get(),color);
        PORTALS.add(portal);
        StaffCasting.send(source,new StaffEffectPacket(StaffEffectPacket.RIFT,player.getUUID(),center,normal,StaffConfig.PORTAL_TICKS.get(),color));
        return true;
    }
    static Vec3 arrival(ServerPlayer caster,ServerLevel destination) {
        if(destination.dimension()==Level.END) {
            ServerLevel.makeObsidianPlatform(destination);
            return Vec3.atBottomCenterOf(ServerLevel.END_SPAWN_POINT);
        }
        if(caster.getRespawnDimension().equals(destination.dimension())&&caster.getRespawnPosition()!=null) {
            Optional<Vec3> respawn=Player.findRespawnPositionAndUseSpawnBlock(destination,caster.getRespawnPosition(),
                    caster.getRespawnAngle(),caster.isRespawnForced(),true);
            if(respawn.isPresent())return respawn.get();
        }
        BlockPos spawn=destination.getSharedSpawnPos();destination.getChunkAt(spawn);
        int reference=Math.max(destination.getMinBuildHeight()+1,Math.min(destination.getMaxBuildHeight()-3,spawn.getY()));
        for(int radius=0;radius<=8;radius++)for(int dx=-radius;dx<=radius;dx++)for(int dz=-radius;dz<=radius;dz++) {
            if(Math.max(Math.abs(dx),Math.abs(dz))!=radius)continue;
            for(int offset=0;offset<destination.getHeight();offset++)for(int sign:new int[]{1,-1}) {
                int y=reference+offset*sign;if(y<=destination.getMinBuildHeight()||y>=destination.getMaxBuildHeight()-2)continue;
                BlockPos pos=new BlockPos(spawn.getX()+dx,y,spawn.getZ()+dz);
                if(destination.getBlockState(pos.below()).isFaceSturdy(destination,pos.below(),Direction.UP)
                        &&destination.getBlockState(pos).isAir()&&destination.getBlockState(pos.above()).isAir()
                        &&destination.getFluidState(pos.below()).isEmpty())return Vec3.atBottomCenterOf(pos);
            }
        }
        // A dimension without a usable spawn surface needs a solid arrival platform.
        BlockPos center=new BlockPos(spawn.getX(),reference,spawn.getZ());
        for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++) {
            destination.setBlockAndUpdate(center.offset(x,-1,z),Blocks.OBSIDIAN.defaultBlockState());
            for(int y=0;y<3;y++)destination.setBlockAndUpdate(center.offset(x,y,z),Blocks.AIR.defaultBlockState());
        }
        return Vec3.atBottomCenterOf(center);
    }
    @SubscribeEvent public static void tick(TickEvent.LevelTickEvent event) {
        if(event.phase!=TickEvent.Phase.END||!(event.level instanceof ServerLevel level)||TimeStop.active(level))return;
        int now=level.getServer().getTickCount();COOLDOWNS.entrySet().removeIf(e->e.getValue()<=now);
        for(Portal portal:List.copyOf(PORTALS)) {
            if(portal.source!=level)continue;
            if(level.getGameTime()>=portal.until){PORTALS.remove(portal);continue;}
            AABB box=new AABB(portal.center.add(-2,0,-2),portal.center.add(2,4,2));
            for(Entity entity:level.getEntities((Entity)null,box,e->!e.isRemoved()&&!e.isPassenger()&&!COOLDOWNS.containsKey(e.getUUID()))) {
                Vec3 offset=entity.position().subtract(portal.center);
                double plane=Math.abs(offset.dot(portal.normal));
                if(plane>entity.getBbWidth()*.5+.25)continue;
                teleportTree(entity,portal,now);
            }
        }
    }
    private static Entity teleportTree(Entity entity,Portal portal,int now) {
        List<Entity> passengers=new ArrayList<>(entity.getPassengers());entity.ejectPassengers();entity.stopRiding();
        COOLDOWNS.put(entity.getUUID(),now+40);Vec3 point=portal.arrival;
        BlockPos arrivalBlock=BlockPos.containing(point);
        portal.destination.getChunkAt(arrivalBlock);
        portal.destination.getChunkSource().addRegionTicket(TicketType.POST_TELEPORT,new ChunkPos(arrivalBlock),3,entity.getId());
        Entity moved;
        if(entity instanceof ServerPlayer player) {
            player.teleportTo(portal.destination,point.x,point.y,point.z,player.getYRot(),player.getXRot());moved=player;
        } else if(entity.level()==portal.destination) {
            entity.teleportTo(point.x,point.y,point.z);moved=entity;
        } else moved=entity.changeDimension(portal.destination,new ITeleporter() {
            @Override public net.minecraft.world.level.portal.PortalInfo getPortalInfo(Entity current,ServerLevel destination,
                    Function<ServerLevel,net.minecraft.world.level.portal.PortalInfo> fallback) {
                return new net.minecraft.world.level.portal.PortalInfo(point,current.getDeltaMovement(),current.getYRot(),current.getXRot());
            }
            @Override public Entity placeEntity(Entity current,ServerLevel oldLevel,ServerLevel newLevel,float yaw,Function<Boolean,Entity> reposition) {
                Entity result=reposition.apply(false);if(result!=null)result.moveTo(point.x,point.y,point.z,yaw,current.getXRot());return result;
            }
        });
        if(moved!=null) {moved.fallDistance=0;for(Entity passenger:passengers){Entity carried=teleportTree(passenger,portal,now);if(carried!=null)carried.startRiding(moved,true);}}
        return moved;
    }
    @SubscribeEvent public static void stop(ServerStoppedEvent event){PORTALS.clear();COOLDOWNS.clear();}
}
