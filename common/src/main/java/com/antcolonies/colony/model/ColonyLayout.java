package com.antcolonies.colony.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import net.minecraft.core.BlockPos;

/**
 * A colony's ring as its last scan found it: its chambers by kind (in a fixed order, so allocation never depends on
 * the scan order), how many of the eight places around the Terrarium have no chamber, and whether a chamber is also in
 * another Terrarium's ring (two colonies may never share one).
 */
public record ColonyLayout(Map<StructureKind, List<BlockPos>> structures, int missing, boolean twoColonies) {
    public static final ColonyLayout EMPTY = new ColonyLayout(new EnumMap<>(StructureKind.class), ColonyRing.SIZE, false);

    public List<BlockPos> of(StructureKind kind) {
        return structures.getOrDefault(kind, Collections.emptyList());
    }

    public int count(StructureKind kind) {
        return of(kind).size();
    }

    public boolean has(StructureKind kind) {
        return !of(kind).isEmpty();
    }

    /** All eight places hold a chamber, and none is shared: the colony works. */
    public boolean complete() {
        return missing == 0 && !twoColonies;
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
