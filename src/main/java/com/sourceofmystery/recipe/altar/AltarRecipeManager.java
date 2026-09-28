package com.sourceofmystery.recipe.altar;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.*;

/**
 * 神秘祭坛配方管理器
 * 负责管理所有祭坛配方，提供配方匹配和查找功能
 */
public class AltarRecipeManager {
    private static final AltarRecipeManager INSTANCE = new AltarRecipeManager();
    private final List<AltarRecipe> recipes = new ArrayList<>();
    private final Map<Item, List<AltarRecipe>> recipesByOutput = new HashMap<>();

    private AltarRecipeManager() {
    }

    public static AltarRecipeManager getInstance() {
        return INSTANCE;
    }

    /**
     * 注册一个祭坛配方
     */
    public void registerRecipe(AltarRecipe recipe) {
        recipes.add(recipe);
        // 按输出物品索引
        Item outputItem = recipe.getOutput().getItem();
        recipesByOutput.computeIfAbsent(outputItem, k -> new ArrayList<>()).add(recipe);
        // 按优先级排序（高优先级在前）
        recipes.sort((a, b) -> b.getTier().getPriority() - a.getTier().getPriority());
    }

    /**
     * 获取所有已注册的配方
     */
    public List<AltarRecipe> getAllRecipes() {
        return Collections.unmodifiableList(recipes);
    }

    /**
     * 根据输出物品查找配方
     */
    public List<AltarRecipe> getRecipesForOutput(Item item) {
        return recipesByOutput.getOrDefault(item, Collections.emptyList());
    }

    /**
     * 根据玩家周围的物品查找最高优先级的可用配方
     * @param availableItems 玩家周围可用的物品映射
     * @return 最高优先级的配方，如果没有可用配方则返回 null
     */
    public AltarRecipe findBestMatchingRecipe(Map<Item, Integer> availableItems) {
        if (availableItems == null || availableItems.isEmpty()) {
            return null;
        }

        // 遍历所有配方，找到第一个匹配的（因为已按优先级排序）
        for (AltarRecipe recipe : recipes) {
            if (recipe.matches(availableItems)) {
                return recipe;
            }
        }
        return null;
    }

    /**
     * 检查是否有任何配方可以匹配
     */
    public boolean hasAnyMatch(Map<Item, Integer> availableItems) {
        return findBestMatchingRecipe(availableItems) != null;
    }

    /**
     * 获取配方的详细信息描述
     */
    public String getRecipeDescription(AltarRecipe recipe) {
        StringBuilder sb = new StringBuilder();
        sb.append("§6【").append(recipe.getTier().getDisplayName()).append("级配方】§r ");
        sb.append(recipe.getDescription()).append("\n");

        sb.append("§7材料需求：§r\n");
        for (Map.Entry<Item, Integer> entry : recipe.getIngredients().entrySet()) {
            ItemStack stack = entry.getKey().getDefaultInstance();
            String itemName = stack.getHoverName().getString();
            sb.append("§e- ").append(entry.getValue()).append("x §7").append(itemName).append("§r\n");
        }

        sb.append("§a→ 输出：§r").append(recipe.getOutput().getCount())
                .append("x §f").append(recipe.getOutput().getHoverName().getString()).append("§r");

        return sb.toString();
    }

    /**
     * 清空所有配方（主要用于测试）
     */
    public void clearRecipes() {
        recipes.clear();
        recipesByOutput.clear();
    }

    /**
     * 获取配方数量
     */
    public int getRecipeCount() {
        return recipes.size();
    }
}
