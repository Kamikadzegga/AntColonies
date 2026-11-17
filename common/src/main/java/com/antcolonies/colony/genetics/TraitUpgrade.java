package com.antcolonies.colony.genetics;

import java.util.List;

import com.antcolonies.colony.species.Cost;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** What the Genetic Laboratory needs to raise one trait: one step per level, a missing step falling back to the last given. */
public record TraitUpgrade(Trait trait, List<Step> steps) {
    public static final Codec<TraitUpgrade> CODEC = RecordCodecBuilder.create(i -> i.group(
            Trait.CODEC.fieldOf("trait").forGetter(TraitUpgrade::trait),
            Step.CODEC.listOf().fieldOf("steps").forGetter(TraitUpgrade::steps)
    ).apply(i, TraitUpgrade::new));

    public Step step(int fromLevel) {
        if (steps.isEmpty()) {
            return Step.EMPTY;
        }
        return steps.get(Math.clamp(fromLevel - Trait.MIN_LEVEL, 0, steps.size() - 1));
    }

    /**
     * @param energy FE used
     * @param time   ticks of processing
     */
    public record Step(List<Cost> cost, int energy, int time) {
        public static final Step EMPTY = new Step(List.of(), 0, 200);
        public static final Codec<Step> CODEC = RecordCodecBuilder.create(i -> i.group(
                Cost.CODEC.listOf().fieldOf("cost").forGetter(Step::cost),
                Codec.intRange(0, 1_000_000).optionalFieldOf("energy", 0).forGetter(Step::energy),
                Codec.intRange(1, 1_000_000).optionalFieldOf("time", 200).forGetter(Step::time)
        ).apply(i, Step::new));
    }
}
