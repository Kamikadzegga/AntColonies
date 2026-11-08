package com.antcolonies.compat.jei;

import java.util.List;
import com.antcolonies.compat.recipes.AntRecipe;

import com.antcolonies.AntColonies;

import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.IRecipeSlotBuilder;
import mezz.jei.api.gui.widgets.IRecipeExtrasBuilder;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.category.AbstractRecipeCategory;
import mezz.jei.api.recipe.types.IRecipeType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.ItemLike;
import org.jspecify.annotations.Nullable;

/**
 * All of the mod's JEI categories share this layout: inputs on the left, an arrow, outputs on the right, and the
 * entry's lines of text underneath.
 */
public class AntCategory extends AbstractRecipeCategory<AntRecipe> {
    public static final int WIDTH = 172;
    private static final int INPUT_COLUMNS = 3;
    private static final int OUTPUT_COLUMNS = 4;
    /** Slots are 18 px with a 2 px gap between them. */
    private static final int STEP = 20;
    private static final int ARROW_X = 64;
    private static final int OUTPUT_X = 92;
    private static final int TEXT_COLOR = 0xFF404040;
    private final int textLines;
    private final int rows;

    public AntCategory(IRecipeType<AntRecipe> type, String key, ItemLike icon, IGuiHelper guiHelper, int rows, int textLines) {
        super(type, Component.translatable("jei.antcolonies." + key), guiHelper.createDrawableItemLike(icon), WIDTH, rows * STEP + 4 + textLines * 10);
        this.textLines = textLines;
        this.rows = rows;
    }

    public static IRecipeType<AntRecipe> type(String name) {
        return IRecipeType.create(AntColonies.id(name), AntRecipe.class);
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, AntRecipe recipe, IFocusGroup focuses) {
        for (int i = 0; i < recipe.inputs().size(); i++) {
            builder.addInputSlot(1 + i % INPUT_COLUMNS * STEP, 1 + i / INPUT_COLUMNS * STEP)
                    .setStandardSlotBackground()
                    .addItemStacks(recipe.inputs().get(i));
        }
        for (int i = 0; i < recipe.outputs().size(); i++) {
            AntRecipe.Output output = recipe.outputs().get(i);
            IRecipeSlotBuilder slot = builder.addOutputSlot(OUTPUT_X + 1 + i % OUTPUT_COLUMNS * STEP, 1 + i / OUTPUT_COLUMNS * STEP)
                    .setStandardSlotBackground()
                    .addItemStacks(output.stacks());
            if (!output.tooltip().isEmpty()) {
                slot.addRichTooltipCallback((view, tooltip) -> output.tooltip().forEach(tooltip::add));
            }
        }
    }

    @Override
    public void createRecipeExtras(IRecipeExtrasBuilder builder, AntRecipe recipe, IFocusGroup focuses) {
        builder.addRecipeArrow().setPosition(ARROW_X, 1);
        // Text always starts under the category's slot rows, so entries line up. Long lines wrap; what still does not fit
        // in the category's text budget is cut, with the whole text in the last line's tooltip.
        int y = rows * STEP + 4;
        var font = net.minecraft.client.Minecraft.getInstance().font;
        List<net.minecraft.network.chat.FormattedText> wrapped = new java.util.ArrayList<>();
        for (Component line : recipe.lines()) {
            wrapped.addAll(font.getSplitter().splitLines(line, WIDTH, net.minecraft.network.chat.Style.EMPTY));
        }
        for (int i = 0; i < Math.min(textLines, wrapped.size()); i++) {
            var text = builder.addText(wrapped.get(i), WIDTH, 10).setPosition(0, y + i * 10).setColor(TEXT_COLOR);
            if (i == textLines - 1 && wrapped.size() > textLines) {
                text.setTooltip(recipe.lines());
            }
        }
    }

    @Override
    public @Nullable Identifier getIdentifier(AntRecipe recipe) {
        return recipe.id();
    }
}
