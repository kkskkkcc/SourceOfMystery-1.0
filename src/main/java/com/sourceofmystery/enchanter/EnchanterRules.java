package com.sourceofmystery.enchanter;

import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.*;
import net.minecraftforge.registries.ForgeRegistries;
import java.util.*;

/** Registry-driven enchantments; only the selected enchantment tag is replaced. */
public final class EnchanterRules {
    public static final int READY=0,EMPTY=1,NO_SELECTION=2,INAPPLICABLE=3,CONFLICT=4,NO_UPGRADE=5,UNSUPPORTED=6;
    public static List<Enchantment> all() {
        return ForgeRegistries.ENCHANTMENTS.getValues().stream().sorted(Comparator.comparing(e->ForgeRegistries.ENCHANTMENTS.getKey(e).toString())).toList();
    }
    public static int maximum(Enchantment enchantment) {
        try {return Math.max(1,Math.min(255,enchantment.getMaxLevel()));}catch(RuntimeException | LinkageError e){return 1;}
    }
    public static int minimum(Enchantment enchantment) {
        try {return Math.max(1,Math.min(maximum(enchantment),enchantment.getMinLevel()));}catch(RuntimeException | LinkageError e){return 1;}
    }
    private static boolean book(ItemStack stack) {return stack.is(Items.BOOK)||stack.is(Items.ENCHANTED_BOOK);}
    private static String key(ItemStack stack) {return book(stack)?"StoredEnchantments":"Enchantments";}
    public static int level(ItemStack stack,Enchantment enchantment) {
        if(stack.isEmpty()||enchantment==null||stack.getTag()==null)return 0;
        var id=ForgeRegistries.ENCHANTMENTS.getKey(enchantment);int level=0;
        for(var raw:stack.getTag().getList(key(stack),Tag.TAG_COMPOUND)) {
            var tag=(CompoundTag)raw;if(id.toString().equals(tag.getString("id")))level=Math.max(level,EnchantmentHelper.getEnchantmentLevel(tag));
        }
        return level;
    }
    public static int canApply(ItemStack stack,Enchantment enchantment,int target) {
        if(stack.isEmpty())return EMPTY;if(enchantment==null)return NO_SELECTION;
        try {
            if(target<minimum(enchantment)||target>maximum(enchantment))return UNSUPPORTED;
            if(book(stack)?!enchantment.isAllowedOnBooks():!enchantment.canEnchant(stack))return INAPPLICABLE;
            for(var other:EnchantmentHelper.getEnchantments(stack).keySet())if(other!=enchantment && !enchantment.isCompatibleWith(other))return CONFLICT;
            if(level(stack,enchantment)>=target)return NO_UPGRADE;
            return READY;
        }catch(RuntimeException | LinkageError e){return UNSUPPORTED;}
    }
    private static int factor(Enchantment e) {
        try {return Math.max(1,Math.min(16,10/Math.max(1,e.getRarity().getWeight())));}catch(RuntimeException | LinkageError x){return 2;}
    }
    public static long enchantCost(ItemStack stack,Enchantment e,int target) {
        if(e==null)return 0;int current=level(stack,e);
        return multiply(multiply(EnchanterConfig.ENCHANT_BASE.get(),factor(e)),Math.max(0,(long)target*target-(long)current*current));
    }
    public static long removeCost(ItemStack stack,Enchantment e) {
        return e==null?0:multiply(multiply(EnchanterConfig.REMOVE_BASE.get(),factor(e)),level(stack,e));
    }
    public static long clearCost(ItemStack stack) {
        long cost=0;
        if(stack.isEmpty()||stack.getTag()==null)return 0;
        for(String key:List.of("Enchantments","StoredEnchantments")) for(var raw:stack.getTag().getList(key,Tag.TAG_COMPOUND)) {
            var tag=(CompoundTag)raw;var id=ResourceLocation.tryParse(tag.getString("id"));
            var e=id==null?null:ForgeRegistries.ENCHANTMENTS.getValue(id);
            long part=e==null?EnchanterConfig.REMOVE_BASE.get():multiply(multiply(EnchanterConfig.REMOVE_BASE.get(),factor(e)),Math.max(1,EnchantmentHelper.getEnchantmentLevel(tag)));
            cost=add(cost,part);
        }
        return cost;
    }
    private static long multiply(long a,long b) {return a==0||b==0?0:a>Long.MAX_VALUE/b?Long.MAX_VALUE:a*b;}
    private static long add(long a,long b) {return Long.MAX_VALUE-a<b?Long.MAX_VALUE:a+b;}
    private static ItemStack asItem(ItemStack source,Item item) {
        var saved=source.save(new CompoundTag());saved.putString("id",ForgeRegistries.ITEMS.getKey(item).toString());return ItemStack.of(saved);
    }
    public static ItemStack apply(ItemStack source,Enchantment enchantment,int level) {
        var result=source.is(Items.BOOK)?asItem(source,Items.ENCHANTED_BOOK):source.copy();
        var id=ForgeRegistries.ENCHANTMENTS.getKey(enchantment);var list=result.getOrCreateTag().getList(key(result),Tag.TAG_COMPOUND).copy();
        list.removeIf(raw->id.toString().equals(((CompoundTag)raw).getString("id")));
        list.add(EnchantmentHelper.storeEnchantment(id,level));result.getOrCreateTag().put(key(result),list);return result;
    }
    public static ItemStack remove(ItemStack source,Enchantment enchantment) {
        var result=source.copy();var id=ForgeRegistries.ENCHANTMENTS.getKey(enchantment);
        var list=result.getOrCreateTag().getList(key(result),Tag.TAG_COMPOUND).copy();
        list.removeIf(raw->id.toString().equals(((CompoundTag)raw).getString("id")));
        if(list.isEmpty())result.removeTagKey(key(result));else result.getOrCreateTag().put(key(result),list);
        return result.is(Items.ENCHANTED_BOOK)&&list.isEmpty()?asItem(result,Items.BOOK):result;
    }
    public static ItemStack clear(ItemStack source) {
        var result=source.copy();result.removeTagKey("Enchantments");result.removeTagKey("StoredEnchantments");
        return result.is(Items.ENCHANTED_BOOK)?asItem(result,Items.BOOK):result;
    }
    private EnchanterRules() {}
}
