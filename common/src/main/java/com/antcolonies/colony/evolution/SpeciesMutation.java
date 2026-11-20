package com.antcolonies.colony.evolution;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import com.antcolonies.colony.genetics.QueenData;
import com.antcolonies.colony.research.Knowledge;
import com.antcolonies.colony.research.Researches;
import com.antcolonies.colony.species.Cost;
import com.antcolonies.core.config.AntConfig;
import com.antcolonies.core.registry.ModRegistries;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * Species mutations: an evolution whose target species has a research entry is made in the Genetic Laboratory, on a
 * Queen in her Test Tube, once its material has been researched. Evolutions without research stay with the
 * Formicarium's feed slot.
 */
public final class SpeciesMutation {
    /** One mutation the laboratory can make: the evolution it follows and the research that unlocks it. */
    public record Option(Identifier id, Evolution evolution, Researches.Entry research) {
        /** The evolution's material, then the research's catalysts. */
        public List<Cost> costs(double multiplier) {
            List<Cost> costs = new ArrayList<>();
            costs.add(new Cost(evolution.item(), Math.max(1, (int) Math.ceil(evolution.labAmount() * multiplier))));
            for (Cost catalyst : research.research().catalysts()) {
                costs.add(new Cost(catalyst.ingredient(), catalyst.scaled(multiplier)));
            }
            return costs;
        }

        public int energy(double multiplier) {
            return (int) Math.ceil(research.research().energyCost() * multiplier);
        }
    }

    /** What stands between a Queen and a mutation, apart from materials and power. */
    public enum Missing {
        /** The mutation's material has not been scanned by the player or their team. */
        RESEARCH,
        /** The Queen has not grown with a colony of the stage the evolution asks for. */
        QUEEN_GROWTH,
        /** The laboratory stands in the wrong dimension. */
        DIMENSION
    }

    /** Whether an evolution is left to the laboratory: the Formicarium's feed slot no longer makes it. */
    public static boolean laboratoryOnly(HolderLookup.@Nullable Provider registries, Evolution evolution) {
        return !AntConfig.get(AntConfig.FEEDING_MAKES_LAB_SPECIES) && Researches.forSpecies(registries, evolution.to()) != null;
    }

    /** Whether some laboratory mutation takes this stack: an evolution's material or a research's catalyst. */
    public static boolean isMaterial(HolderLookup.@Nullable Provider registries, ItemStack stack) {
        var evolutions = ModRegistries.lookup(registries, ModRegistries.EVOLUTION);
        if (evolutions.isEmpty()) {
            return true;
        }
        List<Researches.Entry> researches = Researches.all(registries);
        if (researches.stream().anyMatch(entry -> entry.research().catalysts().stream().anyMatch(cost -> cost.test(stack)))) {
            return true;
        }
        return evolutions.get().listElements().map(Holder::value).anyMatch(evolution -> evolution.item().test(stack)
                && researches.stream().anyMatch(entry -> entry.research().species().filter(evolution.to()::equals).isPresent()));
    }

    /** Every laboratory mutation of a Queen of {@code from}, by id. */
    public static List<Option> options(HolderLookup.@Nullable Provider registries, Identifier from) {
        List<Option> options = new ArrayList<>();
        ModRegistries.lookup(registries, ModRegistries.EVOLUTION).ifPresent(lookup -> lookup.listElements().forEach(holder -> {
            Evolution evolution = holder.value();
            if (!evolution.from().equals(from) || ModRegistries.species(registries, evolution.to()) == null) {
                return;
            }
            Researches.Entry research = Researches.forSpecies(registries, evolution.to());
            if (research != null) {
                options.add(new Option(holder.key().identifier(), evolution, research));
            }
        }));
        options.sort(Comparator.comparing(option -> option.id().toString()));
        return options;
    }

    /** The evolutions of {@code from} that still happen by feeding the Formicarium. */
    public static List<Evolution> natural(HolderLookup.@Nullable Provider registries, Identifier from) {
        List<Evolution> list = new ArrayList<>();
        ModRegistries.lookup(registries, ModRegistries.EVOLUTION).ifPresent(lookup -> lookup.listElements().forEach(holder -> {
            Evolution evolution = holder.value();
            if (evolution.from().equals(from) && ModRegistries.species(registries, evolution.to()) != null
                    && Researches.forSpecies(registries, evolution.to()) == null) {
                list.add(evolution);
            }
        }));
        return list;
    }

    /** Whether she has grown enough. Queens saved before Queens grew (grown 0) pass: their colony's stage is unknown. */
    public static boolean grownEnough(QueenData queen, Evolution evolution) {
        return queen.grown() == 0 || queen.grown() >= evolution.minStage();
    }

    public static Set<Missing> missing(Option option, Knowledge knowledge, QueenData queen, ResourceKey<Level> dimension) {
        Set<Missing> missing = EnumSet.noneOf(Missing.class);
        if (!knowledge.knowsResearch(option.research().id())) {
            missing.add(Missing.RESEARCH);
        }
        if (!grownEnough(queen, option.evolution())) {
            missing.add(Missing.QUEEN_GROWTH);
        }
        if (!option.evolution().allowedIn(dimension)) {
            missing.add(Missing.DIMENSION);
        }
        return missing;
    }

    private SpeciesMutation() {}
}
