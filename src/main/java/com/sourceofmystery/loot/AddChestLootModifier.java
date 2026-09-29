package com.sourceofmystery.loot;

import com.google.common.base.Suppliers;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraftforge.common.loot.IGlobalLootModifier;
import net.minecraftforge.common.loot.LootModifier;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.NotNull;

import java.util.function.Supplier;

/**
 * 往所有宝箱战利品表（路径以 chests/ 开头，包括其他模组的宝箱）里追加随机数量的物品。
 * 配置见 data/sourceofmystery/loot_modifiers/*.json
 */
public class AddChestLootModifier extends LootModifier {

    public static final Supplier<Codec<AddChestLootModifier>> CODEC = Suppliers.memoize(() ->
            RecordCodecBuilder.create(inst -> codecStart(inst).and(inst.group(
                    ForgeRegistries.ITEMS.getCodec().fieldOf("item").forGetter(m -> m.item),
                    Codec.INT.fieldOf("min").forGetter(m -> m.min),
                    Codec.INT.fieldOf("max").forGetter(m -> m.max)
            )).apply(inst, AddChestLootModifier::new)));

    private static final String CHEST_TABLE_PREFIX = "chests/";

    private final Item item;
    private final int min;
    private final int max;

    public AddChestLootModifier(LootItemCondition[] conditions, Item item, int min, int max) {
        super(conditions);
        this.item = item;
        this.min = min;
        this.max = Math.max(min, max);
    }

    @Override
    protected @NotNull ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> generatedLoot, LootContext context) {
        if (context.getQueriedLootTableId().getPath().startsWith(CHEST_TABLE_PREFIX)) {
            generatedLoot.add(new ItemStack(item, context.getRandom().nextIntBetweenInclusive(min, max)));
        }
        return generatedLoot;
    }

    @Override
    public Codec<? extends IGlobalLootModifier> codec() {
        return CODEC.get();
    }
}
