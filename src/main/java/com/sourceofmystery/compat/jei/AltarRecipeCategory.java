package com.sourceofmystery.compat.jei;

import com.sourceofmystery.item.ModItems;
import com.sourceofmystery.recipe.altar.AltarRecipe;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.Arrays;
import java.util.List;

/**
 * JEI 中的神秘祭坛配方页面：左侧最多 6 种材料（3×2），右侧产物，底部显示配方等级
 */
public class AltarRecipeCategory implements IRecipeCategory<AltarRecipe> {

    private static final int WIDTH = 120;
    private static final int HEIGHT = 50;
    private static final int COLUMNS = 3;
    private static final int SLOT_SIZE = 18;
    private static final int OUTPUT_X = 94;
    private static final int OUTPUT_Y = 9;

    private final IDrawable background;
    private final IDrawable icon;
    private final IDrawable slot;

    public AltarRecipeCategory(IGuiHelper guiHelper) {
        this.background = guiHelper.createBlankDrawable(WIDTH, HEIGHT);
        this.icon = guiHelper.createDrawableItemStack(new ItemStack(ModItems.MYSTERY_ALTAR.get()));
        this.slot = guiHelper.getSlotDrawable();
    }

    @Override
    public RecipeType<AltarRecipe> getRecipeType() {
        return SourceOfMysteryJeiPlugin.ALTAR;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("jei.sourceofmystery.altar");
    }

    @Override
    public IDrawable getBackground() {
        return background;
    }

    @Override
    public IDrawable getIcon() {
        return icon;
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, AltarRecipe recipe, IFocusGroup focuses) {
        List<AltarRecipe.Requirement> requirements = recipe.getRequirements();
        for (int i = 0; i < requirements.size(); i++) {
            AltarRecipe.Requirement requirement = requirements.get(i);
            List<ItemStack> stacks = Arrays.stream(requirement.ingredient().getItems())
                    .map(stack -> stack.copyWithCount(requirement.count()))
                    .toList();
            builder.addSlot(RecipeIngredientRole.INPUT, slotX(i) + 1, slotY(i) + 1).addItemStacks(stacks);
        }
        builder.addSlot(RecipeIngredientRole.OUTPUT, OUTPUT_X + 1, OUTPUT_Y + 1).addItemStack(recipe.getResult());
    }

    @Override
    public void draw(AltarRecipe recipe, IRecipeSlotsView recipeSlotsView, GuiGraphics guiGraphics, double mouseX, double mouseY) {
        for (int i = 0; i < recipe.getRequirements().size(); i++) {
            slot.draw(guiGraphics, slotX(i), slotY(i));
        }
        slot.draw(guiGraphics, OUTPUT_X, OUTPUT_Y);

        var font = Minecraft.getInstance().font;
        guiGraphics.drawString(font, "→", 68, OUTPUT_Y + 5, 0xFF404040, false);
        guiGraphics.drawString(font,
                Component.translatable("jei.sourceofmystery.altar.tier", recipe.getTier().getDisplayName()),
                0, HEIGHT - font.lineHeight, 0xFF404040, false);
    }

    private static int slotX(int index) {
        return (index % COLUMNS) * SLOT_SIZE;
    }

    private static int slotY(int index) {
        return (index / COLUMNS) * SLOT_SIZE;
    }
}
