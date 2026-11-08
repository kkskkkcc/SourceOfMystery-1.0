package com.sourceofmystery.staff;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/** Surface identities for all vanilla biome families. Biome IDs themselves are never collapsed. */
public enum EnvironmentPalette {
    GRASS(0x86e5ad, Blocks.GRASS_BLOCK, Blocks.DIRT),
    FOREST(0x48d47e, Blocks.GRASS_BLOCK, Blocks.DIRT),
    JUNGLE(0x9fec41, Blocks.GRASS_BLOCK, Blocks.DIRT),
    SWAMP(0x59bbb0, Blocks.MUD, Blocks.DIRT),
    DESERT(0xffd581, Blocks.SAND, Blocks.SANDSTONE),
    BADLANDS(0xf79b56, Blocks.RED_SAND, Blocks.TERRACOTTA),
    OCEAN(0x45ccff, Blocks.SAND, Blocks.GRAVEL),
    FROZEN_OCEAN(0xa4efff, Blocks.GRAVEL, Blocks.STONE),
    SNOW(0xd7f6ff, Blocks.SNOW_BLOCK, Blocks.DIRT),
    TAIGA(0x83c8ad, Blocks.PODZOL, Blocks.DIRT),
    MUSHROOM(0xe3a7f3, Blocks.MYCELIUM, Blocks.DIRT),
    NETHER(0xff7148, Blocks.NETHERRACK, Blocks.NETHERRACK),
    CRIMSON(0xff4485, Blocks.CRIMSON_NYLIUM, Blocks.NETHERRACK),
    WARPED(0x35f5d5, Blocks.WARPED_NYLIUM, Blocks.NETHERRACK),
    SOUL(0x73e4f4, Blocks.SOUL_SAND, Blocks.SOUL_SOIL),
    BASALT(0xffaa54, Blocks.BASALT, Blocks.BLACKSTONE),
    END(0xc99aff, Blocks.END_STONE, Blocks.END_STONE),
    CHERRY(0xffb7e0, Blocks.GRASS_BLOCK, Blocks.DIRT),
    LUSH(0xb0f97c, Blocks.MOSS_BLOCK, Blocks.CLAY),
    DRIPSTONE(0xe0bd97, Blocks.DRIPSTONE_BLOCK, Blocks.STONE),
    DEEP_DARK(0x40d8df, Blocks.SCULK, Blocks.DEEPSLATE),
    ROCK(0xc6d6ea, Blocks.STONE, Blocks.STONE);

    public final int color;
    public final Block surface, substrate;
    EnvironmentPalette(int color, Block surface, Block substrate) {
        this.color=color; this.surface=surface; this.substrate=substrate;
    }
    public boolean aquatic() { return this==OCEAN || this==FROZEN_OCEAN; }
    public boolean infernal() { return this==NETHER || this==CRIMSON || this==WARPED || this==SOUL || this==BASALT; }
    public static EnvironmentPalette fromVariant(int id) {
        return id>=0 && id<values().length ? values()[id] : GRASS;
    }
    public static EnvironmentPalette of(ResourceLocation biome) {
        String id=biome.getPath();
        if(id.equals("nether_wastes"))return NETHER;
        if(id.equals("crimson_forest"))return CRIMSON;
        if(id.equals("warped_forest"))return WARPED;
        if(id.equals("soul_sand_valley"))return SOUL;
        if(id.equals("basalt_deltas"))return BASALT;
        if(id.equals("the_end")||id.contains("end_")||id.equals("the_void"))return END;
        if(id.equals("lush_caves"))return LUSH;
        if(id.equals("dripstone_caves"))return DRIPSTONE;
        if(id.equals("deep_dark"))return DEEP_DARK;
        if(id.contains("ocean")||id.contains("river"))return id.contains("frozen")?FROZEN_OCEAN:OCEAN;
        if(id.contains("swamp"))return SWAMP;
        if(id.contains("badlands"))return BADLANDS;
        if(id.contains("desert")||id.equals("beach"))return DESERT;
        if(id.contains("snow")||id.contains("frozen")||id.equals("ice_spikes")||id.equals("grove"))return SNOW;
        if(id.contains("taiga"))return TAIGA;
        if(id.contains("jungle"))return JUNGLE;
        if(id.contains("mushroom"))return MUSHROOM;
        if(id.contains("cherry"))return CHERRY;
        if(id.contains("forest"))return FOREST;
        if(id.contains("stony")||id.contains("peaks")||id.contains("gravelly")||id.equals("windswept_hills"))return ROCK;
        return GRASS;
    }
}
