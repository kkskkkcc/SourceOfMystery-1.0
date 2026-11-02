package com.sourceofmystery.staff;

import com.sourceofmystery.network.StaffEffectPacket;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.*;
import net.minecraft.resources.*;
import net.minecraft.server.level.*;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.*;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.*;

/** Persistent terrain jobs preserve progress and block-entity contents across saves. */
@Mod.EventBusSubscriber(modid="sourceofmystery")
public final class StaffTerrain extends SavedData {
    private static final String ID="sourceofmystery_staff_terrain";
    private final List<Job> jobs=new ArrayList<>();
    private int turn;
    private static StaffTerrain get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(StaffTerrain::load,StaffTerrain::new,ID);
    }
    public static Vec3 forward(ServerPlayer player) {
        Vec3 direction=new Vec3(player.getLookAngle().x,0,player.getLookAngle().z).normalize();
        return direction.lengthSqr()<.1?Vec3.atLowerCornerOf(player.getDirection().getNormal()):direction;
    }
    public static boolean withinSplit(Vec3 point,Vec3 origin,Vec3 direction,int length,int width) {
        Vec3 relative=point.subtract(origin); double along=relative.dot(direction);
        double across=Math.abs(relative.x*direction.z-relative.z*direction.x);
        return along>=0&&along<length&&across<width*.5*Math.sin(Math.PI*along/length);
    }
    public static boolean split(ServerPlayer player) {
        ServerLevel level=player.serverLevel(); Job job=new Job(); job.kind=0; job.owner=player.getUUID();
        job.origin=player.position().add(forward(player).scale(3));job.direction=forward(player);
        job.length=StaffConfig.SPLIT_LENGTH.get();job.width=StaffConfig.SPLIT_WIDTH.get();
        job.top=Math.min(level.getMaxBuildHeight()-1,StaffConfig.SPLIT_TOP.get());job.bottom=level.getMinBuildHeight();
        job.columns=splitColumns(job);job.nextY=new int[job.columns.length];Arrays.fill(job.nextY,job.top);
        job.impactAt=level.getGameTime()+140;
        StaffTerrain data=get(level);data.jobs.add(job);data.setDirty();
        boolean targets=!level.getEntities((Entity)null,job.bounds(),e->!e.getUUID().equals(job.owner)&&withinSplit(e.position(),job.origin,job.direction,job.length,job.width)).isEmpty();
        StaffCasting.send(level,new StaffEffectPacket(StaffEffectPacket.SPLIT,job.owner,job.origin,
                job.origin.add(job.direction.scale(job.length)),140,targets?1:0));
        return true;
    }
    private static BlockPos[] splitColumns(Job job) {
        Vec3 end=job.origin.add(job.direction.scale(job.length)); List<BlockPos> columns=new ArrayList<>();
        int left=(int)Math.floor(Math.min(job.origin.x,end.x)-job.width*.5),right=(int)Math.ceil(Math.max(job.origin.x,end.x)+job.width*.5);
        int front=(int)Math.floor(Math.min(job.origin.z,end.z)-job.width*.5),back=(int)Math.ceil(Math.max(job.origin.z,end.z)+job.width*.5);
        for(int x=left;x<=right;x++)for(int z=front;z<=back;z++)
            if(withinSplit(new Vec3(x+.5,0,z+.5),job.origin,job.direction,job.length,job.width))columns.add(new BlockPos(x,0,z));
        columns.sort(Comparator.comparingDouble(p->Vec3.atCenterOf(p).subtract(job.origin).dot(job.direction)));
        return columns.toArray(BlockPos[]::new);
    }
    public static boolean lift(ServerPlayer player) {
        ServerLevel level=player.serverLevel(); Vec3 selected=player.position().add(forward(player).scale(16));
        ChunkPos chunk=new ChunkPos(BlockPos.containing(selected)); Job job=new Job();job.kind=1;job.owner=player.getUUID();
        job.origin=new Vec3(chunk.getMinBlockX(),level.getMinBuildHeight(),chunk.getMinBlockZ());
        job.lift=StaffConfig.LIFT_HEIGHT.get();job.blocks=new ArrayList<>();
        level.getChunk(chunk.x,chunk.z);
        for(int x=chunk.getMinBlockX();x<=chunk.getMaxBlockX();x++)for(int z=chunk.getMinBlockZ();z<=chunk.getMaxBlockZ();z++) {
            int top=level.getHeight(Heightmap.Types.WORLD_SURFACE,x,z)-1;
            if(top+job.lift>=level.getMaxBuildHeight())return StaffCasting.fail(player,"lift_space");
            for(int y=top;y>top-StaffConfig.LIFT_DEPTH.get()&&y>=level.getMinBuildHeight();y--) {
                BlockPos pos=new BlockPos(x,y,z);BlockState state=level.getBlockState(pos);
                if(state.isAir()||state.is(Blocks.BEDROCK))continue;
                BlockPos target=pos.above(job.lift);
                if(!level.getBlockState(target).isAir())return StaffCasting.fail(player,"lift_space");
                BlockEntity tile=level.getBlockEntity(pos);
                job.blocks.add(new MovedBlock(pos,state,tile==null?null:tile.saveWithFullMetadata()));
            }
        }
        if(job.blocks.isEmpty())return StaffCasting.fail(player,"no_ground");
        for(Job existing:get(level).jobs)if(existing.kind==1&&existing.origin.equals(job.origin))return StaffCasting.fail(player,"terrain_busy");
        get(level).jobs.add(job);get(level).setDirty();return true;
    }
    @SubscribeEvent public static void tick(TickEvent.LevelTickEvent event) {
        if(event.phase!=TickEvent.Phase.END||!(event.level instanceof ServerLevel level)||TimeStop.active(level))return;
        StaffTerrain data=get(level); if(data.jobs.isEmpty())return;
        long deadline=System.nanoTime()+4_000_000;int size=data.jobs.size();
        for(int i=0;i<size&&System.nanoTime()<deadline;i++) {
            Job job=data.jobs.get((data.turn+i)%size);
            if(job.kind==0)job.splitTick(level,deadline);else job.liftTick(level,deadline);
        }
        data.turn=(data.turn+1)%size;data.jobs.removeIf(j->j.done);data.setDirty();
    }
    private record MovedBlock(BlockPos pos,BlockState state,CompoundTag tile) {
        CompoundTag save(){CompoundTag tag=new CompoundTag();tag.putLong("Pos",pos.asLong());tag.put("State",NbtUtils.writeBlockState(state));if(tile!=null)tag.put("Tile",tile);return tag;}
    }
    private static final class Job {
        int kind,length,width,top,bottom,lift,cursor,stage;long impactAt,flightAt;
        UUID owner;Vec3 origin,direction=Vec3.ZERO;BlockPos[] columns;int[] nextY;List<MovedBlock> blocks;
        boolean hit,done;
        AABB bounds(){return new AABB(origin,origin.add(direction.scale(length))).inflate(width*.5).setMinY(bottom).setMaxY(top+120);}
        void splitTick(ServerLevel level,long deadline) {
            if(!hit&&level.getGameTime()>=impactAt) {
                DamageSource damage=new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
                        .getHolderOrThrow(ResourceKey.create(Registries.DAMAGE_TYPE,new ResourceLocation("sourceofmystery","staff_earth_split"))),level.getEntity(owner));
                for(Entity target:level.getEntities((Entity)null,bounds(),e->!e.getUUID().equals(owner)&&!e.isSpectator()
                        &&withinSplit(e.position(),origin,direction,length,width)))target.hurt(damage,StaffConfig.SPLIT_DAMAGE.get().floatValue());
                hit=true;
            }
            int visited=0,changed=0;
            while(cursor<columns.length&&visited<16384&&changed<4096&&System.nanoTime()<deadline) {
                BlockPos column=columns[cursor];
                if(!level.hasChunkAt(column)){level.getChunk(column.getX()>>4,column.getZ()>>4);return;}
                if(nextY[cursor]<bottom){cursor++;continue;}
                BlockPos pos=column.atY(nextY[cursor]--);visited++;
                BlockState state=level.getBlockState(pos);
                if(!state.isAir()&&!state.is(Blocks.BEDROCK)) {
                    level.removeBlockEntity(pos);level.setBlock(pos,Blocks.AIR.defaultBlockState(),18);changed++;
                }
            }
            done=cursor==columns.length&&hit;
        }
        void liftTick(ServerLevel level,long deadline) {
            if(stage==0) {
                for(int count=0;cursor<blocks.size()&&count<2048&&System.nanoTime()<deadline;count++,cursor++) {
                    MovedBlock block=blocks.get(cursor);level.getChunkAt(block.pos);
                    level.removeBlockEntity(block.pos);level.setBlock(block.pos,Blocks.AIR.defaultBlockState(),18);
                }
                if(cursor<blocks.size())return;
                stage=1;cursor=0;flightAt=level.getGameTime();
                Set<BlockPos> occupied=new HashSet<>();for(MovedBlock block:blocks)occupied.add(block.pos);
                List<Integer> visible=new ArrayList<>();
                for(MovedBlock block:blocks) {
                    boolean exposed=false;for(Direction side:Direction.values())if(!occupied.contains(block.pos.relative(side))){exposed=true;break;}
                    if(!exposed)continue;
                    int local=((block.pos.getY()-(int)origin.y)<<8)|((block.pos.getZ()&15)<<4)|(block.pos.getX()&15);
                    visible.add(local);visible.add(Block.getId(block.state));
                }
                StaffCasting.send(level,new StaffEffectPacket(StaffEffectPacket.LIFT,owner,origin,origin.add(0,lift,0),120,0,
                        visible.stream().mapToInt(Integer::intValue).toArray()));
            }
            if(stage==1) {if(level.getGameTime()<flightAt+100)return;stage=2;}
            for(int count=0;cursor<blocks.size()&&count<2048&&System.nanoTime()<deadline;count++,cursor++) {
                MovedBlock block=blocks.get(cursor);BlockPos target=block.pos.above(lift);
                level.setBlock(target,block.state,18);
                if(block.tile!=null) {
                    CompoundTag tag=block.tile.copy();tag.putInt("x",target.getX());tag.putInt("y",target.getY());tag.putInt("z",target.getZ());
                    BlockEntity tile=level.getBlockEntity(target);
                    if(tile!=null){tile.load(tag);tile.setChanged();level.sendBlockUpdated(target,block.state,block.state,3);}
                }
            }
            if(cursor==blocks.size()) {
                done=true;
                StaffCasting.send(level,new StaffEffectPacket(StaffEffectPacket.LIFT,owner,origin,origin.add(0,lift,0),0,0));
            }
        }
        CompoundTag save() {
            CompoundTag tag=new CompoundTag();tag.putInt("Kind",kind);tag.putUUID("Owner",owner);
            vector(tag,"Origin",origin);vector(tag,"Direction",direction);
            tag.putInt("Length",length);tag.putInt("Width",width);tag.putInt("Top",top);tag.putInt("Bottom",bottom);
            tag.putInt("Lift",lift);tag.putInt("Cursor",cursor);tag.putInt("Stage",stage);tag.putLong("ImpactAt",impactAt);
            tag.putLong("FlightAt",flightAt);tag.putBoolean("Hit",hit);
            if(nextY!=null)tag.putIntArray("NextY",nextY);
            if(blocks!=null){ListTag values=new ListTag();for(MovedBlock block:blocks)values.add(block.save());tag.put("Blocks",values);}
            return tag;
        }
    }
    private static void vector(CompoundTag tag,String key,Vec3 v){CompoundTag t=new CompoundTag();t.putDouble("X",v.x);t.putDouble("Y",v.y);t.putDouble("Z",v.z);tag.put(key,t);}
    private static Vec3 vector(CompoundTag tag,String key){CompoundTag t=tag.getCompound(key);return new Vec3(t.getDouble("X"),t.getDouble("Y"),t.getDouble("Z"));}
    static StaffTerrain load(CompoundTag tag) {
        StaffTerrain data=new StaffTerrain();
        for(Tag value:tag.getList("Jobs",Tag.TAG_COMPOUND)) {
            CompoundTag t=(CompoundTag)value;Job j=new Job();j.kind=t.getInt("Kind");j.owner=t.getUUID("Owner");
            j.origin=vector(t,"Origin");j.direction=vector(t,"Direction");j.length=t.getInt("Length");j.width=t.getInt("Width");
            j.top=t.getInt("Top");j.bottom=t.getInt("Bottom");j.lift=t.getInt("Lift");j.cursor=t.getInt("Cursor");j.stage=t.getInt("Stage");
            j.impactAt=t.getLong("ImpactAt");j.flightAt=t.getLong("FlightAt");j.hit=t.getBoolean("Hit");
            if(j.kind==0){j.columns=splitColumns(j);j.nextY=t.getIntArray("NextY");}
            else {j.blocks=new ArrayList<>();for(Tag entry:t.getList("Blocks",Tag.TAG_COMPOUND)) {
                CompoundTag b=(CompoundTag)entry;
                j.blocks.add(new MovedBlock(BlockPos.of(b.getLong("Pos")),NbtUtils.readBlockState(net.minecraft.core.registries.BuiltInRegistries.BLOCK.asLookup(),b.getCompound("State")),b.contains("Tile")?b.getCompound("Tile"):null));
            }}
            data.jobs.add(j);
        }
        return data;
    }
    @Override public CompoundTag save(CompoundTag tag) {ListTag list=new ListTag();for(Job job:jobs)list.add(job.save());tag.put("Jobs",list);return tag;}
}
