package com.sourceofmystery.recipe;

import com.sourceofmystery.SourceOfMystery;
import com.sourceofmystery.recipe.altar.AltarRecipe;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModRecipes {
    public static final DeferredRegister<RecipeType<?>> TYPES =
            DeferredRegister.create(ForgeRegistries.RECIPE_TYPES, SourceOfMystery.MOD_ID);
    public static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS =
            DeferredRegister.create(ForgeRegistries.RECIPE_SERIALIZERS, SourceOfMystery.MOD_ID);

    public static final RegistryObject<RecipeType<AltarRecipe>> ALTAR_TYPE = TYPES.register("altar",
            () -> RecipeType.simple(new ResourceLocation(SourceOfMystery.MOD_ID, "altar")));
    public static final RegistryObject<RecipeSerializer<AltarRecipe>> ALTAR_SERIALIZER = SERIALIZERS.register("altar",
            AltarRecipe.Serializer::new);

    private ModRecipes() {
    }
}
