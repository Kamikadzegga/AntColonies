package com.antcolonies.colony.evolution;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.resources.Identifier;
import net.minecraft.world.item.crafting.Ingredient;

/**
 * A Queen evolution, loaded from {@code data/<namespace>/antcolonies/evolution/<name>.json}: a Queen of {@code from}
 * fed {@code item} absorbs one per attempt, and each attempt evolves her into {@code to} with {@code chance} (raised by
 * Mutation Probability and Evolution Upgrades). After {@code guaranteed_after} failed attempts the next one always
 * succeeds, so bad luck never blocks progress.
 *
 * @param minStage      the colony stage needed (1-4)
 * @param minPopulation the adults needed
 */
public record Evolution(Identifier from, Identifier to, Ingredient item, double chance, int guaranteedAfter, int minStage, long minPopulation) {
    public static final Codec<Evolution> CODEC = RecordCodecBuilder.create(i -> i.group(
            Identifier.CODEC.fieldOf("from").forGetter(Evolution::from),
            Identifier.CODEC.fieldOf("to").forGetter(Evolution::to),
            Ingredient.CODEC.fieldOf("item").forGetter(Evolution::item),
            Codec.doubleRange(0, 1).fieldOf("chance").forGetter(Evolution::chance),
            Codec.intRange(1, 100000).optionalFieldOf("guaranteed_after", 32).forGetter(Evolution::guaranteedAfter),
            Codec.intRange(1, 4).optionalFieldOf("min_stage", 1).forGetter(Evolution::minStage),
            Codec.LONG.optionalFieldOf("min_population", 0L).forGetter(Evolution::minPopulation)
    ).apply(i, Evolution::new));
}
