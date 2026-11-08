package com.antcolonies.compat.recipes;

import java.util.List;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/**
 * One recipe-viewer (JEI or EMI) entry of any of the mod's categories: what goes in (each slot a list of alternatives), what comes out (with
 * a tooltip per output: weight, stage, chance), and lines of text (rates, chances, requirements).
 */
public record AntRecipe(Identifier id, List<List<ItemStack>> inputs, List<Output> outputs, List<Component> lines) {
    public record Output(List<ItemStack> stacks, List<Component> tooltip) {
        public static Output of(ItemStack stack, Component... tooltip) {
            return new Output(List.of(stack), List.of(tooltip));
        }
    }

    public static List<ItemStack> one(ItemStack stack) {
        return List.of(stack);
    }
}
