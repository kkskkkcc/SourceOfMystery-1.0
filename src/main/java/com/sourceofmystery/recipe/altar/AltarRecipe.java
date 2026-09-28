package com.sourceofmystery.recipe.altar;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.Map;

/**
 * 神秘祭坛配方类
 * 包含输入材料、输出物品和优先级
 */
public class AltarRecipe {
    private final AltarRecipeTier tier;
    private final Map<Item, Integer> ingredients; // 材料 -> 数量
    private final ItemStack output;
    private final String description;

    public AltarRecipe(AltarRecipeTier tier, Map<Item, Integer> ingredients, ItemStack output, String description) {
        this.tier = tier;
        this.ingredients = new HashMap<>(ingredients);
        this.output = output;
        this.description = description;
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

    public String getDescription() {
        return description;
    }

    /**
     * 获取配方的总材料数量
     */
    public int getTotalIngredientCount() {
        return ingredients.values().stream().mapToInt(Integer::intValue).sum();
    }

    /**
     * 检查给定的物品是否满足此配方的所有材料需求
     */
    public boolean matches(Map<Item, Integer> availableItems) {
        for (Map.Entry<Item, Integer> entry : ingredients.entrySet()) {
            int required = entry.getValue();
            int available = availableItems.getOrDefault(entry.getKey(), 0);
            if (available < required) {
                return false;
            }
        }
        return true;
    }

    /**
     * 消耗配方所需的材料（从可用物品中减去）
     */
    public void consumeIngredients(Map<Item, Integer> availableItems) {
        for (Map.Entry<Item, Integer> entry : ingredients.entrySet()) {
            int consumed = entry.getValue();
            int current = availableItems.getOrDefault(entry.getKey(), 0);
            availableItems.put(entry.getKey(), Math.max(0, current - consumed));
        }
    }

    @Override
    public String toString() {
        return "AltarRecipe{" +
                "tier=" + tier.getDisplayName() +
                ", output=" + output.getHoverName().getString() +
                ", description='" + description + '\'' +
                '}';
    }
}
