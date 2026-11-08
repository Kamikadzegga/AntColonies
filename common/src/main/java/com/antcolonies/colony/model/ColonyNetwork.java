package com.antcolonies.colony.model;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.antcolonies.content.block.ColonyNetworkBlock;
import com.antcolonies.core.config.AntConfig;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.level.LevelEvent;
import org.jspecify.annotations.Nullable;

/**
 * Finds what a Terrarium is connected to. A colony's network is every tube, tunnel and structure reachable from its
 * Terrarium through touching faces. Colony Connectors end a network, and the network on their other side belongs to
 * another colony that may trade food and resources with this one. Reaching a second Terrarium marks both colonies as
 * mixed: they stop working until a Connector separates them, so colonies never blend by accident.
 *
 * <p>Placing or breaking any network block bumps its level's version, and Terrariums rescan when it changed.
 */
public final class ColonyNetwork {
    private static final Map<ResourceKey<Level>, Long> VERSIONS = new HashMap<>();

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

    /** Scans the network of the Terrarium at {@code root}. Only loaded blocks are followed. */
    public static ColonyLayout scan(Level level, BlockPos root) {
        int limit = AntConfig.get(AntConfig.NETWORK_SCAN_LIMIT);
        Map<StructureKind, List<BlockPos>> structures = new EnumMap<>(StructureKind.class);
        Set<BlockPos> conduits = new HashSet<>();
        Set<BlockPos> visited = new HashSet<>();
        List<BlockPos> connectors = new ArrayList<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        boolean twoColonies = false;
        boolean truncated = false;
        visited.add(root);
        queue.add(root);
        while (!queue.isEmpty()) {
            BlockPos pos = queue.poll();
            for (Direction direction : Direction.values()) {
                BlockPos next = pos.relative(direction);
                if (visited.contains(next) || !level.isLoaded(next)) {
                    continue;
                }
                BlockState state = level.getBlockState(next);
                if (!(state.getBlock() instanceof ColonyNetworkBlock block)) {
                    continue;
                }
                visited.add(next);
                if (visited.size() > limit) {
                    truncated = true;
                    queue.clear();
                    break;
                }
                switch (block.role(state)) {
                    case TERRARIUM -> twoColonies = true;
                    case CONNECTOR -> {
                        connectors.add(next.immutable());
                        structures.computeIfAbsent(StructureKind.COLONY_CONNECTOR, k -> new ArrayList<>()).add(next.immutable());
                    }
                    case CONDUIT -> {
                        conduits.add(next.immutable());
                        queue.add(next);
                    }
                    case STRUCTURE -> {
                        StructureKind kind = block.kind(state);
                        if (kind != null) {
                            structures.computeIfAbsent(kind, k -> new ArrayList<>()).add(next.immutable());
                        }
                        queue.add(next);
                    }
                }
            }
        }
        // A fixed order, so the same network always allocates its ants the same way.
        Comparator<BlockPos> order = Comparator.<BlockPos>comparingInt(BlockPos::getY).thenComparingInt(BlockPos::getX).thenComparingInt(BlockPos::getZ);
        structures.values().forEach(list -> list.sort(order));
        List<BlockPos> linked = new ArrayList<>();
        for (BlockPos connector : connectors) {
            BlockPos other = findTerrariumBeyond(level, connector, visited, limit);
            if (other != null && !other.equals(root) && !linked.contains(other)) {
                linked.add(other);
            }
        }
        return new ColonyLayout(structures, conduits, linked, twoColonies, truncated);
    }

    /** The Terrarium of the network on the far side of a Connector, if there is one. */
    private static @Nullable BlockPos findTerrariumBeyond(Level level, BlockPos connector, Set<BlockPos> ours, int limit) {
        Set<BlockPos> visited = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        visited.add(connector);
        queue.add(connector);
        while (!queue.isEmpty()) {
            BlockPos pos = queue.poll();
            for (Direction direction : Direction.values()) {
                BlockPos next = pos.relative(direction);
                if (visited.contains(next) || ours.contains(next) || !level.isLoaded(next)) {
                    continue;
                }
                BlockState state = level.getBlockState(next);
                if (!(state.getBlock() instanceof ColonyNetworkBlock block)) {
                    continue;
                }
                visited.add(next);
                if (visited.size() > limit) {
                    return null;
                }
                switch (block.role(state)) {
                    case TERRARIUM -> {
                        return next.immutable();
                    }
                    case CONDUIT, STRUCTURE -> queue.add(next);
                    case CONNECTOR -> {
                        // A chain of connectors only reaches its first neighbour network.
                    }
                }
            }
        }
        return null;
    }

    private ColonyNetwork() {}
}
