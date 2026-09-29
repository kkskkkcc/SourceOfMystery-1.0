package com.sourceofmystery.datagen;

import com.sourceofmystery.SourceOfMystery;
import com.sourceofmystery.item.ModItems;
import com.sourceofmystery.recipe.altar.AltarRecipeTier;
import net.minecraft.data.PackOutput;
import net.minecraft.data.recipes.FinishedRecipe;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.data.recipes.ShapedRecipeBuilder;
import net.minecraft.data.recipes.SimpleCookingRecipeBuilder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.function.Consumer;

import static com.sourceofmystery.datagen.AltarRecipeBuilder.altar;

public class ModRecipeProvider extends RecipeProvider {

    public ModRecipeProvider(PackOutput output) {
        super(output);
    }

    @Override
    protected void buildRecipes(Consumer<FinishedRecipe> output) {
        // ==================== 原版配方 ====================
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.MYSTERY_ALTAR.get())
                .pattern("MCM")
                .pattern("CWC")
                .pattern("MCM")
                .define('M', ModItems.MYSTERY_INGOT.get())
                .define('C', Items.COBBLESTONE)
                .define('W', Items.CRAFTING_TABLE)
                .unlockedBy("has_mystery_ingot", has(ModItems.MYSTERY_INGOT.get()))
                .save(output);

        SimpleCookingRecipeBuilder.smelting(Ingredient.of(ModItems.MYSTERY_SOURCE_ORE.get()), RecipeCategory.MISC,
                        ModItems.MYSTERY_INGOT.get(), 1.0f, 200)
                .unlockedBy("has_mystery_source_ore", has(ModItems.MYSTERY_SOURCE_ORE.get()))
                .save(output, new ResourceLocation(SourceOfMystery.MOD_ID, "mystery_ingot_smelting"));
        SimpleCookingRecipeBuilder.blasting(Ingredient.of(ModItems.MYSTERY_SOURCE_ORE.get()), RecipeCategory.MISC,
                        ModItems.MYSTERY_INGOT.get(), 1.0f, 100)
                .unlockedBy("has_mystery_source_ore", has(ModItems.MYSTERY_SOURCE_ORE.get()))
                .save(output, new ResourceLocation(SourceOfMystery.MOD_ID, "mystery_ingot_blasting"));

        // ==================== 神秘祭坛配方 ====================
        // S / S+
        altar(AltarRecipeTier.S, ModItems.DIVINE_PUNISHMENT_SWORD.get())
                .needs(ModItems.HEAVENLY_DAO_FRAGMENT.get(), 4)
                .needs(Items.NETHER_STAR, 2)
                .needs(ModItems.ORIGIN_DRAGON_SWORD.get(), 1)
                .save(output);
        altar(AltarRecipeTier.S_PLUS, ModItems.DIVINE_BLESSING_CHESTPLATE.get())
                .needs(ModItems.HEAVENLY_DAO_FRAGMENT.get(), 4)
                .needs(Items.NETHER_STAR, 2)
                .needs(ModItems.ORIGIN_DRAGON_CHESTPLATE.get(), 1)
                .save(output);

        // A / A+：龙魂由末影龙掉落（每只一个）
        altar(AltarRecipeTier.A, ModItems.ORIGIN_DRAGON_SWORD.get())
                .needs(ModItems.DRAGON_SOUL.get(), 1)
                .needs(Items.END_CRYSTAL, 4)
                .needs(Items.NETHER_STAR, 1)
                .needs(Items.DRAGON_HEAD, 1)
                .needs(Items.ELYTRA, 1)
                .needs(ModItems.DARK_SOURCE_SWORD.get(), 1)
                .save(output);
        altar(AltarRecipeTier.A_PLUS, ModItems.ORIGIN_DRAGON_CHESTPLATE.get())
                .needs(ModItems.DRAGON_SOUL.get(), 1)
                .needs(Items.END_CRYSTAL, 4)
                .needs(Items.NETHER_STAR, 1)
                .needs(Items.DRAGON_HEAD, 1)
                .needs(Items.ELYTRA, 1)
                .needs(ModItems.DARK_SOURCE_CHESTPLATE.get(), 1)
                .save(output);

        // B / B+
        altar(AltarRecipeTier.B, ModItems.DARK_SOURCE_SWORD.get())
                .needs(Items.WITHER_SKELETON_SKULL, 2)
                .needs(Items.NETHERITE_INGOT, 4)
                .needs(Items.NETHER_STAR, 2)
                .needs(ModItems.SPIRIT_SOURCE_SWORD.get(), 1)
                .save(output);
        altar(AltarRecipeTier.B_PLUS, ModItems.DARK_SOURCE_CHESTPLATE.get())
                .needs(Items.WITHER_SKELETON_SKULL, 2)
                .needs(Items.NETHERITE_INGOT, 4)
                .needs(Items.NETHER_STAR, 2)
                .needs(ModItems.SPIRIT_SOURCE_CHESTPLATE.get(), 1)
                .save(output);

        // C / C+
        altar(AltarRecipeTier.C, ModItems.SPIRIT_SOURCE_SWORD.get())
                .needs(ModItems.MYSTERY_INGOT.get(), 4)
                .needs(Items.NETHERITE_INGOT, 4)
                .needs(ModItems.MYSTERY_SWORD.get(), 1)
                .save(output);
        altar(AltarRecipeTier.C_PLUS, ModItems.SPIRIT_SOURCE_CHESTPLATE.get())
                .needs(ModItems.MYSTERY_INGOT.get(), 4)
                .needs(Items.NETHERITE_INGOT, 4)
                .needs(ModItems.MYSTERY_CHESTPLATE.get(), 1)
                .save(output);

        // D / D+
        altar(AltarRecipeTier.D, ModItems.MYSTERY_SWORD.get())
                .needs(Items.IRON_INGOT, 4)
                .needs(ModItems.MYSTERY_INGOT.get(), 4)
                .needs(Items.DIAMOND_SWORD, 1)
                .save(output);
        altar(AltarRecipeTier.D_PLUS, ModItems.MYSTERY_CHESTPLATE.get())
                .needs(Items.IRON_INGOT, 4)
                .needs(ModItems.MYSTERY_INGOT.get(), 4)
                .needs(Items.DIAMOND, 1)
                .save(output);
    }
}
