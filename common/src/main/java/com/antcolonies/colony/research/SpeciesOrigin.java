package com.antcolonies.colony.research;

import java.util.Locale;

import com.antcolonies.core.registry.ModRegistries;

import net.minecraft.core.HolderLookup;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/** How a species is come by: found and adapted in the world, made in the Genetic Laboratory, or crossed in a Queen Chamber. */
public enum SpeciesOrigin {
    NATURAL,
    MODIFIED,
    HYBRID;

    public static SpeciesOrigin of(HolderLookup.@Nullable Provider registries, Identifier species) {
        if (Researches.forSpecies(registries, species) != null) {
            return MODIFIED;
        }
        boolean crossed = ModRegistries.lookup(registries, ModRegistries.HYBRID)
                .map(lookup -> lookup.listElements().anyMatch(hybrid -> hybrid.value().result().equals(species))).orElse(false);
        return crossed ? HYBRID : NATURAL;
    }

    public String translationKey() {
        return "origin.antcolonies." + name().toLowerCase(Locale.ROOT);
    }
}
