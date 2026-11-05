package com.sourceofmystery.offering;

import com.mojang.authlib.GameProfile;
import com.sourceofmystery.block.*;
import com.sourceofmystery.item.ModItems;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.*;
import software.bernie.geckolib.animatable.GeoItem;
import java.util.UUID;

@GameTestHolder("sourceofmystery")
@PrefixGameTestTemplate(false)
public final class OfferingDisplayGameTests {
    @GameTest(template="outsider_test",timeoutTicks=40)
    public static void placeAndRecoverWithAnyTool(GameTestHelper h){
        var level=h.getLevel();var item=ModItems.DARK_DIMENSIONAL_OFFERING.get();
        h.assertTrue(item instanceof BlockItem&&item instanceof GeoItem,"Original item must become a GeckoLib BlockItem");
        h.assertTrue(ModBlocks.DARK_DIMENSIONAL_OFFERING.get().asItem()==item,"Placed block must map back to the original offering ID");
        FakePlayer player=new FakePlayer(level,new GameProfile(UUID.randomUUID(),"OfferingDisplayTest"));player.setGameMode(GameType.SURVIVAL);
        int n=0;
        for(ItemStack tool:new ItemStack[]{ItemStack.EMPTY,new ItemStack(Items.STICK),new ItemStack(Items.WOODEN_PICKAXE),new ItemStack(Items.IRON_AXE)}){
            BlockPos pos=h.absolutePos(new BlockPos(2+n++*3,3,2));level.setBlockAndUpdate(pos,Blocks.AIR.defaultBlockState());level.setBlockAndUpdate(pos.below(),Blocks.STONE.defaultBlockState());
            player.setPos(pos.getX()+.5,pos.getY(),pos.getZ()+3);player.setYRot(180);
            ItemStack stack=new ItemStack(item);player.setItemInHand(InteractionHand.MAIN_HAND,stack);
            var hit=new BlockHitResult(Vec3.atCenterOf(pos.below()).add(0,.5,0),Direction.UP,pos.below(),false);
            var result=item.useOn(new UseOnContext(player,InteractionHand.MAIN_HAND,hit));
            h.assertTrue(result.consumesAction()&&stack.isEmpty(),"Survival placement must consume one offering");
            h.assertTrue(level.getBlockState(pos).is(ModBlocks.DARK_DIMENSIONAL_OFFERING.get()),"Item must place the display block");
            h.assertTrue(level.getBlockEntity(pos) instanceof DarkDimensionalOfferingBlockEntity,"Missing GeckoLib display entity");
            player.setItemInHand(InteractionHand.MAIN_HAND,tool);
            var state=level.getBlockState(pos);
            h.assertTrue(!state.requiresCorrectToolForDrops()&&state.getDestroyProgress(player,level,pos)>=1,"Any hand/tool must break immediately");
            h.assertTrue(player.gameMode.destroyBlock(pos)&&level.isEmptyBlock(pos),"Survival mining must remove the ornament");
            var drops=level.getEntitiesOfClass(ItemEntity.class,new AABB(pos).inflate(1));
            h.assertTrue(drops.stream().filter(e->e.getItem().is(item)).mapToInt(e->e.getItem().getCount()).sum()==1,"Mining must return exactly one reusable offering");
            drops.forEach(ItemEntity::discard);
        }
        h.succeed();
    }
}
