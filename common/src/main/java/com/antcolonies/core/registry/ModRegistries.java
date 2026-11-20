package com.antcolonies.core.registry;

import java.util.Optional;

import com.antcolonies.AntColonies;
import com.antcolonies.colony.breeding.Hybrid;
import com.antcolonies.colony.evolution.Evolution;
import com.antcolonies.colony.expedition.Expedition;
import com.antcolonies.colony.genetics.Mutation;
import com.antcolonies.colony.genetics.TraitUpgrade;
import com.antcolonies.colony.research.Research;
import com.antcolonies.colony.species.AntSpecies;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.neoforged.neoforge.registries.DataPackRegistryEvent;
import org.jspecify.annotations.Nullable;

/** The mod's datapack registries, loaded from {@code data/<namespace>/antcolonies/<registry>/} and synced to clients. */
public final class ModRegistries {
    public static final ResourceKey<Registry<AntSpecies>> SPECIES = ResourceKey.createRegistryKey(AntColonies.id("species"));
    public static final ResourceKey<Registry<Evolution>> EVOLUTION = ResourceKey.createRegistryKey(AntColonies.id("evolution"));
    public static final ResourceKey<Registry<Hybrid>> HYBRID = ResourceKey.createRegistryKey(AntColonies.id("hybrid"));
    public static final ResourceKey<Registry<Mutation>> MUTATION = ResourceKey.createRegistryKey(AntColonies.id("mutation"));
    public static final ResourceKey<Registry<TraitUpgrade>> TRAIT_UPGRADE = ResourceKey.createRegistryKey(AntColonies.id("trait_upgrade"));
    public static final ResourceKey<Registry<Expedition>> EXPEDITION =
            ResourceKey.createRegistryKey(AntColonies.id("expedition"));
    public static final ResourceKey<Registry<Research>> RESEARCH = ResourceKey.createRegistryKey(AntColonies.id("research"));

    /** The species every new player starts with, and the fallback of unknown ids. */
    public static final Identifier COMMON = AntColonies.id("common");

    public static void register(DataPackRegistryEvent.NewRegistry event) {
        event.dataPackRegistry(SPECIES, AntSpecies.CODEC, AntSpecies.CODEC);
        event.dataPackRegistry(EVOLUTION, Evolution.CODEC, Evolution.CODEC);
        event.dataPackRegistry(HYBRID, Hybrid.CODEC, Hybrid.CODEC);
        event.dataPackRegistry(MUTATION, Mutation.CODEC, Mutation.CODEC);
        event.dataPackRegistry(TRAIT_UPGRADE, TraitUpgrade.CODEC, TraitUpgrade.CODEC);
        event.dataPackRegistry(EXPEDITION, Expedition.CODEC, Expedition.CODEC);
        event.dataPackRegistry(RESEARCH, Research.CODEC, Research.CODEC);
    }

    public static @Nullable AntSpecies species(HolderLookup.@Nullable Provider registries, @Nullable Identifier id) {
        if (registries == null || id == null) {
            return null;
        }
        return registries.lookup(SPECIES).flatMap(lookup -> lookup.get(ResourceKey.create(SPECIES, id))).map(Holder::value).orElse(null);
    }

    public static <T> Optional<HolderLookup.RegistryLookup<T>> lookup(HolderLookup.@Nullable Provider registries, ResourceKey<Registry<T>> key) {
        return registries == null ? Optional.empty() : registries.lookup(key).map(lookup -> lookup);
    }

    private ModRegistries() {}
}
