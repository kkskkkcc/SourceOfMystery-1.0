package com.sourceofmystery.ritual;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.*;

@GameTestHolder("sourceofmystery")
@PrefixGameTestTemplate(false)
public final class OfferingPortalGameTests {
    @GameTest(template="outsider_test",timeoutTicks=30)
    public static void bothPortalAxes(GameTestHelper h)throws Exception{
        var level=h.getLevel();
        var find=OfferingPortalRitual.class.getDeclaredMethod("findFrame",net.minecraft.server.level.ServerLevel.class,BlockPos.class);find.setAccessible(true);
        for(boolean axis:new boolean[]{false,true}){
            BlockPos base=h.absolutePos(new BlockPos(axis?12:2,2,2));
            var frame=new OfferingPortalRitual.Frame(base.getX(),base.getY(),base.getZ(),axis,level);
            h.assertTrue(frame.blocks().size()==14&&new java.util.HashSet<>(frame.blocks()).size()==14,"14 distinct blocks required");
            for(var p:frame.inner())level.setBlock(p,Blocks.AIR.defaultBlockState(),3);
            for(var p:frame.blocks())level.setBlock(p,Blocks.OBSIDIAN.defaultBlockState(),3);
            for(var p:frame.blocks())h.assertTrue(find.invoke(null,level,p)!=null,"Must accept every frame block on both axes: "+p);
            var inside=frame.inner().get(0);level.setBlock(inside,Blocks.NETHER_PORTAL.defaultBlockState(),3);
            h.assertTrue(find.invoke(null,level,base)==null,"Activated portals must not summon");
        }
        float previous=OfferingPortalRitual.entranceDistance(0);
        h.assertTrue(previous<0,"Actor must begin behind plane");
        for(int i=0;i<=640;i++){
            float distance=OfferingPortalRitual.entranceDistance(i/20f);
            h.assertTrue(distance>=previous,"Entrance motion must never reverse through plane");previous=distance;
        }
        h.assertTrue(previous>.7f,"Actor must clear the plane before dismissal");h.succeed();
    }
}
