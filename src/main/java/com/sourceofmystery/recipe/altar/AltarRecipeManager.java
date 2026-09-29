package com.sourceofmystery.recipe.altar;

import net.minecraft.world.item.Item;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * 神秘祭坛配方管理器
 * 负责管理所有祭坛配方，提供配方匹配和查找功能
 */
public class AltarRecipeManager {
    private static final AltarRecipeManager INSTANCE = new AltarRecipeManager();
    private final List<AltarRecipe> recipes = new ArrayList<>();

    private AltarRecipeManager() {
    }

    public static AltarRecipeManager getInstance() {
        return INSTANCE;
    }

    /**
     * 注册一个祭坛配方，并保持按优先级从高到低排序
     */
    public void registerRecipe(AltarRecipe recipe) {
        recipes.add(recipe);
        recipes.sort(Comparator.comparingInt((AltarRecipe r) -> r.getTier().getPriority()).reversed());
    }

    /**
     * 获取所有已注册的配方
     */
    public List<AltarRecipe> getAllRecipes() {
        return Collections.unmodifiableList(recipes);
    }

    /**
     * 根据周围的物品查找最高优先级的可用配方
     * @param availableItems 可用的物品映射
     * @return 最高优先级的配方，如果没有可用配方则返回 null
     */
    public AltarRecipe findBestMatchingRecipe(Map<Item, Integer> availableItems) {
        if (availableItems == null || availableItems.isEmpty()) {
            return null;
        }
        // 已按优先级排序，第一个匹配的就是最佳配方
        for (AltarRecipe recipe : recipes) {
            if (recipe.matches(availableItems)) {
                return recipe;
            }
        }
        return null;
    }

    /**
     * 获取配方数量
     */
    public int getRecipeCount() {
        return recipes.size();
    }
}
