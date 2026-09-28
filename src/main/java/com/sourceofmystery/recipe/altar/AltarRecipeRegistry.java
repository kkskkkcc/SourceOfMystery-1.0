package com.sourceofmystery.recipe.altar;

import com.sourceofmystery.item.ModItems;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;

/**
 * 神秘祭坛配方注册器
 * 按照设计文档精确匹配材料
 */
@Mod.EventBusSubscriber(modid = com.sourceofmystery.SourceOfMystery.MOD_ID)
public class AltarRecipeRegistry {

    public static void registerRecipes() {
        AltarRecipeManager manager = AltarRecipeManager.getInstance();

        // ==================== S+ 级配方（最高级） ====================

        // 神威天罚剑 (S级) - 天道碎片×4 + 下界之星×2 + 始源龙剑
        registerRecipe(manager,
                AltarRecipeTier.S,
                new ItemStack(ModItems.DIVINE_PUNISHMENT_SWORD.get()),
                "神威天罚剑",
                ModItems.HEAVENLY_DAO_FRAGMENT.get(), 4,
                Items.NETHER_STAR, 2,
                ModItems.ORIGIN_DRAGON_SWORD.get(), 1
        );

        // 神威天佑胸甲 (S+级) - 天道碎片×4 + 下界之星×2 + 始源龙甲
        registerRecipe(manager,
                AltarRecipeTier.S_PLUS,
                new ItemStack(ModItems.DIVINE_BLESSING_CHESTPLATE.get()),
                "神威天佑胸甲",
                ModItems.HEAVENLY_DAO_FRAGMENT.get(), 4,
                Items.NETHER_STAR, 2,
                ModItems.ORIGIN_DRAGON_CHESTPLATE.get(), 1
        );

        // ==================== A 级配方 ====================

        // 始源龙剑 (A级) - 龙蛋×1 + 末地水晶×4 + 下界之星×1 + 龙首×1 + 鞘翅×1 + 暗源之剑
        registerRecipe(manager,
                AltarRecipeTier.A,
                new ItemStack(ModItems.ORIGIN_DRAGON_SWORD.get()),
                "始源龙剑",
                Items.DRAGON_EGG, 1,
                Items.END_CRYSTAL, 4,
                Items.NETHER_STAR, 1,
                Items.DRAGON_HEAD, 1,
                Items.ELYTRA, 1,
                ModItems.DARK_SOURCE_SWORD.get(), 1
        );

        // 始源龙甲 (A+级) - 龙蛋×1 + 末地水晶×4 + 下界之星×1 + 龙首×1 + 鞘翅×1 + 暗源之甲
        registerRecipe(manager,
                AltarRecipeTier.A_PLUS,
                new ItemStack(ModItems.ORIGIN_DRAGON_CHESTPLATE.get()),
                "始源龙甲",
                Items.DRAGON_EGG, 1,
                Items.END_CRYSTAL, 4,
                Items.NETHER_STAR, 1,
                Items.DRAGON_HEAD, 1,
                Items.ELYTRA, 1,
                ModItems.DARK_SOURCE_CHESTPLATE.get(), 1
        );

        // ==================== B+ 级配方 ====================

        // 暗源之剑 (B级) - 凋零骷髅头×2 + 下界合金锭×4 + 下界之星×2 + 灵源秘剑
        registerRecipe(manager,
                AltarRecipeTier.B,
                new ItemStack(ModItems.DARK_SOURCE_SWORD.get()),
                "暗源之剑",
                Items.WITHER_SKELETON_SKULL, 2,
                Items.NETHERITE_INGOT, 4,
                Items.NETHER_STAR, 2,
                ModItems.SPIRIT_SOURCE_SWORD.get(), 1
        );

        // 暗源之甲 (B+级) - 凋零骷髅头×2 + 下界合金锭×4 + 下界之星×2 + 灵源秘甲
        registerRecipe(manager,
                AltarRecipeTier.B_PLUS,
                new ItemStack(ModItems.DARK_SOURCE_CHESTPLATE.get()),
                "暗源之甲",
                Items.WITHER_SKELETON_SKULL, 2,
                Items.NETHERITE_INGOT, 4,
                Items.NETHER_STAR, 2,
                ModItems.SPIRIT_SOURCE_CHESTPLATE.get(), 1
        );

        // ==================== C+ 级配方 ====================

        // 灵源秘剑 (C级) - 秘源锭×4 + 下界合金锭×4 + 秘源剑
        registerRecipe(manager,
                AltarRecipeTier.C,
                new ItemStack(ModItems.SPIRIT_SOURCE_SWORD.get()),
                "灵源秘剑",
                ModItems.MYSTERY_INGOT.get(), 4,
                Items.NETHERITE_INGOT, 4,
                ModItems.MYSTERY_SWORD.get(), 1
        );

        // 灵源秘甲 (C+级) - 秘源锭×4 + 下界合金锭×4 + 秘源甲
        registerRecipe(manager,
                AltarRecipeTier.C_PLUS,
                new ItemStack(ModItems.SPIRIT_SOURCE_CHESTPLATE.get()),
                "灵源秘甲",
                ModItems.MYSTERY_INGOT.get(), 4,
                Items.NETHERITE_INGOT, 4,
                ModItems.MYSTERY_CHESTPLATE.get(), 1
        );

        // ==================== D 级配方 ====================

        // 秘源剑 (D级) - 铁锭×4 + 秘源锭×4 + 钻石剑
        registerRecipe(manager,
                AltarRecipeTier.D,
                new ItemStack(ModItems.MYSTERY_SWORD.get()),
                "秘源剑",
                Items.IRON_INGOT, 4,
                ModItems.MYSTERY_INGOT.get(), 4,
                Items.DIAMOND_SWORD, 1
        );

        // 秘源甲 (D+级) - 铁锭×4 + 秘源锭×4 + 钻石
        registerRecipe(manager,
                AltarRecipeTier.D_PLUS,
                new ItemStack(ModItems.MYSTERY_CHESTPLATE.get()),
                "秘源甲",
                Items.IRON_INGOT, 4,
                ModItems.MYSTERY_INGOT.get(), 4,
                Items.DIAMOND, 1
        );

        com.sourceofmystery.SourceOfMystery.LOGGER.info("Registered {} altar recipes", manager.getRecipeCount());
    }

    /**
     * 注册祭坛配方
     */
    private static void registerRecipe(AltarRecipeManager manager, AltarRecipeTier tier,
                                      ItemStack output, String description,
                                      Object... materials) {
        Map<Item, Integer> ingredients = parseMaterials(materials);
        AltarRecipe recipe = new AltarRecipe(tier, ingredients, output, description);
        manager.registerRecipe(recipe);
    }

    /**
     * 解析材料参数
     * 格式: Item, count, Item, count, ...
     */
    private static Map<Item, Integer> parseMaterials(Object... args) {
        Map<Item, Integer> materials = new HashMap<>();
        for (int i = 0; i < args.length - 1; i += 2) {
            Item item = (Item) args[i];
            int count = (Integer) args[i + 1];
            materials.merge(item, count, Integer::sum);
        }
        return materials;
    }
}
