package com.antcolonies.colony.species;

import java.util.Map;

import com.antcolonies.AntColonies;
import com.mojang.serialization.Codec;

import net.minecraft.resources.Identifier;

/** Species the mod no longer has: a Queen, colony or tube of one from an older world loads as the species that replaced it. */
public final class RetiredSpecies {
    /** The two hybrids that found nothing their parents did not: each becomes the parent of its own climate. */
    private static final Map<Identifier, Identifier> REPLACED = Map.of(
            AntColonies.id("crystal_wood"), AntColonies.id("wood"),
            AntColonies.id("frost_emerald"), AntColonies.id("snow"));

    /** A species id read from a save: retired ids come out as their replacement. */
    public static final Codec<Identifier> CODEC = Identifier.CODEC.xmap(RetiredSpecies::current, id -> id);

    public static Identifier current(Identifier species) {
        return REPLACED.getOrDefault(species, species);
    }

    private RetiredSpecies() {}
}
