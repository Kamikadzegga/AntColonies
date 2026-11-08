package com.antcolonies.colony.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import net.minecraft.core.BlockPos;

/**
 * A colony's build as its last scan found it: its chambers by kind (in a fixed order, so allocation never depends on
 * the scan order), the chambers it shares with another colony (exactly as near to both, so they join neither), and how
 * many touching chambers were left out over the colony's limit.
 */
public record ColonyLayout(Map<StructureKind, List<BlockPos>> structures, List<BlockPos> contested, int overLimit) {
    public static final ColonyLayout EMPTY = new ColonyLayout(new EnumMap<>(StructureKind.class), List.of(), 0);

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

    public int size() {
        return structures.values().stream().mapToInt(List::size).sum();
    }

    public int kinds() {
        return (int) structures.values().stream().filter(list -> !list.isEmpty()).count();
    }
}
