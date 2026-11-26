package com.antcolonies.colony.research;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

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

    /** One research registry's entries, sorted, and by the species each unlocks; {@code size} is the registry's when it was read. */
    private record Sorted(int size, List<Entry> entries, Map<Identifier, Entry> bySpecies) {
        static final Sorted NONE = new Sorted(0, List.of(), Collections.emptyMap());
    }

    /** Per registry (each world, and each side of it, has its own); used from the client and server threads. */
    private static final Map<HolderLookup.RegistryLookup<Research>, Sorted> SORTED = Collections.synchronizedMap(new WeakHashMap<>());

    private static Sorted sorted(HolderLookup.@Nullable Provider registries) {
        HolderLookup.RegistryLookup<Research> lookup = ModRegistries.lookup(registries, ModRegistries.RESEARCH).orElse(null);
        if (lookup == null) {
            return Sorted.NONE;
        }
        int size = (int) lookup.listElements().count();
        Sorted sorted = SORTED.get(lookup);
        if (sorted == null || sorted.size() != size) {
            sorted = sort(lookup, size);
            SORTED.put(lookup, sorted);
        }
        return sorted;
    }

    private static Sorted sort(HolderLookup.RegistryLookup<Research> lookup, int size) {
        List<Entry> list = new ArrayList<>();
        lookup.listElements().forEach(holder -> list.add(new Entry(holder.key().identifier(), holder.value())));
        list.sort(Comparator.<Entry, Research.Tier>comparing(entry -> entry.research().tier())
                .thenComparing(entry -> entry.research().unlocksSpecies() ? 0 : 1)
                .thenComparing(entry -> entry.id().toString()));
        // The first entry in the lists' order wins a species, as a search down the list would find it.
        Map<Identifier, Entry> bySpecies = new HashMap<>();
        for (Entry entry : list) {
            entry.research().species().ifPresent(species -> bySpecies.putIfAbsent(species, entry));
        }
        return new Sorted(size, List.copyOf(list), Collections.unmodifiableMap(bySpecies));
    }

    /** Every entry, by tier and then by id: the order lists show them in. The list is shared: it cannot be changed. */
    public static List<Entry> all(HolderLookup.@Nullable Provider registries) {
        return sorted(registries).entries();
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
        return sorted(registries).bySpecies().get(species);
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
