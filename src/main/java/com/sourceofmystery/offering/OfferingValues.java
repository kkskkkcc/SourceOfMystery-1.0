package com.sourceofmystery.offering;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.util.RandomSource;
import net.minecraftforge.registries.ForgeRegistries;
import java.util.*;

/** Server-side registry/recipe snapshot. No namespace whitelist or minimum-input tier. */
public final class OfferingValues {
    private static final Map<MinecraftServer, OfferingValues> CACHE=new WeakHashMap<>();
    public record Entry(Item item, long value) {}
    private final List<Entry> entries;
    private final Map<Item,Long> values;
    private final List<? extends String> overrides;
    public static synchronized void invalidate() { CACHE.clear(); }
    public static synchronized OfferingValues get(MinecraftServer server) {
        var old=CACHE.get(server);
        if(old==null || !old.overrides.equals(OfferingLotteryConfig.VALUES.get())) {
            old=new OfferingValues(server); CACHE.put(server,old);
        }
        return old;
    }
    private OfferingValues(MinecraftServer server) {
        overrides=List.copyOf(OfferingLotteryConfig.VALUES.get());
        var level=server.overworld(); var floors=new HashMap<Item,Long>();
        for(Item item:ForgeRegistries.ITEMS) if(item!=Items.AIR && item.isEnabled(level.enabledFeatures())) {
            long value=4;try {value=base(item);}catch(RuntimeException | LinkageError ignored) {}
            floors.put(item,value);
        }
        var locked=new HashSet<Item>();
        for(String line:overrides) {
            var p=line.split("=",2); var id=net.minecraft.resources.ResourceLocation.tryParse(p[0]);
            if(id!=null && ForgeRegistries.ITEMS.containsKey(id)) {
                var item=ForgeRegistries.ITEMS.getValue(id); if(floors.containsKey(item)){floors.put(item,Long.parseLong(p[1])); locked.add(item);}
            }
        }
        var current=new HashMap<>(floors);
        // Precompile ingredients once: unsupported dynamic recipes retain their attribute/tag estimate.
        record Cost(Item output,int count,List<Item[]> ingredients) {}
        var recipes=new ArrayList<Cost>();
        for(Recipe<?> recipe:server.getRecipeManager().getRecipes()) try {
            var out=recipe.getResultItem(level.registryAccess()); var ingredients=recipe.getIngredients();
            if(out.isEmpty() || !floors.containsKey(out.getItem()) || ingredients.isEmpty() || ingredients.size()>81) continue;
            var parts=new ArrayList<Item[]>(); boolean valid=true;
            for(var ingredient:ingredients) {
                if(ingredient.isEmpty()) continue;
                var options=Arrays.stream(ingredient.getItems()).filter(s->!s.isEmpty() && s.getItem()!=out.getItem()).map(ItemStack::getItem).distinct().toArray(Item[]::new);
                if(options.length==0) { valid=false; break; } parts.add(options);
            }
            if(valid && !parts.isEmpty()) recipes.add(new Cost(out.getItem(),out.getCount(),parts));
        } catch(RuntimeException | LinkageError ignored) { /* A mod's dynamic recipe need not support static inspection. */ }
        for(int pass=0;pass<6;pass++) {
            var costs=new HashMap<Item,Long>();
            for(var r:recipes) {
                long sum=0;
                for(var options:r.ingredients()) {
                    long cheapest=Long.MAX_VALUE;
                    for(var item:options) cheapest=Math.min(cheapest,current.getOrDefault(item,1L));
                    sum=Math.min(1_000_000_000L,sum+cheapest);
                }
                costs.merge(r.output(),Math.max(1,(sum+r.count()-1)/r.count()),Math::min);
            }
            for(var e:costs.entrySet()) if(!locked.contains(e.getKey())) current.put(e.getKey(),Math.max(floors.get(e.getKey()),e.getValue()));
        }
        values=Map.copyOf(current);
        entries=current.entrySet().stream().sorted(Comparator.comparing(e->ForgeRegistries.ITEMS.getKey(e.getKey()).toString()))
                .map(e->new Entry(e.getKey(),stack(prize(new Entry(e.getKey(),e.getValue()))))).toList();
    }
    private static long base(Item item) {
        ItemStack stack;
        try { stack=item.getDefaultInstance(); } catch(RuntimeException | LinkageError e) { stack=new ItemStack(item); }
        long v=rarityValue(item.getRarity(stack));
        if(item instanceof TieredItem t) v=Math.max(v,16+(long)(t.getTier().getLevel()+1)*48+t.getTier().getUses()/3);
        if(item instanceof ArmorItem a) v=Math.max(v,24+a.getDefense()*60L+(long)(a.getToughness()*120)+stack.getMaxDamage()/3);
        if(item instanceof BowItem || item instanceof CrossbowItem || item instanceof ShieldItem) v=Math.max(v,160);
        if(item instanceof BlockItem) v=Math.max(v,4);
        if(item.isEdible()) v=Math.max(v,8+item.getFoodProperties().getNutrition()*4L);
        for(var tag:stack.getTags().toList()) {
            String p=tag.location().getPath();
            if(p.startsWith("gems")) v=Math.max(v,256);
            if(p.startsWith("ingots")) v=Math.max(v,32);
            if(p.startsWith("ores")) v=Math.max(v,48);
            if(p.startsWith("storage_blocks")) v=Math.max(v,288);
        }
        // Stable material anchors; all other namespaces still use exactly the same rules above.
        if(item==Items.DIAMOND || item==Items.DIAMOND_BLOCK) return item==Items.DIAMOND?1024:9216;
        if(item==Items.EMERALD || item==Items.EMERALD_BLOCK) return item==Items.EMERALD?512:4608;
        if(item==Items.NETHERITE_INGOT || item==Items.NETHERITE_BLOCK) return item==Items.NETHERITE_INGOT?4096:36864;
        if(item==Items.NETHER_STAR) return 8192;
        if(item==Items.GOLD_INGOT) return 96;
        if(item==Items.IRON_INGOT) return 32;
        if(item==Items.COPPER_INGOT) return 12;
        if(item==Items.REDSTONE) return 8;
        if(item==Items.LAPIS_LAZULI) return 16;
        if(item==Items.BARRIER || item==Items.COMMAND_BLOCK || item==Items.STRUCTURE_BLOCK || item==Items.JIGSAW) return 65536;
        return v;
    }
    /** Forge's Rarity is extensible. Exhaustive enum switches throw ICCE on added values. */
    public static long rarityValue(Rarity rarity) {
        if(rarity==null || rarity==Rarity.COMMON)return 4;
        if(rarity==Rarity.UNCOMMON)return 80;
        if(rarity==Rarity.RARE)return 512;
        if(rarity==Rarity.EPIC)return 2048;
        return Math.min(16384,Math.max(2048,((long)rarity.ordinal()+1)*512));
    }
    public List<Entry> entries() { return entries; }
    public long unit(Item item) { return values.getOrDefault(item,4L); }
    public long stack(ItemStack stack) {
        if(stack.isEmpty()) return 0;
        double v=unit(stack.getItem());
        try { if(stack.isDamageableItem()) v*=Math.max(.1,1-(double)stack.getDamageValue()/Math.max(1,stack.getMaxDamage())); }catch(RuntimeException | LinkageError ignored) {}
        try { for(var e:EnchantmentHelper.getEnchantments(stack).entrySet()) v+=Math.min(255,Math.max(0,e.getValue()))*Math.max(8,64/e.getKey().getRarity().getWeight()); } catch(RuntimeException | LinkageError ignored) {}
        return Math.max(1,Math.min(64_000_000_000L,Math.round(v)*stack.getCount()));
    }
    public static double quality(long value) { double x=Math.log1p(Math.max(1,value)); return x/(x+5); }
    public static int tier(long value) { return value>=4096?4:value>=1024?3:value>=256?2:value>=64?1:0; }
    public static double luckFactor(double luck) {return Double.isFinite(luck)?Math.max(.1,Math.min(100,1+luck*OfferingLotteryConfig.LUCK_BONUS.get())):1;}
    public Distribution distribution(long input) {return distribution(input,0);}
    public Distribution distribution(long input,double luck) {return new Distribution(entries,input,OfferingLotteryConfig.GOOD_CHANCE_MULTIPLIER.get()*luckFactor(luck));}
    public static final class Distribution {
        private final List<Entry> entries;
        private final double[] cumulative;
        private final double total;
        public Distribution(List<Entry> entries,long input) {this(entries,input,1);}
        public Distribution(List<Entry> entries,long input,double goodMultiplier) {
            this.entries=entries; cumulative=new double[entries.size()];
            double beta=-1.4+3*quality(input),max=Double.NEGATIVE_INFINITY;
            for(var e:entries) max=Math.max(max,beta*Math.log1p(e.value()/16d));
            double good=0,other=0;double[] weights=new double[entries.size()];
            for(int i=0;i<entries.size();i++) {
                // This floor is intentional: even the cheapest input can win any registered item.
                weights[i]=Math.max(1e-6,Math.exp(beta*Math.log1p(entries.get(i).value()/16d)-max));
                if(entries.get(i).value()>input)good+=weights[i];else other+=weights[i];
            }
            double goodScale=1,otherScale=1;
            if(good>0 && other>0) {
                double baseline=good/(good+other),target=baseline*goodMultiplier;
                // Keep all candidates possible, including at very high Luck. Never reduce a high baseline with a positive boost.
                double ceiling=Math.max(baseline,.9999);
                target=Math.max(baseline*.01,Math.min(ceiling,target));
                goodScale=target/baseline;otherScale=(1-target)/(1-baseline);
            }
            double sum=0;
            for(int i=0;i<entries.size();i++) {
                sum+=weights[i]*(entries.get(i).value()>input?goodScale:otherScale);cumulative[i]=sum;
            }
            total=sum;
        }
        public Entry sample(RandomSource random) {
            if(entries.isEmpty()) throw new IllegalStateException("Empty offering pool");
            double needle=random.nextDouble()*total;
            int low=0,high=cumulative.length-1;
            while(low<high) {int mid=(low+high)>>>1;if(needle<cumulative[mid])high=mid;else low=mid+1;}
            return entries.get(low);
        }
        public double probability(int index) { return (cumulative[index]-(index==0?0:cumulative[index-1]))/total; }
        public double expectedValue() { double value=0;for(int i=0;i<entries.size();i++)value+=probability(i)*entries.get(i).value();return value; }
        public double goodProbability(long input) {double value=0;for(int i=0;i<entries.size();i++)if(entries.get(i).value()>input)value+=probability(i);return value;}
    }
    public static ItemStack prize(Entry entry) {
        ItemStack stack;
        try { stack=entry.item().getDefaultInstance().copy(); } catch(RuntimeException | LinkageError e) { stack=new ItemStack(entry.item()); }
        if(stack.isEmpty()) stack=new ItemStack(entry.item());
        stack.setCount(1);return stack;
    }
}
