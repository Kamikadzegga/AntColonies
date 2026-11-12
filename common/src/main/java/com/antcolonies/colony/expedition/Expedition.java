package com.antcolonies.colony.expedition;

import java.util.List;
import java.util.Optional;

import com.antcolonies.colony.model.StructureKind;
import com.antcolonies.colony.species.AntSpecies;
import com.antcolonies.colony.species.SpeciesOutput;
import com.antcolonies.core.registry.ModRegistries;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * A simulated expedition into another dimension, run from a Nether or End Foraging Site, loaded from
 * {@code data/<namespace>/antcolonies/expedition/<name>.json}. No ant ever leaves: the site's workers are numbers in the
 * colony's simulation, the target dimension is never loaded, and what they bring back comes from this table.
 *
 * @param site      the Foraging Site that runs it
 * @param adapted   the species (a species tag) that can go; a colony of any other species leaves the site alone
 * @param rate      finds compared to an ordinary Foraging Site's
 * @param home      the dimension the expedition goes to: an ordinary Foraging Site of a colony living there finds the
 *                  {@code trail} now and then (the material the site is crafted from)
 * @param trail     the trail pheromone found there, and its chance per find
 * @param outputs   what the expedition brings back; an output naming species is found only by those
 */
public record Expedition(StructureKind site, TagKey<AntSpecies> adapted, double rate, ResourceKey<Level> home, Optional<Trail> trail,
                         List<Find> outputs) {
    public static final Codec<Expedition> CODEC = RecordCodecBuilder.create(i -> i.group(
            StructureKind.CODEC.fieldOf("site").forGetter(Expedition::site),
            TagKey.codec(ModRegistries.SPECIES).fieldOf("adapted").forGetter(Expedition::adapted),
            Codec.doubleRange(0, 1000).optionalFieldOf("rate", 1.0).forGetter(Expedition::rate),
            Level.RESOURCE_KEY_CODEC.fieldOf("home").forGetter(Expedition::home),
            Trail.CODEC.optionalFieldOf("trail").forGetter(Expedition::trail),
            Find.CODEC.listOf().optionalFieldOf("outputs", List.of()).forGetter(Expedition::outputs)
    ).apply(i, Expedition::new));

    /** The trail pheromone a colony living in the expedition's dimension finds while foraging there. */
    public record Trail(Holder<Item> item, double chance) {
        public static final Codec<Trail> CODEC = RecordCodecBuilder.create(i -> i.group(
                BuiltInRegistries.ITEM.holderByNameCodec().fieldOf("item").forGetter(Trail::item),
                Codec.doubleRange(0, 1).fieldOf("chance").forGetter(Trail::chance)
        ).apply(i, Trail::new));
    }

    /** One find: a species output (item or tag, weight, stage, rare...), and the species that make it (empty: every adapted one). */
    public record Find(SpeciesOutput output, List<Identifier> species) {
        public static final Codec<Find> CODEC = RecordCodecBuilder.<Find>create(i -> i.group(
                SpeciesOutput.MAP_CODEC.forGetter(Find::output),
                Identifier.CODEC.listOf().optionalFieldOf("species", List.of()).forGetter(Find::species)
        ).apply(i, Find::new)).validate(find -> SpeciesOutput.validate(find.output()).map(output -> find));

        public boolean foundBy(Identifier speciesId) {
            return species.isEmpty() || species.contains(speciesId);
        }
    }

    /** The finds a colony of this species can bring back. */
    public List<SpeciesOutput> outputsFor(Identifier speciesId) {
        return outputs.stream().filter(find -> find.foundBy(speciesId)).map(Find::output).toList();
    }

    /** Whether a species can go on this expedition (it is in the {@code adapted} tag). */
    public boolean adapted(HolderLookup.@Nullable Provider registries, Identifier speciesId) {
        if (registries == null) {
            return false;
        }
        return registries.lookup(ModRegistries.SPECIES)
                .flatMap(lookup -> lookup.get(ResourceKey.create(ModRegistries.SPECIES, speciesId)))
                .map(holder -> holder.is(adapted))
                .orElse(false);
    }

    /** The species that can go, by id (for JEI, EMI and the screens). */
    public List<Identifier> adaptedSpecies(HolderLookup.@Nullable Provider registries) {
        if (registries == null) {
            return List.of();
        }
        return registries.lookup(ModRegistries.SPECIES)
                .flatMap(lookup -> lookup.get(adapted))
                .map(set -> set.stream().map(holder -> holder.unwrapKey().map(ResourceKey::identifier).orElse(null))
                        .filter(java.util.Objects::nonNull).toList())
                .orElse(List.of());
    }

    /** The expedition a site kind runs, if any is loaded. */
    public static @Nullable Expedition forSite(HolderLookup.@Nullable Provider registries, StructureKind kind) {
        if (registries == null || !kind.expedition()) {
            return null;
        }
        return registries.lookup(ModRegistries.EXPEDITION)
                .flatMap(lookup -> lookup.listElements().map(Holder::value).filter(e -> e.site() == kind).findFirst())
                .orElse(null);
    }

    /** Every loaded expedition. */
    public static List<Expedition> all(HolderLookup.@Nullable Provider registries) {
        if (registries == null) {
            return List.of();
        }
        return registries.lookup(ModRegistries.EXPEDITION).map(lookup -> lookup.listElements().map(Holder::value).toList()).orElse(List.of());
    }
}
