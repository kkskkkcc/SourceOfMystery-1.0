package com.sourceofmystery.alchemy;

import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.event.OnDatapackSyncEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.*;

/** One persisted daily quote sheet shared by every table and portable opening in a world. */
@Mod.EventBusSubscriber(modid="sourceofmystery")
public final class AlchemyShop extends SavedData {
    public static final int COUNT=20,MAX_MATERIALS=9;
    public record Trade(ItemStack output,List<ItemStack> materials){}
    private long day=Long.MIN_VALUE;
    private UUID revision=UUID.randomUUID();
    private List<Trade> trades=List.of();
    public long day(){return day;}
    public UUID revision(){return revision;}
    public List<Trade> trades(){return trades;}
    public static AlchemyShop get(MinecraftServer server){
        var shop=server.overworld().getDataStorage().computeIfAbsent(AlchemyShop::load,AlchemyShop::new,"sourceofmystery_alchemy_shop");
        shop.refresh(server);return shop;
    }
    private void refresh(MinecraftServer server){
        long today=Math.floorDiv(server.overworld().getDayTime(),24000);
        if(day==today&&trades.size()==COUNT)return;
        var pool=new ArrayList<>(AlchemyCatalog.get(server));
        if(pool.size()<COUNT)return;
        var random=new Random(server.overworld().getSeed()^today*0x9e3779b97f4a7c15L^0x416c6368656d79L);
        Collections.shuffle(pool,random);var selected=new ArrayList<Trade>();
        for(int i=0;i<COUNT;i++){
            var template=pool.get(i);var materials=new ArrayList<ItemStack>();
            for(var part:template.materials()){
                var cost=part.stack().copy();cost.setCount(1+random.nextInt(part.maximum()));materials.add(cost);
            }
            selected.add(new Trade(template.output().copy(),List.copyOf(materials)));
        }
        trades=List.copyOf(selected);day=today;revision=UUID.randomUUID();setDirty();
    }
    @Override public CompoundTag save(CompoundTag tag){
        tag.putLong("Day",day);tag.putUUID("Revision",revision);var list=new ListTag();
        for(var trade:trades){var entry=new CompoundTag();entry.put("Output",trade.output().save(new CompoundTag()));
            var costs=new ListTag();for(var cost:trade.materials())costs.add(cost.save(new CompoundTag()));entry.put("Materials",costs);list.add(entry);}
        tag.put("Trades",list);return tag;
    }
    public static AlchemyShop load(CompoundTag tag){
        var shop=new AlchemyShop();shop.day=tag.getLong("Day");if(tag.hasUUID("Revision"))shop.revision=tag.getUUID("Revision");
        var trades=new ArrayList<Trade>();var list=tag.getList("Trades",Tag.TAG_COMPOUND);
        for(int i=0;i<Math.min(COUNT,list.size());i++)try{
            var entry=list.getCompound(i);var output=ItemStack.of(entry.getCompound("Output"));var costs=entry.getList("Materials",Tag.TAG_COMPOUND);var materials=new ArrayList<ItemStack>();
            for(int j=0;j<costs.size()&&j<MAX_MATERIALS;j++){var cost=ItemStack.of(costs.getCompound(j));if(!cost.isEmpty()&&cost.getCount()<=64)materials.add(cost);}
            if(!output.isEmpty()&&!materials.isEmpty()&&materials.size()==costs.size())trades.add(new Trade(output,List.copyOf(materials)));
        }catch(RuntimeException|LinkageError ignored){}
        shop.trades=List.copyOf(trades);return shop;
    }
    @SubscribeEvent public static void reload(OnDatapackSyncEvent event){if(event.getPlayer()==null)AlchemyCatalog.invalidate();}
}
