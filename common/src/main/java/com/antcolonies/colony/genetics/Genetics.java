package com.antcolonies.colony.genetics;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.antcolonies.core.config.AntConfig;
import com.antcolonies.core.registry.ModRegistries;
import com.antcolonies.colony.species.AntSpecies;
import com.antcolonies.colony.species.Category;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import org.jspecify.annotations.Nullable;

/** Making Queens: random wild genetics, daughters that inherit from their mother, crosses, mutations and names. */
public final class Genetics {
    private static final String[] NAME_START = {"Ae", "Bel", "Cy", "Dra", "Ely", "Fae", "Gry", "Hes", "Ira", "Jun", "Ky", "Lyr", "Myr", "Ny",
            "Ori", "Pha", "Qui", "Rhe", "Sa", "Thy", "Ura", "Ves", "Xa", "Yse", "Zel"};
    private static final String[] NAME_MIDDLE = {"", "li", "ra", "the", "mi", "no", "si", "ve", "ri", "da"};
    private static final String[] NAME_END = {"a", "ia", "is", "ra", "elle", "ine", "ys", "ora", "eth", "ix"};
    private static final String[] NUMERALS = {"", " II", " III", " IV", " V", " VI", " VII", " VIII", " IX", " X"};

    public static QueenData wildQueen(Identifier species, RandomSource random) {
        List<Integer> levels = new ArrayList<>();
        for (int t = 0; t < Trait.values().length; t++) {
            levels.add(rollStartingLevel(random));
        }
        UUID id = new UUID(random.nextLong(), random.nextLong());
        Lineage lineage = new Lineage(id, name(id, 0), Optional.empty(), 0, List.of(species));
        return new QueenData(species, new Genome(levels, List.of(), lineage));
    }

    static int rollStartingLevel(RandomSource random) {
        List<? extends Integer> weights = AntConfig.get(AntConfig.STARTING_LEVEL_WEIGHTS);
        int total = 0;
        for (Integer weight : weights) {
            total += Math.max(0, weight);
        }
        if (total <= 0) {
            return Trait.MIN_LEVEL;
        }
        int pick = random.nextInt(total);
        for (int level = 0; level < weights.size(); level++) {
            pick -= Math.max(0, weights.get(level));
            if (pick < 0) {
                return Mth.clamp(level + 1, Trait.MIN_LEVEL, Trait.maxLevel());
            }
        }
        return Trait.MIN_LEVEL;
    }

    /**
     * A daughter Queen: each trait from her mother or her mate, sometimes one level off, perhaps with a new mutation.
     *
     * @param mate a Genetic Sample's genes crossed in, or null
     */
    public static Genome daughter(Genome mother, @Nullable Genome mate, Identifier species, RandomSource random, RegistryAccess registries,
                                  Category category, double mutationBonus) {
        double variation = AntConfig.get(AntConfig.INHERITANCE_VARIATION);
        List<Integer> levels = new ArrayList<>();
        for (Trait trait : Trait.values()) {
            int level = mate != null && random.nextBoolean() ? mate.level(trait) : mother.level(trait);
            if (random.nextDouble() < variation) {
                level += random.nextBoolean() ? 1 : -1;
            }
            // A stabilized trait passes on exactly as it is: the mother's first, else the sample's.
            if (mother.locked(trait)) {
                level = mother.level(trait);
            } else if (mate != null && mate.locked(trait)) {
                level = mate.level(trait);
            }
            levels.add(Mth.clamp(level, Trait.MIN_LEVEL, Trait.maxLevel()));
        }
        List<Identifier> mutations = new ArrayList<>(mother.mutations());
        if (mate != null) {
            for (Identifier inherited : mate.mutations()) {
                // A protected mutation always passes on (the coin is still tossed, so other Queens roll as before).
                if (!mutations.contains(inherited) && (random.nextBoolean() | mate.protects(inherited))
                        && (isFlaw(inherited, registries) || roomForBeneficial(mutations, registries))) {
                    mutations.add(inherited);
                }
            }
        }
        double chance = AntConfig.get(AntConfig.BASE_MUTATION_CHANCE) * mother.multiplier(Trait.MUTATION_PROBABILITY) * mutationBonus;
        if (random.nextDouble() < chance) {
            Identifier mutation = randomMutation(registries, category, mother.level(Trait.MUTATION_PROBABILITY), random);
            // A stable line throws fewer flaws: a new flaw is shed with a chance equal to the mother's stability.
            boolean shed = mutation != null && mother.stability() > 0 && isFlaw(mutation, registries)
                    && random.nextDouble() < mother.stability();
            if (mutation != null && !shed && !mutations.contains(mutation)
                    && (isFlaw(mutation, registries) || roomForBeneficial(mutations, registries))) {
                mutations.add(mutation);
            }
        }
        UUID id = new UUID(random.nextLong(), random.nextLong());
        int generation = mother.lineage().generation() + 1;
        List<Identifier> history = new ArrayList<>(mother.lineage().history());
        if (history.isEmpty() || !history.getLast().equals(species)) {
            history.add(species);
        }
        while (history.size() > 32) {
            history.removeFirst();
        }
        Lineage lineage = new Lineage(id, name(id, generation), Optional.of(mother.lineage().id()), generation, history);
        return new Genome(levels, mutations, lineage);
    }

    /** Whether a mutation is a flaw. Unknown ids (from a removed data pack) count as beneficial. */
    public static boolean isFlaw(Identifier id, HolderLookup.Provider registries) {
        return registries.lookup(ModRegistries.MUTATION).flatMap(lookup -> lookup.get(ResourceKey.create(ModRegistries.MUTATION, id)))
                .map(mutation -> mutation.value().negative()).orElse(false);
    }

    public static int beneficial(List<Identifier> mutations, HolderLookup.Provider registries) {
        int count = 0;
        for (Identifier id : mutations) {
            if (!isFlaw(id, registries)) {
                count++;
            }
        }
        return count;
    }

    public static boolean roomForBeneficial(List<Identifier> mutations, HolderLookup.Provider registries) {
        return beneficial(mutations, registries) < AntConfig.get(AntConfig.MAX_BENEFICIAL_MUTATIONS);
    }

    public static @Nullable Identifier randomMutation(RegistryAccess registries, Category category, int mutationLevel, RandomSource random) {
        Optional<Registry<Mutation>> registry = registries.lookup(ModRegistries.MUTATION);
        if (registry.isEmpty()) {
            return null;
        }
        List<Map.Entry<Identifier, Mutation>> candidates = new ArrayList<>();
        int total = 0;
        for (var entry : registry.get().entrySet()) {
            Mutation mutation = entry.getValue();
            if (mutation.randomWeight() > 0 && mutation.appliesTo(category) && (!mutation.rare() || mutationLevel >= 5)) {
                candidates.add(Map.entry(entry.getKey().identifier(), mutation));
                total += mutation.randomWeight();
            }
        }
        if (total == 0) {
            return null;
        }
        int pick = random.nextInt(total);
        for (Map.Entry<Identifier, Mutation> candidate : candidates) {
            pick -= candidate.getValue().randomWeight();
            if (pick < 0) {
                return candidate.getKey();
            }
        }
        return null;
    }

    /** Everything a Queen's mutations change, multiplied together. Unknown mutations (a removed datapack) do nothing. */
    public static Mutation.MutationEffects effects(Genome genome, HolderLookup.Provider registries) {
        Mutation.MutationEffects effects = Mutation.MutationEffects.NONE;
        Optional<? extends HolderLookup.RegistryLookup<Mutation>> lookup = registries.lookup(ModRegistries.MUTATION);
        if (lookup.isEmpty()) {
            return effects;
        }
        for (Identifier id : genome.mutations()) {
            Optional<Holder.Reference<Mutation>> mutation = lookup.get().get(ResourceKey.create(ModRegistries.MUTATION, id));
            if (mutation.isPresent()) {
                effects = effects.and(mutation.get().value().effects());
            }
        }
        return effects;
    }

    /** A Queen's name, made from her id so it never changes: "Myrrais III" for a third-generation Queen. */
    public static String name(UUID id, int generation) {
        long bits = id.getMostSignificantBits() ^ id.getLeastSignificantBits();
        RandomSource random = RandomSource.create(bits);
        String name = NAME_START[random.nextInt(NAME_START.length)] + NAME_MIDDLE[random.nextInt(NAME_MIDDLE.length)]
                + NAME_END[random.nextInt(NAME_END.length)];
        return name + NUMERALS[Math.min(generation, NUMERALS.length - 1)];
    }

    public static boolean canFarm(@Nullable AntSpecies species) {
        return species != null && species.canFarm();
    }

    private Genetics() {}
}
