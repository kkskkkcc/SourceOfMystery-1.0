package com.sourceofmystery.recipe.altar;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.Map;

/**
 * 神秘祭坛配方类
 * 包含输入材料、输出物品和优先级
 */
public class AltarRecipe {
    private final AltarRecipeTier tier;
    private final Map<Item, Integer> ingredients; // 材料 -> 数量
    private final ItemStack output;

    public AltarRecipe(AltarRecipeTier tier, Map<Item, Integer> ingredients, ItemStack output) {
        this.tier = tier;
        this.ingredients = Map.copyOf(ingredients);
        this.output = output;
    }

    public AltarRecipeTier getTier() {
        return tier;
    }

    public Map<Item, Integer> getIngredients() {
        return ingredients;
    }

    public ItemStack getOutput() {
        return output;
    }

    /**
     * 检查给定的物品是否满足此配方的所有材料需求
     */
    public boolean matches(Map<Item, Integer> availableItems) {
        for (Map.Entry<Item, Integer> entry : ingredients.entrySet()) {
            if (availableItems.getOrDefault(entry.getKey(), 0) < entry.getValue()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public String toString() {
        return "AltarRecipe{tier=" + tier.getDisplayName() + ", output=" + output + '}';
    }
}
