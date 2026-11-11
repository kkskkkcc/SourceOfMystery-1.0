package com.sourceofmystery.alchemy;

import com.sourceofmystery.offering.OfferingValues;
import net.minecraft.server.MinecraftServer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import net.minecraftforge.registries.ForgeRegistries;
import java.util.*;

/** Static recipe inspection is optional: unsupported dynamic mod recipes are skipped. */
public final class AlchemyCatalog {
    public record Material(ItemStack stack,int maximum){}
    public record Template(ItemStack output,List<Material> materials,long score){}
    private static final Map<MinecraftServer,List<Template>> CACHE=new WeakHashMap<>();
    private AlchemyCatalog(){}
    public static synchronized void invalidate(){CACHE.clear();}
    private static String id(Item item){return ForgeRegistries.ITEMS.getKey(item).toString();}
    private static int rank(Item item){
        for(var tag:item.builtInRegistryHolder().tags().toList()){
            String p=tag.location().getPath();
            if(p.equals("storage_blocks")||p.startsWith("storage_blocks/"))return 3;
            if(p.equals("ingots")||p.startsWith("ingots/")||p.equals("gems")||p.startsWith("gems/"))return 2;
            if(p.equals("nuggets")||p.startsWith("nuggets/")||p.equals("dusts")||p.startsWith("dusts/"))return 1;
        }
        return -1;
    }
    public static synchronized List<Template> get(MinecraftServer server){
        var cached=CACHE.get(server);if(cached!=null)return cached;
        var level=server.overworld();var values=OfferingValues.get(server);var best=new HashMap<Item,Template>();
        for(var recipe:server.getRecipeManager().getRecipes())try{
            var output=recipe.getResultItem(level.registryAccess());var parts=recipe.getIngredients();
            if(output.isEmpty()||!output.isItemEnabled(level.enabledFeatures())||parts.isEmpty()||parts.size()>81)continue;
            var materials=new ArrayList<Material>();boolean valid=true;int outputRank=rank(output.getItem());
            for(var ingredient:parts){
                if(ingredient.isEmpty())continue;
                ItemStack chosen=Arrays.stream(ingredient.getItems()).filter(s->!s.isEmpty()&&s.getItem()!=output.getItem()
                        &&s.isItemEnabled(level.enabledFeatures())&&(outputRank<0||rank(s.getItem())<=outputRank))
                        .min(Comparator.<ItemStack>comparingLong(s->values.unit(s.getItem())).thenComparing(s->id(s.getItem())))
                        .map(ItemStack::copy).orElse(ItemStack.EMPTY);
                if(chosen.isEmpty()){valid=false;break;}
                int amount=Math.max(1,chosen.getCount());chosen.setCount(1);int found=-1;
                for(int i=0;i<materials.size();i++)if(ItemStack.isSameItemSameTags(materials.get(i).stack(),chosen)){found=i;break;}
                if(found<0)materials.add(new Material(chosen,amount));
                else{var old=materials.get(found);materials.set(found,new Material(old.stack(),old.maximum()+amount));}
            }
            if(!valid||materials.isEmpty()||materials.size()>AlchemyShop.MAX_MATERIALS)continue;
            var normalized=new ArrayList<Material>();long score=0;
            for(var part:materials){int count=Math.min(64,Math.max(1,(part.maximum()+output.getCount()-1)/output.getCount()));
                normalized.add(new Material(part.stack(),count));score+=values.unit(part.stack().getItem())*count;}
            var result=output.copy();result.setCount(1);var candidate=new Template(result,List.copyOf(normalized),score);
            var old=best.get(result.getItem());if(old==null||score<old.score())best.put(result.getItem(),candidate);
        }catch(RuntimeException|LinkageError ignored){/* Some mods require live containers for recipe previews. */}
        // Forge material tags provide the natural lower tier even when reverse crafting is cheaper.
        for(Item item:ForgeRegistries.ITEMS)try{
            if(item==Items.AIR||!item.isEnabled(level.enabledFeatures()))continue;
            for(var tag:item.builtInRegistryHolder().tags().toList()){
                String path=tag.location().getPath(),material=null;
                if(path.startsWith("storage_blocks/")){
                    String type=path.substring("storage_blocks/".length());
                    for(String tier:List.of("ingots/","gems/","raw_materials/")){
                        var lower=tagItem(tier+type,item);if(lower!=null){material=id(lower);break;}
                    }
                }else if(path.startsWith("ingots/")){
                    var lower=tagItem("nuggets/"+path.substring("ingots/".length()),item);if(lower!=null)material=id(lower);
                }
                if(material!=null){Item lower=ForgeRegistries.ITEMS.getValue(new ResourceLocation(material));
                    var output=item.getDefaultInstance().copy();output.setCount(1);
                    best.put(item,new Template(output,List.of(new Material(new ItemStack(lower),9)),values.unit(lower)*9));break;}
            }
        }catch(RuntimeException|LinkageError ignored){}
        var result=best.values().stream().sorted(Comparator.comparing(t->id(t.output().getItem()))).toList();
        CACHE.put(server,result);return result;
    }
    private static Item tagItem(String path,Item output){
        var tags=ForgeRegistries.ITEMS.tags();if(tags==null)return null;
        var tag=tags.getTag(net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.ITEM,new ResourceLocation("forge",path)));
        String namespace=ForgeRegistries.ITEMS.getKey(output).getNamespace();
        return tag.stream().filter(i->i!=output).min(Comparator.<Item>comparingInt(i->ForgeRegistries.ITEMS.getKey(i).getNamespace().equals(namespace)?0:1).thenComparing(AlchemyCatalog::id)).orElse(null);
    }
}
