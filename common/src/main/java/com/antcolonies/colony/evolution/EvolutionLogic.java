package com.antcolonies.colony.evolution;

import java.util.Optional;

import com.antcolonies.colony.model.Colony;
import com.antcolonies.core.config.AntConfig;
import com.antcolonies.colony.genetics.QueenData;
import com.antcolonies.colony.genetics.Trait;
import com.antcolonies.core.registry.ModRegistries;

import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/** Feeding a Queen evolution materials: one item is absorbed per attempt, and an attempt may evolve her. */
public final class EvolutionLogic {
    public enum Outcome {
        /** The item isn't an evolution material for this Queen. */
        NONE,
        /** The Terrarium is in the wrong dimension for this evolution; nothing was used. */
        WRONG_DIMENSION,
        /** The colony isn't ready (stage or population); nothing was used. */
        NOT_READY,
        /** Absorbed, no evolution this time. */
        ABSORBED,
        EVOLVED
    }

    public record Result(Outcome outcome, @Nullable Evolution evolution) {}

    /** The evolution this item starts for a Queen of the colony's species, if any. */
    public static @Nullable Evolution find(RegistryAccess registries, Colony colony, ItemStack stack) {
        if (stack.isEmpty()) {
            return null;
        }
        Optional<? extends net.minecraft.core.HolderLookup.RegistryLookup<Evolution>> lookup = registries.lookup(ModRegistries.EVOLUTION);
        return lookup.flatMap(l -> l.listElements().map(Holder::value)
                .filter(evolution -> evolution.from().equals(colony.species()) && evolution.item().test(stack)
                        && ModRegistries.species(registries, evolution.to()) != null)
                .findFirst()).orElse(null);
    }

    /** The chance of one attempt, as the GUI and JEI show it. */
    public static double chance(Evolution evolution, Colony colony, int evolutionUpgradeTiers, boolean catalyst) {
        QueenData queen = colony.queen();
        double genetics = queen == null ? 1 : queen.genome().multiplier(Trait.MUTATION_PROBABILITY);
        double chance = evolution.chance() * genetics * AntConfig.get(AntConfig.EVOLUTION_CHANCE_MULTIPLIER) * (1 + 0.25 * evolutionUpgradeTiers);
        return Math.min(1, catalyst ? chance * 2 : chance);
    }

    /** One attempt with one item of {@code stack} (the caller removes the item when the result says it was used). */
    public static Result attempt(RegistryAccess registries, Colony colony, ItemStack stack, int evolutionUpgradeTiers,
                                 net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension, RandomSource random) {
        QueenData queen = colony.queen();
        if (queen == null) {
            return new Result(Outcome.NONE, null);
        }
        Evolution evolution = find(registries, colony, stack);
        if (evolution == null) {
            return new Result(Outcome.NONE, null);
        }
        if (!evolution.allowedIn(dimension)) {
            return new Result(Outcome.WRONG_DIMENSION, evolution);
        }
        if (colony.stage() < evolution.minStage() || colony.adults() < evolution.minPopulation()) {
            return new Result(Outcome.NOT_READY, evolution);
        }
        boolean catalyst = colony.useCatalyst();
        double chance = chance(evolution, colony, evolutionUpgradeTiers, catalyst);
        boolean guaranteed = colony.evolutionAttempts() + 1 >= evolution.guaranteedAfter();
        if (guaranteed || random.nextDouble() < chance) {
            colony.evolve(queen.evolvedInto(evolution.to()));
            return new Result(Outcome.EVOLVED, evolution);
        }
        colony.countEvolutionAttempt();
        return new Result(Outcome.ABSORBED, evolution);
    }

    private EvolutionLogic() {}
}
