package com.antcolonies.colony.research;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.antcolonies.core.registry.ModRegistries;

import net.minecraft.core.HolderLookup;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/** Looking research entries up in the loaded data. */
public final class Researches {
    /** A research entry with its id. */
    public record Entry(Identifier id, Research research) {
    }

    /** Every entry, by tier and then by id: the order lists show them in. */
    public static List<Entry> all(HolderLookup.@Nullable Provider registries) {
        List<Entry> list = new ArrayList<>();
        ModRegistries.lookup(registries, ModRegistries.RESEARCH).ifPresent(lookup -> lookup.listElements()
                .forEach(holder -> list.add(new Entry(holder.key().identifier(), holder.value()))));
        list.sort(Comparator.<Entry, Research.Tier>comparing(entry -> entry.research().tier())
                .thenComparing(entry -> entry.research().unlocksSpecies() ? 0 : 1)
                .thenComparing(entry -> entry.id().toString()));
        return list;
    }

    public static @Nullable Entry byId(HolderLookup.@Nullable Provider registries, Identifier id) {
        for (Entry entry : all(registries)) {
            if (entry.id().equals(id)) {
                return entry;
            }
        }
        return null;
    }

    /** The research that unlocks a species in the laboratory, or null: the species is not a genetically modified one. */
    public static @Nullable Entry forSpecies(HolderLookup.@Nullable Provider registries, Identifier species) {
        for (Entry entry : all(registries)) {
            if (entry.research().species().isPresent() && entry.research().species().get().equals(species)) {
                return entry;
            }
        }
        return null;
    }

    public static @Nullable Entry forMutation(HolderLookup.@Nullable Provider registries, Identifier mutation) {
        for (Entry entry : all(registries)) {
            if (entry.research().mutation().isPresent() && entry.research().mutation().get().equals(mutation)) {
                return entry;
            }
        }
        return null;
    }

    /** Every entry the laboratory's scanner reads out of this item. */
    public static List<Entry> scannedFrom(HolderLookup.@Nullable Provider registries, ItemStack stack) {
        List<Entry> list = new ArrayList<>();
        if (!stack.isEmpty()) {
            for (Entry entry : all(registries)) {
                if (entry.research().material().test(stack)) {
                    list.add(entry);
                }
            }
        }
        return list;
    }

    /** The research an entry still waits for, given what its owner knows. */
    public static List<Identifier> missing(Research research, Knowledge knowledge) {
        return research.requires().stream().filter(id -> !knowledge.knowsResearch(id)).toList();
    }

    private Researches() {}
}
