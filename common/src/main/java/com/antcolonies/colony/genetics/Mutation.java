package com.antcolonies.colony.genetics;

import java.util.List;
import java.util.Optional;

import com.antcolonies.colony.species.Category;
import com.antcolonies.colony.species.Cost;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.resources.Identifier;

/**
 * A mutation, loaded from {@code data/<namespace>/antcolonies/mutation/<name>.json}. A Queen gets one at random when she
 * is reared (weighted by {@code random_weight}, more often with a high Mutation Probability), or on purpose in the
 * Genetic Laboratory's Mutation Chamber by feeding its {@code catalyst}.
 *
 * @param categories the species categories it can appear in (empty: all)
 * @param negative   a harmful mutation; the Mutation Chamber's catalysts can't give these, and the lab can remove them
 * @param rare       shown as rare in JEI; rare mutations are never rolled at random below Mutation Probability 5
 */
public record Mutation(MutationEffects effects, int randomWeight, boolean negative, boolean rare, Optional<Cost> catalyst,
                       List<Category> categories) {
    public static final Codec<Mutation> CODEC = RecordCodecBuilder.create(i -> i.group(
            MutationEffects.CODEC.fieldOf("effects").forGetter(Mutation::effects),
            Codec.intRange(0, 100000).optionalFieldOf("random_weight", 10).forGetter(Mutation::randomWeight),
            Codec.BOOL.optionalFieldOf("negative", false).forGetter(Mutation::negative),
            Codec.BOOL.optionalFieldOf("rare", false).forGetter(Mutation::rare),
            Cost.CODEC.optionalFieldOf("catalyst").forGetter(Mutation::catalyst),
            Category.CODEC.listOf().optionalFieldOf("categories", List.of()).forGetter(Mutation::categories)
    ).apply(i, Mutation::new));

    public boolean appliesTo(Category category) {
        return categories.isEmpty() || categories.contains(category);
    }

    public static String translationKey(Identifier id) {
        return "mutation." + id.getNamespace() + "." + id.getPath();
    }

    /**
     * What a mutation changes; every factor multiplies the matching rate (1 = unchanged).
     *
     * @param coldResistant ignores climates that are too cold
     * @param heatResistant ignores climates that are too hot
     */
    public record MutationEffects(double production, double gathering, double food, double eggs, double growth, double soldiers,
                                  double farming, double special, boolean coldResistant, boolean heatResistant) {
        public static final MutationEffects NONE = new MutationEffects(1, 1, 1, 1, 1, 1, 1, 1, false, false);
        public static final Codec<MutationEffects> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.DOUBLE.optionalFieldOf("production", 1.0).forGetter(MutationEffects::production),
                Codec.DOUBLE.optionalFieldOf("gathering", 1.0).forGetter(MutationEffects::gathering),
                Codec.DOUBLE.optionalFieldOf("food", 1.0).forGetter(MutationEffects::food),
                Codec.DOUBLE.optionalFieldOf("eggs", 1.0).forGetter(MutationEffects::eggs),
                Codec.DOUBLE.optionalFieldOf("growth", 1.0).forGetter(MutationEffects::growth),
                Codec.DOUBLE.optionalFieldOf("soldiers", 1.0).forGetter(MutationEffects::soldiers),
                Codec.DOUBLE.optionalFieldOf("farming", 1.0).forGetter(MutationEffects::farming),
                Codec.DOUBLE.optionalFieldOf("special", 1.0).forGetter(MutationEffects::special),
                Codec.BOOL.optionalFieldOf("cold_resistant", false).forGetter(MutationEffects::coldResistant),
                Codec.BOOL.optionalFieldOf("heat_resistant", false).forGetter(MutationEffects::heatResistant)
        ).apply(i, MutationEffects::new));

        /** Both together, as a Queen with several mutations has them. */
        public MutationEffects and(MutationEffects other) {
            return new MutationEffects(production * other.production, gathering * other.gathering, food * other.food, eggs * other.eggs,
                    growth * other.growth, soldiers * other.soldiers, farming * other.farming, special * other.special,
                    coldResistant || other.coldResistant, heatResistant || other.heatResistant);
        }
    }
}
