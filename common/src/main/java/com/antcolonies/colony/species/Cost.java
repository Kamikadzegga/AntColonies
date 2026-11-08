package com.antcolonies.colony.species;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

/** Some amount of an ingredient (a tag or items), as costs in the data files take it. */
public record Cost(Ingredient ingredient, int count) {
    public static final Codec<Cost> CODEC = RecordCodecBuilder.create(i -> i.group(
            Ingredient.CODEC.fieldOf("ingredient").forGetter(Cost::ingredient),
            Codec.intRange(1, 10000).optionalFieldOf("count", 1).forGetter(Cost::count)
    ).apply(i, Cost::new));

    public boolean test(ItemStack stack) {
        return ingredient.test(stack);
    }

    /** The count with a multiplier applied, at least one. */
    public int scaled(double multiplier) {
        return Math.max(1, (int) Math.ceil(count * multiplier));
    }
}
