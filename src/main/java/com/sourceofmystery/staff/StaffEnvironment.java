package com.sourceofmystery.staff;

import com.sourceofmystery.network.StaffEffectPacket;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.*;
import net.minecraft.server.level.*;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.*;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.*;

/** Saved, bounded terraforming jobs. Only natural terrain is replaced; block entities are never touched. */
@Mod.EventBusSubscriber(modid="sourceofmystery")
public final class StaffEnvironment extends SavedData {
    public static final int RADIUS=500;
    public static final String BIOME_KEY="StaffEnvironmentBiome";
    private static final String DATA_ID="sourceofmystery_staff_environment";
    private static final TicketType<UUID> ENVIRONMENT_TICKET=TicketType.create("sourceofmystery_environment",Comparator.comparing(UUID::toString));
    private static final ResourceLocation DEFAULT=new ResourceLocation("minecraft","plains");
    private final List<Job> jobs=new ArrayList<>();
    private int turn;

    public static StaffEnvironment get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(StaffEnvironment::load,StaffEnvironment::new,DATA_ID);
    }
    public static List<ResourceLocation> choices(RegistryAccess access) {
        return access.registryOrThrow(Registries.BIOME).keySet().stream()
                .filter(id->id.getNamespace().equals("minecraft")).sorted().toList();
    }
    public static ResourceLocation selected(ItemStack stack) {
        ResourceLocation id=stack.hasTag()?ResourceLocation.tryParse(stack.getTag().getString(BIOME_KEY)):null;
        return id!=null && id.getNamespace().equals("minecraft")?id:DEFAULT;
    }
    public static Component title(ResourceLocation id) {
        return Component.translatable("biome."+id.getNamespace()+"."+id.getPath());
    }
    public static void cycle(ServerPlayer player,ItemStack stack) {
        if(TimeStop.frozen(player)||player.isUsingItem())return;
        List<ResourceLocation> ids=choices(player.serverLevel().registryAccess());
        if(ids.isEmpty())return;
        int index=(ids.indexOf(selected(stack))+1)%ids.size();
        ResourceLocation id=ids.get(index);
        stack.getOrCreateTag().putString(BIOME_KEY,id.toString());
        player.getInventory().setChanged();player.containerMenu.broadcastChanges();
        player.displayClientMessage(Component.translatable("message.sourceofmystery.staff.environment_selected",title(id),index+1,ids.size()),true);
    }
    public static boolean start(ServerPlayer player,ResourceLocation id) {
        ServerLevel level=player.serverLevel();
        if(!choices(level.registryAccess()).contains(id))return StaffCasting.fail(player,"environment_missing");
        StaffEnvironment data=get(level);Vec3 origin=player.position();
        Job job=new Job(player.getUUID(),origin,id,RADIUS);
        // A newer cast owns its disk permanently, even if it finishes before an
        // older, partially overlapping conversion. Fully covered jobs can retire.
        List<Job> replaced=data.jobs.stream().filter(old->job.area().covers(old.area())).toList();
        if(data.jobs.size()-replaced.size()>=4)return StaffCasting.fail(player,"environment_busy");
        for(Job old:data.jobs)if(!replaced.contains(old)&&old.area().overlaps(job.area()))old.exclude(job.area());
        for(Job old:replaced)old.releaseChunk(level);
        data.jobs.removeAll(replaced);
        data.jobs.add(job);data.setDirty();
        StaffCasting.send(level,new StaffEffectPacket(StaffEffectPacket.ENVIRONMENT,job.owner,origin,
                new Vec3(RADIUS,0,0),100,job.palette.ordinal()));
        player.displayClientMessage(Component.translatable("message.sourceofmystery.staff.environment_started",title(id)),true);
        return true;
    }
    public boolean busy() { return !jobs.isEmpty(); }
    /** Releases transient chunk tickets before replacing a saved-data instance during checkpoint recovery. */
    public void releaseTickets(ServerLevel level) { for(Job job:jobs)job.releaseChunk(level); }
    @SubscribeEvent public static void tick(TickEvent.LevelTickEvent event) {
        if(event.phase!=TickEvent.Phase.END || !(event.level instanceof ServerLevel level) || TimeStop.active(level))return;
        StaffEnvironment data=get(level);if(data.jobs.isEmpty())return;
        // One shared budget for all casters, including chunk loads and vegetation.
        long deadline=System.nanoTime()+3_000_000;
        int size=data.jobs.size();
        for(int i=0;i<size && System.nanoTime()<deadline;i++) {
            Job job=data.jobs.get((data.turn+i)%size);job.tick(level,deadline);
        }
        data.turn=(data.turn+1)%size;
        data.jobs.removeIf(j->j.done);data.setDirty();
    }
    private static boolean ground(BlockState s) {
        return s.is(BlockTags.DIRT)||s.is(BlockTags.SAND)||s.is(BlockTags.BASE_STONE_OVERWORLD)||s.is(BlockTags.BASE_STONE_NETHER)
                ||s.is(BlockTags.TERRACOTTA)||s.is(Blocks.END_STONE)||s.is(Blocks.GRAVEL)||s.is(Blocks.CLAY)
                ||s.is(Blocks.MUD)||s.is(Blocks.MYCELIUM)||s.is(Blocks.SNOW_BLOCK)||s.is(Blocks.ICE)||s.is(Blocks.PACKED_ICE)
                ||s.is(Blocks.BLUE_ICE)||s.is(Blocks.MOSS_BLOCK)||s.is(Blocks.SCULK)||s.is(Blocks.DRIPSTONE_BLOCK)
                ||s.is(Blocks.SOUL_SAND)||s.is(Blocks.SOUL_SOIL)||s.is(Blocks.CRIMSON_NYLIUM)||s.is(Blocks.WARPED_NYLIUM)
                ||s.is(Blocks.SANDSTONE)||s.is(Blocks.RED_SANDSTONE)||s.is(Blocks.MAGMA_BLOCK)
                ||s.is(Blocks.BRAIN_CORAL_BLOCK)||s.is(Blocks.TUBE_CORAL_BLOCK);
    }
    private static boolean flora(BlockState s) {
        return s.is(BlockTags.LEAVES)||s.is(BlockTags.LOGS)||s.getBlock() instanceof BushBlock
                ||s.is(Blocks.VINE)||s.is(Blocks.GLOW_LICHEN)||s.is(Blocks.SNOW)||s.is(Blocks.CACTUS)||s.is(Blocks.SUGAR_CANE)
                ||s.is(Blocks.BAMBOO)||s.is(Blocks.BAMBOO_SAPLING)||s.is(Blocks.CHORUS_PLANT)||s.is(Blocks.CHORUS_FLOWER)
                ||s.is(Blocks.NETHER_WART_BLOCK)||s.is(Blocks.WARPED_WART_BLOCK)||s.is(Blocks.SHROOMLIGHT)
                ||s.is(Blocks.RED_MUSHROOM_BLOCK)||s.is(Blocks.BROWN_MUSHROOM_BLOCK)||s.is(Blocks.MUSHROOM_STEM)
                ||s.is(Blocks.MANGROVE_ROOTS)||s.is(Blocks.MUDDY_MANGROVE_ROOTS)
                ||s.is(Blocks.WEEPING_VINES)||s.is(Blocks.WEEPING_VINES_PLANT)||s.is(Blocks.TWISTING_VINES)||s.is(Blocks.TWISTING_VINES_PLANT)
                ||s.is(Blocks.POINTED_DRIPSTONE)||s.is(Blocks.SPORE_BLOSSOM)||s.is(Blocks.SCULK_VEIN);
    }
    private static boolean liquid(BlockState s) { return s.is(Blocks.WATER)||s.is(Blocks.LAVA); }
    private static boolean natural(BlockState s) { return !s.hasBlockEntity()&&(ground(s)||flora(s)||liquid(s)||s.isAir()); }

    private record Area(double x,double z,int radius) {
        boolean contains(double px,double pz){return (px-x)*(px-x)+(pz-z)*(pz-z)<=radius*(double)radius;}
        double distance(Area other){return Math.hypot(x-other.x,z-other.z);}
        boolean covers(Area other){return distance(other)+other.radius<=radius;}
        boolean overlaps(Area other){return distance(other)<radius+(double)other.radius;}
        boolean covers(ChunkPos chunk){return contains(chunk.getMinBlockX()+.5,chunk.getMinBlockZ()+.5)
                &&contains(chunk.getMaxBlockX()+.5,chunk.getMinBlockZ()+.5)
                &&contains(chunk.getMinBlockX()+.5,chunk.getMaxBlockZ()+.5)
                &&contains(chunk.getMaxBlockX()+.5,chunk.getMaxBlockZ()+.5);}
        CompoundTag save(){var tag=new CompoundTag();tag.putDouble("X",x);tag.putDouble("Z",z);tag.putInt("Radius",radius);return tag;}
    }

    private static final class Job {
        final UUID owner;final Vec3 origin;final ResourceLocation biome;final EnvironmentPalette palette;
        UUID token=UUID.randomUUID();
        final List<Area> excluded=new ArrayList<>();
        final int radius;
        final List<ChunkPos> chunks=new ArrayList<>();
        int cursor,stage,columnCursor;boolean done;
        float previousFront;
        long lastFrontTick=Long.MIN_VALUE;
        private BlockPos[] columns;
        private LevelChunk activeChunk;
        private java.util.concurrent.CompletableFuture<com.mojang.datafixers.util.Either<ChunkAccess,ChunkHolder.ChunkLoadingFailure>> pending;
        private ChunkPos ticket;
        Job(UUID owner,Vec3 origin,ResourceLocation biome,int radius) {
            this.owner=owner;this.origin=origin;this.biome=biome;this.radius=radius;palette=EnvironmentPalette.of(biome);
            int minX=((int)Math.floor(origin.x)-radius)>>4,maxX=((int)Math.floor(origin.x)+radius)>>4;
            int minZ=((int)Math.floor(origin.z)-radius)>>4,maxZ=((int)Math.floor(origin.z)+radius)>>4;
            for(int x=minX;x<=maxX;x++)for(int z=minZ;z<=maxZ;z++) {
                double nearX=Math.max(x*16+.5,Math.min(x*16+15.5,origin.x));
                double nearZ=Math.max(z*16+.5,Math.min(z*16+15.5,origin.z));
                if(inside(nearX,nearZ))chunks.add(new ChunkPos(x,z));
            }
            chunks.sort(Comparator.comparingDouble(p->distance(p.getMinBlockX()+8,p.getMinBlockZ()+8)));
        }
        double distance(double x,double z) { return (x-origin.x)*(x-origin.x)+(z-origin.z)*(z-origin.z); }
        boolean inside(double x,double z) { return distance(x,z)<=radius*radius; }
        Area area(){return new Area(origin.x,origin.z,radius);}
        void exclude(Area newer){
            if(excluded.stream().anyMatch(a->a.covers(newer)))return;
            excluded.removeIf(newer::covers);excluded.add(newer);
        }
        boolean claimed(double x,double z){return inside(x,z)&&excluded.stream().noneMatch(a->a.contains(x,z));}
        boolean editable(ServerLevel level,BlockPos p) {
            return !level.isOutsideBuildHeight(p)&&level.getWorldBorder().isWithinBounds(p)&&claimed(p.getX()+.5,p.getZ()+.5)
                    &&natural(level.getBlockState(p));
        }
        void set(ServerLevel level,BlockPos p,BlockState state) {
            if(editable(level,p))level.setBlock(p,state,18);
        }
        private void releaseChunk(ServerLevel level) {
            if(ticket!=null)level.getChunkSource().removeRegionTicket(ENVIRONMENT_TICKET,ticket,0,token);
            ticket=null;pending=null;activeChunk=null;columns=null;
        }
        void tick(ServerLevel level,long deadline) {
            int count=0,completedChunks=0;
            // Continue immediately through already-ready chunks; wait only for genuinely pending I/O.
            while(count<512 && completedChunks<4 && System.nanoTime()<deadline) {
                if(cursor>=chunks.size()) {
                    releaseChunk(level);cursor=0;columnCursor=0;stage++;
                    if(stage>1){finish(level);return;}
                }
                ChunkPos pos=chunks.get(cursor);
                if(excluded.stream().anyMatch(a->a.covers(pos))){releaseChunk(level);cursor++;columnCursor=0;continue;}
                if(!level.getWorldBorder().isWithinBounds(pos)) {cursor++;columnCursor=0;completedChunks++;continue;}
                if(activeChunk==null) {
                    if(pending==null) {
                        ticket=pos;
                        level.getChunkSource().addRegionTicket(ENVIRONMENT_TICKET,pos,0,token);
                        pending=level.getChunkSource().getChunkFuture(pos.x,pos.z,ChunkStatus.FULL,true);
                    }
                    if(!pending.isDone())return;
                    ChunkAccess loaded;
                    try {loaded=pending.getNow(null).left().orElse(null);}
                    catch(java.util.concurrent.CompletionException error) {
                        com.sourceofmystery.SourceOfMystery.LOGGER.error("Environment chunk loading failed at {}",pos,error);
                        releaseChunk(level);cursor++;columnCursor=0;return;
                    }
                    if(!(loaded instanceof LevelChunk)) {releaseChunk(level);cursor++;columnCursor=0;return;}
                    activeChunk=(LevelChunk)loaded;
                    List<BlockPos> local=new ArrayList<>(256);
                    for(int x=pos.getMinBlockX();x<=pos.getMaxBlockX();x++)for(int z=pos.getMinBlockZ();z<=pos.getMaxBlockZ();z++)
                        if(inside(x+.5,z+.5))local.add(new BlockPos(x,0,z));
                    local.sort(Comparator.comparingDouble(p->distance(p.getX()+.5,p.getZ()+.5)));
                    columns=local.toArray(BlockPos[]::new);
                    columnCursor=Math.min(columnCursor,columns.length);
                }
                while(columnCursor<columns.length&&count<512&&System.nanoTime()<deadline) {
                    BlockPos p=columns[columnCursor++];count++;
                    if(!claimed(p.getX()+.5,p.getZ()+.5))continue;
                    if(!level.getWorldBorder().isWithinBounds(p.atY((int)origin.y)))continue;
                    if(stage==0)surface(level,p);else decorate(level,p);
                }
                if(stage==0&&level.getGameTime()%20==0&&lastFrontTick!=level.getGameTime()) {
                    float current=Math.min(radius,(float)Math.sqrt(distance(pos.getMinBlockX()+8,pos.getMinBlockZ()+8)));
                    current=Math.max(previousFront,current);
                    StaffCasting.send(level,new StaffEffectPacket(StaffEffectPacket.ENVIRONMENT_FRONT,token,origin,
                            new Vec3(current,previousFront,0),45,palette.ordinal()));previousFront=current;
                    lastFrontTick=level.getGameTime();
                }
                if(columnCursor==columns.length) {
                    if(stage==1)biomes(level,activeChunk);
                    cursor++;columnCursor=0;releaseChunk(level);completedChunks++;
                }
            }
        }
        private void biomes(ServerLevel level,LevelChunk chunk) {
            var registry=level.registryAccess().registryOrThrow(Registries.BIOME);
            Holder<Biome> replacement=registry.getHolder(ResourceKey.create(Registries.BIOME,biome)).orElse(null);
            if(replacement==null)return;
            ChunkPos pos=chunk.getPos();
            int minY=QuartPos.fromBlock(level.getMinBuildHeight()),maxY=QuartPos.fromBlock(level.getMaxBuildHeight()-1);
            // A small fixed array for this one chunk, rather than a map for the whole 1000-block disk.
            @SuppressWarnings("unchecked") Holder<Biome>[] old=new Holder[(maxY-minY+1)*16];
            for(int x=0;x<4;x++)for(int z=0;z<4;z++)for(int y=minY;y<=maxY;y++)
                old[(y-minY)*16+z*4+x]=chunk.getNoiseBiome(pos.x*4+x,y,pos.z*4+z);
            chunk.fillBiomesFromNoise((x,y,z,sampler)->claimed(QuartPos.toBlock(x)+2,QuartPos.toBlock(z)+2)
                    &&level.getWorldBorder().isWithinBounds(new BlockPos(QuartPos.toBlock(x)+2,(int)origin.y,QuartPos.toBlock(z)+2))
                    ?replacement:old[(y-minY)*16+(z&3)*4+(x&3)],level.getChunkSource().randomState().sampler());
            chunk.setUnsaved(true);level.getChunkSource().chunkMap.resendBiomesForChunks(List.of(chunk));
        }
        int top(ServerLevel level,BlockPos p) {
            int top=level.getHeight(Heightmap.Types.WORLD_SURFACE,p.getX(),p.getZ())-1;
            // In roofed dimensions work on the playable surface near the caster, not the bedrock ceiling.
            if(level.dimensionType().hasCeiling())top=Math.min(top,(int)origin.y+20);
            return Math.min(level.getMaxBuildHeight()-2,top);
        }
        void surface(ServerLevel level,BlockPos p) {
            int top=top(level,p),floor=top,waterTop=Integer.MIN_VALUE;
            for(int y=top;y>level.getMinBuildHeight();y--) {
                BlockState s=level.getBlockState(p.atY(y));
                if(liquid(s)){waterTop=y;break;}
                if(!s.isAir()&&!flora(s))break;
            }
            while(floor>level.getMinBuildHeight() && (flora(level.getBlockState(p.atY(floor)))||liquid(level.getBlockState(p.atY(floor)))||level.isEmptyBlock(p.atY(floor))))floor--;
            if(!ground(level.getBlockState(p.atY(floor))))return;
            // A chest, machine, placed stone brick, etc. anywhere in the edited column protects the column.
            int protectBottom=palette.aquatic()?Math.min(floor-3,(int)Math.floor(origin.y)-14):floor-3;
            for(int y=protectBottom;y<=top;y++)if(y>=level.getMinBuildHeight()) {
                BlockState s=level.getBlockState(p.atY(y));
                if(!natural(s)&&!s.is(Blocks.BEDROCK))return;
            }
            for(int y=top;y>floor;y--)set(level,p.atY(y),Blocks.AIR.defaultBlockState());
            if(palette.aquatic()) {
                int waterline=Math.min(top,(int)Math.floor(origin.y)-1);
                int depth=biome.getPath().contains("deep")?10:biome.getPath().contains("river")?3:6;
                int bed=Math.max(level.getMinBuildHeight()+1,waterline-depth);
                // Retain an enclosing bank and a small landing below the caster.
                if(distance(p.getX()+.5,p.getZ()+.5)>(radius-2)*(radius-2)||distance(p.getX()+.5,p.getZ()+.5)<6.25) {
                    for(int y=0;y<3;y++)set(level,p.atY(floor-y),palette.surface.defaultBlockState());return;
                }
                if(floor<bed-4||floor>waterline+16)return;
                for(int y=Math.min(floor,bed);y<=Math.max(floor,waterline);y++) {
                    BlockState s=y<=bed?palette.substrate.defaultBlockState():y>waterline?Blocks.AIR.defaultBlockState():Blocks.WATER.defaultBlockState();
                    if(y==waterline&&palette==EnvironmentPalette.FROZEN_OCEAN)s=Blocks.ICE.defaultBlockState();
                    set(level,p.atY(y),s);
                }
                set(level,p.atY(bed),palette.surface.defaultBlockState());return;
            }
            // Fill old water to its previous surface so an ocean can become walkable land again.
            int surface=waterTop==Integer.MIN_VALUE?floor:Math.min(waterTop,floor+12);
            for(int y=floor-3;y<=surface;y++)set(level,p.atY(y),(y==surface?palette.surface:palette.substrate).defaultBlockState());
        }
        long noise(BlockPos p) {
            long n=p.asLong()^origin.hashCode()^biome.hashCode();n=(n^(n>>>33))*0xff51afd7ed558ccdL;return (n^(n>>>33))&Long.MAX_VALUE;
        }
        void decorate(ServerLevel level,BlockPos col) {
            int y=top(level,col);BlockPos p=col.atY(y+1);BlockState base=level.getBlockState(p.below());long seed=noise(col);
            if(palette.aquatic()) {
                int bed=y;
                while(bed>level.getMinBuildHeight()&&(liquid(level.getBlockState(col.atY(bed)))||level.getBlockState(col.atY(bed)).is(Blocks.ICE)))bed--;
                BlockPos plant=col.atY(bed+1);
                if(bed<y-1&&level.getBlockState(col.atY(bed)).is(palette.surface)&&level.getBlockState(plant).is(Blocks.WATER)) {
                    if(biome.getPath().equals("warm_ocean")&&seed%19==0)set(level,plant,(seed%2==0?Blocks.BRAIN_CORAL_BLOCK:Blocks.TUBE_CORAL_BLOCK).defaultBlockState());
                    else if(seed%13==0)set(level,plant,Blocks.SEAGRASS.defaultBlockState());
                }
                return;
            }
            if(!base.is(palette.surface)||!level.isEmptyBlock(p)||distance(p.getX()+.5,p.getZ()+.5)<9)return;
            String id=biome.getPath();
            if(palette==EnvironmentPalette.SNOW) {
                set(level,p,Blocks.SNOW.defaultBlockState());
                if(id.equals("ice_spikes") && seed%43==0)pillar(level,p,Blocks.PACKED_ICE,4+(int)(seed%6));
                else if((id.contains("taiga")||id.equals("grove"))&&seed%47==0)tree(level,p,Blocks.SPRUCE_LOG,Blocks.SPRUCE_LEAVES,5,2);
            } else if(palette==EnvironmentPalette.DESERT) {
                if(seed%79==0&&level.isEmptyBlock(p.north())&&level.isEmptyBlock(p.south())&&level.isEmptyBlock(p.east())&&level.isEmptyBlock(p.west()))pillar(level,p,Blocks.CACTUS,2+(int)(seed%2));
                else if(seed%19==0)set(level,p,Blocks.DEAD_BUSH.defaultBlockState());
            } else if(palette==EnvironmentPalette.BADLANDS) {
                if(seed%67==0)pillar(level,p,Blocks.ORANGE_TERRACOTTA,id.equals("eroded_badlands")?5:2);
                else if(seed%19==0)set(level,p,Blocks.DEAD_BUSH.defaultBlockState());
            } else if(palette==EnvironmentPalette.CRIMSON||palette==EnvironmentPalette.WARPED) {
                boolean warped=palette==EnvironmentPalette.WARPED;
                if(seed%53==0)tree(level,p,warped?Blocks.WARPED_STEM:Blocks.CRIMSON_STEM,warped?Blocks.WARPED_WART_BLOCK:Blocks.NETHER_WART_BLOCK,5,2);
                else if(seed%6==0)set(level,p,(warped?Blocks.WARPED_FUNGUS:Blocks.CRIMSON_FUNGUS).defaultBlockState());
            } else if(palette==EnvironmentPalette.NETHER) {
                if(seed%41==0)set(level,p.below(),Blocks.MAGMA_BLOCK.defaultBlockState());
            } else if(palette==EnvironmentPalette.SOUL) {
                if(seed%37==0)pillar(level,p,Blocks.BASALT,3);
            } else if(palette==EnvironmentPalette.BASALT) {
                if(seed%17==0)pillar(level,p,Blocks.BASALT,2+(int)(seed%5));
            } else if(palette==EnvironmentPalette.END) {
                if(seed%61==0&&!id.equals("the_void")&&!id.equals("end_barrens")) {
                    // A supported vanilla flower grows naturally after the conversion.
                    set(level,p,Blocks.CHORUS_FLOWER.defaultBlockState());
                }
            } else if(palette==EnvironmentPalette.MUSHROOM) {
                if(seed%89==0)tree(level,p,Blocks.MUSHROOM_STEM,Blocks.RED_MUSHROOM_BLOCK,4,2);
                else if(seed%9==0)set(level,p,(seed%2==0?Blocks.RED_MUSHROOM:Blocks.BROWN_MUSHROOM).defaultBlockState());
            } else if(palette==EnvironmentPalette.DRIPSTONE) {
                if(seed%13==0)set(level,p,Blocks.POINTED_DRIPSTONE.defaultBlockState());
            } else if(palette==EnvironmentPalette.LUSH) {
                if(seed%29==0)tree(level,p,Blocks.OAK_LOG,Blocks.FLOWERING_AZALEA_LEAVES,3,2);
                else if(seed%7==0)set(level,p,Blocks.FLOWERING_AZALEA.defaultBlockState());
            } else if(palette==EnvironmentPalette.CHERRY) {
                if(seed%73==0)tree(level,p,Blocks.CHERRY_LOG,Blocks.CHERRY_LEAVES,5,3);
                else if(seed%7==0)set(level,p,Blocks.PINK_PETALS.defaultBlockState());
            } else if(palette==EnvironmentPalette.JUNGLE) {
                if(id.equals("bamboo_jungle")&&seed%13==0)pillar(level,p,Blocks.BAMBOO,4+(int)(seed%5));
                else if(seed%(id.equals("sparse_jungle")?113:53)==0)tree(level,p,Blocks.JUNGLE_LOG,Blocks.JUNGLE_LEAVES,7,3);
                else if(seed%7==0)set(level,p,Blocks.FERN.defaultBlockState());
            } else if(palette==EnvironmentPalette.SWAMP) {
                if(seed%83==0)tree(level,p,id.contains("mangrove")?Blocks.MANGROVE_LOG:Blocks.OAK_LOG,id.contains("mangrove")?Blocks.MANGROVE_LEAVES:Blocks.OAK_LEAVES,5,3);
                else if(seed%29==0){set(level,p.below(),Blocks.WATER.defaultBlockState());set(level,p,Blocks.LILY_PAD.defaultBlockState());}
            } else if(palette==EnvironmentPalette.TAIGA||palette==EnvironmentPalette.FOREST) {
                boolean spruce=palette==EnvironmentPalette.TAIGA,birch=id.contains("birch"),dark=id.equals("dark_forest");
                if(seed%67==0)tree(level,p,spruce?Blocks.SPRUCE_LOG:birch?Blocks.BIRCH_LOG:dark?Blocks.DARK_OAK_LOG:Blocks.OAK_LOG,
                        spruce?Blocks.SPRUCE_LEAVES:birch?Blocks.BIRCH_LEAVES:dark?Blocks.DARK_OAK_LEAVES:Blocks.OAK_LEAVES,5,2);
                else if(seed%7==0)set(level,p,(id.equals("flower_forest")?(seed%2==0?Blocks.ALLIUM:Blocks.AZURE_BLUET):Blocks.FERN).defaultBlockState());
            } else if(palette==EnvironmentPalette.GRASS) {
                if(id.contains("savanna")&&seed%131==0)tree(level,p,Blocks.ACACIA_LOG,Blocks.ACACIA_LEAVES,4,3);
                else if(seed%7==0)set(level,p,(seed%3==0?Blocks.DANDELION:seed%3==1?Blocks.POPPY:Blocks.GRASS).defaultBlockState());
            }
        }
        void pillar(ServerLevel level,BlockPos p,Block block,int height) {
            for(int i=0;i<height;i++)if(!editable(level,p.above(i))||!level.isEmptyBlock(p.above(i)))return;
            for(int i=0;i<height;i++)set(level,p.above(i),block.defaultBlockState());
        }
        void tree(ServerLevel level,BlockPos p,Block trunk,Block leaves,int height,int radius) {
            // Validate the complete crown before placing: never cut into structures or outside the disk.
            for(int x=-radius;x<=radius;x++)for(int z=-radius;z<=radius;z++)for(int y=0;y<=height+1;y++) {
                BlockPos q=p.offset(x,y,z);
                if(!level.hasChunkAt(q)||!editable(level,q)||(!level.isEmptyBlock(q)&&!flora(level.getBlockState(q))))return;
            }
            BlockState leaf=leaves.defaultBlockState();
            if(leaf.hasProperty(LeavesBlock.PERSISTENT))leaf=leaf.setValue(LeavesBlock.PERSISTENT,true);
            for(int y=height-2;y<=height+1;y++)for(int x=-radius;x<=radius;x++)for(int z=-radius;z<=radius;z++)
                if(x*x+z*z<=radius*radius+(y<height?1:-1))set(level,p.offset(x,y,z),leaf);
            for(int y=0;y<height;y++)set(level,p.above(y),trunk.defaultBlockState());
        }
        void finish(ServerLevel level) {
            done=true;
            var player=level.getServer().getPlayerList().getPlayer(owner);
            if(player!=null)player.displayClientMessage(Component.translatable("message.sourceofmystery.staff.environment_done",title(biome)),true);
            StaffCasting.send(level,new StaffEffectPacket(StaffEffectPacket.ENVIRONMENT,owner,origin,new Vec3(radius,1,0),60,palette.ordinal()));
        }
        CompoundTag save() {
            CompoundTag tag=new CompoundTag();tag.putUUID("Owner",owner);tag.putString("Biome",biome.toString());
            tag.putDouble("X",origin.x);tag.putDouble("Y",origin.y);tag.putDouble("Z",origin.z);
            tag.putUUID("Token",token);ListTag masks=new ListTag();for(Area area:excluded)masks.add(area.save());tag.put("ExcludedAreas",masks);
            tag.putInt("Version",3);tag.putInt("Radius",radius);tag.putInt("Cursor",cursor);tag.putInt("Stage",stage);tag.putInt("ColumnCursor",columnCursor);return tag;
        }
    }
    public static StaffEnvironment load(CompoundTag tag) {
        StaffEnvironment data=new StaffEnvironment();
        for(Tag entry:tag.getList("Jobs",Tag.TAG_COMPOUND)) {
            CompoundTag row=(CompoundTag)entry;ResourceLocation id=ResourceLocation.tryParse(row.getString("Biome"));
            if(id==null||!id.getNamespace().equals("minecraft")||!row.hasUUID("Owner"))continue;
            if(!Double.isFinite(row.getDouble("X"))||!Double.isFinite(row.getDouble("Y"))||!Double.isFinite(row.getDouble("Z")))continue;
            Job job=new Job(row.getUUID("Owner"),new Vec3(row.getDouble("X"),row.getDouble("Y"),row.getDouble("Z")),id,Math.max(1,Math.min(RADIUS,row.contains("Radius")?row.getInt("Radius"):50)));
            if(row.hasUUID("Token"))job.token=row.getUUID("Token");
            for(Tag mask:row.getList("ExcludedAreas",Tag.TAG_COMPOUND)){
                var disk=(CompoundTag)mask;double x=disk.getDouble("X"),z=disk.getDouble("Z");
                if(Double.isFinite(x)&&Double.isFinite(z)&&disk.getInt("Radius")>0)job.exclude(new Area(x,z,Math.min(RADIUS,disk.getInt("Radius"))));
            }
            job.stage=Math.max(0,Math.min(1,row.getInt("Stage")));
            job.cursor=row.getInt("Version")>=2?Math.max(0,Math.min(job.chunks.size(),row.getInt("Cursor"))):0;
            job.columnCursor=Math.max(0,Math.min(256,row.getInt("ColumnCursor")));
            if(!job.chunks.isEmpty())data.jobs.add(job);
        }
        return data;
    }
    @Override public CompoundTag save(CompoundTag tag) {
        ListTag rows=new ListTag();for(Job job:jobs)rows.add(job.save());tag.put("Jobs",rows);return tag;
    }
}
