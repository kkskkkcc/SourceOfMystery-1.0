package com.sourceofmystery.compat.jei;

import com.sourceofmystery.SourceOfMystery;
import com.sourceofmystery.item.ModItems;
import com.sourceofmystery.recipe.ModRecipes;
import com.sourceofmystery.recipe.altar.AltarRecipe;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.Comparator;
import java.util.List;

/**
 * JEI 兼容：在 JEI 中显示神秘祭坛配方（JEI 为可选依赖，未安装时本类不会被加载）
 */
@JeiPlugin
public class SourceOfMysteryJeiPlugin implements IModPlugin {

    public static final RecipeType<AltarRecipe> ALTAR = RecipeType.create(SourceOfMystery.MOD_ID, "altar", AltarRecipe.class);

    @Override
    public ResourceLocation getPluginUid() {
        return new ResourceLocation(SourceOfMystery.MOD_ID, "jei_plugin");
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        registration.addRecipeCategories(new AltarRecipeCategory(registration.getJeiHelpers().getGuiHelper()));
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        if (Minecraft.getInstance().level == null) {
            return;
        }
        List<AltarRecipe> recipes = Minecraft.getInstance().level.getRecipeManager()
                .getAllRecipesFor(ModRecipes.ALTAR_TYPE.get()).stream()
                .sorted(Comparator.comparingInt((AltarRecipe r) -> r.getTier().getPriority()).reversed())
                .toList();
        registration.addRecipes(ALTAR, recipes);
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        registration.addRecipeCatalyst(new ItemStack(ModItems.MYSTERY_ALTAR.get()), ALTAR);
    }
}
