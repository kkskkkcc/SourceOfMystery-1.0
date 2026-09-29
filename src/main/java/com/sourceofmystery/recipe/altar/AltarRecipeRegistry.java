package com.sourceofmystery.recipe.altar;

import com.sourceofmystery.SourceOfMystery;
import com.sourceofmystery.item.ModItems;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;

import java.util.HashMap;
import java.util.Map;

/**
 * 神秘祭坛配方注册器
 * 按照设计文档精确匹配材料
 */
public class AltarRecipeRegistry {

    public static void registerRecipes() {
        // ==================== S / S+ 级配方（最高级） ====================

        // 神威天罚剑 (S) - 天道碎片×4 + 下界之星×2 + 始源龙剑
        recipe(AltarRecipeTier.S, ModItems.DIVINE_PUNISHMENT_SWORD.get())
                .needs(ModItems.HEAVENLY_DAO_FRAGMENT.get(), 4)
                .needs(Items.NETHER_STAR, 2)
                .needs(ModItems.ORIGIN_DRAGON_SWORD.get(), 1)
                .register();

        // 神威天佑胸甲 (S+) - 天道碎片×4 + 下界之星×2 + 始源龙甲
        recipe(AltarRecipeTier.S_PLUS, ModItems.DIVINE_BLESSING_CHESTPLATE.get())
                .needs(ModItems.HEAVENLY_DAO_FRAGMENT.get(), 4)
                .needs(Items.NETHER_STAR, 2)
                .needs(ModItems.ORIGIN_DRAGON_CHESTPLATE.get(), 1)
                .register();

        // ==================== A / A+ 级配方 ====================

        // 始源龙剑 (A) - 龙蛋×1 + 末地水晶×4 + 下界之星×1 + 龙首×1 + 鞘翅×1 + 暗源之剑
        recipe(AltarRecipeTier.A, ModItems.ORIGIN_DRAGON_SWORD.get())
                .needs(Items.DRAGON_EGG, 1)
                .needs(Items.END_CRYSTAL, 4)
                .needs(Items.NETHER_STAR, 1)
                .needs(Items.DRAGON_HEAD, 1)
                .needs(Items.ELYTRA, 1)
                .needs(ModItems.DARK_SOURCE_SWORD.get(), 1)
                .register();

        // 始源龙甲 (A+) - 龙蛋×1 + 末地水晶×4 + 下界之星×1 + 龙首×1 + 鞘翅×1 + 暗源之甲
        recipe(AltarRecipeTier.A_PLUS, ModItems.ORIGIN_DRAGON_CHESTPLATE.get())
                .needs(Items.DRAGON_EGG, 1)
                .needs(Items.END_CRYSTAL, 4)
                .needs(Items.NETHER_STAR, 1)
                .needs(Items.DRAGON_HEAD, 1)
                .needs(Items.ELYTRA, 1)
                .needs(ModItems.DARK_SOURCE_CHESTPLATE.get(), 1)
                .register();

        // ==================== B / B+ 级配方 ====================

        // 暗源之剑 (B) - 凋零骷髅头×2 + 下界合金锭×4 + 下界之星×2 + 灵源秘剑
        recipe(AltarRecipeTier.B, ModItems.DARK_SOURCE_SWORD.get())
                .needs(Items.WITHER_SKELETON_SKULL, 2)
                .needs(Items.NETHERITE_INGOT, 4)
                .needs(Items.NETHER_STAR, 2)
                .needs(ModItems.SPIRIT_SOURCE_SWORD.get(), 1)
                .register();

        // 暗源之甲 (B+) - 凋零骷髅头×2 + 下界合金锭×4 + 下界之星×2 + 灵源秘甲
        recipe(AltarRecipeTier.B_PLUS, ModItems.DARK_SOURCE_CHESTPLATE.get())
                .needs(Items.WITHER_SKELETON_SKULL, 2)
                .needs(Items.NETHERITE_INGOT, 4)
                .needs(Items.NETHER_STAR, 2)
                .needs(ModItems.SPIRIT_SOURCE_CHESTPLATE.get(), 1)
                .register();

        // ==================== C / C+ 级配方 ====================

        // 灵源秘剑 (C) - 秘源锭×4 + 下界合金锭×4 + 秘源剑
        recipe(AltarRecipeTier.C, ModItems.SPIRIT_SOURCE_SWORD.get())
                .needs(ModItems.MYSTERY_INGOT.get(), 4)
                .needs(Items.NETHERITE_INGOT, 4)
                .needs(ModItems.MYSTERY_SWORD.get(), 1)
                .register();

        // 灵源秘甲 (C+) - 秘源锭×4 + 下界合金锭×4 + 秘源甲
        recipe(AltarRecipeTier.C_PLUS, ModItems.SPIRIT_SOURCE_CHESTPLATE.get())
                .needs(ModItems.MYSTERY_INGOT.get(), 4)
                .needs(Items.NETHERITE_INGOT, 4)
                .needs(ModItems.MYSTERY_CHESTPLATE.get(), 1)
                .register();

        // ==================== D / D+ 级配方 ====================

        // 秘源剑 (D) - 铁锭×4 + 秘源锭×4 + 钻石剑
        recipe(AltarRecipeTier.D, ModItems.MYSTERY_SWORD.get())
                .needs(Items.IRON_INGOT, 4)
                .needs(ModItems.MYSTERY_INGOT.get(), 4)
                .needs(Items.DIAMOND_SWORD, 1)
                .register();

        // 秘源甲 (D+) - 铁锭×4 + 秘源锭×4 + 钻石
        recipe(AltarRecipeTier.D_PLUS, ModItems.MYSTERY_CHESTPLATE.get())
                .needs(Items.IRON_INGOT, 4)
                .needs(ModItems.MYSTERY_INGOT.get(), 4)
                .needs(Items.DIAMOND, 1)
                .register();

        SourceOfMystery.LOGGER.info("Registered {} altar recipes", AltarRecipeManager.getInstance().getRecipeCount());
    }

    private static Builder recipe(AltarRecipeTier tier, ItemLike output) {
        return new Builder(tier, new ItemStack(output));
    }

    private static class Builder {
        private final AltarRecipeTier tier;
        private final ItemStack output;
        private final Map<Item, Integer> ingredients = new HashMap<>();

        Builder(AltarRecipeTier tier, ItemStack output) {
            this.tier = tier;
            this.output = output;
        }

        Builder needs(ItemLike item, int count) {
            ingredients.merge(item.asItem(), count, Integer::sum);
            return this;
        }

        void register() {
            AltarRecipeManager.getInstance().registerRecipe(new AltarRecipe(tier, ingredients, output));
        }
    }
}
