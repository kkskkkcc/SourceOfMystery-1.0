package com.sourceofmystery.recipe.altar;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.sourceofmystery.block.ModBlocks;
import com.sourceofmystery.recipe.ModRecipes;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * 神秘祭坛配方：从 data/&lt;namespace&gt;/recipes/ 下的 JSON 加载，可以用数据包增删改。
 * <pre>
 * {
 *   "type": "sourceofmystery:altar",
 *   "tier": "S+",
 *   "ingredients": [
 *     { "ingredient": { "item": "minecraft:nether_star" }, "count": 2 },
 *     { "ingredient": { "tag": "forge:ingots/iron" }, "count": 4 }
 *   ],
 *   "result": { "item": "sourceofmystery:mystery_sword" }
 * }
 * </pre>
 */
public class AltarRecipe implements Recipe<Container> {

    /**
     * 一种材料及其数量
     */
    public record Requirement(Ingredient ingredient, int count) {
    }

    private final ResourceLocation id;
    private final AltarRecipeTier tier;
    private final List<Requirement> requirements;
    private final ItemStack result;

    public AltarRecipe(ResourceLocation id, AltarRecipeTier tier, List<Requirement> requirements, ItemStack result) {
        this.id = id;
        this.tier = tier;
        this.requirements = List.copyOf(requirements);
        this.result = result;
    }

    public AltarRecipeTier getTier() {
        return tier;
    }

    public List<Requirement> getRequirements() {
        return requirements;
    }

    public ItemStack getResult() {
        return result;
    }

    /**
     * 计算从每个物品堆中各取多少个来满足配方。
     *
     * @param stacks 祭坛周围可用的物品堆
     * @return 与 stacks 一一对应的取用数量；材料不足时返回 null
     */
    @Nullable
    public int[] allocate(List<ItemStack> stacks) {
        int[] remaining = new int[stacks.size()];
        for (int i = 0; i < stacks.size(); i++) {
            remaining[i] = stacks.get(i).getCount();
        }
        int[] taken = new int[stacks.size()];
        for (Requirement requirement : requirements) {
            int needed = requirement.count();
            for (int i = 0; i < stacks.size() && needed > 0; i++) {
                if (remaining[i] > 0 && requirement.ingredient().test(stacks.get(i))) {
                    int take = Math.min(needed, remaining[i]);
                    remaining[i] -= take;
                    taken[i] += take;
                    needed -= take;
                }
            }
            if (needed > 0) {
                return null;
            }
        }
        return taken;
    }

    @Override
    public boolean matches(Container container, Level level) {
        List<ItemStack> stacks = new ArrayList<>();
        for (int i = 0; i < container.getContainerSize(); i++) {
            stacks.add(container.getItem(i));
        }
        return allocate(stacks) != null;
    }

    @Override
    public ItemStack assemble(Container container, RegistryAccess registryAccess) {
        return result.copy();
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return true;
    }

    @Override
    public ItemStack getResultItem(RegistryAccess registryAccess) {
        return result;
    }

    @Override
    public NonNullList<Ingredient> getIngredients() {
        NonNullList<Ingredient> ingredients = NonNullList.create();
        requirements.forEach(requirement -> ingredients.add(requirement.ingredient()));
        return ingredients;
    }

    @Override
    public ItemStack getToastSymbol() {
        return new ItemStack(ModBlocks.MYSTERY_ALTAR.get());
    }

    /**
     * 祭坛配方不进配方书，标记为特殊配方可以避免客户端刷 "Unknown recipe category" 警告
     */
    @Override
    public boolean isSpecial() {
        return true;
    }

    @Override
    public ResourceLocation getId() {
        return id;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipes.ALTAR_SERIALIZER.get();
    }

    @Override
    public RecipeType<?> getType() {
        return ModRecipes.ALTAR_TYPE.get();
    }

    public static class Serializer implements RecipeSerializer<AltarRecipe> {

        @Override
        public AltarRecipe fromJson(ResourceLocation id, JsonObject json) {
            AltarRecipeTier tier;
            try {
                tier = AltarRecipeTier.fromName(GsonHelper.getAsString(json, "tier"));
            } catch (IllegalArgumentException e) {
                throw new JsonSyntaxException(e.getMessage());
            }

            JsonArray array = GsonHelper.getAsJsonArray(json, "ingredients");
            List<Requirement> requirements = new ArrayList<>();
            for (JsonElement element : array) {
                JsonObject entry = GsonHelper.convertToJsonObject(element, "ingredient entry");
                Ingredient ingredient = Ingredient.fromJson(entry.get("ingredient"));
                int count = GsonHelper.getAsInt(entry, "count", 1);
                if (count < 1) {
                    throw new JsonSyntaxException("Ingredient count must be at least 1 in altar recipe " + id);
                }
                requirements.add(new Requirement(ingredient, count));
            }
            if (requirements.isEmpty()) {
                throw new JsonSyntaxException("Altar recipe " + id + " has no ingredients");
            }

            ItemStack result = ShapedRecipe.itemStackFromJson(GsonHelper.getAsJsonObject(json, "result"));
            return new AltarRecipe(id, tier, requirements, result);
        }

        @Override
        public AltarRecipe fromNetwork(ResourceLocation id, FriendlyByteBuf buf) {
            AltarRecipeTier tier = buf.readEnum(AltarRecipeTier.class);
            int size = buf.readVarInt();
            List<Requirement> requirements = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                requirements.add(new Requirement(Ingredient.fromNetwork(buf), buf.readVarInt()));
            }
            return new AltarRecipe(id, tier, requirements, buf.readItem());
        }

        @Override
        public void toNetwork(FriendlyByteBuf buf, AltarRecipe recipe) {
            buf.writeEnum(recipe.tier);
            buf.writeVarInt(recipe.requirements.size());
            for (Requirement requirement : recipe.requirements) {
                requirement.ingredient().toNetwork(buf);
                buf.writeVarInt(requirement.count());
            }
            buf.writeItem(recipe.result);
        }
    }
}
