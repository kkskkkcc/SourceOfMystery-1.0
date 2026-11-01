package com.sourceofmystery.entity;

import com.sourceofmystery.SourceOfMystery;
import com.sourceofmystery.guardian.GuardianShields;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.event.ForgeEventFactory;
import java.util.*;

/** Persistent, dimension-local terrain work; independent of caster/effect life. */
@Mod.EventBusSubscriber(modid=SourceOfMystery.MOD_ID)
public final class OutsiderBlastTasks extends SavedData {
    private static final String NAME="sourceofmystery_outsider_blasts";
    private static final int NEW=Integer.MIN_VALUE,DONE=Integer.MAX_VALUE;
    private static final int[][] COLUMNS=columns();
    private final List<Job> jobs=new ArrayList<>();
    private int turn;
    private static int[][] columns(){
        List<int[]> result=new ArrayList<>();int r=OutsiderSpell.BLAST_RADIUS;
        // Fractional impact centres can include cells just beyond the integer
        // circle. Exact sphere membership is checked per job below.
        for(int x=-r;x<=r;x++)for(int z=-r;z<=r;z++)if(x*x+z*z<=(r+1)*(r+1))result.add(new int[]{x,z});
        result.sort(Comparator.comparingInt(p->p[0]*p[0]+p[1]*p[1]));
        return result.toArray(int[][]::new);
    }
    static OutsiderBlastTasks get(ServerLevel level){
        return level.getDataStorage().computeIfAbsent(OutsiderBlastTasks::load,OutsiderBlastTasks::new,NAME);
    }
    static boolean enqueue(ServerLevel level,LivingEntity owner,BlockPos centre){
        return enqueue(level,owner,Vec3.atCenterOf(centre));
    }
    static boolean enqueue(ServerLevel level,LivingEntity owner,Vec3 centre){
        boolean playerCast=owner instanceof net.minecraft.world.entity.player.Player;
        if(!playerCast&&!ForgeEventFactory.getMobGriefingEvent(level,owner)){
            SourceOfMystery.LOGGER.info("Outsider meteor terrain disabled by mobGriefing/event at {}",centre);
            return false;
        }
        var data=get(level);Job job=new Job(centre,owner.getUUID());job.playerCast=playerCast;job.source=owner;data.jobs.add(job);data.setDirty();
        SourceOfMystery.LOGGER.info("Outsider meteor terrain queued at {} (radius {})",centre,OutsiderSpell.BLAST_RADIUS);
        return true;
    }
    static boolean containsBlock(Vec3 centre,BlockPos pos){
        double x=pos.getX()+.5-centre.x,y=pos.getY()+.5-centre.y,z=pos.getZ()+.5-centre.z;
        return x*x+y*y+z*z<=OutsiderSpell.BLAST_RADIUS*OutsiderSpell.BLAST_RADIUS+1e-9;
    }
    @SubscribeEvent public static void tick(TickEvent.LevelTickEvent event){
        if(event.phase==TickEvent.Phase.END&&event.level instanceof ServerLevel server)get(server).advance(server);
    }
    private static final class Budget {int visited,broken;long deadline=System.nanoTime()+2_000_000;}
    private void advance(ServerLevel level){
        if(com.sourceofmystery.staff.TimeStop.active(level))return;
        if(jobs.isEmpty())return;
        Budget budget=new Budget();int count=jobs.size();
        // Rotate the first job so simultaneous blasts share the budget fairly.
        for(int n=0;n<count;n++){
            Job job=jobs.get((turn+n)%count);
            if(level.getGameTime()<job.retryAt)continue;
            boolean progress=job.advance(level,budget);
            if(progress)setDirty();else job.retryAt=level.getGameTime()+20;
            if(budget.visited>=8192||budget.broken>=1024||System.nanoTime()>=budget.deadline)break;
        }
        turn=(turn+1)%count;
        if(jobs.removeIf(job->job.remaining==0))setDirty();
    }
    private static final class Job {
        final Vec3 centre;final BlockPos anchor;final UUID owner;
        int[] nextY=new int[COLUMNS.length];int cursor,remaining=COLUMNS.length,broken;
        long retryAt;LivingEntity source;boolean playerCast;
        Job(Vec3 centre,UUID owner){this.centre=centre;this.anchor=BlockPos.containing(centre);this.owner=owner;Arrays.fill(nextY,NEW);}
        boolean advance(ServerLevel level,Budget budget){
            if(source==null){
                if(level.getEntity(owner) instanceof LivingEntity living)source=living;
                else {
                    // An unspawned attribution object preserves Forge's living
                    // block-destroy hooks after the caster dies or unloads.
                    source=new OutsiderEntity(ModEntities.OUTSIDER.get(),level);
                    source.setUUID(owner);source.setPos(centre);
                }
            }
            if(!playerCast&&!ForgeEventFactory.getMobGriefingEvent(level,source))return false;
            boolean progress=false;BlockPos.MutableBlockPos pos=new BlockPos.MutableBlockPos();
            for(int tried=0;tried<COLUMNS.length&&budget.visited<8192&&budget.broken<1024&&System.nanoTime()<budget.deadline;tried++){
                int i=cursor;cursor=(cursor+1)%COLUMNS.length;budget.visited++;
                if(nextY[i]==DONE)continue;
                int dx=COLUMNS[i][0],dz=COLUMNS[i][1];
                pos.set(anchor.getX()+dx,anchor.getY(),anchor.getZ()+dz);
                // Keep the column pending. Do not load or permanently skip it.
                if(!level.hasChunkAt(pos))continue;
                int r=OutsiderSpell.BLAST_RADIUS;
                double x=pos.getX()+.5-centre.x,z=pos.getZ()+.5-centre.z;
                double square=r*r-x*x-z*z;
                if(square<0){nextY[i]=DONE;remaining--;progress=true;continue;}
                double extent=Math.sqrt(square);
                int bottom=Math.max((int)Math.ceil(centre.y-extent-.5-1e-9),level.getMinBuildHeight());
                int top=Math.min((int)Math.floor(centre.y+extent-.5+1e-9),level.getMaxBuildHeight()-1);
                int surface=level.getHeight(Heightmap.Types.WORLD_SURFACE,pos.getX(),pos.getZ())-1;
                if(nextY[i]==NEW)nextY[i]=Math.min(top,surface);
                else nextY[i]=Math.min(nextY[i],surface);
                progress=true;
                // Remove a shallow layer per visit, exposing the visible crater
                // across the field before spending the budget deep underground.
                for(int k=0;k<4&&nextY[i]>=bottom&&budget.visited<8192&&budget.broken<1024&&System.nanoTime()<budget.deadline;k++){
                    pos.setY(nextY[i]--);budget.visited++;
                    var state=level.getBlockState(pos);
                    if(containsBlock(centre,pos)&&!state.isAir()&&state.getDestroySpeed(level,pos)>=0&&state.getBlock().getExplosionResistance()<1200
                            &&!GuardianShields.isProtected(level,pos)&&ForgeEventFactory.onEntityDestroyBlock(source,pos,state)
                            &&level.destroyBlock(pos,false,source)){
                        budget.broken++;broken++;
                    }
                }
                if(nextY[i]<bottom){nextY[i]=DONE;remaining--;}
            }
            if(remaining==0)SourceOfMystery.LOGGER.info("Outsider meteor terrain completed at {}: {} blocks removed",centre,broken);
            return progress;
        }
        CompoundTag save(){
            CompoundTag tag=new CompoundTag();tag.putLong("Centre",anchor.asLong());tag.putUUID("Owner",owner);
            tag.putDouble("CentreX",centre.x);tag.putDouble("CentreY",centre.y);tag.putDouble("CentreZ",centre.z);
            tag.putIntArray("NextY",nextY);tag.putInt("Cursor",cursor);tag.putInt("Broken",broken);tag.putBoolean("PlayerCast",playerCast);return tag;
        }
    }
    static OutsiderBlastTasks load(CompoundTag tag){
        OutsiderBlastTasks data=new OutsiderBlastTasks();
        for(Tag entry:tag.getList("Jobs",Tag.TAG_COMPOUND)){
            CompoundTag t=(CompoundTag)entry;
            Vec3 centre=t.contains("CentreX")?new Vec3(t.getDouble("CentreX"),t.getDouble("CentreY"),t.getDouble("CentreZ"))
                    :Vec3.atCenterOf(BlockPos.of(t.getLong("Centre")));
            Job j=new Job(centre,t.getUUID("Owner"));
            j.playerCast=t.getBoolean("PlayerCast");
            int[] saved=t.getIntArray("NextY");if(saved.length==COLUMNS.length)j.nextY=saved;
            j.remaining=(int)Arrays.stream(j.nextY).filter(y->y!=DONE).count();
            j.cursor=Math.floorMod(t.getInt("Cursor"),COLUMNS.length);j.broken=t.getInt("Broken");data.jobs.add(j);
        }
        return data;
    }
    @Override public CompoundTag save(CompoundTag tag){
        ListTag list=new ListTag();for(Job j:jobs)list.add(j.save());tag.put("Jobs",list);return tag;
    }
}
