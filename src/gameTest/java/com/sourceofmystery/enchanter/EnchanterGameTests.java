package com.sourceofmystery.enchanter;

import com.mojang.authlib.GameProfile;
import com.sourceofmystery.block.ModBlocks;
import com.sourceofmystery.energy.MysteryEnergy;
import com.sourceofmystery.item.ModItems;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.*;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.enchantment.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.*;
import java.util.UUID;

@GameTestHolder("sourceofmystery")
@PrefixGameTestTemplate(false)
public final class EnchanterGameTests {
    private static FakePlayer player(GameTestHelper h,String name){var p=new FakePlayer(h.getLevel(),new GameProfile(UUID.randomUUID(),name));p.setGameMode(GameType.SURVIVAL);return p;}
    private static EnchanterMenu menu(GameTestHelper h,FakePlayer p,int id){var pos=h.absolutePos(new BlockPos(2,3,2));h.getLevel().setBlockAndUpdate(pos,ModBlocks.MYSTERY_ENCHANTER.get().defaultBlockState());p.setPos(Vec3.atCenterOf(pos));var m=new EnchanterMenu(id,p.getInventory(),pos);p.containerMenu=m;return m;}
    private static void select(FakePlayer p,EnchanterMenu m,Enchantment e){m.clickMenuButton(p,EnchanterMenu.SELECT_BASE+BuiltInRegistries.ENCHANTMENT.getId(e));}
    @GameTest(template="outsider_test",timeoutTicks=40)
    public static void energyEnchantRemoveAndBooks(GameTestHelper h){
        var p=player(h,"EnchanterTest");var m=menu(h,p,50);MysteryEnergy.setEnergyAndMax(p,100000);
        h.assertTrue(m.enchantments.size()==net.minecraftforge.registries.ForgeRegistries.ENCHANTMENTS.getValues().size(),"Registry enchantments omitted");
        var sword=new ItemStack(Items.DIAMOND_SWORD);sword.setDamageValue(123);sword.setHoverName(net.minecraft.network.chat.Component.literal("Keep my name"));sword.getOrCreateTag().putString("othermod_data","preserve_me");
        var unknown=new ListTag();var tag=new CompoundTag();tag.putString("id","missingmod:legacy");tag.putShort("lvl",(short)2);unknown.add(tag);sword.getOrCreateTag().put("Enchantments",unknown);
        m.getSlot(0).set(sword);select(p,m,Enchantments.SHARPNESS);MysteryEnergy.setEnergy(p,m.enchantCost()-1);
        var before=sword.copy();long energy=MysteryEnergy.getEnergy(p);
        h.assertFalse(m.clickMenuButton(p,EnchanterMenu.APPLY),"Insufficient energy accepted");h.assertTrue(MysteryEnergy.getEnergy(p)==energy&&ItemStack.matches(before,m.getSlot(0).getItem()),"Failed operation consumed or modified item");
        MysteryEnergy.setEnergy(p,100000);long cost=m.enchantCost();h.assertTrue(m.clickMenuButton(p,EnchanterMenu.APPLY),"Sharpness rejected");
        h.assertTrue(MysteryEnergy.getEnergy(p)==100000-cost&&EnchanterRules.level(m.getSlot(0).getItem(),Enchantments.SHARPNESS)==1,"Wrong enchantment or energy charge");
        energy=MysteryEnergy.getEnergy(p);h.assertFalse(m.clickMenuButton(p,EnchanterMenu.APPLY),"Repeated click charged twice");h.assertTrue(MysteryEnergy.getEnergy(p)==energy,"Repeated charge");
        m.clickMenuButton(p,EnchanterMenu.MORE);h.assertTrue(m.enchantCost()==3*cost,"Upgrade price not incremental");m.clickMenuButton(p,EnchanterMenu.APPLY);
        select(p,m,Enchantments.UNBREAKING);h.assertTrue(m.clickMenuButton(p,EnchanterMenu.APPLY),"Compatible enchantment rejected");
        select(p,m,Enchantments.SMITE);energy=MysteryEnergy.getEnergy(p);h.assertFalse(m.clickMenuButton(p,EnchanterMenu.APPLY),"Conflicting enchantment accepted");h.assertTrue(MysteryEnergy.getEnergy(p)==energy,"Conflict charged energy");
        select(p,m,Enchantments.SHARPNESS);h.assertTrue(m.clickMenuButton(p,EnchanterMenu.REMOVE),"Selected removal failed");
        var item=m.getSlot(0).getItem();h.assertTrue(EnchanterRules.level(item,Enchantments.SHARPNESS)==0&&EnchanterRules.level(item,Enchantments.UNBREAKING)==1,"Removal changed another enchantment");
        h.assertTrue(item.getTag().getList("Enchantments",Tag.TAG_COMPOUND).stream().anyMatch(t->((CompoundTag)t).getString("id").equals("missingmod:legacy")),"Unknown enchantment NBT was destroyed");
        h.assertTrue(m.clickMenuButton(p,EnchanterMenu.CLEAR),"Clear all failed");item=m.getSlot(0).getItem();
        h.assertTrue(item.getDamageValue()==123&&item.getHoverName().getString().equals("Keep my name")&&item.getTag().getString("othermod_data").equals("preserve_me"),"Custom data/durability/name changed");
        energy=MysteryEnergy.getEnergy(p);h.assertFalse(m.clickMenuButton(p,EnchanterMenu.CLEAR),"Empty clear accepted");h.assertTrue(MysteryEnergy.getEnergy(p)==energy,"Empty clear charged");
        m.removed(p);m.removed(p);h.assertTrue(p.getInventory().countItem(Items.DIAMOND_SWORD)==1,"Closed menu lost or duplicated item");
        var books=menu(h,p,51);p.getInventory().setItem(9,new ItemStack(Items.BOOK,64));books.quickMoveStack(p,1);
        h.assertTrue(books.getSlot(0).getItem().getCount()==1&&p.getInventory().getItem(9).getCount()==63,"Input did not split exactly one book");
        select(p,books,Enchantments.MENDING);MysteryEnergy.setEnergy(p,100000);h.assertTrue(books.clickMenuButton(p,EnchanterMenu.APPLY),"Treasure enchantment on book rejected");
        h.assertTrue(books.getSlot(0).getItem().is(Items.ENCHANTED_BOOK)&&EnchanterRules.level(books.getSlot(0).getItem(),Enchantments.MENDING)==1,"Book conversion failed");
        h.assertTrue(books.clickMenuButton(p,EnchanterMenu.REMOVE)&&books.getSlot(0).getItem().is(Items.BOOK),"Disenchanted book did not become a normal book");
        select(p,books,Enchantments.BINDING_CURSE);h.assertTrue(books.clickMenuButton(p,EnchanterMenu.APPLY)&&books.clickMenuButton(p,EnchanterMenu.CLEAR),"Curse handling failed");
        h.assertTrue(p.experienceLevel==0,"Station consumed/generated experience");books.removed(p);p.containerMenu=p.inventoryMenu;h.succeed();
    }
    @GameTest(template="outsider_test",timeoutTicks=40)
    public static void exactRecipePlaceAndMine(GameTestHelper h){
        var p=player(h,"EnchanterRecipe");var grid=new TransientCraftingContainer(p.inventoryMenu,3,3);
        Item[] ingredients={ModItems.MYSTERY_INGOT.get(),Items.NETHER_STAR,ModItems.MYSTERY_INGOT.get(),Items.DIAMOND_BLOCK,Items.ENCHANTING_TABLE,Items.DIAMOND_BLOCK,Items.OBSIDIAN,Items.OBSIDIAN,Items.OBSIDIAN};
        for(int i=0;i<9;i++)grid.setItem(i,new ItemStack(ingredients[i]));
        var recipe=(CraftingRecipe)h.getLevel().getRecipeManager().byKey(new ResourceLocation("sourceofmystery","mystery_enchanter")).orElseThrow();
        h.assertTrue(recipe.matches(grid,h.getLevel())&&recipe.assemble(grid,h.getLevel().registryAccess()).is(ModItems.MYSTERY_ENCHANTER.get()),"Requested crafting recipe does not match");
        grid.setItem(3,new ItemStack(Items.DIAMOND));h.assertFalse(recipe.matches(grid,h.getLevel()),"Diamond accepted instead of diamond block");
        var pos=h.absolutePos(new BlockPos(5,3,5));h.getLevel().setBlockAndUpdate(pos.below(),Blocks.STONE.defaultBlockState());p.setPos(Vec3.atCenterOf(pos).add(0,0,2));
        var stack=new ItemStack(ModItems.MYSTERY_ENCHANTER.get());p.setItemInHand(InteractionHand.MAIN_HAND,stack);
        var result=stack.getItem().useOn(new UseOnContext(p,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(pos.below()).add(0,.5,0),Direction.UP,pos.below(),false)));
        h.assertTrue(result.consumesAction()&&stack.isEmpty()&&h.getLevel().getBlockEntity(pos) instanceof MysteryEnchanterBlockEntity,"Placement failed");
        p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.DIAMOND_PICKAXE));h.assertTrue(p.gameMode.destroyBlock(pos),"Mining failed");
        var drops=h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,new AABB(pos).inflate(2));
        h.assertTrue(drops.stream().filter(e->e.getItem().is(ModItems.MYSTERY_ENCHANTER.get())).mapToInt(e->e.getItem().getCount()).sum()==1,"Mining did not drop one enchanter");drops.forEach(net.minecraft.world.entity.item.ItemEntity::discard);h.succeed();
    }
}
