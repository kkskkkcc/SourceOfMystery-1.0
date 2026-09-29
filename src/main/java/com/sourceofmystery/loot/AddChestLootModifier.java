package com.sourceofmystery.loot;

import com.google.common.base.Suppliers;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraftforge.common.loot.IGlobalLootModifier;
import net.minecraftforge.common.loot.LootModifier;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * 按表逐个模拟一个原版战利品池条目，往指定宝箱里独立追加物品。
 * 每条规则对应原版宝箱里的一个条目：抽 rolls 次，每次以 weight / total_weight 的概率抽中，
 * 抽中时给 count 个。照抄原版钻石条目的参数，就能得到和钻石完全一样的出现概率和数量。
 * 没有列出的宝箱不追加。配置见 data/sourceofmystery/loot_modifiers/*.json
 */
public class AddChestLootModifier extends LootModifier {

    public record ChestRule(ResourceLocation table, int minRolls, int maxRolls,
                            int weight, int totalWeight, int minCount, int maxCount) {
        public static final Codec<ChestRule> CODEC = RecordCodecBuilder.create(inst -> inst.group(
                ResourceLocation.CODEC.fieldOf("table").forGetter(ChestRule::table),
                Codec.INT.fieldOf("min_rolls").forGetter(ChestRule::minRolls),
                Codec.INT.fieldOf("max_rolls").forGetter(ChestRule::maxRolls),
                Codec.INT.fieldOf("weight").forGetter(ChestRule::weight),
                Codec.INT.fieldOf("total_weight").forGetter(ChestRule::totalWeight),
                Codec.INT.fieldOf("min_count").forGetter(ChestRule::minCount),
                Codec.INT.fieldOf("max_count").forGetter(ChestRule::maxCount)
        ).apply(inst, ChestRule::new));

        int roll(RandomSource random) {
            int total = 0;
            int rolls = random.nextIntBetweenInclusive(minRolls, Math.max(minRolls, maxRolls));
            for (int i = 0; i < rolls; i++) {
                if (random.nextInt(Math.max(1, totalWeight)) < weight) {
                    total += random.nextIntBetweenInclusive(minCount, Math.max(minCount, maxCount));
                }
            }
            return total;
        }
    }

    public static final Supplier<Codec<AddChestLootModifier>> CODEC = Suppliers.memoize(() ->
            RecordCodecBuilder.create(inst -> codecStart(inst).and(inst.group(
                    ForgeRegistries.ITEMS.getCodec().fieldOf("item").forGetter(m -> m.item),
                    ChestRule.CODEC.listOf().fieldOf("tables").forGetter(m -> m.rules)
            )).apply(inst, AddChestLootModifier::new)));

    private final Item item;
    private final List<ChestRule> rules;
    private final Map<ResourceLocation, ChestRule> rulesByTable;

    public AddChestLootModifier(LootItemCondition[] conditions, Item item, List<ChestRule> rules) {
        super(conditions);
        this.item = item;
        this.rules = rules;
        this.rulesByTable = rules.stream().collect(Collectors.toMap(ChestRule::table, Function.identity(), (a, b) -> a));
    }

    @Override
    protected @NotNull ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> generatedLoot, LootContext context) {
        ChestRule rule = rulesByTable.get(context.getQueriedLootTableId());
        if (rule == null) {
            return generatedLoot;
        }
        int count = rule.roll(context.getRandom());
        // 按最大堆叠拆分，避免一格里超过 64
        int maxStack = item.getDefaultInstance().getMaxStackSize();
        while (count > 0) {
            int stack = Math.min(count, maxStack);
            generatedLoot.add(new ItemStack(item, stack));
            count -= stack;
        }
        return generatedLoot;
    }

    @Override
    public Codec<? extends IGlobalLootModifier> codec() {
        return CODEC.get();
    }
}
