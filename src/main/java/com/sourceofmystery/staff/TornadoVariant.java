package com.sourceofmystery.staff;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.*;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/** Tags and actual surface fluids let modded biomes participate without a hardcoded registry list. */
public enum TornadoVariant {
    NORMAL("normal",0x939fa6),SAND("sand",0xc99b60),WATER("water",0x79b4d1),FIRE("fire",0xed7139);
    private static TagKey<Biome> tag(String id){return TagKey.create(Registries.BIOME,new ResourceLocation("sourceofmystery","tornado_"+id));}
    public static final TagKey<Biome> ARID=tag("arid"),AQUATIC=tag("aquatic"),INFERNAL=tag("infernal");
    public final String id;public final int color;
    TornadoVariant(String id,int color){this.id=id;this.color=color;}
    public Component title(){return Component.translatable("tornado.sourceofmystery."+id);}
    public static TornadoVariant index(int i){return i>=0&&i<values().length?values()[i]:NORMAL;}
    public static TornadoVariant detect(ServerLevel level,Vec3 center){
        var biome=level.getBiome(BlockPos.containing(center));
        if(level.dimensionType().ultraWarm()||biome.is(INFERNAL))return FIRE;
        int water=0,heat=0,sand=0;
        for(int i=0;i<9;i++){
            int x=(int)Math.floor(center.x)+(i%3-1)*5,z=(int)Math.floor(center.z)+(i/3-1)*5;
            if(!level.hasChunk(x>>4,z>>4))continue;
            var pos=level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,new BlockPos(x,0,z)).below();
            var state=level.getBlockState(pos);
            if(level.getFluidState(pos).is(FluidTags.LAVA)||state.is(Blocks.FIRE)||state.is(Blocks.MAGMA_BLOCK)||level.getBlockState(pos.above()).is(Blocks.FIRE))heat++;
            if(level.getFluidState(pos).is(FluidTags.WATER))water++;
            if(state.is(BlockTags.SAND)||state.is(Blocks.SANDSTONE)||state.is(Blocks.RED_SANDSTONE))sand++;
        }
        if(heat>=2)return FIRE;
        if(water>=3||biome.is(AQUATIC)||biome.is(BiomeTags.IS_OCEAN)||biome.is(BiomeTags.IS_RIVER))return WATER;
        if(sand>=3||biome.is(ARID)||biome.is(BiomeTags.IS_BADLANDS))return SAND;
        return NORMAL;
    }
}
