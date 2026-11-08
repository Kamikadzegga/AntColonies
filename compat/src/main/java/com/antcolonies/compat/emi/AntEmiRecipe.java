package com.antcolonies.compat.emi;

import java.util.ArrayList;
import java.util.List;

import com.antcolonies.AntColonies;
import com.antcolonies.compat.recipes.AntRecipe;

import dev.emi.emi.api.recipe.BasicEmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.render.EmiTexture;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.SlotWidget;
import dev.emi.emi.api.widget.WidgetHolder;
import net.minecraft.client.Minecraft;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.world.item.ItemStack;

/**
 * One entry, laid out like the JEI categories: inputs on the left (three columns), an arrow, outputs on the right
 * (four columns, each with its weight and stage in the tooltip), and the entry's lines of text underneath.
 */
class AntEmiRecipe extends BasicEmiRecipe {
    static final int WIDTH = 172;
    private static final int INPUT_COLUMNS = 3;
    private static final int OUTPUT_COLUMNS = 4;
    /** Slots are 18 px with a 2 px gap between them. */
    private static final int STEP = 20;
    private static final int ARROW_X = 64;
    private static final int OUTPUT_X = 92;
    private static final int TEXT_COLOR = 0xFF404040;

    private final AntRecipe recipe;
    private final int rows;
    private final int textLines;

    AntEmiRecipe(EmiRecipeCategory category, AntRecipe recipe, int rows, int textLines) {
        // A leading "/" marks an id that is not a data pack recipe, as EMI asks.
        super(category, AntColonies.id("/" + recipe.id().getPath()), WIDTH, rows * STEP + 4 + textLines * 10);
        this.recipe = recipe;
        this.rows = rows;
        this.textLines = textLines;
        for (List<ItemStack> slot : recipe.inputs()) {
            inputs.add(ingredient(slot));
        }
        for (AntRecipe.Output output : recipe.outputs()) {
            for (ItemStack stack : output.stacks()) {
                outputs.add(EmiStack.of(stack));
            }
        }
    }

    private static EmiIngredient ingredient(List<ItemStack> alternatives) {
        return EmiIngredient.of(alternatives.stream().map(EmiStack::of).toList());
    }

    @Override
    public void addWidgets(WidgetHolder widgets) {
        for (int i = 0; i < recipe.inputs().size(); i++) {
            widgets.addSlot(inputs.get(i), i % INPUT_COLUMNS * STEP, i / INPUT_COLUMNS * STEP);
        }
        widgets.addTexture(EmiTexture.EMPTY_ARROW, ARROW_X, 1);
        for (int i = 0; i < recipe.outputs().size(); i++) {
            AntRecipe.Output output = recipe.outputs().get(i);
            SlotWidget slot = widgets.addSlot(ingredient(output.stacks()), OUTPUT_X + i % OUTPUT_COLUMNS * STEP, i / OUTPUT_COLUMNS * STEP)
                    .recipeContext(this);
            for (Component line : output.tooltip()) {
                slot.appendTooltip(line);
            }
        }
        // Text starts under the category's slot rows, so entries line up. Long lines wrap; what still doesn't fit in
        // the category's text budget is cut, with the whole text in the last line's tooltip.
        int y = rows * STEP + 4;
        var font = Minecraft.getInstance().font;
        List<FormattedText> wrapped = new ArrayList<>();
        for (Component line : recipe.lines()) {
            wrapped.addAll(font.getSplitter().splitLines(line, WIDTH, Style.EMPTY));
        }
        for (int i = 0; i < Math.min(textLines, wrapped.size()); i++) {
            widgets.addText(Language.getInstance().getVisualOrder(wrapped.get(i)), 0, y + i * 10, TEXT_COLOR, false);
        }
        if (wrapped.size() > textLines) {
            widgets.addTooltipText(recipe.lines(), 0, y + (textLines - 1) * 10, WIDTH, 10);
        }
    }
}
