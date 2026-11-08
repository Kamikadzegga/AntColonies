package com.antcolonies.colony.model;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.antcolonies.content.block.StructureBlock;
import com.antcolonies.content.block.TerrariumBlock;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.level.LevelEvent;

/**
 * A colony is a 3x3 ring on one level: the Terrarium in the middle and a chamber in each of the eight places around it
 * (sides and corners), any kinds in any mix. A chamber next to two Terrariums would be in two rings; both colonies then
 * stop until one Terrarium moves, so colonies never share a chamber by accident.
 *
 * <p>Placing or breaking a Terrarium or a chamber bumps its level's version, and Terrariums look at their ring again
 * when it changed.
 */
public final class ColonyRing {
    /** Places around the Terrarium. */
    public static final int SIZE = 8;
    private static final Map<ResourceKey<Level>, Long> VERSIONS = new HashMap<>();

    private ColonyRing() {}

    public static long version(Level level) {
        return VERSIONS.getOrDefault(level.dimension(), 0L);
    }

    public static void changed(Level level) {
        if (!level.isClientSide()) {
            VERSIONS.merge(level.dimension(), 1L, Long::sum);
        }
    }

    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof Level level && !level.isClientSide()) {
            VERSIONS.remove(level.dimension());
        }
    }

    /** The eight places around a Terrarium, in a fixed order (north-west first, clockwise). */
    public static List<BlockPos> places(BlockPos terrarium) {
        List<BlockPos> places = new ArrayList<>(SIZE);
        int[][] offsets = {{-1, -1}, {0, -1}, {1, -1}, {1, 0}, {1, 1}, {0, 1}, {-1, 1}, {-1, 0}};
        for (int[] offset : offsets) {
            places.add(terrarium.offset(offset[0], 0, offset[1]));
        }
        return places;
    }

    /** Looks at the ring of the Terrarium at {@code root}. Places in unloaded chunks count as empty. */
    public static ColonyLayout scan(Level level, BlockPos root) {
        Map<StructureKind, List<BlockPos>> structures = new EnumMap<>(StructureKind.class);
        int missing = 0;
        boolean shared = false;
        for (BlockPos place : places(root)) {
            if (!level.isLoaded(place)) {
                missing++;
                continue;
            }
            BlockState state = level.getBlockState(place);
            if (!(state.getBlock() instanceof StructureBlock chamber)) {
                missing++;
                continue;
            }
            structures.computeIfAbsent(chamber.structureKind(), k -> new ArrayList<>()).add(place.immutable());
            shared |= nearOtherTerrarium(level, place, root);
        }
        Comparator<BlockPos> order = Comparator.<BlockPos>comparingInt(BlockPos::getY).thenComparingInt(BlockPos::getX).thenComparingInt(BlockPos::getZ);
        structures.values().forEach(list -> list.sort(order));
        return new ColonyLayout(structures, missing, shared);
    }

    /** Whether a chamber also touches another Terrarium on its level (sides or corners): it would be in two rings. */
    private static boolean nearOtherTerrarium(Level level, BlockPos chamber, BlockPos ours) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                BlockPos pos = chamber.offset(dx, 0, dz);
                if (!pos.equals(ours) && level.isLoaded(pos) && level.getBlockState(pos).getBlock() instanceof TerrariumBlock) {
                    return true;
                }
            }
        }
        return false;
    }
}
