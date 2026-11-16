package com.antcolonies.colony.evolution;

import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;

/**
 * A Queen evolution: a Queen of {@code from} fed {@code item} may evolve into {@code to}.
 *
 * @param guaranteedAfter the attempt that always succeeds, whatever the chance
 * @param minStage        the colony stage needed (1-4)
 * @param minPopulation   the adults needed
 * @param dimension       if set, the Terrarium must be in this dimension
 */
public record Evolution(Identifier from, Identifier to, Ingredient item, double chance, int guaranteedAfter, int minStage, long minPopulation,
                        Optional<ResourceKey<Level>> dimension) {
    public static final Codec<Evolution> CODEC = RecordCodecBuilder.create(i -> i.group(
            Identifier.CODEC.fieldOf("from").forGetter(Evolution::from),
            Identifier.CODEC.fieldOf("to").forGetter(Evolution::to),
            Ingredient.CODEC.fieldOf("item").forGetter(Evolution::item),
            Codec.doubleRange(0, 1).fieldOf("chance").forGetter(Evolution::chance),
            Codec.intRange(1, 100000).optionalFieldOf("guaranteed_after", 32).forGetter(Evolution::guaranteedAfter),
            Codec.intRange(1, 4).optionalFieldOf("min_stage", 1).forGetter(Evolution::minStage),
            Codec.LONG.optionalFieldOf("min_population", 0L).forGetter(Evolution::minPopulation),
            ResourceKey.codec(Registries.DIMENSION).optionalFieldOf("dimension").forGetter(Evolution::dimension)
    ).apply(i, Evolution::new));

    /** Whether a Terrarium in {@code level} may attempt it. */
    public boolean allowedIn(ResourceKey<Level> level) {
        return dimension.isEmpty() || dimension.get().equals(level);
    }
}
