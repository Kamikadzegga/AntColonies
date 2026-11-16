package com.antcolonies.compat.recipes;

import java.util.List;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jspecify.annotations.Nullable;

/** One recipe-viewer (JEI or EMI) entry: inputs (each slot a list of alternatives), outputs with their tooltips, lines of text. */
public record AntRecipe(Identifier id, List<List<ItemStack>> inputs, List<Output> outputs, List<Component> lines) {
    /**
     * @param fluid an output that is a fluid instead of items (the Droplet Centrifuge's), or null
     */
    public record Output(List<ItemStack> stacks, List<Component> tooltip,
                         net.neoforged.neoforge.fluids.@Nullable FluidStack fluid) {
        public Output(List<ItemStack> stacks, List<Component> tooltip) {
            this(stacks, tooltip, null);
        }

        public static Output of(ItemStack stack, Component... tooltip) {
            return new Output(List.of(stack), List.of(tooltip));
        }

        public static Output ofFluid(FluidStack fluid, Component... tooltip) {
            return new Output(List.of(), List.of(tooltip), fluid);
        }
    }

    public static List<ItemStack> one(ItemStack stack) {
        return List.of(stack);
    }
}
