package com.antcolonies.colony.genetics;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

/**
 * A Queen's genetic profile: one level per {@link Trait} (in the enum's order), her mutations (ids in the mutation
 * registry), and her lineage. Immutable; every change makes a new genome. The Genetic Laboratory only ever raises the
 * one trait it works on, so an upgrade never harms another.
 */
public record Genome(List<Integer> levels, List<Identifier> mutations, Lineage lineage) {
    private static final Codec<List<Integer>> LEVELS_CODEC = Codec.intRange(Trait.MIN_LEVEL, Trait.ABSOLUTE_MAX_LEVEL).listOf()
            .comapFlatMap(list -> list.size() == Trait.values().length ? DataResult.success(List.copyOf(list))
                    : DataResult.error(() -> "A genome needs " + Trait.values().length + " levels, got " + list.size()), list -> list);

    public static final Codec<Genome> CODEC = RecordCodecBuilder.create(i -> i.group(
            LEVELS_CODEC.fieldOf("levels").forGetter(Genome::levels),
            Identifier.CODEC.listOf().optionalFieldOf("mutations", List.of()).forGetter(Genome::mutations),
            Lineage.CODEC.fieldOf("lineage").forGetter(Genome::lineage)
    ).apply(i, Genome::new));

    public static final StreamCodec<ByteBuf, Genome> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list(Trait.values().length)), Genome::levels,
            Identifier.STREAM_CODEC.apply(ByteBufCodecs.list(32)), Genome::mutations,
            Lineage.STREAM_CODEC, Genome::lineage,
            Genome::new);

    public Genome {
        levels = List.copyOf(levels);
        mutations = List.copyOf(mutations);
    }

    /** Every trait at level 1, for tests and unknown Queens. */
    public static Genome basic(Identifier species) {
        List<Integer> ones = new ArrayList<>();
        for (int t = 0; t < Trait.values().length; t++) {
            ones.add(Trait.MIN_LEVEL);
        }
        return new Genome(ones, List.of(), new Lineage(UUID.randomUUID(), "", Optional.empty(), 0, List.of(species)));
    }

    public int level(Trait trait) {
        return levels.get(trait.ordinal());
    }

    public double multiplier(Trait trait) {
        return Trait.multiplier(level(trait));
    }

    public Genome withLevel(Trait trait, int level) {
        List<Integer> next = new ArrayList<>(levels);
        next.set(trait.ordinal(), Mth.clamp(level, Trait.MIN_LEVEL, Trait.ABSOLUTE_MAX_LEVEL));
        return new Genome(next, mutations, lineage);
    }

    public Genome withMutation(Identifier mutation) {
        if (mutations.contains(mutation)) {
            return this;
        }
        List<Identifier> next = new ArrayList<>(mutations);
        next.add(mutation);
        return new Genome(levels, next, lineage);
    }

    public Genome withoutMutation(Identifier mutation) {
        List<Identifier> next = new ArrayList<>(mutations);
        next.remove(mutation);
        return new Genome(levels, next, lineage);
    }

    public Genome withLineage(Lineage lineage) {
        return new Genome(levels, mutations, lineage);
    }

    public double averageLevel() {
        return levels.stream().mapToInt(Integer::intValue).average().orElse(1);
    }

    /** Whether every trait is at the configured maximum. */
    public boolean perfect() {
        int max = Trait.maxLevel();
        return levels.stream().allMatch(level -> level >= max);
    }

    /** The overall rarity grade for tooltips: 0 common .. 4 legendary, 5 perfect. */
    public int rarity() {
        if (perfect()) {
            return 5;
        }
        double average = averageLevel();
        return average >= 8 ? 4 : average >= 6 ? 3 : average >= 4 ? 2 : average >= 2.5 ? 1 : 0;
    }
}
