package com.sourceofmystery.datagen;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.sourceofmystery.SourceOfMystery;
import com.sourceofmystery.recipe.ModRecipes;
import com.sourceofmystery.recipe.altar.AltarRecipe;
import com.sourceofmystery.recipe.altar.AltarRecipeTier;
import net.minecraft.data.recipes.FinishedRecipe;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.ItemLike;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * 生成神秘祭坛配方 JSON，输出到 data/sourceofmystery/recipes/altar/&lt;产物名&gt;.json
 */
public class AltarRecipeBuilder {

    private final AltarRecipeTier tier;
    private final Item result;
    private final List<AltarRecipe.Requirement> requirements = new ArrayList<>();

    private AltarRecipeBuilder(AltarRecipeTier tier, ItemLike result) {
        this.tier = tier;
        this.result = result.asItem();
    }

    public static AltarRecipeBuilder altar(AltarRecipeTier tier, ItemLike result) {
        return new AltarRecipeBuilder(tier, result);
    }

    public AltarRecipeBuilder needs(ItemLike item, int count) {
        requirements.add(new AltarRecipe.Requirement(Ingredient.of(item), count));
        return this;
    }

    public void save(Consumer<FinishedRecipe> output) {
        ResourceLocation id = new ResourceLocation(SourceOfMystery.MOD_ID,
                "altar/" + ForgeRegistries.ITEMS.getKey(result).getPath());
        output.accept(new Result(id, tier, List.copyOf(requirements), result));
    }

    private record Result(ResourceLocation id, AltarRecipeTier tier, List<AltarRecipe.Requirement> requirements,
                          Item result) implements FinishedRecipe {

        @Override
        public void serializeRecipeData(JsonObject json) {
            json.addProperty("tier", tier.getDisplayName());
            JsonArray ingredients = new JsonArray();
            for (AltarRecipe.Requirement requirement : requirements) {
                JsonObject entry = new JsonObject();
                entry.add("ingredient", requirement.ingredient().toJson());
                entry.addProperty("count", requirement.count());
                ingredients.add(entry);
            }
            json.add("ingredients", ingredients);
            JsonObject resultJson = new JsonObject();
            resultJson.addProperty("item", ForgeRegistries.ITEMS.getKey(result).toString());
            json.add("result", resultJson);
        }

        @Override
        public ResourceLocation getId() {
            return id;
        }

        @Override
        public RecipeSerializer<?> getType() {
            return ModRecipes.ALTAR_SERIALIZER.get();
        }

        @Nullable
        @Override
        public JsonObject serializeAdvancement() {
            return null;
        }

        @Nullable
        @Override
        public ResourceLocation getAdvancementId() {
            return null;
        }
    }
}
