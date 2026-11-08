package com.antcolonies.colony.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.core.BlockPos;

/**
 * What a Terrarium's network reached in its last scan: its structures by kind (in a fixed order, so allocation never
 * depends on the scan order), its tubes and tunnels, other colonies joined through Colony Connectors, and whether it
 * touches another Terrarium (two colonies may never share one network).
 */
public record ColonyLayout(Map<StructureKind, List<BlockPos>> structures, Set<BlockPos> conduits, List<BlockPos> linkedTerrariums,
                           boolean twoColonies, boolean truncated) {
    public static final ColonyLayout EMPTY = new ColonyLayout(new EnumMap<>(StructureKind.class), Set.of(), List.of(), false, false);

    public List<BlockPos> of(StructureKind kind) {
        return structures.getOrDefault(kind, Collections.emptyList());
    }

    public int count(StructureKind kind) {
        return of(kind).size();
    }

    public boolean has(StructureKind kind) {
        return !of(kind).isEmpty();
    }

    public List<BlockPos> all() {
        List<BlockPos> all = new ArrayList<>();
        structures.values().forEach(all::addAll);
        return all;
    }

    public int kinds() {
        return (int) structures.values().stream().filter(list -> !list.isEmpty()).count();
    }
}
